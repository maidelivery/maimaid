import { inject, singleton } from "tsyringe";
import type { Prisma, PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { CatalogService } from "./catalog.service.js";
import { CommunityAliasService } from "./community-alias.service.js";
import { StaticBundleService } from "./static-bundle.service.js";

@singleton()
export class JobService {
	constructor(
		@inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
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

	async dispatch(limit = 10) {
		const pending = await this.prisma.jobQueue.findMany({
			where: {
				status: "pending",
				scheduledAt: {
					lte: new Date(),
				},
			},
			orderBy: [{ scheduledAt: "asc" }],
			take: Math.max(1, Math.min(limit, 50)),
		});

		const results: Array<{ jobId: bigint; status: string; error?: string }> = [];

		for (const job of pending) {
			await this.prisma.jobQueue.update({
				where: { id: job.id },
				data: {
					status: "running",
					startedAt: new Date(),
				},
			});
			try {
				if (job.jobType === "catalog_sync") {
					await this.catalogService.syncCatalog(false);
				} else if (job.jobType === "community_alias_roll_cycle") {
					await this.communityAliasService.rollCycle();
				} else if (job.jobType === "static_bundle_build") {
					// buildBundle also refreshes Song/Sheet catalog from bundle data_json.
					await this.staticBundleService.buildBundle(false);
				}
				await this.prisma.jobQueue.update({
					where: { id: job.id },
					data: {
						status: "success",
						finishedAt: new Date(),
					},
				});
				results.push({ jobId: job.id, status: "success" });
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
				results.push({ jobId: job.id, status: "failed", error: message });
			}
		}

		return results;
	}
}
