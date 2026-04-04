import { cpSync, existsSync, mkdirSync } from "node:fs";
import { dirname, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const currentDirectory = dirname(fileURLToPath(import.meta.url));
const sourcePath = resolve(currentDirectory, "../../../maimaid/data.json");
const targetPath = resolve(currentDirectory, "../public/song-catalog.json");
const targetDirectory = dirname(targetPath);

if (!existsSync(sourcePath)) {
  throw new Error(`Song catalog source not found: ${sourcePath}`);
}

mkdirSync(targetDirectory, { recursive: true });
cpSync(sourcePath, targetPath);
console.log(`Copied song catalog to ${targetPath}`);
