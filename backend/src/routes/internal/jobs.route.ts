import { Hono } from "hono";
import { z } from "zod";
import { JobService } from "../../services/job.service.js";
import { adminRequired } from "../../middleware/auth.js";
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

export const jobsInternalRoute = new Hono<AppEnv>();
jobsInternalRoute.use("*", adminRequired);

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
