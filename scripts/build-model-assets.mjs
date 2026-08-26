import { createHash } from "node:crypto";
import { cpSync, existsSync, mkdtempSync, mkdirSync, readFileSync, readdirSync, rmSync, statSync, utimesSync, writeFileSync } from "node:fs";
import os from "node:os";
import path from "node:path";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const outputDirectory = path.join(repositoryRoot, "models-worker", "public");
const stagingDirectory = mkdtempSync(path.join(os.tmpdir(), "maimaid-models-"));

const modelSpecs = [
	{ filename: "maimaidetector-v12n.onnx", source: "model-assets/android/vision/maimaidetector-v12n.onnx" },
	{ filename: "maimaidistinguish-v12n.onnx", source: "model-assets/android/vision/maimaidistinguish-v12n.onnx" },
	{ filename: "maimaid-v141n.onnx", source: "model-assets/android/vision/maimaid-v141n.onnx" },
	{ filename: "ppocr-v6-small-rec.onnx", source: "model-assets/android/ocr/ppocr-v6-small-rec.onnx" },
	{ filename: "ppocr-v6-small-chars.json", source: "model-assets/android/ocr/ppocr-v6-small-chars.json" },
	{
		filename: "maimaid-v141n.mlpackage.zip",
		source: "model-assets/ios/maimaid v1.41n.mlpackage",
		packageRoot: "maimaid-v141n.mlpackage",
	},
	{
		filename: "maimaidetector-v12n.mlpackage.zip",
		source: "model-assets/ios/maimaidetector v1.2n.mlpackage",
		packageRoot: "maimaidetector-v12n.mlpackage",
	},
	{
		filename: "maimaidistinguish-v12.mlpackage.zip",
		source: "model-assets/ios/maimaidistinguish v1.2.mlpackage",
		packageRoot: "maimaidistinguish-v12.mlpackage",
	},
];

const hashFile = (filePath) => {
	const digest = createHash("sha256").update(readFileSync(filePath)).digest("hex");
	return { sha256: digest, size: statSync(filePath).size };
};

const normalizePackageTimestamps = (directory) => {
	const epoch = new Date("1980-01-01T00:00:00.000Z");
	for (const entry of readdirSync(directory, { withFileTypes: true }).sort((left, right) => left.name.localeCompare(right.name))) {
		const entryPath = path.join(directory, entry.name);
		if (entry.isDirectory()) normalizePackageTimestamps(entryPath);
		utimesSync(entryPath, epoch, epoch);
	}
	utimesSync(directory, epoch, epoch);
};

const buildModel = (spec) => {
	const sourcePath = path.join(repositoryRoot, spec.source);
	if (!existsSync(sourcePath)) {
		throw new Error(`Model source is missing: ${spec.source}`);
	}
	const destinationPath = path.join(outputDirectory, spec.filename);
	if (spec.packageRoot) {
		const packageStage = path.join(stagingDirectory, spec.packageRoot);
		cpSync(sourcePath, packageStage, { recursive: true });
		normalizePackageTimestamps(packageStage);
		execFileSync("zip", ["-X", "-0", "-qry", destinationPath, spec.packageRoot], {
			cwd: stagingDirectory,
			env: { ...process.env, TZ: "UTC" },
		});
	} else {
		cpSync(sourcePath, destinationPath);
	}
	return { filename: spec.filename, ...hashFile(destinationPath) };
};

try {
	rmSync(outputDirectory, { recursive: true, force: true });
	mkdirSync(outputDirectory, { recursive: true });
	const entries = modelSpecs.map(buildModel);
	writeFileSync(path.join(outputDirectory, "manifest.json"), `${JSON.stringify(entries)}\n`);
	writeFileSync(
		path.join(outputDirectory, "_headers"),
		"/manifest.json\n  Cache-Control: public, max-age=60, must-revalidate\n/*.onnx\n  Cache-Control: public, max-age=31536000, immutable\n/ppocr-v6-small-chars.json\n  Cache-Control: public, max-age=31536000, immutable\n/*.zip\n  Cache-Control: public, max-age=31536000, immutable\n",
	);
	console.log(JSON.stringify(entries, null, 2));
} finally {
	rmSync(stagingDirectory, { recursive: true, force: true });
}
