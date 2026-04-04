import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { JobService } from "../../services/job.service.js";
import { adminRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

const dispatchSchema = z.object({
	limit: z.number().int().positive().max(50).default(10),
});

const enqueueSchema = z.object({
	jobType: z.string().min(1),
	payload: z.record(z.string(), z.unknown()).default({}),
});

export const jobsInternalRoute = new Hono<AppEnv>();
jobsInternalRoute.use("*", adminRequired);

jobsInternalRoute.post("/dispatch", async (c) => {
	const body = dispatchSchema.parse(await c.req.json());
	const jobService = di.resolve<JobService>(TOKENS.JobService);
	const result = await jobService.dispatch(body.limit);
	return ok(c, { jobs: result });
});

jobsInternalRoute.post("/enqueue", async (c) => {
	const body = enqueueSchema.parse(await c.req.json());
	const jobService = di.resolve<JobService>(TOKENS.JobService);
	const job = await jobService.enqueue(body.jobType, body.payload);
	return ok(c, { job }, 201);
});
