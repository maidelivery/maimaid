import { Hono, type Context } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { authOptional, authRequired } from "../../middleware/auth.js";
import type { CommunityAliasService } from "../../services/community-alias.service.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";
import type { RateLimitService } from "../../services/rate-limit.service.js";
import { readCustomMethodParam } from "../../utils/custom-method.js";

const submitSchema = z.object({
	songIdentifier: z.string().min(1),
	aliasText: z.string().min(1).max(64),
	deviceLocalDate: z.string().default(new Date().toISOString().slice(0, 10)),
	tzOffsetMinutes: z.number().int().default(0),
});

const voteSchema = z.object({
	vote: z
		.number()
		.int()
		.refine((value) => value === -1 || value === 1),
});

export const communityV1Route = new Hono<AppEnv>();
const COMMUNITY_RATE_LIMIT = {
	approvedSyncIp: { bucket: "community.approved_sync.ip", limit: 120, windowSeconds: 60 },
} as const;

const resolveClientIp = (c: Context<AppEnv>): string => {
	const cfConnectingIp = c.req.header("cf-connecting-ip")?.trim();
	if (cfConnectingIp) {
		return cfConnectingIp;
	}

	const xForwardedFor = c.req.header("x-forwarded-for");
	if (xForwardedFor) {
		const first = xForwardedFor
			.split(",")
			.map((item) => item.trim())
			.find((item) => item.length > 0);
		if (first) {
			return first;
		}
	}

	const realIp = c.req.header("x-real-ip")?.trim();
	if (realIp) {
		return realIp;
	}

	return "unknown";
};

const enforceRateLimit = async (input: { bucket: string; key: string; limit: number; windowSeconds: number }) => {
	const rateLimitService = di.resolve<RateLimitService>(TOKENS.RateLimitService);
	await rateLimitService.consume({
		bucket: input.bucket,
		key: input.key,
		limit: input.limit,
		windowSeconds: input.windowSeconds,
	});
};

communityV1Route.post("/candidates", authRequired, async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const body = submitSchema.parse(await c.req.json());
	const result = await communityAliasService.submitAlias({
		userId: auth.userId,
		isAdmin: auth.isAdmin,
		songIdentifier: body.songIdentifier,
		aliasText: body.aliasText,
		deviceLocalDate: body.deviceLocalDate,
		tzOffsetMinutes: body.tzOffsetMinutes,
	});
	return ok(c, result);
});

communityV1Route.get("/candidates:votingBoard", authOptional, async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	const limit = Number(c.req.query("limit") ?? 120);
	const offset = Number(c.req.query("offset") ?? 0);
	const rows = await communityAliasService.fetchVotingBoard(auth?.userId ?? null, limit, offset);
	return ok(c, { rows });
});

communityV1Route.get("/candidates:my", authRequired, async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const songIdentifier = c.req.query("songIdentifier");
	const limit = Number(c.req.query("limit") ?? 50);
	const rows = await communityAliasService.fetchMyCandidates(auth.userId, limit, songIdentifier);
	return ok(c, { rows });
});

communityV1Route.get("/candidates:dailyCount", authRequired, async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const localDate = c.req.query("localDate") ?? new Date().toISOString().slice(0, 10);
	const count = await communityAliasService.fetchMyDailyCount(auth.userId, localDate);
	return ok(c, { count });
});

communityV1Route.post("/candidates/:candidateId:vote", authRequired, async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const candidateId = z.string().uuid().parse(readCustomMethodParam(c, "candidateId", "vote"));
	const body = voteSchema.parse(await c.req.json());
	const result = await communityAliasService.vote(auth.userId, candidateId, body.vote);
	return ok(c, result);
});

communityV1Route.get("/aliases:sync", async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	await enforceRateLimit({
		...COMMUNITY_RATE_LIMIT.approvedSyncIp,
		key: resolveClientIp(c),
	});
	const sinceQuery = c.req.query("since");
	const since = sinceQuery ? new Date(sinceQuery) : null;
	const limit = Number(c.req.query("limit") ?? 1000);
	const rows = await communityAliasService.approvedSync(since && !Number.isNaN(since.getTime()) ? since : null, limit);
	return ok(c, { rows });
});
