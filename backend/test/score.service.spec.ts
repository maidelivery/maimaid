import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { ScoreService } from "../src/services/score.service.js";

const sheet = (id: bigint) => ({
	id,
	songIdentifier: id.toString(),
	chartType: "dx",
	difficulty: "master",
	song: { title: `Song ${id}` },
});

describe("ScoreService D1 writes", () => {
	it("chunks large best-score lookups for D1", async () => {
		const create = vi.fn().mockResolvedValue({});
		const sheetFindMany = vi.fn().mockImplementation(async (args: { where: { id: { in: bigint[] } } }) =>
			args.where.id.in.map((id) => sheet(id)),
		);
		const bestScoreFindMany = vi.fn().mockResolvedValue([]);
		const database = {
			$transaction: vi.fn(),
			sheet: { findMany: sheetFindMany },
			bestScore: {
				findMany: bestScoreFindMany,
				create,
				createMany: vi.fn(),
				update: vi.fn(),
			},
		};
		const service = new ScoreService(database as never, { DATABASE_DIALECT: "sqlite" } as never);
		const scores = Array.from({ length: 51 }, (_, index) => ({ sheetId: BigInt(index + 1), achievements: 100 }));

		const result = await service.bulkUpsertBestScores("profile", scores, "df_import");

		expect(result.applied).toHaveLength(51);
		expect(sheetFindMany).toHaveBeenCalledTimes(3);
		expect(bestScoreFindMany).toHaveBeenCalledTimes(3);
		for (const call of [...sheetFindMany.mock.calls, ...bestScoreFindMany.mock.calls]) {
			const input = (call[0] as { where: { id?: { in: bigint[] }; sheetId?: { in: bigint[] } } }).where;
			const values = input.id?.in ?? input.sheetId?.in ?? [];
			expect(values.length).toBeLessThanOrEqual(25);
		}
	});

	it("chunks large play-record duplicate lookups for D1", async () => {
		const create = vi.fn().mockResolvedValue({});
		const sheetFindMany = vi.fn().mockImplementation(async (args: { where: { id: { in: bigint[] } } }) =>
			args.where.id.in.map((id) => sheet(id)),
		);
		const playRecordFindMany = vi.fn().mockResolvedValue([]);
		const database = {
			$transaction: vi.fn(),
			sheet: { findMany: sheetFindMany },
			playRecord: {
				findMany: playRecordFindMany,
				create,
				createMany: vi.fn(),
			},
		};
		const service = new ScoreService(database as never, { DATABASE_DIALECT: "sqlite" } as never);
		const records = Array.from({ length: 51 }, (_, index) => ({
			sheetId: BigInt(index + 1),
			achievements: 100,
			playTime: new Date(`2026-08-20T10:${String(index).padStart(2, "0")}:00.000Z`),
		}));

		const result = await service.bulkInsertPlayRecords("profile", records, "df_import");

		expect(result.created).toHaveLength(51);
		expect(sheetFindMany).toHaveBeenCalledTimes(3);
		expect(playRecordFindMany).toHaveBeenCalledTimes(3);
		for (const call of [...sheetFindMany.mock.calls, ...playRecordFindMany.mock.calls]) {
			const input = (call[0] as { where: { id?: { in: bigint[] }; sheetId?: { in: bigint[] } } }).where;
			const values = input.id?.in ?? input.sheetId?.in ?? [];
			expect(values.length).toBeLessThanOrEqual(25);
		}
	});

	it("creates best scores without Prisma transactions or createMany", async () => {
		const create = vi.fn().mockResolvedValue({});
		const database = {
			$transaction: vi.fn(),
			sheet: { findMany: vi.fn().mockResolvedValue([sheet(1n), sheet(2n)]) },
			bestScore: {
				findMany: vi.fn().mockResolvedValue([]),
				create,
				createMany: vi.fn(),
				update: vi.fn(),
			},
		};
		const service = new ScoreService(database as never, { DATABASE_DIALECT: "sqlite" } as never);

		const result = await service.bulkUpsertBestScores(
			"profile",
			[
				{ sheetId: 1n, achievements: 100 },
				{ sheetId: 2n, achievements: 99 },
			],
			"sync_push",
		);

		expect(result.applied).toEqual([
			{ sheetId: 1n, action: "created" },
			{ sheetId: 2n, action: "created" },
		]);
		expect(create).toHaveBeenCalledTimes(2);
		expect(database.bestScore.createMany).not.toHaveBeenCalled();
		expect(database.$transaction).not.toHaveBeenCalled();
	});

	it("creates play records without Prisma transactions or createMany", async () => {
		const create = vi.fn().mockResolvedValue({});
		const database = {
			$transaction: vi.fn(),
			sheet: { findMany: vi.fn().mockResolvedValue([sheet(1n), sheet(2n)]) },
			playRecord: {
				findMany: vi.fn().mockResolvedValue([]),
				create,
				createMany: vi.fn(),
			},
		};
		const service = new ScoreService(database as never, { DATABASE_DIALECT: "sqlite" } as never);

		const result = await service.bulkInsertPlayRecords(
			"profile",
			[
				{ sheetId: 1n, achievements: 100, playTime: "2026-08-20T10:00:00.000Z" },
				{ sheetId: 2n, achievements: 99, playTime: "2026-08-20T10:01:00.000Z" },
			],
			"sync_push",
		);

		expect(result.created).toEqual([{ sheetId: 1n }, { sheetId: 2n }]);
		expect(create).toHaveBeenCalledTimes(2);
		expect(database.playRecord.createMany).not.toHaveBeenCalled();
		expect(database.$transaction).not.toHaveBeenCalled();
	});
});
