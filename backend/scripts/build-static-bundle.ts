/**
 * Builds the public static-data tree consumed directly by mobile and web clients.
 * Cloudflare deployment is handled by the workflow so the API only participates
 * in source configuration, private chart aggregation, and publication records.
 */
import "reflect-metadata";
import { mkdir, readFile, rm, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";
import {
	buildSongIdMapping,
	serializeSongIdMapping,
	type SerializedChartFitSongIdMapping,
} from "../src/services/chart-fit.service.js";
import { collectStaticAssetCandidates, type StaticAssetCandidate } from "../src/services/static-bundle-assets.utils.js";
import { composeBundlePayload, type StaticSourceTarget } from "../src/services/static-bundle.utils.js";

type StaticManifest = {
	schemaVersion: 1;
	version: string;
	md5: string;
	createdAt: string;
	bundle: string;
	assets: {
		coverBaseUrl: string;
		presetAvatarBaseUrl: string;
		coverFallbackBaseUrl: string;
		presetAvatarFallbackBaseUrl: string;
	};
};

const apiBaseUrl = process.env.MAIMAID_API_URL?.trim().replace(/\/+$/u, "");
const staticAssetsBaseUrl = process.env.MAIMAID_STATIC_ASSETS_URL?.trim().replace(/\/+$/u, "");
const jobToken = process.env.MAIMAID_INTERNAL_JOB_TOKEN?.trim();
const notifyOnly = process.argv.includes("--notify");
const currentDirectory = path.dirname(fileURLToPath(import.meta.url));
const outputDirectory = path.resolve(currentDirectory, "../../static-worker/public");
const requestTimeoutMs = 10 * 60_000;
const assetRequestTimeoutMs = 60_000;
const assetConcurrency = 12;

if (!apiBaseUrl) throw new Error("MAIMAID_API_URL is required.");
if (!staticAssetsBaseUrl) throw new Error("MAIMAID_STATIC_ASSETS_URL is required.");
if (!jobToken) throw new Error("MAIMAID_INTERNAL_JOB_TOKEN is required.");

const callApi = async <T>(pathName: string, init?: { method?: string; body?: unknown }): Promise<T> => {
	const method = init?.method ?? "GET";
	const response = await fetch(`${apiBaseUrl}${pathName}`, {
		method,
		headers: {
			authorization: `Bearer ${jobToken}`,
			...(init?.body === undefined ? {} : { "content-type": "application/json" }),
		},
		...(init?.body === undefined ? {} : { body: JSON.stringify(init.body) }),
		signal: AbortSignal.timeout(requestTimeoutMs),
	});
	if (!response.ok) {
		const detail = (await response.text().catch(() => "")).slice(0, 2000);
		throw new Error(`${method} ${pathName} failed: HTTP ${response.status} ${detail}`);
	}
	return (await response.json()) as T;
};

const mapWithConcurrency = async <T>(items: T[], limit: number, operation: (item: T) => Promise<void>) => {
	let cursor = 0;
	const worker = async () => {
		while (cursor < items.length) {
			const index = cursor;
			cursor += 1;
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
			if (attempt < attempts) await new Promise((resolve) => setTimeout(resolve, attempt * 500));
		}
	}
	throw lastError;
};

const downloadAsset = async (candidate: StaticAssetCandidate) => {
	const directory = candidate.kind === "cover" ? "covers" : "lxns-icons";
	const destination = path.join(outputDirectory, directory, candidate.name);
	await retry(async () => {
		const response = await fetch(candidate.sourceUrl, {
			headers: { accept: "image/*", "user-agent": "maimaid-static-bundle-builder" },
			signal: AbortSignal.timeout(assetRequestTimeoutMs),
		});
		if (!response.ok) throw new Error(`HTTP ${response.status} ${candidate.sourceUrl}`);
		const contentType = response.headers.get("content-type") ?? "";
		if (!contentType.toLocaleLowerCase().startsWith("image/")) {
			throw new Error(`Unexpected content type ${contentType || "unknown"}: ${candidate.sourceUrl}`);
		}
		const body = Buffer.from(await response.arrayBuffer());
		if (body.byteLength === 0) throw new Error(`Empty image: ${candidate.sourceUrl}`);
		await writeFile(destination, body);
	});
};

const writeStaticTree = async () => {
	const { sources } = await callApi<{ sources: StaticSourceTarget[] }>("/internal/jobs/static-bundle/sources");
	console.log(`[build] ${sources.length} enabled source(s): ${sources.map((source) => source.category).join(", ")}`);
	const composed = await composeBundlePayload(sources, async (input) => {
		const mapping = serializeSongIdMapping(buildSongIdMapping(input.dataJson, input.songidJson));
		console.log(
			`[build] song-id mapping: ${Object.keys(mapping.byTitleAndType).length} title+type, ` +
				`${Object.keys(mapping.byTitle).length} title`,
		);
		return callApi<{ payload: unknown; meta: Record<string, unknown> }>("/internal/jobs/static-bundle/self-chart-fit", {
			method: "POST",
			body: mapping satisfies SerializedChartFitSongIdMapping,
		});
	});

	const createdAt = new Date().toISOString();
	const version = `bundle-${Date.parse(createdAt)}`;
	const bundlePath = `/bundles/${composed.md5}.json`;
	const manifest: StaticManifest = {
		schemaVersion: 1,
		version,
		md5: composed.md5,
		createdAt,
		bundle: bundlePath,
		assets: {
			coverBaseUrl: `${staticAssetsBaseUrl}/cdn-cgi/image/f=auto/covers/`,
			presetAvatarBaseUrl: `${staticAssetsBaseUrl}/cdn-cgi/image/f=auto/lxns-icons/`,
			coverFallbackBaseUrl: `${staticAssetsBaseUrl}/covers/`,
			presetAvatarFallbackBaseUrl: `${staticAssetsBaseUrl}/lxns-icons/`,
		},
	};
	const bundle = { version, md5: composed.md5, createdAt, payload: composed.payload, sourceMeta: composed.sourceMeta };

	await rm(outputDirectory, { recursive: true, force: true });
	await Promise.all([
		mkdir(path.join(outputDirectory, "bundles"), { recursive: true }),
		mkdir(path.join(outputDirectory, "covers"), { recursive: true }),
		mkdir(path.join(outputDirectory, "lxns-icons"), { recursive: true }),
	]);
	await Promise.all([
		writeFile(path.join(outputDirectory, "manifest.json"), JSON.stringify(manifest)),
		writeFile(path.join(outputDirectory, bundlePath.slice(1)), JSON.stringify(bundle)),
		writeFile(
			path.join(outputDirectory, "_headers"),
			"/manifest.json\n  Cache-Control: public, max-age=60, must-revalidate\n/bundles/*\n  Cache-Control: public, max-age=31536000, immutable\n/covers/*\n  Cache-Control: public, max-age=31536000, immutable\n/lxns-icons/*\n  Cache-Control: public, max-age=31536000, immutable\n",
		),
	]);

	const candidates = collectStaticAssetCandidates(composed.payload);
	let completed = 0;
	console.log(`[assets] downloading ${candidates.length} object(s)`);
	await mapWithConcurrency(candidates, assetConcurrency, async (candidate) => {
		await downloadAsset(candidate);
		completed += 1;
		if (completed % 100 === 0) console.log(`[assets] downloaded ${completed}/${candidates.length}`);
	});
	console.log(`[build] wrote ${outputDirectory}, md5=${composed.md5}`);
};

const notifyPublished = async () => {
	const manifest = JSON.parse(await readFile(path.join(outputDirectory, "manifest.json"), "utf8")) as StaticManifest;
	const result = await callApi<{ created: boolean }>("/internal/jobs/static-bundle/generated", {
		method: "POST",
		body: {
			version: manifest.version,
			md5: manifest.md5,
			createdAt: manifest.createdAt,
			manifestUrl: `${staticAssetsBaseUrl}/manifest.json`,
			bundleUrl: new URL(manifest.bundle, `${staticAssetsBaseUrl}/`).toString(),
		},
	});
	console.log(`[publish] API recorded ${manifest.version} (${result.created ? "created" : "existing"})`);
};

(notifyOnly ? notifyPublished() : writeStaticTree()).catch((error: unknown) => {
	console.error("[static-bundle] failed:", error instanceof Error ? error.message : error);
	process.exitCode = 1;
});
