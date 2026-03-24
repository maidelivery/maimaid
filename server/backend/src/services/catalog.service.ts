import { inject, injectable } from "tsyringe";
import type { Prisma, PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import type { Env } from "../env.js";
import { AppError } from "../lib/errors.js";
import { sha256Hex } from "../lib/crypto.js";

type RemoteDataResponse = {
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
    return this.prisma.alias.findMany({
      where,
      orderBy: [{ songIdentifier: "asc" }, { aliasText: "asc" }]
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

  async syncCatalog(force = false) {
    const response = await fetch(this.env.CATALOG_SOURCE_URL, {
      method: "GET"
    });
    if (!response.ok) {
      throw new AppError(502, "catalog_fetch_failed", `Catalog source fetch failed: ${response.status}`);
    }

    const etag = response.headers.get("etag");
    const payload = (await response.json()) as RemoteDataResponse;
    if (!payload.songs || !Array.isArray(payload.songs)) {
      throw new AppError(502, "catalog_invalid_payload", "Catalog source payload is invalid.");
    }

    const payloadHash = sha256Hex(JSON.stringify(payload));
    const existed = await this.prisma.catalogSnapshot.findFirst({
      where: {
        source: "data.json",
        payloadHash
      }
    });

    if (existed && !force) {
      return { snapshot: existed, applied: false };
    }

    const snapshot = await this.prisma.catalogSnapshot.create({
      data: {
        source: "data.json",
        sourceUrl: this.env.CATALOG_SOURCE_URL,
        etag,
        payloadHash,
        status: "pending",
        payloadJson: payload,
        metadataJson: {
          songCount: payload.songs.length,
          categoryCount: payload.categories?.length ?? 0,
          versionCount: payload.versions?.length ?? 0
        }
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
}
