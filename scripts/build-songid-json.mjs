#!/usr/bin/env node

import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { ADX_ATTRIBUTION, fetchJSON, isRecord, writeJSONAtomically } from "./json-build-utils.mjs";

const SOURCE_URL = "https://adxdls.saop.cc/charts/search-index.json";
const repositoryRoot = fileURLToPath(new URL("..", import.meta.url));
const outputPath = resolve(process.argv[2] ?? repositoryRoot, process.argv[2] ? "" : "songid.json");

const source = await fetchJSON(SOURCE_URL);
if (!Array.isArray(source)) {
	throw new TypeError("ADX search index must be an array");
}

const seenIds = new Set();
const songs = source.map((item, index) => {
	if (!isRecord(item)) {
		throw new TypeError(`Search index entry ${index} must be an object`);
	}

	const id = typeof item.slug === "string" && /^\d+$/u.test(item.slug) ? Number(item.slug) : NaN;
	const name = typeof item.title === "string" ? item.title.trim() : "";
	if (!Number.isSafeInteger(id) || id <= 0) {
		throw new TypeError(`Search index entry ${index} has an invalid slug`);
	}
	if (!name) {
		throw new TypeError(`Search index entry ${index} has an empty title`);
	}
	if (seenIds.has(id)) {
		throw new Error(`Duplicate song ID ${id}`);
	}

	seenIds.add(id);
	return { id, name };
});

songs.sort((left, right) => left.id - right.id);
await writeJSONAtomically(outputPath, songs);

console.log(`Wrote ${songs.length} songs to ${outputPath}`);
console.log(`Source: ${ADX_ATTRIBUTION}`);
