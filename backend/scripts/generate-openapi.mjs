import { mkdir, writeFile } from "node:fs/promises";
import path from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";

process.env.NODE_ENV = "development";
process.env.DATABASE_URL ??= "postgresql://postgres:postgres@localhost:5432/maimaid";
process.env.JWT_ACCESS_SECRET ??= "openapi-build-secret-token";
process.env.JWT_ISSUER ??= "maimaid-backend";
process.env.JWT_AUDIENCE ??= "maimaid-clients";
process.env.OPAQUE_SERVER_SETUP ??= "openapi-build-opaque-server-setup";

const scriptPath = fileURLToPath(import.meta.url);
const scriptDirectory = path.dirname(scriptPath);
const distDirectory = path.resolve(scriptDirectory, "../dist");
const appModulePath = path.join(distDirectory, "app.js");
const appModuleUrl = pathToFileURL(appModulePath).href;
const envModuleUrl = pathToFileURL(path.join(distDirectory, "node-env.js")).href;
const openApiModuleUrl = pathToFileURL(path.join(distDirectory, "openapi.js")).href;

const { createApp } = await import(appModuleUrl);
const { getEnv } = await import(envModuleUrl);
const { buildOpenApiDocument } = await import(openApiModuleUrl);
const app = createApp();
const payload = `${JSON.stringify(buildOpenApiDocument(app, getEnv()), null, 2)}\n`;
await mkdir(distDirectory, { recursive: true });
const outputPath = path.join(distDirectory, "openapi.prebuilt.json");
await writeFile(outputPath, payload, "utf8");

console.log(`[openapi] prebuilt document generated at ${outputPath}`);
