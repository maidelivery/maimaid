import { inject, injectable } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";

type RecordEventInput = {
  userId: string;
  profileId?: string | null;
  entityType: "profile" | "avatar" | "best_scores" | "play_records" | "import" | "static_bundle";
  entityId: string;
  op: "upsert" | "delete" | "replace" | "bulk_upsert" | "imported";
  payload?: Record<string, unknown> | null;
};

@injectable()
export class SyncService {
  constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

  private toJsonValue(value: unknown): Prisma.InputJsonValue {
    return JSON.parse(JSON.stringify(value)) as Prisma.InputJsonValue;
  }

  private toNullableJson(value: unknown): Prisma.InputJsonValue | Prisma.NullableJsonNullValueInput {
    if (value === null) {
      return Prisma.JsonNull;
    }
    return this.toJsonValue(value);
  }

  async recordEvent(input: RecordEventInput) {
    return this.prisma.syncEvent.create({
      data: {
        userId: input.userId,
        profileId: input.profileId ?? null,
        entityType: input.entityType,
        entityId: input.entityId,
        op: input.op,
        payloadJson: this.toNullableJson(input.payload ?? null)
      }
    });
  }

  async findMutation(userId: string, idempotencyKey: string) {
    return this.prisma.syncMutation.findUnique({
      where: {
        userId_idempotencyKey: {
          userId,
          idempotencyKey
        }
      }
    });
  }

  async saveMutationResult(
    userId: string,
    idempotencyKey: string,
    result: Record<string, unknown>
  ) {
    return this.prisma.syncMutation.upsert({
      where: {
        userId_idempotencyKey: {
          userId,
          idempotencyKey
        }
      },
      update: {
        resultJson: this.toJsonValue(result)
      },
      create: {
        userId,
        idempotencyKey,
        resultJson: this.toJsonValue(result)
      }
    });
  }

  async listEvents(input: {
    userId: string;
    sinceRevision?: bigint;
    profileId?: string;
    limit: number;
  }) {
    const where: Prisma.SyncEventWhereInput = {
      userId: input.userId
    };
    if (input.profileId !== undefined) {
      where.profileId = input.profileId;
    }
    if (input.sinceRevision !== undefined) {
      where.revision = {
        gt: input.sinceRevision
      };
    }

    return this.prisma.syncEvent.findMany({
      where,
      orderBy: { revision: "asc" },
      take: Math.max(1, Math.min(input.limit, 500))
    });
  }

  async buildSnapshot(userId: string, profileIds: string[]) {
    const ids = Array.from(new Set(profileIds.filter((value) => value.length > 0)));
    if (ids.length === 0) {
      return {
        profiles: [],
        scores: [],
        records: []
      };
    }

    const profiles = await this.prisma.profile.findMany({
      where: {
        userId,
        id: {
          in: ids
        }
      },
      orderBy: [{ isActive: "desc" }, { createdAt: "asc" }]
    });

    const scores = await this.prisma.bestScore.findMany({
      where: {
        profileId: {
          in: ids
        }
      },
      include: {
        sheet: {
          include: {
            song: true
          }
        }
      },
      orderBy: [{ updatedAt: "desc" }]
    });

    const records = await this.prisma.playRecord.findMany({
      where: {
        profileId: {
          in: ids
        }
      },
      include: {
        sheet: {
          include: {
            song: true
          }
        }
      },
      orderBy: [{ playTime: "desc" }]
    });

    return {
      profiles,
      scores,
      records
    };
  }
}
