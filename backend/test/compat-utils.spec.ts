import { describe, expect, it } from "vitest";
import {
  difficultyByLevelIndex,
  levelIndexByDifficulty,
  lxnsSongIdToLocal,
  normalizeChartType,
  normalizeLxnsSongId
} from "../src/utils/compat.js";

describe("compat utils", () => {
  it("normalizes LXNS song ids with modulo rule (legacy)", () => {
    expect(normalizeLxnsSongId(9999)).toBe(9999);
    expect(normalizeLxnsSongId(10001)).toBe(1);
    expect(normalizeLxnsSongId(12345)).toBe(2345);
  });

  it("keeps utage-like ids greater than 100000 (legacy)", () => {
    expect(normalizeLxnsSongId(100001)).toBe(100001);
    expect(normalizeLxnsSongId(145678)).toBe(145678);
  });

  it("converts LXNS song id + chart type to local id", () => {
    // STD charts: LXNS id maps directly
    expect(lxnsSongIdToLocal(30, "standard")).toBe(30);
    expect(lxnsSongIdToLocal(8, "SD")).toBe(8);
    expect(lxnsSongIdToLocal(1662, "standard")).toBe(1662);

    // DX charts: LXNS id + 10000
    expect(lxnsSongIdToLocal(30, "dx")).toBe(10030);
    expect(lxnsSongIdToLocal(44, "DX")).toBe(10044);
    expect(lxnsSongIdToLocal(1662, "dx")).toBe(11662);

    // Utage: >= 100000, direct regardless of type
    expect(lxnsSongIdToLocal(100018, "dx")).toBe(100018);
    expect(lxnsSongIdToLocal(100199, "standard")).toBe(100199);
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
