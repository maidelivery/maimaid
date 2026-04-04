import { Prisma, type PrismaClient } from "@prisma/client";
import { inject, injectable } from "tsyringe";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import type { CatalogService } from "./catalog.service.js";

const SHANGHAI_TIMEZONE = "Asia/Shanghai";
type DuplicateReason = "lxns_existing" | "community_existing" | "admin_rejected_locked";

@injectable()
export class CommunityAliasService {
  constructor(
    @inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
    @inject(TOKENS.CatalogService) private readonly catalogService: CatalogService
  ) {}

  normalizeAlias(raw: string): string {
    return raw
      .normalize("NFKC")
      .trim()
      .toLowerCase()
      .replace(/[\s]+/gu, "")
      .replace(/[\p{P}\p{S}，。！？、；：·・•（）【】《》〈〉「」『』“”‘’—～＿－…￥]+/gu, "");
  }

  async submitAlias(input: {
    userId: string;
    isAdmin: boolean;
    songIdentifier: string;
    aliasText: string;
    deviceLocalDate: string;
    tzOffsetMinutes: number;
  }) {
    const aliasText = input.aliasText.trim();
    const aliasNorm = this.normalizeAlias(aliasText);
    if (!input.songIdentifier || !aliasText || aliasText.length > 64 || !aliasNorm) {
      throw new AppError(400, "invalid_request", "songIdentifier and aliasText are required.");
    }

    const [dailyCount, lxnsAliases, communityCandidate, communityAlias, adminRejectedCandidate] = await Promise.all([
      this.prisma.communityAliasCandidate.count({
        where: {
          submitterId: input.userId,
          submittedLocalDate: new Date(input.deviceLocalDate)
        }
      }),
      this.catalogService.listAliases(input.songIdentifier, "lxns"),
      this.prisma.communityAliasCandidate.findFirst({
        where: {
          songIdentifier: input.songIdentifier,
          aliasNorm,
          status: { in: ["voting", "approved"] }
        }
      }),
      this.prisma.alias.findFirst({
        where: {
          songIdentifier: input.songIdentifier,
          aliasNorm,
          source: "community"
        }
      }),
      this.prisma.communityAliasCandidate.findFirst({
        where: {
          songIdentifier: input.songIdentifier,
          aliasNorm,
          status: "rejected",
          rejectionSource: "admin_manual"
        }
      })
    ]);

    const lxnsMatched = lxnsAliases.find(
      (item) => this.normalizeAlias(item.aliasText) === aliasNorm || this.normalizeAlias(item.aliasNorm) === aliasNorm
    );
    if (lxnsMatched) {
      return this.buildDuplicateResponse({
        message: "该别名已在 LXNS 中存在，无法重复投稿。",
        duplicateReason: "lxns_existing",
        similarAliases: [lxnsMatched.aliasText]
      });
    }

    if (communityCandidate || communityAlias) {
      return this.buildDuplicateResponse({
        message: "该别名已在社区别名中存在，无法重复投稿。",
        duplicateReason: "community_existing",
        similarAliases: [communityCandidate?.aliasText, communityAlias?.aliasText].filter(
          (value): value is string => Boolean(value)
        )
      });
    }

    if (adminRejectedCandidate && !input.isAdmin) {
      return this.buildDuplicateResponse({
        message: "该别名曾被管理员拒绝，仅管理员可再次投稿。",
        duplicateReason: "admin_rejected_locked",
        similarAliases: [adminRejectedCandidate.aliasText]
      });
    }

    if (dailyCount >= 5) {
      return {
        status: "quota_exceeded",
        message: "Daily alias submission quota reached.",
        quotaRemaining: 0
      } as const;
    }

    const voteOpenAt = new Date();
    const voteCloseAt = this.computeCycleEnd(voteOpenAt);

    const candidate = await this.prisma.communityAliasCandidate.create({
      data: {
        songIdentifier: input.songIdentifier,
        aliasText,
        aliasNorm,
        submitterId: input.userId,
        status: "voting",
        rejectionSource: null,
        voteOpenAt,
        voteCloseAt,
        submittedLocalDate: new Date(input.deviceLocalDate),
        submittedTzOffsetMin: Math.trunc(input.tzOffsetMinutes)
      }
    });

    return {
      status: "created",
      message: "Alias submitted and is now public in the current voting cycle.",
      candidate,
      quotaRemaining: Math.max(0, 5 - (dailyCount + 1))
    } as const;
  }

  async fetchVotingBoard(userId: string | null, limit: number, offset: number) {
    const safeLimit = Math.max(1, Math.min(limit, 200));
    const safeOffset = Math.max(0, offset);

    const rows = await this.prisma.communityAliasCandidate.findMany({
      where: {
        status: "voting",
        OR: [{ voteOpenAt: null }, { voteOpenAt: { lte: new Date() } }],
        AND: [{ OR: [{ voteCloseAt: null }, { voteCloseAt: { gte: new Date() } }] }]
      },
      include: {
        votes: true
      },
      orderBy: [{ voteCloseAt: "asc" }, { createdAt: "desc" }],
      skip: safeOffset,
      take: safeLimit
    });

    return rows.map((row) => {
      const supportCount = row.votes.filter((item) => item.vote === 1).length;
      const opposeCount = row.votes.filter((item) => item.vote === -1).length;
      const myVote = userId ? row.votes.find((item) => item.voterId === userId)?.vote ?? null : null;
      return {
        candidateId: row.id,
        songIdentifier: row.songIdentifier,
        aliasText: row.aliasText,
        submitterId: row.submitterId,
        voteOpenAt: row.voteOpenAt,
        voteCloseAt: row.voteCloseAt,
        supportCount,
        opposeCount,
        myVote,
        createdAt: row.createdAt
      };
    });
  }

  async fetchMyCandidates(userId: string, limit: number, songIdentifier?: string) {
    const where: Prisma.CommunityAliasCandidateWhereInput = {
      submitterId: userId
    };
    const normalizedSongIdentifier = songIdentifier?.trim();
    if (normalizedSongIdentifier) {
      where.songIdentifier = normalizedSongIdentifier;
    }

    const rows = await this.prisma.communityAliasCandidate.findMany({
      where,
      include: {
        votes: true
      },
      orderBy: { createdAt: "desc" },
      take: Math.max(1, Math.min(limit, 200))
    });

    return rows.map((row) => ({
      candidateId: row.id,
      songIdentifier: row.songIdentifier,
      aliasText: row.aliasText,
      status: row.status,
      voteOpenAt: row.voteOpenAt,
      voteCloseAt: row.voteCloseAt,
      supportCount: row.votes.filter((item) => item.vote === 1).length,
      opposeCount: row.votes.filter((item) => item.vote === -1).length,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt
    }));
  }

  async fetchMyDailyCount(userId: string, localDate: string) {
    const date = new Date(localDate);
    if (Number.isNaN(date.getTime())) {
      throw new AppError(400, "invalid_date", "Invalid local date.");
    }
    return this.prisma.communityAliasCandidate.count({
      where: {
        submitterId: userId,
        submittedLocalDate: date
      }
    });
  }

  async vote(userId: string, candidateId: string, vote: number) {
    if (vote !== -1 && vote !== 1) {
      throw new AppError(400, "invalid_vote", "Invalid vote value.");
    }

    const candidate = await this.prisma.communityAliasCandidate.findUnique({
      where: { id: candidateId }
    });
    if (!candidate) {
      throw new AppError(404, "candidate_not_found", "Candidate not found.");
    }
    if (candidate.status !== "voting") {
      throw new AppError(400, "candidate_not_voting", "Candidate is not in voting status.");
    }
    const now = new Date();
    if ((candidate.voteOpenAt && now < candidate.voteOpenAt) || (candidate.voteCloseAt && now > candidate.voteCloseAt)) {
      throw new AppError(400, "voting_window_closed", "Voting window is closed.");
    }

    const existingVote = await this.prisma.communityAliasVote.findUnique({
      where: {
        candidateId_voterId: {
          candidateId,
          voterId: userId
        }
      }
    });

    let myVote: number | null = vote;
    if (existingVote && existingVote.vote === vote) {
      await this.prisma.communityAliasVote.delete({
        where: {
          candidateId_voterId: {
            candidateId,
            voterId: userId
          }
        }
      });
      myVote = null;
    } else {
      await this.prisma.communityAliasVote.upsert({
        where: {
          candidateId_voterId: {
            candidateId,
            voterId: userId
          }
        },
        create: {
          candidateId,
          voterId: userId,
          vote
        },
        update: {
          vote
        }
      });
    }

    const votes = await this.prisma.communityAliasVote.findMany({
      where: { candidateId }
    });
    return {
      candidateId,
      supportCount: votes.filter((item) => item.vote === 1).length,
      opposeCount: votes.filter((item) => item.vote === -1).length,
      myVote
    };
  }

  async approvedSync(since: Date | null, limit: number) {
    const where: Prisma.CommunityAliasCandidateWhereInput = {
      status: "approved"
    };
    if (since) {
      where.updatedAt = { gt: since };
    }

    const rows = await this.prisma.communityAliasCandidate.findMany({
      where,
      orderBy: { updatedAt: "asc" },
      take: Math.max(1, Math.min(limit, 2000))
    });

    return rows.map((row) => ({
      candidateId: row.id,
      songIdentifier: row.songIdentifier,
      aliasText: row.aliasText,
      updatedAt: row.updatedAt,
      approvedAt: row.approvedAt
    }));
  }

  async rollCycle() {
    const now = new Date();
    const due = await this.prisma.communityAliasCandidate.findMany({
      where: {
        status: "voting",
        voteCloseAt: {
          lte: now
        }
      },
      include: {
        votes: true
      }
    });

    let settledCount = 0;

    for (const candidate of due) {
      const support = candidate.votes.filter((item) => item.vote === 1).length;
      const oppose = candidate.votes.filter((item) => item.vote === -1).length;
      const approved = support > oppose && support >= 3;
      if (!approved) {
        await this.deleteOtherRejectedCandidates(candidate.songIdentifier, candidate.aliasNorm, candidate.id);
      }
      await this.prisma.communityAliasCandidate.update({
        where: { id: candidate.id },
        data: {
          status: approved ? "approved" : "rejected",
          rejectionSource: approved ? null : "community_vote",
          approvedAt: approved ? now : null,
          rejectedAt: approved ? null : now
        }
      });

      if (approved) {
        await this.prisma.alias.upsert({
          where: {
            songIdentifier_aliasNorm_source: {
              songIdentifier: candidate.songIdentifier,
              aliasNorm: candidate.aliasNorm,
              source: "community"
            }
          },
          create: {
            songIdentifier: candidate.songIdentifier,
            aliasText: candidate.aliasText,
            aliasNorm: candidate.aliasNorm,
            source: "community",
            status: "approved"
          },
          update: {
            aliasText: candidate.aliasText,
            status: "approved"
          }
        });
      }

      settledCount += 1;
    }

    return {
      now,
      timezone: SHANGHAI_TIMEZONE,
      settledCount
    };
  }

  async adminDashboardStats() {
    const now = new Date();
    const startOfDayShanghai = new Date(
      new Date().toLocaleString("en-US", { timeZone: SHANGHAI_TIMEZONE, hour12: false })
    );
    startOfDayShanghai.setHours(0, 0, 0, 0);
    const endOfDayShanghai = new Date(startOfDayShanghai);
    endOfDayShanghai.setDate(endOfDayShanghai.getDate() + 1);

    const [totalCount, votingCount, approvedCount, rejectedCount, closingSoonCount, expiredVotingCount, todaySubmissions] = await Promise.all([
      this.prisma.communityAliasCandidate.count(),
      this.prisma.communityAliasCandidate.count({ where: { status: "voting" } }),
      this.prisma.communityAliasCandidate.count({ where: { status: "approved" } }),
      this.prisma.communityAliasCandidate.count({ where: { status: "rejected" } }),
      this.prisma.communityAliasCandidate.count({
        where: {
          status: "voting",
          voteCloseAt: { gte: now, lte: new Date(now.getTime() + 24 * 60 * 60 * 1000) }
        }
      }),
      this.prisma.communityAliasCandidate.count({
        where: {
          status: "voting",
          voteCloseAt: { lt: now }
        }
      }),
      this.prisma.communityAliasCandidate.count({
        where: {
          createdAt: {
            gte: startOfDayShanghai,
            lt: endOfDayShanghai
          }
        }
      })
    ]);

    return {
      totalCount,
      votingCount,
      approvedCount,
      rejectedCount,
      closingSoonCount,
      expiredVotingCount,
      todaySubmissions
    };
  }

  async adminListCandidates(input: {
    status?: string | null;
    search?: string | null;
    sort?: string | null;
    limit: number;
    offset: number;
  }) {
    const where: Prisma.CommunityAliasCandidateWhereInput = {};
    if (input.status && input.status !== "all") {
      where.status = input.status as Prisma.EnumCandidateStatusFilter<"CommunityAliasCandidate">;
    }
    if (input.search) {
      const search = input.search.trim();
      where.OR = [
        { songIdentifier: { contains: search, mode: "insensitive" } },
        { aliasText: { contains: search, mode: "insensitive" } }
      ];
    }

    const [rows, totalCount] = await Promise.all([
      this.prisma.communityAliasCandidate.findMany({
        where,
        include: { votes: true, submitter: true },
        orderBy: this.resolveAdminSort(input.sort),
        skip: input.offset,
        take: input.limit
      }),
      this.prisma.communityAliasCandidate.count({ where })
    ]);

    return rows.map((row) => ({
      candidateId: row.id,
      songIdentifier: row.songIdentifier,
      aliasText: row.aliasText,
      submitterId: row.submitterId,
      submitterEmail: row.submitter.email,
      status: row.status,
      voteOpenAt: row.voteOpenAt,
      voteCloseAt: row.voteCloseAt,
      approvedAt: row.approvedAt,
      rejectedAt: row.rejectedAt,
      supportCount: row.votes.filter((item) => item.vote === 1).length,
      opposeCount: row.votes.filter((item) => item.vote === -1).length,
      totalCount,
      createdAt: row.createdAt,
      updatedAt: row.updatedAt
    }));
  }

  async adminCreateCandidate(input: {
    submitterId: string;
    songIdentifier: string;
    aliasText: string;
    status: "voting" | "approved";
  }) {
    const aliasNorm = this.normalizeAlias(input.aliasText);
    const now = new Date();
    return this.prisma.communityAliasCandidate.create({
      data: {
        songIdentifier: input.songIdentifier,
        aliasText: input.aliasText,
        aliasNorm,
        submitterId: input.submitterId,
        status: input.status,
        voteOpenAt: now,
        voteCloseAt: input.status === "voting" ? this.computeCycleEnd(now) : now,
        approvedAt: input.status === "approved" ? now : null,
        submittedLocalDate: now,
        submittedTzOffsetMin: 480
      }
    });
  }

  async adminSetStatus(candidateId: string, status: "voting" | "approved" | "rejected") {
    const candidate = await this.prisma.communityAliasCandidate.findUnique({
      where: { id: candidateId }
    });
    if (!candidate) {
      throw new AppError(404, "candidate_not_found", "Candidate not found.");
    }

    if (status === "rejected") {
      await this.deleteOtherRejectedCandidates(candidate.songIdentifier, candidate.aliasNorm, candidateId);
    }

    const now = new Date();
    const data: Prisma.CommunityAliasCandidateUpdateInput = {
      status,
      rejectionSource: status === "rejected" ? "admin_manual" : null,
      approvedAt: status === "approved" ? now : null,
      rejectedAt: status === "rejected" ? now : null
    };
    if (status === "voting") {
      data.voteOpenAt = now;
      data.voteCloseAt = this.computeCycleEnd(now);
    }

    return this.prisma.communityAliasCandidate.update({
      where: { id: candidateId },
      data
    });
  }

  async adminUpdateVoteWindow(candidateId: string, voteCloseAt: Date) {
    if (voteCloseAt <= new Date()) {
      throw new AppError(400, "invalid_vote_close_at", "vote_close_at must be later than now.");
    }
    return this.prisma.communityAliasCandidate.update({
      where: { id: candidateId },
      data: {
        voteOpenAt: new Date(),
        voteCloseAt
      }
    });
  }

  private resolveAdminSort(sort?: string | null): Prisma.CommunityAliasCandidateOrderByWithRelationInput[] {
    switch ((sort ?? "updated_desc").toLowerCase()) {
      case "deadline_asc":
        return [{ voteCloseAt: "asc" }, { createdAt: "desc" }];
      case "created_desc":
        return [{ createdAt: "desc" }];
      case "created_asc":
        return [{ createdAt: "asc" }];
      case "updated_asc":
        return [{ updatedAt: "asc" }];
      default:
        return [{ updatedAt: "desc" }, { createdAt: "desc" }];
    }
  }

  private computeCycleEnd(from: Date): Date {
    const beijingNow = new Date(from.toLocaleString("en-US", { timeZone: SHANGHAI_TIMEZONE }));
    const start = new Date(beijingNow);
    start.setHours(0, 0, 0, 0);

    const epoch = new Date("1970-01-01T00:00:00+08:00");
    const offsetDays = Math.floor((start.getTime() - epoch.getTime()) / (24 * 60 * 60 * 1000)) % 3;
    start.setDate(start.getDate() - offsetDays + 3);
    start.setSeconds(-1);
    return start;
  }

  private buildDuplicateResponse(input: {
    message: string;
    duplicateReason: DuplicateReason;
    similarAliases?: string[];
  }) {
    const similarAliases = Array.from(new Set((input.similarAliases ?? []).map((value) => value.trim()).filter(Boolean)));
    return {
      status: "rejected_duplicate",
      message: input.message,
      duplicateReason: input.duplicateReason,
      similarAliases: similarAliases.length > 0 ? similarAliases.slice(0, 3) : undefined,
      candidate: null
    } as const;
  }

  private async deleteOtherRejectedCandidates(songIdentifier: string, aliasNorm: string, candidateId: string) {
    await this.prisma.communityAliasCandidate.deleteMany({
      where: {
        songIdentifier,
        aliasNorm,
        status: "rejected",
        id: {
          not: candidateId
        }
      }
    });
  }
}
