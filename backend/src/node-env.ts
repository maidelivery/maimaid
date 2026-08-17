import { parseEnv, type Env } from "./env.js";
import { loadBackendEnvFiles } from "./lib/env-files.js";

let envCache: Env | null = null;

export const getEnv = (): Env => {
	if (envCache) {
		return envCache;
	}

	loadBackendEnvFiles();
	envCache = parseEnv(process.env);
	return envCache;
};
