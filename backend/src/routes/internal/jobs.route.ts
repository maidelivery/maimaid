import { Hono } from "hono";
import { z } from "zod";
import { JobService } from "../../services/job.service.js";
import { ChartFitService, deserializeSongIdMapping } from "../../services/chart-fit.service.js";
import { StaticBundleService } from "../../services/static-bundle.service.js";
import { internalJobAuthRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";

const dispatchSchema = z.object({
	limit: z.number().int().positive().max(50).default(10),
});

const enqueueSchema = z.object({
	jobType: z.string().min(1),
	payload: z.record(z.string(), z.unknown()).default({}),
});

const songIdMappingSchema = z.object({
	byTitleAndType: z.record(z.string(), z.number()),
	byTitle: z.record(z.string(), z.array(z.number())),
});

/**
 * The payload is several MB of upstream JSON, so it is deliberately not validated
 * structurally: walking it with Zod would double the parse cost this endpoint
 * exists to keep off the server. `publishBundle` checks the md5 format and that
 * `resources.data_json` is present, which is what actually has to hold.
 */
const publishBundleSchema = z.object({
	payload: z.record(z.string(), z.unknown()),
	sourceMeta: z.record(z.string(), z.unknown()),
	md5: z.string(),
	force: z.boolean().default(false),
});

export const jobsInternalRoute = new Hono<AppEnv>();
jobsInternalRoute.use("*", internalJobAuthRequired);

jobsInternalRoute.post("/dispatch", standardValidator("json", dispatchSchema, validationHook), async (c) => {
	const body = c.req.valid("json");
	const jobService = c.var.resolve(JobService);
	const result = await jobService.dispatch(body.limit);
	return ok(c, { jobs: result });
});

jobsInternalRoute.post("/enqueue", standardValidator("json", enqueueSchema, validationHook), async (c) => {
	const body = c.req.valid("json");
	const jobService = c.var.resolve(JobService);
	const job = await jobService.enqueue(body.jobType, body.payload);
	return ok(c, { job }, 201);
});

/**
 * Step 1 of the GitHub Actions bundle build: which upstreams to fetch. Served from
 * the database so the dashboard's static-source editor still drives CI builds.
 */
jobsInternalRoute.get("/static-bundle/sources", async (c) => {
	const staticBundleService = c.var.resolve(StaticBundleService);
	const sources = await staticBundleService.listEnabledSourceTargets();
	return ok(c, { sources });
});

/**
 * Step 2: the one part of a build that needs the database. CI derives the song-id
 * mapping from the multi-MB source files and posts just the mapping; the server
 * aggregates `best_scores` against it and returns the resulting chart stats.
 */
jobsInternalRoute.post(
	"/static-bundle/self-chart-fit",
	standardValidator("json", songIdMappingSchema, validationHook),
	async (c) => {
		const body = c.req.valid("json");
		const chartFitService = c.var.resolve(ChartFitService);
		const result = await chartFitService.refreshSnapshotWithMapping(deserializeSongIdMapping(body));
		return ok(c, { payload: result.payload, meta: result.meta });
	},
);

/** Step 3: persist and activate the bundle CI assembled, and apply its catalog. */
jobsInternalRoute.post("/static-bundle/publish", standardValidator("json", publishBundleSchema, validationHook), async (c) => {
	const body = c.req.valid("json");
	const staticBundleService = c.var.resolve(StaticBundleService);
	const result = await staticBundleService.publishBundle({
		payload: body.payload,
		sourceMeta: body.sourceMeta,
		md5: body.md5,
		force: body.force,
	});
	return ok(c, {
		created: result.created,
		bundle: {
			version: result.bundle.version,
			md5: result.bundle.md5,
			createdAt: result.bundle.createdAt,
		},
	});
});
