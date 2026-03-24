import type { Prisma, PrismaClient } from "@prisma/client";
import { inject, injectable } from "tsyringe";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { StorageService } from "./storage.service.js";

@injectable()
export class ProfileService {
  constructor(
    @inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
    @inject(TOKENS.StorageService) private readonly storageService: StorageService
  ) {}

  async list(userId: string) {
    return this.prisma.profile.findMany({
      where: { userId },
      orderBy: [{ isActive: "desc" }, { createdAt: "asc" }]
    });
  }

  async create(userId: string, input: { name: string; server: "jp" | "intl" | "usa" | "cn" }) {
    const count = await this.prisma.profile.count({ where: { userId } });
    const shouldActive = count === 0;
    return this.prisma.profile.create({
      data: {
        userId,
        name: input.name,
        server: input.server,
        isActive: shouldActive
      }
    });
  }

  async upsertByClientId(
    userId: string,
    profileId: string,
    input: {
      name: string;
      server: "jp" | "intl" | "usa" | "cn";
      isActive?: boolean;
      playerRating?: number;
      plate?: string | null;
      avatarUrl?: string | null;
      dfUsername?: string;
      dfImportToken?: string;
      lxnsRefreshToken?: string;
      b35Count?: number;
      b15Count?: number;
      b35RecLimit?: number;
      b15RecLimit?: number;
      createdAt?: Date;
    }
  ) {
    const existing = await this.prisma.profile.findUnique({
      where: { id: profileId }
    });

    if (existing && existing.userId !== userId) {
      throw new AppError(403, "forbidden", "Profile does not belong to current user.");
    }

    const shouldActivateFirstProfile =
      existing === null
        ? (await this.prisma.profile.count({ where: { userId } })) === 0
        : false;
    const shouldActivate = input.isActive ?? shouldActivateFirstProfile;

    if (!existing) {
      const created = await this.prisma.profile.create({
        data: {
          id: profileId,
          userId,
          name: input.name,
          server: input.server,
          isActive: shouldActivate,
          playerRating: input.playerRating ?? 0,
          plate: input.plate ?? null,
          avatarUrl: input.avatarUrl ?? null,
          dfUsername: input.dfUsername ?? "",
          dfImportToken: input.dfImportToken ?? "",
          lxnsRefreshToken: input.lxnsRefreshToken ?? "",
          b35Count: input.b35Count ?? 35,
          b15Count: input.b15Count ?? 15,
          b35RecLimit: input.b35RecLimit ?? 10,
          b15RecLimit: input.b15RecLimit ?? 10,
          createdAt: input.createdAt ?? new Date()
        }
      });

      if (shouldActivate) {
        await this.prisma.profile.updateMany({
          where: {
            userId,
            id: { not: created.id }
          },
          data: { isActive: false }
        });
      }

      return created;
    }

    const data: Prisma.ProfileUpdateInput = {
      name: input.name,
      server: input.server
    };
    if (input.isActive !== undefined) data.isActive = input.isActive;
    if (input.playerRating !== undefined) data.playerRating = input.playerRating;
    if (input.plate !== undefined) data.plate = input.plate;
    if (input.avatarUrl !== undefined) data.avatarUrl = input.avatarUrl;
    if (input.dfUsername !== undefined) data.dfUsername = input.dfUsername;
    if (input.dfImportToken !== undefined) data.dfImportToken = input.dfImportToken;
    if (input.lxnsRefreshToken !== undefined) data.lxnsRefreshToken = input.lxnsRefreshToken;
    if (input.b35Count !== undefined) data.b35Count = input.b35Count;
    if (input.b15Count !== undefined) data.b15Count = input.b15Count;
    if (input.b35RecLimit !== undefined) data.b35RecLimit = input.b35RecLimit;
    if (input.b15RecLimit !== undefined) data.b15RecLimit = input.b15RecLimit;

    const updated = await this.prisma.profile.update({
      where: { id: profileId },
      data
    });

    if (input.isActive) {
      await this.prisma.profile.updateMany({
        where: {
          userId,
          id: { not: profileId }
        },
        data: { isActive: false }
      });
    }

    return updated;
  }

  async update(
    userId: string,
    profileId: string,
    input: Partial<{
      name: string;
      server: "jp" | "intl" | "usa" | "cn";
      isActive: boolean;
      playerRating: number;
      plate: string | null;
      dfUsername: string;
      dfImportToken: string;
      lxnsRefreshToken: string;
      b35Count: number;
      b15Count: number;
      b35RecLimit: number;
      b15RecLimit: number;
    }>
  ) {
    const profile = await this.prisma.profile.findFirst({
      where: { id: profileId, userId }
    });
    if (!profile) {
      throw new AppError(404, "profile_not_found", "Profile not found.");
    }

    const data: Prisma.ProfileUpdateInput = {};
    if (input.name !== undefined) data.name = input.name;
    if (input.server !== undefined) data.server = input.server;
    if (input.isActive !== undefined) data.isActive = input.isActive;
    if (input.playerRating !== undefined) data.playerRating = input.playerRating;
    if (input.plate !== undefined) data.plate = input.plate;
    if (input.dfUsername !== undefined) data.dfUsername = input.dfUsername;
    if (input.dfImportToken !== undefined) data.dfImportToken = input.dfImportToken;
    if (input.lxnsRefreshToken !== undefined) data.lxnsRefreshToken = input.lxnsRefreshToken;
    if (input.b35Count !== undefined) data.b35Count = input.b35Count;
    if (input.b15Count !== undefined) data.b15Count = input.b15Count;
    if (input.b35RecLimit !== undefined) data.b35RecLimit = input.b35RecLimit;
    if (input.b15RecLimit !== undefined) data.b15RecLimit = input.b15RecLimit;

    const updated = await this.prisma.profile.update({
      where: { id: profileId },
      data
    });

    if (input.isActive) {
      await this.prisma.profile.updateMany({
        where: {
          userId,
          id: { not: profileId }
        },
        data: { isActive: false }
      });
    }

    return updated;
  }

  async activeProfile(userId: string) {
    return this.prisma.profile.findFirst({
      where: { userId, isActive: true }
    });
  }

  async createAvatarUploadUrl(userId: string, profileId: string, contentType: string) {
    const profile = await this.prisma.profile.findFirst({
      where: { id: profileId, userId }
    });
    if (!profile) {
      throw new AppError(404, "profile_not_found", "Profile not found.");
    }

    const { key, uploadUrl } = await this.storageService.createAvatarUploadUrl(profileId, contentType);
    await this.prisma.profile.update({
      where: { id: profileId },
      data: {
        avatarObjectKey: key
      }
    });
    return { key, uploadUrl };
  }

  async getAvatar(profileId: string) {
    const profile = await this.prisma.profile.findUnique({
      where: { id: profileId },
      select: { avatarObjectKey: true }
    });
    if (!profile?.avatarObjectKey) {
      throw new AppError(404, "avatar_not_found", "Avatar not found.");
    }

    return this.storageService.getObject(profile.avatarObjectKey);
  }
}
