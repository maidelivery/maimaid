import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { adminRequired } from "../../middleware/auth.js";
import type { CommunityAliasService } from "../../services/community-alias.service.js";
import type { AdminUserService } from "../../services/admin-user.service.js";
import type { StaticBundleService } from "../../services/static-bundle.service.js";
import { ok } from "../../http/response.js";
import { createCustomMethodParamSchema, standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";

const createCandidateSchema = z.object({
	songIdentifier: z.string().min(1),
	aliasText: z.string().min(1).max(64),
	status: z.enum(["voting", "approved"]).default("approved"),
});

const setStatusSchema = z.object({
	status: z.enum(["voting", "approved", "rejected"]),
});

const voteWindowSchema = z.object({
	voteCloseAt: z.iso.datetime(),
});

const listUsersQuerySchema = z.object({
	limit: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 30);
			if (!Number.isFinite(parsed)) return 30;
			return Math.max(1, Math.min(200, Math.trunc(parsed)));
		}),
	offset: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 0);
			if (!Number.isFinite(parsed)) return 0;
			return Math.max(0, Math.trunc(parsed));
		}),
});

const createUserSchema = z.object({
	email: z.email(),
	password: z.string().min(8),
});

const staticSourceCreateSchema = z.object({
	category: z.string().min(1),
	activeUrl: z.url(),
	fallbackUrls: z.array(z.url()).default([]),
	enabled: z.boolean().default(true),
	metadata: z.record(z.string(), z.unknown()).nullable().optional(),
});

const staticSourcePatchSchema = z
	.object({
		activeUrl: z.url().optional(),
		fallbackUrls: z.array(z.url()).optional(),
		enabled: z.boolean().optional(),
		metadata: z.record(z.string(), z.unknown()).nullable().optional(),
	})
	.refine((value) => Object.keys(value).length > 0, "No field to update.");

const bundleBuildSchema = z.object({
	force: z.boolean().default(false),
});

const staticBundleSchedulePatchSchema = z
	.object({
		enabled: z.boolean().optional(),
		intervalHours: z.coerce.number().int().positive().optional(),
	})
	.refine((value) => Object.keys(value).length > 0, "No field to update.");

const listCandidatesQuerySchema = z.object({
	status: z.string().optional(),
	search: z.string().optional(),
	sort: z.string().optional(),
	limit: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 30);
			if (!Number.isFinite(parsed)) return 30;
			return Math.max(1, Math.min(200, Math.trunc(parsed)));
		}),
	offset: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 0);
			if (!Number.isFinite(parsed)) return 0;
			return Math.max(0, Math.trunc(parsed));
		}),
});

const candidateSetStatusParamSchema = createCustomMethodParamSchema("candidateId", "setStatus", z.uuid());

const candidateVoteWindowParamSchema = createCustomMethodParamSchema("candidateId", "updateVoteWindow", z.uuid());

const userIdParamSchema = z.object({
	userId: z.uuid(),
});

const sourceIdParamSchema = z.object({
	sourceId: z.uuid(),
});

export const adminV1Route = new Hono<AppEnv>();

adminV1Route.get("/admin/context", adminRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	return ok(c, {
		userId: auth.userId,
		email: auth.email,
		isAdmin: auth.isAdmin,
	});
});

adminV1Route.get("/admin/dashboard", adminRequired, async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const stats = await communityAliasService.adminDashboardStats();
	return ok(c, stats);
});

adminV1Route.get(
	"/admin/candidates",
	adminRequired,
	standardValidator("query", listCandidatesQuerySchema, validationHook),
	async (c) => {
		const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
		const query = c.req.valid("query");

		const input: Parameters<CommunityAliasService["adminListCandidates"]>[0] = {
			limit: query.limit,
			offset: query.offset,
		};
		if (query.status !== undefined) input.status = query.status;
		if (query.search !== undefined) input.search = query.search;
		if (query.sort !== undefined) input.sort = query.sort;
		const rows = await communityAliasService.adminListCandidates(input);
		return ok(c, { rows });
	},
);

adminV1Route.post(
	"/admin/candidates",
	adminRequired,
	standardValidator("json", createCandidateSchema, validationHook),
	async (c) => {
		const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const body = c.req.valid("json");
		const candidate = await communityAliasService.adminCreateCandidate({
			submitterId: auth.userId,
			songIdentifier: body.songIdentifier,
			aliasText: body.aliasText,
			status: body.status,
		});
		return ok(c, { candidate }, 201);
	},
);

adminV1Route.post(
	"/admin/candidates/:candidateId:setStatus",
	adminRequired,
	standardValidator("param", candidateSetStatusParamSchema, validationHook),
	standardValidator("json", setStatusSchema, validationHook),
	async (c) => {
		const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
		const params = c.req.valid("param");
		const body = c.req.valid("json");
		const candidate = await communityAliasService.adminSetStatus(params.candidateId, body.status);
		return ok(c, { candidate });
	},
);

adminV1Route.post(
	"/admin/candidates/:candidateId:updateVoteWindow",
	adminRequired,
	standardValidator("param", candidateVoteWindowParamSchema, validationHook),
	standardValidator("json", voteWindowSchema, validationHook),
	async (c) => {
		const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
		const params = c.req.valid("param");
		const body = c.req.valid("json");
		const candidate = await communityAliasService.adminUpdateVoteWindow(params.candidateId, new Date(body.voteCloseAt));
		return ok(c, { candidate });
	},
);

adminV1Route.post("/admin:rollCycle", adminRequired, async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const result = await communityAliasService.rollCycle();
	return ok(c, result);
});

adminV1Route.get("/admin/users", adminRequired, standardValidator("query", listUsersQuerySchema, validationHook), async (c) => {
	const adminUserService = di.resolve<AdminUserService>(TOKENS.AdminUserService);
	const query = c.req.valid("query");
	const result = await adminUserService.listUsers({
		limit: query.limit,
		offset: query.offset,
	});
	return ok(c, result);
});

adminV1Route.post("/admin/users", adminRequired, standardValidator("json", createUserSchema, validationHook), async (c) => {
	const adminUserService = di.resolve<AdminUserService>(TOKENS.AdminUserService);
	const body = c.req.valid("json");
	const user = await adminUserService.createUser(body);
	return ok(c, { user }, 201);
});

adminV1Route.delete(
	"/admin/users/:userId",
	adminRequired,
	standardValidator("param", userIdParamSchema, validationHook),
	async (c) => {
		const adminUserService = di.resolve<AdminUserService>(TOKENS.AdminUserService);
		const params = c.req.valid("param");
		const result = await adminUserService.deleteUser(params.userId);
		return ok(c, result);
	},
);

adminV1Route.get("/admin/static-sources", adminRequired, async (c) => {
	const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
	const sources = await staticBundleService.listSources();
	return ok(c, { sources });
});

adminV1Route.post(
	"/admin/static-sources",
	adminRequired,
	standardValidator("json", staticSourceCreateSchema, validationHook),
	async (c) => {
		const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
		const body = c.req.valid("json");
		const source = await staticBundleService.createSource({
			category: body.category,
			activeUrl: body.activeUrl,
			fallbackUrls: body.fallbackUrls,
			enabled: body.enabled,
			metadata: body.metadata ?? null,
		});
		return ok(c, { source }, 201);
	},
);

adminV1Route.patch(
	"/admin/static-sources/:sourceId",
	adminRequired,
	standardValidator("param", sourceIdParamSchema, validationHook),
	standardValidator("json", staticSourcePatchSchema, validationHook),
	async (c) => {
		const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
		const params = c.req.valid("param");
		const body = c.req.valid("json");
		const patch: Parameters<StaticBundleService["updateSource"]>[1] = {};
		if (body.activeUrl !== undefined) {
			patch.activeUrl = body.activeUrl;
		}
		if (body.fallbackUrls !== undefined) {
			patch.fallbackUrls = body.fallbackUrls;
		}
		if (body.enabled !== undefined) {
			patch.enabled = body.enabled;
		}
		if (body.metadata !== undefined) {
			patch.metadata = body.metadata;
		}
		const source = await staticBundleService.updateSource(params.sourceId, patch);
		return ok(c, { source });
	},
);

adminV1Route.post(
	"/admin/static-bundles:build",
	adminRequired,
	standardValidator("json", bundleBuildSchema, validationHook),
	async (c) => {
		const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
		const body = c.req.valid("json");
		const result = await staticBundleService.buildBundle(body.force);
		return ok(c, result);
	},
);

adminV1Route.get("/admin/static-bundles", adminRequired, async (c) => {
	const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
	const bundles = await staticBundleService.listBundles();
	return ok(c, { bundles });
});

adminV1Route.get("/admin/static-bundle-schedule", adminRequired, async (c) => {
	const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
	const schedule = await staticBundleService.getPeriodicBuildSchedule();
	return ok(c, { schedule });
});

adminV1Route.patch(
	"/admin/static-bundle-schedule",
	adminRequired,
	standardValidator("json", staticBundleSchedulePatchSchema, validationHook),
	async (c) => {
		const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
		const body = c.req.valid("json");
		const patch: Parameters<StaticBundleService["updatePeriodicBuildSchedule"]>[0] = {};
		if (body.enabled !== undefined) {
			patch.enabled = body.enabled;
		}
		if (body.intervalHours !== undefined) {
			patch.intervalHours = body.intervalHours;
		}
		const schedule = await staticBundleService.updatePeriodicBuildSchedule(patch);
		return ok(c, { schedule });
	},
);
