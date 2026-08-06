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
 * paths. Only two things reach the server:
 *   - the song-id mapping (a few hundred KB), so it can aggregate `best_scores`
 *   - the finished bundle, to store and activate
 *
 * Usage:
 *   MAIMAID_API_URL=https://api.example.com \
 *   MAIMAID_INTERNAL_JOB_TOKEN=... \
 *   pnpm run static-bundle:build [-- --force]
 */
import {
	buildSongIdMapping,
	serializeSongIdMapping,
	type SerializedChartFitSongIdMapping,
} from "../src/services/chart-fit.service.js";
import { composeBundlePayload, type StaticSourceTarget } from "../src/services/static-bundle.utils.js";
import "reflect-metadata";

const apiBaseUrl = process.env.MAIMAID_API_URL?.trim().replace(/\/+$/u, "");
const jobToken = process.env.MAIMAID_INTERNAL_JOB_TOKEN?.trim();
const force = process.argv.includes("--force");

if (!apiBaseUrl) {
	throw new Error("MAIMAID_API_URL is required.");
}
if (!jobToken) {
	throw new Error("MAIMAID_INTERNAL_JOB_TOKEN is required.");
}

// The publish request carries the whole bundle, so it can take a while to upload
// and for the server to write plus apply the catalog. Node's default is no
// timeout at all, which would hang a stuck build until the CI job limit.
const REQUEST_TIMEOUT_MS = 10 * 60_000;

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

const main = async () => {
	console.log(`[build] target ${apiBaseUrl}${force ? " (force)" : ""}`);

	const { sources } = await callApi<{ sources: StaticSourceTarget[] }>("/internal/jobs/static-bundle/sources");
	console.log(`[build] ${sources.length} enabled source(s): ${sources.map((source) => source.category).join(", ")}`);

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

	const result = await callApi<{
		created: boolean;
		bundle: { version: string; md5: string; createdAt: string };
	}>("/internal/jobs/static-bundle/publish", {
		method: "POST",
		body: {
			payload: composed.payload,
			sourceMeta: composed.sourceMeta,
			md5: composed.md5,
			force,
		},
	});

	if (result.created) {
		console.log(`[build] published ${result.bundle.version} (md5=${result.bundle.md5})`);
		return;
	}
	console.log(`[build] unchanged; kept ${result.bundle.version} (md5=${result.bundle.md5})`);
};

main().catch((error: unknown) => {
	console.error("[build] failed:", error instanceof Error ? error.message : error);
	process.exitCode = 1;
});
