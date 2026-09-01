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
		expect(matchUpdate).toHaveBeenCalledWith(
			expect.objectContaining({
				where: { id: "match-1" },
				data: expect.objectContaining({ status: "abandoned", turnDeadline: null }),
			}),
		);
		expect(memberUpdateMany).toHaveBeenCalledWith(
			expect.objectContaining({
				where: { roomId: "room-1", status: { in: ["accepted", "pending"] } },
			}),
		);
		expect(roomUpdateMany).toHaveBeenCalledWith(
			expect.objectContaining({
				where: { id: "room-1", status: "open" },
				data: expect.objectContaining({ status: "closed" }),
			}),
		);
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
		expect(matchPlayerUpdateMany).toHaveBeenCalledWith(
			expect.objectContaining({
				where: { matchId: { in: ["match-1"] }, status: "active" },
			}),
		);
		expect(matchUpdateMany).toHaveBeenCalledWith(
			expect.objectContaining({
				data: expect.objectContaining({ status: "abandoned", revision: { increment: 1 } }),
			}),
		);
	});

	it("expires stale accepted members before closing their rooms", async () => {
		const memberUpdate = vi.fn().mockResolvedValue(undefined);
		const roomUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ id: "room-1", status: "open", hostUserId: "user-1", turnDurationSeconds: 30 }),
				findFirst: vi.fn(),
				updateMany: roomUpdateMany,
			},
			letterGameRoomMember: {
				findUnique: vi
					.fn()
					.mockResolvedValue({
						id: "member-1",
						userId: "user-1",
						status: "accepted",
						lastSeenAt: new Date(Date.now() - 180_000),
					}),
				findFirst: vi.fn(),
				update: memberUpdate,
				updateMany: vi.fn(),
				count: vi.fn().mockResolvedValue(0),
			},
			letterGameMatch: {
				findFirst: vi.fn().mockResolvedValue(null),
				findMany: vi.fn().mockResolvedValue([]),
			},
		};
		const root = {
			letterGameRoomMember: {
				findMany: vi.fn().mockResolvedValue([{ roomId: "room-1", userId: "user-1" }]),
			},
			letterGameRoom: { findMany: vi.fn().mockResolvedValue([]) },
		};
		const { service } = createTransactionService(tx, root);

		await expect(service.dissolveEmptyRooms()).resolves.toEqual(["room-1"]);
		expect(memberUpdate).toHaveBeenCalledWith(expect.objectContaining({ data: { status: "left", leftAt: expect.any(Date) } }));
		expect(roomUpdateMany).toHaveBeenCalledWith(
			expect.objectContaining({ data: expect.objectContaining({ status: "closed" }) }),
		);
	});

	it("filters songs that only contain UTAGE charts from match selection", async () => {
		catalogService.listAliases.mockResolvedValue([]);
		const songFindMany = vi
			.fn()
			.mockResolvedValueOnce([
				{ songIdentifier: "song-standard", title: "Standard" },
				{ songIdentifier: "song-utage", title: "宴" },
			])
			.mockResolvedValueOnce([{ songIdentifier: "song-standard", title: "Standard" }]);
		const service = new LetterGameService(
			{
				song: { findMany: songFindMany },
				sheet: {
					findMany: vi.fn().mockResolvedValue([
						{ songIdentifier: "song-standard", chartType: "standard" },
						{ songIdentifier: "song-utage", chartType: "utage" },
					]),
				},
			} as never,
			catalogService as never,
		);

		const songs = await (service as any).selectSongs("user-1", {
			selectionMode: "filtered_random",
			selectionConfig: {},
		});
		expect(songs.map((song: { songIdentifier: string }) => song.songIdentifier)).toEqual(["song-standard"]);
	});

	it("keeps only ASCII English titles when the English-only filter is enabled", async () => {
		catalogService.listAliases.mockResolvedValue([]);
		const songFindMany = vi
			.fn()
			.mockResolvedValueOnce([
				{ songIdentifier: "song-english", title: "Bad Apple!!" },
				{ songIdentifier: "song-cjk", title: "中文标题" },
				{ songIdentifier: "song-utage", title: "宴" },
			])
			.mockResolvedValueOnce([{ songIdentifier: "song-english", title: "Bad Apple!!" }]);
		const service = new LetterGameService(
			{
				song: { findMany: songFindMany },
				sheet: {
					findMany: vi.fn().mockResolvedValue([
						{ songIdentifier: "song-english", chartType: "standard" },
						{ songIdentifier: "song-cjk", chartType: "standard" },
						{ songIdentifier: "song-utage", chartType: "utage" },
					]),
				},
			} as never,
			catalogService as never,
		);

		const songs = await (service as any).selectSongs("user-1", {
			selectionMode: "filtered_random",
			selectionConfig: { englishOnly: true },
		});
		expect(songs.map((song: { songIdentifier: string }) => song.songIdentifier)).toEqual(["song-english"]);
	});

	it("defaults filtered random selection to English titles", async () => {
		catalogService.listAliases.mockResolvedValue([]);
		const songFindMany = vi
			.fn()
			.mockResolvedValueOnce([
				{ songIdentifier: "song-english", title: "Bad Apple!!" },
				{ songIdentifier: "song-cjk", title: "中文标题" },
			])
			.mockResolvedValueOnce([{ songIdentifier: "song-english", title: "Bad Apple!!" }]);
		const service = new LetterGameService(
			{
				song: { findMany: songFindMany },
				sheet: {
					findMany: vi.fn().mockResolvedValue([
						{ songIdentifier: "song-english", chartType: "standard" },
						{ songIdentifier: "song-cjk", chartType: "standard" },
					]),
				},
			} as never,
			catalogService as never,
		);

		const songs = await (service as any).selectSongs("user-1", {
			selectionMode: "filtered_random",
			selectionConfig: {},
		});
		expect(songs.map((song: { songIdentifier: string }) => song.songIdentifier)).toEqual(["song-english"]);
	});

	it("deduplicates collection songs before applying the shared filters", async () => {
		catalogService.listAliases.mockResolvedValue([]);
		catalogService.listVersions.mockResolvedValue([
			{ version: "maimai" },
			{ version: "maimai PLUS" },
			{ version: "GreeN" },
			{ version: "GreeN PLUS" },
		]);
		const songFindMany = vi
			.fn()
			.mockResolvedValueOnce([
				{ songIdentifier: "song-english", title: "Bad Apple!!" },
				{ songIdentifier: "song-cjk", title: "中文标题" },
			])
			.mockResolvedValueOnce([{ songIdentifier: "song-english", title: "Bad Apple!!" }]);
		const collectionFindMany = vi.fn().mockResolvedValue([
			{ items: [{ songId: "song-english" }, { songId: "song-cjk" }] },
			{ items: [{ songId: "song-english" }, { songId: "song-outside-range" }] },
		]);
		const service = new LetterGameService(
			{
				song: { findMany: songFindMany },
				sheet: {
					findMany: vi.fn().mockResolvedValue([
						{ songIdentifier: "song-english", chartType: "standard" },
						{ songIdentifier: "song-cjk", chartType: "standard" },
					]),
				},
				songCollection: { findMany: collectionFindMany },
			} as never,
			catalogService as never,
		);

		const songs = await (service as any).selectSongs("user-1", {
			selectionMode: "collection",
			selectionConfig: {
				collectionIds: ["collection-1", "collection-2"],
				excludeDeleted: true,
				englishOnly: true,
				minVersion: "maimai PLUS",
				maxVersion: "GreeN",
				categories: ["POPS＆ANIME"],
				chartTypes: ["standard"],
			},
		});

		expect(collectionFindMany).toHaveBeenCalledWith(
			expect.objectContaining({ where: { id: { in: ["collection-1", "collection-2"] }, userId: "user-1", deletedAt: null } }),
		);
		expect(songFindMany).toHaveBeenNthCalledWith(
			1,
			{
				where: {
					songIdentifier: { in: ["song-english", "song-cjk", "song-outside-range"] },
					disabled: false,
					category: { in: ["POPS＆ANIME"] },
					sheets: { some: { chartType: { in: ["standard", "std", "sd"] }, disabled: false } },
					version: { in: ["maimai PLUS", "GreeN"] },
				},
				select: { songIdentifier: true, title: true },
			},
		);
		expect(songs.map((song: { songIdentifier: string }) => song.songIdentifier)).toEqual(["song-english"]);
	});

	it("excludes closed and empty rooms from the public lobby query", async () => {
		const findMany = vi.fn().mockResolvedValue([]);
		const service = new LetterGameService({ letterGameRoom: { findMany } } as never, catalogService as never);

		await service.listPublicRooms();

		expect(findMany).toHaveBeenCalledWith(
			expect.objectContaining({
				where: { visibility: "public", status: "open", members: { some: { status: "accepted" } } },
			}),
		);
	});

	it("rejects reads for dissolved rooms", async () => {
		const service = new LetterGameService(
			{
				letterGameRoom: { findFirst: vi.fn().mockResolvedValue({ status: "closed", members: [], matches: [] }) },
			} as never,
			catalogService as never,
		);

		await expect(service.getRoom("user-1", "ABC234")).rejects.toMatchObject({ code: "room_closed", status: 410 });
	});

	it("rejects reads for non-members of public rooms", async () => {
		const service = new LetterGameService(
			{
				letterGameRoom: {
					findFirst: vi.fn().mockResolvedValue({
						status: "open",
						visibility: "public",
						members: [{ userId: "user-2", status: "accepted" }],
						matches: [],
					}),
				},
			} as never,
			catalogService as never,
		);

		await expect(service.getRoom("user-1", "ABC234")).rejects.toMatchObject({ code: "room_access_denied", status: 403 });
	});

	it("lets every accepted player return to a finished room without reapproval", async () => {
		const memberUpdateMany = vi.fn();
		const roomUpdate = vi.fn();
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ status: "open", hostMode: "fixed", hostUserId: "host-1" }),
				update: roomUpdate,
			},
			letterGameRoomMember: {
				findUnique: vi.fn().mockResolvedValue({ status: "accepted" }),
				findMany: vi.fn(),
				updateMany: memberUpdateMany,
			},
			letterGameMatch: {
				findFirst: vi.fn().mockResolvedValue({ status: "finished", hostUserId: "host-1" }),
			},
		};
		const { service } = createTransactionService(tx);
		vi.spyOn(service, "getRoom").mockResolvedValue({ code: "ABC234" } as never);

		await expect(service.prepareReopen("player-2", "room-1")).resolves.toEqual({ code: "ABC234" });
		expect(memberUpdateMany).not.toHaveBeenCalled();
		expect(roomUpdate).not.toHaveBeenCalled();
	});

	it("rejects multi-grapheme character actions before starting a transaction", async () => {
		const transaction = vi.fn();
		const service = new LetterGameService({ $transaction: transaction } as never, catalogService as never);

		await expect(
			service.applyAction("user-1", "match-1", "action-1", 0, {
				kind: "open_character",
				character: "AB",
			}),
		).rejects.toMatchObject({ code: "invalid_character", status: 400 });
		expect(transaction).not.toHaveBeenCalled();
	});

	it("matches a guess against every active song title and alias", async () => {
		const songs = [
			{
				id: "song-1",
				slotId: "slot-1",
				songIdentifier: "song-1",
				title: "Alpha",
				aliases: ["A"],
				status: "active",
				revealedIndices: [],
			},
			{
				id: "song-2",
				slotId: "slot-2",
				songIdentifier: "song-2",
				title: "Beta",
				aliases: [],
				status: "active",
				revealedIndices: [],
			},
		];
		const songUpdate = vi.fn().mockResolvedValue(undefined);
		const matchUpdate = vi.fn().mockResolvedValue({ revision: 1, status: "active" });
		const actionCreate = vi.fn().mockResolvedValue(undefined);
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameMatch: {
				findUnique: vi
					.fn()
					.mockResolvedValueOnce({ roomId: "room-1" })
					.mockResolvedValueOnce({
						id: "match-1",
						status: "active",
						revision: 0,
						turnOrder: ["user-1"],
						currentTurnIndex: 0,
						noProgressRounds: 0,
						players: [{ id: "player-1", userId: "user-1", status: "active", score: 0, scoringEligible: true }],
						songs,
					}),
				update: matchUpdate,
			},
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ status: "open", stalledRoundLimit: 3, turnDurationSeconds: 30 }),
			},
			letterGameAction: { findFirst: vi.fn().mockResolvedValue(null), create: actionCreate },
			letterGameMatchSong: { update: songUpdate, count: vi.fn().mockResolvedValue(1) },
			letterGameMatchPlayer: { update: vi.fn().mockResolvedValue(undefined) },
		};
		const { service } = createTransactionService(tx);

		await expect(
			service.applyAction("user-1", "match-1", "action-1", 0, {
				kind: "guess_song",
				guess: " a ",
			}),
		).resolves.toMatchObject({ correct: true, blind: true, points: 15 });
		expect(songUpdate).toHaveBeenCalledWith(expect.objectContaining({ where: { id: "song-1" } }));
		expect(actionCreate).toHaveBeenCalled();
	});

	it("rejects ambiguous title or alias guesses without consuming the turn", async () => {
		const songs = [
			{
				id: "song-1",
				slotId: "slot-1",
				songIdentifier: "song-1",
				title: "Alpha",
				aliases: ["Same"],
				status: "active",
				revealedIndices: [],
			},
			{
				id: "song-2",
				slotId: "slot-2",
				songIdentifier: "song-2",
				title: "Beta",
				aliases: ["Same"],
				status: "active",
				revealedIndices: [],
			},
		];
		const matchUpdate = vi.fn();
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameMatch: {
				findUnique: vi
					.fn()
					.mockResolvedValueOnce({ roomId: "room-1" })
					.mockResolvedValueOnce({
						status: "active",
						revision: 0,
						turnOrder: ["user-1"],
						currentTurnIndex: 0,
						players: [{ id: "player-1", userId: "user-1", status: "active", score: 0, scoringEligible: true }],
						songs,
					}),
				update: matchUpdate,
			},
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ status: "open", stalledRoundLimit: 3, turnDurationSeconds: 30 }),
			},
			letterGameAction: { findFirst: vi.fn().mockResolvedValue(null) },
		};
		const { service } = createTransactionService(tx);

		await expect(
			service.applyAction("user-1", "match-1", "action-1", 0, {
				kind: "guess_song",
				guess: "same",
			}),
		).rejects.toMatchObject({ code: "ambiguous_song_guess", status: 409 });
		expect(matchUpdate).not.toHaveBeenCalled();
	});

	it("rejects purchasing a hint that is already known publicly or privately", async () => {
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameMatch: {
				findUnique: vi
					.fn()
					.mockResolvedValueOnce({ roomId: "room-1" })
					.mockResolvedValueOnce({
						status: "active",
						revision: 0,
						turnOrder: ["user-1"],
						currentTurnIndex: 0,
						players: [{ id: "player-1", userId: "user-1", status: "active", score: 100, scoringEligible: true }],
						songs: [{ id: "song-1", slotId: "slot-1", songIdentifier: "song-1", status: "active", revealedIndices: [] }],
					}),
			},
			letterGameRoom: { findUnique: vi.fn().mockResolvedValue({ status: "open", publicHintCost: 5, privateHintCost: 10 }) },
			letterGameAction: { findFirst: vi.fn().mockResolvedValue(null) },
			letterGamePlayerFact: {
				findMany: vi.fn().mockResolvedValue([{ factType: "version", visibility: "public", value: "DX" }]),
			},
			letterGameMatchPlayer: { update: vi.fn() },
		};
		const { service } = createTransactionService(tx);

		await expect(
			service.applyAction("user-1", "match-1", "action-1", 0, {
				kind: "buy_hint",
				slotId: "slot-1",
				hintType: "version",
				visibility: "private",
			}),
		).rejects.toMatchObject({ code: "hint_already_known", status: 409 });
		expect(tx.letterGameMatchPlayer.update).not.toHaveBeenCalled();
	});

	it("uses the master constant until remaster existence is known", async () => {
		const findWhiteFact = vi.fn().mockResolvedValueOnce(null).mockResolvedValueOnce({ value: true });
		const tx = {
			letterGamePlayerFact: { findFirst: findWhiteFact },
			sheet: {
				findMany: vi.fn().mockResolvedValue([
					{ difficulty: "Master", internalLevelValue: { toString: () => "13.5" }, levelValue: null },
					{ difficulty: "Re:MASTER", internalLevelValue: { toString: () => "14.2" }, levelValue: null },
				]),
			},
		};
		const service = new LetterGameService({} as never, catalogService as never);
		const input = { kind: "buy_hint", slotId: "slot-1", hintType: "constant", visibility: "public" } as const;

		await expect((service as any).resolveHintValue(input, "song-1", "user-1", "match-1", "song-row-1", tx)).resolves.toEqual({
			difficulty: "Master",
			value: 13.5,
		});
		await expect((service as any).resolveHintValue(input, "song-1", "user-1", "match-1", "song-row-1", tx)).resolves.toEqual({
			difficulty: "Re:MASTER",
			value: 14.2,
		});
	});

	it("returns the higher master or remaster constant after remaster is confirmed", async () => {
		const tx = {
			letterGamePlayerFact: { findFirst: vi.fn().mockResolvedValue({ value: true }) },
			sheet: {
				findMany: vi.fn().mockResolvedValue([
					{ difficulty: "master", internalLevelValue: 14.4, levelValue: null },
					{ difficulty: "remaster", internalLevelValue: 14.1, levelValue: null },
				]),
			},
		};
		const service = new LetterGameService({} as never, catalogService as never);
		const input = { kind: "buy_hint", slotId: "slot-1", hintType: "constant", visibility: "private" } as const;

		await expect((service as any).resolveHintValue(input, "song-1", "user-1", "match-1", "song-row-1", tx)).resolves.toEqual({
			difficulty: "master",
			value: 14.4,
		});
	});

	it("serializes a match when the selected song has no master or remaster chart", async () => {
		const match = {
			id: "match-1",
			room: { code: "ABC234" },
			status: "active",
			revision: 0,
			turnOrder: ["user-1"],
			currentTurnIndex: 0,
			turnDeadline: null,
			noProgressRounds: 0,
			players: [{ userId: "user-1", score: 0, turnOrder: 0, status: "active", scoringEligible: true }],
			songs: [
				{
					id: "song-row-1",
					slotId: "slot-1",
					songIdentifier: "song-1",
					title: "ABC",
					revealedIndices: [],
					status: "active",
					completionReason: null,
					completedByUserId: null,
				},
			],
			facts: [],
			actions: [],
		};
		const prisma = {
			letterGameMatch: { findUnique: vi.fn().mockResolvedValue(match) },
			profile: { findMany: vi.fn().mockResolvedValue([]) },
			user: { findMany: vi.fn().mockResolvedValue([{ id: "user-1", username: "tester" }]) },
			song: { findMany: vi.fn().mockResolvedValue([]) },
			sheet: { findMany: vi.fn().mockResolvedValue([]) },
		};
		const service = new LetterGameService(prisma as never, catalogService as never);

		await expect(service.getMatchSnapshot("user-1", "match-1")).resolves.toMatchObject({
			songs: [{ maxConstant: null, title: "***" }],
		});
	});

	it("advances a single-player turn after the deadline", async () => {
		const update = vi.fn().mockResolvedValue(undefined);
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameMatch: {
				findUnique: vi
					.fn()
					.mockResolvedValueOnce({ roomId: "room-1" })
					.mockResolvedValueOnce({
						id: "match-1",
						status: "active",
						turnDeadline: new Date(Date.now() - 1_000),
						turnOrder: ["user-1"],
						currentTurnIndex: 0,
						noProgressRounds: 0,
						players: [{ userId: "user-1", status: "active" }],
					}),
				update,
			},
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ stalledRoundLimit: 3, turnDurationSeconds: 30 }),
			},
		};
		const root = {
			letterGameMatch: {
				findMany: vi.fn().mockResolvedValue([{ id: "match-1" }]),
			},
		};
		const { service } = createTransactionService(tx, root);

		await expect(service.expireDueMatches()).resolves.toEqual(["match-1"]);
		expect(update).toHaveBeenCalledWith(
			expect.objectContaining({
				where: { id: "match-1" },
				data: expect.objectContaining({
					currentTurnIndex: 0,
					noProgressRounds: 1,
					revision: { increment: 1 },
				}),
			}),
		);
		const updateData = update.mock.calls[0]?.[0]?.data as Record<string, unknown>;
		expect(updateData.turnDeadline).toBeInstanceOf(Date);
		expect(updateData.status).toBeUndefined();
	});

	it("removes a member when a finished-match connection disappears", async () => {
		const memberUpdate = vi.fn().mockResolvedValue(undefined);
		const roomUpdateMany = vi.fn().mockResolvedValue({ count: 1 });
		const tx = {
			$executeRawUnsafe: vi.fn().mockResolvedValue(undefined),
			letterGameRoom: {
				findUnique: vi.fn().mockResolvedValue({ id: "room-1", status: "open", hostUserId: "user-1", turnDurationSeconds: 30 }),
				findFirst: vi.fn(),
				updateMany: roomUpdateMany,
			},
			letterGameRoomMember: {
				findUnique: vi.fn().mockResolvedValue({ id: "member-1", userId: "user-1", status: "accepted" }),
				findFirst: vi.fn(),
				update: memberUpdate,
				updateMany: vi.fn(),
				count: vi.fn().mockResolvedValue(0),
			},
			letterGameMatch: {
				findFirst: vi.fn().mockResolvedValueOnce(null).mockResolvedValueOnce({ status: "finished" }),
				findMany: vi.fn().mockResolvedValue([]),
			},
		};
		const { service } = createTransactionService(tx);

		await expect(service.leaveFinishedRoomOnDisconnect("user-1", "room-1")).resolves.toEqual({ left: true, dissolved: true });
		expect(memberUpdate).toHaveBeenCalledWith(expect.objectContaining({ data: { status: "left", leftAt: expect.any(Date) } }));
		expect(roomUpdateMany).toHaveBeenCalledWith(
			expect.objectContaining({ data: expect.objectContaining({ status: "closed" }) }),
		);
	});
});
