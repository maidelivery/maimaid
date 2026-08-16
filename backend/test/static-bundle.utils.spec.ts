import "reflect-metadata";
import { afterEach, describe, expect, it, vi } from "vitest";
import { composeBundlePayload, mergeLxnsCnRegions } from "../src/services/static-bundle.utils.js";

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

	it("stores LXNS chart constants as CN region overrides", () => {
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
			sheets: Array<{
				regionOverrides: Record<string, Record<string, unknown>>;
			}>;
		}>;
		expect(songs[0]?.sheets[0]?.regionOverrides).toEqual({
			intl: { version: "CiRCLE PLUS" },
			cn: {
				level: "13+",
				levelValue: 13.8,
				internalLevel: "13.8",
				internalLevelValue: 13.8,
			},
		});
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

	it("rejects an empty LXNS response before clearing CN availability", () => {
		expect(() => mergeLxnsCnRegions({ songs: [] }, { songs: [] })).toThrow("LXNS song list is missing songs");
	});

	it("applies LXNS during composition without shipping the raw song list", async () => {
		const dataJson = {
			songs: [{ title: "Current Song", artist: "Artist", sheets: [sheet("dx", "master", false)] }],
		};
		const lxnsSongList = {
			songs: [
				{
					title: "Current Song",
					artist: "Artist",
					difficulties: { standard: [], dx: [lxnsChart("dx", 3)] },
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
		const mergedData = resources.data_json as { songs: Array<{ sheets: Array<{ regions: { cn: boolean } }> }> };
		expect(mergedData.songs[0]?.sheets[0]?.regions.cn).toBe(true);
		expect(resources.lxns_song_list).toBeUndefined();
		expect(result.sourceMeta.lxns_song_list).toMatchObject({
			lxnsSongCount: 1,
			lxnsChartCount: 1,
			catalogChartCount: 1,
			matchedChartCount: 1,
		});
	});
});
