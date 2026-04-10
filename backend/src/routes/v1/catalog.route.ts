import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { CatalogService } from "../../services/catalog.service.js";
import { adminRequired, authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";

const syncSchema = z.object({
	force: z.boolean().default(false),
});

const songsQuerySchema = z.object({
	includeDisabled: z
		.enum(["true", "false"])
		.optional()
		.transform((value) => value === "true"),
	keyword: z.string().optional(),
});

const sheetsQuerySchema = z.object({
	songIdentifier: z.string().optional(),
});

const aliasesQuerySchema = z.object({
	songIdentifier: z.string().optional(),
	source: z.string().optional(),
});

const snapshotRollbackParamSchema = z.object({
	snapshotId: z.string().regex(/^\d+$/).transform((value) => BigInt(value)),
});

export const catalogV1Route = new Hono<AppEnv>();

catalogV1Route.get("/songs", standardValidator("query", songsQuerySchema, validationHook), async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const query = c.req.valid("query");
	const songs = await catalogService.listSongs(query.includeDisabled, query.keyword);
	return ok(c, { songs });
});

catalogV1Route.get("/sheets", standardValidator("query", sheetsQuerySchema, validationHook), async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const query = c.req.valid("query");
	const sheets = await catalogService.listSheets(query.songIdentifier);
	return ok(c, { sheets });
});

catalogV1Route.get("/aliases", standardValidator("query", aliasesQuerySchema, validationHook), async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const query = c.req.valid("query");
	const aliases = await catalogService.listAliases(query.songIdentifier, query.source);
	return ok(c, { aliases });
});

catalogV1Route.get("/versions", async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const versions = await catalogService.listVersions();
	return ok(c, { versions });
});

catalogV1Route.get("/icons", async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const icons = await catalogService.listIcons();
	return ok(c, { icons });
});

catalogV1Route.get("/snapshots", authRequired, async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const snapshots = await catalogService.listSnapshots();
	return ok(c, { snapshots });
});

catalogV1Route.post("/sync", adminRequired, standardValidator("json", syncSchema, validationHook), async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const body = c.req.valid("json");
	const result = await catalogService.syncCatalog(body.force);
	return ok(c, result);
});

catalogV1Route.post(
	"/snapshots/:snapshotId/rollback",
	adminRequired,
	standardValidator("param", snapshotRollbackParamSchema, validationHook),
	async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const params = c.req.valid("param");
	const snapshot = await catalogService.rollback(params.snapshotId);
	return ok(c, { snapshot });
	},
);
