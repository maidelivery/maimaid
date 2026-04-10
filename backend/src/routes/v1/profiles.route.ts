import { Hono, type Context } from "hono";
import { z } from "zod";
import { authRequired } from "../../middleware/auth.js";
import { ProfileService } from "../../services/profile.service.js";
import { SyncService } from "../../services/sync.service.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";

const createProfileSchema = z.object({
	name: z.string().min(1).max(40),
	server: z.enum(["jp", "intl", "usa", "cn"]).default("jp"),
});

const putProfileSchema = z.object({
	name: z.string().min(1).max(40),
	server: z.enum(["jp", "intl", "usa", "cn"]).default("jp"),
	isActive: z.boolean().optional(),
	playerRating: z.number().int().nonnegative().optional(),
	plate: z.string().nullable().optional(),
	avatarUrl: z.url().nullable().optional(),
	dfUsername: z.string().optional(),
	b35Count: z.number().int().positive().optional(),
	b15Count: z.number().int().positive().optional(),
	b35RecLimit: z.number().int().positive().optional(),
	b15RecLimit: z.number().int().positive().optional(),
	createdAt: z.coerce.date().optional(),
});

const updateProfileSchema = z
	.object({
		name: z.string().min(1).max(40).optional(),
		server: z.enum(["jp", "intl", "usa", "cn"]).optional(),
		isActive: z.boolean().optional(),
		playerRating: z.number().int().nonnegative().optional(),
		plate: z.string().nullable().optional(),
		dfUsername: z.string().optional(),
		b35Count: z.number().int().positive().optional(),
		b15Count: z.number().int().positive().optional(),
		b35RecLimit: z.number().int().positive().optional(),
		b15RecLimit: z.number().int().positive().optional(),
	})
	.refine((value) => Object.keys(value).length > 0, "No profile field to update.");

const avatarUploadSchema = z.object({
	contentType: z.string().default("image/png"),
});

const profileIdParamSchema = z.object({
	profileId: z.uuid(),
});

export const profilesV1Route = new Hono<AppEnv>();

function isWebClient(c: Context<AppEnv>) {
	const client = c.req.header("x-maimaid-client");
	return client?.trim().toLowerCase() === "web";
}

profilesV1Route.get("/:profileId/avatar", standardValidator("param", profileIdParamSchema, validationHook), async (c) => {
	const profileService = c.var.resolve(ProfileService);
	const params = c.req.valid("param");
	const avatar = await profileService.getAvatar(params.profileId);

	const ifNoneMatch = c.req.header("if-none-match");
	if (ifNoneMatch && avatar.etag && ifNoneMatch === avatar.etag) {
		if (avatar.body instanceof ReadableStream) {
			void avatar.body.cancel();
		}
		return new Response(null, { status: 304 });
	}

	const headers = new Headers({
		"content-type": avatar.contentType ?? "image/png",
		"cache-control": "public, max-age=300",
	});
	if (avatar.etag) {
		headers.set("etag", avatar.etag);
	}
	if (avatar.lastModified) {
		headers.set("last-modified", avatar.lastModified.toUTCString());
	}

	return new Response(avatar.body, { status: 200, headers });
});

profilesV1Route.use("*", authRequired);

profilesV1Route.get("/", async (c) => {
	const profileService = c.var.resolve(ProfileService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const profiles = await profileService.list(auth.userId);
	return ok(c, { profiles });
});

profilesV1Route.post("/", standardValidator("json", createProfileSchema, validationHook), async (c) => {
	const profileService = c.var.resolve(ProfileService);
	const syncService = c.var.resolve(SyncService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const body = c.req.valid("json");
	const profile = await profileService.create(auth.userId, body);
	await syncService.recordEvent({
		userId: auth.userId,
		profileId: profile.id,
		entityType: "profile",
		entityId: profile.id,
		op: "upsert",
		payload: {
			updatedAt: profile.updatedAt.toISOString(),
		},
	});
	return ok(c, { profile }, 201);
});

profilesV1Route.put(
	"/:profileId",
	standardValidator("param", profileIdParamSchema, validationHook),
	standardValidator("json", putProfileSchema, validationHook),
	async (c) => {
		const profileService = c.var.resolve(ProfileService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const params = c.req.valid("param");
		const body = c.req.valid("json");
		const payload: Parameters<ProfileService["upsertByClientId"]>[2] = {
			name: body.name,
			server: body.server,
		};
		if (body.isActive !== undefined && !isWebClient(c)) payload.isActive = body.isActive;
		if (body.playerRating !== undefined) payload.playerRating = body.playerRating;
		if (body.plate !== undefined) payload.plate = body.plate;
		if (body.avatarUrl !== undefined) payload.avatarUrl = body.avatarUrl;
		if (body.dfUsername !== undefined) payload.dfUsername = body.dfUsername;
		if (body.b35Count !== undefined) payload.b35Count = body.b35Count;
		if (body.b15Count !== undefined) payload.b15Count = body.b15Count;
		if (body.b35RecLimit !== undefined) payload.b35RecLimit = body.b35RecLimit;
		if (body.b15RecLimit !== undefined) payload.b15RecLimit = body.b15RecLimit;
		if (body.createdAt !== undefined) payload.createdAt = body.createdAt;
		const profile = await profileService.upsertByClientId(auth.userId, params.profileId, payload);
		await syncService.recordEvent({
			userId: auth.userId,
			profileId: profile.id,
			entityType: "profile",
			entityId: profile.id,
			op: "upsert",
			payload: {
				updatedAt: profile.updatedAt.toISOString(),
			},
		});
		if (body.avatarUrl !== undefined) {
			await syncService.recordEvent({
				userId: auth.userId,
				profileId: profile.id,
				entityType: "avatar",
				entityId: profile.id,
				op: "upsert",
				payload: {
					avatarUrl: body.avatarUrl,
				},
			});
		}
		return ok(c, { profile });
	},
);

profilesV1Route.patch(
	"/:profileId",
	standardValidator("param", profileIdParamSchema, validationHook),
	standardValidator("json", updateProfileSchema, validationHook),
	async (c) => {
		const profileService = c.var.resolve(ProfileService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const body = c.req.valid("json");
		const params = c.req.valid("param");
		const payload: Parameters<ProfileService["update"]>[2] = {};
		if (body.name !== undefined) payload.name = body.name;
		if (body.server !== undefined) payload.server = body.server;
		if (body.isActive !== undefined && !isWebClient(c)) payload.isActive = body.isActive;
		if (body.playerRating !== undefined) payload.playerRating = body.playerRating;
		if (body.plate !== undefined) payload.plate = body.plate;
		if (body.dfUsername !== undefined) payload.dfUsername = body.dfUsername;
		if (body.b35Count !== undefined) payload.b35Count = body.b35Count;
		if (body.b15Count !== undefined) payload.b15Count = body.b15Count;
		if (body.b35RecLimit !== undefined) payload.b35RecLimit = body.b35RecLimit;
		if (body.b15RecLimit !== undefined) payload.b15RecLimit = body.b15RecLimit;
		if (Object.keys(payload).length === 0 && body.isActive !== undefined && isWebClient(c)) {
			return ok(c, { code: "forbidden", message: "Only app client can change active profile." }, 403);
		}
		const profile = await profileService.update(auth.userId, params.profileId, payload);
		await syncService.recordEvent({
			userId: auth.userId,
			profileId: profile.id,
			entityType: "profile",
			entityId: profile.id,
			op: "upsert",
			payload: {
				updatedAt: profile.updatedAt.toISOString(),
			},
		});
		return ok(c, { profile });
	},
);

profilesV1Route.delete("/:profileId", standardValidator("param", profileIdParamSchema, validationHook), async (c) => {
	const profileService = c.var.resolve(ProfileService);
	const syncService = c.var.resolve(SyncService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const params = c.req.valid("param");
	const deleted = await profileService.remove(auth.userId, params.profileId);
	await syncService.recordEvent({
		userId: auth.userId,
		profileId: deleted.id,
		entityType: "profile",
		entityId: deleted.id,
		op: "delete",
		payload: null,
	});
	return ok(c, { profileId: deleted.id });
});

profilesV1Route.post(
	"/:profileId/avatar:createUploadUrl",
	standardValidator("param", profileIdParamSchema, validationHook),
	standardValidator("json", avatarUploadSchema, validationHook),
	async (c) => {
		const profileService = c.var.resolve(ProfileService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const params = c.req.valid("param");
		const body = c.req.valid("json");
		const result = await profileService.createAvatarUploadUrl(auth.userId, params.profileId, body.contentType);
		return ok(c, result);
	},
);
