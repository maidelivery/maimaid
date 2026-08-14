import { randomUUID } from "node:crypto";
import { mkdir, rename, rm, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

export const ADX_ATTRIBUTION = "ADX 谱面资源 — https://adxdls.saop.cc (CC BY 4.0)";

export function isRecord(value) {
	return typeof value === "object" && value !== null && !Array.isArray(value);
}

export async function fetchJSON(url) {
	const response = await fetch(url, {
		headers: { Accept: "application/json" },
	});

	if (!response.ok) {
		throw new Error(`Failed to fetch ${url}: HTTP ${response.status}`);
	}

	try {
		return await response.json();
	} catch (error) {
		throw new Error(`Failed to parse JSON from ${url}`, { cause: error });
	}
}

export async function writeJSONAtomically(outputPath, value) {
	await mkdir(dirname(outputPath), { recursive: true });
	const temporaryPath = `${outputPath}.${process.pid}.${randomUUID()}.tmp`;

	try {
		await writeFile(temporaryPath, `${JSON.stringify(value, null, 2)}\n`, "utf8");
		await rename(temporaryPath, outputPath);
	} catch (error) {
		await rm(temporaryPath, { force: true });
		throw error;
	}
}
