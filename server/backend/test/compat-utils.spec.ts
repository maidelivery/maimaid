import { describe, expect, it } from "vitest";
import { difficultyByLevelIndex, levelIndexByDifficulty, normalizeChartType, normalizeLxnsSongId } from "../src/utils/compat.js";

describe("compat utils", () => {
  it("normalizes LXNS song ids with modulo rule", () => {
    expect(normalizeLxnsSongId(9999)).toBe(9999);
    expect(normalizeLxnsSongId(10001)).toBe(1);
    expect(normalizeLxnsSongId(12345)).toBe(2345);
  });

  it("keeps utage-like ids greater than 100000", () => {
    expect(normalizeLxnsSongId(100001)).toBe(100001);
    expect(normalizeLxnsSongId(145678)).toBe(145678);
  });

  it("maps difficulty index both directions", () => {
    expect(difficultyByLevelIndex(0)).toBe("basic");
    expect(difficultyByLevelIndex(4)).toBe("remaster");
    expect(difficultyByLevelIndex(99)).toBeNull();
    expect(levelIndexByDifficulty("master")).toBe(3);
    expect(levelIndexByDifficulty("ReMaster")).toBe(4);
  });

  it("normalizes chart type aliases", () => {
    expect(normalizeChartType("sd")).toBe("standard");
    expect(normalizeChartType("STD")).toBe("standard");
    expect(normalizeChartType("dx")).toBe("dx");
    expect(normalizeChartType("utage")).toBe("utage");
  });
});
