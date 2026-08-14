#!/usr/bin/env node

import { resolve } from "node:path";
import { fileURLToPath } from "node:url";

import { ADX_ATTRIBUTION, fetchJSON, isRecord, writeJSONAtomically } from "./json-build-utils.mjs";

const SOURCE_URL = "https://raw.githubusercontent.com/AdingApkgg/adx-dl/main/data/catalog/index.json";
const repositoryRoot = fileURLToPath(new URL("..", import.meta.url));
const outputPath = resolve(process.argv[2] ?? repositoryRoot, process.argv[2] ? "" : "utage_chart_stats.json");

function readCount(notes, key, entryId) {
	const value = notes[key];
	if (!Number.isSafeInteger(value) || value < 0) {
		throw new TypeError(`Utage chart ${entryId} has an invalid ${key} count`);
	}
	return value;
}

const source = await fetchJSON(SOURCE_URL);
if (!isRecord(source) || !Array.isArray(source.entries)) {
	throw new TypeError("ADX catalog must contain an entries array");
}

const seenIds = new Set();
const charts = [];

for (const [index, entry] of source.entries.entries()) {
	if (!isRecord(entry)) {
		throw new TypeError(`Catalog entry ${index} must be an object`);
	}

	const id = typeof entry.short_id === "string" && /^\d+$/u.test(entry.short_id) ? Number(entry.short_id) : NaN;
	if (!Number.isSafeInteger(id) || id < 100000) {
		continue;
	}

	const title = typeof entry.title === "string" ? entry.title.trim() : "";
	if (!title) {
		throw new TypeError(`Utage chart ${id} has an empty title`);
	}
	if (!Array.isArray(entry.difficulties)) {
		throw new TypeError(`Utage chart ${id} has no difficulties array`);
	}

	const utageDifficulties = entry.difficulties.filter((difficulty) => isRecord(difficulty) && difficulty.name === "Utage");
	if (utageDifficulties.length !== 1) {
		throw new Error(`Utage chart ${id} must have exactly one Utage difficulty`);
	}

	const notes = utageDifficulties[0].notes;
	if (!isRecord(notes)) {
		throw new TypeError(`Utage chart ${id} has no notes object`);
	}

	const tap = readCount(notes, "tap", id);
	const hold = readCount(notes, "hold", id);
	const slide = readCount(notes, "slide", id);
	const touch = readCount(notes, "touch", id) + readCount(notes, "touch_hold", id);
	const breakCount = readCount(notes, "break", id);
	const total = readCount(notes, "total", id);
	const calculatedTotal = tap + hold + slide + touch + breakCount;
	if (total !== calculatedTotal) {
		throw new Error(`Utage chart ${id} total ${total} does not match note type sum ${calculatedTotal}`);
	}
	if (seenIds.has(id)) {
		throw new Error(`Duplicate Utage chart ID ${id}`);
	}

	seenIds.add(id);
	charts.push({
		id,
		title,
		notes: total,
		noteTypes: {
			tap,
			hold,
			slide,
			touch,
			break: breakCount,
		},
	});
}

charts.sort((left, right) => left.id - right.id);
await writeJSONAtomically(outputPath, charts);

console.log(`Wrote ${charts.length} Utage charts to ${outputPath}`);
console.log(`Source: ${ADX_ATTRIBUTION}`);
