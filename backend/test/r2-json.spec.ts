import { describe, expect, it } from "vitest";
import { R2JsonStorage, type R2JsonBucket } from "../.worker/r2-json.js";

const createBucket = () => {
	const objects = new Map<string, string>();
	const bucket: R2JsonBucket = {
		async get(key) {
			const value = objects.get(key);
			return value === undefined ? null : { async text() {
				return value;
			} };
		},
		async put(key, value) {
			objects.set(key, value);
		},
	};
	return { bucket, objects };
};

describe("R2JsonStorage", () => {
	it("keeps small JSON values in D1", async () => {
		const { bucket, objects } = createBucket();
		const storage = new R2JsonStorage(bucket);
		const value = { songs: ["a"] };

		expect(await storage.externalize("catalog_snapshots", value)).toEqual(value);
		expect(objects.size).toBe(0);
	});

	it("externalizes and hydrates large JSON values", async () => {
		const { bucket, objects } = createBucket();
		const storage = new R2JsonStorage(bucket);
		const value = { payload: "x".repeat(60_000) };

		const reference = await storage.externalize("static_bundles", value);
		expect(reference).toMatchObject({ bytes: 60_014 });
		expect(objects.size).toBe(1);

		const freshStorage = new R2JsonStorage(bucket);
		expect(await freshStorage.hydrate(reference)).toEqual(value);
	});

	it("transforms write arguments and read results", async () => {
		const { bucket } = createBucket();
		const storage = new R2JsonStorage(bucket);
		const value = { payload: "x".repeat(60_000) };
		const args = await storage.prepareArgs("chart_fit_snapshots", "create", {
			data: { payloadJson: value, metaJson: {} },
		});
		const reference = (args as { data: { payloadJson: unknown } }).data.payloadJson;

		expect(await storage.hydrateResult({ id: 1, payloadJson: reference })).toEqual({
			id: 1,
			payloadJson: value,
		});
	});
});
