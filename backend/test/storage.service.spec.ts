import "reflect-metadata";
import { describe, expect, it } from "vitest";
import { StorageService } from "../src/services/storage.service.js";

const makeStorage = (publicBaseUrl?: string) =>
	new StorageService({
		S3_ENDPOINT: "https://account.r2.cloudflarestorage.com",
		S3_REGION: "auto",
		S3_STATIC_BUNDLE_BUCKET: "maimaid-static",
		S3_ACCESS_KEY_ID: "access-key",
		S3_SECRET_ACCESS_KEY: "secret-key",
		S3_STATIC_BUNDLE_PUBLIC_BASE_URL: publicBaseUrl,
	} as never);

describe("StorageService static assets", () => {
	it("builds R2 object keys and Cloudflare AVIF delivery URLs", () => {
		const storage = makeStorage("https://static.example.com");

		expect(storage.staticAssetObjectKey({ kind: "cover", name: "cover.png" })).toBe("static-assets/covers/cover.png");
		expect(storage.staticAssetObjectKey({ kind: "presetAvatar", name: "123.png" })).toBe("static-assets/lxns-icons/123.png");
		expect(storage.staticAssetConfiguration()).toEqual({
			coverBaseUrl:
				"https://static.example.com/cdn-cgi/image/format=avif,quality=95,fit=scale-down/static-assets/covers/",
			coverFallbackBaseUrl: "https://static.example.com/static-assets/covers/",
			presetAvatarBaseUrl:
				"https://static.example.com/cdn-cgi/image/format=avif,quality=95,fit=scale-down/static-assets/lxns-icons/",
			presetAvatarFallbackBaseUrl: "https://static.example.com/static-assets/lxns-icons/",
		});
	});

	it("rejects names that can escape their static asset directory", () => {
		const storage = makeStorage();

		expect(() => storage.staticAssetObjectKey({ kind: "cover", name: "../cover.png" })).toThrow("Static asset name is invalid");
		expect(() => storage.staticAssetObjectKey({ kind: "presetAvatar", name: "avatar.png" })).toThrow("numeric PNG filename");
	});
});
