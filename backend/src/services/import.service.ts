import type { PrismaClient } from "@prisma/client";
import { inject, injectable } from "tsyringe";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { ScoreService } from "./score.service.js";
import { SyncService } from "./sync.service.js";
import { difficultyByLevelIndex, lxnsSongIdToLocal, normalizeChartType } from "../utils/compat.js";

type DivingFishRecord = {
	achievements: number;
	title: string;
	type: string;
	level_index: number;
	fc?: string | null;
	fs?: string | null;
	dx_score?: number | null;
	dxScore?: number | null;
	song_id?: number | null;
};

type DivingFishResponse = {
	username?: string;
	nickname?: string;
	rating?: number;
	plate?: string | null;
	charts?: {
		dx?: DivingFishRecord[];
		sd?: DivingFishRecord[];
	};
	message?: string;
};

type DivingFishRecordsResponse = {
	username?: string;
	nickname?: string;
	rating?: number;
	plate?: string | null;
	records?: DivingFishRecord[];
	message?: string;
};

type LxnsScore = {
	id: number;
	song_name: string;
	level_index: number;
	type: string;
	achievements: number;
	fc?: string | null;
	fs?: string | null;
	dx_score: number;
	play_time?: string | null;
};

type LxnsResponse = {
	success: boolean;
	code?: number;
	message?: string;
	data?: LxnsScore[];
};

type LxnsPlayerResponse = {
	success: boolean;
	code?: number;
	message?: string;
	data?: {
		name?: string | null;
		rating?: number | null;
		trophy?: {
			name?: string | null;
		} | null;
	} | null;
};

type LxnsTokenData = {
	access_token?: string;
	refresh_token?: string;
};

type LxnsTokenResponse = {
	success?: boolean;
	data?: LxnsTokenData | null;
	message?: string;
};

export type TransformedImportRecord = {
	source: "df" | "lxns";
	sheetKey: string | null;
	songIdentifier: string | null;
	songId: number | null;
	title: string;
	chartType: "std" | "dx" | "utage";
	difficulty: string;
	levelIndex: number;
	achievements: number;
	rank: string;
	dxScore: number;
	fc: string | null;
	fs: string | null;
	playTime: string | null;
};

export type TransformedImportResult = {
	provider: "df" | "lxns";
	fetchedCount: number;
	mappedCount: number;
	player: {
		name: string | null;
		rating: number | null;
		plate: string | null;
	} | null;
	records: TransformedImportRecord[];
};

type CatalogMappingInput = {
	songId: number | null;
	title: string;
	chartType: "standard" | "dx" | "utage";
	difficulty: string;
};

type CatalogMappingResult = {
	songIdentifier: string | null;
	songId: number | null;
	sheetKey: string | null;
};

type CatalogSheetCandidate = {
	songIdentifier: string;
	chartType: string;
	difficulty: string;
	songId: number;
	song: {
		songId: number;
		title: string;
	} | null;
};

@injectable()
export class ImportService {
	private readonly lxnsClientId = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338";
	private readonly lxnsRedirectUri = "urn:ietf:wg:oauth:2.0:oob";

	constructor(
		@inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
		@inject(TOKENS.ScoreService) private readonly scoreService: ScoreService,
		@inject(TOKENS.SyncService) private readonly syncService: SyncService,
	) {}

	async transformFromDivingFish(input: {
		username?: string;
		qq?: string;
		importToken?: string;
	}): Promise<TransformedImportResult> {
		const importToken = input.importToken?.trim();
		if (importToken) {
			const response = await fetch("https://www.diving-fish.com/api/maimaidxprober/player/records", {
				method: "GET",
				headers: {
					"Import-Token": importToken,
				},
			});
			const payload = (await response.json()) as DivingFishRecordsResponse;
			if (!response.ok || !Array.isArray(payload.records)) {
				throw new AppError(400, "df_import_failed", payload.message ?? "Failed to import from Diving Fish.");
			}

			const normalizedRecords = payload.records.map((record) => {
				const backendChartType = this.normalizeBackendChartType(record.type);
				const difficulty = difficultyByLevelIndex(record.level_index) ?? "basic";
				// DF song IDs are identical to local IDs — no conversion needed
				const localSongId = this.parseProviderSongId(record.song_id);
				return {
					record,
					backendChartType,
					difficulty,
					localSongId,
				};
			});
			const mappings = await this.resolveCatalogMappings(
				normalizedRecords.map((item) => ({
					songId: item.localSongId,
					title: item.record.title,
					chartType: item.backendChartType,
					difficulty: item.difficulty,
				})),
			);

			const transformed: TransformedImportRecord[] = normalizedRecords.map((item, index) => {
				const mapped = mappings[index] ?? {
					songIdentifier: null,
					songId: item.localSongId,
					sheetKey: null,
				};
				return {
					source: "df",
					sheetKey: mapped.sheetKey,
					songIdentifier: mapped.songIdentifier,
					songId: mapped.songId ?? item.localSongId,
					title: item.record.title,
					chartType: this.toAppChartType(item.backendChartType),
					difficulty: item.difficulty,
					levelIndex: item.record.level_index,
					achievements: item.record.achievements,
					rank: this.rankByAchievements(item.record.achievements),
					dxScore: item.record.dxScore ?? item.record.dx_score ?? 0,
					fc: this.normalizeProgress(item.record.fc),
					fs: this.normalizeProgress(item.record.fs),
					playTime: null,
				};
			});

			return {
				provider: "df",
				fetchedCount: payload.records.length,
				mappedCount: transformed.filter((item) => item.sheetKey !== null).length,
				player: {
					name: payload.nickname ?? payload.username ?? input.username ?? input.qq ?? null,
					rating: typeof payload.rating === "number" ? payload.rating : null,
					plate: payload.plate ?? null,
				},
				records: transformed,
			};
		}

		const requestBody: Record<string, unknown> = {};
		if (input.qq) {
			requestBody.qq = input.qq;
		} else if (input.username) {
			requestBody.username = input.username;
		} else {
			throw new AppError(400, "invalid_request", "username or qq is required.");
		}
		requestBody.b50 = true;

		const response = await fetch("https://www.diving-fish.com/api/maimaidxprober/query/player", {
			method: "POST",
			headers: { "Content-Type": "application/json" },
			body: JSON.stringify(requestBody),
		});
		const payload = (await response.json()) as DivingFishResponse;
		if (!response.ok || !payload.charts) {
			throw new AppError(400, "df_import_failed", payload.message ?? "Failed to import from Diving Fish.");
		}

		const allRecords = [...(payload.charts.dx ?? []), ...(payload.charts.sd ?? [])];
		const normalizedRecords = allRecords.map((record) => {
			const backendChartType = this.normalizeBackendChartType(record.type);
			const difficulty = difficultyByLevelIndex(record.level_index) ?? "basic";
			return {
				record,
				backendChartType,
				difficulty,
			};
		});
		const mappings = await this.resolveCatalogMappings(
			normalizedRecords.map((item) => ({
				songId: null,
				title: item.record.title,
				chartType: item.backendChartType,
				difficulty: item.difficulty,
			})),
		);

		const transformed: TransformedImportRecord[] = normalizedRecords.map((item, index) => {
			const mapped = mappings[index] ?? {
				songIdentifier: null,
				songId: null,
				sheetKey: null,
			};
			return {
				source: "df",
				sheetKey: mapped.sheetKey,
				songIdentifier: mapped.songIdentifier,
				songId: mapped.songId,
				title: item.record.title,
				chartType: this.toAppChartType(item.backendChartType),
				difficulty: item.difficulty,
				levelIndex: item.record.level_index,
				achievements: item.record.achievements,
				rank: this.rankByAchievements(item.record.achievements),
				dxScore: item.record.dx_score ?? 0,
				fc: this.normalizeProgress(item.record.fc),
				fs: this.normalizeProgress(item.record.fs),
				playTime: null,
			};
		});

		return {
			provider: "df",
			fetchedCount: allRecords.length,
			mappedCount: transformed.filter((item) => item.sheetKey !== null).length,
			player: {
				name: payload.nickname ?? payload.username ?? input.username ?? input.qq ?? null,
				rating: typeof payload.rating === "number" ? payload.rating : null,
				plate: payload.plate ?? null,
			},
			records: transformed,
		};
	}

	async transformFromLxns(input: { accessToken: string }): Promise<TransformedImportResult> {
		const [scoresResponse, playerResponse] = await Promise.all([
			fetch("https://maimai.lxns.net/api/v0/user/maimai/player/scores", {
				method: "GET",
				headers: {
					Authorization: `Bearer ${input.accessToken}`,
				},
			}),
			fetch("https://maimai.lxns.net/api/v0/user/maimai/player", {
				method: "GET",
				headers: {
					Authorization: `Bearer ${input.accessToken}`,
				},
			}),
		]);

		const scoresPayload = (await scoresResponse.json()) as LxnsResponse;
		if (!scoresResponse.ok || !scoresPayload.success || !scoresPayload.data) {
			throw new AppError(400, "lxns_import_failed", scoresPayload.message ?? "Failed to import from LXNS.");
		}

		let player: TransformedImportResult["player"] = null;
		if (playerResponse.ok) {
			const playerPayload = (await playerResponse.json()) as LxnsPlayerResponse;
			if (playerPayload.success && playerPayload.data) {
				player = {
					name: playerPayload.data.name ?? null,
					rating: playerPayload.data.rating ?? null,
					plate: playerPayload.data.trophy?.name ?? null,
				};
			}
		}

		const normalizedScores = scoresPayload.data.map((score) => {
			const backendChartType = this.normalizeBackendChartType(score.type);
			const difficulty = difficultyByLevelIndex(score.level_index) ?? "basic";
			// LXNS uses a single id per song; DX charts need +10000 to match local IDs
			const localSongId = lxnsSongIdToLocal(score.id, score.type);
			return {
				score,
				localSongId,
				backendChartType,
				difficulty,
			};
		});
		const mappings = await this.resolveCatalogMappings(
			normalizedScores.map((item) => ({
				songId: item.localSongId,
				title: item.score.song_name,
				chartType: item.backendChartType,
				difficulty: item.difficulty,
			})),
		);

		const transformed: TransformedImportRecord[] = normalizedScores.map((item, index) => {
			const mapped = mappings[index] ?? {
				songIdentifier: null,
				songId: item.localSongId,
				sheetKey: null,
			};
			return {
				source: "lxns",
				sheetKey: mapped.sheetKey,
				songIdentifier: mapped.songIdentifier,
				songId: mapped.songId ?? item.localSongId,
				title: item.score.song_name,
				chartType: this.toAppChartType(item.backendChartType),
				difficulty: item.difficulty,
				levelIndex: item.score.level_index,
				achievements: item.score.achievements,
				rank: this.rankByAchievements(item.score.achievements),
				dxScore: item.score.dx_score,
				fc: this.normalizeProgress(item.score.fc),
				fs: this.normalizeProgress(item.score.fs),
				playTime: item.score.play_time ?? null,
			};
		});

		return {
			provider: "lxns",
			fetchedCount: scoresPayload.data.length,
			mappedCount: transformed.filter((item) => item.sheetKey !== null).length,
			player,
			records: transformed,
		};
	}

	async importFromDivingFish(input: {
		userId: string;
		profileId: string;
		username?: string;
		qq?: string;
		importToken?: string;
	}) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
		const run = await this.prisma.importRun.create({
			data: {
				profileId: input.profileId,
				provider: "df",
				status: "pending",
			},
		});

		try {
			const transformInput: Parameters<ImportService["transformFromDivingFish"]>[0] = {};
			if (input.username !== undefined) {
				transformInput.username = input.username;
			}
			if (input.qq !== undefined) {
				transformInput.qq = input.qq;
			}
			if (input.importToken !== undefined) {
				transformInput.importToken = input.importToken;
			}
			const transformed = await this.transformFromDivingFish(transformInput);
			const mapped = transformed.records.map((record) => {
				const row: {
					songIdentifier?: string;
					songId?: number;
					title: string;
					type: string;
					levelIndex: number;
					difficulty: string;
					achievements: number;
					dxScore: number;
					rank: string;
					fc: string | null;
					fs: string | null;
					sourcePayload: unknown;
				} = {
					title: record.title,
					type: record.chartType,
					levelIndex: record.levelIndex,
					difficulty: record.difficulty,
					achievements: record.achievements,
					dxScore: record.dxScore,
					rank: record.rank,
					fc: record.fc,
					fs: record.fs,
					sourcePayload: record,
				};
				if (record.songIdentifier) {
					row.songIdentifier = record.songIdentifier;
				}
				if (record.songId !== null) {
					row.songId = record.songId;
				}
				return row;
			});

			const upsertResult = await this.scoreService.bulkUpsertBestScores(input.profileId, mapped, "df_import");
			const recordResult = await this.scoreService.bulkInsertPlayRecords(
				input.profileId,
				mapped.map((item) => ({
					...item,
					playTime: new Date(),
				})),
				"df_import",
			);

			await this.prisma.importRawPayload.create({
				data: {
					importRunId: run.id,
					payloadType: "df.transformed.records",
					payloadJson: {
						fetchedCount: transformed.fetchedCount,
						mappedCount: transformed.mappedCount,
						records: transformed.records,
					},
				},
			});

			await this.prisma.importRun.update({
				where: { id: run.id },
				data: {
					status: "success",
					finishedAt: new Date(),
					summaryJson: {
						fetched: transformed.fetchedCount,
						upserted: upsertResult.applied.length,
						skipped: upsertResult.skipped.length,
						recordsInserted: recordResult.created.length,
					},
				},
			});
			await this.syncService.recordEvent({
				userId: input.userId,
				profileId: input.profileId,
				entityType: "import",
				entityId: run.id,
				op: "imported",
				payload: {
					provider: "df",
					fetched: transformed.fetchedCount,
					upserted: upsertResult.applied.length,
					recordsInserted: recordResult.created.length,
				},
			});
			await this.syncService.recordEvent({
				userId: input.userId,
				profileId: input.profileId,
				entityType: "best_scores",
				entityId: input.profileId,
				op: "bulk_upsert",
				payload: {
					source: "df_import",
					count: upsertResult.applied.length,
				},
			});
			await this.syncService.recordEvent({
				userId: input.userId,
				profileId: input.profileId,
				entityType: "play_records",
				entityId: input.profileId,
				op: "bulk_upsert",
				payload: {
					source: "df_import",
					count: recordResult.created.length,
				},
			});

			return {
				importRunId: run.id,
				fetchedCount: transformed.fetchedCount,
				upsertedCount: upsertResult.applied.length,
				skippedCount: upsertResult.skipped.length,
			};
		} catch (error) {
			await this.prisma.importRun.update({
				where: { id: run.id },
				data: {
					status: "failed",
					finishedAt: new Date(),
					errorMessage: error instanceof Error ? error.message : "unknown_error",
				},
			});
			throw error;
		}
	}

	async exchangeLxnsAuthorizationCode(input: { code: string; codeVerifier: string }) {
		const code = input.code.trim();
		const codeVerifier = input.codeVerifier.trim();
		if (!code || !codeVerifier) {
			throw new AppError(400, "invalid_request", "LXNS authorization code and code verifier are required.");
		}

		const body = new URLSearchParams({
			grant_type: "authorization_code",
			client_id: this.lxnsClientId,
			redirect_uri: this.lxnsRedirectUri,
			code,
			code_verifier: codeVerifier,
		});

		const response = await fetch("https://maimai.lxns.net/api/v0/oauth/token", {
			method: "POST",
			headers: {
				"Content-Type": "application/x-www-form-urlencoded",
			},
			body: body.toString(),
		});
		const payload = (await response.json()) as LxnsTokenResponse;
		const accessToken = payload.data?.access_token?.trim() ?? "";
		const refreshToken = payload.data?.refresh_token?.trim() ?? "";
		if (!response.ok || !accessToken || !refreshToken) {
			throw new AppError(400, "lxns_oauth_failed", payload.message ?? "Failed to exchange LXNS authorization code.");
		}

		return {
			accessToken,
			refreshToken,
		};
	}

	async importFromLxns(input: { userId: string; profileId: string; accessToken: string }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);

		const run = await this.prisma.importRun.create({
			data: {
				profileId: input.profileId,
				provider: "lxns",
				status: "pending",
			},
		});

		try {
			const transformed = await this.transformFromLxns({
				accessToken: input.accessToken,
			});
			const mapped = transformed.records.map((record) => {
				const row: {
					songIdentifier?: string;
					songId?: number;
					title: string;
					type: string;
					levelIndex: number;
					difficulty: string;
					achievements: number;
					dxScore: number;
					rank: string;
					fc: string | null;
					fs: string | null;
					sourcePayload: unknown;
				} = {
					title: record.title,
					type: record.chartType,
					levelIndex: record.levelIndex,
					difficulty: record.difficulty,
					achievements: record.achievements,
					dxScore: record.dxScore,
					rank: record.rank,
					fc: record.fc,
					fs: record.fs,
					sourcePayload: record,
				};
				if (record.songIdentifier) {
					row.songIdentifier = record.songIdentifier;
				}
				if (record.songId !== null) {
					row.songId = record.songId;
				}
				return row;
			});

			const upsertResult = await this.scoreService.bulkUpsertBestScores(input.profileId, mapped, "lxns_import");
			const recordResult = await this.scoreService.bulkInsertPlayRecords(
				input.profileId,
				mapped.map((item, index) => ({
					...item,
					playTime: transformed.records[index]?.playTime ?? new Date(),
				})),
				"lxns_import",
			);

			await this.prisma.importRawPayload.create({
				data: {
					importRunId: run.id,
					payloadType: "lxns.transformed.records",
					payloadJson: {
						fetchedCount: transformed.fetchedCount,
						mappedCount: transformed.mappedCount,
						records: transformed.records,
					},
				},
			});

			await this.prisma.importRun.update({
				where: { id: run.id },
				data: {
					status: "success",
					finishedAt: new Date(),
					summaryJson: {
						fetched: transformed.fetchedCount,
						upserted: upsertResult.applied.length,
						skipped: upsertResult.skipped.length,
						recordsInserted: recordResult.created.length,
					},
				},
			});
			await this.syncService.recordEvent({
				userId: input.userId,
				profileId: input.profileId,
				entityType: "import",
				entityId: run.id,
				op: "imported",
				payload: {
					provider: "lxns",
					fetched: transformed.fetchedCount,
					upserted: upsertResult.applied.length,
					recordsInserted: recordResult.created.length,
				},
			});
			await this.syncService.recordEvent({
				userId: input.userId,
				profileId: input.profileId,
				entityType: "best_scores",
				entityId: input.profileId,
				op: "bulk_upsert",
				payload: {
					source: "lxns_import",
					count: upsertResult.applied.length,
				},
			});
			await this.syncService.recordEvent({
				userId: input.userId,
				profileId: input.profileId,
				entityType: "play_records",
				entityId: input.profileId,
				op: "bulk_upsert",
				payload: {
					source: "lxns_import",
					count: recordResult.created.length,
				},
			});

			return {
				importRunId: run.id,
				fetchedCount: transformed.fetchedCount,
				upsertedCount: upsertResult.applied.length,
				skippedCount: upsertResult.skipped.length,
			};
		} catch (error) {
			await this.prisma.importRun.update({
				where: { id: run.id },
				data: {
					status: "failed",
					finishedAt: new Date(),
					errorMessage: error instanceof Error ? error.message : "unknown_error",
				},
			});
			throw error;
		}
	}

	private normalizeBackendChartType(input: string): "standard" | "dx" | "utage" {
		const normalized = normalizeChartType(input) ?? "standard";
		if (normalized === "dx" || normalized === "utage") {
			return normalized;
		}
		return "standard";
	}

	private toAppChartType(input: "standard" | "dx" | "utage"): "std" | "dx" | "utage" {
		if (input === "standard") {
			return "std";
		}
		return input;
	}

	private normalizeProgress(value: string | null | undefined): string | null {
		if (!value) {
			return null;
		}
		const normalized = value.trim();
		if (!normalized) {
			return null;
		}
		return normalized;
	}

	private async resolveCatalogMappings(inputs: CatalogMappingInput[]): Promise<CatalogMappingResult[]> {
		if (inputs.length === 0) {
			return [];
		}

		const songIds = Array.from(
			new Set(inputs.map((item) => item.songId).filter((item): item is number => Boolean(item && item > 0))),
		);
		const chartTypes = Array.from(new Set(inputs.map((item) => item.chartType)));
		const difficulties = Array.from(new Set(inputs.map((item) => item.difficulty)));

		const bySongIdKey = new Map<string, CatalogSheetCandidate>();
		if (songIds.length > 0) {
			const songIdentifierCandidates = songIds.map((item) => String(item));
			const sheetsBySongId = await this.prisma.sheet.findMany({
				where: {
					chartType: {
						in: chartTypes,
					},
					difficulty: {
						in: difficulties,
					},
					OR: [
						{ songId: { in: songIds } },
						{ song: { songId: { in: songIds } } },
						{ songIdentifier: { in: songIdentifierCandidates } },
					],
				},
				select: {
					songIdentifier: true,
					chartType: true,
					difficulty: true,
					songId: true,
					song: {
						select: {
							songId: true,
							title: true,
						},
					},
				},
			});

			for (const sheet of sheetsBySongId) {
				const resolvedSongId = this.extractPositiveSongIdFromCatalogSheet(sheet);
				if (!resolvedSongId) {
					continue;
				}
				const key = this.catalogSongIdKey(resolvedSongId, this.normalizeBackendChartType(sheet.chartType), sheet.difficulty);
				if (!bySongIdKey.has(key)) {
					bySongIdKey.set(key, sheet);
				}
			}
		}

		const resolved: Array<CatalogMappingResult | null> = new Array(inputs.length).fill(null);
		const unresolvedIndexes: number[] = [];
		for (const [index, input] of inputs.entries()) {
			if (input.songId && input.songId > 0) {
				const key = this.catalogSongIdKey(input.songId, input.chartType, input.difficulty);
				const matched = bySongIdKey.get(key);
				if (matched) {
					resolved[index] = this.buildCatalogMappingResult(matched, input.songId);
					continue;
				}
			}
			unresolvedIndexes.push(index);
		}

		if (unresolvedIndexes.length > 0) {
			const unresolvedInputs: CatalogMappingInput[] = [];
			for (const index of unresolvedIndexes) {
				const input = inputs[index];
				if (input) {
					unresolvedInputs.push(input);
				}
			}
			const unresolvedTitles = Array.from(new Set(unresolvedInputs.map((item) => item.title)));
			const unresolvedChartTypes = Array.from(new Set(unresolvedInputs.map((item) => item.chartType)));
			const unresolvedDifficulties = Array.from(new Set(unresolvedInputs.map((item) => item.difficulty)));

			const sheetsByTitle = await this.prisma.sheet.findMany({
				where: {
					chartType: {
						in: unresolvedChartTypes,
					},
					difficulty: {
						in: unresolvedDifficulties,
					},
					song: {
						title: {
							in: unresolvedTitles,
						},
					},
				},
				select: {
					songIdentifier: true,
					chartType: true,
					difficulty: true,
					songId: true,
					song: {
						select: {
							songId: true,
							title: true,
						},
					},
				},
			});

			const byTitleKey = new Map<string, CatalogSheetCandidate>();
			for (const sheet of sheetsByTitle) {
				const title = sheet.song?.title;
				if (!title) {
					continue;
				}
				const key = this.catalogTitleKey(title, this.normalizeBackendChartType(sheet.chartType), sheet.difficulty);
				if (!byTitleKey.has(key)) {
					byTitleKey.set(key, sheet);
				}
			}

			for (const index of unresolvedIndexes) {
				const input = inputs[index];
				if (!input) {
					continue;
				}
				const byTitle = byTitleKey.get(this.catalogTitleKey(input.title, input.chartType, input.difficulty));
				resolved[index] = byTitle
					? this.buildCatalogMappingResult(byTitle, input.songId)
					: {
							songIdentifier: null,
							songId: input.songId,
							sheetKey: null,
						};
			}
		}

		return resolved.map((item, index) => {
			if (item) {
				return item;
			}
			const input = inputs[index];
			return {
				songIdentifier: null,
				songId: input ? input.songId : null,
				sheetKey: null,
			};
		});
	}

	private parseProviderSongId(value: number | null | undefined): number | null {
		if (typeof value !== "number" || !Number.isFinite(value)) {
			return null;
		}
		const normalized = Math.trunc(value);
		if (normalized <= 0) {
			return null;
		}
		return normalized;
	}

	private extractPositiveSongIdFromCatalogSheet(sheet: CatalogSheetCandidate): number | null {
		if (sheet.song?.songId && sheet.song.songId > 0) {
			return sheet.song.songId;
		}
		if (sheet.songId > 0) {
			return sheet.songId;
		}
		const numericSongIdentifier = Number(sheet.songIdentifier);
		if (Number.isFinite(numericSongIdentifier) && numericSongIdentifier > 0) {
			return Math.trunc(numericSongIdentifier);
		}
		return null;
	}

	private catalogSongIdKey(songId: number, chartType: "standard" | "dx" | "utage", difficulty: string) {
		return `${songId}:${chartType}:${difficulty}`;
	}

	private catalogTitleKey(title: string, chartType: "standard" | "dx" | "utage", difficulty: string) {
		return `${title}:${chartType}:${difficulty}`;
	}

	private buildCatalogMappingResult(sheet: CatalogSheetCandidate, fallbackSongId: number | null): CatalogMappingResult {
		const appType = this.toAppChartType(this.normalizeBackendChartType(sheet.chartType));
		const resolvedSongId = this.extractPositiveSongIdFromCatalogSheet(sheet);
		return {
			songIdentifier: sheet.songIdentifier,
			songId: resolvedSongId ?? fallbackSongId,
			sheetKey: `${sheet.songIdentifier}_${appType}_${sheet.difficulty}`,
		};
	}

	private rankByAchievements(rate: number): string {
		if (rate >= 100.5) return "SSS+";
		if (rate >= 100.0) return "SSS";
		if (rate >= 99.5) return "SS+";
		if (rate >= 99.0) return "SS";
		if (rate >= 98.0) return "S+";
		if (rate >= 97.0) return "S";
		if (rate >= 94.0) return "AAA";
		if (rate >= 90.0) return "AA";
		if (rate >= 80.0) return "A";
		if (rate >= 75.0) return "BBB";
		if (rate >= 70.0) return "BB";
		if (rate >= 60.0) return "B";
		if (rate >= 50.0) return "C";
		return "D";
	}
}
