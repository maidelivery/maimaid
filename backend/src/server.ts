import "reflect-metadata";
import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { serve } from "@hono/node-server";
import { createApp, registerOpenApiRoutes } from "./app.js";
import { TOKENS } from "./di/tokens.js";
import { getEnv } from "./node-env.js";
import { JobService } from "./services/job.service.js";
import { container } from "tsyringe";
import { getPrismaClient } from "./lib/node-prisma.js";
import { buildOpenApiDocument } from "./openapi.js";
import { LetterGameConnectionHub } from "./services/letter-game.connection.js";
import { LetterGameTurnScheduler } from "./services/letter-game.scheduler.js";

const env = getEnv();
const prisma = getPrismaClient();
container.register(TOKENS.Env, { useValue: env });
container.register(TOKENS.Prisma, { useValue: prisma });

const app = createApp({
	resolveDependencies: () => ({ env, prisma }),
});

const resolvePrebuiltOpenApiPath = () => {
	const currentFilePath = fileURLToPath(import.meta.url);
	return path.join(path.dirname(currentFilePath), "openapi.prebuilt.json");
};

const loadPrebuiltOpenApiDocument = () => {
	if (env.NODE_ENV !== "production") {
		return null;
	}

	const prebuiltPath = resolvePrebuiltOpenApiPath();
	if (!existsSync(prebuiltPath)) {
		return null;
	}

	try {
		const parsed = JSON.parse(readFileSync(prebuiltPath, "utf8")) as unknown;
		return typeof parsed === "object" && parsed !== null ? parsed : null;
	} catch (error) {
		console.warn("[openapi] failed to load prebuilt document, falling back to runtime generation", error);
		return null;
	}
};

registerOpenApiRoutes(app, loadPrebuiltOpenApiDocument() ?? buildOpenApiDocument(app, env));

/**
 * Drain "job_queue" on a timer. pg_cron only enqueues; a consumer executes the
 * scheduled catalog and community maintenance jobs.
 *
 * A tick is skipped while the previous one is still running. Claiming is atomic
 * in the database, so a second
 * process (or a manual POST to /internal/jobs/dispatch) cannot double-run a job.
 */
const startJobDispatcher = () => {
	if (!env.JOB_DISPATCHER_ENABLED) {
		console.log("[jobs] dispatcher disabled (JOB_DISPATCHER_ENABLED=false); scheduled jobs will not run");
		return () => {};
	}

	const jobService = container.resolve(JobService);
	let running = false;

	const tick = async () => {
		if (running) {
			return;
		}
		running = true;
		try {
			const results = await jobService.dispatch(env.JOB_DISPATCHER_BATCH_SIZE);
			for (const result of results) {
				if (result.status === "failed") {
					console.error(`[jobs] ${result.jobType} (${result.jobId.toString()}) failed: ${result.error}`);
				} else {
					console.log(`[jobs] ${result.jobType} (${result.jobId.toString()}) succeeded`);
				}
			}
		} catch (error) {
			console.error("[jobs] dispatch tick failed:", error);
		} finally {
			running = false;
		}
	};

	const intervalMs = env.JOB_DISPATCHER_INTERVAL_SECONDS * 1000;
	const timer = setInterval(() => void tick(), intervalMs);
	// Don't hold the process open on shutdown just because a timer is pending.
	timer.unref();
	console.log(
		`[jobs] dispatcher started (every ${env.JOB_DISPATCHER_INTERVAL_SECONDS}s, up to ${env.JOB_DISPATCHER_BATCH_SIZE} jobs per tick)`,
	);

	return () => clearInterval(timer);
};

const start = async () => {
	const stopJobDispatcher = startJobDispatcher();

	const server = serve(
		{
			fetch: app.fetch,
			hostname: env.HOST,
			port: env.PORT,
		},
		(info) => {
			console.log(`maimaid-backend listening on http://${env.HOST}:${info.port}`);
		},
	);
	container.resolve(LetterGameConnectionHub).attach(server as import("node:http").Server);
	const letterGameScheduler = container.resolve(LetterGameTurnScheduler);
	letterGameScheduler.start();

	// The container stops the process with SIGTERM; without a handler Node exits
	// immediately and an in-flight job stays stuck in 'running'.
	let shuttingDown = false;
	const shutdown = (signal: string) => {
		if (shuttingDown) {
			return;
		}
		shuttingDown = true;
		console.log(`[shutdown] received ${signal}, closing server`);
		stopJobDispatcher();
		letterGameScheduler.stop();
		server.close(() => {
			void prisma.$disconnect().finally(() => process.exit(0));
		});
	};

	process.on("SIGTERM", () => shutdown("SIGTERM"));
	process.on("SIGINT", () => shutdown("SIGINT"));
};

void start();
