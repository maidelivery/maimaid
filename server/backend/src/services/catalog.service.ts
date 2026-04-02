import { inject, injectable } from "tsyringe";
import type { Prisma, PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import type { Env } from "../env.js";
import { AppError } from "../lib/errors.js";
import { sha256Hex } from "../lib/crypto.js";

export type RemoteDataResponse = {
  songs: RemoteSong[];
  categories?: Array<{ category: string }>;
  versions?: Array<{ version: string; abbr: string; releaseDate?: string | null }>;
};

type RemoteSong = {
  songId: string;
  category?: string | null;
  title?: string | null;
  artist?: string | null;
  bpm?: number | null;
  imageName?: string | null;
  version?: string | null;
  releaseDate?: string | null;
  isNew?: boolean | null;
  isLocked?: boolean | null;
  comment?: string | null;
  sheets: RemoteSheet[];
};

type RemoteSheet = {
  type: string;
  difficulty: string;
  version?: string | null;
  level: string;
  levelValue?: number | null;
  internalLevel?: string | null;
  internalLevelValue?: number | null;
  noteDesigner?: string | null;
  noteCounts?: {
    tap?: number | null;
    hold?: number | null;
    slide?: number | null;
    touch?: number | null;
    break?: number | null;
    total?: number | null;
  } | null;
  regions?: Record<string, boolean> | null;
  isSpecial?: boolean | null;
};

type LxnsAliasItem = {
  song_id?: number | string;
  aliases?: string[];
};

type CatalogAliasRow = {
  id: string;
  songIdentifier: string;
  aliasText: string;
  aliasNorm: string;
  source: string;
  status: string;
  createdAt: Date;
  updatedAt: Date;
};

type CatalogVersionItem = {
  version: string;
  abbr: string;
  releaseDate: string | null;
};

@injectable()
export class CatalogService {
  constructor(
    @inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
    @inject(TOKENS.Env) private readonly env: Env
  ) {}

  async listSongs(includeDisabled: boolean, keyword?: string) {
    const where: Prisma.SongWhereInput = {};
    if (!includeDisabled) {
      where.disabled = false;
    }
    if (keyword) {
      where.OR = [
        { title: { contains: keyword, mode: "insensitive" } },
        { artist: { contains: keyword, mode: "insensitive" } },
        { songIdentifier: { contains: keyword, mode: "insensitive" } }
      ];
    }

    return this.prisma.song.findMany({
      where,
      orderBy: [{ sortOrder: "asc" }, { songIdentifier: "asc" }]
    });
  }

  async listSheets(songIdentifier?: string) {
    const where: Prisma.SheetWhereInput = {};
    if (songIdentifier !== undefined) {
      where.songIdentifier = songIdentifier;
    }
    return this.prisma.sheet.findMany({
      where,
      orderBy: [{ songIdentifier: "asc" }, { chartType: "asc" }, { difficulty: "asc" }]
    });
  }

  async listAliases(songIdentifier?: string, source?: string) {
    const where: Prisma.AliasWhereInput = {};
    if (songIdentifier !== undefined) {
      where.songIdentifier = songIdentifier;
    }
    if (source !== undefined) {
      where.source = source;
    }
    const aliases = await this.prisma.alias.findMany({
      where,
      orderBy: [{ songIdentifier: "asc" }, { aliasText: "asc" }]
    });

    const baseRows: CatalogAliasRow[] = aliases.map((item) => ({
      id: item.id.toString(),
      songIdentifier: item.songIdentifier,
      aliasText: item.aliasText,
      aliasNorm: item.aliasNorm,
      source: item.source,
      status: item.status,
      createdAt: item.createdAt,
      updatedAt: item.updatedAt
    }));

    const normalizedSource = source?.trim().toLowerCase();
    const shouldIncludeLxns = !normalizedSource || normalizedSource === "lxns" || normalizedSource === "lxns_aliases";
    if (!shouldIncludeLxns) {
      return baseRows;
    }

    const songIdentifierByNameMap = await this.buildSongIdentifierByNameMap();
    const lxnsRowsFromBundle = await this.listLxnsAliasesFromBundle(songIdentifier, songIdentifierByNameMap);
    const lxnsRows =
      lxnsRowsFromBundle.length > 0
        ? lxnsRowsFromBundle
        : await this.listLxnsAliasesFromRemote(songIdentifier, songIdentifierByNameMap);
    if (lxnsRows.length === 0) {
      return baseRows;
    }

    const existing = new Set(baseRows.map((item) => `${item.songIdentifier}:${item.aliasNorm}`));
    const merged = [...baseRows];
    for (const row of lxnsRows) {
      const key = `${row.songIdentifier}:${row.aliasNorm}`;
      if (existing.has(key)) {
        continue;
      }
      existing.add(key);
      merged.push(row);
    }

    return merged.sort((left, right) => {
      const identifierDiff = left.songIdentifier.localeCompare(right.songIdentifier);
      if (identifierDiff !== 0) return identifierDiff;
      return left.aliasText.localeCompare(right.aliasText);
    });
  }

  async listIcons() {
    return this.prisma.icon.findMany({
      orderBy: { id: "asc" }
    });
  }

  async listSnapshots() {
    return this.prisma.catalogSnapshot.findMany({
      orderBy: { fetchedAt: "desc" },
      take: 30
    });
  }

  async listVersions() {
    const latestAppliedSnapshot = await this.prisma.catalogSnapshot.findFirst({
      where: { status: "applied" },
      orderBy: [{ activatedAt: "desc" }, { fetchedAt: "desc" }],
      select: { payloadJson: true }
    });
    const versionsFromSnapshot = this.extractCatalogVersionItems(latestAppliedSnapshot?.payloadJson);
    if (versionsFromSnapshot.length > 0) {
      return versionsFromSnapshot;
    }

    const activeBundle = await this.prisma.staticBundle.findFirst({
      where: { active: true },
      orderBy: { createdAt: "desc" },
      select: { payloadJson: true }
    });
    const bundlePayload = this.toRecord(activeBundle?.payloadJson);
    const resources = this.toRecord(bundlePayload?.resources);
    return this.extractCatalogVersionItems(resources?.data_json);
  }

  async applyCatalogData(
    payloadInput: unknown,
    options: {
      source: string;
      sourceUrl: string;
      etag?: string | null;
      force?: boolean;
      applyWhenUnchanged?: boolean;
      metadata?: Record<string, unknown>;
    }
  ) {
    const payload = this.parseCatalogPayload(payloadInput);
    if (!payload) {
      throw new AppError(502, "catalog_invalid_payload", "Catalog source payload is invalid.");
    }

    const source = options.source.trim();
    const sourceUrl = options.sourceUrl.trim();
    if (!source || !sourceUrl) {
      throw new AppError(400, "catalog_snapshot_source_invalid", "Catalog snapshot source metadata is invalid.");
    }

    const force = options.force ?? false;
    const applyWhenUnchanged = options.applyWhenUnchanged ?? false;
    const payloadHash = sha256Hex(JSON.stringify(payload));
    const metadata = this.buildSnapshotMetadata(payload, options.metadata);

    const existed = await this.prisma.catalogSnapshot.findFirst({
      where: {
        source,
        payloadHash
      }
    });

    if (existed && !force && !applyWhenUnchanged) {
      return { snapshot: existed, applied: false };
    }

    const snapshot = existed
      ? await this.prisma.catalogSnapshot.update({
          where: { id: existed.id },
          data: {
            sourceUrl,
            etag: options.etag ?? null,
            status: "pending",
            payloadJson: payload,
            metadataJson: metadata
          }
        })
      : await this.prisma.catalogSnapshot.create({
          data: {
            source,
            sourceUrl,
            etag: options.etag ?? null,
            payloadHash,
            status: "pending",
            payloadJson: payload,
            metadataJson: metadata
          }
        });

    try {
      await this.applySnapshotPayload(snapshot.id, payload);
      const appliedSnapshot = await this.prisma.catalogSnapshot.update({
        where: { id: snapshot.id },
        data: {
          status: "applied",
          activatedAt: new Date()
        }
      });
      return { snapshot: appliedSnapshot, applied: true };
    } catch (error) {
      await this.prisma.catalogSnapshot.update({
        where: { id: snapshot.id },
        data: {
          status: "failed",
          metadataJson: {
            ...(snapshot.metadataJson as Record<string, unknown> | null),
            error: error instanceof Error ? error.message : "unknown_error"
          }
        }
      });
      throw error;
    }
  }

  async syncCatalog(force = false) {
    const sourceUrl = this.env.CATALOG_SOURCE_URL?.trim();
    if (!sourceUrl) {
      throw new AppError(
        400,
        "catalog_source_not_configured",
        "CATALOG_SOURCE_URL is not configured. Build a static bundle or set CATALOG_SOURCE_URL for manual sync."
      );
    }

    const response = await fetch(sourceUrl, {
      method: "GET"
    });
    if (!response.ok) {
      throw new AppError(502, "catalog_fetch_failed", `Catalog source fetch failed: ${response.status}`);
    }

    const etag = response.headers.get("etag");
    const payload = (await response.json()) as unknown;
    return this.applyCatalogData(payload, {
      source: "data.json",
      sourceUrl,
      etag,
      force,
      applyWhenUnchanged: force
    });
  }

  async rollback(snapshotId: bigint) {
    const snapshot = await this.prisma.catalogSnapshot.findUnique({
      where: { id: snapshotId }
    });
    if (!snapshot) {
      throw new AppError(404, "snapshot_not_found", "Catalog snapshot not found.");
    }

    const payload = snapshot.payloadJson as unknown as RemoteDataResponse;
    if (!payload?.songs) {
      throw new AppError(400, "snapshot_invalid", "Catalog snapshot payload is invalid.");
    }

    await this.applySnapshotPayload(snapshot.id, payload);
    return this.prisma.catalogSnapshot.update({
      where: { id: snapshot.id },
      data: {
        status: "applied",
        activatedAt: new Date()
      }
    });
  }

  private async applySnapshotPayload(snapshotId: bigint, payload: RemoteDataResponse) {
    const songs = payload.songs;
    await this.prisma.$transaction(async (tx) => {
      await tx.song.updateMany({
        data: { disabled: true }
      });

      for (let index = 0; index < songs.length; index += 1) {
        const song = songs[index]!;
        const songIdentifier = `${song.songId}`;

        await tx.song.upsert({
          where: { songIdentifier },
          create: {
            songIdentifier,
            songId: 0,
            category: (song.category ?? "").trim(),
            title: (song.title ?? "").trim(),
            artist: (song.artist ?? "").trim(),
            imageName: (song.imageName ?? "").trim(),
            version: song.version ?? null,
            releaseDate: this.parseDate(song.releaseDate),
            sortOrder: index,
            bpm: song.bpm ?? null,
            isNew: song.isNew ?? false,
            isLocked: song.isLocked ?? false,
            comment: song.comment ?? null,
            disabled: false,
            snapshotId
          },
          update: {
            category: (song.category ?? "").trim(),
            title: (song.title ?? "").trim(),
            artist: (song.artist ?? "").trim(),
            imageName: (song.imageName ?? "").trim(),
            version: song.version ?? null,
            releaseDate: this.parseDate(song.releaseDate),
            sortOrder: index,
            bpm: song.bpm ?? null,
            isNew: song.isNew ?? false,
            isLocked: song.isLocked ?? false,
            comment: song.comment ?? null,
            disabled: false,
            snapshotId
          }
        });
      }

      await tx.sheet.deleteMany({});

      const chunks: Prisma.SheetCreateManyInput[][] = [];
      const buffer: Prisma.SheetCreateManyInput[] = [];
      for (const song of songs) {
        const songIdentifier = `${song.songId}`;
        for (const sheet of song.sheets) {
          buffer.push({
            songIdentifier,
            songId: 0,
            chartType: this.normalizeChartType(sheet.type),
            difficulty: sheet.difficulty,
            version: sheet.version ?? null,
            level: sheet.level,
            levelValue: sheet.levelValue ?? null,
            internalLevel: sheet.internalLevel ?? null,
            internalLevelValue: sheet.internalLevelValue ?? null,
            noteDesigner: sheet.noteDesigner ?? null,
            tap: sheet.noteCounts?.tap ?? null,
            hold: sheet.noteCounts?.hold ?? null,
            slide: sheet.noteCounts?.slide ?? null,
            touch: sheet.noteCounts?.touch ?? null,
            breakCount: sheet.noteCounts?.break ?? null,
            total: sheet.noteCounts?.total ?? null,
            regionJp: sheet.regions?.jp ?? false,
            regionIntl: sheet.regions?.intl ?? false,
            regionUsa: sheet.regions?.usa ?? false,
            regionCn: sheet.regions?.cn ?? false,
            isSpecial: sheet.isSpecial ?? false
          });
          if (buffer.length >= 1000) {
            chunks.push([...buffer]);
            buffer.length = 0;
          }
        }
      }
      if (buffer.length > 0) {
        chunks.push([...buffer]);
      }

      for (const batch of chunks) {
        await tx.sheet.createMany({
          data: batch,
          skipDuplicates: true
        });
      }
    });
  }

  private parseCatalogPayload(value: unknown): RemoteDataResponse | null {
    const record = this.toRecord(value);
    if (!record) {
      return null;
    }
    if (!Array.isArray(record.songs)) {
      return null;
    }
    return record as unknown as RemoteDataResponse;
  }

  private buildSnapshotMetadata(payload: RemoteDataResponse, metadata?: Record<string, unknown>) {
    return {
      songCount: payload.songs.length,
      categoryCount: payload.categories?.length ?? 0,
      versionCount: payload.versions?.length ?? 0,
      ...(metadata ?? {})
    };
  }

  private extractCatalogVersionItems(value: unknown): CatalogVersionItem[] {
    const record = this.toRecord(value);
    const source = Array.isArray(record?.versions) ? record.versions : Array.isArray(value) ? value : [];
    if (!Array.isArray(source)) {
      return [];
    }

    const rows: CatalogVersionItem[] = [];
    for (const item of source) {
      const row = this.toRecord(item);
      if (!row) {
        continue;
      }
      const versionRaw = typeof row.version === "string" ? row.version : "";
      const abbrRaw = typeof row.abbr === "string" ? row.abbr : "";
      const version = versionRaw.trim();
      if (!version) {
        continue;
      }
      rows.push({
        version,
        abbr: abbrRaw.trim() || version,
        releaseDate: typeof row.releaseDate === "string" ? row.releaseDate : null
      });
    }

    return rows;
  }

  private parseDate(value?: string | null): Date | null {
    if (!value) {
      return null;
    }
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) {
      return null;
    }
    return date;
  }

  private normalizeChartType(value: string): string {
    const lowercased = value.trim().toLowerCase();
    if (lowercased === "dx") {
      return "dx";
    }
    if (lowercased === "std" || lowercased === "sd") {
      return "standard";
    }
    if (lowercased === "utage") {
      return "utage";
    }
    return lowercased;
  }

  private async listLxnsAliasesFromBundle(
    songIdentifier: string | undefined,
    songIdentifierByNameMap: Map<string, string>
  ) {
    const activeBundle = await this.prisma.staticBundle.findFirst({
      where: { active: true },
      orderBy: { createdAt: "desc" },
      select: { payloadJson: true }
    });
    if (!activeBundle) {
      return [];
    }

    const payload = activeBundle.payloadJson as Record<string, unknown>;
    const resources = this.toRecord(payload.resources);
    const lxnsPayload = resources ? (resources.lxns_aliases as unknown) : null;
    const songIdNameMap = this.extractSongIdNameMap(resources ? resources.songid_json : null);
    const rawItems = this.extractLxnsItems(lxnsPayload);
    if (rawItems.length === 0) {
      return [];
    }
    const knownSongIdentifiers = new Set(songIdentifierByNameMap.values());

    const now = new Date();
    const rows: CatalogAliasRow[] = [];
    for (const item of rawItems) {
      const candidateIdentifiers = this.resolveLxnsSongIdentifiers(item.song_id, songIdNameMap, songIdentifierByNameMap);
      if (candidateIdentifiers.length === 0) {
        continue;
      }
      const matchedIdentifier = songIdentifier
        ? candidateIdentifiers.find((value) => value === songIdentifier)
        : candidateIdentifiers.find((value) => knownSongIdentifiers.has(value)) ?? candidateIdentifiers[0];
      if (!matchedIdentifier) {
        continue;
      }
      const aliasList = Array.isArray(item.aliases) ? item.aliases : [];
      for (const aliasTextRaw of aliasList) {
        const aliasText = aliasTextRaw.trim();
        const aliasNorm = this.normalizeAlias(aliasText);
        if (!aliasText || !aliasNorm) {
          continue;
        }
        rows.push({
          id: `lxns:${matchedIdentifier}:${aliasNorm}`,
          songIdentifier: matchedIdentifier,
          aliasText,
          aliasNorm,
          source: "lxns",
          status: "approved",
          createdAt: now,
          updatedAt: now
        });
      }
    }
    return rows;
  }

  private async listLxnsAliasesFromRemote(
    songIdentifier: string | undefined,
    songIdentifierByNameMap: Map<string, string>
  ) {
    try {
      const source = await this.prisma.staticSource.findUnique({
        where: { category: "lxns_aliases" },
        select: { activeUrl: true, enabled: true }
      });
      const songIdNameMap = await this.loadSongIdNameMap();
      const targetUrl = source?.enabled && source.activeUrl ? source.activeUrl : "https://maimai.lxns.net/api/v0/maimai/alias/list";
      const response = await fetch(targetUrl, { method: "GET" });
      if (!response.ok) {
        return [];
      }
      const payload = (await response.json()) as unknown;
      const rawItems = this.extractLxnsItems(payload);
      if (rawItems.length === 0) {
        return [];
      }
      const knownSongIdentifiers = new Set(songIdentifierByNameMap.values());
      const now = new Date();
      const rows: CatalogAliasRow[] = [];
      for (const item of rawItems) {
        const candidateIdentifiers = this.resolveLxnsSongIdentifiers(item.song_id, songIdNameMap, songIdentifierByNameMap);
        if (candidateIdentifiers.length === 0) {
          continue;
        }
        const matchedIdentifier = songIdentifier
          ? candidateIdentifiers.find((value) => value === songIdentifier)
          : candidateIdentifiers.find((value) => knownSongIdentifiers.has(value)) ?? candidateIdentifiers[0];
        if (!matchedIdentifier) {
          continue;
        }
        const aliasList = Array.isArray(item.aliases) ? item.aliases : [];
        for (const aliasTextRaw of aliasList) {
          const aliasText = aliasTextRaw.trim();
          const aliasNorm = this.normalizeAlias(aliasText);
          if (!aliasText || !aliasNorm) {
            continue;
          }
          rows.push({
            id: `lxns:${matchedIdentifier}:${aliasNorm}`,
            songIdentifier: matchedIdentifier,
            aliasText,
            aliasNorm,
            source: "lxns",
            status: "approved",
            createdAt: now,
            updatedAt: now
          });
        }
      }
      return rows;
    } catch {
      return [];
    }
  }

  private extractLxnsItems(value: unknown): LxnsAliasItem[] {
    if (!value) {
      return [];
    }
    if (Array.isArray(value)) {
      return value.filter((item): item is LxnsAliasItem => this.isLxnsAliasItem(item));
    }
    const record = this.toRecord(value);
    const aliases = record ? record.aliases : null;
    if (!Array.isArray(aliases)) {
      return [];
    }
    return aliases.filter((item): item is LxnsAliasItem => this.isLxnsAliasItem(item));
  }

  private isLxnsAliasItem(value: unknown): value is LxnsAliasItem {
    return typeof value === "object" && value !== null;
  }

  private resolveLxnsSongIdentifiers(
    songId: number | string | undefined,
    songIdNameMap: Map<number, string>,
    songIdentifierByNameMap: Map<string, string>
  ) {
    if (songId === undefined) {
      return [];
    }
    const numericId = Number(songId);
    if (!Number.isFinite(numericId)) {
      const raw = String(songId).trim();
      return raw ? [raw] : [];
    }
    const normalized = Math.trunc(numericId);
    const numericCandidates = this.expandLxnsSongIdCandidates(normalized);
    const candidates = new Set<string>();
    for (const id of numericCandidates) {
      candidates.add(String(id));
    }

    for (const id of numericCandidates) {
      const songName = songIdNameMap.get(id);
      if (!songName) {
        continue;
      }
      const mappedIdentifier = songIdentifierByNameMap.get(this.normalizeSongName(songName));
      if (mappedIdentifier) {
        candidates.add(mappedIdentifier);
      }
    }
    return Array.from(candidates);
  }

  private expandLxnsSongIdCandidates(songId: number) {
    const candidates = new Set<number>();
    if (!Number.isFinite(songId) || songId <= 0) {
      return candidates;
    }

    candidates.add(songId);

    // DX era compatibility:
    // - LXNS may provide base ids (e.g. 1234)
    // - local songid.json may use 10000 + base (e.g. 11234)
    if (songId < 10000) {
      candidates.add(songId + 10000);
    }

    // Some providers may return 10000+ ids; keep both forms.
    if (songId > 10000 && songId < 100000) {
      const baseId = songId % 10000;
      if (baseId > 0) {
        candidates.add(baseId);
        candidates.add(baseId + 10000);
      }
    }

    // Keep UTAGE ids as-is, but still attempt base fallbacks.
    if (songId >= 100000) {
      const baseId = songId % 100000;
      if (baseId > 0) {
        candidates.add(baseId);
        if (baseId < 10000) {
          candidates.add(baseId + 10000);
        }
      }
    }

    return candidates;
  }

  private normalizeAlias(value: string) {
    return value.trim().toLocaleLowerCase().replace(/\s+/gu, "");
  }

  private normalizeSongName(value: string) {
    return value.trim().toLocaleLowerCase().replace(/\s+/gu, "");
  }

  private toRecord(value: unknown) {
    return typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;
  }

  private extractSongIdNameMap(value: unknown) {
    const map = new Map<number, string>();
    if (!Array.isArray(value)) {
      return map;
    }
    for (const item of value) {
      if (typeof item !== "object" || item === null) {
        continue;
      }
      const row = item as Record<string, unknown>;
      const id = Number(row.id);
      const name = typeof row.name === "string" ? row.name.trim() : "";
      if (!Number.isFinite(id) || !name) {
        continue;
      }
      map.set(Math.trunc(id), name);
    }
    return map;
  }

  private async loadSongIdNameMap() {
    const activeBundle = await this.prisma.staticBundle.findFirst({
      where: { active: true },
      orderBy: { createdAt: "desc" },
      select: { payloadJson: true }
    });
    const payload = activeBundle?.payloadJson as Record<string, unknown> | undefined;
    const resources = payload ? this.toRecord(payload.resources) : null;
    const fromBundle = this.extractSongIdNameMap(resources ? resources.songid_json : null);
    if (fromBundle.size > 0) {
      return fromBundle;
    }

    try {
      const source = await this.prisma.staticSource.findUnique({
        where: { category: "songid_json" },
        select: { activeUrl: true, enabled: true }
      });
      const targetUrl = source?.enabled && source.activeUrl ? source.activeUrl : "https://maimaid.shikoch.in/songid.json";
      const response = await fetch(targetUrl, { method: "GET" });
      if (!response.ok) {
        return new Map<number, string>();
      }
      const parsed = (await response.json()) as unknown;
      return this.extractSongIdNameMap(parsed);
    } catch {
      return new Map<number, string>();
    }
  }

  private async buildSongIdentifierByNameMap() {
    const [songs, aliases] = await Promise.all([
      this.prisma.song.findMany({
        select: {
          songIdentifier: true,
          title: true
        }
      }),
      this.prisma.alias.findMany({
        select: {
          songIdentifier: true,
          aliasText: true
        }
      })
    ]);

    const map = new Map<string, string>();
    for (const song of songs) {
      map.set(this.normalizeSongName(song.title), song.songIdentifier);
      map.set(this.normalizeSongName(song.songIdentifier), song.songIdentifier);
    }
    for (const alias of aliases) {
      const normalized = this.normalizeSongName(alias.aliasText);
      if (!normalized || map.has(normalized)) {
        continue;
      }
      map.set(normalized, alias.songIdentifier);
    }
    return map;
  }
}
