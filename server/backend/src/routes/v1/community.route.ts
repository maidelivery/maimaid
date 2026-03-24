import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { authOptional, authRequired } from "../../middleware/auth.js";
import type { CommunityAliasService } from "../../services/community-alias.service.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

const submitSchema = z.object({
  songIdentifier: z.string().min(1),
  aliasText: z.string().min(1).max(64),
  deviceLocalDate: z.string().default(new Date().toISOString().slice(0, 10)),
  tzOffsetMinutes: z.number().int().default(0)
});

const voteSchema = z.object({
  candidateId: z.string().uuid(),
  vote: z.number().int().refine((value) => value === -1 || value === 1)
});

export const communityV1Route = new Hono<AppEnv>();

communityV1Route.post("/aliases/submit", authRequired, async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = submitSchema.parse(await c.req.json());
  const result = await communityAliasService.submitAlias({
    userId: auth.userId,
    songIdentifier: body.songIdentifier,
    aliasText: body.aliasText,
    deviceLocalDate: body.deviceLocalDate,
    tzOffsetMinutes: body.tzOffsetMinutes
  });
  return ok(c, result);
});

communityV1Route.get("/aliases/voting-board", authOptional, async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const auth = c.get("auth");
  const limit = Number(c.req.query("limit") ?? 120);
  const offset = Number(c.req.query("offset") ?? 0);
  const rows = await communityAliasService.fetchVotingBoard(auth?.userId ?? null, limit, offset);
  return ok(c, { rows });
});

communityV1Route.get("/aliases/my-candidates", authRequired, async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const songIdentifier = c.req.query("songIdentifier");
  if (!songIdentifier) {
    return ok(c, { rows: [] });
  }
  const limit = Number(c.req.query("limit") ?? 50);
  const rows = await communityAliasService.fetchMySongCandidates(auth.userId, songIdentifier, limit);
  return ok(c, { rows });
});

communityV1Route.get("/aliases/daily-count", authRequired, async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const localDate = c.req.query("localDate") ?? new Date().toISOString().slice(0, 10);
  const count = await communityAliasService.fetchMyDailyCount(auth.userId, localDate);
  return ok(c, { count });
});

communityV1Route.post("/aliases/vote", authRequired, async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = voteSchema.parse(await c.req.json());
  const result = await communityAliasService.vote(auth.userId, body.candidateId, body.vote);
  return ok(c, result);
});

communityV1Route.get("/aliases/approved-sync", async (c) => {
  const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
  const sinceQuery = c.req.query("since");
  const since = sinceQuery ? new Date(sinceQuery) : null;
  const limit = Number(c.req.query("limit") ?? 1000);
  const rows = await communityAliasService.approvedSync(
    since && !Number.isNaN(since.getTime()) ? since : null,
    limit
  );
  return ok(c, { rows });
});
