import { Hono, type Context } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { authOptional, authRequired } from "../../middleware/auth.js";
import type { CommunityAliasService } from "../../services/community-alias.service.js";
import { ok } from "../../http/response.js";
import { createCustomMethodParamSchema, standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";
import type { RateLimitService } from "../../services/rate-limit.service.js";

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

const votingBoardQuerySchema = z.object({
	limit: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 120);
			if (!Number.isFinite(parsed)) return 120;
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

const myCandidatesQuerySchema = z.object({
	songIdentifier: z.string().optional(),
	limit: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 50);
			if (!Number.isFinite(parsed)) return 50;
			return Math.max(1, Math.min(200, Math.trunc(parsed)));
		}),
});

const dailyCountQuerySchema = z.object({
	localDate: z.string().default(new Date().toISOString().slice(0, 10)),
});

const approvedSyncQuerySchema = z.object({
	since: z
		.string()
		.optional()
		.transform((value) => {
			if (!value) {
				return null;
			}
			const parsed = new Date(value);
			return Number.isNaN(parsed.getTime()) ? null : parsed;
		}),
	limit: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 1000);
			if (!Number.isFinite(parsed)) return 1000;
			return Math.max(1, Math.trunc(parsed));
		}),
});

const candidateVoteParamSchema = createCustomMethodParamSchema("candidateId", "vote", z.uuid());

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

communityV1Route.post("/candidates", authRequired, standardValidator("json", submitSchema, validationHook), async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const body = c.req.valid("json");
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

communityV1Route.get(
	"/candidates:votingBoard",
	authOptional,
	standardValidator("query", votingBoardQuerySchema, validationHook),
	async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	const query = c.req.valid("query");
	const rows = await communityAliasService.fetchVotingBoard(auth?.userId ?? null, query.limit, query.offset);
	return ok(c, { rows });
	},
);

communityV1Route.get("/candidates:my", authRequired, standardValidator("query", myCandidatesQuerySchema, validationHook), async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const query = c.req.valid("query");
	const rows = await communityAliasService.fetchMyCandidates(auth.userId, query.limit, query.songIdentifier);
	return ok(c, { rows });
});

communityV1Route.get(
	"/candidates:dailyCount",
	authRequired,
	standardValidator("query", dailyCountQuerySchema, validationHook),
	async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const query = c.req.valid("query");
	const count = await communityAliasService.fetchMyDailyCount(auth.userId, query.localDate);
	return ok(c, { count });
	},
);

communityV1Route.post(
	"/candidates/:candidateId:vote",
	authRequired,
	standardValidator("param", candidateVoteParamSchema, validationHook),
	standardValidator("json", voteSchema, validationHook),
	async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const params = c.req.valid("param");
	const body = c.req.valid("json");
	const result = await communityAliasService.vote(auth.userId, params.candidateId, body.vote);
	return ok(c, result);
	},
);

communityV1Route.get("/aliases:sync", standardValidator("query", approvedSyncQuerySchema, validationHook), async (c) => {
	const communityAliasService = di.resolve<CommunityAliasService>(TOKENS.CommunityAliasService);
	await enforceRateLimit({
		...COMMUNITY_RATE_LIMIT.approvedSyncIp,
		key: resolveClientIp(c),
	});
	const query = c.req.valid("query");
	const rows = await communityAliasService.approvedSync(query.since, query.limit);
	return ok(c, { rows });
});
