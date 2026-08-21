import { inject, injectable } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { CatalogService } from "./catalog.service.js";
import {
	assertStaticBundleMd5,
	assertStaticBundleVersion,
	normalizeSourceCategory,
	parseStaticBundleArtifact,
	toRecord,
	type StaticSourceTarget,
} from "./static-bundle.utils.js";

const STATIC_SOURCE_DEFAULTS: Array<{ category: string; activeUrl: string; fallbackUrls: string[] }> = [
	{
		category: "data_json",
		activeUrl: "https://raw.githubusercontent.com/gekichumai/dxrating/refs/heads/main/packages/dxdata/dxdata.json",
		fallbackUrls: [],
	},
	{ category: "songid_json", activeUrl: "https://static.shikoch.in/songid.json", fallbackUrls: [] },
	{ category: "utage_note_json", activeUrl: "https://static.shikoch.in/utage_chart_stats.json", fallbackUrls: [] },
	{ category: "lxns_aliases", activeUrl: "https://maimai.lxns.net/api/v0/maimai/alias/list", fallbackUrls: [] },
	{ category: "lxns_song_list", activeUrl: "https://maimai.lxns.net/api/v0/maimai/song/list", fallbackUrls: [] },
	{ category: "lxns_icon_list", activeUrl: "https://maimai.lxns.net/api/v0/maimai/icon/list", fallbackUrls: [] },
	{
		category: "chart_fit",
		activeUrl: "https://www.diving-fish.com/api/maimaidxprober/chart_stats",
		fallbackUrls: [],
	},
	{ category: "dan_info", activeUrl: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/gallery.yaml", fallbackUrls: [] },
];
const LEGACY_DATA_JSON_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json";
const PUBLICATION_RETENTION = 30;

export type RecordStaticBundleGenerationInput = {
	version: string;
	md5: string;
	createdAt: string;
	manifestUrl: string;
	bundleUrl: string;
};

type StaticBundleManifest = {
	schemaVersion: 1;
	version: string;
	md5: string;
	createdAt: string;
	bundle: string;
};

const parseStaticBundleManifest = (value: unknown): StaticBundleManifest => {
	if (typeof value !== "object" || value === null) {
		throw new AppError(502, "static_bundle_manifest_invalid", "Published static manifest is invalid.");
	}
	const manifest = value as Record<string, unknown>;
	if (
		manifest.schemaVersion !== 1 ||
		typeof manifest.version !== "string" ||
		typeof manifest.md5 !== "string" ||
		typeof manifest.createdAt !== "string" ||
		typeof manifest.bundle !== "string"
	) {
		throw new AppError(502, "static_bundle_manifest_invalid", "Published static manifest is invalid.");
	}
	return manifest as StaticBundleManifest;
};

@injectable()
export class StaticBundleService {
	constructor(
		@inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
		@inject(CatalogService) private readonly catalogService: CatalogService,
	) {}

	private toNullableJson(value: unknown): Prisma.InputJsonValue | Prisma.NullableJsonNullValueInput {
		return value === null ? Prisma.JsonNull : (JSON.parse(JSON.stringify(value)) as Prisma.InputJsonValue);
	}

	private sanitizeFallbackUrls(category: string, fallbackUrls: string[]) {
		const deduplicated = Array.from(new Set(fallbackUrls.map((item) => item.trim()).filter(Boolean)));
		if (category === "chart_fit" && deduplicated.length > 1) {
			throw new AppError(400, "static_source_invalid_fallback_urls", "chart_fit supports at most one extra URL.");
		}
		return deduplicated;
	}

	async ensureDefaultSources() {
		const [legacyChartFit, modernChartFit] = await Promise.all([
			this.prisma.staticSource.findUnique({ where: { category: "df_chart_fit" }, select: { id: true } }),
			this.prisma.staticSource.findUnique({ where: { category: "chart_fit" }, select: { id: true } }),
		]);
		if (legacyChartFit && modernChartFit) {
			await this.prisma.staticSource.delete({ where: { id: legacyChartFit.id } });
		} else if (legacyChartFit) {
			await this.prisma.staticSource.update({ where: { id: legacyChartFit.id }, data: { category: "chart_fit" } });
		}
		for (const item of STATIC_SOURCE_DEFAULTS) {
			if (item.category === "data_json") {
				await this.prisma.staticSource.updateMany({
					where: { category: item.category, activeUrl: LEGACY_DATA_JSON_URL },
					data: { activeUrl: item.activeUrl, fallbackUrls: item.fallbackUrls },
				});
			}
			await this.prisma.staticSource.upsert({
				where: { category: item.category },
				update: {},
				create: { ...item, enabled: true },
			});
		}
	}

	async listSources() {
		await this.ensureDefaultSources();
		return this.prisma.staticSource.findMany({ orderBy: { category: "asc" } });
	}

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

	async createSource(input: {
		category: string;
		activeUrl: string;
		fallbackUrls?: string[];
		enabled?: boolean;
		metadata?: Record<string, unknown> | null;
	}) {
		const category = normalizeSourceCategory(input.category.trim());
		return this.prisma.staticSource.create({
			data: {
				category,
				activeUrl: input.activeUrl.trim(),
				fallbackUrls: this.sanitizeFallbackUrls(category, input.fallbackUrls ?? []),
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
		const existing = await this.prisma.staticSource.findUnique({ where: { id: sourceId }, select: { category: true } });
		if (!existing) throw new AppError(404, "static_source_not_found", "Static source not found.");
		const category = normalizeSourceCategory(existing.category);
		const data: Prisma.StaticSourceUpdateInput = {};
		if (input.activeUrl !== undefined) data.activeUrl = input.activeUrl.trim();
		if (input.fallbackUrls !== undefined) {
			data.fallbackUrls = this.sanitizeFallbackUrls(category, input.fallbackUrls);
		}
		if (input.enabled !== undefined) data.enabled = input.enabled;
		if (input.metadata !== undefined) data.metadataJson = this.toNullableJson(input.metadata);
		return this.prisma.staticSource.update({ where: { id: sourceId }, data });
	}

	async recordGeneration(input: RecordStaticBundleGenerationInput) {
		assertStaticBundleVersion(input.version);
		assertStaticBundleMd5(input.md5);
		const createdAt = new Date(input.createdAt);
		if (!Number.isFinite(createdAt.getTime())) {
			throw new AppError(400, "static_bundle_invalid_created_at", "Bundle createdAt is invalid.");
		}

		const fetchOptions = {
			headers: { accept: "application/json", "user-agent": "maimaid-backend" },
			signal: AbortSignal.timeout(2 * 60_000),
		};
		const manifestResponse = await fetch(input.manifestUrl, fetchOptions);
		if (!manifestResponse.ok) {
			throw new AppError(502, "static_bundle_manifest_fetch_failed", "Published static manifest could not be fetched.", {
				status: manifestResponse.status,
			});
		}
		let manifestValue: unknown;
		try {
			manifestValue = await manifestResponse.json();
		} catch {
			throw new AppError(502, "static_bundle_manifest_invalid", "Published static manifest is invalid.");
		}
		const manifest = parseStaticBundleManifest(manifestValue);
		let resolvedBundleUrl: string;
		try {
			resolvedBundleUrl = new URL(manifest.bundle, input.manifestUrl).toString();
		} catch {
			throw new AppError(502, "static_bundle_manifest_invalid", "Published static manifest has an invalid bundle URL.");
		}
		if (
			manifest.version !== input.version ||
			manifest.md5 !== input.md5 ||
			manifest.createdAt !== createdAt.toISOString() ||
			resolvedBundleUrl !== new URL(input.bundleUrl).toString()
		) {
			throw new AppError(502, "static_bundle_manifest_mismatch", "Published static manifest differs from the notification.");
		}

		const bundleResponse = await fetch(resolvedBundleUrl, fetchOptions);
		if (!bundleResponse.ok) {
			throw new AppError(502, "static_bundle_fetch_failed", "Published static bundle could not be fetched.", {
				status: bundleResponse.status,
			});
		}
		const artifact = parseStaticBundleArtifact(await bundleResponse.text(), input);
		if (artifact.createdAt !== createdAt.toISOString()) {
			throw new AppError(502, "static_bundle_artifact_mismatch", "Published static bundle differs from the notification.");
		}
		const resources = toRecord(artifact.payload.resources);
		const dataJson = resources?.data_json;
		if (dataJson === undefined) {
			throw new AppError(502, "static_bundle_missing_catalog_data", "Bundle payload is missing data_json.");
		}

		const catalogResult = await this.catalogService.applyCatalogData(dataJson, {
			source: `static_bundle:${input.version}`,
			sourceUrl: resolvedBundleUrl,
			applyWhenUnchanged: true,
			metadata: { bundleVersion: input.version, bundleMd5: input.md5, manifestUrl: input.manifestUrl },
		});
		const existing = await this.prisma.staticBundle.findUnique({ where: { version: input.version } });
		const bundle = await this.prisma.$transaction(async (tx) => {
			await tx.staticBundle.updateMany({ where: { active: true }, data: { active: false } });
			return tx.staticBundle.upsert({
				where: { version: input.version },
				update: {
					md5: input.md5,
					manifestUrl: input.manifestUrl,
					bundleUrl: resolvedBundleUrl,
					sourceMeta: artifact.sourceMeta as Prisma.InputJsonValue,
					active: true,
					activatedAt: new Date(),
				},
				create: {
					version: input.version,
					md5: input.md5,
					manifestUrl: input.manifestUrl,
					bundleUrl: resolvedBundleUrl,
					sourceMeta: artifact.sourceMeta as Prisma.InputJsonValue,
					active: true,
					createdAt,
					activatedAt: new Date(),
				},
			});
		});
		await this.prunePublications();
		return { bundle, created: existing === null, catalogApplied: catalogResult.applied };
	}

	async listBundles(limit = 20) {
		return this.prisma.staticBundle.findMany({
			orderBy: { createdAt: "desc" },
			take: Math.max(1, Math.min(limit, 100)),
		});
	}

	private async prunePublications() {
		const keep = await this.prisma.staticBundle.findMany({
			orderBy: { createdAt: "desc" },
			take: PUBLICATION_RETENTION,
			select: { id: true },
		});
		if (keep.length < PUBLICATION_RETENTION) return;
		await this.prisma.staticBundle.deleteMany({
			where: { active: false, id: { notIn: keep.map((item) => item.id) } },
		});
	}
}
