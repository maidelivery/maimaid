const DIFFICULTY_BY_INDEX = ["basic", "advanced", "expert", "master", "remaster"];

export const normalizeLxnsSongId = (songId: number): number => {
  if (songId > 100000) {
    return songId;
  }
  if (songId > 10000) {
    return songId % 10000;
  }
  return songId;
};

export const difficultyByLevelIndex = (levelIndex: number): string | null => {
  if (levelIndex < 0 || levelIndex > DIFFICULTY_BY_INDEX.length - 1) {
    return null;
  }
  return DIFFICULTY_BY_INDEX[levelIndex] ?? null;
};

export const levelIndexByDifficulty = (difficulty: string): number => {
  const lookup: Record<string, number> = {
    basic: 0,
    advanced: 1,
    expert: 2,
    master: 3,
    remaster: 4
  };
  return lookup[difficulty.toLowerCase()] ?? 0;
};

export const normalizeChartType = (value?: string): string | null => {
  if (!value) {
    return null;
  }
  const lower = value.trim().toLowerCase();
  if (lower === "sd" || lower === "std" || lower === "standard") {
    return "standard";
  }
  if (lower === "dx") {
    return "dx";
  }
  if (lower === "utage") {
    return "utage";
  }
  return lower;
};
