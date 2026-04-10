import "reflect-metadata";
import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { TOKENS } from "./di/tokens.js";
import { getEnv } from "./env.js";
import type { PrismaClient } from "@prisma/client";
import { CatalogService } from "./services/catalog.service.js";
import { StaticBundleService } from "./services/static-bundle.service.js";
import { container } from "tsyringe";
import { getPrismaClient } from "./lib/prisma.js";

const env = getEnv();
const app = createApp();

const prisma = getPrismaClient();
container.register(TOKENS.Env, { useValue: env });
container.register(TOKENS.Prisma, { useValue: prisma });

const toRecord = (value: unknown): Record<string, unknown> | null => {
	return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
};

const bootstrapCatalogIfEmpty = async () => {
	const prisma = container.resolve<PrismaClient>(TOKENS.Prisma);
	const existingSheets = await prisma.sheet.count();
	if (existingSheets > 0) {
		return;
	}

	const activeBundle = await prisma.staticBundle.findFirst({
		where: { active: true },
		orderBy: { createdAt: "desc" },
		select: {
			id: true,
			version: true,
			md5: true,
			payloadJson: true,
			sourceMeta: true,
		},
	});
	if (!activeBundle) {
		console.warn(
			"[bootstrap] catalog is empty and no active static bundle exists. Trigger a bundle build from admin dashboard.",
		);
		return;
	}

	const payload = toRecord(activeBundle.payloadJson);
	const resources = toRecord(payload?.resources);
	const dataJsonResource = resources?.data_json;
	if (!dataJsonResource) {
		console.warn(
			`[bootstrap] catalog is empty and active static bundle ${activeBundle.version} has no data_json resource. Trigger a bundle build from admin dashboard.`,
		);
		return;
	}

	const sourceMeta = toRecord(activeBundle.sourceMeta);
	const dataJsonMeta = toRecord(sourceMeta?.data_json);
	const sourceUrl =
		typeof dataJsonMeta?.url === "string" && dataJsonMeta.url.trim()
			? dataJsonMeta.url
			: `static-bundle://${activeBundle.version}/data_json`;

	const catalogService = container.resolve(CatalogService);
	console.log(`[bootstrap] catalog is empty, applying data_json from active static bundle ${activeBundle.version}...`);
	const result = await catalogService.applyCatalogData(dataJsonResource, {
		source: `static_bundle:${activeBundle.version}`,
		sourceUrl,
		applyWhenUnchanged: true,
		metadata: {
			bundleId: activeBundle.id.toString(),
			bundleVersion: activeBundle.version,
			bundleMd5: activeBundle.md5,
			appliedFrom: "server_bootstrap",
		},
	});
	console.log(
		`[bootstrap] catalog apply complete (applied=${result.applied}) snapshot=${result.snapshot.id.toString()} source=static_bundle:${activeBundle.version}`,
	);
};

const bootstrapStaticBundleSchedule = async () => {
	const staticBundleService = container.resolve(StaticBundleService);
	const schedule = await staticBundleService.syncPeriodicBuildSchedule();
	console.log(
		`[bootstrap] static bundle auto-build schedule synced (enabled=${schedule.enabled}, intervalHours=${schedule.intervalHours}, cron="${schedule.cronExpression}")`,
	);
};

const start = async () => {
	try {
		await bootstrapCatalogIfEmpty();
	} catch (error) {
		console.error("[bootstrap] catalog apply failed:", error);
	}

	try {
		await bootstrapStaticBundleSchedule();
	} catch (error) {
		console.error("[bootstrap] static bundle auto-build schedule sync failed:", error);
	}

	serve(
		{
			fetch: app.fetch,
			hostname: env.HOST,
			port: env.PORT,
		},
		(info) => {
			console.log(`maimaid-backend listening on http://${env.HOST}:${info.port}`);
		},
	);
};

void start();
