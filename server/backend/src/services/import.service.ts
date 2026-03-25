import type { PrismaClient } from "@prisma/client";
import { inject, injectable } from "tsyringe";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { ScoreService } from "./score.service.js";
import { SyncService } from "./sync.service.js";
import { difficultyByLevelIndex, normalizeChartType, normalizeLxnsSongId } from "../utils/compat.js";

type DivingFishRecord = {
  achievements: number;
  title: string;
  type: string;
  level_index: number;
  fc?: string | null;
  fs?: string | null;
  dx_score?: number | null;
};

type DivingFishResponse = {
  username?: string;
  nickname?: string;
  rating?: number;
  plate?: string | null;
  charts?: {
    dx?: DivingFishRecord[];
    sd?: DivingFishRecord[];
  };
  message?: string;
};

type LxnsScore = {
  id: number;
  song_name: string;
  level_index: number;
  type: string;
  achievements: number;
  fc?: string | null;
  fs?: string | null;
  dx_score: number;
  play_time?: string | null;
};

type LxnsResponse = {
  success: boolean;
  code?: number;
  message?: string;
  data?: LxnsScore[];
};

type LxnsPlayerResponse = {
  success: boolean;
  code?: number;
  message?: string;
  data?: {
    name?: string | null;
    rating?: number | null;
    trophy?: {
      name?: string | null;
    } | null;
  } | null;
};

export type TransformedImportRecord = {
  source: "df" | "lxns";
  sheetKey: string | null;
  songIdentifier: string | null;
  songId: number | null;
  title: string;
  chartType: "std" | "dx" | "utage";
  difficulty: string;
  levelIndex: number;
  achievements: number;
  rank: string;
  dxScore: number;
  fc: string | null;
  fs: string | null;
  playTime: string | null;
};

export type TransformedImportResult = {
  provider: "df" | "lxns";
  fetchedCount: number;
  mappedCount: number;
  player: {
    name: string | null;
    rating: number | null;
    plate: string | null;
  } | null;
  records: TransformedImportRecord[];
};

@injectable()
export class ImportService {
  constructor(
    @inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
    @inject(TOKENS.ScoreService) private readonly scoreService: ScoreService,
    @inject(TOKENS.SyncService) private readonly syncService: SyncService
  ) {}

  async transformFromDivingFish(input: { username?: string; qq?: string }): Promise<TransformedImportResult> {
    const requestBody: Record<string, unknown> = {};
    if (input.qq) {
      requestBody.qq = input.qq;
    } else if (input.username) {
      requestBody.username = input.username;
    } else {
      throw new AppError(400, "invalid_request", "username or qq is required.");
    }
    requestBody.b50 = true;

    const response = await fetch("https://www.diving-fish.com/api/maimaidxprober/query/player", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(requestBody)
    });
    const payload = (await response.json()) as DivingFishResponse;
    if (!response.ok || !payload.charts) {
      throw new AppError(400, "df_import_failed", payload.message ?? "Failed to import from Diving Fish.");
    }

    const allRecords = [...(payload.charts.dx ?? []), ...(payload.charts.sd ?? [])];
    const transformed: TransformedImportRecord[] = [];

    for (const record of allRecords) {
      const backendChartType = this.normalizeBackendChartType(record.type);
      const difficulty = difficultyByLevelIndex(record.level_index) ?? "basic";
      const mapped = await this.resolveCatalogMapping({
        songId: null,
        title: record.title,
        chartType: backendChartType,
        difficulty
      });

      transformed.push({
        source: "df",
        sheetKey: mapped.sheetKey,
        songIdentifier: mapped.songIdentifier,
        songId: mapped.songId,
        title: record.title,
        chartType: this.toAppChartType(backendChartType),
        difficulty,
        levelIndex: record.level_index,
        achievements: record.achievements,
        rank: this.rankByAchievements(record.achievements),
        dxScore: record.dx_score ?? 0,
        fc: this.normalizeProgress(record.fc),
        fs: this.normalizeProgress(record.fs),
        playTime: null
      });
    }

    return {
      provider: "df",
      fetchedCount: allRecords.length,
      mappedCount: transformed.filter((item) => item.sheetKey !== null).length,
      player: {
        name: payload.nickname ?? payload.username ?? input.username ?? input.qq ?? null,
        rating: typeof payload.rating === "number" ? payload.rating : null,
        plate: payload.plate ?? null
      },
      records: transformed
    };
  }

  async transformFromLxns(input: { accessToken: string }): Promise<TransformedImportResult> {
    const [scoresResponse, playerResponse] = await Promise.all([
      fetch("https://maimai.lxns.net/api/v0/user/maimai/player/scores", {
        method: "GET",
        headers: {
          Authorization: `Bearer ${input.accessToken}`
        }
      }),
      fetch("https://maimai.lxns.net/api/v0/user/maimai/player", {
        method: "GET",
        headers: {
          Authorization: `Bearer ${input.accessToken}`
        }
      })
    ]);

    const scoresPayload = (await scoresResponse.json()) as LxnsResponse;
    if (!scoresResponse.ok || !scoresPayload.success || !scoresPayload.data) {
      throw new AppError(400, "lxns_import_failed", scoresPayload.message ?? "Failed to import from LXNS.");
    }

    let player: TransformedImportResult["player"] = null;
    if (playerResponse.ok) {
      const playerPayload = (await playerResponse.json()) as LxnsPlayerResponse;
      if (playerPayload.success && playerPayload.data) {
        player = {
          name: playerPayload.data.name ?? null,
          rating: playerPayload.data.rating ?? null,
          plate: playerPayload.data.trophy?.name ?? null
        };
      }
    }

    const transformed: TransformedImportRecord[] = [];
    for (const score of scoresPayload.data) {
      const normalizedSongId = normalizeLxnsSongId(score.id);
      const backendChartType = this.normalizeBackendChartType(score.type);
      const difficulty = difficultyByLevelIndex(score.level_index) ?? "basic";
      const mapped = await this.resolveCatalogMapping({
        songId: normalizedSongId,
        title: score.song_name,
        chartType: backendChartType,
        difficulty
      });

      transformed.push({
        source: "lxns",
        sheetKey: mapped.sheetKey,
        songIdentifier: mapped.songIdentifier,
        songId: mapped.songId ?? normalizedSongId,
        title: score.song_name,
        chartType: this.toAppChartType(backendChartType),
        difficulty,
        levelIndex: score.level_index,
        achievements: score.achievements,
        rank: this.rankByAchievements(score.achievements),
        dxScore: score.dx_score,
        fc: this.normalizeProgress(score.fc),
        fs: this.normalizeProgress(score.fs),
        playTime: score.play_time ?? null
      });
    }

    return {
      provider: "lxns",
      fetchedCount: scoresPayload.data.length,
      mappedCount: transformed.filter((item) => item.sheetKey !== null).length,
      player,
      records: transformed
    };
  }

  async importFromDivingFish(input: {
    userId: string;
    profileId: string;
    username?: string;
    qq?: string;
  }) {
    await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
    const run = await this.prisma.importRun.create({
      data: {
        profileId: input.profileId,
        provider: "df",
        status: "pending"
      }
    });

    try {
      const transformInput: Parameters<ImportService["transformFromDivingFish"]>[0] = {};
      if (input.username !== undefined) {
        transformInput.username = input.username;
      }
      if (input.qq !== undefined) {
        transformInput.qq = input.qq;
      }
      const transformed = await this.transformFromDivingFish(transformInput);
      const mapped = transformed.records.map((record) => {
        const row: {
          songIdentifier?: string;
          songId?: number;
          title: string;
          type: string;
          levelIndex: number;
          difficulty: string;
          achievements: number;
          dxScore: number;
          rank: string;
          fc: string | null;
          fs: string | null;
          sourcePayload: unknown;
        } = {
          title: record.title,
          type: record.chartType,
          levelIndex: record.levelIndex,
          difficulty: record.difficulty,
          achievements: record.achievements,
          dxScore: record.dxScore,
          rank: record.rank,
          fc: record.fc,
          fs: record.fs,
          sourcePayload: record
        };
        if (record.songIdentifier) {
          row.songIdentifier = record.songIdentifier;
        }
        if (record.songId !== null) {
          row.songId = record.songId;
        }
        return row;
      });

      const upsertResult = await this.scoreService.bulkUpsertBestScores(input.profileId, mapped, "df_import");
      const recordResult = await this.scoreService.bulkInsertPlayRecords(
        input.profileId,
        mapped.map((item) => ({
          ...item,
          playTime: new Date()
        })),
        "df_import"
      );

      await this.prisma.importRawPayload.create({
        data: {
          importRunId: run.id,
          payloadType: "df.transformed.records",
          payloadJson: {
            fetchedCount: transformed.fetchedCount,
            mappedCount: transformed.mappedCount,
            records: transformed.records
          }
        }
      });

      await this.prisma.importRun.update({
        where: { id: run.id },
        data: {
          status: "success",
          finishedAt: new Date(),
          summaryJson: {
            fetched: transformed.fetchedCount,
            upserted: upsertResult.applied.length,
            skipped: upsertResult.skipped.length,
            recordsInserted: recordResult.created.length
          }
        }
      });
      await this.syncService.recordEvent({
        userId: input.userId,
        profileId: input.profileId,
        entityType: "import",
        entityId: run.id,
        op: "imported",
        payload: {
          provider: "df",
          fetched: transformed.fetchedCount,
          upserted: upsertResult.applied.length,
          recordsInserted: recordResult.created.length
        }
      });
      await this.syncService.recordEvent({
        userId: input.userId,
        profileId: input.profileId,
        entityType: "best_scores",
        entityId: input.profileId,
        op: "bulk_upsert",
        payload: {
          source: "df_import",
          count: upsertResult.applied.length
        }
      });
      await this.syncService.recordEvent({
        userId: input.userId,
        profileId: input.profileId,
        entityType: "play_records",
        entityId: input.profileId,
        op: "bulk_upsert",
        payload: {
          source: "df_import",
          count: recordResult.created.length
        }
      });

      return {
        importRunId: run.id,
        fetchedCount: transformed.fetchedCount,
        upsertedCount: upsertResult.applied.length,
        skippedCount: upsertResult.skipped.length
      };
    } catch (error) {
      await this.prisma.importRun.update({
        where: { id: run.id },
        data: {
          status: "failed",
          finishedAt: new Date(),
          errorMessage: error instanceof Error ? error.message : "unknown_error"
        }
      });
      throw error;
    }
  }

  async importFromLxns(input: {
    userId: string;
    profileId: string;
    accessToken: string;
  }) {
    await this.scoreService.requireProfileOwnership(input.profileId, input.userId);

    const run = await this.prisma.importRun.create({
      data: {
        profileId: input.profileId,
        provider: "lxns",
        status: "pending"
      }
    });

    try {
      const transformed = await this.transformFromLxns({
        accessToken: input.accessToken
      });
      const mapped = transformed.records.map((record) => {
        const row: {
          songIdentifier?: string;
          songId?: number;
          title: string;
          type: string;
          levelIndex: number;
          difficulty: string;
          achievements: number;
          dxScore: number;
          rank: string;
          fc: string | null;
          fs: string | null;
          sourcePayload: unknown;
        } = {
          title: record.title,
          type: record.chartType,
          levelIndex: record.levelIndex,
          difficulty: record.difficulty,
          achievements: record.achievements,
          dxScore: record.dxScore,
          rank: record.rank,
          fc: record.fc,
          fs: record.fs,
          sourcePayload: record
        };
        if (record.songIdentifier) {
          row.songIdentifier = record.songIdentifier;
        }
        if (record.songId !== null) {
          row.songId = record.songId;
        }
        return row;
      });

      const upsertResult = await this.scoreService.bulkUpsertBestScores(input.profileId, mapped, "lxns_import");
      const recordResult = await this.scoreService.bulkInsertPlayRecords(
        input.profileId,
        mapped.map((item, index) => ({
          ...item,
          playTime: transformed.records[index]?.playTime ?? new Date()
        })),
        "lxns_import"
      );

      await this.prisma.importRawPayload.create({
        data: {
          importRunId: run.id,
          payloadType: "lxns.transformed.records",
          payloadJson: {
            fetchedCount: transformed.fetchedCount,
            mappedCount: transformed.mappedCount,
            records: transformed.records
          }
        }
      });

      await this.prisma.importRun.update({
        where: { id: run.id },
        data: {
          status: "success",
          finishedAt: new Date(),
          summaryJson: {
            fetched: transformed.fetchedCount,
            upserted: upsertResult.applied.length,
            skipped: upsertResult.skipped.length,
            recordsInserted: recordResult.created.length
          }
        }
      });
      await this.syncService.recordEvent({
        userId: input.userId,
        profileId: input.profileId,
        entityType: "import",
        entityId: run.id,
        op: "imported",
        payload: {
          provider: "lxns",
          fetched: transformed.fetchedCount,
          upserted: upsertResult.applied.length,
          recordsInserted: recordResult.created.length
        }
      });
      await this.syncService.recordEvent({
        userId: input.userId,
        profileId: input.profileId,
        entityType: "best_scores",
        entityId: input.profileId,
        op: "bulk_upsert",
        payload: {
          source: "lxns_import",
          count: upsertResult.applied.length
        }
      });
      await this.syncService.recordEvent({
        userId: input.userId,
        profileId: input.profileId,
        entityType: "play_records",
        entityId: input.profileId,
        op: "bulk_upsert",
        payload: {
          source: "lxns_import",
          count: recordResult.created.length
        }
      });

      return {
        importRunId: run.id,
        fetchedCount: transformed.fetchedCount,
        upsertedCount: upsertResult.applied.length,
        skippedCount: upsertResult.skipped.length
      };
    } catch (error) {
      await this.prisma.importRun.update({
        where: { id: run.id },
        data: {
          status: "failed",
          finishedAt: new Date(),
          errorMessage: error instanceof Error ? error.message : "unknown_error"
        }
      });
      throw error;
    }
  }

  private normalizeBackendChartType(input: string): "standard" | "dx" | "utage" {
    const normalized = normalizeChartType(input) ?? "standard";
    if (normalized === "dx" || normalized === "utage") {
      return normalized;
    }
    return "standard";
  }

  private toAppChartType(input: "standard" | "dx" | "utage"): "std" | "dx" | "utage" {
    if (input === "standard") {
      return "std";
    }
    return input;
  }

  private normalizeProgress(value: string | null | undefined): string | null {
    if (!value) {
      return null;
    }
    const normalized = value.trim();
    if (!normalized) {
      return null;
    }
    return normalized;
  }

  private async resolveCatalogMapping(input: {
    songId: number | null;
    title: string;
    chartType: "standard" | "dx" | "utage";
    difficulty: string;
  }): Promise<{ songIdentifier: string | null; songId: number | null; sheetKey: string | null }> {
    let sheet = null as
      | {
          songIdentifier: string;
          chartType: string;
          difficulty: string;
          songId: number;
          song: { songId: number } | null;
        }
      | null;

    if (input.songId && input.songId > 0) {
      sheet = await this.prisma.sheet.findFirst({
        where: {
          chartType: input.chartType,
          difficulty: input.difficulty,
          OR: [{ songId: input.songId }, { song: { songId: input.songId } }]
        },
        select: {
          songIdentifier: true,
          chartType: true,
          difficulty: true,
          songId: true,
          song: {
            select: {
              songId: true
            }
          }
        }
      });
    }

    if (!sheet) {
      sheet = await this.prisma.sheet.findFirst({
        where: {
          chartType: input.chartType,
          difficulty: input.difficulty,
          song: {
            title: input.title
          }
        },
        select: {
          songIdentifier: true,
          chartType: true,
          difficulty: true,
          songId: true,
          song: {
            select: {
              songId: true
            }
          }
        }
      });
    }

    if (!sheet) {
      return {
        songIdentifier: null,
        songId: input.songId,
        sheetKey: null
      };
    }

    const appType = this.toAppChartType(this.normalizeBackendChartType(sheet.chartType));
    const resolvedSongId = sheet.song?.songId && sheet.song.songId > 0 ? sheet.song.songId : sheet.songId;
    return {
      songIdentifier: sheet.songIdentifier,
      songId: resolvedSongId > 0 ? resolvedSongId : input.songId,
      sheetKey: `${sheet.songIdentifier}_${appType}_${sheet.difficulty}`
    };
  }

  private rankByAchievements(rate: number): string {
    if (rate >= 100.5) return "SSS+";
    if (rate >= 100.0) return "SSS";
    if (rate >= 99.5) return "SS+";
    if (rate >= 99.0) return "SS";
    if (rate >= 98.0) return "S+";
    if (rate >= 97.0) return "S";
    if (rate >= 94.0) return "AAA";
    if (rate >= 90.0) return "AA";
    if (rate >= 80.0) return "A";
    if (rate >= 75.0) return "BBB";
    if (rate >= 70.0) return "BB";
    if (rate >= 60.0) return "B";
    if (rate >= 50.0) return "C";
    return "D";
  }
}
