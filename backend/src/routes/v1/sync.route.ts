import { Hono, type Context } from "hono";
import { z } from "zod";
import { TOKENS } from "../../di/tokens.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";
import { SyncService } from "../../services/sync.service.js";
import { ProfileService } from "../../services/profile.service.js";
import { ScoreService } from "../../services/score.service.js";
import { removeLegacyDivingFishPlayRecords } from "../../services/legacy-play-record-cleanup.js";
import { PrismaClient } from "@prisma/client";
import { AppError } from "../../lib/errors.js";

// Full-profile backups can contain tens of thousands of play records. Keep a
// bounded payload while allowing existing profiles to synchronize atomically.
const BULK_ARRAY_MAX = 50_000;
const MAX_COLLECTIONS_PER_USER = 100;
const MAX_ITEMS_PER_COLLECTION = 10_000;
const MAX_ITEMS_PER_USER = 50_000;

const scoreEntrySchema = z.object({
	sheetId: z
		.union([z.bigint(), z.number().int(), z.string().regex(/^\d+$/)])
		.transform((value) => (typeof value === "bigint" ? value : BigInt(value)))
		.optional(),
	songIdentifier: z.string().optional(),
	songId: z.number().int().optional(),
	title: z.string().optional(),
	chartType: z.string().optional(),
	type: z.string().optional(),
	difficulty: z.string().optional(),
	levelIndex: z.number().int().optional(),
	achievements: z.number(),
	rank: z.string().optional(),
	dxScore: z.number().int().optional(),
	fc: z.string().nullable().optional(),
	fs: z.string().nullable().optional(),
	achievedAt: z.string().optional(),
});

const playRecordSchema = scoreEntrySchema.extend({
	playTime: z.string().optional(),
});

const collectionUpsertSchema = z.object({
	collectionId: z.uuid(),
	name: z.string().trim().min(1).max(40),
	sortIndex: z.number().int().min(0).max(100_000),
	createdAt: z.coerce.date().optional(),
	deletedAt: z.coerce.date().nullable().optional(),
	clientUpdatedAt: z.coerce.date().optional(),
});

const collectionItemUpsertSchema = z.object({
	itemId: z.uuid(),
	collectionId: z.uuid(),
	songId: z.string().trim().min(1).max(200),
	chartType: z.string().trim().min(1).max(32).transform((value) => value.toLowerCase()),
	difficulty: z.string().trim().min(1).max(64).transform((value) => value.toLowerCase()),
	position: z.number().int().min(0).max(1_000_000),
	createdAt: z.coerce.date().optional(),
	deletedAt: z.coerce.date().nullable().optional(),
	clientUpdatedAt: z.coerce.date().optional(),
});

const pushSchema = z.object({
	idempotencyKey: z.string().min(8),
	forceProfileOverwrite: z.boolean().default(false),
	replaceScoreProfileIds: z.array(z.uuid()).max(BULK_ARRAY_MAX).default([]),
	replacePlayRecordProfileIds: z.array(z.uuid()).max(BULK_ARRAY_MAX).default([]),
	profileUpserts: z
		.array(
			z.object({
				profileId: z.uuid(),
				name: z.string().min(1).max(40),
				server: z.enum(["jp", "intl", "usa", "cn"]).default("jp"),
				isActive: z.boolean().optional(),
				playerRating: z.number().int().nonnegative().optional(),
				plate: z.string().nullable().optional(),
				avatarUrl: z.url().nullable().optional(),
				dfUsername: z.string().optional(),
				b35Count: z.number().int().positive().optional(),
				b15Count: z.number().int().positive().optional(),
				b35RecLimit: z.number().int().positive().optional(),
				b15RecLimit: z.number().int().positive().optional(),
				createdAt: z.coerce.date().optional(),
				clientUpdatedAt: z.coerce.date().optional(),
			}),
		)
		.max(BULK_ARRAY_MAX)
		.default([]),
	scoreUpserts: z
		.array(
			z.object({
				profileId: z.uuid(),
				scores: z.array(scoreEntrySchema).min(1).max(BULK_ARRAY_MAX),
			}),
		)
		.max(BULK_ARRAY_MAX)
		.default([]),
	playRecordUpserts: z
		.array(
			z.object({
				profileId: z.uuid(),
				records: z.array(playRecordSchema).min(1).max(BULK_ARRAY_MAX),
			}),
		)
		.max(BULK_ARRAY_MAX)
		.default([]),
	collectionUpserts: z.array(collectionUpsertSchema).max(BULK_ARRAY_MAX).default([]),
	collectionItemUpserts: z.array(collectionItemUpsertSchema).max(BULK_ARRAY_MAX).default([]),
});

const pullQuerySchema = z.object({
	sinceRevision: z
		.string()
		.optional()
		.transform((value) => {
			if (!value) return 0n;
			if (!/^\d+$/.test(value)) return 0n;
			return BigInt(value);
		}),
	profileId: z.uuid().optional(),
	limit: z
		.string()
		.optional()
		.transform((value) => {
			const parsed = Number(value ?? 200);
			if (!Number.isFinite(parsed)) return 200;
			return Math.max(1, Math.min(500, Math.trunc(parsed)));
		}),
	includeSnapshot: z
		.enum(["true", "false"])
		.optional()
		.transform((value) => {
			if (value === undefined) {
				return null;
			}
			return value === "true";
		}),
});

type ScoreEntryBody = z.infer<typeof scoreEntrySchema>;
type PlayRecordBody = z.infer<typeof playRecordSchema>;

const mapScoresForUpsert = (scores: ScoreEntryBody[]) =>
	scores.map((item) => {
		const mapped: {
			sheetId?: bigint;
			songIdentifier?: string;
			songId?: number;
			title?: string;
			chartType?: string;
			type?: string;
			difficulty?: string;
			levelIndex?: number;
			achievements: number;
			rank?: string;
			dxScore?: number;
			fc?: string | null;
			fs?: string | null;
			achievedAt?: string;
		} = {
			achievements: item.achievements,
		};
		if (item.sheetId !== undefined) mapped.sheetId = item.sheetId;
		if (item.songIdentifier !== undefined) mapped.songIdentifier = item.songIdentifier;
		if (item.songId !== undefined) mapped.songId = item.songId;
		if (item.title !== undefined) mapped.title = item.title;
		if (item.chartType !== undefined) mapped.chartType = item.chartType;
		if (item.type !== undefined) mapped.type = item.type;
		if (item.difficulty !== undefined) mapped.difficulty = item.difficulty;
		if (item.levelIndex !== undefined) mapped.levelIndex = item.levelIndex;
		if (item.rank !== undefined) mapped.rank = item.rank;
		if (item.dxScore !== undefined) mapped.dxScore = item.dxScore;
		if (item.fc !== undefined) mapped.fc = item.fc;
		if (item.fs !== undefined) mapped.fs = item.fs;
		if (item.achievedAt !== undefined) mapped.achievedAt = item.achievedAt;
		return mapped;
	});

const mapPlayRecords = (records: PlayRecordBody[]) =>
	records.map((item) => {
		const mapped: {
			sheetId?: bigint;
			songIdentifier?: string;
			songId?: number;
			title?: string;
			chartType?: string;
			type?: string;
			difficulty?: string;
			levelIndex?: number;
			achievements: number;
			rank?: string;
			dxScore?: number;
			fc?: string | null;
			fs?: string | null;
			playTime?: string;
		} = {
			achievements: item.achievements,
		};
		if (item.sheetId !== undefined) mapped.sheetId = item.sheetId;
		if (item.songIdentifier !== undefined) mapped.songIdentifier = item.songIdentifier;
		if (item.songId !== undefined) mapped.songId = item.songId;
		if (item.title !== undefined) mapped.title = item.title;
		if (item.chartType !== undefined) mapped.chartType = item.chartType;
		if (item.type !== undefined) mapped.type = item.type;
		if (item.difficulty !== undefined) mapped.difficulty = item.difficulty;
		if (item.levelIndex !== undefined) mapped.levelIndex = item.levelIndex;
		if (item.rank !== undefined) mapped.rank = item.rank;
		if (item.dxScore !== undefined) mapped.dxScore = item.dxScore;
		if (item.fc !== undefined) mapped.fc = item.fc;
		if (item.fs !== undefined) mapped.fs = item.fs;
		if (item.playTime !== undefined) mapped.playTime = item.playTime;
		return mapped;
	});

export const syncV1Route = new Hono<AppEnv>();

function isWebClient(c: Context<AppEnv>) {
	const client = c.req.header("x-maimaid-client");
	return client?.trim().toLowerCase() === "web";
}

syncV1Route.post("/sync:push", authRequired, standardValidator("json", pushSchema, validationHook), async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const body = c.req.valid("json");
	const webClient = isWebClient(c);
	const syncService = c.var.resolve(SyncService);
	const profileService = c.var.resolve(ProfileService);
	const scoreService = c.var.resolve(ScoreService);
	const prisma = c.var.resolve<PrismaClient>(TOKENS.Prisma);

	const existing = await syncService.findMutation(auth.userId, body.idempotencyKey);
	if (existing) {
		return ok(c, existing.resultJson);
	}

	const result: {
		applied: {
			profiles: number;
			scores: number;
			records: number;
			collections: number;
			collectionItems: number;
		};
		conflicts: Array<{
			profileId: string;
			reason: string;
			serverProfile: unknown;
			collectionId?: string;
			itemId?: string;
			serverEntity?: unknown;
		}>;
		profileVersions: Record<string, string>;
		latestRevision: string;
	} = {
		applied: {
			profiles: 0,
			scores: 0,
			records: 0,
			collections: 0,
			collectionItems: 0,
		},
		conflicts: [],
		profileVersions: {},
		latestRevision: "0",
	};

	const appliedResult = await prisma.$transaction(
		async (transaction) => {
			await transaction.$queryRaw`SELECT "id" FROM "users" WHERE "id" = ${auth.userId}::uuid FOR UPDATE`;
			const replay = await syncService.findMutation(auth.userId, body.idempotencyKey, transaction);
			if (replay) {
				return replay.resultJson;
			}
			const existingProfilesById = new Map(
				(body.profileUpserts.length > 0
					? await transaction.profile.findMany({
							where: {
								id: {
									in: Array.from(new Set(body.profileUpserts.map((item) => item.profileId))),
								},
							},
						})
					: []
				).map((item) => [item.id, item]),
			);
			let successfulActiveProfileId: string | null = null;

			for (const item of body.profileUpserts) {
				const existingProfile = existingProfilesById.get(item.profileId) ?? null;
				if (existingProfile && existingProfile.userId !== auth.userId) {
					result.conflicts.push({
						profileId: item.profileId,
						reason: "forbidden",
						serverProfile: null,
					});
					continue;
				}
				if (existingProfile && !body.forceProfileOverwrite && !item.clientUpdatedAt) {
					result.conflicts.push({
						profileId: item.profileId,
						reason: "server_newer",
						serverProfile: existingProfile,
					});
					continue;
				}
				const payload: Parameters<ProfileService["upsertByClientId"]>[2] = {
					name: item.name,
					server: item.server,
				};
				if (item.isActive !== undefined && !webClient) payload.isActive = item.isActive;
				if (item.playerRating !== undefined) payload.playerRating = item.playerRating;
				if (item.plate !== undefined) payload.plate = item.plate;
				if (item.avatarUrl !== undefined) payload.avatarUrl = item.avatarUrl;
				if (item.dfUsername !== undefined) payload.dfUsername = item.dfUsername;
				if (item.b35Count !== undefined) payload.b35Count = item.b35Count;
				if (item.b15Count !== undefined) payload.b15Count = item.b15Count;
				if (item.b35RecLimit !== undefined) payload.b35RecLimit = item.b35RecLimit;
				if (item.b15RecLimit !== undefined) payload.b15RecLimit = item.b15RecLimit;
				if (item.createdAt !== undefined) payload.createdAt = item.createdAt;

				const profile = await profileService.upsertByClientId(
					auth.userId,
					item.profileId,
					payload,
					body.forceProfileOverwrite ? undefined : item.clientUpdatedAt,
					false,
					transaction,
				);
				if (!profile) {
					const serverProfile = await transaction.profile.findFirst({
						where: { id: item.profileId, userId: auth.userId },
					});
					result.conflicts.push({
						profileId: item.profileId,
						reason: "server_newer",
						serverProfile,
					});
					continue;
				}
				existingProfilesById.set(profile.id, profile);
				if (profile.isActive) successfulActiveProfileId = profile.id;
				result.profileVersions[profile.id] = profile.updatedAt.toISOString();
				await syncService.recordEvent(
					{
						userId: auth.userId,
						profileId: profile.id,
						entityType: "profile",
						entityId: profile.id,
						op: "upsert",
						payload: { updatedAt: profile.updatedAt.toISOString() },
					},
					transaction,
				);
				if (item.avatarUrl !== undefined) {
					await syncService.recordEvent(
						{
							userId: auth.userId,
							profileId: profile.id,
							entityType: "avatar",
							entityId: profile.id,
							op: "upsert",
							payload: { avatarUrl: item.avatarUrl },
						},
						transaction,
					);
				}
				result.applied.profiles += 1;
			}

			if (successfulActiveProfileId) {
				const deactivatedProfiles = await transaction.profile.updateManyAndReturn({
					where: {
						userId: auth.userId,
						isActive: true,
						id: { not: successfulActiveProfileId },
					},
					data: { isActive: false },
				});
				for (const profile of deactivatedProfiles) {
					result.profileVersions[profile.id] = profile.updatedAt.toISOString();
					await syncService.recordEvent(
						{
							userId: auth.userId,
							profileId: profile.id,
							entityType: "profile",
							entityId: profile.id,
							op: "upsert",
							payload: { updatedAt: profile.updatedAt.toISOString() },
						},
						transaction,
					);
				}
			}

			for (const profileId of new Set(body.replaceScoreProfileIds)) {
				await scoreService.requireProfileOwnership(profileId, auth.userId, transaction);
				const deleted = await transaction.bestScore.deleteMany({ where: { profileId } });
				await syncService.recordEvent(
					{
						userId: auth.userId,
						profileId,
						entityType: "best_scores",
						entityId: profileId,
						op: "replace",
						payload: { deleted: deleted.count },
					},
					transaction,
				);
			}

			for (const profileId of new Set(body.replacePlayRecordProfileIds)) {
				await scoreService.requireProfileOwnership(profileId, auth.userId, transaction);
				const deleted = await transaction.playRecord.deleteMany({ where: { profileId } });
				await syncService.recordEvent(
					{
						userId: auth.userId,
						profileId,
						entityType: "play_records",
						entityId: profileId,
						op: "replace",
						payload: { deleted: deleted.count },
					},
					transaction,
				);
			}

			for (const scoreSet of body.scoreUpserts) {
				await scoreService.requireProfileOwnership(scoreSet.profileId, auth.userId, transaction);
				const mapped = mapScoresForUpsert(scoreSet.scores);
				const response = await scoreService.bulkUpsertBestScores(scoreSet.profileId, mapped, "sync_push", transaction);
				result.applied.scores += response.applied.length;
				if (response.applied.length > 0) {
					await syncService.recordEvent(
						{
							userId: auth.userId,
							profileId: scoreSet.profileId,
							entityType: "best_scores",
							entityId: scoreSet.profileId,
							op: "bulk_upsert",
							payload: { count: response.applied.length },
						},
						transaction,
					);
				}
			}

			for (const recordSet of body.playRecordUpserts) {
				await scoreService.requireProfileOwnership(recordSet.profileId, auth.userId, transaction);
				const mapped = mapPlayRecords(removeLegacyDivingFishPlayRecords(recordSet.records));
				if (mapped.length === 0) continue;
				const response = await scoreService.bulkInsertPlayRecords(recordSet.profileId, mapped, "sync_push", transaction);
				result.applied.records += response.created.length;
				if (response.created.length > 0) {
					await syncService.recordEvent(
						{
							userId: auth.userId,
							profileId: recordSet.profileId,
							entityType: "play_records",
							entityId: recordSet.profileId,
							op: "bulk_upsert",
							payload: { count: response.created.length },
						},
						transaction,
					);
				}
			}

			for (const item of body.collectionUpserts) {
				const existing = await transaction.songCollection.findFirst({ where: { id: item.collectionId } });
				if (existing && existing.userId !== auth.userId) {
					result.conflicts.push({ profileId: "", reason: "forbidden", serverProfile: null, collectionId: item.collectionId, serverEntity: existing });
					continue;
				}
				const incomingVersion = item.clientUpdatedAt ?? item.createdAt ?? new Date(0);
				const existingVersion = existing?.clientUpdatedAt ?? existing?.updatedAt;
				if (existing && existingVersion && existingVersion > incomingVersion) {
					result.conflicts.push({ profileId: "", reason: "server_newer", serverProfile: null, collectionId: item.collectionId, serverEntity: existing });
					continue;
				}
				const nextClientUpdatedAt = item.clientUpdatedAt ?? item.createdAt ?? existing?.clientUpdatedAt ?? existing?.updatedAt;
				const collection = await transaction.songCollection.upsert({
					where: { id: item.collectionId },
					create: {
						id: item.collectionId,
						userId: auth.userId,
						name: item.name,
						sortIndex: item.sortIndex,
						...(item.createdAt === undefined ? {} : { createdAt: item.createdAt }),
						clientUpdatedAt: item.clientUpdatedAt ?? item.createdAt ?? new Date(),
						...(item.deletedAt === undefined ? {} : { deletedAt: item.deletedAt }),
					},
					update: {
						name: item.name.trim(),
						sortIndex: item.sortIndex,
						...(nextClientUpdatedAt === undefined ? {} : { clientUpdatedAt: nextClientUpdatedAt }),
						deletedAt: item.deletedAt ?? null,
					},
				});
				result.applied.collections += 1;
				await syncService.recordEvent({
					userId: auth.userId,
					entityType: "song_collection",
					entityId: collection.id,
					op: collection.deletedAt ? "delete" : "upsert",
					payload: { updatedAt: collection.updatedAt.toISOString() },
				}, transaction);
			}

			for (const item of body.collectionItemUpserts) {
				const collection = await transaction.songCollection.findFirst({ where: { id: item.collectionId, userId: auth.userId } });
				if (!collection) {
					result.conflicts.push({ profileId: "", reason: "collection_not_found", serverProfile: null, collectionId: item.collectionId, itemId: item.itemId });
					continue;
				}
				const existingById = await transaction.songCollectionItem.findUnique({
					where: { id: item.itemId },
					include: { collection: { select: { userId: true } } },
				});
				if (existingById && existingById.collection.userId !== auth.userId) {
					result.conflicts.push({ profileId: "", reason: "forbidden", serverProfile: null, collectionId: item.collectionId, itemId: item.itemId });
					continue;
				}
				if (existingById && existingById.collectionId !== item.collectionId) {
					result.conflicts.push({ profileId: "", reason: "item_collection_mismatch", serverProfile: null, collectionId: item.collectionId, itemId: item.itemId, serverEntity: existingById });
					continue;
				}
				const existingByKey = await transaction.songCollectionItem.findFirst({
					where: {
						collectionId: item.collectionId,
						songId: item.songId.trim(),
						chartType: item.chartType,
						difficulty: item.difficulty,
					},
				});
				if (existingByKey && existingById && existingByKey.id !== existingById.id) {
					result.conflicts.push({
						profileId: "",
						reason: "item_key_conflict",
						serverProfile: null,
						collectionId: item.collectionId,
						itemId: item.itemId,
						serverEntity: existingByKey,
					});
					continue;
				}
				const existing = existingByKey ?? existingById;
				const incomingVersion = item.clientUpdatedAt ?? item.createdAt ?? new Date(0);
				const existingVersion = existing?.clientUpdatedAt ?? existing?.updatedAt;
				if (existing && existingVersion && existingVersion > incomingVersion) {
					result.conflicts.push({ profileId: "", reason: "server_newer", serverProfile: null, collectionId: item.collectionId, itemId: item.itemId, serverEntity: existing });
					continue;
				}
				const nextClientUpdatedAt = item.clientUpdatedAt ?? item.createdAt ?? existing?.clientUpdatedAt ?? existing?.updatedAt;
				const stored = existing
					? await transaction.songCollectionItem.update({
						where: { id: existing.id },
						data: {
							songId: item.songId.trim(),
							chartType: item.chartType,
							difficulty: item.difficulty,
							position: item.position,
							...(nextClientUpdatedAt === undefined ? {} : { clientUpdatedAt: nextClientUpdatedAt }),
							deletedAt: item.deletedAt ?? null,
						},
					})
					: await transaction.songCollectionItem.create({
						data: {
							id: item.itemId,
							collectionId: item.collectionId,
							songId: item.songId.trim(),
							chartType: item.chartType,
							difficulty: item.difficulty,
							position: item.position,
							...(item.createdAt === undefined ? {} : { createdAt: item.createdAt }),
							clientUpdatedAt: item.clientUpdatedAt ?? item.createdAt ?? new Date(),
							...(item.deletedAt === undefined ? {} : { deletedAt: item.deletedAt }),
						},
					});
				result.applied.collectionItems += 1;
				await syncService.recordEvent({
					userId: auth.userId,
					entityType: "song_collection_item",
					entityId: stored.id,
					op: stored.deletedAt ? "delete" : "upsert",
					payload: { collectionId: stored.collectionId, updatedAt: stored.updatedAt.toISOString() },
				}, transaction);
			}

			// Validate the committed post-merge state. Running this after LWW checks
			// lets stale device mutations resolve as conflicts instead of consuming
			// capacity or being rejected by a limit they would not change.
			const activeCollectionCount = await transaction.songCollection.count({
				where: { userId: auth.userId, deletedAt: null },
			});
			if (activeCollectionCount > MAX_COLLECTIONS_PER_USER) {
				throw new AppError(409, "collection_limits_exceeded", `An account can contain at most ${MAX_COLLECTIONS_PER_USER} collections.`, {
					limit: MAX_COLLECTIONS_PER_USER,
					kind: "collections",
				});
			}
			const activeItemWhere = {
				deletedAt: null,
				collection: { userId: auth.userId, deletedAt: null },
			};
			const activeItemCount = await transaction.songCollectionItem.count({ where: activeItemWhere });
			if (activeItemCount > MAX_ITEMS_PER_USER) {
				throw new AppError(409, "collection_limits_exceeded", `An account can contain at most ${MAX_ITEMS_PER_USER} entries.`, {
					limit: MAX_ITEMS_PER_USER,
					kind: "collection_items_total",
				});
			}
			const itemCounts = await transaction.songCollectionItem.groupBy({
				by: ["collectionId"],
				where: activeItemWhere,
				_count: { _all: true },
			});
			const oversizedCollection = itemCounts.find((entry) => entry._count._all > MAX_ITEMS_PER_COLLECTION);
			if (oversizedCollection) {
				throw new AppError(409, "collection_limits_exceeded", `A collection can contain at most ${MAX_ITEMS_PER_COLLECTION} entries.`, {
					limit: MAX_ITEMS_PER_COLLECTION,
					kind: "collection_items",
					collectionId: oversizedCollection.collectionId,
				});
			}

			const latestEvent = await transaction.syncEvent.findFirst({
				where: { userId: auth.userId },
				orderBy: { revision: "desc" },
			});
			result.latestRevision = latestEvent ? latestEvent.revision.toString() : "0";

			await syncService.saveMutationResult(
				auth.userId,
				body.idempotencyKey,
				result as unknown as Record<string, unknown>,
				transaction,
			);
			return result;
		},
		{ maxWait: 10_000, timeout: 120_000 },
	);

	return ok(c, appliedResult);
});

syncV1Route.get("/sync:pull", authRequired, standardValidator("query", pullQuerySchema, validationHook), async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const syncService = c.var.resolve(SyncService);
	const prisma = c.var.resolve<PrismaClient>(TOKENS.Prisma);
	const query = c.req.valid("query");
	const listInput: Parameters<SyncService["listEvents"]>[0] = {
		userId: auth.userId,
		sinceRevision: query.sinceRevision,
		limit: query.limit,
	};
	if (query.profileId) {
		listInput.profileId = query.profileId;
	}
	const events = await syncService.listEvents(listInput);
	const latestRevision = events.length > 0 ? events[events.length - 1]!.revision.toString() : query.sinceRevision.toString();

	const shouldIncludeSnapshot = query.includeSnapshot ?? query.sinceRevision === 0n;
	let snapshot: Awaited<ReturnType<SyncService["buildSnapshot"]>> = {
		profiles: [],
		scores: [],
		records: [],
		collections: [],
	};
	if (shouldIncludeSnapshot) {
		const profileIds = new Set<string>();
		if (query.sinceRevision === 0n && !query.profileId) {
			const profiles = await prisma.profile.findMany({
				where: { userId: auth.userId },
				select: { id: true },
			});
			for (const profile of profiles) {
				profileIds.add(profile.id);
			}
		}
		if (query.profileId) {
			profileIds.add(query.profileId);
		}
		for (const event of events) {
			if (event.profileId) {
				profileIds.add(event.profileId);
			}
		}
		snapshot = await syncService.buildSnapshot(auth.userId, Array.from(profileIds));
	}

	return ok(c, {
		events,
		latestRevision,
		hasMore: events.length >= query.limit,
		snapshotIncluded: shouldIncludeSnapshot,
		snapshot,
	});
});
