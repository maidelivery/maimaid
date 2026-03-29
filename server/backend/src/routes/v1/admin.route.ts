import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { adminRequired } from "../../middleware/auth.js";
import type { CommunityAliasService } from "../../services/community-alias.service.js";
import type { AdminUserService } from "../../services/admin-user.service.js";
import type { StaticBundleService } from "../../services/static-bundle.service.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

const createCandidateSchema = z.object({
  songIdentifier: z.string().min(1),
  aliasText: z.string().min(1).max(64),
  status: z.enum(["voting", "approved"]).default("approved")
});

const setStatusSchema = z.object({
  status: z.enum(["voting", "approved", "rejected"])
});

const voteWindowSchema = z.object({
  voteCloseAt: z.string().datetime()
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
    })
});

const createUserSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8)
});

const staticSourceCreateSchema = z.object({
  category: z.string().min(1),
  activeUrl: z.string().url(),
  fallbackUrls: z.array(z.string().url()).default([]),
  enabled: z.boolean().default(true),
  metadata: z.record(z.string(), z.unknown()).nullable().optional()
});

const staticSourcePatchSchema = z
  .object({
    activeUrl: z.string().url().optional(),
    fallbackUrls: z.array(z.string().url()).optional(),
    enabled: z.boolean().optional(),
    metadata: z.record(z.string(), z.unknown()).nullable().optional()
  })
  .refine((value) => Object.keys(value).length > 0, "No field to update.");

const bundleBuildSchema = z.object({
  force: z.boolean().default(false)
});

const staticBundleSchedulePatchSchema = z
  .object({
    enabled: z.boolean().optional(),
    intervalHours: z.coerce.number().int().min(1).max(24).optional()
  })
  .refine((value) => Object.keys(value).length > 0, "No field to update.");

export const adminV1Route = new Hono<AppEnv>();
adminV1Route.use("*", adminRequired);

adminV1Route.get("/context", async (c) => {
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  return ok(c, {
    userId: auth.userId,
    email: auth.email,
    isAdmin: auth.isAdmin
  });
});

adminV1Route.get("/dashboard", async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const stats = await communityAliasService.adminDashboardStats();
  return ok(c, stats);
});

adminV1Route.get("/candidates", async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const status = c.req.query("status");
  const search = c.req.query("search");
  const sort = c.req.query("sort");
  const limit = Number(c.req.query("limit") ?? 30);
  const offset = Number(c.req.query("offset") ?? 0);

  const input: Parameters<CommunityAliasService["adminListCandidates"]>[0] = {
    limit: Math.max(1, Math.min(limit, 200)),
    offset: Math.max(0, offset)
  };
  if (status !== undefined) input.status = status;
  if (search !== undefined) input.search = search;
  if (sort !== undefined) input.sort = sort;
  const rows = await communityAliasService.adminListCandidates(input);
  return ok(c, { rows });
});

adminV1Route.post("/candidates", async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = createCandidateSchema.parse(await c.req.json());
  const candidate = await communityAliasService.adminCreateCandidate({
    submitterId: auth.userId,
    songIdentifier: body.songIdentifier,
    aliasText: body.aliasText,
    status: body.status
  });
  return ok(c, { candidate }, 201);
});

adminV1Route.patch("/candidates/:candidateId/status", async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const candidateId = c.req.param("candidateId");
  const body = setStatusSchema.parse(await c.req.json());
  const candidate = await communityAliasService.adminSetStatus(candidateId, body.status);
  return ok(c, { candidate });
});

adminV1Route.patch("/candidates/:candidateId/vote-window", async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const candidateId = c.req.param("candidateId");
  const body = voteWindowSchema.parse(await c.req.json());
  const candidate = await communityAliasService.adminUpdateVoteWindow(candidateId, new Date(body.voteCloseAt));
  return ok(c, { candidate });
});

adminV1Route.post("/roll-cycle", async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const result = await communityAliasService.rollCycle();
  return ok(c, result);
});

adminV1Route.get("/users", async (c) => {
  const adminUserService = di.resolve<AdminUserService>(TOKENS.AdminUserService);
  const query = listUsersQuerySchema.parse(c.req.query());
  const result = await adminUserService.listUsers({
    limit: query.limit,
    offset: query.offset
  });
  return ok(c, result);
});

adminV1Route.post("/users", async (c) => {
  const adminUserService = di.resolve<AdminUserService>(TOKENS.AdminUserService);
  const body = createUserSchema.parse(await c.req.json());
  const user = await adminUserService.createUser(body);
  return ok(c, { user }, 201);
});

adminV1Route.delete("/users/:userId", async (c) => {
  const adminUserService = di.resolve<AdminUserService>(TOKENS.AdminUserService);
  const userId = c.req.param("userId");
  const result = await adminUserService.deleteUser(userId);
  return ok(c, result);
});

adminV1Route.get("/static-sources", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const sources = await staticBundleService.listSources();
  return ok(c, { sources });
});

adminV1Route.post("/static-sources", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const body = staticSourceCreateSchema.parse(await c.req.json());
  const source = await staticBundleService.createSource({
    category: body.category,
    activeUrl: body.activeUrl,
    fallbackUrls: body.fallbackUrls,
    enabled: body.enabled,
    metadata: body.metadata ?? null
  });
  return ok(c, { source }, 201);
});

adminV1Route.patch("/static-sources/:sourceId", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const sourceId = c.req.param("sourceId");
  const body = staticSourcePatchSchema.parse(await c.req.json());
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
  const source = await staticBundleService.updateSource(sourceId, patch);
  return ok(c, { source });
});

adminV1Route.post("/static-bundles/build", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const body = bundleBuildSchema.parse(await c.req.json());
  const result = await staticBundleService.buildBundle(body.force);
  return ok(c, result);
});

adminV1Route.get("/static-bundles", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const bundles = await staticBundleService.listBundles();
  return ok(c, { bundles });
});

adminV1Route.get("/static-bundle-schedule", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const schedule = await staticBundleService.getPeriodicBuildSchedule();
  return ok(c, { schedule });
});

adminV1Route.patch("/static-bundle-schedule", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const body = staticBundleSchedulePatchSchema.parse(await c.req.json());
  const patch: Parameters<StaticBundleService["updatePeriodicBuildSchedule"]>[0] = {};
  if (body.enabled !== undefined) {
    patch.enabled = body.enabled;
  }
  if (body.intervalHours !== undefined) {
    patch.intervalHours = body.intervalHours;
  }
  const schedule = await staticBundleService.updatePeriodicBuildSchedule(patch);
  return ok(c, { schedule });
});
