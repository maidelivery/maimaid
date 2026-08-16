import { DeleteObjectCommand, GetObjectCommand, HeadObjectCommand, PutObjectCommand, S3Client } from "@aws-sdk/client-s3";
import { getSignedUrl } from "@aws-sdk/s3-request-presigner";
import { inject, singleton } from "tsyringe";
import type { Env } from "../env.js";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";

export type StaticAssetKind = "cover" | "presetAvatar";

export type StaticAssetReference = {
	kind: StaticAssetKind;
	name: string;
};

export type StaticAssetConfiguration = {
	coverBaseUrl: string;
	coverFallbackBaseUrl: string;
	presetAvatarBaseUrl: string;
	presetAvatarFallbackBaseUrl: string;
};

@singleton()
export class StorageService {
	private static readonly STATIC_BUNDLE_CONTENT_TYPE = "application/json";
	private static readonly STATIC_BUNDLE_CACHE_CONTROL = "public, max-age=31536000, immutable";
	private static readonly STATIC_ASSET_CONTENT_TYPE = "image/png";
	private static readonly STATIC_ASSET_CACHE_CONTROL = "public, max-age=31536000, immutable";
	private static readonly STATIC_ASSET_UPLOAD_CONCURRENCY = 12;
	private readonly client: S3Client | null;
	private readonly signingClient: S3Client | null;

	constructor(@inject(TOKENS.Env) private readonly env: Env) {
		const endpoint = env.S3_ENDPOINT;
		const signingEndpoint = env.S3_PUBLIC_ENDPOINT ?? endpoint;
		const accessKeyId = env.S3_ACCESS_KEY_ID;
		const secretAccessKey = env.S3_SECRET_ACCESS_KEY;
		if (!endpoint || !signingEndpoint || !accessKeyId || !secretAccessKey) {
			this.client = null;
			this.signingClient = null;
			return;
		}

		const baseConfig = {
			region: env.S3_REGION,
			credentials: {
				accessKeyId,
				secretAccessKey,
			},
			// R2 supports path style, and MinIO required it.
			forcePathStyle: true,
			// Without this the SDK computes a CRC32 over the *empty* body at signing
			// time and bakes `x-amz-checksum-crc32` into the presigned query string.
			// The client then PUTs real bytes against a signature that promises an
			// empty-body checksum, and R2 rejects the upload. The parameter is signed,
			// so the client can neither drop it nor satisfy it.
			requestChecksumCalculation: "WHEN_REQUIRED",
			// R2 does not return the trailing checksums the SDK would try to validate.
			responseChecksumValidation: "WHEN_REQUIRED",
		} as const;

		this.client = new S3Client({
			endpoint,
			...baseConfig,
		});
		this.signingClient = new S3Client({
			endpoint: signingEndpoint,
			...baseConfig,
		});
	}

	async createAvatarUploadUrl(profileId: string, contentType: string): Promise<{ key: string; uploadUrl: string }> {
		if (!this.signingClient) {
			throw new AppError(500, "storage_not_configured", "S3 storage is not configured.");
		}
		const key = `avatars/profiles/${profileId}`;
		const command = new PutObjectCommand({
			Bucket: this.env.S3_BUCKET,
			Key: key,
			ContentType: contentType,
		});

		const uploadUrl = await getSignedUrl(this.signingClient, command, { expiresIn: 300 });
		return { key, uploadUrl };
	}

	isStaticBundleStorageConfigured() {
		return Boolean(
			this.client && this.signingClient && this.env.S3_STATIC_BUNDLE_BUCKET && this.env.S3_STATIC_BUNDLE_PUBLIC_BASE_URL,
		);
	}

	staticBundleObjectKey(version: string, md5: string) {
		return `static-bundles/${version}-${md5}.json`;
	}

	staticAssetObjectKey(asset: StaticAssetReference) {
		const name = asset.name.trim();
		if (!name || name.length > 200 || name === "." || name === ".." || name.includes("/") || name.includes("\\")) {
			throw new AppError(400, "static_asset_invalid_name", "Static asset name is invalid.");
		}
		if (asset.kind === "presetAvatar" && !/^\d+\.png$/u.test(name)) {
			throw new AppError(400, "static_asset_invalid_name", "Preset avatar name must be a numeric PNG filename.");
		}
		if (asset.kind === "cover" && !name.toLocaleLowerCase().endsWith(".png")) {
			throw new AppError(400, "static_asset_invalid_name", "Cover name must be a PNG filename.");
		}

		const directory = asset.kind === "cover" ? "covers" : "lxns-icons";
		return `static-assets/${directory}/${name}`;
	}

	staticAssetConfiguration(): StaticAssetConfiguration | null {
		if (!this.isStaticBundleStorageConfigured()) {
			return null;
		}
		const coverFallbackBaseUrl = this.staticAssetPublicBaseUrl("covers");
		const presetAvatarFallbackBaseUrl = this.staticAssetPublicBaseUrl("lxns-icons");
		const coverBaseUrl = this.transformedStaticAssetBaseUrl("covers", 512);
		const presetAvatarBaseUrl = this.transformedStaticAssetBaseUrl("lxns-icons", 256);
		if (!coverBaseUrl || !coverFallbackBaseUrl || !presetAvatarBaseUrl || !presetAvatarFallbackBaseUrl) {
			return null;
		}
		return {
			coverBaseUrl,
			coverFallbackBaseUrl,
			presetAvatarBaseUrl,
			presetAvatarFallbackBaseUrl,
		};
	}

	async prepareStaticAssetUploads(assets: StaticAssetReference[]) {
		const { client, signingClient, bucket } = this.requireStaticBundleStorage();
		const uploads = await this.mapWithConcurrency(assets, StorageService.STATIC_ASSET_UPLOAD_CONCURRENCY, async (asset) => {
			const key = this.staticAssetObjectKey(asset);
			if (await this.objectExists(client, bucket, key)) {
				return null;
			}

			const command = new PutObjectCommand({
				Bucket: bucket,
				Key: key,
				ContentType: StorageService.STATIC_ASSET_CONTENT_TYPE,
				CacheControl: StorageService.STATIC_ASSET_CACHE_CONTROL,
			});
			const uploadUrl = await getSignedUrl(signingClient, command, { expiresIn: 900 });
			return {
				...asset,
				key,
				uploadUrl,
				contentType: StorageService.STATIC_ASSET_CONTENT_TYPE,
				cacheControl: StorageService.STATIC_ASSET_CACHE_CONTROL,
			};
		});
		return uploads.filter((upload): upload is NonNullable<typeof upload> => upload !== null);
	}

	async createStaticBundleUploadUrl(
		version: string,
		md5: string,
	): Promise<{ key: string; uploadUrl: string; contentType: string; cacheControl: string }> {
		const { signingClient, bucket } = this.requireStaticBundleStorage();
		const key = this.staticBundleObjectKey(version, md5);
		const command = new PutObjectCommand({
			Bucket: bucket,
			Key: key,
			ContentType: StorageService.STATIC_BUNDLE_CONTENT_TYPE,
			CacheControl: StorageService.STATIC_BUNDLE_CACHE_CONTROL,
		});
		const uploadUrl = await getSignedUrl(signingClient, command, { expiresIn: 600 });
		return {
			key,
			uploadUrl,
			contentType: StorageService.STATIC_BUNDLE_CONTENT_TYPE,
			cacheControl: StorageService.STATIC_BUNDLE_CACHE_CONTROL,
		};
	}

	async putStaticBundleArtifact(key: string, body: string) {
		const { client, bucket } = this.requireStaticBundleStorage();
		await client.send(
			new PutObjectCommand({
				Bucket: bucket,
				Key: key,
				Body: body,
				ContentType: StorageService.STATIC_BUNDLE_CONTENT_TYPE,
				CacheControl: StorageService.STATIC_BUNDLE_CACHE_CONTROL,
			}),
		);
	}

	async getStaticBundleArtifact(key: string) {
		const { client, bucket } = this.requireStaticBundleStorage();
		const object = await this.getObjectFromBucket(
			client,
			bucket,
			key,
			"static_bundle_object_not_found",
			"Static bundle object not found.",
		);
		return new Response(object.body).text();
	}

	async deleteStaticBundleArtifact(key: string) {
		const { client, bucket } = this.requireStaticBundleStorage();
		await client.send(
			new DeleteObjectCommand({
				Bucket: bucket,
				Key: key,
			}),
		);
	}

	staticBundlePublicUrl(key: string): string | null {
		const baseUrl = this.env.S3_STATIC_BUNDLE_PUBLIC_BASE_URL;
		if (!baseUrl) {
			return null;
		}
		const normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl : `${baseUrl}/`;
		const encodedKey = key
			.split("/")
			.map((segment) => encodeURIComponent(segment))
			.join("/");
		return new URL(encodedKey, normalizedBaseUrl).toString();
	}

	private staticAssetPublicBaseUrl(directory: "covers" | "lxns-icons") {
		return this.staticBundlePublicUrl(`static-assets/${directory}/`);
	}

	private transformedStaticAssetBaseUrl(directory: "covers" | "lxns-icons", width: number) {
		const publicBaseUrl = this.env.S3_STATIC_BUNDLE_PUBLIC_BASE_URL;
		if (!publicBaseUrl) {
			return null;
		}
		const publicBase = new URL(publicBaseUrl.endsWith("/") ? publicBaseUrl : `${publicBaseUrl}/`);
		const sourcePrefix = `${publicBase.pathname.replace(/^\/+|\/+$/gu, "")}/static-assets/${directory}/`.replace(/^\//u, "");
		return new URL(
			`cdn-cgi/image/format=avif,quality=80,width=${width},fit=scale-down/${sourcePrefix}`,
			publicBase.origin,
		).toString();
	}

	async deleteAvatar(profileId: string) {
		if (!this.client) {
			throw new AppError(500, "storage_not_configured", "S3 storage is not configured.");
		}

		await this.client.send(
			new DeleteObjectCommand({
				Bucket: this.env.S3_BUCKET,
				Key: `avatars/profiles/${profileId}`,
			}),
		);
	}

	async getObject(
		key: string,
	): Promise<{ body: BodyInit; contentType: string | null; etag: string | null; lastModified: Date | null }> {
		if (!this.client) {
			throw new AppError(500, "storage_not_configured", "S3 storage is not configured.");
		}
		return this.getObjectFromBucket(this.client, this.env.S3_BUCKET, key, "avatar_not_found", "Avatar object not found.");
	}

	private async getObjectFromBucket(
		client: S3Client,
		bucket: string,
		key: string,
		notFoundCode: string,
		notFoundMessage: string,
	): Promise<{ body: BodyInit; contentType: string | null; etag: string | null; lastModified: Date | null }> {
		try {
			const result = await client.send(
				new GetObjectCommand({
					Bucket: bucket,
					Key: key,
				}),
			);

			if (!result.Body) {
				throw new AppError(404, notFoundCode, notFoundMessage);
			}

			const body = await this.toBodyInit(result.Body);
			return {
				body,
				contentType: result.ContentType ?? null,
				etag: result.ETag ?? null,
				lastModified: result.LastModified ?? null,
			};
		} catch (error) {
			const statusCode = this.readHttpStatus(error);
			if (statusCode === 404) {
				throw new AppError(404, notFoundCode, notFoundMessage);
			}
			throw error;
		}
	}

	private async objectExists(client: S3Client, bucket: string, key: string) {
		try {
			await client.send(new HeadObjectCommand({ Bucket: bucket, Key: key }));
			return true;
		} catch (error) {
			if (this.readHttpStatus(error) === 404) {
				return false;
			}
			throw error;
		}
	}

	private requireStaticBundleStorage() {
		const bucket = this.env.S3_STATIC_BUNDLE_BUCKET;
		if (!this.client || !this.signingClient || !bucket || !this.env.S3_STATIC_BUNDLE_PUBLIC_BASE_URL) {
			throw new AppError(500, "static_bundle_storage_not_configured", "Static bundle R2 storage is not configured.");
		}
		return {
			client: this.client,
			signingClient: this.signingClient,
			bucket,
		};
	}

	private async toBodyInit(body: unknown): Promise<BodyInit> {
		if (body instanceof ReadableStream) {
			return body;
		}
		if (body instanceof Uint8Array) {
			return this.toArrayBuffer(body);
		}
		if (typeof body === "string") {
			return body;
		}
		if (body instanceof ArrayBuffer) {
			return body;
		}

		if (body instanceof Blob) {
			return body;
		}

		if (
			typeof body === "object" &&
			body !== null &&
			"transformToWebStream" in body &&
			typeof body.transformToWebStream === "function"
		) {
			return body.transformToWebStream() as ReadableStream;
		}

		if (
			typeof body === "object" &&
			body !== null &&
			"transformToByteArray" in body &&
			typeof body.transformToByteArray === "function"
		) {
			const bytes = (await body.transformToByteArray()) as Uint8Array;
			return this.toArrayBuffer(bytes);
		}

		if (this.isAsyncIterable(body)) {
			const chunks: Uint8Array[] = [];
			for await (const chunk of body) {
				if (chunk instanceof Uint8Array) {
					chunks.push(chunk);
					continue;
				}

				if (typeof chunk === "string") {
					chunks.push(new TextEncoder().encode(chunk));
					continue;
				}

				if (chunk instanceof ArrayBuffer) {
					chunks.push(new Uint8Array(chunk));
				}
			}
			return this.concatChunks(chunks);
		}

		throw new AppError(500, "storage_stream_error", "Unsupported storage body stream type.");
	}

	private concatChunks(chunks: Uint8Array[]) {
		const totalLength = chunks.reduce((sum, chunk) => sum + chunk.length, 0);
		const merged = new Uint8Array(totalLength);
		let offset = 0;
		for (const chunk of chunks) {
			merged.set(chunk, offset);
			offset += chunk.length;
		}
		return merged.buffer;
	}

	private toArrayBuffer(value: Uint8Array) {
		const copy = new Uint8Array(value.byteLength);
		copy.set(value);
		return copy.buffer;
	}

	private isAsyncIterable(value: unknown): value is AsyncIterable<unknown> {
		return typeof value === "object" && value !== null && Symbol.asyncIterator in value;
	}

	private async mapWithConcurrency<T, R>(items: T[], limit: number, operation: (item: T) => Promise<R>): Promise<R[]> {
		const results = new Array<R>(items.length);
		let nextIndex = 0;
		const worker = async () => {
			while (nextIndex < items.length) {
				const index = nextIndex;
				nextIndex += 1;
				results[index] = await operation(items[index] as T);
			}
		};
		await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
		return results;
	}

	private readHttpStatus(error: unknown): number | null {
		if (typeof error !== "object" || error === null || !("$metadata" in error)) {
			return null;
		}

		const metadata = error.$metadata;
		if (typeof metadata !== "object" || metadata === null || !("httpStatusCode" in metadata)) {
			return null;
		}

		const status = metadata.httpStatusCode;
		return typeof status === "number" ? status : null;
	}
}
