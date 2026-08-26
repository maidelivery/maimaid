import type { Alias, Sheet, Song } from "@/components/songs/types";
import type { CatalogVersionItem, SongIdItem } from "@/lib/app-types";
import {
	STATIC_ASSETS_URL,
	parseBundleLxnsAliases,
	parseBundleSheets,
	parseBundleSongs,
	parseCatalogVersionItems,
	parseSongIdItems,
} from "@/lib/app-helpers";

export type StaticCatalog = {
	songs: Song[];
	sheets: Sheet[];
	aliases: Alias[];
	songIdItems: SongIdItem[];
	versionItems: CatalogVersionItem[];
};

export async function loadStaticCatalog(): Promise<StaticCatalog> {
	if (!STATIC_ASSETS_URL) {
		throw new Error("Missing NEXT_PUBLIC_STATIC_ASSETS_URL.");
	}

	const manifestResponse = await fetch(`${STATIC_ASSETS_URL}/manifest.json`, { cache: "no-store" });
	if (!manifestResponse.ok) {
		throw new Error(`Static manifest HTTP ${manifestResponse.status}`);
	}

	const manifest = (await manifestResponse.json()) as { bundle?: string };
	if (!manifest.bundle) {
		throw new Error("Static manifest has no bundle path.");
	}

	const bundleURL = new URL(manifest.bundle, `${STATIC_ASSETS_URL}/`);
	const bundleResponse = await fetch(bundleURL);
	if (!bundleResponse.ok) {
		throw new Error(`Static bundle HTTP ${bundleResponse.status}`);
	}

	const bundlePayload = (await bundleResponse.json()) as { payload?: { resources?: Record<string, unknown> } };
	const resources = bundlePayload.payload?.resources;
	const dataJsonResource = resources?.data_json;
	const songs = parseBundleSongs(dataJsonResource);
	const sheets = parseBundleSheets(dataJsonResource);
	if (songs.length === 0 || sheets.length === 0) {
		throw new Error("Static catalog is empty.");
	}

	const songIdItems = parseSongIdItems(resources?.songid_json);
	return {
		songs,
		sheets,
		aliases: parseBundleLxnsAliases(resources?.lxns_aliases, { songs, songIdItems }),
		songIdItems,
		versionItems: parseCatalogVersionItems(dataJsonResource),
	};
}
