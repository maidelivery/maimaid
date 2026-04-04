import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { CommunityAliasService } from "../src/services/community-alias.service.js";

function createService(input?: {
  dailyCount?: number;
  lxnsAliases?: Array<{ aliasText: string; aliasNorm: string }>;
  communityCandidate?: { aliasText: string } | null;
  communityAlias?: { aliasText: string } | null;
  adminRejectedCandidate?: { aliasText: string } | null;
}) {
  const create = vi.fn().mockResolvedValue({
    id: "candidate-id",
    songIdentifier: "123",
    aliasText: "new alias",
    status: "voting",
  });

  const findFirst = vi.fn().mockImplementation(async (args: { where?: { status?: unknown; rejectionSource?: string } }) => {
    const status = args.where?.status;
    if (typeof status === "object" && status && "in" in status) {
      return input?.communityCandidate ?? null;
    }
    if (status === "rejected" && args.where?.rejectionSource === "admin_manual") {
      return input?.adminRejectedCandidate ?? null;
    }
    return null;
  });

  const prisma = {
    communityAliasCandidate: {
      count: vi.fn().mockResolvedValue(input?.dailyCount ?? 0),
      findFirst,
      create,
    },
    alias: {
      findFirst: vi.fn().mockResolvedValue(input?.communityAlias ?? null),
    },
  };

  const catalogService = {
    listAliases: vi.fn().mockResolvedValue(input?.lxnsAliases ?? []),
  };

  const service = new CommunityAliasService(prisma as never, catalogService as never);
  return {
    service,
    mocks: {
      create,
      listAliases: catalogService.listAliases,
    },
  };
}

describe("community alias submit duplicate rules", () => {
  it("rejects when alias already exists in lxns for the same song", async () => {
    const { service } = createService({
      lxnsAliases: [{ aliasText: "Alias-X", aliasNorm: "aliasx" }],
    });

    const result = await service.submitAlias({
      userId: "user-id",
      isAdmin: false,
      songIdentifier: "123",
      aliasText: "alias x",
      deviceLocalDate: "2026-03-29",
      tzOffsetMinutes: 480,
    });

    expect(result.status).toBe("rejected_duplicate");
    expect(result.duplicateReason).toBe("lxns_existing");
  });

  it("rejects when alias already exists in community aliases for the same song", async () => {
    const { service } = createService({
      communityAlias: { aliasText: "社区别名" },
    });

    const result = await service.submitAlias({
      userId: "user-id",
      isAdmin: false,
      songIdentifier: "123",
      aliasText: "社区别名",
      deviceLocalDate: "2026-03-29",
      tzOffsetMinutes: 480,
    });

    expect(result.status).toBe("rejected_duplicate");
    expect(result.duplicateReason).toBe("community_existing");
  });

  it("blocks non-admin users when alias was rejected by admin", async () => {
    const { service } = createService({
      adminRejectedCandidate: { aliasText: "locked alias" },
    });

    const result = await service.submitAlias({
      userId: "user-id",
      isAdmin: false,
      songIdentifier: "123",
      aliasText: "locked alias",
      deviceLocalDate: "2026-03-29",
      tzOffsetMinutes: 480,
    });

    expect(result.status).toBe("rejected_duplicate");
    expect(result.duplicateReason).toBe("admin_rejected_locked");
  });

  it("allows admin users to resubmit aliases rejected by admin", async () => {
    const { service, mocks } = createService({
      adminRejectedCandidate: { aliasText: "locked alias" },
    });

    const result = await service.submitAlias({
      userId: "admin-id",
      isAdmin: true,
      songIdentifier: "123",
      aliasText: "locked alias",
      deviceLocalDate: "2026-03-29",
      tzOffsetMinutes: 480,
    });

    expect(result.status).toBe("created");
    expect(mocks.create).toHaveBeenCalledTimes(1);
  });

  it("allows resubmission when prior rejected record has no source (legacy community reject)", async () => {
    const { service, mocks } = createService();

    const result = await service.submitAlias({
      userId: "user-id",
      isAdmin: false,
      songIdentifier: "123",
      aliasText: "retry alias",
      deviceLocalDate: "2026-03-29",
      tzOffsetMinutes: 480,
    });

    expect(result.status).toBe("created");
    expect(mocks.listAliases).toHaveBeenCalledWith("123", "lxns");
  });
});
