import { Prisma, type PrismaClient } from "@prisma/client";
import { inject, injectable } from "tsyringe";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { randomToken, sha256Hex } from "../lib/crypto.js";
import type { Env } from "../env.js";
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

type DivingFishRecordsResponse = {
	username?: string;
	nickname?: string;
	rating?: number;
	plate?: string | null;
	records?: DivingFishRecord[];
	message?: string;
};

type DivingFishTokenResponse = {
	token_type?: string;
	access_token?: string;
	refresh_token?: string;
	expires_in?: number;
	scope?: string;
	sub?: string;
	error?: string;
	error_description?: string;
};

type DivingFishCredentials = {
	accessToken: string;
	refreshToken: string;
	expiresAt: string;
	scope: string;
};

export type DivingFishScoreSyncRecord = {
	title: string;
	chartType: "standard" | "dx";
	levelIndex: number;
	achievements: number;
	dxScore: number;
	fc?: string | null;
	fs?: string | null;
};

const DIVING_FISH_AUTH_BASE_URL = "https://auth.diving-fish.com";
const DIVING_FISH_API_BASE_URL = "https://www.diving-fish.com/api/maimaidxprober";
const DIVING_FISH_REDIRECT_URI = "https://api.rhythmeta.org/v1/imports:divingFishCallback";
const DIVING_FISH_READ_SCOPE = "prober.records.read";
const DIVING_FISH_WRITE_SCOPE = "prober.records.write";
const DIVING_FISH_SCOPE = `${DIVING_FISH_READ_SCOPE} ${DIVING_FISH_WRITE_SCOPE}`;
const DIVING_FISH_OAUTH_SESSION_TTL_MS = 10 * 60_000;

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
		@inject(ScoreService) private readonly scoreService: ScoreService,
		@inject(SyncService) private readonly syncService: SyncService,
		@inject(TOKENS.Env) private readonly env: Env,
	) {}

	async startDivingFishAuthorization(input: { userId: string; profileId: string }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);

		const state = randomToken(32);
		const codeVerifier = randomToken(64);
		const codeChallenge = new Uint8Array(
			await crypto.subtle.digest("SHA-256", new TextEncoder().encode(codeVerifier)),
		).toBase64({ alphabet: "base64url", omitPadding: true });
		const expiresAt = new Date(Date.now() + DIVING_FISH_OAUTH_SESSION_TTL_MS);

		await this.prisma.divingFishOAuthSession.deleteMany({
			where: { profileId: input.profileId },
		});
		const session = await this.prisma.divingFishOAuthSession.create({
			data: {
				profileId: input.profileId,
				stateHash: await sha256Hex(state),
				codeVerifier,
				expiresAt,
			},
		});

		const query = new URLSearchParams({
			response_type: "code",
			client_id: this.env.DIVING_FISH_OAUTH_CLIENT_ID,
			redirect_uri: DIVING_FISH_REDIRECT_URI,
			scope: DIVING_FISH_SCOPE,
			state,
			code_challenge: codeChallenge,
			code_challenge_method: "S256",
		});

		return {
			authorizationId: session.id,
			authorizationUrl: `${DIVING_FISH_AUTH_BASE_URL}/oauth/authorize?${query.toString()}`,
			expiresAt: expiresAt.toISOString(),
		};
	}

	async getDivingFishAuthorizationStatus(input: { userId: string; authorizationId: string }) {
		const session = await this.prisma.divingFishOAuthSession.findFirst({
			where: {
				id: input.authorizationId,
				profile: { userId: input.userId },
			},
			select: {
				status: true,
				errorCode: true,
				expiresAt: true,
			},
		});
		if (!session) {
			throw new AppError(404, "df_oauth_session_not_found", "Diving Fish authorization session was not found.");
		}

		const status = session.status === "pending" && session.expiresAt <= new Date() ? "expired" : session.status;
		return {
			status,
			errorCode: session.errorCode,
			expiresAt: session.expiresAt.toISOString(),
		};
	}

	async getDivingFishBindingStatus(input: { userId: string; profileId: string }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
		const binding = await this.prisma.profileBinding.findUnique({
			where: {
				profileId_provider: {
					profileId: input.profileId,
					provider: "df",
				},
			},
			select: {
				externalUserId: true,
				externalUsername: true,
				credentialJson: true,
				updatedAt: true,
			},
		});
		const credentials = this.parseDivingFishCredentials(binding?.credentialJson);
		return {
			connected: binding !== null,
			canWrite: credentials ? this.hasDivingFishScope(credentials, DIVING_FISH_WRITE_SCOPE) : false,
			externalUserId: binding?.externalUserId ?? null,
			externalUsername: binding?.externalUsername ?? null,
			updatedAt: binding?.updatedAt.toISOString() ?? null,
		};
	}

	async disconnectDivingFish(input: { userId: string; profileId: string }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
		const binding = await this.prisma.profileBinding.findUnique({
			where: {
				profileId_provider: {
					profileId: input.profileId,
					provider: "df",
				},
			},
			select: { credentialJson: true },
		});
		const credentials = this.parseDivingFishCredentials(binding?.credentialJson);
		if (credentials) {
			await this.revokeDivingFishToken(credentials.refreshToken);
		}
		await this.prisma.divingFishOAuthSession.deleteMany({
			where: { profileId: input.profileId },
		});
		await this.prisma.profileBinding.deleteMany({
			where: {
				profileId: input.profileId,
				provider: "df",
			},
		});
		return {
			connected: false,
			canWrite: false,
			externalUserId: null,
			externalUsername: null,
			updatedAt: null,
		};
	}

	async completeDivingFishAuthorization(input: { state?: string; code?: string; error?: string; errorDescription?: string }) {
		const state = input.state?.trim();
		if (!state) {
			throw new AppError(400, "df_oauth_invalid_state", "Diving Fish OAuth state is missing.");
		}

		const session = await this.prisma.divingFishOAuthSession.findUnique({
			where: { stateHash: await sha256Hex(state) },
		});
		if (!session || session.status !== "pending" || session.expiresAt <= new Date()) {
			throw new AppError(400, "df_oauth_invalid_state", "Diving Fish OAuth state is invalid or expired.");
		}

		if (input.error) {
			const claimed = await this.prisma.divingFishOAuthSession.updateMany({
				where: {
					id: session.id,
					status: "pending",
					expiresAt: { gt: new Date() },
				},
				data: { status: "exchanging" },
			});
			if (claimed.count !== 1) {
				throw new AppError(400, "df_oauth_invalid_state", "Diving Fish OAuth state is invalid or expired.");
			}
			await this.prisma.divingFishOAuthSession.update({
				where: { id: session.id },
				data: {
					status: "failed",
					errorCode: input.error,
					completedAt: new Date(),
				},
			});
			throw new AppError(400, input.error, input.errorDescription ?? "Diving Fish authorization was declined.");
		}

		const code = input.code?.trim();
		if (!code) {
			throw new AppError(400, "df_oauth_missing_code", "Diving Fish authorization code is missing.");
		}
		const claimed = await this.prisma.divingFishOAuthSession.updateMany({
			where: {
				id: session.id,
				status: "pending",
				expiresAt: { gt: new Date() },
			},
			data: { status: "exchanging" },
		});
		if (claimed.count !== 1) {
			throw new AppError(400, "df_oauth_invalid_state", "Diving Fish OAuth state is invalid or expired.");
		}

		try {
			const token = await this.requestDivingFishToken({
				grant_type: "authorization_code",
				code,
				redirect_uri: DIVING_FISH_REDIRECT_URI,
				code_verifier: session.codeVerifier,
			});
			const credentials = this.credentialsFromToken(token);
			const bindingUpdate: Prisma.ProfileBindingUpdateInput = {
				credentialJson: credentials as unknown as Prisma.InputJsonObject,
			};
			if (token.sub) bindingUpdate.externalUserId = token.sub;
			await this.prisma.$transaction([
				this.prisma.profileBinding.upsert({
					where: {
						profileId_provider: {
							profileId: session.profileId,
							provider: "df",
						},
					},
					create: {
						profileId: session.profileId,
						provider: "df",
						externalUserId: token.sub ?? null,
						credentialJson: credentials as unknown as Prisma.InputJsonObject,
					},
					update: bindingUpdate,
				}),
				this.prisma.divingFishOAuthSession.update({
					where: { id: session.id },
					data: {
						status: "success",
						errorCode: null,
						completedAt: new Date(),
					},
				}),
			]);
		} catch (error) {
			await this.prisma.divingFishOAuthSession.update({
				where: { id: session.id },
				data: {
					status: "failed",
					errorCode: error instanceof AppError ? error.code : "df_oauth_failed",
					completedAt: new Date(),
				},
			});
			throw error;
		}
	}

	async transformFromDivingFish(input: { accessToken: string }): Promise<TransformedImportResult> {
		const response = await fetch(`${DIVING_FISH_API_BASE_URL}/player/records`, {
			method: "GET",
			headers: {
				Authorization: `Bearer ${input.accessToken}`,
			},
			signal: AbortSignal.timeout(15_000),
		});
		const payload = (await response.json().catch(() => ({}))) as DivingFishRecordsResponse;
		if (!response.ok || !Array.isArray(payload.records)) {
			const status = response.status === 401 ? 401 : response.status === 429 ? 429 : 400;
			const code =
				response.status === 401
					? "df_authorization_required"
					: response.status === 429
						? "df_rate_limited"
						: "df_import_failed";
			throw new AppError(status, code, payload.message ?? "Failed to import from Diving Fish.");
		}

		const normalizedRecords = payload.records.map((record) => {
			const backendChartType = this.normalizeBackendChartType(record.type);
			const difficulty = difficultyByLevelIndex(record.level_index) ?? "basic";
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
				name: payload.nickname ?? payload.username ?? null,
				rating: typeof payload.rating === "number" ? payload.rating : null,
				plate: payload.plate ?? null,
			},
			records: transformed,
		};
	}

	async syncScoresToDivingFish(input: { userId: string; profileId: string; records: DivingFishScoreSyncRecord[] }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
		const profile = await this.prisma.profile.findUnique({
			where: { id: input.profileId },
			select: { server: true },
		});
		if (profile?.server.toLowerCase() !== "cn") {
			throw new AppError(409, "df_sync_cn_profile_required", "Diving Fish score sync requires a CN server profile.");
		}
		const records = input.records.map((record) => ({
			title: record.title,
			level_index: record.levelIndex,
			achievements: record.achievements,
			type: record.chartType === "dx" ? "DX" : "SD",
			dxScore: record.dxScore,
			fc: record.fc ?? null,
			fs: record.fs ?? null,
		}));

		await this.withDivingFishAccessToken(input.profileId, DIVING_FISH_WRITE_SCOPE, async (accessToken) => {
			const response = await fetch(`${DIVING_FISH_API_BASE_URL}/player/update_records`, {
				method: "POST",
				headers: {
					Authorization: `Bearer ${accessToken}`,
					"Content-Type": "application/json",
				},
				body: JSON.stringify(records),
				signal: AbortSignal.timeout(15_000),
			});
			if (!response.ok) {
				const payload = (await response.json().catch(() => ({}))) as DivingFishRecordsResponse;
				const status = response.status === 401 ? 401 : response.status === 403 ? 403 : response.status === 429 ? 429 : 400;
				const code =
					response.status === 401
						? "df_authorization_required"
						: response.status === 403
							? "df_write_scope_required"
							: response.status === 429
								? "df_rate_limited"
								: "df_sync_failed";
				throw new AppError(status, code, payload.message ?? "Failed to sync scores to Diving Fish.");
			}
		});

		return { syncedCount: records.length };
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

	async importFromDivingFish(input: { userId: string; profileId: string }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
		const run = await this.prisma.importRun.create({
			data: {
				profileId: input.profileId,
				provider: "df",
				status: "pending",
			},
		});

		try {
			const transformed = await this.withDivingFishAccessToken(input.profileId, DIVING_FISH_READ_SCOPE, (accessToken) =>
				this.transformFromDivingFish({ accessToken }),
			);
			if (transformed.player) {
				const profileUpdate: Prisma.ProfileUpdateInput = {};
				if (transformed.player.name) profileUpdate.dfUsername = transformed.player.name;
				if (transformed.player.rating !== null) profileUpdate.playerRating = transformed.player.rating;
				if (transformed.player.plate !== null) profileUpdate.plate = transformed.player.plate;
				await this.prisma.profile.update({
					where: { id: input.profileId },
					data: profileUpdate,
				});
				if (transformed.player.name) {
					await this.prisma.profileBinding.update({
						where: {
							profileId_provider: {
								profileId: input.profileId,
								provider: "df",
							},
						},
						data: { externalUsername: transformed.player.name },
					});
				}
			}
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

	private async withDivingFishAccessToken<T>(
		profileId: string,
		requiredScope: string,
		operation: (accessToken: string) => Promise<T>,
	): Promise<T> {
		const accessToken = await this.divingFishAccessToken(profileId, requiredScope);
		try {
			return await operation(accessToken);
		} catch (error) {
			if (!(error instanceof AppError) || error.code !== "df_authorization_required") {
				throw error;
			}
		}

		const refreshedAccessToken = await this.divingFishAccessToken(profileId, requiredScope, true);
		try {
			return await operation(refreshedAccessToken);
		} catch (error) {
			if (!(error instanceof AppError) || error.code !== "df_authorization_required") {
				throw error;
			}
			await this.prisma.profileBinding.deleteMany({
				where: { profileId, provider: "df" },
			});
			throw new AppError(409, "df_authorization_required", "Diving Fish authorization expired. Connect the account again.");
		}
	}

	private async divingFishAccessToken(profileId: string, requiredScope: string, forceRefresh = false): Promise<string> {
		const credentials = await this.prisma.$transaction(
			async (transaction) => {
				await transaction.$queryRaw(
					Prisma.sql`select "id" from "profile_bindings" where "profileId" = ${profileId}::uuid and "provider" = 'df'::"BindingProvider" for update`,
				);
				const binding = await transaction.profileBinding.findUnique({
					where: {
						profileId_provider: {
							profileId,
							provider: "df",
						},
					},
				});
				const credentials = this.parseDivingFishCredentials(binding?.credentialJson);
				if (!binding || !credentials) {
					throw new AppError(409, "df_authorization_required", "Connect a Diving Fish account before importing.");
				}
				if (!forceRefresh && new Date(credentials.expiresAt).getTime() > Date.now() + 30_000) {
					return credentials;
				}

				try {
					const token = await this.requestDivingFishToken({
						grant_type: "refresh_token",
						refresh_token: credentials.refreshToken,
					});
					const refreshed = this.credentialsFromToken(token, credentials.scope);
					await transaction.profileBinding.update({
						where: { id: binding.id },
						data: { credentialJson: refreshed as unknown as Prisma.InputJsonObject },
					});
					return refreshed;
				} catch (error) {
					if (error instanceof AppError && error.code === "df_oauth_invalid_grant") {
						await transaction.profileBinding.delete({ where: { id: binding.id } });
						throw new AppError(
							409,
							"df_authorization_required",
							"Diving Fish authorization expired. Connect the account again.",
						);
					}
					throw error;
				}
			},
			{ maxWait: 5_000, timeout: 20_000 },
		);
		this.requireDivingFishScope(credentials, requiredScope);
		return credentials.accessToken;
	}

	private requireDivingFishScope(credentials: DivingFishCredentials, requiredScope: string): void {
		if (!this.hasDivingFishScope(credentials, requiredScope)) {
			const code = requiredScope === DIVING_FISH_WRITE_SCOPE ? "df_write_scope_required" : "df_read_scope_required";
			throw new AppError(403, code, `Diving Fish authorization is missing the ${requiredScope} scope.`);
		}
	}

	private hasDivingFishScope(credentials: DivingFishCredentials, requiredScope: string): boolean {
		return new Set(credentials.scope.split(/\s+/u).filter(Boolean)).has(requiredScope);
	}

	private async requestDivingFishToken(parameters: Record<string, string>): Promise<DivingFishTokenResponse> {
		const body = new URLSearchParams({
			...parameters,
			client_id: this.env.DIVING_FISH_OAUTH_CLIENT_ID,
		});
		if (this.env.DIVING_FISH_OAUTH_CLIENT_SECRET) {
			body.set("client_secret", this.env.DIVING_FISH_OAUTH_CLIENT_SECRET);
		}

		const response = await fetch(`${DIVING_FISH_AUTH_BASE_URL}/oauth/token`, {
			method: "POST",
			headers: { "Content-Type": "application/x-www-form-urlencoded" },
			body: body.toString(),
			signal: AbortSignal.timeout(10_000),
		});
		const token = (await response.json().catch(() => ({}))) as DivingFishTokenResponse;
		if (!response.ok || !token.access_token || !token.refresh_token || !token.expires_in) {
			const providerCode = token.error?.trim() || "token_exchange_failed";
			throw new AppError(400, `df_oauth_${providerCode}`, token.error_description ?? "Diving Fish token exchange failed.");
		}
		return token;
	}

	private async revokeDivingFishToken(refreshToken: string): Promise<void> {
		const body = new URLSearchParams({
			token: refreshToken,
			token_type_hint: "refresh_token",
			client_id: this.env.DIVING_FISH_OAUTH_CLIENT_ID,
		});
		if (this.env.DIVING_FISH_OAUTH_CLIENT_SECRET) {
			body.set("client_secret", this.env.DIVING_FISH_OAUTH_CLIENT_SECRET);
		}
		const response = await fetch(`${DIVING_FISH_AUTH_BASE_URL}/oauth/revoke`, {
			method: "POST",
			headers: { "Content-Type": "application/x-www-form-urlencoded" },
			body: body.toString(),
			signal: AbortSignal.timeout(10_000),
		});
		if (!response.ok) {
			const payload = (await response.json().catch(() => ({}))) as DivingFishTokenResponse;
			throw new AppError(
				response.status,
				`df_oauth_${payload.error?.trim() || "revocation_failed"}`,
				payload.error_description ?? "Diving Fish authorization revocation failed.",
			);
		}
	}

	private credentialsFromToken(token: DivingFishTokenResponse, fallbackScope = DIVING_FISH_SCOPE): DivingFishCredentials {
		if (!token.access_token || !token.refresh_token || !token.expires_in) {
			throw new AppError(400, "df_oauth_invalid_token", "Diving Fish returned an incomplete token response.");
		}
		return {
			accessToken: token.access_token,
			refreshToken: token.refresh_token,
			expiresAt: new Date(Date.now() + token.expires_in * 1000).toISOString(),
			scope: token.scope ?? fallbackScope,
		};
	}

	private parseDivingFishCredentials(value: Prisma.JsonValue | null | undefined): DivingFishCredentials | null {
		if (!value || typeof value !== "object" || Array.isArray(value)) {
			return null;
		}
		const accessToken = value.accessToken;
		const refreshToken = value.refreshToken;
		const expiresAt = value.expiresAt;
		const scope = value.scope;
		if (
			typeof accessToken !== "string" ||
			typeof refreshToken !== "string" ||
			typeof expiresAt !== "string" ||
			typeof scope !== "string"
		) {
			return null;
		}
		return { accessToken, refreshToken, expiresAt, scope };
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
				// Several rows can share one lookup key, and the map below keeps the
				// first. Charts that vanished upstream are kept as `disabled` rather
				// than deleted, so order them last to route scores to a live chart.
				orderBy: [{ disabled: "asc" }, { songIdentifier: "asc" }],
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
				// Titles are not unique across songs, so prefer live charts here too.
				orderBy: [{ disabled: "asc" }, { songIdentifier: "asc" }],
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
