import "reflect-metadata";
import { afterEach, describe, expect, it, vi } from "vitest";
import { StaticBundleService } from "../src/services/static-bundle.service.js";
import { createStaticBundleArtifact } from "../src/services/static-bundle.utils.js";

const md5 = "0123456789abcdef0123456789abcdef";
const version = "bundle-1786852800000";
const createdAt = new Date("2026-08-16T04:00:00.000Z");
const manifestUrl = "https://static.example.com/manifest.json";
const bundleUrl = `https://static.example.com/bundles/${md5}.json`;

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("StaticBundleService Worker publications", () => {
	it("verifies the public artifact, applies its catalog, and records the publication", async () => {
		const payload = { resources: { data_json: { songs: [] } } };
		const artifact = createStaticBundleArtifact(version, md5, createdAt, payload, { data_json: { url: "source" } });
		const manifest = { schemaVersion: 1, version, md5, createdAt: createdAt.toISOString(), bundle: `/bundles/${md5}.json` };
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValueOnce(Response.json(manifest)).mockResolvedValueOnce(Response.json(artifact)),
		);

		const stored = { id: 1n, version, md5, manifestUrl, bundleUrl, active: true, createdAt };
		const transaction = {
			staticBundle: {
				updateMany: vi.fn().mockResolvedValue({ count: 1 }),
				upsert: vi.fn().mockResolvedValue(stored),
			},
		};
		const database = {
			staticBundle: {
				findUnique: vi.fn().mockResolvedValue(null),
				findMany: vi.fn().mockResolvedValue([]),
			},
			$transaction: vi.fn().mockImplementation((operation: (tx: typeof transaction) => unknown) => operation(transaction)),
		};
		const catalog = {
			applyCatalogData: vi.fn().mockResolvedValue({ applied: true }),
		};

		const result = await new StaticBundleService(database as never, catalog as never).recordGeneration({
			version,
			md5,
			createdAt: createdAt.toISOString(),
			manifestUrl,
			bundleUrl,
		});

		expect(result).toEqual({ bundle: stored, created: true, catalogApplied: true });
		expect(fetch).toHaveBeenCalledWith(
			manifestUrl,
			expect.objectContaining({ headers: expect.objectContaining({ accept: "application/json" }) }),
		);
		expect(fetch).toHaveBeenCalledWith(bundleUrl, expect.any(Object));
		expect(catalog.applyCatalogData).toHaveBeenCalledWith(
			payload.resources.data_json,
			expect.objectContaining({
				source: `static_bundle:${version}`,
				sourceUrl: bundleUrl,
			}),
		);
		expect(transaction.staticBundle.upsert).toHaveBeenCalledWith(
			expect.objectContaining({
				create: expect.objectContaining({ version, md5, manifestUrl, bundleUrl, active: true }),
			}),
		);
	});

	it("rejects a public artifact whose metadata differs from the notification", async () => {
		const artifact = createStaticBundleArtifact(
			"bundle-1786852800001",
			md5,
			createdAt,
			{ resources: { data_json: { songs: [] } } },
			{},
		);
		const manifest = { schemaVersion: 1, version, md5, createdAt: createdAt.toISOString(), bundle: `/bundles/${md5}.json` };
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValueOnce(Response.json(manifest)).mockResolvedValueOnce(Response.json(artifact)),
		);
		const database = { staticBundle: {} };
		const catalog = { applyCatalogData: vi.fn() };

		await expect(
			new StaticBundleService(database as never, catalog as never).recordGeneration({
				version,
				md5,
				createdAt: createdAt.toISOString(),
				manifestUrl,
				bundleUrl,
			}),
		).rejects.toMatchObject({ code: "static_bundle_artifact_mismatch" });
		expect(catalog.applyCatalogData).not.toHaveBeenCalled();
	});

	it("rejects a manifest whose bundle URL differs from the notification", async () => {
		const manifest = {
			schemaVersion: 1,
			version,
			md5,
			createdAt: createdAt.toISOString(),
			bundle: "/bundles/other.json",
		};
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(manifest)));
		const catalog = { applyCatalogData: vi.fn() };

		await expect(
			new StaticBundleService({} as never, catalog as never).recordGeneration({
				version,
				md5,
				createdAt: createdAt.toISOString(),
				manifestUrl,
				bundleUrl,
			}),
		).rejects.toMatchObject({ code: "static_bundle_manifest_mismatch" });
		expect(fetch).toHaveBeenCalledTimes(1);
		expect(catalog.applyCatalogData).not.toHaveBeenCalled();
	});
});
