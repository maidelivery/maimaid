import { Hono } from "hono";
import { z } from "zod";
import { authRequired } from "../../middleware/auth.js";
import { ScoreService } from "../../services/score.service.js";
import { SyncService } from "../../services/sync.service.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";

const BULK_ARRAY_MAX = 10_000;

const scoreEntrySchema = z.object({
	sheetId: z
		.union([z.bigint(), z.number().int(), z.string().regex(/^\d+$/)])
		.transform((value) => (typeof value === "bigint" ? value : BigInt(value)))
		.optional(),
	songIdentifier: z.string().optional(),
	songId: z.number().int().optional(),
	title: z.string().optional(),
	chartType: z.string().optional(),
	type: z.string().optional(),
	difficulty: z.string().optional(),
	levelIndex: z.number().int().optional(),
	achievements: z.number(),
	rank: z.string().optional(),
	dxScore: z.number().int().optional(),
	fc: z.string().nullable().optional(),
	fs: z.string().nullable().optional(),
	achievedAt: z.string().optional(),
});

const playRecordSchema = scoreEntrySchema.extend({
	playTime: z.string().optional(),
});

const bulkScoreSchema = z.object({
	profileId: z.uuid(),
	scores: z.array(scoreEntrySchema).min(1).max(BULK_ARRAY_MAX),
});

const overwriteScoreSchema = z.object({
	profileId: z.uuid(),
	scores: z.array(scoreEntrySchema),
});

const bulkRecordSchema = z.object({
	profileId: z.uuid(),
	records: z.array(playRecordSchema).min(1).max(BULK_ARRAY_MAX),
});

const overwriteRecordSchema = z.object({
	profileId: z.uuid(),
	records: z.array(playRecordSchema),
});

const patchScoreSchema = z
	.object({
		achievements: z.number().optional(),
		rank: z.string().optional(),
		dxScore: z.number().int().optional(),
		fc: z.string().nullable().optional(),
		fs: z.string().nullable().optional(),
		achievedAt: z.coerce.date().optional(),
	})
	.refine((value) => Object.keys(value).length > 0, "No field to update.");

const profileScoresQuerySchema = z.object({
	profileId: z.uuid().optional(),
});

const playRecordsQuerySchema = z.object({
	profileId: z.uuid().optional(),
	limit: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 100);
			if (!Number.isFinite(parsed)) return 100;
			return Math.max(1, Math.min(5000, Math.trunc(parsed)));
		}),
});

const scoreIdParamSchema = z.object({
	scoreId: z.uuid(),
});

const recordIdParamSchema = z.object({
	recordId: z.uuid(),
});

export const scoresV1Route = new Hono<AppEnv>();

type ScoreEntryBody = z.infer<typeof scoreEntrySchema>;
type PlayRecordBody = z.infer<typeof playRecordSchema>;

const mapScoresForUpsert = (scores: ScoreEntryBody[]): Parameters<ScoreService["bulkUpsertBestScores"]>[1] =>
	scores.map((item): Parameters<ScoreService["bulkUpsertBestScores"]>[1][number] => {
		const mapped: Parameters<ScoreService["bulkUpsertBestScores"]>[1][number] = {
			achievements: item.achievements,
		};
		if (item.sheetId !== undefined) mapped.sheetId = item.sheetId;
		if (item.songIdentifier !== undefined) mapped.songIdentifier = item.songIdentifier;
		if (item.songId !== undefined) mapped.songId = item.songId;
		if (item.title !== undefined) mapped.title = item.title;
		if (item.chartType !== undefined) mapped.chartType = item.chartType;
		if (item.type !== undefined) mapped.type = item.type;
		if (item.difficulty !== undefined) mapped.difficulty = item.difficulty;
		if (item.levelIndex !== undefined) mapped.levelIndex = item.levelIndex;
		if (item.rank !== undefined) mapped.rank = item.rank;
		if (item.dxScore !== undefined) mapped.dxScore = item.dxScore;
		if (item.fc !== undefined) mapped.fc = item.fc;
		if (item.fs !== undefined) mapped.fs = item.fs;
		if (item.achievedAt !== undefined) mapped.achievedAt = item.achievedAt;
		return mapped;
	});

const mapPlayRecords = (records: PlayRecordBody[]): Parameters<ScoreService["bulkInsertPlayRecords"]>[1] =>
	records.map((item): Parameters<ScoreService["bulkInsertPlayRecords"]>[1][number] => {
		const mapped: Parameters<ScoreService["bulkInsertPlayRecords"]>[1][number] = {
			achievements: item.achievements,
		};
		if (item.sheetId !== undefined) mapped.sheetId = item.sheetId;
		if (item.songIdentifier !== undefined) mapped.songIdentifier = item.songIdentifier;
		if (item.songId !== undefined) mapped.songId = item.songId;
		if (item.title !== undefined) mapped.title = item.title;
		if (item.chartType !== undefined) mapped.chartType = item.chartType;
		if (item.type !== undefined) mapped.type = item.type;
		if (item.difficulty !== undefined) mapped.difficulty = item.difficulty;
		if (item.levelIndex !== undefined) mapped.levelIndex = item.levelIndex;
		if (item.rank !== undefined) mapped.rank = item.rank;
		if (item.dxScore !== undefined) mapped.dxScore = item.dxScore;
		if (item.fc !== undefined) mapped.fc = item.fc;
		if (item.fs !== undefined) mapped.fs = item.fs;
		if (item.playTime !== undefined) mapped.playTime = item.playTime;
		return mapped;
	});

scoresV1Route.get("/scores", authRequired, standardValidator("query", profileScoresQuerySchema, validationHook), async (c) => {
	const scoreService = c.var.resolve(ScoreService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const query = c.req.valid("query");
	if (!query.profileId) {
		return ok(c, { scores: [] });
	}
	await scoreService.requireProfileOwnership(query.profileId, auth.userId);
	const scores = await scoreService.listBestScores(query.profileId);
	return ok(c, { scores });
});

scoresV1Route.patch(
	"/scores/:scoreId",
	authRequired,
	standardValidator("param", scoreIdParamSchema, validationHook),
	standardValidator("json", patchScoreSchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const params = c.req.valid("param");
		const body = c.req.valid("json");
		const patch: Parameters<ScoreService["updateBestScore"]>[2] = {};
		if (body.achievements !== undefined) patch.achievements = body.achievements;
		if (body.rank !== undefined) patch.rank = body.rank;
		if (body.dxScore !== undefined) patch.dxScore = body.dxScore;
		if (body.fc !== undefined) patch.fc = body.fc;
		if (body.fs !== undefined) patch.fs = body.fs;
		if (body.achievedAt !== undefined) patch.achievedAt = body.achievedAt;
		const updated = await scoreService.updateBestScore(params.scoreId, auth.userId, patch);
		await syncService.recordEvent({
			userId: auth.userId,
			profileId: updated.profileId,
			entityType: "best_scores",
			entityId: updated.id,
			op: "upsert",
			payload: {
				profileId: updated.profileId,
			},
		});
		return ok(c, { score: updated });
	},
);

scoresV1Route.delete(
	"/scores/:scoreId",
	authRequired,
	standardValidator("param", scoreIdParamSchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const params = c.req.valid("param");
		const result = await scoreService.deleteBestScore(params.scoreId, auth.userId);
		await syncService.recordEvent({
			userId: auth.userId,
			profileId: result.profileId,
			entityType: "best_scores",
			entityId: params.scoreId,
			op: "delete",
			payload: {
				profileId: result.profileId,
			},
		});
		return ok(c, result);
	},
);

scoresV1Route.post(
	"/scores:batchUpsert",
	authRequired,
	standardValidator("json", bulkScoreSchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const body = c.req.valid("json");
		await scoreService.requireProfileOwnership(body.profileId, auth.userId);
		const scores = mapScoresForUpsert(body.scores);
		const result = await scoreService.bulkUpsertBestScores(body.profileId, scores, "canonical");
		if (result.applied.length > 0) {
			await syncService.recordEvent({
				userId: auth.userId,
				profileId: body.profileId,
				entityType: "best_scores",
				entityId: body.profileId,
				op: "bulk_upsert",
				payload: {
					count: result.applied.length,
				},
			});
		}
		return ok(c, result);
	},
);

scoresV1Route.post(
	"/scores:replace",
	authRequired,
	standardValidator("json", overwriteScoreSchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const body = c.req.valid("json");
		await scoreService.requireProfileOwnership(body.profileId, auth.userId);
		const scores = mapScoresForUpsert(body.scores);
		const result = await scoreService.replaceBestScores(body.profileId, scores, "overwrite");
		await syncService.recordEvent({
			userId: auth.userId,
			profileId: body.profileId,
			entityType: "best_scores",
			entityId: body.profileId,
			op: "replace",
			payload: {
				count: result.applied.length,
				deletedCount: result.deletedCount,
			},
		});
		return ok(c, result);
	},
);

scoresV1Route.get(
	"/play-records",
	authRequired,
	standardValidator("query", playRecordsQuerySchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const query = c.req.valid("query");
		if (!query.profileId) {
			return ok(c, { records: [] });
		}
		await scoreService.requireProfileOwnership(query.profileId, auth.userId);
		const records = await scoreService.listPlayRecords(query.profileId, query.limit);
		return ok(c, { records });
	},
);

scoresV1Route.post(
	"/play-records:batchUpsert",
	authRequired,
	standardValidator("json", bulkRecordSchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const body = c.req.valid("json");
		await scoreService.requireProfileOwnership(body.profileId, auth.userId);
		const records = mapPlayRecords(body.records);
		const result = await scoreService.bulkInsertPlayRecords(body.profileId, records, "canonical");
		if (result.created.length > 0) {
			await syncService.recordEvent({
				userId: auth.userId,
				profileId: body.profileId,
				entityType: "play_records",
				entityId: body.profileId,
				op: "bulk_upsert",
				payload: {
					count: result.created.length,
				},
			});
		}
		return ok(c, result);
	},
);

scoresV1Route.post(
	"/play-records:replace",
	authRequired,
	standardValidator("json", overwriteRecordSchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const body = c.req.valid("json");
		await scoreService.requireProfileOwnership(body.profileId, auth.userId);
		const records = mapPlayRecords(body.records);
		const result = await scoreService.replacePlayRecords(body.profileId, records, "overwrite");
		await syncService.recordEvent({
			userId: auth.userId,
			profileId: body.profileId,
			entityType: "play_records",
			entityId: body.profileId,
			op: "replace",
			payload: {
				count: result.created.length,
				deletedCount: result.deletedCount,
			},
		});
		return ok(c, result);
	},
);

scoresV1Route.delete(
	"/play-records/:recordId",
	authRequired,
	standardValidator("param", recordIdParamSchema, validationHook),
	async (c) => {
		const scoreService = c.var.resolve(ScoreService);
		const syncService = c.var.resolve(SyncService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const params = c.req.valid("param");
		const result = await scoreService.deletePlayRecord(params.recordId, auth.userId);
		await syncService.recordEvent({
			userId: auth.userId,
			profileId: result.profileId,
			entityType: "play_records",
			entityId: params.recordId,
			op: "delete",
			payload: {
				profileId: result.profileId,
			},
		});
		return ok(c, result);
	},
);
