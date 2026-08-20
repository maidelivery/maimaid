/**
 * Pure helpers backing {@link CatalogService.applySnapshotPayload}.
 *
 * A catalog apply writes every song and sheet in the upstream payload. Those
 * writes are batched into multi-row `INSERT ... ON CONFLICT DO UPDATE`
 * statements, which imposes two constraints that these helpers satisfy:
 *
 * 1. PostgreSQL caps a statement at 65535 bind parameters, so rows are chunked.
 * 2. `ON CONFLICT ... DO UPDATE` raises "cannot affect row a second time" when a
 *    single statement carries two rows sharing a conflict key, so upstream
 *    duplicates are collapsed first.
 */

/**
 * NUL cannot appear in the catalog's identifier, chart type or difficulty
 * values, so it joins composite keys without risking a collision between, say,
 * `("a b", "c")` and `("a", "b c")`.
 */
const DEDUPE_KEY_SEPARATOR = "\u0000";

/** Split `items` into consecutive batches of at most `size`. */
export const chunk = <T>(items: T[], size: number): T[][] => {
	if (size < 1) {
		throw new Error("chunk size must be at least 1");
	}
	const batches: T[][] = [];
	for (let index = 0; index < items.length; index += size) {
		batches.push(items.slice(index, index + size));
	}
	return batches;
};

/**
 * Collapse duplicates keeping the *last* occurrence, matching the sequential
 * `upsert` calls this replaced for songs (a later entry overwrote an earlier
 * one). Relative order follows each key's first appearance.
 */
export const dedupeLastWins = <T>(rows: T[], keyOf: (row: T) => string): T[] => {
	const byKey = new Map<string, T>();
	for (const row of rows) {
		byKey.set(keyOf(row), row);
	}
	return Array.from(byKey.values());
};

/**
 * Collapse duplicates keeping the *first* occurrence, matching
 * `createMany({ skipDuplicates: true })` which this replaced for sheets (a later
 * entry was dropped).
 */
export const dedupeFirstWins = <T>(rows: T[], keyOf: (row: T) => string): T[] => {
	const byKey = new Map<string, T>();
	for (const row of rows) {
		const key = keyOf(row);
		if (!byKey.has(key)) {
			byKey.set(key, row);
		}
	}
	return Array.from(byKey.values());
};

/** Conflict key for `songs`: its primary key. */
export const songUpsertKey = (row: { songIdentifier: string }): string => row.songIdentifier;

/** Conflict key for `sheets`: the stable business key, not the bigserial id. */
export const sheetUpsertKey = (row: { songIdentifier: string; chartType: string; difficulty: string }): string =>
	[row.songIdentifier, row.chartType, row.difficulty].join(DEDUPE_KEY_SEPARATOR);

export type SqliteLiteralValue = string | number | bigint | boolean | Date | null;

/** Encode one trusted, typed value for a SQLite statement without bind variables. */
export const sqliteLiteral = (value: SqliteLiteralValue): string => {
	if (value === null) return "NULL";
	if (typeof value === "boolean") return value ? "1" : "0";
	if (typeof value === "number") {
		if (!Number.isFinite(value)) throw new Error("SQLite numeric literal must be finite");
		return String(value);
	}
	if (typeof value === "bigint") return String(value);
	const text = value instanceof Date ? value.toISOString() : value;
	return `'${text.replaceAll("'", "''")}'`;
};
