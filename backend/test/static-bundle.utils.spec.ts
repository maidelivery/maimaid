import "reflect-metadata";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
	composeBundlePayload,
	createStaticBundleArtifact,
	mergeLxnsCnRegions,
	mergeSongIdPayload,
	normalizeDxDataCatalog,
	parseStaticBundleArtifact,
} from "../src/services/static-bundle.utils.js";

const sheet = (type: string, difficulty: string, cn: boolean) => ({
	type,
	difficulty,
	regions: { jp: true, intl: true, cn },
});

const lxnsChart = (type: string, difficulty: number, level?: string, levelValue?: number) => ({
	type,
	difficulty,
	level,
	level_value: levelValue,
});

describe("static bundle artifacts", () => {
	const md5 = "0123456789abcdef0123456789abcdef";
	const createdAt = new Date("2026-08-16T04:00:00.000Z");

	it("round-trips the immutable R2 artifact metadata and payload", () => {
		const artifact = createStaticBundleArtifact(
			"bundle-1786852800000",
			md5,
			createdAt,
			{ resources: { data_json: { songs: [] } } },
			{ data_json: { url: "https://example.com/data.json" } },
		);

		expect(parseStaticBundleArtifact(JSON.stringify(artifact), artifact)).toEqual(artifact);
	});

	it("rejects an artifact whose metadata differs from the publish request", () => {
		const artifact = createStaticBundleArtifact(
			"bundle-1786852800000",
			md5,
			createdAt,
			{ resources: { data_json: { songs: [] } } },
			{},
		);

		expect(() =>
			parseStaticBundleArtifact(JSON.stringify(artifact), {
				version: artifact.version,
				md5: "fedcba9876543210fedcba9876543210",
			}),
		).toThrow("metadata does not match");
	});
});

describe("mergeLxnsCnRegions", () => {
	afterEach(() => {
		vi.restoreAllMocks();
	});

	it("uses LXNS as the authoritative CN availability source", () => {
		const result = mergeLxnsCnRegions(
			{
				songs: [
					{
						title: "Current Song",
						artist: "Artist",
						sheets: [sheet("dx", "basic", false), sheet("dx", "master", true), sheet("std", "expert", true)],
					},
				],
			},
			{
				songs: [
					{
						title: "Current Song",
						artist: "Artist",
						difficulties: {
							standard: [],
							dx: [lxnsChart("dx", 0), lxnsChart("dx", 3)],
						},
					},
				],
			},
		);

		const songs = result.dataJson.songs as Array<{ sheets: Array<{ regions: Record<string, boolean> }> }>;
		expect(songs[0]?.sheets.map((item) => item.regions.cn)).toEqual([true, true, false]);
		expect(songs[0]?.sheets[0]?.regions.jp).toBe(true);
		expect(result.stats).toEqual({
			lxnsSongCount: 1,
			lxnsChartCount: 2,
			catalogChartCount: 3,
			matchedChartCount: 2,
		});
	});

	it("ignores LXNS chart constants", () => {
		const result = mergeLxnsCnRegions(
			{
				songs: [
					{
						title: "Server Constant Song",
						artist: "Artist",
						sheets: [
							{
								...sheet("dx", "master", false),
								level: "13+",
								levelValue: 13.6,
								internalLevel: "13.9",
								internalLevelValue: 13.9,
								regionOverrides: { intl: { version: "CiRCLE PLUS" } },
							},
						],
					},
				],
			},
			{
				songs: [
					{
						title: "Server Constant Song",
						artist: "Artist",
						difficulties: { standard: [], dx: [lxnsChart("dx", 3, "13+", 13.8)] },
					},
				],
			},
		);

		const songs = result.dataJson.songs as Array<{
			sheets: Array<{ regions: Record<string, boolean>; regionOverrides: Record<string, Record<string, unknown>> }>;
		}>;
		expect(songs[0]?.sheets[0]?.regions.cn).toBe(true);
		expect(songs[0]?.sheets[0]?.regionOverrides).toEqual({ intl: { version: "CiRCLE PLUS" } });
	});

	it("uses artist to disambiguate duplicate titles", () => {
		const result = mergeLxnsCnRegions(
			{
				songs: [
					{ title: "Link", artist: "First Artist", sheets: [sheet("std", "remaster", false)] },
					{ title: "Link", artist: "Second Artist", sheets: [sheet("std", "remaster", true)] },
				],
			},
			{
				songs: [
					{
						title: "Link",
						artist: "First Artist",
						difficulties: { standard: [lxnsChart("standard", 4)], dx: [] },
					},
					{
						title: "Link",
						artist: "Second Artist",
						difficulties: { standard: [lxnsChart("standard", 3)], dx: [] },
					},
				],
			},
		);

		const songs = result.dataJson.songs as Array<{ sheets: Array<{ regions: Record<string, boolean> }> }>;
		expect(songs[0]?.sheets[0]?.regions.cn).toBe(true);
		expect(songs[1]?.sheets[0]?.regions.cn).toBe(false);
	});

	it("accepts a unique title when artist text differs between sources", () => {
		const result = mergeLxnsCnRegions(
			{
				songs: [{ title: "Hurtling Boys", artist: "Long Artist Credit", sheets: [sheet("dx", "master", false)] }],
			},
			{
				songs: [
					{
						title: "Hurtling Boys",
						artist: "Short Artist Credit",
						difficulties: { standard: [], dx: [lxnsChart("dx", 3)] },
					},
				],
			},
		);

		const songs = result.dataJson.songs as Array<{ sheets: Array<{ regions: Record<string, boolean> }> }>;
		expect(songs[0]?.sheets[0]?.regions.cn).toBe(true);
	});

	it("matches the LXNS song whose title is a full-width space", () => {
		const result = mergeLxnsCnRegions(
			{
				songs: [
					{
						title: "\u3000",
						artist: "x0o0x_",
						sheets: [sheet("dx", "basic", false), sheet("dx", "master", false)],
					},
				],
			},
			{
				songs: [
					{
						id: 1422,
						title: "\u3000",
						artist: "x0o0x_",
						difficulties: { standard: [], dx: [lxnsChart("dx", 0), lxnsChart("dx", 3)] },
					},
				],
			},
		);

		const songs = result.dataJson.songs as Array<{ sheets: Array<{ regions: Record<string, boolean> }> }>;
		expect(songs[0]?.sheets.map((item) => item.regions.cn)).toEqual([true, true]);
		expect(result.stats.matchedChartCount).toBe(2);
	});

	it("matches dxdata utage variants to LXNS kanji markers", () => {
		const result = mergeLxnsCnRegions(
			{
				songs: [
					{
						title: "[協]Ultimate taste",
						artist: "Ultimate taste",
						sheets: [sheet("utage2p", "【協】", false)],
					},
				],
			},
			{
				songs: [
					{
						title: "[協]Ultimate taste",
						artist: "Ultimate taste",
						difficulties: {
							standard: [],
							dx: [],
							utage: [{ kanji: "協" }],
						},
					},
				],
			},
		);

		const songs = result.dataJson.songs as Array<{ sheets: Array<{ regions: Record<string, boolean> }> }>;
		expect(songs[0]?.sheets[0]?.regions.cn).toBe(true);
		expect(result.stats).toEqual({
			lxnsSongCount: 1,
			lxnsChartCount: 1,
			catalogChartCount: 1,
			matchedChartCount: 1,
		});
	});

	it("matches renamed utage songs by their stable internal id", () => {
		const result = mergeLxnsCnRegions(
			{
				songs: [
					{
						title: "[X]人マニア",
						artist: "原口沙輔 feat.重音テト",
						sheets: [{ ...sheet("utage", "【X】", false), internalId: 111772 }],
					},
				],
			},
			{
				songs: [
					{
						id: 111772,
						title: "[宴]人マニア",
						artist: "原口沙輔 feat.重音テト",
						difficulties: {
							standard: [],
							dx: [],
							utage: [{ kanji: "X" }],
						},
					},
				],
			},
		);

		const songs = result.dataJson.songs as Array<{ sheets: Array<{ regions: Record<string, boolean> }> }>;
		expect(songs[0]?.sheets[0]?.regions.cn).toBe(true);
		expect(result.stats.matchedChartCount).toBe(1);
	});

	it("rejects an empty LXNS response before clearing CN availability", () => {
		expect(() => mergeLxnsCnRegions({ songs: [] }, { songs: [] })).toThrow("LXNS song list is missing songs");
	});

	it("applies LXNS during composition without shipping the raw song list", async () => {
		const dataJson = {
			updateTime: "2026-08-15T15:00:12Z",
			versions: [
				{ version: "PRiSM PLUS", releaseDate: "2025-03-13" },
				{ version: "CiRCLE", releaseDate: "2025-09-18" },
				{ version: "CiRCLE PLUS", releaseDate: "2026-03-19" },
			],
			songs: [
				{
					title: "Current Song",
					artist: "Artist",
					sheets: [
						{
							...sheet("dx", "master", false),
							level: "13+",
							internalLevelValue: 13.9,
							internalId: 1844,
							multiverInternalLevelValue: {
								"PRiSM PLUS": 13.8,
								CiRCLE: 13.8,
								"CiRCLE PLUS": 13.9,
							},
						},
						{
							type: "dx",
							difficulty: "expert",
							level: "12",
							internalLevelValue: 12.5,
							regions: { jp: true, intl: true, cn: true },
							multiverInternalLevelValue: { Splash: 12.1 },
						},
					],
				},
			],
		};
		const lxnsSongList = {
			songs: [
				{
					title: "Current Song",
					artist: "Artist",
					difficulties: { standard: [], dx: [lxnsChart("dx", 3, "13+", 13.9)] },
				},
			],
		};

		vi.spyOn(globalThis, "fetch").mockImplementation(async (input) => {
			const url = String(input);
			const payload = url.endsWith("/data.json") ? dataJson : lxnsSongList;
			return new Response(JSON.stringify(payload), {
				status: 200,
				headers: { "content-type": "application/json" },
			});
		});

		const result = await composeBundlePayload(
			[
				{ category: "data_json", activeUrl: "https://example.com/data.json", fallbackUrls: [] },
				{ category: "lxns_song_list", activeUrl: "https://example.com/song/list", fallbackUrls: [] },
			],
			async () => ({ payload: {}, meta: {} }),
		);

		const resources = result.payload.resources as Record<string, unknown>;
		const mergedData = resources.data_json as {
			songs: Array<{
				sheets: Array<{
					regions: { cn: boolean };
					regionOverrides?: { cn: { internalLevelValue: number } };
				}>;
			}>;
		};
		expect(mergedData.songs[0]?.sheets[0]?.regions.cn).toBe(true);
		expect(mergedData.songs[0]?.sheets[0]?.regionOverrides?.cn.internalLevelValue).toBe(13.8);
		expect(mergedData.songs[0]?.sheets[1]?.regions.cn).toBe(false);
		expect(mergedData.songs[0]?.sheets[1]?.regionOverrides).toBeUndefined();
		expect(resources.songid_json).toEqual([{ id: 11844, name: "Current Song" }]);
		expect(resources.lxns_song_list).toBeUndefined();
		expect(result.sourceMeta.lxns_song_list).toMatchObject({
			lxnsSongCount: 1,
			lxnsChartCount: 1,
			catalogChartCount: 2,
			matchedChartCount: 1,
		});
	});
});

describe("normalizeDxDataCatalog", () => {
	it("uses chart release dates for version membership", () => {
		const normalized = normalizeDxDataCatalog({
			versions: [
				{ version: "ORANGE", releaseDate: "2014-09-18" },
				{ version: "Splash", releaseDate: "2020-09-17" },
			],
			songs: [
				{
					songId: "Dragoon",
					title: "Dragoon",
					sheets: [
						{ type: "std", difficulty: "master", version: "ORANGE", releaseDate: "2015-02-05" },
						{ type: "std", difficulty: "remaster", version: "ORANGE", releaseDate: "2020-11-04" },
					],
				},
			],
		});

		const song = normalized.songs[0] as { version: string; sheets: Array<{ version: string }> };
		expect(song.version).toBe("ORANGE");
		expect(song.sheets.map((sheet) => sheet.version)).toEqual(["ORANGE", "Splash"]);
	});

	it("keeps the declared version when an upstream date is one day early", () => {
		const normalized = normalizeDxDataCatalog({
			versions: [
				{ version: "FiNALE", releaseDate: "2018-12-13" },
				{ version: "maimaiでらっくす", releaseDate: "2019-07-11" },
			],
			songs: [
				{
					title: "Launch Song",
					sheets: [
						{
							type: "dx",
							difficulty: "master",
							version: "maimaiでらっくす",
							releaseDate: "2019-07-10",
						},
					],
				},
			],
		});

		const song = normalized.songs[0] as { version: string; sheets: Array<{ version: string }> };
		expect(song.version).toBe("maimaiでらっくす");
		expect(song.sheets[0]?.version).toBe("maimaiでらっくす");
	});

	it("normalizes pure utage songs into the client chart and version contract", () => {
		const normalized = normalizeDxDataCatalog({
			versions: [{ version: "PRiSM PLUS", releaseDate: "2025-03-13" }],
			songs: [
				{
					title: "[協]Ultimate taste",
					sheets: [
						{
							type: "utage2p",
							difficulty: "【協】",
							version: "PRiSM PLUS",
							releaseDate: "2025-05-21",
						},
					],
				},
			],
		});

		const song = normalized.songs[0] as {
			version: string;
			releaseDate: string;
			sheets: Array<{ type: string; version: string }>;
		};
		expect(song.version).toBe("PRiSM PLUS");
		expect(song.releaseDate).toBe("2025-05-21");
		expect(song.sheets[0]).toMatchObject({ type: "utage", version: "PRiSM PLUS" });
	});

	it("fills the client level value from dxdata's display level", () => {
		const normalized = normalizeDxDataCatalog({
			songs: [
				{
					title: "Level Song",
					sheets: [
						{ type: "dx", difficulty: "expert", level: "12" },
						{ type: "dx", difficulty: "master", level: "13+" },
					],
				},
			],
		});

		const song = normalized.songs[0] as { sheets: Array<{ levelValue: number }> };
		expect(song.sheets.map((item) => item.levelValue)).toEqual([12, 13.6]);
	});

	it("uses dxdata version history for regional constants", () => {
		const normalized = normalizeDxDataCatalog({
			updateTime: "2026-08-15T15:00:12Z",
			versions: [
				{ version: "PRiSM PLUS", releaseDate: "2025-03-13" },
				{ version: "CiRCLE", releaseDate: "2025-09-18" },
				{ version: "CiRCLE PLUS", releaseDate: "2026-03-19" },
			],
			songs: [
				{
					title: "Versioned Constant Song",
					sheets: [
						{
							type: "dx",
							difficulty: "master",
							level: "13+",
							internalLevelValue: 13.9,
							regions: { jp: true, intl: true, cn: true },
							regionOverrides: { intl: { version: "PRiSM PLUS" } },
							multiverInternalLevelValue: {
								"PRiSM PLUS": 13.8,
								CiRCLE: 13.8,
								"CiRCLE PLUS": 13.9,
							},
						},
						{
							type: "dx",
							difficulty: "expert",
							level: "12",
							internalLevelValue: 12.5,
							regions: { jp: true, intl: true, cn: true },
							multiverInternalLevelValue: { Splash: 12.1 },
						},
					],
				},
			],
		});

		const song = normalized.songs[0] as {
			sheets: Array<{
				internalLevelValue: number;
				regionOverrides?: Record<string, Record<string, unknown>>;
			}>;
		};
		expect(song.sheets[0]?.regionOverrides).toEqual({
			intl: {
				version: "PRiSM PLUS",
				level: "13+",
				levelValue: 13.6,
				internalLevel: "13.9",
				internalLevelValue: 13.9,
			},
			cn: {
				level: "13+",
				levelValue: 13.6,
				internalLevel: "13.8",
				internalLevelValue: 13.8,
			},
		});
		expect(song.sheets[1]?.internalLevelValue).toBe(12.5);
		expect(song.sheets[1]?.regionOverrides).toBeUndefined();
	});
});

describe("mergeSongIdPayload", () => {
	it("retains provider IDs for whitespace-only song titles", () => {
		expect(mergeSongIdPayload({ songs: [{ title: "\u3000", sheets: [{ type: "dx", internalId: 1422 }] }] }, [])).toEqual([
			{ id: 11422, name: "\u3000" },
		]);
	});
});

describe("mergeSongIdPayload", () => {
	it("adds dxdata ids while preserving existing id names", () => {
		const merged = mergeSongIdPayload(
			{
				songs: [
					{
						title: "Catalog Title",
						sheets: [
							{ type: "std", internalId: 367 },
							{ type: "dx", internalId: 367 },
							{ type: "dx", internalId: 11844 },
						],
					},
				],
			},
			[
				{ id: 367, name: "Existing Standard Title" },
				{ id: 42, name: "Existing Only" },
			],
		);

		expect(merged).toEqual([
			{ id: 42, name: "Existing Only" },
			{ id: 367, name: "Existing Standard Title" },
			{ id: 10367, name: "Catalog Title" },
			{ id: 11844, name: "Catalog Title" },
		]);
	});
});
