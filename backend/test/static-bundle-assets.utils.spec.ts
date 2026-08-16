import { describe, expect, it } from "vitest";
import { collectStaticAssetCandidates } from "../src/services/static-bundle-assets.utils.js";

describe("static bundle asset collection", () => {
	it("deduplicates covers and maps LXNS icon ids to upstream PNGs", () => {
		const assets = collectStaticAssetCandidates({
			resources: {
				data_json: {
					songs: [{ imageName: "cover one.png" }, { imageName: "cover one.png" }, { imageName: "cover-two.png" }],
				},
				lxns_icon_list: {
					icons: [{ id: 1 }, { id: 605905 }, { id: "invalid" }],
				},
			},
		});

		expect(assets).toEqual([
			{
				kind: "cover",
				name: "cover one.png",
				sourceUrl: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/cover%20one.png",
			},
			{
				kind: "cover",
				name: "cover-two.png",
				sourceUrl: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/cover-two.png",
			},
			{
				kind: "presetAvatar",
				name: "1.png",
				sourceUrl: "https://assets2.lxns.net/maimai/icon/1.png",
			},
			{
				kind: "presetAvatar",
				name: "605905.png",
				sourceUrl: "https://assets2.lxns.net/maimai/icon/605905.png",
			},
		]);
	});
});
