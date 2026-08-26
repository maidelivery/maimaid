import { Hono } from "hono";
import { z } from "zod";
import { ok } from "../../http/response.js";
import { AppError } from "../../lib/errors.js";
import { CollectionSharingService } from "../../services/collection-sharing.service.js";
import type { AppEnv } from "../../types/hono.js";
import { standardValidator, validationHook } from "../../http/validation.js";

const collectionParamSchema = z.object({ collectionId: z.uuid() });

export const publicCollectionsV1Route = new Hono<AppEnv>();

publicCollectionsV1Route.get(
	"/collections/:collectionId",
	standardValidator("param", collectionParamSchema, validationHook),
	async (c) => {
		const { collectionId } = c.req.valid("param");
		const collection = await c.var.resolve(CollectionSharingService).findPublicCollection(collectionId);
		if (!collection) {
			throw new AppError(404, "collection_not_found", "Collection not found.");
		}

		c.header("Cache-Control", "public, max-age=30, stale-while-revalidate=60");
		return ok(c, { collection });
	},
);
