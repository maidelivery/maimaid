import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { StaticBundleService } from "../src/services/static-bundle.service.js";
import { createStaticBundleArtifact } from "../src/services/static-bundle.utils.js";

const md5 = "0123456789abcdef0123456789abcdef";

const makeService = (database: object, storage: object) =>
	new StaticBundleService(database as never, {} as never, {} as never, {} as never, storage as never);

describe("StaticBundleService R2 publishing", () => {
	it("includes the active bundle R2 URL in the manifest", async () => {
		const bundle = {
			version: "bundle-1786852800000",
			md5,
			objectKey: `static-bundles/bundle-1786852800000-${md5}.json`,
			createdAt: new Date("2026-08-16T04:00:00.000Z"),
		};
		const database = {
			staticBundle: {
				findFirst: vi.fn().mockResolvedValue(bundle),
			},
		};
		const storage = {
			staticBundlePublicUrl: vi.fn().mockReturnValue("https://static.example.com/static-bundles/bundle.json"),
			staticAssetConfiguration: vi.fn().mockReturnValue({
				coverBaseUrl:
					"https://static.example.com/cdn-cgi/image/format=avif,quality=80,width=512,fit=scale-down/static-assets/covers/",
				coverFallbackBaseUrl: "https://static.example.com/static-assets/covers/",
				presetAvatarBaseUrl:
					"https://static.example.com/cdn-cgi/image/format=avif,quality=80,width=256,fit=scale-down/static-assets/lxns-icons/",
				presetAvatarFallbackBaseUrl: "https://static.example.com/static-assets/lxns-icons/",
			}),
		};

		const result = await makeService(database, storage).manifest();

		expect(result).toEqual({
			version: bundle.version,
			md5: bundle.md5,
			createdAt: bundle.createdAt,
			downloadUrl: "https://static.example.com/static-bundles/bundle.json",
			assets: storage.staticAssetConfiguration(),
		});
	});

	it("skips an upload when an R2-backed bundle already has the same md5", async () => {
		const bundle = {
			id: 1n,
			version: "bundle-1786852800000",
			md5,
			objectKey: `static-bundles/bundle-1786852800000-${md5}.json`,
			createdAt: new Date("2026-08-16T04:00:00.000Z"),
		};
		const database = {
			staticBundle: {
				findFirst: vi.fn().mockResolvedValue(bundle),
			},
		};
		const storage = {
			createStaticBundleUploadUrl: vi.fn(),
		};

		const result = await makeService(database, storage).prepareBundleUpload(md5);

		expect(result).toEqual({ uploadRequired: false, bundle });
		expect(storage.createStaticBundleUploadUrl).not.toHaveBeenCalled();
	});

	it("issues an upload URL for a new bundle", async () => {
		const database = {
			staticBundle: {
				findFirst: vi.fn().mockResolvedValue(null),
			},
		};
		const storage = {
			createStaticBundleUploadUrl: vi.fn().mockImplementation((version: string, hash: string) => ({
				key: `static-bundles/${version}-${hash}.json`,
				uploadUrl: "https://r2.example/upload",
				contentType: "application/json",
				cacheControl: "public, max-age=31536000, immutable",
			})),
		};

		const result = await makeService(database, storage).prepareBundleUpload(md5);

		expect(result.uploadRequired).toBe(true);
		if (result.uploadRequired) {
			expect(result.version).toMatch(/^bundle-\d+$/u);
			expect(result.objectKey).toBe(`static-bundles/${result.version}-${md5}.json`);
			expect(result.uploadUrl).toBe("https://r2.example/upload");
		}
	});

	it("backfills the R2 object key on an existing database-only bundle", async () => {
		const version = "bundle-1786852800000";
		const objectKey = `static-bundles/${version}-${md5}.json`;
		const createdAt = new Date("2026-08-16T04:00:00.000Z");
		const existing = { id: 1n, version, md5, objectKey: null, createdAt };
		const updated = { ...existing, objectKey };
		const artifact = createStaticBundleArtifact(version, md5, createdAt, { resources: { data_json: { songs: [] } } }, {});
		const database = {
			staticBundle: {
				findFirst: vi.fn().mockResolvedValue(existing),
				update: vi.fn().mockResolvedValue(updated),
			},
		};
		const storage = {
			staticBundleObjectKey: vi.fn().mockReturnValue(objectKey),
			getStaticBundleArtifact: vi.fn().mockResolvedValue(JSON.stringify(artifact)),
		};

		const result = await makeService(database, storage).publishUploadedBundle({ version, md5, objectKey });

		expect(result).toEqual({ bundle: updated, created: false });
		expect(database.staticBundle.update).toHaveBeenCalledWith({
			where: { id: existing.id },
			data: { objectKey },
		});
	});
});
