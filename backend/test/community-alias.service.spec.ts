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

	it("serializes submitterHandle on the public voting board", async () => {
		const prisma = {
			communityAliasCandidate: {
				findMany: vi.fn().mockResolvedValue([
					{
						id: "candidate-1",
						songIdentifier: "123",
						aliasText: "Alias-X",
						submitterId: "user-1",
						submitter: {
							username: "Alice",
							usernameDiscriminator: "0042",
						},
						voteOpenAt: null,
						voteCloseAt: null,
						createdAt: new Date("2026-04-12T10:00:00Z"),
						votes: [
							{ voterId: "user-1", vote: 1 },
							{ voterId: "user-2", vote: -1 },
						],
					},
				]),
			},
		};
		const service = new CommunityAliasService(prisma as never, { listAliases: vi.fn() } as never);

		const rows = await service.fetchVotingBoard("user-1", 20, 0);

		expect(rows[0]).toMatchObject({
			candidateId: "candidate-1",
			submitterHandle: "Alice#0042",
			supportCount: 1,
			opposeCount: 1,
			myVote: 1,
		});
	});

	it("keeps submitterEmail for admins while adding submitterHandle", async () => {
		const prisma = {
			communityAliasCandidate: {
				findMany: vi.fn().mockResolvedValue([
					{
						id: "candidate-1",
						songIdentifier: "123",
						aliasText: "Alias-X",
						submitterId: "user-1",
						submitter: {
							email: "alice@example.com",
							username: "Alice",
							usernameDiscriminator: "0042",
						},
						status: "voting",
						voteOpenAt: null,
						voteCloseAt: null,
						approvedAt: null,
						rejectedAt: null,
						createdAt: new Date("2026-04-12T10:00:00Z"),
						updatedAt: new Date("2026-04-12T10:05:00Z"),
						votes: [{ vote: 1 }],
					},
				]),
				count: vi.fn().mockResolvedValue(1),
			},
		};
		const service = new CommunityAliasService(prisma as never, { listAliases: vi.fn() } as never);

		const rows = await service.adminListCandidates({
			limit: 20,
			offset: 0,
		});

		expect(rows[0]).toMatchObject({
			submitterEmail: "alice@example.com",
			submitterHandle: "Alice#0042",
			totalCount: 1,
		});
	});
});
