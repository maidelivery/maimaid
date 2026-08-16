export type StaticAssetKind = "cover" | "presetAvatar";

export type StaticAssetCandidate = {
	kind: StaticAssetKind;
	name: string;
	sourceUrl: string;
};

const COVER_SOURCE_BASE_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/";
const PRESET_AVATAR_SOURCE_BASE_URL = "https://assets2.lxns.net/maimai/icon/";

const toRecord = (value: unknown): Record<string, unknown> | null =>
	typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;

export const collectStaticAssetCandidates = (payload: Record<string, unknown>): StaticAssetCandidate[] => {
	const resources = toRecord(payload.resources);
	const catalog = toRecord(resources?.data_json);
	const songs = Array.isArray(catalog?.songs) ? catalog.songs : [];
	const iconList = toRecord(resources?.lxns_icon_list);
	const icons = Array.isArray(iconList?.icons) ? iconList.icons : [];
	if (songs.length === 0) {
		throw new Error("Composed bundle has no songs to mirror covers for.");
	}
	if (icons.length === 0) {
		throw new Error("Composed bundle has no LXNS icons to mirror.");
	}

	const assets = new Map<string, StaticAssetCandidate>();
	for (const rawSong of songs) {
		const imageName = toRecord(rawSong)?.imageName;
		if (typeof imageName !== "string" || !imageName.trim()) {
			continue;
		}
		const name = imageName.trim();
		const candidate = {
			kind: "cover" as const,
			name,
			sourceUrl: `${COVER_SOURCE_BASE_URL}${encodeURIComponent(name)}`,
		};
		assets.set(`${candidate.kind}|${candidate.name}`, candidate);
	}

	for (const rawIcon of icons) {
		const id = Number(toRecord(rawIcon)?.id);
		if (!Number.isSafeInteger(id) || id < 0) {
			continue;
		}
		const name = `${id}.png`;
		const candidate = {
			kind: "presetAvatar" as const,
			name,
			sourceUrl: `${PRESET_AVATAR_SOURCE_BASE_URL}${name}`,
		};
		assets.set(`${candidate.kind}|${candidate.name}`, candidate);
	}

	return [...assets.values()];
};
