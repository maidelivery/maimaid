import { createHash } from "node:crypto";
import { inject, injectable } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { parse as parseYaml } from "yaml";
import { TOKENS } from "../di/tokens.js";
import type { Env } from "../env.js";
import { AppError } from "../lib/errors.js";

const STATIC_SOURCE_DEFAULTS: Array<{ category: string; activeUrl: string; fallbackUrls: string[] }> = [
  {
    category: "data_json",
    activeUrl: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json",
    fallbackUrls: []
  },
  {
    category: "songid_json",
    activeUrl: "https://maimaid.shikoch.in/songid.json",
    fallbackUrls: []
  },
  {
    category: "utage_note_json",
    activeUrl: "https://maimaid.shikoch.in/utage_chart_stats.json",
    fallbackUrls: []
  },
  {
    category: "lxns_aliases",
    activeUrl: "https://maimai.lxns.net/api/v0/maimai/alias/list",
    fallbackUrls: []
  },
  {
    category: "df_chart_fit",
    activeUrl: "https://www.diving-fish.com/api/maimaidxprober/chart_stats",
    fallbackUrls: []
  },
  {
    category: "dan_info",
    activeUrl: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/gallery.yaml",
    fallbackUrls: []
  }
];

const STATIC_BUNDLE_SCHEDULE_ROW_ID = 1;
const STATIC_BUNDLE_CRON_JOB_NAME = "maimaid-static-bundle-build-request";
const STATIC_BUNDLE_BUILD_CRON_SQL = "SELECT public.enqueue_job('static_bundle_build', '{}'::jsonb);";

export type StaticBundlePeriodicBuildSchedule = {
  enabled: boolean;
  intervalHours: number;
  cronExpression: string;
};

@injectable()
export class StaticBundleService {
  constructor(
    @inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
    @inject(TOKENS.Env) private readonly env: Env
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

  async ensureDefaultSources() {
    for (const item of STATIC_SOURCE_DEFAULTS) {
      await this.prisma.staticSource.upsert({
        where: {
          category: item.category
        },
        update: {},
        create: {
          category: item.category,
          activeUrl: item.activeUrl,
          fallbackUrls: item.fallbackUrls,
          enabled: true
        }
      });
    }
  }

  async listSources() {
    await this.ensureDefaultSources();
    return this.prisma.staticSource.findMany({
      orderBy: { category: "asc" }
    });
  }

  async createSource(input: {
    category: string;
    activeUrl: string;
    fallbackUrls?: string[];
    enabled?: boolean;
    metadata?: Record<string, unknown> | null;
  }) {
    return this.prisma.staticSource.create({
      data: {
        category: input.category.trim(),
        activeUrl: input.activeUrl.trim(),
        fallbackUrls: input.fallbackUrls ?? [],
        enabled: input.enabled ?? true,
        metadataJson: this.toNullableJson(input.metadata ?? null)
      }
    });
  }

  async updateSource(
    sourceId: string,
    input: Partial<{
      activeUrl: string;
      fallbackUrls: string[];
      enabled: boolean;
      metadata: Record<string, unknown> | null;
    }>
  ) {
    const data: Prisma.StaticSourceUpdateInput = {};
    if (input.activeUrl !== undefined) {
      data.activeUrl = input.activeUrl.trim();
    }
    if (input.fallbackUrls !== undefined) {
      data.fallbackUrls = input.fallbackUrls;
    }
    if (input.enabled !== undefined) {
      data.enabled = input.enabled;
    }
    if (input.metadata !== undefined) {
      data.metadataJson = this.toNullableJson(input.metadata);
    }
    return this.prisma.staticSource.update({
      where: { id: sourceId },
      data
    });
  }

  async getPeriodicBuildSchedule(): Promise<StaticBundlePeriodicBuildSchedule> {
    const config = await this.getOrCreatePeriodicBuildScheduleConfig();
    return this.toPeriodicBuildSchedule(config.enabled, config.intervalHours);
  }

  async updatePeriodicBuildSchedule(input: Partial<{ enabled: boolean; intervalHours: number }>) {
    const current = await this.getOrCreatePeriodicBuildScheduleConfig();
    const enabled = input.enabled ?? current.enabled;
    const intervalHours = input.intervalHours !== undefined
      ? this.normalizeInputIntervalHours(input.intervalHours)
      : current.intervalHours;

    const updated = await this.prisma.staticBundleScheduleConfig.upsert({
      where: { id: STATIC_BUNDLE_SCHEDULE_ROW_ID },
      update: {
        enabled,
        intervalHours
      },
      create: {
        id: STATIC_BUNDLE_SCHEDULE_ROW_ID,
        enabled,
        intervalHours
      }
    });

    await this.syncPeriodicBuildCronJob(updated.enabled, updated.intervalHours);
    return this.toPeriodicBuildSchedule(updated.enabled, updated.intervalHours);
  }

  async syncPeriodicBuildSchedule() {
    const config = await this.getOrCreatePeriodicBuildScheduleConfig();
    await this.syncPeriodicBuildCronJob(config.enabled, config.intervalHours);
    return this.toPeriodicBuildSchedule(config.enabled, config.intervalHours);
  }

  async buildBundle(force = false) {
    await this.ensureDefaultSources();
    const sources = await this.prisma.staticSource.findMany({
      where: { enabled: true },
      orderBy: { category: "asc" }
    });
    if (sources.length === 0) {
      throw new AppError(400, "static_source_empty", "No enabled static source.");
    }

    const sourceMeta: Record<string, unknown> = {};
    const payload: Record<string, unknown> = {
      resources: {}
    };

    for (const source of sources) {
      const targets = [source.activeUrl, ...source.fallbackUrls];
      let finalUrl: string | null = null;
      let content: unknown = null;
      let contentType: string | null = null;
      let fetchError: string | null = null;

      for (const url of targets) {
        try {
          const response = await fetch(url, {
            method: "GET"
          });
          if (!response.ok) {
            fetchError = `HTTP_${response.status}`;
            continue;
          }
          contentType = response.headers.get("content-type");
          const raw = await response.text();
          const parsed = this.tryParseText(raw, contentType);
          content = this.normalizeResourcePayload(source.category, parsed, raw);
          finalUrl = url;
          fetchError = null;
          break;
        } catch (error) {
          fetchError = error instanceof Error ? error.message : "unknown_error";
        }
      }

      if (!finalUrl) {
        throw new AppError(
          502,
          "static_source_fetch_failed",
          `Static source fetch failed: ${source.category}`,
          { category: source.category, error: fetchError }
        );
      }

      (payload.resources as Record<string, unknown>)[source.category] = content;
      sourceMeta[source.category] = {
        url: finalUrl,
        contentType
      };
    }

    payload.resources = this.normalizeBundleResources(payload.resources as Record<string, unknown>);

    const md5 = this.computeBundleMd5(payload);
    const version = `bundle-${Date.now()}`;

    if (!force) {
      const existing = await this.prisma.staticBundle.findFirst({
        where: {
          md5
        },
        orderBy: { createdAt: "desc" }
      });
      if (existing) {
        return {
          bundle: existing,
          created: false
        };
      }
    }

    const bundle = await this.prisma.$transaction(async (tx) => {
      await tx.staticBundle.updateMany({
        where: { active: true },
        data: { active: false }
      });
      return tx.staticBundle.create({
        data: {
          version,
          md5,
          payloadJson: this.toJsonValue(payload),
          sourceMeta: this.toJsonValue(sourceMeta),
          active: true,
          activatedAt: new Date()
        }
      });
    });

    return {
      bundle,
      created: true
    };
  }

  async listBundles(limit = 20) {
    return this.prisma.staticBundle.findMany({
      orderBy: { createdAt: "desc" },
      take: Math.max(1, Math.min(limit, 100))
    });
  }

  async manifest() {
    const active = await this.prisma.staticBundle.findFirst({
      where: { active: true },
      orderBy: { createdAt: "desc" }
    });
    if (!active) {
      const result = await this.buildBundle(false);
      return {
        version: result.bundle.version,
        md5: result.bundle.md5,
        createdAt: result.bundle.createdAt
      };
    }
    return {
      version: active.version,
      md5: active.md5,
      createdAt: active.createdAt
    };
  }

  async getBundle(version: string) {
    const where =
      version === "latest"
        ? {
            active: true
          }
        : {
            version
          };
    const bundle = await this.prisma.staticBundle.findFirst({
      where,
      orderBy: { createdAt: "desc" }
    });
    if (!bundle) {
      throw new AppError(404, "static_bundle_not_found", "Static bundle not found.");
    }
    return bundle;
  }

  async enqueuePeriodicBuild() {
    await this.prisma.jobQueue.create({
      data: {
        jobType: "static_bundle_build",
        payload: { intervalHours: this.env.STATIC_SYNC_INTERVAL_HOURS },
        status: "pending",
        scheduledAt: new Date()
      }
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
        intervalHours: defaultIntervalHours
      }
    });
  }

  private toPeriodicBuildSchedule(enabled: boolean, intervalHours: number): StaticBundlePeriodicBuildSchedule {
    return {
      enabled,
      intervalHours,
      cronExpression: this.toCronExpression(intervalHours)
    };
  }

  private normalizeEnvIntervalHours(value: number): number {
    if (!Number.isFinite(value)) {
      return 6;
    }
    return Math.max(1, Math.min(24, Math.trunc(value)));
  }

  private normalizeInputIntervalHours(value: number): number {
    if (!Number.isFinite(value)) {
      throw new AppError(400, "static_bundle_schedule_invalid_interval", "Interval hours must be a number.");
    }
    const normalized = Math.trunc(value);
    if (normalized < 1 || normalized > 24) {
      throw new AppError(400, "static_bundle_schedule_invalid_interval", "Interval hours must be between 1 and 24.");
    }
    return normalized;
  }

  private toCronExpression(intervalHours: number) {
    if (intervalHours <= 1) {
      return "0 * * * *";
    }
    if (intervalHours >= 24) {
      return "0 0 * * *";
    }
    return `0 */${intervalHours} * * *`;
  }

  private async ensurePgCronAvailable() {
    const rows = await this.prisma.$queryRaw<Array<{ available: boolean }>>`
      SELECT to_regclass('cron.job') IS NOT NULL AS "available";
    `;
    if (!rows[0]?.available) {
      throw new AppError(
        500,
        "static_bundle_schedule_unavailable",
        "pg_cron is unavailable; cannot configure automatic static bundle build."
      );
    }
  }

  private async syncPeriodicBuildCronJob(enabled: boolean, intervalHours: number) {
    await this.ensurePgCronAvailable();
    const cronExpression = this.toCronExpression(intervalHours);
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
      throw new AppError(
        500,
        "static_bundle_schedule_sync_failed",
        "Failed to sync static bundle periodic build schedule.",
        { error: error instanceof Error ? error.message : "unknown_error" }
      );
    }
  }

  private tryParseText(raw: string, contentType: string | null) {
    const normalizedType = (contentType ?? "").toLowerCase();
    const maybeJson = normalizedType.includes("json") || raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[");
    if (!maybeJson) {
      return raw;
    }
    try {
      return JSON.parse(raw) as unknown;
    } catch {
      return raw;
    }
  }

  private normalizeResourcePayload(category: string, parsed: unknown, raw: string) {
    if (category !== "dan_info") {
      return parsed;
    }

    return this.parseDanInfoPayload(parsed, raw);
  }

  private normalizeBundleResources(resources: Record<string, unknown>) {
    const normalized = { ...resources };
    normalized.lxns_aliases = this.normalizeLxnsAliasesPayload(
      resources.lxns_aliases,
      resources.songid_json
    );
    return normalized;
  }

  private normalizeLxnsAliasesPayload(lxnsPayload: unknown, songIdPayload: unknown) {
    const aliases = this.extractLxnsAliasRows(lxnsPayload);
    if (aliases.length === 0) {
      return lxnsPayload;
    }

    const knownSongIds = this.extractSongIdSet(songIdPayload);
    const merged = new Map<number, Set<string>>();

    for (const row of aliases) {
      const canonicalSongId = this.resolveCanonicalLxnsSongId(row.song_id, knownSongIds);
      const existing = merged.get(canonicalSongId) ?? new Set<string>();
      for (const alias of row.aliases) {
        const trimmed = alias.trim();
        if (!trimmed) {
          continue;
        }
        existing.add(trimmed);
      }
      merged.set(canonicalSongId, existing);
    }

    const normalizedAliases = Array.from(merged.entries())
      .sort((left, right) => left[0] - right[0])
      .map(([songId, aliasSet]) => ({
        song_id: songId,
        aliases: Array.from(aliasSet).sort()
      }));

    return {
      aliases: normalizedAliases
    };
  }

  private extractLxnsAliasRows(payload: unknown) {
    const sourceArray = Array.isArray(payload)
      ? payload
      : (this.toRecord(payload)?.aliases as unknown[] | undefined) ?? [];

    const rows: Array<{ song_id: number; aliases: string[] }> = [];
    for (const item of sourceArray) {
      const record = this.toRecord(item);
      if (!record) {
        continue;
      }
      const songId = Number(record.song_id);
      if (!Number.isFinite(songId)) {
        continue;
      }
      const aliasesRaw = Array.isArray(record.aliases)
        ? record.aliases.filter((entry: unknown): entry is string => typeof entry === "string")
        : [];
      rows.push({
        song_id: Math.trunc(songId),
        aliases: aliasesRaw
      });
    }
    return rows;
  }

  private extractSongIdSet(payload: unknown) {
    const set = new Set<number>();
    if (!Array.isArray(payload)) {
      return set;
    }
    for (const item of payload) {
      const record = this.toRecord(item);
      if (!record) {
        continue;
      }
      const id = Number(record.id);
      if (!Number.isFinite(id)) {
        continue;
      }
      set.add(Math.trunc(id));
    }
    return set;
  }

  private resolveCanonicalLxnsSongId(songId: number, knownSongIds: Set<number>) {
    if (knownSongIds.size === 0) {
      return songId;
    }

    if (songId > 0 && songId < 10000) {
      const dxCandidate = songId + 10000;
      if (!knownSongIds.has(songId) && knownSongIds.has(dxCandidate)) {
        return dxCandidate;
      }
    }

    const candidates = this.buildLxnsSongIdCandidates(songId);
    for (const candidate of candidates) {
      if (knownSongIds.has(candidate)) {
        return candidate;
      }
    }
    return songId;
  }

  private buildLxnsSongIdCandidates(songId: number) {
    const candidates: number[] = [];
    const push = (value: number) => {
      if (!Number.isFinite(value) || value <= 0) {
        return;
      }
      if (!candidates.includes(value)) {
        candidates.push(value);
      }
    };

    push(songId);

    if (songId > 0 && songId < 10000) {
      push(songId + 10000);
    }

    if (songId > 10000 && songId < 100000) {
      const baseId = songId % 10000;
      if (baseId > 0) {
        push(baseId);
        push(baseId + 10000);
      }
    }

    if (songId >= 100000) {
      const baseId = songId % 100000;
      if (baseId > 0) {
        push(baseId);
        if (baseId < 10000) {
          push(baseId + 10000);
        }
      }
    }

    return candidates;
  }

  private parseDanInfoPayload(parsed: unknown, raw: string) {
    let candidate = parsed;
    if (typeof candidate === "string") {
      try {
        candidate = parseYaml(raw);
      } catch (error) {
        throw new AppError(
          502,
          "static_source_invalid_payload",
          "Dan info YAML parse failed.",
          { error: error instanceof Error ? error.message : "unknown_error" }
        );
      }
    }

    return this.sanitizeDanCategories(candidate);
  }

  private sanitizeDanCategories(value: unknown) {
    if (!Array.isArray(value)) {
      return [];
    }

    const rows: Array<{
      title: string;
      id: string;
      sections: Array<{
        title?: string;
        description?: string;
        sheets: string[];
        sheetDescriptions?: string[];
      }>;
    }> = [];

    for (let index = 0; index < value.length; index += 1) {
      const item = value[index];
      if (typeof item !== "object" || item === null) {
        continue;
      }

      const record = item as Record<string, unknown>;
      const titleRaw = typeof record.title === "string" ? record.title : "";
      const title = titleRaw.trim();
      if (!title) {
        continue;
      }

      const lowerTitle = title.toLocaleLowerCase();
      if (lowerTitle.includes("test") || lowerTitle.includes("author's choice")) {
        continue;
      }

      const sectionItems = Array.isArray(record.sections) ? record.sections : [];
      const cleanedSections = sectionItems
        .map((section) => this.sanitizeDanSection(section))
        .filter((section): section is NonNullable<typeof section> => section !== null);
      if (cleanedSections.length === 0) {
        continue;
      }

      const idRaw = typeof record.id === "string" ? record.id.trim() : "";
      rows.push({
        title,
        id: idRaw || this.fallbackDanCategoryId(title, index),
        sections: cleanedSections
      });
    }

    return rows;
  }

  private sanitizeDanSection(section: unknown) {
    if (typeof section !== "object" || section === null) {
      return null;
    }

    const record = section as Record<string, unknown>;
    const rawSheets = Array.isArray(record.sheets)
      ? record.sheets.filter((item): item is string => typeof item === "string")
      : [];
    if (rawSheets.length === 0) {
      return null;
    }

    const validSheetIndexes = new Set<number>();
    const cleanedSheets: string[] = [];
    for (let index = 0; index < rawSheets.length; index += 1) {
      const rawSheet = rawSheets[index]!;
      if (!this.isValidDanRawSheetRef(rawSheet)) {
        continue;
      }
      validSheetIndexes.add(index);
      cleanedSheets.push(rawSheet.trim());
    }
    if (cleanedSheets.length === 0) {
      return null;
    }

    let cleanedSheetDescriptions: string[] | undefined;
    if (Array.isArray(record.sheetDescriptions)) {
      const descriptions = record.sheetDescriptions.filter((item): item is string => typeof item === "string");
      const paired: string[] = [];
      const pairCount = Math.min(rawSheets.length, descriptions.length);
      for (let index = 0; index < pairCount; index += 1) {
        if (!validSheetIndexes.has(index)) {
          continue;
        }
        const description = descriptions[index]!;
        paired.push(description);
      }
      cleanedSheetDescriptions = paired.length > 0 ? paired : undefined;
    }

    const title = typeof record.title === "string" ? record.title.trim() : "";
    const description = typeof record.description === "string" ? record.description.trim() : "";

    const cleanedSection: {
      title?: string;
      description?: string;
      sheets: string[];
      sheetDescriptions?: string[];
    } = {
      sheets: cleanedSheets
    };
    if (title) {
      cleanedSection.title = title;
    }
    if (description) {
      cleanedSection.description = description;
    }
    if (cleanedSheetDescriptions && cleanedSheetDescriptions.length > 0) {
      cleanedSection.sheetDescriptions = cleanedSheetDescriptions;
    }
    return cleanedSection;
  }

  private isValidDanRawSheetRef(raw: string) {
    const trimmed = raw.trim();
    if (!trimmed) {
      return false;
    }

    const parts = trimmed.split("|");
    if (parts.length < 3) {
      return false;
    }

    const title = (parts[0] ?? "").trim();
    const type = (parts[1] ?? "").trim().toLocaleLowerCase();
    const difficulty = (parts[2] ?? "").trim().toLocaleLowerCase();
    if (!title || !type || !difficulty) {
      return false;
    }

    if (type.includes("utage") || difficulty.includes("utage")) {
      return false;
    }

    const validTypes = new Set(["dx", "std"]);
    if (!validTypes.has(type)) {
      return false;
    }

    const validDifficulties = new Set(["basic", "advanced", "expert", "master", "remaster"]);
    return validDifficulties.has(difficulty);
  }

  private fallbackDanCategoryId(title: string, index: number) {
    const normalized = title
      .normalize("NFKC")
      .toLocaleLowerCase()
      .replace(/\s+/gu, "-")
      .replace(/[^\p{L}\p{N}_-]/gu, "");
    return normalized || `category-${index + 1}`;
  }

  private computeBundleMd5(payload: Record<string, unknown>) {
    const hashMaterial = payload.resources ?? {};
    const canonical = this.stableStringify(hashMaterial);
    return createHash("md5").update(canonical).digest("hex");
  }

  private stableStringify(value: unknown) {
    return JSON.stringify(this.normalizeForStableHash(value));
  }

  private toRecord(value: unknown) {
    return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
  }

  private normalizeForStableHash(value: unknown): unknown {
    if (value === null || value === undefined) {
      return null;
    }

    if (value instanceof Date) {
      return value.toISOString();
    }

    if (Array.isArray(value)) {
      return value.map((item) => this.normalizeForStableHash(item));
    }

    if (typeof value === "object") {
      const record = value as Record<string, unknown>;
      const normalized: Record<string, unknown> = {};
      for (const key of Object.keys(record).sort()) {
        normalized[key] = this.normalizeForStableHash(record[key]);
      }
      return normalized;
    }

    return value;
  }
}
