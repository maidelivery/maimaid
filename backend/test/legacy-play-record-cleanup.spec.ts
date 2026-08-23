import { describe, expect, it } from "vitest";
import { removeLegacyDivingFishPlayRecords } from "../src/services/legacy-play-record-cleanup.js";

describe("removeLegacyDivingFishPlayRecords", () => {
	it("removes large same-millisecond batches produced by legacy Diving Fish imports", () => {
		const legacy = Array.from({ length: 100 }, (_, index) => ({
			playTime: "2026-08-20T18:19:00.134Z",
			index,
		}));
		const realRecord = { playTime: "2026-08-20T18:20:00.000Z", index: 100 };

		expect(removeLegacyDivingFishPlayRecords([...legacy, realRecord])).toEqual([realRecord]);
	});

	it("preserves normal sessions sharing a timestamp", () => {
		const records = Array.from({ length: 4 }, (_, index) => ({
			playTime: "2026-08-20T18:19:00.134Z",
			index,
		}));

		expect(removeLegacyDivingFishPlayRecords(records)).toEqual(records);
	});
});
