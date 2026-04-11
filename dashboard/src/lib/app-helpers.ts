import type { Alias, Sheet as SongSheet, Song, SongFilterSettings } from "@/components/songs/types";
import type { CatalogVersionItem, SongFilterSnapshot, SongIdItem } from "@/lib/app-types";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;
const PASSWORD_COMPLEXITY_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,}$/u;
const USERNAME_PATTERN = /^[\p{L}\p{N}_.-]+$/u;

export const BACKEND_URL = (process.env.NEXT_PUBLIC_BACKEND_URL ?? "").trim().replace(/\/+$/u, "");
export const LXNS_OAUTH_CLIENT_ID = (process.env.NEXT_PUBLIC_LXNS_CLIENT_ID ?? "").trim();
export const COVER_BASE_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover";
export const SONG_FAVORITE_STORAGE_KEY = "dashboard.songFavorites";
export const SONG_SORT_OPTION_STORAGE_KEY = "dashboard.songSortOption";
export const SONG_SORT_ASC_STORAGE_KEY = "dashboard.songSortAscending";
export const SONG_HIDE_DELETED_STORAGE_KEY = "dashboard.hideDeletedSongs";
export const CHART_TYPE_ORDER: Record<string, number> = {
	standard: 1,
	std: 1,
	dx: 2,
	utage: 3,
};
export const PASSWORD_COMPLEXITY_HINT = "至少8字符，有大小写字母，数字和特殊符号";
export const USERNAME_HINT = "2-32 个字符，可使用字母、数字、下划线、连字符和句点";
export const DEFAULT_SONG_FILTERS: SongFilterSettings = {
	selectedCategories: new Set<string>(),
	selectedVersions: new Set<string>(),
	selectedDifficulties: new Set<string>(),
	selectedTypes: new Set<string>(),
	minLevel: 1,
	maxLevel: 15,
	showFavoritesOnly: false,
	hideDeletedSongs: false,
};

export function getLocalStorageItem(key: string): string | null {
	if (typeof window === "undefined") {
		return null;
	}

	const storage = window.localStorage;
	if (!storage || typeof storage.getItem !== "function") {
		return null;
	}

	return storage.getItem(key);
}

export function normalizeSearchText(value: string) {
	return value.trim().normalize("NFKC").toLocaleLowerCase();
}

export function compactSearchText(value: string) {
	return normalizeSearchText(value).replace(/\s+/gu, "");
}

export function localizedStandardContains(text: string, query: string) {
	const normalizedText = normalizeSearchText(text);
	const normalizedQuery = normalizeSearchText(query);
	if (!normalizedQuery) {
		return true;
	}
	return normalizedText.includes(normalizedQuery);
}

export function isValidEmailAddress(value: string) {
	return EMAIL_PATTERN.test(value.trim());
}

export function isPasswordComplexEnough(value: string) {
	return PASSWORD_COMPLEXITY_PATTERN.test(value);
}

export function normalizeUsername(value: string) {
	return value.normalize("NFKC").trim();
}

export function isValidUsername(value: string) {
	const normalized = normalizeUsername(value);
	const length = Array.from(normalized).length;
	return length >= 2 && length <= 32 && !/\s/u.test(normalized) && USERNAME_PATTERN.test(normalized);
}

export function songSnapshotMatchesSearch(snapshot: SongFilterSnapshot, searchText: string) {
	const trimmedSearch = searchText.trim();
	if (!trimmedSearch) {
		return true;
	}

	const normalizedSearch = compactSearchText(trimmedSearch);
	const { song, aliases, sheets, songIds } = snapshot;

	const titleMatch =
		localizedStandardContains(song.title, trimmedSearch) ||
		localizedStandardContains(song.title.replace(/\s+/gu, ""), normalizedSearch);
	const artistMatch =
		localizedStandardContains(song.artist, trimmedSearch) ||
		localizedStandardContains(song.artist.replace(/\s+/gu, ""), normalizedSearch);
	const keywordMatch = song.searchKeywords
		? localizedStandardContains(song.searchKeywords, trimmedSearch) ||
			localizedStandardContains(song.searchKeywords.replace(/\s+/gu, ""), normalizedSearch)
		: false;
	const aliasMatch = aliases.some(
		(alias) =>
			localizedStandardContains(alias, trimmedSearch) ||
			localizedStandardContains(alias.replace(/\s+/gu, ""), normalizedSearch),
	);
	const designerMatch = sheets.some((sheet) =>
		sheet.noteDesigner
			? localizedStandardContains(sheet.noteDesigner, trimmedSearch) ||
				localizedStandardContains(sheet.noteDesigner.replace(/\s+/gu, ""), normalizedSearch)
			: false,
	);
	const songIdentifierMatch =
		localizedStandardContains(song.songIdentifier, trimmedSearch) ||
		localizedStandardContains(song.songIdentifier.replace(/\s+/gu, ""), normalizedSearch);
	const idMatch = songIds.some((songId) => String(songId) === trimmedSearch);

	return titleMatch || artistMatch || keywordMatch || aliasMatch || designerMatch || songIdentifierMatch || idMatch;
}

export function expandProviderSongIdCandidates(songId: number) {
	const candidates = new Set<number>();
	if (!Number.isFinite(songId) || songId <= 0) {
		return candidates;
	}

	candidates.add(songId);

	if (songId < 10000) {
		candidates.add(songId + 10000);
	}

	if (songId > 10000 && songId < 100000) {
		const baseId = songId % 10000;
		if (baseId > 0) {
			candidates.add(baseId);
			candidates.add(baseId + 10000);
		}
	}

	if (songId >= 100000) {
		const baseId = songId % 100000;
		if (baseId > 0) {
			candidates.add(baseId);
			if (baseId < 10000) {
				candidates.add(baseId + 10000);
			}
		}
	}

	return candidates;
}

export function parseSongIdItems(value: unknown): SongIdItem[] {
	if (!Array.isArray(value)) {
		return [];
	}

	const rows: SongIdItem[] = [];
	for (const item of value) {
		if (typeof item !== "object" || item === null) {
			continue;
		}
		const record = item as Record<string, unknown>;
		const id = Number(record.id);
		const rawName = typeof record.name === "string" ? record.name : "";
		const trimmedName = rawName.trim();
		const name = trimmedName || rawName;
		if (!Number.isFinite(id) || !name) {
			continue;
		}
		rows.push({
			id: Math.trunc(id),
			name,
		});
	}

	return rows;
}

function toRecord(value: unknown): Record<string, unknown> | null {
	return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
}

function toCleanString(value: unknown): string {
	return typeof value === "string" ? value.trim() : "";
}

function toOptionalString(value: unknown): string | null {
	const trimmed = toCleanString(value);
	return trimmed.length > 0 ? trimmed : null;
}

function toOptionalNumber(value: unknown): number | null {
	const parsed = Number(value);
	return Number.isFinite(parsed) ? parsed : null;
}

function toBoolean(value: unknown): boolean {
	return value === true;
}

function parseSongIdentifier(value: unknown): string {
	if (typeof value === "string") {
		const trimmed = value.trim();
		if (trimmed.length > 0) {
			return trimmed;
		}
	}

	if (typeof value === "number" && Number.isFinite(value)) {
		return String(Math.trunc(value));
	}

	return "";
}

function parseRemoteSongs(value: unknown) {
	const record = toRecord(value);
	const songs = Array.isArray(record?.songs) ? record.songs : [];
	return songs.map((item) => toRecord(item)).filter((item): item is Record<string, unknown> => item !== null);
}

type SongResolverContext = {
	songs: Song[];
	songIdItems: SongIdItem[];
};

type SongResolverIndexes = {
	songIdentifierSet: Set<string>;
	songIdentifierByProviderId: Map<number, string>;
};

function buildSongResolverIndexes(context: SongResolverContext): SongResolverIndexes {
	const songIdentifierSet = new Set(context.songs.map((song) => song.songIdentifier));
	const songIdentifierByNormalizedName = new Map<string, string>();

	for (const song of context.songs) {
		songIdentifierByNormalizedName.set(normalizeSearchText(song.title), song.songIdentifier);
		songIdentifierByNormalizedName.set(compactSearchText(song.title), song.songIdentifier);
		songIdentifierByNormalizedName.set(normalizeSearchText(song.songIdentifier), song.songIdentifier);
		songIdentifierByNormalizedName.set(compactSearchText(song.songIdentifier), song.songIdentifier);
	}

	const songIdentifierByProviderId = new Map<number, string>();
	for (const item of context.songIdItems) {
		const mappedIdentifier =
			songIdentifierByNormalizedName.get(normalizeSearchText(item.name)) ??
			songIdentifierByNormalizedName.get(compactSearchText(item.name));
		if (mappedIdentifier) {
			songIdentifierByProviderId.set(item.id, mappedIdentifier);
		}
	}

	return {
		songIdentifierSet,
		songIdentifierByProviderId,
	};
}

function resolveBundleAliasSongIdentifier(songId: number, indexes: SongResolverIndexes): string {
	const candidates = Array.from(expandProviderSongIdCandidates(songId));
	for (const candidateId of candidates) {
		const mapped = indexes.songIdentifierByProviderId.get(candidateId);
		if (mapped) {
			return mapped;
		}
	}

	for (const candidateId of candidates) {
		const candidateIdentifier = String(candidateId);
		if (indexes.songIdentifierSet.has(candidateIdentifier)) {
			return candidateIdentifier;
		}
	}

	return String(songId);
}

export function parseBundleSongs(value: unknown): Song[] {
	// Legacy parser kept for fallback when catalog APIs are unavailable.
	const rows = parseRemoteSongs(value);
	const songs: Song[] = [];

	for (let index = 0; index < rows.length; index += 1) {
		const row = rows[index]!;
		const songIdentifier = parseSongIdentifier(row.songId ?? row.id);
		if (!songIdentifier) {
			continue;
		}

		const title = toCleanString(row.title);
		if (!title) {
			continue;
		}

		const songId = Number(songIdentifier);
		songs.push({
			songIdentifier,
			songId: Number.isFinite(songId) ? Math.trunc(songId) : 0,
			title,
			artist: toCleanString(row.artist),
			category: toOptionalString(row.category) ?? undefined,
			sortOrder: index,
			version: toOptionalString(row.version),
			releaseDate: toOptionalString(row.releaseDate),
			bpm: toOptionalNumber(row.bpm),
			isLocked: toBoolean(row.isLocked),
			isNew: toBoolean(row.isNew),
			comment: toOptionalString(row.comment),
			imageName: toOptionalString(row.imageName) ?? undefined,
		});
	}

	return songs;
}

export function parseBundleSheets(value: unknown): SongSheet[] {
	// Legacy parser kept for fallback when catalog APIs are unavailable.
	const rows = parseRemoteSongs(value);
	const sheets: SongSheet[] = [];

	for (const songRow of rows) {
		const songIdentifier = parseSongIdentifier(songRow.songId ?? songRow.id);
		if (!songIdentifier) {
			continue;
		}
		const songId = Number(songIdentifier);
		const sheetRows = Array.isArray(songRow.sheets) ? songRow.sheets : [];
		for (let index = 0; index < sheetRows.length; index += 1) {
			const sheetRow = toRecord(sheetRows[index]);
			if (!sheetRow) {
				continue;
			}

			const chartType = toCleanString(sheetRow.type ?? sheetRow.chartType);
			const difficulty = toCleanString(sheetRow.difficulty);
			const level = toCleanString(sheetRow.level);
			if (!chartType || !difficulty || !level) {
				continue;
			}

			const noteCounts = toRecord(sheetRow.noteCounts);
			const regions = toRecord(sheetRow.regions);
			const identity = [songIdentifier, chartType.toLowerCase(), difficulty.toLowerCase(), String(index)].join(":");
			sheets.push({
				id: identity,
				songIdentifier,
				songId: Number.isFinite(songId) ? Math.trunc(songId) : undefined,
				chartType,
				difficulty,
				level,
				version: toOptionalString(sheetRow.version),
				levelValue: toOptionalNumber(sheetRow.levelValue),
				internalLevel: toOptionalString(sheetRow.internalLevel),
				internalLevelValue: toOptionalNumber(sheetRow.internalLevelValue),
				noteDesigner: toOptionalString(sheetRow.noteDesigner),
				tap: toOptionalNumber(noteCounts?.tap),
				hold: toOptionalNumber(noteCounts?.hold),
				slide: toOptionalNumber(noteCounts?.slide),
				touch: toOptionalNumber(noteCounts?.touch),
				breakCount: toOptionalNumber(noteCounts?.break),
				total: toOptionalNumber(noteCounts?.total),
				regionJp: toBoolean(regions?.jp),
				regionIntl: toBoolean(regions?.intl),
				regionUsa: toBoolean(regions?.usa),
				regionCn: toBoolean(regions?.cn),
				isSpecial: toBoolean(sheetRow.isSpecial),
			});
		}
	}

	return sheets;
}

export function parseBundleLxnsAliases(value: unknown, context: SongResolverContext): Alias[] {
	// Legacy parser kept for fallback when catalog APIs are unavailable.
	const record = toRecord(value);
	const source = Array.isArray(value) ? value : Array.isArray(record?.aliases) ? record.aliases : [];
	const aliases: Alias[] = [];
	const unique = new Set<string>();
	const indexes = buildSongResolverIndexes(context);

	for (const item of source) {
		const row = toRecord(item);
		if (!row) {
			continue;
		}
		const songId = Number(row.song_id);
		if (!Number.isFinite(songId)) {
			continue;
		}
		const normalizedSongId = Math.trunc(songId);
		const resolvedSongIdentifier = resolveBundleAliasSongIdentifier(normalizedSongId, indexes);
		const aliasRows = Array.isArray(row.aliases) ? row.aliases : [];
		for (const aliasValue of aliasRows) {
			if (typeof aliasValue !== "string") {
				continue;
			}
			const aliasText = aliasValue.trim();
			if (!aliasText) {
				continue;
			}
			const id = `lxns:${resolvedSongIdentifier}:${compactSearchText(aliasText)}`;
			if (unique.has(id)) {
				continue;
			}
			unique.add(id);
			aliases.push({
				id,
				songIdentifier: resolvedSongIdentifier,
				aliasText,
				source: "lxns",
			});
		}
	}

	return aliases.sort((left, right) => {
		const identifierDiff = left.songIdentifier.localeCompare(right.songIdentifier);
		if (identifierDiff !== 0) {
			return identifierDiff;
		}
		return left.aliasText.localeCompare(right.aliasText);
	});
}

export function parseCatalogVersionItems(value: unknown): CatalogVersionItem[] {
	const record = typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
	const source = Array.isArray(record?.versions) ? record.versions : Array.isArray(value) ? value : [];
	if (!Array.isArray(source)) {
		return [];
	}

	const rows: CatalogVersionItem[] = [];
	for (const item of source) {
		if (typeof item !== "object" || item === null) {
			continue;
		}
		const row = item as Record<string, unknown>;
		const versionRaw = typeof row.version === "string" ? row.version : "";
		const abbrRaw = typeof row.abbr === "string" ? row.abbr : "";
		const version = versionRaw.trim();
		if (!version) {
			continue;
		}

		const abbr = abbrRaw.trim() || version;
		rows.push({
			version,
			abbr,
			releaseDate: typeof row.releaseDate === "string" ? row.releaseDate : null,
		});
	}

	return rows;
}
