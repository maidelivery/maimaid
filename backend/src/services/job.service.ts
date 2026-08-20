import { inject, injectable } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import type { Env } from "../env.js";
import { CatalogService } from "./catalog.service.js";
import { CommunityAliasService } from "./community-alias.service.js";
import { StaticBundleService } from "./static-bundle.service.js";

// Finished rows are only useful for reading recent history from the dashboard.
// Keeping them forever grows a table that is written to on a schedule.
const FINISHED_JOB_RETENTION_DAYS = 14;

// A row left in 'running' blocks its job type forever, because `enqueue_job`
// treats pending-or-running as "already queued". That happens whenever the
// process dies mid-job — an OOM kill during a bundle build is the likely case on
// a small host. Anything running longer than this lease is treated as abandoned.
// Well above the catalog apply transaction timeout (120s) plus the upstream
// fetches a bundle build makes, so a slow-but-alive job is never stolen.
const RUNNING_JOB_LEASE_MS = 30 * 60_000;

type ClaimedJob = {
	id: bigint;
	jobType: string;
};

export type DispatchResult = {
	jobId: bigint;
	jobType: string;
	status: "success" | "failed";
	error?: string;
};

@injectable()
export class JobService {
	constructor(
		@inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
		@inject(TOKENS.Env) private readonly env: Env,
		@inject(CatalogService) private readonly catalogService: CatalogService,
		@inject(CommunityAliasService) private readonly communityAliasService: CommunityAliasService,
		@inject(StaticBundleService) private readonly staticBundleService: StaticBundleService,
	) {}

	async enqueue(jobType: string, payload: Record<string, unknown> = {}) {
		return this.prisma.jobQueue.create({
			data: {
				jobType,
				payload: payload as Prisma.InputJsonValue,
				status: "pending",
				scheduledAt: new Date(),
			},
		});
	}

	/**
	 * Claim one due job, atomically. `FOR UPDATE SKIP LOCKED` means a second
	 * dispatcher running concurrently takes a different row instead of the same
	 * one: a select-then-update pair would let both claim it and run an expensive
	 * bundle build twice.
	 */
	private async claimNextJob(): Promise<ClaimedJob | null> {
		if (this.env.DATABASE_DIALECT === "sqlite") {
			const rows = await this.prisma.$queryRaw<ClaimedJob[]>(Prisma.sql`
				UPDATE "job_queue"
				   SET "status" = 'running',
				       "startedAt" = CURRENT_TIMESTAMP,
				       "updatedAt" = CURRENT_TIMESTAMP
				 WHERE "id" = (
				       SELECT "id"
				         FROM "job_queue"
				        WHERE "status" = 'pending'
				          AND "scheduledAt" <= CURRENT_TIMESTAMP
				        ORDER BY "scheduledAt" ASC
				        LIMIT 1
				 )
				RETURNING "id", "jobType"
			`);
			return rows[0] ?? null;
		}
		const rows = await this.prisma.$queryRaw<ClaimedJob[]>(Prisma.sql`
			UPDATE "job_queue"
			   SET "status" = 'running',
			       "startedAt" = now(),
			       "updatedAt" = now()
			 WHERE "id" = (
			       SELECT "id"
			         FROM "job_queue"
			        WHERE "status" = 'pending'
			          AND "scheduledAt" <= now()
			        ORDER BY "scheduledAt" ASC
			        LIMIT 1
			          FOR UPDATE SKIP LOCKED
			 )
			RETURNING "id", "jobType"
		`);
		return rows[0] ?? null;
	}

	private async runJob(jobType: string) {
		switch (jobType) {
			case "catalog_sync":
				await this.catalogService.syncCatalog(false);
				return;
			case "community_alias_roll_cycle":
				await this.communityAliasService.rollCycle();
				return;
			case "static_bundle_build":
				// buildBundle also refreshes Song/Sheet catalog from bundle data_json.
				await this.staticBundleService.buildBundle(false);
				return;
			default:
				// Previously an unrecognised type fell through the if/else chain and was
				// marked 'success' without doing anything, hiding typos in the enqueued
				// job type.
				throw new Error(`unknown_job_type:${jobType}`);
		}
	}

	/**
	 * Release jobs whose lease expired. Without this, a process killed mid-job
	 * leaves a 'running' row that `enqueue_job` counts as "already queued",
	 * so that job type is never scheduled again. Marked failed rather than
	 * requeued: whatever killed the process is likely to kill the retry too, and
	 * a visible failure is better than a silent loop.
	 */
	private async reclaimAbandonedJobs() {
		const cutoff = new Date(Date.now() - RUNNING_JOB_LEASE_MS);
		const { count } = await this.prisma.jobQueue.updateMany({
			where: {
				status: "running",
				startedAt: { lt: cutoff },
			},
			data: {
				status: "failed",
				finishedAt: new Date(),
				error: "abandoned: exceeded running lease",
			},
		});
		return count;
	}

	async dispatch(limit = 10) {
		const maxJobs = Math.max(1, Math.min(limit, 50));
		const results: DispatchResult[] = [];

		const reclaimed = await this.reclaimAbandonedJobs();
		if (reclaimed > 0) {
			console.warn(`[jobs] reclaimed ${reclaimed} abandoned job(s) past the running lease`);
		}

		for (let claimed = 0; claimed < maxJobs; claimed += 1) {
			const job = await this.claimNextJob();
			if (!job) {
				break;
			}

			try {
				await this.runJob(job.jobType);
				await this.prisma.jobQueue.update({
					where: { id: job.id },
					data: {
						status: "success",
						finishedAt: new Date(),
					},
				});
				results.push({ jobId: job.id, jobType: job.jobType, status: "success" });
			} catch (error) {
				const message = error instanceof Error ? error.message : "unknown_error";
				await this.prisma.jobQueue.update({
					where: { id: job.id },
					data: {
						status: "failed",
						finishedAt: new Date(),
						error: message,
					},
				});
				results.push({ jobId: job.id, jobType: job.jobType, status: "failed", error: message });
			}
		}

		if (results.length > 0) {
			await this.pruneFinishedJobs();
		}

		return results;
	}

	/** Drop finished rows past the retention window. Never touches live work. */
	private async pruneFinishedJobs() {
		const cutoff = new Date(Date.now() - FINISHED_JOB_RETENTION_DAYS * 24 * 60 * 60 * 1000);
		await this.prisma.jobQueue.deleteMany({
			where: {
				status: { in: ["success", "failed"] },
				finishedAt: { lt: cutoff },
			},
		});
	}
}
