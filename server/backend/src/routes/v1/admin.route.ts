import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { adminRequired } from "../../middleware/auth.js";
import type { CommunityAliasService } from "../../services/community-alias.service.js";
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
