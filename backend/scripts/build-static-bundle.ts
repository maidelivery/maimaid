/**
 * Builds a static bundle and publishes it to a running API, for GitHub Actions.
 *
 * Assembling a bundle fetches every upstream source (~15 MB of JSON), merges chart
 * stats, and hashes the result — several full parses and re-serializations, and by
 * far the heaviest thing the API process ever did. On a 1 vCPU / 1 GB host that is
 * enough to get the process OOM-killed mid-build. Running it on a CI runner
 * instead leaves the server with only the work that genuinely needs the database.
 *
 * The compute itself is `composeBundlePayload`, shared verbatim with
 * `StaticBundleService.buildBundle`, so the md5 cannot drift between the two
 * paths. The API receives only compact coordination data:
 *   - the song-id mapping (a few hundred KB), so it can aggregate `best_scores`
 *   - the finished artifact metadata, after CI uploads the JSON directly to R2
 *
 * Usage:
 *   MAIMAID_API_URL=https://api.example.com \
 *   MAIMAID_INTERNAL_JOB_TOKEN=... \
 *   pnpm run static-bundle:build [-- --force]
 */
// Must be the first import: tsyringe checks for this polyfill on load, before
// any of the service imports below can pull it in transitively.
import "reflect-metadata";
import {
	buildSongIdMapping,
	serializeSongIdMapping,
	type SerializedChartFitSongIdMapping,
} from "../src/services/chart-fit.service.js";
import {
	collectStaticAssetCandidates,
	type StaticAssetCandidate,
	type StaticAssetKind,
} from "../src/services/static-bundle-assets.utils.js";
import { composeBundlePayload, type StaticSourceTarget } from "../src/services/static-bundle.utils.js";

type StaticAssetConfiguration = {
	coverBaseUrl: string;
	coverFallbackBaseUrl: string;
	presetAvatarBaseUrl: string;
	presetAvatarFallbackBaseUrl: string;
};

type StaticAssetUpload = {
	kind: StaticAssetKind;
	name: string;
	uploadUrl: string;
	contentType: string;
	cacheControl: string;
};

const apiBaseUrl = process.env.MAIMAID_API_URL?.trim().replace(/\/+$/u, "");
const jobToken = process.env.MAIMAID_INTERNAL_JOB_TOKEN?.trim();
const force = process.argv.includes("--force");

if (!apiBaseUrl) {
	throw new Error("MAIMAID_API_URL is required.");
}
if (!jobToken) {
	throw new Error("MAIMAID_INTERNAL_JOB_TOKEN is required.");
}

// Source fetches, R2 upload, and catalog apply can each take a while. Node's
// default has no timeout, which would leave a stuck build until the CI job limit.
const REQUEST_TIMEOUT_MS = 10 * 60_000;
const ASSET_REQUEST_TIMEOUT_MS = 60_000;
const ASSET_UPLOAD_BATCH_SIZE = 100;
const ASSET_UPLOAD_CONCURRENCY = 12;

const callApi = async <T>(path: string, init?: { method?: string; body?: unknown }): Promise<T> => {
	const method = init?.method ?? "GET";
	const response = await fetch(`${apiBaseUrl}${path}`, {
		method,
		headers: {
			authorization: `Bearer ${jobToken}`,
			...(init?.body === undefined ? {} : { "content-type": "application/json" }),
		},
		...(init?.body === undefined ? {} : { body: JSON.stringify(init.body) }),
		signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
	});

	if (!response.ok) {
		// Truncated: an error body can itself be large, and a CI log is not the
		// place to dump a rejected bundle.
		const detail = (await response.text().catch(() => "")).slice(0, 2000);
		throw new Error(`${method} ${path} failed: HTTP ${response.status} ${detail}`);
	}

	return (await response.json()) as T;
};

const mapWithConcurrency = async <T>(items: T[], limit: number, operation: (item: T) => Promise<void>) => {
	let nextIndex = 0;
	const worker = async () => {
		while (nextIndex < items.length) {
			const index = nextIndex;
			nextIndex += 1;
			await operation(items[index] as T);
		}
	};
	await Promise.all(Array.from({ length: Math.min(limit, items.length) }, worker));
};

const retry = async (operation: () => Promise<void>, attempts = 3) => {
	let lastError: unknown;
	for (let attempt = 1; attempt <= attempts; attempt += 1) {
		try {
			await operation();
			return;
		} catch (error) {
			lastError = error;
			if (attempt < attempts) {
				await new Promise((resolve) => setTimeout(resolve, attempt * 500));
			}
		}
	}
	throw lastError;
};

const uploadStaticAsset = async (candidate: StaticAssetCandidate, upload: StaticAssetUpload) => {
	await retry(async () => {
		const sourceResponse = await fetch(candidate.sourceUrl, {
			headers: { accept: "image/*", "user-agent": "maimaid-static-bundle-builder" },
			signal: AbortSignal.timeout(ASSET_REQUEST_TIMEOUT_MS),
		});
		if (!sourceResponse.ok) {
			throw new Error(`Asset source failed: HTTP ${sourceResponse.status} ${candidate.sourceUrl}`);
		}
		const sourceContentType = sourceResponse.headers.get("content-type") ?? "";
		if (!sourceContentType.toLocaleLowerCase().startsWith("image/")) {
			throw new Error(`Asset source returned ${sourceContentType || "an unknown content type"}: ${candidate.sourceUrl}`);
		}
		const body = await sourceResponse.arrayBuffer();
		if (body.byteLength === 0) {
			throw new Error(`Asset source returned an empty image: ${candidate.sourceUrl}`);
		}

		const uploadResponse = await fetch(upload.uploadUrl, {
			method: "PUT",
			headers: {
				"content-type": upload.contentType,
				"cache-control": upload.cacheControl,
			},
			body,
			signal: AbortSignal.timeout(ASSET_REQUEST_TIMEOUT_MS),
		});
		if (!uploadResponse.ok) {
			const detail = (await uploadResponse.text().catch(() => "")).slice(0, 500);
			throw new Error(`R2 asset upload failed: HTTP ${uploadResponse.status} ${detail}`);
		}
	});
};

const mirrorStaticAssets = async (payload: Record<string, unknown>) => {
	const candidates = collectStaticAssetCandidates(payload);
	const byKey = new Map(candidates.map((candidate) => [`${candidate.kind}|${candidate.name}`, candidate]));
	let existingCount = 0;
	let uploadedCount = 0;
	console.log(`[assets] checking ${candidates.length} cover/avatar object(s)`);

	for (let offset = 0; offset < candidates.length; offset += ASSET_UPLOAD_BATCH_SIZE) {
		const batch = candidates.slice(offset, offset + ASSET_UPLOAD_BATCH_SIZE);
		const preparation = await callApi<{ uploads: StaticAssetUpload[]; existingCount: number }>(
			"/internal/jobs/static-bundle/assets/uploads",
			{
				method: "POST",
				body: { assets: batch.map(({ kind, name }) => ({ kind, name })) },
			},
		);
		existingCount += preparation.existingCount;
		await mapWithConcurrency(preparation.uploads, ASSET_UPLOAD_CONCURRENCY, async (upload) => {
			const candidate = byKey.get(`${upload.kind}|${upload.name}`);
			if (!candidate) {
				throw new Error(`API requested an unknown static asset: ${upload.kind}/${upload.name}`);
			}
			await uploadStaticAsset(candidate, upload);
			uploadedCount += 1;
			if (uploadedCount % 50 === 0) {
				console.log(`[assets] uploaded ${uploadedCount} new object(s)`);
			}
		});
	}

	console.log(`[assets] ready: ${existingCount} existing, ${uploadedCount} uploaded`);
};

const main = async () => {
	console.log(`[build] target ${apiBaseUrl}${force ? " (force)" : ""}`);

	const { sources, assets } = await callApi<{
		sources: StaticSourceTarget[];
		assets: StaticAssetConfiguration | null;
	}>("/internal/jobs/static-bundle/sources");
	if (!assets) {
		throw new Error("Static asset R2 configuration is unavailable.");
	}
	console.log(`[build] ${sources.length} enabled source(s): ${sources.map((source) => source.category).join(", ")}`);
	console.log(`[assets] cover delivery ${assets.coverBaseUrl}`);
	console.log(`[assets] avatar delivery ${assets.presetAvatarBaseUrl}`);

	const composed = await composeBundlePayload(sources, async (input) => {
		const mapping = buildSongIdMapping(input.dataJson, input.songidJson);
		const serialized: SerializedChartFitSongIdMapping = serializeSongIdMapping(mapping);
		console.log(
			`[build] song-id mapping: ${Object.keys(serialized.byTitleAndType).length} title+type, ` +
				`${Object.keys(serialized.byTitle).length} title`,
		);
		return callApi<{ payload: unknown; meta: Record<string, unknown> }>("/internal/jobs/static-bundle/self-chart-fit", {
			method: "POST",
			body: serialized,
		});
	});

	console.log(`[build] composed md5=${composed.md5}`);
	await mirrorStaticAssets(composed.payload);
	const preparation = await callApi<
		| {
				uploadRequired: false;
				bundle: { version: string; md5: string; createdAt: string };
		  }
		| {
				uploadRequired: true;
				version: string;
				md5: string;
				createdAt: string;
				objectKey: string;
				uploadUrl: string;
				contentType: string;
				cacheControl: string;
		  }
	>("/internal/jobs/static-bundle/upload", {
		method: "POST",
		body: { md5: composed.md5, force },
	});

	if (!preparation.uploadRequired) {
		console.log(`[build] unchanged; kept ${preparation.bundle.version} (md5=${preparation.bundle.md5})`);
		return;
	}

	const artifact = JSON.stringify({
		version: preparation.version,
		md5: preparation.md5,
		createdAt: preparation.createdAt,
		payload: composed.payload,
		sourceMeta: composed.sourceMeta,
	});
	console.log(`[build] uploading ${(Buffer.byteLength(artifact) / 1024 / 1024).toFixed(1)} MiB to R2`);
	const uploadResponse = await fetch(preparation.uploadUrl, {
		method: "PUT",
		headers: {
			"content-type": preparation.contentType,
			"cache-control": preparation.cacheControl,
		},
		body: artifact,
		signal: AbortSignal.timeout(REQUEST_TIMEOUT_MS),
	});
	if (!uploadResponse.ok) {
		const detail = (await uploadResponse.text().catch(() => "")).slice(0, 2000);
		throw new Error(`R2 upload failed: HTTP ${uploadResponse.status} ${detail}`);
	}

	const result = await callApi<{
		created: boolean;
		bundle: { version: string; md5: string; createdAt: string };
	}>("/internal/jobs/static-bundle/publish", {
		method: "POST",
		body: {
			version: preparation.version,
			md5: preparation.md5,
			objectKey: preparation.objectKey,
			force,
		},
	});

	if (result.created) {
		console.log(`[build] published ${result.bundle.version} (md5=${result.bundle.md5})`);
		return;
	}
	console.log(`[build] activated existing ${result.bundle.version} (md5=${result.bundle.md5})`);
};

main().catch((error: unknown) => {
	console.error("[build] failed:", error instanceof Error ? error.message : error);
	process.exitCode = 1;
});
