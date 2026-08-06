import { inject, singleton } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import type { Env } from "../env.js";
import { AppError } from "../lib/errors.js";
import { buildSongIdMapping, ChartFitService } from "./chart-fit.service.js";
import { CatalogService } from "./catalog.service.js";
import {
	composeBundlePayload,
	normalizeSourceCategory,
	toRecord,
	type ComposedBundle,
	type StaticSourceTarget,
} from "./static-bundle.utils.js";

const STATIC_SOURCE_DEFAULTS: Array<{ category: string; activeUrl: string; fallbackUrls: string[] }> = [
	{
		category: "data_json",
		activeUrl: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json",
		fallbackUrls: [],
	},
	{
		category: "songid_json",
		activeUrl: "https://static.shikoch.in/songid.json",
		fallbackUrls: [],
	},
	{
		category: "utage_note_json",
		activeUrl: "https://static.shikoch.in/utage_chart_stats.json",
		fallbackUrls: [],
	},
	{
		category: "lxns_aliases",
		activeUrl: "https://maimai.lxns.net/api/v0/maimai/alias/list",
		fallbackUrls: [],
	},
	{
		category: "chart_fit",
		activeUrl: "https://www.diving-fish.com/api/maimaidxprober/chart_stats",
		fallbackUrls: [],
	},
	{
		category: "dan_info",
		activeUrl: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/gallery.yaml",
		fallbackUrls: [],
	},
];

// Every row carries a full copy of the bundle payload (the catalog alone is
// several MB), so history is the largest avoidable disk consumer here. Clients
// read `/manifest` and then fetch that exact version, so a handful of recent
// bundles must survive: a client that read the manifest just before a rebuild
// still has an in-flight request for the previous version.
const STATIC_BUNDLE_RETENTION = 5;

const STATIC_BUNDLE_SCHEDULE_ROW_ID = 1;
const STATIC_BUNDLE_CRON_JOB_NAME = "maimaid-static-bundle-build-request";
const STATIC_BUNDLE_CRON_DRIVER_EXPRESSION = "* * * * *";
const STATIC_BUNDLE_BUILD_CRON_SQL = "SELECT public.enqueue_static_bundle_build_if_due();";

export type StaticBundlePeriodicBuildSchedule = {
	enabled: boolean;
	intervalHours: number;
	cronExpression: string;
};

/** A composed bundle ready to store. `force` skips the md5 dedupe check. */
export type PublishBundleInput = ComposedBundle & {
	force?: boolean;
};

@singleton()
export class StaticBundleService {
	constructor(
		@inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
		@inject(TOKENS.Env) private readonly env: Env,
		@inject(ChartFitService) private readonly chartFitService: ChartFitService,
		@inject(CatalogService) private readonly catalogService: CatalogService,
	) {}

	private toJsonValue(value: unknown): Prisma.InputJsonValue {
		return JSON.parse(JSON.stringify(value)) as Prisma.InputJsonValue;
	}

	private toNullableJson(value: unknown): Prisma.InputJsonValue | Prisma.NullableJsonNullValueInput {
		if (value === null) {
			return Prisma.JsonNull;
		}
		return this.toJsonValue(value);
	}

	private sanitizeFallbackUrls(category: string, fallbackUrls: string[]) {
		const normalized = fallbackUrls.map((item) => item.trim()).filter((item) => item.length > 0);
		const deduplicated = Array.from(new Set(normalized));

		if (category === "chart_fit" && deduplicated.length > 1) {
			throw new AppError(400, "static_source_invalid_fallback_urls", "chart_fit supports at most one extra URL.");
		}
		return deduplicated;
	}

	async ensureDefaultSources() {
		const legacyChartFit = await this.prisma.staticSource.findUnique({
			where: { category: "df_chart_fit" },
			select: { id: true },
		});
		const modernChartFit = await this.prisma.staticSource.findUnique({
			where: { category: "chart_fit" },
			select: { id: true },
		});

		if (legacyChartFit && modernChartFit) {
			await this.prisma.staticSource.delete({
				where: { id: legacyChartFit.id },
			});
		} else if (legacyChartFit && !modernChartFit) {
			await this.prisma.staticSource.update({
				where: { id: legacyChartFit.id },
				data: { category: "chart_fit" },
			});
		}

		for (const item of STATIC_SOURCE_DEFAULTS) {
			await this.prisma.staticSource.upsert({
				where: {
					category: item.category,
				},
				update: {},
				create: {
					category: item.category,
					activeUrl: item.activeUrl,
					fallbackUrls: item.fallbackUrls,
					enabled: true,
				},
			});
		}
	}

	async listSources() {
		await this.ensureDefaultSources();
		return this.prisma.staticSource.findMany({
			orderBy: { category: "asc" },
		});
	}

	async createSource(input: {
		category: string;
		activeUrl: string;
		fallbackUrls?: string[];
		enabled?: boolean;
		metadata?: Record<string, unknown> | null;
	}) {
		const category = normalizeSourceCategory(input.category.trim());
		const fallbackUrls = this.sanitizeFallbackUrls(category, input.fallbackUrls ?? []);

		return this.prisma.staticSource.create({
			data: {
				category,
				activeUrl: input.activeUrl.trim(),
				fallbackUrls,
				enabled: input.enabled ?? true,
				metadataJson: this.toNullableJson(input.metadata ?? null),
			},
		});
	}

	async updateSource(
		sourceId: string,
		input: Partial<{
			activeUrl: string;
			fallbackUrls: string[];
			enabled: boolean;
			metadata: Record<string, unknown> | null;
		}>,
	) {
		const existing = await this.prisma.staticSource.findUnique({
			where: { id: sourceId },
			select: { category: true },
		});
		if (!existing) {
			throw new AppError(404, "static_source_not_found", "Static source not found.");
		}

		const category = normalizeSourceCategory(existing.category);
		const data: Prisma.StaticSourceUpdateInput = {};
		if (input.activeUrl !== undefined) {
			data.activeUrl = input.activeUrl.trim();
		}
		if (input.fallbackUrls !== undefined) {
			data.fallbackUrls = this.sanitizeFallbackUrls(category, input.fallbackUrls);
		}
		if (input.enabled !== undefined) {
			data.enabled = input.enabled;
		}
		if (input.metadata !== undefined) {
			data.metadataJson = this.toNullableJson(input.metadata);
		}
		return this.prisma.staticSource.update({
			where: { id: sourceId },
			data,
		});
	}

	async getPeriodicBuildSchedule(): Promise<StaticBundlePeriodicBuildSchedule> {
		const config = await this.getOrCreatePeriodicBuildScheduleConfig();
		return this.toPeriodicBuildSchedule(config.enabled, config.intervalHours);
	}

	async updatePeriodicBuildSchedule(input: Partial<{ enabled: boolean; intervalHours: number }>) {
		const current = await this.getOrCreatePeriodicBuildScheduleConfig();
		const enabled = input.enabled ?? current.enabled;
		const intervalHours =
			input.intervalHours !== undefined ? this.normalizeInputIntervalHours(input.intervalHours) : current.intervalHours;

		const now = new Date();
		const shouldResetNextEnqueueAt =
			enabled && (!current.enabled || input.intervalHours !== undefined || current.nextEnqueueAt === null);
		const nextEnqueueAt = this.addHours(now, intervalHours);
		const updateData: Prisma.StaticBundleScheduleConfigUpdateInput = {
			enabled,
			intervalHours,
		};
		if (!enabled) {
			updateData.nextEnqueueAt = null;
		} else if (shouldResetNextEnqueueAt) {
			updateData.nextEnqueueAt = nextEnqueueAt;
		}

		const createData: Prisma.StaticBundleScheduleConfigCreateInput = {
			id: STATIC_BUNDLE_SCHEDULE_ROW_ID,
			enabled,
			intervalHours,
			nextEnqueueAt: enabled ? nextEnqueueAt : null,
		};

		const updated = await this.prisma.staticBundleScheduleConfig.upsert({
			where: { id: STATIC_BUNDLE_SCHEDULE_ROW_ID },
			update: updateData,
			create: createData,
		});

		await this.syncPeriodicBuildCronJob(updated.enabled);
		return this.toPeriodicBuildSchedule(updated.enabled, updated.intervalHours);
	}

	async syncPeriodicBuildSchedule() {
		let config = await this.getOrCreatePeriodicBuildScheduleConfig();
		if (config.enabled && config.nextEnqueueAt === null) {
			config = await this.prisma.staticBundleScheduleConfig.update({
				where: { id: STATIC_BUNDLE_SCHEDULE_ROW_ID },
				data: {
					nextEnqueueAt: this.addHours(new Date(), config.intervalHours),
				},
			});
		}
		await this.syncPeriodicBuildCronJob(config.enabled);
		return this.toPeriodicBuildSchedule(config.enabled, config.intervalHours);
	}

	/**
	 * The enabled sources, category-normalized. Exposed so the GitHub Actions
	 * builder can fetch the upstreams itself instead of making the API do it.
	 */
	async listEnabledSourceTargets(): Promise<StaticSourceTarget[]> {
		await this.ensureDefaultSources();
		const sources = await this.prisma.staticSource.findMany({
			where: { enabled: true },
			orderBy: { category: "asc" },
		});
		return sources.map((source) => ({
			category: normalizeSourceCategory(source.category),
			activeUrl: source.activeUrl,
			fallbackUrls: source.fallbackUrls,
		}));
	}

	/**
	 * Build in-process: compute, then publish. Still used by the admin "build now"
	 * button and by `manifest()` when no bundle exists yet. The scheduled path runs
	 * the compute half in GitHub Actions and calls `publishBundle` over HTTP, so
	 * this is the fallback rather than the normal route.
	 */
	async buildBundle(force = false) {
		const sources = await this.listEnabledSourceTargets();
		const composed = await composeBundlePayload(sources, async (input) =>
			this.chartFitService.refreshSnapshotWithMapping(buildSongIdMapping(input.dataJson, input.songidJson)),
		);
		return this.publishBundle({ ...composed, force });
	}

	/**
	 * Database half of a bundle build: dedupe by md5, insert, activate, prune, then
	 * refresh the Song/Sheet catalog from the bundle's data_json.
	 *
	 * `md5` is taken on trust rather than recomputed. Hashing means a full
	 * key-sorted clone of ~15 MB of JSON, the single largest heap spike in a build,
	 * and not paying it here is the point of moving builds to CI. Both callers hash
	 * with the same `computeBundleMd5`, so there is no second implementation free to
	 * drift. The format is still checked so a malformed value fails loudly instead
	 * of silently poisoning client caching.
	 */
	async publishBundle(input: PublishBundleInput) {
		if (!/^[0-9a-f]{32}$/.test(input.md5)) {
			throw new AppError(400, "static_bundle_invalid_md5", "Bundle md5 must be 32 lowercase hex characters.");
		}

		const resourcesRecord = toRecord(input.payload.resources);
		const dataJsonResource = resourcesRecord?.data_json;
		// Checked before the insert. Previously this threw *after* activating the
		// bundle, leaving an active row whose catalog had never been applied.
		if (dataJsonResource === undefined || dataJsonResource === null) {
			throw new AppError(502, "static_bundle_missing_catalog_data", "Bundle payload is missing data_json.");
		}

		if (!input.force) {
			const existing = await this.prisma.staticBundle.findFirst({
				where: { md5: input.md5 },
				orderBy: { createdAt: "desc" },
			});
			if (existing) {
				return {
					bundle: existing,
					created: false,
				};
			}
		}

		const version = `bundle-${Date.now()}`;
		const bundle = await this.prisma.$transaction(async (tx) => {
			await tx.staticBundle.updateMany({
				where: { active: true },
				data: { active: false },
			});
			return tx.staticBundle.create({
				data: {
					version,
					md5: input.md5,
					payloadJson: this.toJsonValue(input.payload),
					sourceMeta: this.toJsonValue(input.sourceMeta),
					active: true,
					activatedAt: new Date(),
				},
			});
		});

		await this.pruneBundles();

		const dataJsonMeta = toRecord(input.sourceMeta.data_json);
		const dataJsonSourceUrl =
			typeof dataJsonMeta?.url === "string" && dataJsonMeta.url.trim()
				? dataJsonMeta.url
				: `static-bundle://${bundle.version}/data_json`;
		await this.catalogService.applyCatalogData(dataJsonResource, {
			source: `static_bundle:${bundle.version}`,
			sourceUrl: dataJsonSourceUrl,
			applyWhenUnchanged: true,
			metadata: {
				bundleId: bundle.id.toString(),
				bundleVersion: bundle.version,
				bundleMd5: bundle.md5,
			},
		});

		return {
			bundle,
			created: true,
		};
	}

	/**
	 * Drop bundles older than the newest `STATIC_BUNDLE_RETENTION`. Runs after the
	 * insert so a prune failure cannot lose the bundle that was just built. The
	 * active row is excluded explicitly rather than relying on it being newest,
	 * so this stays safe if activation ever stops implying "most recent".
	 */
	private async pruneBundles() {
		const keep = await this.prisma.staticBundle.findMany({
			orderBy: { createdAt: "desc" },
			take: STATIC_BUNDLE_RETENTION,
			select: { id: true },
		});
		if (keep.length < STATIC_BUNDLE_RETENTION) {
			return;
		}
		await this.prisma.staticBundle.deleteMany({
			where: {
				active: false,
				id: { notIn: keep.map((row) => row.id) },
			},
		});
	}

	async listBundles(limit = 20) {
		return this.prisma.staticBundle.findMany({
			orderBy: { createdAt: "desc" },
			take: Math.max(1, Math.min(limit, 100)),
		});
	}

	async manifest() {
		const active = await this.prisma.staticBundle.findFirst({
			where: { active: true },
			orderBy: { createdAt: "desc" },
		});
		if (!active) {
			const result = await this.buildBundle(false);
			return {
				version: result.bundle.version,
				md5: result.bundle.md5,
				createdAt: result.bundle.createdAt,
			};
		}
		return {
			version: active.version,
			md5: active.md5,
			createdAt: active.createdAt,
		};
	}

	async getBundle(version: string) {
		const where =
			version === "latest"
				? {
						active: true,
					}
				: {
						version,
					};
		const bundle = await this.prisma.staticBundle.findFirst({
			where,
			orderBy: { createdAt: "desc" },
		});
		if (!bundle) {
			throw new AppError(404, "static_bundle_not_found", "Static bundle not found.");
		}
		return bundle;
	}

	async listSongIdItems() {
		const activeBundle = await this.prisma.staticBundle.findFirst({
			where: { active: true },
			orderBy: { createdAt: "desc" },
			select: { payloadJson: true },
		});
		if (!activeBundle) {
			return [];
		}

		const payload = toRecord(activeBundle.payloadJson);
		const resources = toRecord(payload?.resources);
		const rows = Array.isArray(resources?.songid_json) ? resources.songid_json : [];
		const items: Array<{ id: number; name: string }> = [];
		for (const rowValue of rows) {
			const row = toRecord(rowValue);
			if (!row) {
				continue;
			}
			const id = Number(row.id);
			const name = typeof row.name === "string" ? row.name.trim() : "";
			if (!Number.isFinite(id) || !name) {
				continue;
			}
			items.push({
				id: Math.trunc(id),
				name,
			});
		}
		return items;
	}

	async enqueuePeriodicBuild() {
		await this.prisma.jobQueue.create({
			data: {
				jobType: "static_bundle_build",
				payload: { intervalHours: this.env.STATIC_SYNC_INTERVAL_HOURS },
				status: "pending",
				scheduledAt: new Date(),
			},
		});
	}

	private async getOrCreatePeriodicBuildScheduleConfig() {
		const defaultIntervalHours = this.normalizeEnvIntervalHours(this.env.STATIC_SYNC_INTERVAL_HOURS);
		return this.prisma.staticBundleScheduleConfig.upsert({
			where: { id: STATIC_BUNDLE_SCHEDULE_ROW_ID },
			update: {},
			create: {
				id: STATIC_BUNDLE_SCHEDULE_ROW_ID,
				enabled: true,
				intervalHours: defaultIntervalHours,
				nextEnqueueAt: this.addHours(new Date(), defaultIntervalHours),
			},
		});
	}

	private toPeriodicBuildSchedule(enabled: boolean, intervalHours: number): StaticBundlePeriodicBuildSchedule {
		return {
			enabled,
			intervalHours,
			cronExpression: this.describeCronExpression(),
		};
	}

	private normalizeEnvIntervalHours(value: number): number {
		if (!Number.isFinite(value)) {
			return 6;
		}
		return Math.max(1, Math.trunc(value));
	}

	private normalizeInputIntervalHours(value: number): number {
		if (!Number.isFinite(value)) {
			throw new AppError(400, "static_bundle_schedule_invalid_interval", "Interval hours must be a number.");
		}
		const normalized = Math.trunc(value);
		if (normalized < 1) {
			throw new AppError(400, "static_bundle_schedule_invalid_interval", "Interval hours must be greater than or equal to 1.");
		}
		return normalized;
	}

	private addHours(base: Date, hours: number) {
		return new Date(base.getTime() + hours * 60 * 60 * 1000);
	}

	private toCronExpression() {
		return STATIC_BUNDLE_CRON_DRIVER_EXPRESSION;
	}

	private describeCronExpression() {
		return `${STATIC_BUNDLE_CRON_DRIVER_EXPRESSION} (precise-hour driver)`;
	}

	private async ensurePgCronAvailable() {
		const rows = await this.prisma.$queryRaw<Array<{ available: boolean }>>`
      SELECT to_regclass('cron.job') IS NOT NULL AS "available";
    `;
		if (!rows[0]?.available) {
			throw new AppError(
				500,
				"static_bundle_schedule_unavailable",
				"pg_cron is unavailable; cannot configure automatic static bundle build.",
			);
		}
	}

	private async syncPeriodicBuildCronJob(enabled: boolean) {
		await this.ensurePgCronAvailable();
		const cronExpression = this.toCronExpression();
		try {
			await this.prisma.$executeRawUnsafe(`
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM cron.job WHERE jobname = '${STATIC_BUNDLE_CRON_JOB_NAME}') THEN
    PERFORM cron.unschedule((SELECT jobid FROM cron.job WHERE jobname = '${STATIC_BUNDLE_CRON_JOB_NAME}' LIMIT 1));
  END IF;
END;
$$;
      `);

			if (!enabled) {
				return;
			}

			await this.prisma.$executeRawUnsafe(`
SELECT cron.schedule(
  '${STATIC_BUNDLE_CRON_JOB_NAME}',
  '${cronExpression}',
  $$${STATIC_BUNDLE_BUILD_CRON_SQL}$$
);
      `);
		} catch (error) {
			throw new AppError(500, "static_bundle_schedule_sync_failed", "Failed to sync static bundle periodic build schedule.", {
				error: error instanceof Error ? error.message : "unknown_error",
			});
		}
	}
}
