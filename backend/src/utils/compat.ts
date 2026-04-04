const DIFFICULTY_BY_INDEX = ["basic", "advanced", "expert", "master", "remaster"];

/**
 * Converts an LXNS song ID + chart type into the local (bundle) song ID.
 *
 * ID systems:
 *   Local (songid.json / Prisma):
 *     - Pre-DX STD songs: id < 10000
 *     - DX-era songs:     10000 < id < 100000  (base + 10000)
 *     - Utage songs:      id >= 100000
 *
 *   LXNS:
 *     - Uses ONE id per song for both STD and DX charts, distinguished by `type`
 *     - All non-Utage ids are < 10000 (the base id)
 *     - Utage ids >= 100000 (same as local)
 *
 *   Diving Fish:
 *     - Identical to local. No conversion needed.
 *
 * Conversion rule for LXNS:
 *   - type = "standard" / "SD" → local id = lxns_id  (direct)
 *   - type = "dx" / "DX"      → local id = lxns_id + 10000
 *   - Utage (id >= 100000)     → local id = lxns_id  (direct)
 */
export const lxnsSongIdToLocal = (lxnsSongId: number, chartType: string): number => {
	// Utage songs share the same ID space
	if (lxnsSongId >= 100000) {
		return lxnsSongId;
	}

	const normalized = chartType.trim().toLowerCase();
	if (normalized === "dx") {
		return lxnsSongId + 10000;
	}

	// "standard", "sd", "std", or anything else → direct
	return lxnsSongId;
};

/**
 * @deprecated Use `lxnsSongIdToLocal` with chart type instead.
 * Kept only for backward compatibility with existing callers that haven't
 * been migrated yet.
 */
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
		remaster: 4,
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
