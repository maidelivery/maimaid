import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { CatalogService } from "../../services/catalog.service.js";
import { adminRequired, authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

const syncSchema = z.object({
	force: z.boolean().default(false),
});

export const catalogV1Route = new Hono<AppEnv>();

catalogV1Route.get("/songs", async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const includeDisabled = c.req.query("includeDisabled") === "true";
	const keyword = c.req.query("keyword");
	const songs = await catalogService.listSongs(includeDisabled, keyword);
	return ok(c, { songs });
});

catalogV1Route.get("/sheets", async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const songIdentifier = c.req.query("songIdentifier");
	const sheets = await catalogService.listSheets(songIdentifier);
	return ok(c, { sheets });
});

catalogV1Route.get("/aliases", async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const songIdentifier = c.req.query("songIdentifier");
	const source = c.req.query("source");
	const aliases = await catalogService.listAliases(songIdentifier, source);
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

catalogV1Route.post("/sync", adminRequired, async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const body = syncSchema.parse(await c.req.json());
	const result = await catalogService.syncCatalog(body.force);
	return ok(c, result);
});

catalogV1Route.post("/snapshots/:snapshotId/rollback", adminRequired, async (c) => {
	const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
	const snapshotIdRaw = c.req.param("snapshotId");
	if (!snapshotIdRaw) {
		return ok(c, { code: "invalid_snapshot_id", message: "snapshotId is required." }, 400);
	}
	const snapshotId = BigInt(snapshotIdRaw);
	const snapshot = await catalogService.rollback(snapshotId);
	return ok(c, { snapshot });
});
