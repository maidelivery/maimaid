import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { authRequired } from "../../middleware/auth.js";
import type { ScoreService } from "../../services/score.service.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

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
  achievedAt: z.string().optional()
});

const playRecordSchema = scoreEntrySchema.extend({
  playTime: z.string().optional()
});

const bulkScoreSchema = z.object({
  profileId: z.string().uuid(),
  scores: z.array(scoreEntrySchema).min(1)
});

const overwriteScoreSchema = z.object({
  profileId: z.string().uuid(),
  scores: z.array(scoreEntrySchema)
});

const bulkRecordSchema = z.object({
  profileId: z.string().uuid(),
  records: z.array(playRecordSchema).min(1)
});

const overwriteRecordSchema = z.object({
  profileId: z.string().uuid(),
  records: z.array(playRecordSchema)
});

export const scoresV1Route = new Hono<AppEnv>();

type ScoreEntryBody = z.infer<typeof scoreEntrySchema>;
type PlayRecordBody = z.infer<typeof playRecordSchema>;

const mapScoresForUpsert = (
  scores: ScoreEntryBody[]
): Parameters<ScoreService["bulkUpsertBestScores"]>[1] =>
  scores.map((item): Parameters<ScoreService["bulkUpsertBestScores"]>[1][number] => {
    const mapped: Parameters<ScoreService["bulkUpsertBestScores"]>[1][number] = {
      achievements: item.achievements
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

const mapPlayRecords = (
  records: PlayRecordBody[]
): Parameters<ScoreService["bulkInsertPlayRecords"]>[1] =>
  records.map((item): Parameters<ScoreService["bulkInsertPlayRecords"]>[1][number] => {
    const mapped: Parameters<ScoreService["bulkInsertPlayRecords"]>[1][number] = {
      achievements: item.achievements
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

scoresV1Route.use("*", authRequired);

scoresV1Route.get("/", async (c) => {
  const scoreService = di.resolve<ScoreService>(TOKENS.ScoreService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const profileId = c.req.query("profileId");
  if (!profileId) {
    return ok(c, { scores: [] });
  }
  await scoreService.requireProfileOwnership(profileId, auth.userId);
  const scores = await scoreService.listBestScores(profileId);
  return ok(c, { scores });
});

scoresV1Route.post("/bulk-upsert", async (c) => {
  const scoreService = di.resolve<ScoreService>(TOKENS.ScoreService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = bulkScoreSchema.parse(await c.req.json());
  await scoreService.requireProfileOwnership(body.profileId, auth.userId);
  const scores = mapScoresForUpsert(body.scores);
  const result = await scoreService.bulkUpsertBestScores(body.profileId, scores, "canonical");
  return ok(c, result);
});

scoresV1Route.post("/overwrite", async (c) => {
  const scoreService = di.resolve<ScoreService>(TOKENS.ScoreService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = overwriteScoreSchema.parse(await c.req.json());
  await scoreService.requireProfileOwnership(body.profileId, auth.userId);
  const scores = mapScoresForUpsert(body.scores);
  const result = await scoreService.replaceBestScores(body.profileId, scores, "overwrite");
  return ok(c, result);
});

scoresV1Route.get("/play-records", async (c) => {
  const scoreService = di.resolve<ScoreService>(TOKENS.ScoreService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const profileId = c.req.query("profileId");
  const limit = Number(c.req.query("limit") ?? 100);
  if (!profileId) {
    return ok(c, { records: [] });
  }
  await scoreService.requireProfileOwnership(profileId, auth.userId);
  const records = await scoreService.listPlayRecords(profileId, Math.max(1, Math.min(limit, 5000)));
  return ok(c, { records });
});

scoresV1Route.post("/play-records/bulk-upsert", async (c) => {
  const scoreService = di.resolve<ScoreService>(TOKENS.ScoreService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = bulkRecordSchema.parse(await c.req.json());
  await scoreService.requireProfileOwnership(body.profileId, auth.userId);
  const records = mapPlayRecords(body.records);
  const result = await scoreService.bulkInsertPlayRecords(body.profileId, records, "canonical");
  return ok(c, result);
});

scoresV1Route.post("/play-records/overwrite", async (c) => {
  const scoreService = di.resolve<ScoreService>(TOKENS.ScoreService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = overwriteRecordSchema.parse(await c.req.json());
  await scoreService.requireProfileOwnership(body.profileId, auth.userId);
  const records = mapPlayRecords(body.records);
  const result = await scoreService.replacePlayRecords(body.profileId, records, "overwrite");
  return ok(c, result);
});
