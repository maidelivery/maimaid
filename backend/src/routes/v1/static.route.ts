import { Hono } from "hono";
import { z } from "zod";
import { StaticBundleService } from "../../services/static-bundle.service.js";
import { ChartFitService } from "../../services/chart-fit.service.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";

export const staticV1Route = new Hono<AppEnv>();

const bundleParamSchema = z.object({
	version: z.string().min(1),
});

staticV1Route.get("/manifest", async (c) => {
	const staticBundleService = c.var.resolve(StaticBundleService);
	const manifest = await staticBundleService.manifest();
	return ok(c, manifest);
});

staticV1Route.get("/bundle/:version", standardValidator("param", bundleParamSchema, validationHook), async (c) => {
	const staticBundleService = c.var.resolve(StaticBundleService);
	const params = c.req.valid("param");
	const bundle = await staticBundleService.getBundle(params.version);
	return ok(c, {
		version: bundle.version,
		md5: bundle.md5,
		createdAt: bundle.createdAt,
		payload: bundle.payloadJson,
	});
});

staticV1Route.get("/songid-items", async (c) => {
	const staticBundleService = c.var.resolve(StaticBundleService);
	const items = await staticBundleService.listSongIdItems();
	return ok(c, { items });
});

staticV1Route.get("/chart_stats", async (c) => {
	const chartFitService = c.var.resolve(ChartFitService);
	const snapshot = await chartFitService.getLatestSnapshotOrRefresh();
	return ok(c, snapshot.payload);
});
