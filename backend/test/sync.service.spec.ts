import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { SyncService } from "../src/services/sync.service.js";

describe("SyncService snapshots", () => {
	it("skips play records when a lightweight snapshot is requested", async () => {
		const playRecordFindMany = vi.fn().mockResolvedValue([]);
		const database = {
			profile: { findMany: vi.fn().mockResolvedValue([]) },
			bestScore: { findMany: vi.fn().mockResolvedValue([]) },
			playRecord: { findMany: playRecordFindMany },
		};
		const service = new SyncService(database as never);

		const snapshot = await service.buildSnapshot("user", ["profile"], false);

		expect(snapshot.records).toEqual([]);
		expect(playRecordFindMany).not.toHaveBeenCalled();
	});
});
