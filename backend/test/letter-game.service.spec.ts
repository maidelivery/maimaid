import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { LetterGameService } from "../src/services/letter-game.service.js";

const catalogService = { listAliases: vi.fn(), listVersions: vi.fn() };

const createTransactionService = (tx: Record<string, unknown>, root: Record<string, unknown> = {}) => {
	const prisma = {
		...root,
		$transaction: vi.fn(async (operation: (database: typeof tx) => unknown) => operation(tx)),
	};
	return { service: new LetterGameService(prisma as never, catalogService as never), prisma };
};

describe("LetterGameService room lifecycle", () => {
	it("dissolves the room when its final accepted member leaves", async () => {
		const roomUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
		const memberUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
		const matchUpdate = vi.fn().mockResolvedValue({ revision: 2 });
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ id: "room-1", status: "open", hostUserId: "user-1", turnDurationSeconds: 30 }),
				findFirst: vi.fn(),
				update: vi.fn(),
				updateMany: roomUpdateMany,
			},
			letterGameRoomMember: {
				findUnique: vi.fn().mockResolvedValue({ id: "member-1", userId: "user-1", status: "accepted" }),
				findFirst: vi.fn(),
				update: vi.fn().mockResolvedValue(undefined),
				updateMany: memberUpdateMany,
				count: vi.fn().mockResolvedValue(0),
			},
			letterGameMatch: {
				findFirst: vi.fn().mockResolvedValue({
					id: "match-1",
					turnOrder: ["user-1"],
					currentTurnIndex: 0,
					players: [{ id: "player-1", userId: "user-1", status: "active" }],
				}),
				findMany: vi.fn().mockResolvedValue([]),
				update: matchUpdate,
				updateMany: vi.fn(),
			},
			letterGameMatchPlayer: {
				update: vi.fn().mockResolvedValue(undefined),
				updateMany: vi.fn(),
			},
		};
		const { service } = createTransactionService(tx);

		await expect(service.leaveRoom("user-1", "room-1")).resolves.toEqual({ left: true, dissolved: true });
		expect(matchUpdate).toHaveBeenCalledWith(expect.objectContaining({
			where: { id: "match-1" },
			data: expect.objectContaining({ status: "abandoned", turnDeadline: null }),
		}));
		expect(memberUpdateMany).toHaveBeenCalledWith(expect.objectContaining({
			where: { roomId: "room-1", status: { in: ["accepted", "pending"] } },
		}));
		expect(roomUpdateMany).toHaveBeenCalledWith(expect.objectContaining({
			where: { id: "room-1", status: "open" },
			data: expect.objectContaining({ status: "closed" }),
		}));
	});

	it("keeps the room open and transfers host ownership when another accepted member remains", async () => {
		const roomUpdate = vi.fn().mockResolvedValue(undefined);
		const roomUpdateMany = vi.fn();
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ id: "room-1", status: "open", hostUserId: "user-1", turnDurationSeconds: 30 }),
				update: roomUpdate,
				updateMany: roomUpdateMany,
			},
			letterGameRoomMember: {
				findUnique: vi.fn().mockResolvedValue({ id: "member-1", userId: "user-1", status: "accepted" }),
				findFirst: vi.fn().mockResolvedValue({ id: "member-2", userId: "user-2", status: "accepted" }),
				update: vi.fn().mockResolvedValue(undefined),
				updateMany: vi.fn(),
				count: vi.fn().mockResolvedValue(1),
			},
			letterGameMatch: { findFirst: vi.fn().mockResolvedValue(null) },
		};
		const { service } = createTransactionService(tx);

		await expect(service.leaveRoom("user-1", "room-1")).resolves.toEqual({ left: true, dissolved: false });
		expect(roomUpdate).toHaveBeenCalledWith({ where: { id: "room-1" }, data: { hostUserId: "user-2" } });
		expect(roomUpdateMany).not.toHaveBeenCalled();
	});

	it("sweeps legacy empty rooms and abandons their active matches", async () => {
		const matchPlayerUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
		const matchUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ status: "open" }),
				updateMany: vi.fn().mockResolvedValue({ count: 1 }),
			},
			letterGameRoomMember: {
				count: vi.fn().mockResolvedValue(0),
				updateMany: vi.fn().mockResolvedValue({ count: 0 }),
			},
			letterGameMatch: {
				findMany: vi.fn().mockResolvedValue([{ id: "match-1" }]),
				updateMany: matchUpdateMany,
			},
			letterGameMatchPlayer: { updateMany: matchPlayerUpdateMany },
		};
		const { service } = createTransactionService(tx, {
			letterGameRoom: { findMany: vi.fn().mockResolvedValue([{ id: "room-1" }]) },
		});

		await expect(service.dissolveEmptyRooms()).resolves.toEqual(["room-1"]);
		expect(matchPlayerUpdateMany).toHaveBeenCalledWith(expect.objectContaining({
			where: { matchId: { in: ["match-1"] }, status: "active" },
		}));
		expect(matchUpdateMany).toHaveBeenCalledWith(expect.objectContaining({
			data: expect.objectContaining({ status: "abandoned", revision: { increment: 1 } }),
		}));
	});

	it("excludes closed and empty rooms from the public lobby query", async () => {
		const findMany = vi.fn().mockResolvedValue([]);
		const service = new LetterGameService({ letterGameRoom: { findMany } } as never, catalogService as never);

		await service.listPublicRooms();

		expect(findMany).toHaveBeenCalledWith(expect.objectContaining({
			where: { visibility: "public", status: "open", members: { some: { status: "accepted" } } },
		}));
	});

	it("rejects reads for dissolved rooms", async () => {
		const service = new LetterGameService({
			letterGameRoom: { findFirst: vi.fn().mockResolvedValue({ status: "closed", members: [], matches: [] }) },
		} as never, catalogService as never);

		await expect(service.getRoom("user-1", "ABC234")).rejects.toMatchObject({ code: "room_closed", status: 410 });
	});

	it("rejects multi-grapheme character actions before starting a transaction", async () => {
		const transaction = vi.fn();
		const service = new LetterGameService({ $transaction: transaction } as never, catalogService as never);

		await expect(service.applyAction("user-1", "match-1", "action-1", 0, {
			kind: "open_character",
			character: "AB",
		})).rejects.toMatchObject({ code: "invalid_character", status: 400 });
		expect(transaction).not.toHaveBeenCalled();
	});
});
