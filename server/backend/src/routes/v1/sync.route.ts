import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";
import type { SyncService } from "../../services/sync.service.js";
import type { ProfileService } from "../../services/profile.service.js";
import type { ScoreService } from "../../services/score.service.js";
import type { PrismaClient } from "@prisma/client";

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

const pushSchema = z.object({
  idempotencyKey: z.string().min(8),
  profileUpserts: z
    .array(
      z.object({
        profileId: z.string().uuid(),
        name: z.string().min(1).max(40),
        server: z.enum(["jp", "intl", "usa", "cn"]).default("jp"),
        isActive: z.boolean().optional(),
        playerRating: z.number().int().nonnegative().optional(),
        plate: z.string().nullable().optional(),
        avatarUrl: z.string().url().nullable().optional(),
        dfUsername: z.string().optional(),
        dfImportToken: z.string().optional(),
        lxnsRefreshToken: z.string().optional(),
        b35Count: z.number().int().positive().optional(),
        b15Count: z.number().int().positive().optional(),
        b35RecLimit: z.number().int().positive().optional(),
        b15RecLimit: z.number().int().positive().optional(),
        createdAt: z.coerce.date().optional(),
        clientUpdatedAt: z.coerce.date().optional()
      })
    )
    .default([]),
  scoreUpserts: z
    .array(
      z.object({
        profileId: z.string().uuid(),
        scores: z.array(scoreEntrySchema).min(1)
      })
    )
    .default([]),
  playRecordUpserts: z
    .array(
      z.object({
        profileId: z.string().uuid(),
        records: z.array(playRecordSchema).min(1)
      })
    )
    .default([])
});

const pullQuerySchema = z.object({
  sinceRevision: z
    .string()
    .optional()
    .transform((value) => {
      if (!value) return 0n;
      if (!/^\d+$/.test(value)) return 0n;
      return BigInt(value);
    }),
  profileId: z.string().uuid().optional(),
  limit: z
    .string()
    .optional()
    .transform((value) => {
      const parsed = Number(value ?? 200);
      if (!Number.isFinite(parsed)) return 200;
      return Math.max(1, Math.min(500, Math.trunc(parsed)));
    })
});

type ScoreEntryBody = z.infer<typeof scoreEntrySchema>;
type PlayRecordBody = z.infer<typeof playRecordSchema>;

const mapScoresForUpsert = (scores: ScoreEntryBody[]) =>
  scores.map((item) => {
    const mapped: {
      sheetId?: bigint;
      songIdentifier?: string;
      songId?: number;
      title?: string;
      chartType?: string;
      type?: string;
      difficulty?: string;
      levelIndex?: number;
      achievements: number;
      rank?: string;
      dxScore?: number;
      fc?: string | null;
      fs?: string | null;
      achievedAt?: string;
    } = {
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

const mapPlayRecords = (records: PlayRecordBody[]) =>
  records.map((item) => {
    const mapped: {
      sheetId?: bigint;
      songIdentifier?: string;
      songId?: number;
      title?: string;
      chartType?: string;
      type?: string;
      difficulty?: string;
      levelIndex?: number;
      achievements: number;
      rank?: string;
      dxScore?: number;
      fc?: string | null;
      fs?: string | null;
      playTime?: string;
    } = {
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

export const syncV1Route = new Hono<AppEnv>();
syncV1Route.use("*", authRequired);

syncV1Route.post("/push", async (c) => {
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = pushSchema.parse(await c.req.json());
  const syncService = di.resolve<SyncService>(TOKENS.SyncService);
  const profileService = di.resolve<ProfileService>(TOKENS.ProfileService);
  const scoreService = di.resolve<ScoreService>(TOKENS.ScoreService);
  const prisma = di.resolve<PrismaClient>(TOKENS.Prisma);

  const existing = await syncService.findMutation(auth.userId, body.idempotencyKey);
  if (existing) {
    return ok(c, existing.resultJson);
  }

  const result: {
    applied: {
      profiles: number;
      scores: number;
      records: number;
    };
    conflicts: Array<{
      profileId: string;
      reason: string;
      serverProfile: unknown;
    }>;
    latestRevision: string;
  } = {
    applied: {
      profiles: 0,
      scores: 0,
      records: 0
    },
    conflicts: [],
    latestRevision: "0"
  };

  for (const item of body.profileUpserts) {
    const existingProfile = await prisma.profile.findUnique({
      where: { id: item.profileId }
    });
    if (existingProfile && existingProfile.userId !== auth.userId) {
      result.conflicts.push({
        profileId: item.profileId,
        reason: "forbidden",
        serverProfile: null
      });
      continue;
    }
    if (
      existingProfile &&
      item.clientUpdatedAt &&
      existingProfile.updatedAt.getTime() > item.clientUpdatedAt.getTime()
    ) {
      result.conflicts.push({
        profileId: item.profileId,
        reason: "server_newer",
        serverProfile: existingProfile
      });
      continue;
    }

    const payload: Parameters<ProfileService["upsertByClientId"]>[2] = {
      name: item.name,
      server: item.server
    };
    if (item.isActive !== undefined) payload.isActive = item.isActive;
    if (item.playerRating !== undefined) payload.playerRating = item.playerRating;
    if (item.plate !== undefined) payload.plate = item.plate;
    if (item.avatarUrl !== undefined) payload.avatarUrl = item.avatarUrl;
    if (item.dfUsername !== undefined) payload.dfUsername = item.dfUsername;
    if (item.dfImportToken !== undefined) payload.dfImportToken = item.dfImportToken;
    if (item.lxnsRefreshToken !== undefined) payload.lxnsRefreshToken = item.lxnsRefreshToken;
    if (item.b35Count !== undefined) payload.b35Count = item.b35Count;
    if (item.b15Count !== undefined) payload.b15Count = item.b15Count;
    if (item.b35RecLimit !== undefined) payload.b35RecLimit = item.b35RecLimit;
    if (item.b15RecLimit !== undefined) payload.b15RecLimit = item.b15RecLimit;
    if (item.createdAt !== undefined) payload.createdAt = item.createdAt;

    const profile = await profileService.upsertByClientId(auth.userId, item.profileId, payload);
    await syncService.recordEvent({
      userId: auth.userId,
      profileId: profile.id,
      entityType: "profile",
      entityId: profile.id,
      op: "upsert",
      payload: {
        updatedAt: profile.updatedAt.toISOString()
      }
    });
    if (item.avatarUrl !== undefined) {
      await syncService.recordEvent({
        userId: auth.userId,
        profileId: profile.id,
        entityType: "avatar",
        entityId: profile.id,
        op: "upsert",
        payload: {
          avatarUrl: item.avatarUrl
        }
      });
    }
    result.applied.profiles += 1;
  }

  for (const scoreSet of body.scoreUpserts) {
    await scoreService.requireProfileOwnership(scoreSet.profileId, auth.userId);
    const mapped = mapScoresForUpsert(scoreSet.scores);
    const response = await scoreService.bulkUpsertBestScores(scoreSet.profileId, mapped, "sync_push");
    result.applied.scores += response.applied.length;
    if (response.applied.length > 0) {
      await syncService.recordEvent({
        userId: auth.userId,
        profileId: scoreSet.profileId,
        entityType: "best_scores",
        entityId: scoreSet.profileId,
        op: "bulk_upsert",
        payload: {
          count: response.applied.length
        }
      });
    }
  }

  for (const recordSet of body.playRecordUpserts) {
    await scoreService.requireProfileOwnership(recordSet.profileId, auth.userId);
    const mapped = mapPlayRecords(recordSet.records);
    const response = await scoreService.bulkInsertPlayRecords(recordSet.profileId, mapped, "sync_push");
    result.applied.records += response.created.length;
    if (response.created.length > 0) {
      await syncService.recordEvent({
        userId: auth.userId,
        profileId: recordSet.profileId,
        entityType: "play_records",
        entityId: recordSet.profileId,
        op: "bulk_upsert",
        payload: {
          count: response.created.length
        }
      });
    }
  }

  const latestEvent = await prisma.syncEvent.findFirst({
    where: { userId: auth.userId },
    orderBy: { revision: "desc" }
  });
  result.latestRevision = latestEvent ? latestEvent.revision.toString() : "0";

  await syncService.saveMutationResult(auth.userId, body.idempotencyKey, result as unknown as Record<string, unknown>);
  return ok(c, result);
});

syncV1Route.get("/pull", async (c) => {
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const syncService = di.resolve<SyncService>(TOKENS.SyncService);
  const query = pullQuerySchema.parse(c.req.query());
  const listInput: Parameters<SyncService["listEvents"]>[0] = {
    userId: auth.userId,
    sinceRevision: query.sinceRevision,
    limit: query.limit
  };
  if (query.profileId) {
    listInput.profileId = query.profileId;
  }
  const events = await syncService.listEvents(listInput);

  const profileIds = new Set<string>();
  if (query.profileId) {
    profileIds.add(query.profileId);
  }
  for (const event of events) {
    if (event.profileId) {
      profileIds.add(event.profileId);
    }
  }

  const snapshot = await syncService.buildSnapshot(auth.userId, Array.from(profileIds));
  const latestRevision = events.length > 0 ? events[events.length - 1]!.revision.toString() : query.sinceRevision.toString();

  return ok(c, {
    events,
    latestRevision,
    snapshot
  });
});
