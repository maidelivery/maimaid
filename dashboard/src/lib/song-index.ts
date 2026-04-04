import type { Alias, Sheet as SongSheet, Song } from "@/components/songs/types";
import type { SongFilterSnapshot, SongIdItem } from "@/lib/app-types";
import { compactSearchText, expandProviderSongIdCandidates, normalizeSearchText } from "@/lib/app-helpers";

export function normalizeSheetType(value?: string | null): string {
	const normalized = value?.trim().toLowerCase() ?? "";
	if (normalized === "standard" || normalized === "std" || normalized === "sd") return "std";
	if (normalized === "dx") return "dx";
	if (normalized === "utage") return "utage";
	return normalized;
}

export function normalizeDifficulty(value?: string | null): string {
	const normalized = value?.trim().toLowerCase() ?? "";
	if (normalized === "re_master") return "remaster";
	return normalized;
}

export type SongCatalogIndex = {
	songTitleByIdentifier: Map<string, string>;
	songCatalogIndexByIdentifier: Map<string, number>;
	songCatalogByIdentifier: Map<string, Song>;
	aliasesBySongIdentifier: Map<string, string[]>;
	sheetsBySongIdentifier: Map<string, SongSheet[]>;
	providerSongIdsByName: Map<string, number[]>;
	songSnapshots: SongFilterSnapshot[];
	songSnapshotByIdentifier: Map<string, SongFilterSnapshot>;
	allCategories: string[];
};

export function buildSongCatalogIndex(input: {
	songs: Song[];
	sheets: SongSheet[];
	aliases: Alias[];
	songIdItems: SongIdItem[];
}): SongCatalogIndex {
	const { songs, sheets, aliases, songIdItems } = input;
	const songTitleByIdentifier = new Map(songs.map((item) => [item.songIdentifier, item.title]));
	const songCatalogIndexByIdentifier = new Map(songs.map((item, index) => [item.songIdentifier, index]));
	const songCatalogByIdentifier = new Map(songs.map((item) => [item.songIdentifier, item]));
	const sheetsBySongIdentifier = new Map<string, SongSheet[]>();
	const providerSongIdsByName = new Map<string, number[]>();
	const allCategories = Array.from(new Set(songs.map((song) => song.category).filter((item): item is string => Boolean(item))));

	for (const sheet of sheets) {
		const list = sheetsBySongIdentifier.get(sheet.songIdentifier) ?? [];
		list.push(sheet);
		sheetsBySongIdentifier.set(sheet.songIdentifier, list);
	}

	for (const item of songIdItems) {
		const keys = [normalizeSearchText(item.name), compactSearchText(item.name)];
		for (const key of keys) {
			if (!key) {
				continue;
			}
			const list = providerSongIdsByName.get(key) ?? [];
			if (!list.includes(item.id)) {
				list.push(item.id);
			}
			providerSongIdsByName.set(key, list);
		}
	}

	const aliasesBySongIdentifier = buildAliasIndex({
		songs,
		aliases,
		songIdItems,
	});

	const songSnapshots = songs.map((song) => {
		const aliasList = aliasesBySongIdentifier.get(song.songIdentifier) ?? [];
		const songSheets = (sheetsBySongIdentifier.get(song.songIdentifier) ?? []).map((sheet) => ({
			type: normalizeSheetType(sheet.chartType),
			difficulty: normalizeDifficulty(sheet.difficulty),
			noteDesigner: sheet.noteDesigner,
			internalLevelValue: sheet.internalLevelValue,
			levelValue: sheet.levelValue,
			regionJp: sheet.regionJp,
			regionIntl: sheet.regionIntl,
			regionCn: sheet.regionCn,
		}));

		const maxDifficulty = songSheets.reduce((currentMax, sheet) => {
			const level = sheet.internalLevelValue ?? sheet.levelValue ?? 0;
			return Math.max(currentMax, level);
		}, 0);

		const songIds = new Set<number>();
		if (song.songId > 0) {
			songIds.add(song.songId);
		}
		const fallbackIds =
			providerSongIdsByName.get(normalizeSearchText(song.title)) ??
			providerSongIdsByName.get(compactSearchText(song.title)) ??
			providerSongIdsByName.get(normalizeSearchText(song.songIdentifier)) ??
			providerSongIdsByName.get(compactSearchText(song.songIdentifier)) ??
			[];

		if (fallbackIds.length > 0) {
			const hasUtage = songSheets.some((sheet) => sheet.type === "utage");
			const hasDx = songSheets.some((sheet) => sheet.type === "dx");
			const hasStd = songSheets.some((sheet) => sheet.type === "std");

			let selectedId: number | undefined;
			if (hasUtage) {
				selectedId = fallbackIds.find((id) => id >= 100000);
			}
			if (selectedId === undefined && hasDx) {
				selectedId = fallbackIds.find((id) => id >= 10000 && id < 100000);
			}
			if (selectedId === undefined && hasStd) {
				selectedId = fallbackIds.find((id) => id < 10000);
			}
			if (selectedId !== undefined) {
				songIds.add(selectedId);
			}
		}

		return {
			song,
			aliases: aliasList,
			songIds: Array.from(songIds),
			sheets: songSheets,
			maxDifficulty,
		} satisfies SongFilterSnapshot;
	});

	return {
		songTitleByIdentifier,
		songCatalogIndexByIdentifier,
		songCatalogByIdentifier,
		aliasesBySongIdentifier,
		sheetsBySongIdentifier,
		providerSongIdsByName,
		songSnapshots,
		songSnapshotByIdentifier: new Map(songSnapshots.map((snapshot) => [snapshot.song.songIdentifier, snapshot])),
		allCategories,
	};
}

function buildAliasIndex(input: { songs: Song[]; aliases: Alias[]; songIdItems: SongIdItem[] }): Map<string, string[]> {
	const { songs, aliases, songIdItems } = input;
	const map = new Map<string, Set<string>>();
	const songIdentifierSet = new Set(songs.map((song) => song.songIdentifier));
	const songIdentifierByNormalizedName = new Map<string, string>();

	for (const song of songs) {
		songIdentifierByNormalizedName.set(normalizeSearchText(song.title), song.songIdentifier);
		songIdentifierByNormalizedName.set(compactSearchText(song.title), song.songIdentifier);
		songIdentifierByNormalizedName.set(normalizeSearchText(song.songIdentifier), song.songIdentifier);
		songIdentifierByNormalizedName.set(compactSearchText(song.songIdentifier), song.songIdentifier);
	}

	const songIdentifierByProviderId = new Map<number, string>();
	for (const item of songIdItems) {
		const mappedIdentifier =
			songIdentifierByNormalizedName.get(normalizeSearchText(item.name)) ??
			songIdentifierByNormalizedName.get(compactSearchText(item.name));
		if (mappedIdentifier) {
			songIdentifierByProviderId.set(item.id, mappedIdentifier);
		}
	}

	const addAlias = (songIdentifier: string, aliasText: string) => {
		const trimmed = aliasText.trim();
		if (!trimmed) {
			return;
		}
		const list = map.get(songIdentifier) ?? new Set<string>();
		list.add(trimmed);
		map.set(songIdentifier, list);
	};

	for (const alias of aliases) {
		const candidateSongIdentifiers = new Set<string>();

		if (songIdentifierSet.has(alias.songIdentifier)) {
			candidateSongIdentifiers.add(alias.songIdentifier);
		}

		const normalizedAliasSongIdentifier = normalizeSearchText(alias.songIdentifier);
		const compactAliasSongIdentifier = compactSearchText(alias.songIdentifier);
		const mappedByName =
			songIdentifierByNormalizedName.get(normalizedAliasSongIdentifier) ??
			songIdentifierByNormalizedName.get(compactAliasSongIdentifier);
		if (mappedByName) {
			candidateSongIdentifiers.add(mappedByName);
		}

		const numericAliasSongIdentifier = Number(alias.songIdentifier);
		if (Number.isFinite(numericAliasSongIdentifier)) {
			const numericId = Math.trunc(numericAliasSongIdentifier);
			for (const candidateId of expandProviderSongIdCandidates(numericId)) {
				const mapped = songIdentifierByProviderId.get(candidateId);
				if (mapped) {
					candidateSongIdentifiers.add(mapped);
				}
				const candidateIdentifier = String(candidateId);
				if (songIdentifierSet.has(candidateIdentifier)) {
					candidateSongIdentifiers.add(candidateIdentifier);
				}
			}
		}

		if (candidateSongIdentifiers.size === 0) {
			candidateSongIdentifiers.add(alias.songIdentifier);
		}

		for (const songIdentifier of candidateSongIdentifiers) {
			addAlias(songIdentifier, alias.aliasText);
		}
	}

	return new Map(
		Array.from(map.entries()).map(([songIdentifier, aliasSet]) => [songIdentifier, Array.from(aliasSet.values())]),
	);
}
