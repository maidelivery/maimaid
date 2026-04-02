import { inject, injectable } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { difficultyByLevelIndex, lxnsSongIdToLocal, normalizeChartType, normalizeLxnsSongId } from "../utils/compat.js";

export type ScoreLocator = {
  sheetId?: bigint;
  songIdentifier?: string;
  songId?: number;
  title?: string;
  chartType?: string;
  type?: string;
  difficulty?: string;
  levelIndex?: number;
};

export type UpsertScoreInput = ScoreLocator & {
  achievements: number;
  rank?: string;
  dxScore?: number;
  fc?: string | null;
  fs?: string | null;
  achievedAt?: string | Date;
  sourcePayload?: unknown;
};

export type PlayRecordInput = ScoreLocator & {
  achievements: number;
  rank?: string;
  dxScore?: number;
  fc?: string | null;
  fs?: string | null;
  playTime?: string | Date;
  sourcePayload?: unknown;
};

export type UpdateBestScoreInput = {
  achievements?: number;
  rank?: string;
  dxScore?: number;
  fc?: string | null;
  fs?: string | null;
  achievedAt?: string | Date;
};

const FC_ORDER = ["fc", "fcp", "ap", "app"];
const FS_ORDER = ["sync", "fs", "fsp", "fsd", "fsdp"];

@injectable()
export class ScoreService {
  constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

  async listBestScores(profileId: string) {
    return this.prisma.bestScore.findMany({
      where: { profileId },
      include: {
        sheet: {
          include: { song: true }
        }
      },
      orderBy: [{ updatedAt: "desc" }]
    });
  }

  async listPlayRecords(profileId: string, limit: number) {
    return this.prisma.playRecord.findMany({
      where: { profileId },
      include: {
        sheet: {
          include: { song: true }
        }
      },
      orderBy: [{ playTime: "desc" }],
      take: limit
    });
  }

  async updateBestScore(scoreId: string, userId: string, input: UpdateBestScoreInput) {
    const existing = await this.prisma.bestScore.findFirst({
      where: {
        id: scoreId,
        profile: {
          userId
        }
      },
      include: {
        sheet: {
          include: { song: true }
        }
      }
    });
    if (!existing) {
      throw new AppError(404, "score_not_found", "Score not found.");
    }

    const achievements =
      input.achievements !== undefined
        ? this.normalizeAchievements(input.achievements)
        : existing.achievements.toNumber();
    const rank = input.rank?.trim() || this.rankByAchievements(achievements);
    const dxScore = input.dxScore !== undefined ? this.normalizeDxScore(input.dxScore) : existing.dxScore;
    const fc = input.fc !== undefined ? this.normalizeFc(input.fc) : existing.fc;
    const fs = input.fs !== undefined ? this.normalizeFs(input.fs) : existing.fs;
    const achievedAt =
      input.achievedAt !== undefined ? this.normalizeDate(input.achievedAt) ?? existing.achievedAt : existing.achievedAt;

    return this.prisma.bestScore.update({
      where: { id: existing.id },
      data: {
        achievements,
        rank,
        dxScore,
        fc,
        fs,
        achievedAt
      },
      include: {
        sheet: {
          include: { song: true }
        }
      }
    });
  }

  async deleteBestScore(scoreId: string, userId: string) {
    const existing = await this.prisma.bestScore.findFirst({
      where: {
        id: scoreId,
        profile: {
          userId
        }
      },
      select: {
        id: true,
        profileId: true
      }
    });
    if (!existing) {
      throw new AppError(404, "score_not_found", "Score not found.");
    }
    await this.prisma.bestScore.delete({
      where: { id: existing.id }
    });
    return {
      deleted: true,
      profileId: existing.profileId
    };
  }

  async deletePlayRecord(recordId: string, userId: string) {
    const existing = await this.prisma.playRecord.findFirst({
      where: {
        id: recordId,
        profile: {
          userId
        }
      },
      select: {
        id: true,
        profileId: true
      }
    });
    if (!existing) {
      throw new AppError(404, "play_record_not_found", "Play record not found.");
    }
    await this.prisma.playRecord.delete({
      where: { id: existing.id }
    });
    return {
      deleted: true,
      profileId: existing.profileId
    };
  }

  async bulkUpsertBestScores(profileId: string, scores: UpsertScoreInput[], source: string) {
    const applied: Array<{ sheetId: bigint; action: "created" | "updated" }> = [];
    const skipped: Array<{ reason: string; locator: ScoreLocator }> = [];

    for (const score of scores) {
      const sheet = await this.resolveSheet(score);
      if (!sheet) {
        skipped.push({ reason: "sheet_not_found", locator: score });
        continue;
      }

      const achievements = this.normalizeAchievements(score.achievements);
      const rank = score.rank ?? this.rankByAchievements(achievements);
      const dxScore = this.normalizeDxScore(score.dxScore);
      const fc = this.normalizeFc(score.fc);
      const fs = this.normalizeFs(score.fs);
      const achievedAt = this.normalizeDate(score.achievedAt) ?? new Date();

      const existing = await this.prisma.bestScore.findUnique({
        where: {
          profileId_sheetId: {
            profileId,
            sheetId: sheet.id
          }
        }
      });

      if (!existing) {
        await this.prisma.bestScore.create({
          data: {
            profileId,
            sheetId: sheet.id,
            achievements,
            rank,
            dxScore,
            fc,
            fs,
          achievedAt,
          source,
          sourcePayload: this.toSourcePayload(score.sourcePayload)
        }
      });
        applied.push({ sheetId: sheet.id, action: "created" });
        continue;
      }

      const mergedAchievements = Math.max(existing.achievements.toNumber(), achievements);
      const mergedDxScore = Math.max(existing.dxScore, dxScore);
      const mergedFc = this.pickBetterProgress(existing.fc, fc, FC_ORDER);
      const mergedFs = this.pickBetterProgress(existing.fs, fs, FS_ORDER);
      const finalRank = this.rankByAchievements(mergedAchievements);

      await this.prisma.bestScore.update({
        where: { id: existing.id },
        data: {
          achievements: mergedAchievements,
          rank: finalRank,
          dxScore: mergedDxScore,
          fc: mergedFc,
          fs: mergedFs,
          achievedAt: mergedAchievements > existing.achievements.toNumber() ? achievedAt : existing.achievedAt,
          source,
          sourcePayload: this.toSourcePayload(score.sourcePayload)
        }
      });
      applied.push({ sheetId: sheet.id, action: "updated" });
    }

    return {
      applied,
      skipped
    };
  }

  async replaceBestScores(profileId: string, scores: UpsertScoreInput[], source: string) {
    const deleted = await this.prisma.bestScore.deleteMany({
      where: { profileId }
    });
    const result = await this.bulkUpsertBestScores(profileId, scores, source);
    return {
      deletedCount: deleted.count,
      ...result
    };
  }

  async bulkInsertPlayRecords(profileId: string, records: PlayRecordInput[], source: string) {
    const created: Array<{ sheetId: bigint }> = [];
    const skipped: Array<{ reason: string; locator: ScoreLocator }> = [];

    for (const record of records) {
      const sheet = await this.resolveSheet(record);
      if (!sheet) {
        skipped.push({ reason: "sheet_not_found", locator: record });
        continue;
      }
      const achievements = this.normalizeAchievements(record.achievements);
      const rank = record.rank ?? this.rankByAchievements(achievements);
      const dxScore = this.normalizeDxScore(record.dxScore);
      const fc = this.normalizeFc(record.fc);
      const fs = this.normalizeFs(record.fs);
      const playTime = this.normalizeDate(record.playTime) ?? new Date();

      const duplicated = await this.prisma.playRecord.findFirst({
        where: {
          profileId,
          sheetId: sheet.id,
          playTime,
          achievements,
          dxScore,
          fc,
          fs
        },
        select: { id: true }
      });
      if (duplicated) {
        skipped.push({ reason: "duplicated_play_record", locator: record });
        continue;
      }

      await this.prisma.playRecord.create({
        data: {
          profileId,
          sheetId: sheet.id,
          achievements,
          rank,
          dxScore,
          fc,
          fs,
          playTime,
          source,
          sourcePayload: this.toSourcePayload(record.sourcePayload)
        }
      });

      created.push({ sheetId: sheet.id });
    }

    return {
      created,
      skipped
    };
  }

  async replacePlayRecords(profileId: string, records: PlayRecordInput[], source: string) {
    const deleted = await this.prisma.playRecord.deleteMany({
      where: { profileId }
    });
    const result = await this.bulkInsertPlayRecords(profileId, records, source);
    return {
      deletedCount: deleted.count,
      ...result
    };
  }

  async requireProfileOwnership(profileId: string, userId: string) {
    const profile = await this.prisma.profile.findFirst({
      where: { id: profileId, userId }
    });
    if (!profile) {
      throw new AppError(404, "profile_not_found", "Profile not found.");
    }
    return profile;
  }

  normalizeLxnsSongId(songId: number): number {
    return normalizeLxnsSongId(songId);
  }

  private async resolveSheet(locator: ScoreLocator) {
    if (locator.sheetId !== undefined) {
      return this.prisma.sheet.findUnique({ where: { id: locator.sheetId } });
    }

    const type = normalizeChartType(locator.chartType ?? locator.type);
    const difficulty = this.normalizeDifficulty(locator.difficulty, locator.levelIndex);

    if (type && difficulty) {
      // Try songIdentifier first (most reliable — set by ImportService catalog mapping)
      if (locator.songIdentifier) {
        const byIdentifier = await this.prisma.sheet.findUnique({
          where: {
            songIdentifier_chartType_difficulty: {
              songIdentifier: locator.songIdentifier,
              chartType: type,
              difficulty
            }
          }
        });
        if (byIdentifier) {
          return byIdentifier;
        }
      }

      // Try songId as songIdentifier (local IDs are used as songIdentifier strings)
      if (typeof locator.songId === "number" && Number.isFinite(locator.songId) && locator.songId > 0) {
        const songIdStr = String(Math.trunc(locator.songId));
        if (songIdStr !== locator.songIdentifier) {
          const bySongId = await this.prisma.sheet.findUnique({
            where: {
              songIdentifier_chartType_difficulty: {
                songIdentifier: songIdStr,
                chartType: type,
                difficulty
              }
            }
          });
          if (bySongId) {
            return bySongId;
          }
        }
      }
    }

    if (locator.title && type && difficulty) {
      return this.prisma.sheet.findFirst({
        where: {
          chartType: type,
          difficulty,
          song: {
            title: locator.title
          }
        }
      });
    }

    return null;
  }

  private normalizeDifficulty(difficulty?: string, levelIndex?: number): string | null {
    if (difficulty) {
      return difficulty.trim().toLowerCase();
    }
    if (levelIndex === undefined) {
      return null;
    }
    return difficultyByLevelIndex(levelIndex);
  }

  private normalizeAchievements(value: number): number {
    if (!Number.isFinite(value)) {
      throw new AppError(400, "invalid_achievements", "achievements must be a number.");
    }
    if (value < 0) {
      return 0;
    }
    if (value > 101) {
      return 101;
    }
    return Number(value.toFixed(4));
  }

  private normalizeDxScore(value?: number): number {
    if (value === undefined || value === null || !Number.isFinite(value)) {
      return 0;
    }
    return Math.trunc(value);
  }

  private normalizeFc(value?: string | null): string | null {
    if (!value) {
      return null;
    }
    const normalized = value.toLowerCase().trim();
    if (!FC_ORDER.includes(normalized)) {
      return null;
    }
    return normalized;
  }

  private normalizeFs(value?: string | null): string | null {
    if (!value) {
      return null;
    }
    const normalized = value.toLowerCase().trim();
    if (!FS_ORDER.includes(normalized)) {
      return null;
    }
    return normalized;
  }

  private normalizeDate(value?: string | Date): Date | null {
    if (!value) {
      return null;
    }
    const date = value instanceof Date ? value : new Date(value);
    if (Number.isNaN(date.getTime())) {
      return null;
    }
    return date;
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

  private pickBetterProgress(
    currentValue: string | null | undefined,
    nextValue: string | null | undefined,
    order: string[]
  ): string | null {
    const currentIndex = currentValue ? order.indexOf(currentValue.toLowerCase()) : -1;
    const nextIndex = nextValue ? order.indexOf(nextValue.toLowerCase()) : -1;
    if (nextIndex > currentIndex && nextValue) {
      return nextValue;
    }
    if (currentValue) {
      return currentValue;
    }
    return null;
  }

  private toSourcePayload(
    payload: unknown
  ): Prisma.InputJsonValue | Prisma.NullableJsonNullValueInput {
    if (payload === undefined) {
      return Prisma.JsonNull;
    }
    return payload as Prisma.InputJsonValue;
  }
}
