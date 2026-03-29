import type { SongFilterSettings } from "@/components/songs/types";
import type { CatalogVersionItem, SongFilterSnapshot, SongIdItem } from "@/lib/app-types";

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/u;
const PASSWORD_COMPLEXITY_PATTERN = /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^A-Za-z0-9\s]).{8,}$/u;

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
  const keywordMatch =
    (song.searchKeywords
      ? localizedStandardContains(song.searchKeywords, trimmedSearch) ||
        localizedStandardContains(song.searchKeywords.replace(/\s+/gu, ""), normalizedSearch)
      : false);
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
