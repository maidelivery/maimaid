import { createHash } from "node:crypto";
import { parse as parseYaml } from "yaml";
import { AppError } from "../lib/errors.js";
import { difficultyByLevelIndex, normalizeChartType } from "../utils/compat.js";
import { mergeChartStatsPayloads } from "./chart-fit.service.js";

/**
 * Pure half of the static bundle build: fetch upstream sources, normalize them,
 * and hash the result. Nothing here touches the database.
 *
 * These used to be private methods on `StaticBundleService`. They live here so
 * `scripts/build-static-bundle.ts` can run the same code inside GitHub Actions:
 * assembling a bundle parses and re-serializes ~15 MB of JSON several times over,
 * which is the heaviest thing the API process ever did. A second implementation
 * for CI would be free to drift, and a drifting md5 means every client
 * re-downloads a bundle whose contents did not change.
 */

export type FetchedStaticSource = {
	url: string;
	contentType: string | null;
	content: unknown;
};

export type StaticSourceTarget = {
	category: string;
	activeUrl: string;
	fallbackUrls: string[];
};

export type DanSection = {
	title?: string;
	description?: string;
	sheets: string[];
	sheetDescriptions?: string[];
};

export type DanCategory = {
	title: string;
	id: string;
	sections: DanSection[];
};

export const toRecord = (value: unknown): Record<string, unknown> | null =>
	typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;

/** `df_chart_fit` is the historical name for `chart_fit`. */
export const normalizeSourceCategory = (category: string) => (category === "df_chart_fit" ? "chart_fit" : category);

export const tryParseText = (raw: string, contentType: string | null) => {
	const normalizedType = (contentType ?? "").toLowerCase();
	const maybeJson = normalizedType.includes("json") || raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[");
	if (!maybeJson) {
		return raw;
	}
	try {
		return JSON.parse(raw) as unknown;
	} catch {
		return raw;
	}
};

export const normalizeResourcePayload = (category: string, parsed: unknown, raw: string) => {
	if (category !== "dan_info") {
		return parsed;
	}
	return parseDanInfoPayload(parsed, raw);
};

export const fetchStaticSourceByUrl = async (category: string, url: string): Promise<FetchedStaticSource> => {
	const response = await fetch(url, { method: "GET" });
	if (!response.ok) {
		throw new AppError(502, "static_source_fetch_failed", `Static source fetch failed: ${category}`, {
			category,
			url,
			error: `HTTP_${response.status}`,
		});
	}

	const contentType = response.headers.get("content-type");
	const raw = await response.text();
	const parsed = tryParseText(raw, contentType);
	return {
		url,
		contentType,
		content: normalizeResourcePayload(category, parsed, raw),
	};
};

export const fetchStaticSourceFromTargets = async (category: string, targets: string[]): Promise<FetchedStaticSource> => {
	let fetchError: string | null = null;
	for (const url of targets) {
		try {
			return await fetchStaticSourceByUrl(category, url);
		} catch (error) {
			fetchError = error instanceof Error ? error.message : "unknown_error";
		}
	}

	throw new AppError(502, "static_source_fetch_failed", `Static source fetch failed: ${category}`, {
		category,
		error: fetchError,
	});
};

export const normalizeBundleResources = (resources: Record<string, unknown>) => {
	const normalized = { ...resources };
	if (normalized.chart_fit === undefined && normalized.df_chart_fit !== undefined) {
		normalized.chart_fit = normalized.df_chart_fit;
	}
	delete (normalized as Record<string, unknown>).df_chart_fit;
	normalized.lxns_aliases = normalizeLxnsAliasesPayload(resources.lxns_aliases, resources.songid_json);
	return normalized;
};

export const normalizeLxnsAliasesPayload = (lxnsPayload: unknown, songIdPayload: unknown) => {
	const aliases = extractLxnsAliasRows(lxnsPayload);
	if (aliases.length === 0) {
		return lxnsPayload;
	}

	const knownSongIds = extractSongIdSet(songIdPayload);
	const merged = new Map<number, Set<string>>();

	for (const row of aliases) {
		const canonicalSongId = resolveCanonicalLxnsSongId(row.song_id, knownSongIds);
		const existing = merged.get(canonicalSongId) ?? new Set<string>();
		for (const alias of row.aliases) {
			const trimmed = alias.trim();
			if (!trimmed) {
				continue;
			}
			existing.add(trimmed);
		}
		merged.set(canonicalSongId, existing);
	}

	const normalizedAliases = Array.from(merged.entries())
		.sort((left, right) => left[0] - right[0])
		.map(([songId, aliasSet]) => ({
			song_id: songId,
			aliases: Array.from(aliasSet).sort(),
		}));

	return {
		aliases: normalizedAliases,
	};
};

type LxnsPlayableSong = {
	artist: string;
	charts: Set<string>;
};

export type LxnsCnRegionMergeStats = {
	lxnsSongCount: number;
	lxnsChartCount: number;
	catalogChartCount: number;
	matchedChartCount: number;
};

const normalizeCatalogIdentity = (value: unknown) =>
	typeof value === "string" ? value.normalize("NFKC").trim().toLocaleLowerCase().replace(/\s+/gu, " ") : "";

const lxnsChartKey = (chartType: string, difficulty: string) => `${chartType}|${difficulty}`;

/**
 * Treats the LXNS CN song list as authoritative for ordinary chart availability.
 * Other region flags and Utage sheets keep their catalog values.
 */
export const mergeLxnsCnRegions = (
	dataJson: unknown,
	lxnsSongList: unknown,
): { dataJson: Record<string, unknown>; stats: LxnsCnRegionMergeStats } => {
	const dataRoot = toRecord(dataJson);
	const dataSongs = Array.isArray(dataRoot?.songs) ? dataRoot.songs : null;
	if (!dataRoot || !dataSongs) {
		throw new AppError(502, "static_source_invalid_payload", "data_json is missing songs for CN region merge.");
	}

	const lxnsRoot = toRecord(lxnsSongList);
	const lxnsSongs = Array.isArray(lxnsRoot?.songs) ? lxnsRoot.songs : null;
	if (!lxnsSongs || lxnsSongs.length === 0) {
		throw new AppError(502, "static_source_invalid_payload", "LXNS song list is missing songs.");
	}

	const playableByTitle = new Map<string, LxnsPlayableSong[]>();
	let lxnsChartCount = 0;

	for (const rawSong of lxnsSongs) {
		const song = toRecord(rawSong);
		const title = normalizeCatalogIdentity(song?.title);
		if (!song || !title) {
			continue;
		}

		const difficulties = toRecord(song.difficulties);
		const charts = new Set<string>();
		for (const chartType of ["standard", "dx"] as const) {
			const rawCharts = difficulties?.[chartType];
			if (!Array.isArray(rawCharts)) {
				continue;
			}

			for (const rawChart of rawCharts) {
				const chart = toRecord(rawChart);
				const difficultyIndex = Number(chart?.difficulty);
				if (!Number.isInteger(difficultyIndex)) {
					continue;
				}
				const difficulty = difficultyByLevelIndex(difficultyIndex);
				if (!difficulty) {
					continue;
				}
				charts.add(lxnsChartKey(chartType, difficulty));
			}
		}

		if (charts.size === 0) {
			continue;
		}

		lxnsChartCount += charts.size;
		const candidates = playableByTitle.get(title) ?? [];
		candidates.push({
			artist: normalizeCatalogIdentity(song.artist),
			charts,
		});
		playableByTitle.set(title, candidates);
	}

	if (lxnsChartCount === 0) {
		throw new AppError(502, "static_source_invalid_payload", "LXNS song list contains no playable charts.");
	}

	let catalogChartCount = 0;
	let matchedChartCount = 0;
	const mergedSongs = dataSongs.map((rawSong) => {
		const song = toRecord(rawSong);
		if (!song || !Array.isArray(song.sheets)) {
			return rawSong;
		}

		const titleCandidates = playableByTitle.get(normalizeCatalogIdentity(song.title)) ?? [];
		const artist = normalizeCatalogIdentity(song.artist);
		const artistCandidates = titleCandidates.filter((candidate) => candidate.artist === artist);
		const candidates = titleCandidates.length === 1 ? titleCandidates : artistCandidates;

		const sheets = song.sheets.map((rawSheet) => {
			const sheet = toRecord(rawSheet);
			if (!sheet) {
				return rawSheet;
			}

			const chartType = normalizeChartType(typeof sheet.type === "string" ? sheet.type : undefined);
			if (chartType !== "standard" && chartType !== "dx") {
				return rawSheet;
			}

			catalogChartCount += 1;
			const difficulty = normalizeCatalogIdentity(sheet.difficulty);
			const key = lxnsChartKey(chartType, difficulty);
			const isPlayableInCn = candidates.some((candidate) => candidate.charts.has(key));
			if (isPlayableInCn) {
				matchedChartCount += 1;
			}

			return {
				...sheet,
				regions: {
					...(toRecord(sheet.regions) ?? {}),
					cn: isPlayableInCn,
				},
			};
		});

		return {
			...song,
			sheets,
		};
	});

	if (catalogChartCount > 0 && matchedChartCount === 0) {
		throw new AppError(502, "static_source_invalid_payload", "LXNS song list did not match any catalog charts.");
	}

	return {
		dataJson: {
			...dataRoot,
			songs: mergedSongs,
		},
		stats: {
			lxnsSongCount: lxnsSongs.length,
			lxnsChartCount,
			catalogChartCount,
			matchedChartCount,
		},
	};
};

export const extractLxnsAliasRows = (payload: unknown) => {
	const sourceArray = Array.isArray(payload) ? payload : ((toRecord(payload)?.aliases as unknown[] | undefined) ?? []);

	const rows: Array<{ song_id: number; aliases: string[] }> = [];
	for (const item of sourceArray) {
		const record = toRecord(item);
		if (!record) {
			continue;
		}
		const songId = Number(record.song_id);
		if (!Number.isFinite(songId)) {
			continue;
		}
		const aliasesRaw = Array.isArray(record.aliases)
			? record.aliases.filter((entry: unknown): entry is string => typeof entry === "string")
			: [];
		rows.push({
			song_id: Math.trunc(songId),
			aliases: aliasesRaw,
		});
	}
	return rows;
};

export const extractSongIdSet = (payload: unknown) => {
	const set = new Set<number>();
	if (!Array.isArray(payload)) {
		return set;
	}
	for (const item of payload) {
		const record = toRecord(item);
		if (!record) {
			continue;
		}
		const id = Number(record.id);
		if (!Number.isFinite(id)) {
			continue;
		}
		set.add(Math.trunc(id));
	}
	return set;
};

export const resolveCanonicalLxnsSongId = (songId: number, knownSongIds: Set<number>) => {
	if (knownSongIds.size === 0) {
		return songId;
	}

	if (songId > 0 && songId < 10000) {
		const dxCandidate = songId + 10000;
		if (!knownSongIds.has(songId) && knownSongIds.has(dxCandidate)) {
			return dxCandidate;
		}
	}

	const candidates = buildLxnsSongIdCandidates(songId);
	for (const candidate of candidates) {
		if (knownSongIds.has(candidate)) {
			return candidate;
		}
	}
	return songId;
};

export const buildLxnsSongIdCandidates = (songId: number) => {
	const candidates: number[] = [];
	const push = (value: number) => {
		if (!Number.isFinite(value) || value <= 0) {
			return;
		}
		if (!candidates.includes(value)) {
			candidates.push(value);
		}
	};

	push(songId);

	if (songId > 0 && songId < 10000) {
		push(songId + 10000);
	}

	if (songId > 10000 && songId < 100000) {
		const baseId = songId % 10000;
		if (baseId > 0) {
			push(baseId);
			push(baseId + 10000);
		}
	}

	if (songId >= 100000) {
		const baseId = songId % 100000;
		if (baseId > 0) {
			push(baseId);
			if (baseId < 10000) {
				push(baseId + 10000);
			}
		}
	}

	return candidates;
};

export const parseDanInfoPayload = (parsed: unknown, raw: string) => {
	let candidate = parsed;
	if (typeof candidate === "string") {
		try {
			candidate = parseYaml(raw);
		} catch (error) {
			throw new AppError(502, "static_source_invalid_payload", "Dan info YAML parse failed.", {
				error: error instanceof Error ? error.message : "unknown_error",
			});
		}
	}

	return sanitizeDanCategories(candidate);
};

export const sanitizeDanCategories = (value: unknown): DanCategory[] => {
	if (!Array.isArray(value)) {
		return [];
	}

	const rows: DanCategory[] = [];

	for (let index = 0; index < value.length; index += 1) {
		const item = value[index];
		if (typeof item !== "object" || item === null) {
			continue;
		}

		const record = item as Record<string, unknown>;
		const titleRaw = typeof record.title === "string" ? record.title : "";
		const title = titleRaw.trim();
		if (!title) {
			continue;
		}

		const lowerTitle = title.toLocaleLowerCase();
		if (lowerTitle.includes("test") || lowerTitle.includes("author's choice")) {
			continue;
		}

		const sectionItems = Array.isArray(record.sections) ? record.sections : [];
		const cleanedSections = sectionItems
			.map((section) => sanitizeDanSection(section))
			.filter((section): section is NonNullable<typeof section> => section !== null);
		if (cleanedSections.length === 0) {
			continue;
		}

		const idRaw = typeof record.id === "string" ? record.id.trim() : "";
		rows.push({
			title,
			id: idRaw || fallbackDanCategoryId(title, index),
			sections: cleanedSections,
		});
	}

	return rows;
};

export const sanitizeDanSection = (section: unknown): DanSection | null => {
	if (typeof section !== "object" || section === null) {
		return null;
	}

	const record = section as Record<string, unknown>;
	const rawSheets = Array.isArray(record.sheets)
		? record.sheets.filter((item): item is string => typeof item === "string")
		: [];
	if (rawSheets.length === 0) {
		return null;
	}

	const validSheetIndexes = new Set<number>();
	const cleanedSheets: string[] = [];
	for (let index = 0; index < rawSheets.length; index += 1) {
		const rawSheet = rawSheets[index]!;
		if (!isValidDanRawSheetRef(rawSheet)) {
			continue;
		}
		validSheetIndexes.add(index);
		cleanedSheets.push(rawSheet.trim());
	}
	if (cleanedSheets.length === 0) {
		return null;
	}

	let cleanedSheetDescriptions: string[] | undefined;
	if (Array.isArray(record.sheetDescriptions)) {
		const descriptions = record.sheetDescriptions.filter((item): item is string => typeof item === "string");
		const paired: string[] = [];
		const pairCount = Math.min(rawSheets.length, descriptions.length);
		for (let index = 0; index < pairCount; index += 1) {
			if (!validSheetIndexes.has(index)) {
				continue;
			}
			const description = descriptions[index]!;
			paired.push(description);
		}
		cleanedSheetDescriptions = paired.length > 0 ? paired : undefined;
	}

	const title = typeof record.title === "string" ? record.title.trim() : "";
	const description = typeof record.description === "string" ? record.description.trim() : "";

	const cleanedSection: DanSection = {
		sheets: cleanedSheets,
	};
	if (title) {
		cleanedSection.title = title;
	}
	if (description) {
		cleanedSection.description = description;
	}
	if (cleanedSheetDescriptions && cleanedSheetDescriptions.length > 0) {
		cleanedSection.sheetDescriptions = cleanedSheetDescriptions;
	}
	return cleanedSection;
};

export const isValidDanRawSheetRef = (raw: string) => {
	const trimmed = raw.trim();
	if (!trimmed) {
		return false;
	}

	const parts = trimmed.split("|");
	if (parts.length < 3) {
		return false;
	}

	const title = (parts[0] ?? "").trim();
	const type = (parts[1] ?? "").trim().toLocaleLowerCase();
	const difficulty = (parts[2] ?? "").trim().toLocaleLowerCase();
	if (!title || !type || !difficulty) {
		return false;
	}

	if (type.includes("utage") || difficulty.includes("utage")) {
		return false;
	}

	const validTypes = new Set(["dx", "std"]);
	if (!validTypes.has(type)) {
		return false;
	}

	const validDifficulties = new Set(["basic", "advanced", "expert", "master", "remaster"]);
	return validDifficulties.has(difficulty);
};

export const fallbackDanCategoryId = (title: string, index: number) => {
	const normalized = title
		.normalize("NFKC")
		.toLocaleLowerCase()
		.replace(/\s+/gu, "-")
		.replace(/[^\p{L}\p{N}_-]/gu, "");
	return normalized || `category-${index + 1}`;
};

export const normalizeForStableHash = (value: unknown): unknown => {
	if (value === null || value === undefined) {
		return null;
	}

	if (value instanceof Date) {
		return value.toISOString();
	}

	if (Array.isArray(value)) {
		return value.map((item) => normalizeForStableHash(item));
	}

	if (typeof value === "object") {
		const record = value as Record<string, unknown>;
		const normalized: Record<string, unknown> = {};
		for (const key of Object.keys(record).sort()) {
			normalized[key] = normalizeForStableHash(record[key]);
		}
		return normalized;
	}

	return value;
};

export const stableStringify = (value: unknown) => JSON.stringify(normalizeForStableHash(value));

/**
 * Hashes only `payload.resources`, so bundle-level bookkeeping (version strings,
 * timestamps) cannot change the md5 and force every client to re-download.
 */
export const computeBundleMd5 = (payload: Record<string, unknown>) => {
	const hashMaterial = payload.resources ?? {};
	return createHash("md5").update(stableStringify(hashMaterial)).digest("hex");
};

export const mapWithConcurrency = async <TInput, TResult>(
	inputs: TInput[],
	concurrency: number,
	worker: (input: TInput) => Promise<TResult>,
) => {
	if (inputs.length === 0) {
		return [];
	}

	const normalizedConcurrency = Math.max(1, Math.min(concurrency, inputs.length));
	const results = new Array<TResult>(inputs.length);
	let cursor = 0;

	const runWorker = async () => {
		while (true) {
			const current = cursor;
			cursor += 1;
			if (current >= inputs.length) {
				return;
			}
			results[current] = await worker(inputs[current]!);
		}
	};

	await Promise.all(Array.from({ length: normalizedConcurrency }, () => runWorker()));
	return results;
};

export type ChartFitMergeInput = {
	primaryPayload: unknown;
	primaryResolvedUrl: string | null;
	primaryContentType: string | null;
	primaryFetchError: string | null;
	extraUrl: string | null;
	extraResolvedUrl: string | null;
	extraFetchError: string | null;
	secondaryPayload: unknown;
};

/**
 * Fetch the `chart_fit` source and its one optional extra URL. Split out of the
 * build so CI and the API agree on which failures are tolerated: a primary fetch
 * failure is recorded in `sourceMeta` and the build continues on the secondary
 * payload, because diving-fish is the least reliable upstream and a bundle
 * without fresh chart stats is far better than no bundle.
 */
export const fetchChartFitSources = async (
	source: StaticSourceTarget,
	selfChartFitPayload: unknown,
): Promise<ChartFitMergeInput> => {
	if (source.fallbackUrls.length > 1) {
		throw new AppError(400, "static_source_invalid_fallback_urls", "chart_fit supports at most one extra URL.");
	}

	let primaryPayload: unknown = {};
	let primaryResolvedUrl: string | null = null;
	let primaryContentType: string | null = null;
	let primaryFetchError: string | null = null;

	try {
		const primary = await fetchStaticSourceByUrl("chart_fit", source.activeUrl);
		primaryPayload = primary.content;
		primaryResolvedUrl = primary.url;
		primaryContentType = primary.contentType;
	} catch (error) {
		primaryFetchError = error instanceof Error ? error.message : "unknown_error";
	}

	const extraUrl = source.fallbackUrls[0] ?? null;
	let secondaryPayload: unknown = selfChartFitPayload;
	let secondaryResolvedUrl: string | null = null;
	let secondaryFetchError: string | null = null;

	if (extraUrl) {
		try {
			const secondary = await fetchStaticSourceByUrl("chart_fit", extraUrl);
			secondaryPayload = secondary.content;
			secondaryResolvedUrl = secondary.url;
		} catch (error) {
			secondaryFetchError = error instanceof Error ? error.message : "unknown_error";
		}
	}

	return {
		primaryPayload,
		primaryResolvedUrl,
		primaryContentType,
		primaryFetchError,
		extraUrl,
		extraResolvedUrl: secondaryResolvedUrl,
		extraFetchError: secondaryFetchError,
		secondaryPayload,
	};
};

export type SelfChartFit = {
	payload: unknown;
	meta: Record<string, unknown>;
};

/**
 * Resolves the service's own chart stats. This is the one part of a bundle build
 * that needs the database (it aggregates every `best_scores` row), so the two
 * callers supply it differently: the API queries Prisma directly, and CI posts
 * the song-id mapping it derived from `data_json`/`songid_json` to the API and
 * gets the aggregate back. The mapping is a few hundred KB, versus several MB for
 * the source files it came from.
 */
export type SelfChartFitResolver = (input: { dataJson: unknown; songidJson: unknown }) => Promise<SelfChartFit>;

export type ComposedBundle = {
	payload: Record<string, unknown>;
	sourceMeta: Record<string, unknown>;
	md5: string;
};

/**
 * The whole compute half of a bundle build: fetch every enabled source, merge
 * chart stats, normalize, and hash. No database access except through
 * `resolveSelfChartFit`.
 */
export const composeBundlePayload = async (
	sources: StaticSourceTarget[],
	resolveSelfChartFit: SelfChartFitResolver,
): Promise<ComposedBundle> => {
	if (sources.length === 0) {
		throw new AppError(400, "static_source_empty", "No enabled static source.");
	}

	const normalizedSources = sources.map((source) => ({
		...source,
		category: normalizeSourceCategory(source.category),
	}));
	const chartFitSource = normalizedSources.find((source) => source.category === "chart_fit");
	const commonSources = normalizedSources.filter((source) => source.category !== "chart_fit");

	const sourceMeta: Record<string, unknown> = {};
	const resources: Record<string, unknown> = {};

	const commonFetched = await mapWithConcurrency(commonSources, 4, async (source) => ({
		source,
		fetched: await fetchStaticSourceFromTargets(source.category, [source.activeUrl, ...source.fallbackUrls]),
	}));
	for (const item of commonFetched) {
		resources[item.source.category] = item.fetched.content;
		sourceMeta[item.source.category] = {
			url: item.fetched.url,
			contentType: item.fetched.contentType,
		};
	}

	if (resources.lxns_song_list === undefined) {
		throw new AppError(502, "static_source_missing", "LXNS song list source is required for CN region validation.");
	}
	const cnRegionMerge = mergeLxnsCnRegions(resources.data_json, resources.lxns_song_list);
	resources.data_json = cnRegionMerge.dataJson;
	delete resources.lxns_song_list;
	const lxnsSongListMeta = toRecord(sourceMeta.lxns_song_list) ?? {};
	sourceMeta.lxns_song_list = {
		...lxnsSongListMeta,
		...cnRegionMerge.stats,
	};

	// Depends on data_json/songid_json above, so it cannot run in the same batch.
	const selfChartFit = await resolveSelfChartFit({
		dataJson: resources.data_json,
		songidJson: resources.songid_json,
	});

	if (chartFitSource) {
		const merged = await fetchChartFitSources(chartFitSource, selfChartFit.payload);
		resources.chart_fit = mergeChartStatsPayloads(merged.primaryPayload, merged.secondaryPayload, {
			secondaryMinCnt: 1000,
		});
		sourceMeta.chart_fit = {
			url: merged.primaryResolvedUrl,
			contentType: merged.primaryContentType,
			primaryFetchError: merged.primaryFetchError,
			extraUrl: merged.extraUrl,
			extraResolvedUrl: merged.extraResolvedUrl,
			extraFetchError: merged.extraFetchError,
			selfGeneratedAt: selfChartFit.meta.generatedAt ?? null,
		};
	}

	const payload: Record<string, unknown> = {
		resources: normalizeBundleResources(resources),
	};

	return {
		payload,
		sourceMeta,
		md5: computeBundleMd5(payload),
	};
};
