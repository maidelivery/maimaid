import { describe, expect, it } from "vitest";
import {
	chunk,
	dedupeFirstWins,
	dedupeLastWins,
	sheetUpsertKey,
	songUpsertKey,
	sqliteLiteral,
} from "../src/services/catalog.utils.js";

describe("chunk", () => {
	it("splits into batches of at most the given size", () => {
		expect(chunk([1, 2, 3, 4, 5], 2)).toEqual([[1, 2], [3, 4], [5]]);
	});

	it("returns a single batch when the input fits", () => {
		expect(chunk([1, 2, 3], 500)).toEqual([[1, 2, 3]]);
	});

	it("returns no batches for an empty input", () => {
		expect(chunk([], 10)).toEqual([]);
	});

	it("rejects a size below one so a bad constant cannot loop forever", () => {
		expect(() => chunk([1], 0)).toThrow();
	});
});

describe("dedupeLastWins", () => {
	it("keeps the final row for a repeated key, matching sequential upserts", () => {
		const rows = [
			{ songIdentifier: "a", title: "first" },
			{ songIdentifier: "b", title: "other" },
			{ songIdentifier: "a", title: "last" },
		];
		expect(dedupeLastWins(rows, songUpsertKey)).toEqual([
			{ songIdentifier: "a", title: "last" },
			{ songIdentifier: "b", title: "other" },
		]);
	});

	it("leaves rows with distinct keys untouched", () => {
		const rows = [{ songIdentifier: "a" }, { songIdentifier: "b" }];
		expect(dedupeLastWins(rows, songUpsertKey)).toEqual(rows);
	});
});

describe("dedupeFirstWins", () => {
	it("keeps the first row for a repeated key, matching skipDuplicates", () => {
		const rows = [
			{ songIdentifier: "a", chartType: "dx", difficulty: "master", level: "13" },
			{ songIdentifier: "a", chartType: "dx", difficulty: "master", level: "14" },
		];
		expect(dedupeFirstWins(rows, sheetUpsertKey)).toEqual([rows[0]]);
	});

	it("treats sheets of the same song as distinct across chart type and difficulty", () => {
		const rows = [
			{ songIdentifier: "a", chartType: "dx", difficulty: "master" },
			{ songIdentifier: "a", chartType: "standard", difficulty: "master" },
			{ songIdentifier: "a", chartType: "dx", difficulty: "expert" },
		];
		expect(dedupeFirstWins(rows, sheetUpsertKey)).toEqual(rows);
	});
});

describe("sheetUpsertKey", () => {
	it("does not collide when a separator-like value appears in a field", () => {
		const a = sheetUpsertKey({ songIdentifier: "a", chartType: "b:c", difficulty: "d" });
		const b = sheetUpsertKey({ songIdentifier: "a", chartType: "b", difficulty: "c:d" });
		expect(a).not.toBe(b);
	});
});

describe("sqliteLiteral", () => {
	it("encodes typed SQLite values without bind variables", () => {
		expect(sqliteLiteral(null)).toBe("NULL");
		expect(sqliteLiteral(true)).toBe("1");
		expect(sqliteLiteral(12.5)).toBe("12.5");
		expect(sqliteLiteral(42n)).toBe("42");
		expect(sqliteLiteral(new Date("2026-08-20T00:00:00.000Z"))).toBe("'2026-08-20T00:00:00.000Z'");
	});

	it("escapes quotes in strings", () => {
		expect(sqliteLiteral("It's maimai")).toBe("'It''s maimai'");
	});

	it("rejects non-finite numbers", () => {
		expect(() => sqliteLiteral(Number.NaN)).toThrow("must be finite");
	});
});
