import { Hono } from "hono";
import { z } from "zod";
import { CatalogService } from "../../services/catalog.service.js";
import { adminRequired, authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";

const syncSchema = z.object({
	force: z.boolean().default(false),
});

const snapshotRollbackParamSchema = z.object({
	snapshotId: z
		.string()
		.regex(/^\d+$/)
		.transform((value) => BigInt(value)),
});

export const catalogV1Route = new Hono<AppEnv>();

catalogV1Route.get("/snapshots", authRequired, async (c) => {
	const catalogService = c.var.resolve(CatalogService);
	const snapshots = await catalogService.listSnapshots();
	return ok(c, { snapshots });
});

catalogV1Route.post("/sync", adminRequired, standardValidator("json", syncSchema, validationHook), async (c) => {
	const catalogService = c.var.resolve(CatalogService);
	const body = c.req.valid("json");
	const result = await catalogService.syncCatalog(body.force);
	return ok(c, result);
});

catalogV1Route.post(
	"/snapshots/:snapshotId/rollback",
	adminRequired,
	standardValidator("param", snapshotRollbackParamSchema, validationHook),
	async (c) => {
		const catalogService = c.var.resolve(CatalogService);
		const params = c.req.valid("param");
		const snapshot = await catalogService.rollback(params.snapshotId);
		return ok(c, { snapshot });
	},
);
