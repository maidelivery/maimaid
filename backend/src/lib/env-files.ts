import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

let envFilesLoaded = false;

const projectRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "../..");

const parseEnvValue = (rawValue: string): string => {
	const trimmed = rawValue.trim();
	if (
		(trimmed.startsWith('"') && trimmed.endsWith('"')) ||
		(trimmed.startsWith("'") && trimmed.endsWith("'"))
	) {
		return trimmed.slice(1, -1);
	}
	return trimmed;
};

const loadEnvFile = (filePath: string, protectedKeys: Set<string>) => {
	if (!existsSync(filePath)) {
		return;
	}

	const contents = readFileSync(filePath, "utf8");
	for (const line of contents.split(/\r?\n/u)) {
		const trimmed = line.trim();
		if (!trimmed || trimmed.startsWith("#")) {
			continue;
		}

		const separatorIndex = line.indexOf("=");
		if (separatorIndex <= 0) {
			continue;
		}

		const key = line.slice(0, separatorIndex).trim();
		if (!key || protectedKeys.has(key)) {
			continue;
		}

		const rawValue = line.slice(separatorIndex + 1);
		process.env[key] = parseEnvValue(rawValue);
	}
};

export const loadBackendEnvFiles = () => {
	if (envFilesLoaded) {
		return;
	}

	const protectedKeys = new Set(Object.keys(process.env));
	loadEnvFile(path.join(projectRoot, ".env.docker"), protectedKeys);
	loadEnvFile(path.join(projectRoot, ".env.docker.local"), protectedKeys);
	envFilesLoaded = true;
};
