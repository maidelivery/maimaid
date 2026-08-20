import type { Prisma, PrismaClient } from "@prisma/client";
import { inject, injectable } from "tsyringe";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { randomToken, sha256Base64Url, sha256Hex } from "../lib/crypto.js";
import { ScoreService } from "./score.service.js";
import { SyncService } from "./sync.service.js";
import { chunk } from "./catalog.utils.js";
import { difficultyByLevelIndex, lxnsSongIdToLocal, normalizeChartType } from "../utils/compat.js";
import type { Env } from "../env.js";

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

type DivingFishDiscovery = {
	issuer?: string;
	authorization_endpoint?: string;
	token_endpoint?: string;
	userinfo_endpoint?: string;
};

type DivingFishTokenResponse = {
	token_type?: string;
	access_token?: string;
	expires_in?: number;
	refresh_token?: string;
	id_token?: string;
	scope?: string;
	error?: string;
	error_description?: string;
};

type DivingFishToken = {
	accessToken: string;
	refreshToken: string;
	expiresIn: number;
	scope: string;
};

type DivingFishUserInfo = {
	sub?: string;
	preferred_username?: string;
	name?: string;
	nickname?: string;
};

type DivingFishCredential = {
	version: 1;
	accessToken?: string;
	accessTokenExpiresAt?: string;
	refreshToken?: string;
	scope?: string;
	pending?: {
		stateHash: string;
		codeVerifier: string;
		nonce: string;
		expiresAt: string;
	};
	exchange?: {
		id: string;
		expiresAt: string;
	};
	refreshLease?: {
		id: string;
		expiresAt: string;
	};
};

const DIVING_FISH_ISSUER = "https://auth.diving-fish.com";
const DIVING_FISH_DISCOVERY_URL = `${DIVING_FISH_ISSUER}/.well-known/openid-configuration`;
const DIVING_FISH_RECORDS_URL = "https://www.diving-fish.com/api/maimaidxprober/player/records";
const DIVING_FISH_SCOPE = "openid profile prober.records.read";
const DIVING_FISH_STATE_TTL_MS = 10 * 60 * 1_000;
const DIVING_FISH_EXCHANGE_TTL_MS = 2 * 60 * 1_000;
const DIVING_FISH_REFRESH_LEASE_TTL_MS = 30 * 1_000;
const DIVING_FISH_ACCESS_TOKEN_SKEW_MS = 30 * 1_000;

// D1 deployments can enforce a 100-variable SQLite statement limit. Keep
// repeated IN predicates below that ceiling, including the three song-id
// predicates used by the catalog compatibility lookup.
const D1_LOOKUP_CHUNK_SIZE = 25;
const POSTGRES_LOOKUP_CHUNK_SIZE = 500;

const IMPORT_UPSTREAM_ROUTES = new Map<string, string>([
	[DIVING_FISH_DISCOVERY_URL, "diving-fish/discovery"],
	["https://auth.diving-fish.com/oauth/token", "diving-fish/token"],
	["https://auth.diving-fish.com/oauth/userinfo", "diving-fish/userinfo"],
	[DIVING_FISH_RECORDS_URL, "diving-fish/records"],
	["https://maimai.lxns.net/api/v0/oauth/token", "lxns/token"],
	["https://maimai.lxns.net/api/v0/user/maimai/player", "lxns/player"],
	["https://maimai.lxns.net/api/v0/user/maimai/player/scores", "lxns/scores"],
]);

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
	error?: string;
	error_description?: string;
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

	async createDivingFishAuthorization(input: { userId: string; profileId: string }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
		const config = this.requireDivingFishOAuthConfig();
		const discovery = await this.getDivingFishDiscovery();
		const state = `${input.profileId}.${randomToken(32)}`;
		const codeVerifier = randomToken(64);
		const nonce = randomToken(24);
		const expiresAt = new Date(Date.now() + DIVING_FISH_STATE_TTL_MS);
		const existing = await this.prisma.profileBinding.findUnique({
			where: {
				profileId_provider: {
					profileId: input.profileId,
					provider: "df",
				},
			},
		});
		const credentials = this.parseDivingFishCredential(existing?.credentialJson);
		credentials.pending = {
			stateHash: await sha256Hex(state),
			codeVerifier,
			nonce,
			expiresAt: expiresAt.toISOString(),
		};
		delete credentials.exchange;

		await this.prisma.profileBinding.upsert({
			where: {
				profileId_provider: {
					profileId: input.profileId,
					provider: "df",
				},
			},
			create: {
				profileId: input.profileId,
				provider: "df",
				credentialJson: this.toCredentialJson(credentials),
			},
			update: {
				credentialJson: this.toCredentialJson(credentials),
			},
		});

		const authorizationUrl = new URL(discovery.authorizationEndpoint);
		authorizationUrl.search = new URLSearchParams({
			response_type: "code",
			client_id: config.clientId,
			redirect_uri: config.redirectUri,
			scope: DIVING_FISH_SCOPE,
			state,
			nonce,
			code_challenge: await sha256Base64Url(codeVerifier),
			code_challenge_method: "S256",
		}).toString();

		return {
			authorizationUrl: authorizationUrl.toString(),
			expiresAt: expiresAt.toISOString(),
		};
	}

	async handleDivingFishCallback(input: { code: string | undefined; state: string | undefined; error: string | undefined }) {
		const state = input.state?.trim() ?? "";
		const separatorIndex = state.indexOf(".");
		const profileId = separatorIndex > 0 ? state.slice(0, separatorIndex) : "";
		if (!this.isUuid(profileId)) {
			throw new AppError(400, "df_oauth_invalid_state", "Diving Fish authorization state is invalid.");
		}

		const binding = await this.prisma.profileBinding.findUnique({
			where: {
				profileId_provider: {
					profileId,
					provider: "df",
				},
			},
		});
		const credentials = this.parseDivingFishCredential(binding?.credentialJson);
		const pending = credentials.pending;
		if (!binding || !pending || Date.parse(pending.expiresAt) <= Date.now()) {
			throw new AppError(400, "df_oauth_invalid_state", "Diving Fish authorization state expired.");
		}
		const receivedStateHash = await sha256Hex(state);
		if (!this.constantTimeEqual(receivedStateHash, pending.stateHash)) {
			throw new AppError(400, "df_oauth_invalid_state", "Diving Fish authorization state is invalid.");
		}

		delete credentials.pending;
		const exchangeId = randomToken(24);
		if (input.error) {
			delete credentials.exchange;
		} else {
			credentials.exchange = {
				id: exchangeId,
				expiresAt: new Date(Date.now() + DIVING_FISH_EXCHANGE_TTL_MS).toISOString(),
			};
		}
		const consumed = await this.prisma.profileBinding.updateMany({
			where: {
				id: binding.id,
				updatedAt: binding.updatedAt,
			},
			data: {
				credentialJson: this.toCredentialJson(credentials),
			},
		});
		if (consumed.count !== 1) {
			throw new AppError(400, "df_oauth_invalid_state", "Diving Fish authorization state was already used.");
		}
		if (input.error) {
			throw new AppError(400, "df_oauth_denied", "Diving Fish authorization was denied.");
		}

		const code = input.code?.trim() ?? "";
		if (!code) {
			throw new AppError(400, "df_oauth_missing_code", "Diving Fish authorization code is required.");
		}

		const config = this.requireDivingFishOAuthConfig();
		const discovery = await this.getDivingFishDiscovery();
		const token = await this.requestDivingFishToken(discovery.tokenEndpoint, {
			grant_type: "authorization_code",
			code,
			redirect_uri: config.redirectUri,
			client_id: config.clientId,
			client_secret: config.clientSecret,
			code_verifier: pending.codeVerifier,
		});
		const userInfo = await this.getDivingFishUserInfo(discovery.userInfoEndpoint, token.accessToken);
		const current = await this.prisma.profileBinding.findUnique({ where: { id: binding.id } });
		const currentCredentials = this.parseDivingFishCredential(current?.credentialJson);
		if (!current || currentCredentials.exchange?.id !== exchangeId) {
			throw new AppError(409, "df_oauth_superseded", "A newer Diving Fish authorization has started.");
		}

		const connectedCredentials = this.createConnectedDivingFishCredential(token, currentCredentials);
		delete connectedCredentials.exchange;
		delete connectedCredentials.refreshLease;
		const saved = await this.prisma.profileBinding.updateMany({
			where: {
				id: current.id,
				updatedAt: current.updatedAt,
			},
			data: {
				credentialJson: this.toCredentialJson(connectedCredentials),
				externalUserId: userInfo?.sub ?? current.externalUserId,
				externalUsername: userInfo?.preferred_username ?? userInfo?.nickname ?? userInfo?.name ?? current.externalUsername,
			},
		});
		if (saved.count !== 1) {
			throw new AppError(409, "df_oauth_superseded", "A newer Diving Fish authorization has started.");
		}

		return {
			profileId,
			externalUsername: userInfo?.preferred_username ?? userInfo?.nickname ?? userInfo?.name ?? null,
		};
	}

	async getDivingFishConnection(input: { userId: string; profileId: string }) {
		await this.scoreService.requireProfileOwnership(input.profileId, input.userId);
		const binding = await this.prisma.profileBinding.findUnique({
			where: {
				profileId_provider: {
					profileId: input.profileId,
					provider: "df",
				},
			},
		});
		const credentials = this.parseDivingFishCredential(binding?.credentialJson);
		return {
			connected: Boolean(credentials.refreshToken),
			externalUsername: binding?.externalUsername ?? null,
		};
	}

	async transformFromDivingFish(input: { accessToken: string }): Promise<TransformedImportResult> {
		const response = await this.fetchImportUpstream(DIVING_FISH_RECORDS_URL, {
			method: "GET",
			headers: {
				Authorization: `Bearer ${input.accessToken}`,
			},
		});
		const payload = await this.readJsonResponse<DivingFishRecordsResponse>(response, "df_import_failed", "Diving Fish");
		if (!response.ok || !Array.isArray(payload.records)) {
			throw new AppError(
				response.status === 401 ? 409 : 400,
				response.status === 401 ? "df_oauth_required" : "df_import_failed",
				payload.message ?? "Failed to import from Diving Fish.",
				{ upstreamStatus: response.status },
			);
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

	async transformFromLxns(input: { accessToken: string }): Promise<TransformedImportResult> {
		const [scoresResponse, playerResponse] = await Promise.all([
			this.fetchImportUpstream("https://maimai.lxns.net/api/v0/user/maimai/player/scores", {
				method: "GET",
				headers: {
					Authorization: `Bearer ${input.accessToken}`,
				},
			}),
			this.fetchImportUpstream("https://maimai.lxns.net/api/v0/user/maimai/player", {
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
		const accessToken = await this.getDivingFishAccessToken(input.profileId);
		const run = await this.prisma.importRun.create({
			data: {
				profileId: input.profileId,
				provider: "df",
				status: "pending",
			},
		});

		try {
			const transformed = await this.transformFromDivingFish({ accessToken });
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

		let response: Response;
		try {
			response = await this.fetchImportUpstream("https://maimai.lxns.net/api/v0/oauth/token", {
				method: "POST",
				headers: {
					Accept: "application/json",
					"Content-Type": "application/x-www-form-urlencoded",
				},
				body: body.toString(),
			});
		} catch {
			throw new AppError(502, "lxns_oauth_unavailable", "LXNS authorization server is unavailable.");
		}
		const payload = await this.readJsonResponse<LxnsTokenResponse>(response, "lxns_oauth_unavailable", "LXNS token endpoint");
		const accessToken = payload.data?.access_token?.trim() ?? "";
		const refreshToken = payload.data?.refresh_token?.trim() ?? "";
		if (!response.ok || !accessToken || !refreshToken) {
			throw new AppError(
				response.status >= 500 ? 502 : 400,
				response.status >= 500 ? "lxns_oauth_unavailable" : "lxns_oauth_failed",
				payload.message ?? payload.error_description ?? "Failed to exchange LXNS authorization code.",
				{ upstreamStatus: response.status },
			);
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

	private requireDivingFishOAuthConfig() {
		const clientId = this.env.DIVING_FISH_CLIENT_ID?.trim();
		const clientSecret = this.env.DIVING_FISH_CLIENT_SECRET?.trim();
		if (!clientId || !clientSecret) {
			throw new AppError(503, "df_oauth_unconfigured", "Diving Fish OAuth is unavailable.");
		}
		return {
			clientId,
			clientSecret,
			redirectUri: this.env.DIVING_FISH_REDIRECT_URI,
		};
	}

	private async getDivingFishDiscovery() {
		let response: Response;
		try {
			response = await this.fetchImportUpstream(DIVING_FISH_DISCOVERY_URL, {
				headers: { Accept: "application/json" },
			});
		} catch {
			throw new AppError(502, "df_oauth_unavailable", "Diving Fish authorization server is unavailable.");
		}
		const payload = await this.readJsonResponse<DivingFishDiscovery>(
			response,
			"df_oauth_unavailable",
			"Diving Fish authorization server",
		);
		if (!response.ok || payload.issuer !== DIVING_FISH_ISSUER) {
			throw new AppError(502, "df_oauth_unavailable", "Diving Fish authorization discovery failed.", {
				upstreamStatus: response.status,
			});
		}
		return {
			authorizationEndpoint: this.requireDivingFishEndpoint(payload.authorization_endpoint, "authorization"),
			tokenEndpoint: this.requireDivingFishEndpoint(payload.token_endpoint, "token"),
			userInfoEndpoint: this.requireDivingFishEndpoint(payload.userinfo_endpoint, "userinfo"),
		};
	}

	private requireDivingFishEndpoint(value: string | undefined, name: string): string {
		if (!value) {
			throw new AppError(502, "df_oauth_unavailable", `Diving Fish ${name} endpoint is unavailable.`);
		}
		try {
			const endpoint = new URL(value);
			if (endpoint.origin !== DIVING_FISH_ISSUER) {
				throw new Error("unexpected_origin");
			}
			return endpoint.toString();
		} catch {
			throw new AppError(502, "df_oauth_unavailable", `Diving Fish ${name} endpoint is invalid.`);
		}
	}

	private async requestDivingFishToken(tokenEndpoint: string, parameters: Record<string, string>): Promise<DivingFishToken> {
		let response: Response;
		try {
			response = await this.fetchImportUpstream(tokenEndpoint, {
				method: "POST",
				headers: {
					Accept: "application/json",
					"Content-Type": "application/x-www-form-urlencoded",
				},
				body: new URLSearchParams(parameters).toString(),
			});
		} catch {
			throw new AppError(502, "df_oauth_unavailable", "Diving Fish token exchange is unavailable.");
		}
		const payload = await this.readJsonResponse<DivingFishTokenResponse>(
			response,
			"df_oauth_failed",
			"Diving Fish token endpoint",
		);
		const accessToken = payload.access_token?.trim() ?? "";
		const refreshToken = payload.refresh_token?.trim() ?? "";
		const expiresIn = payload.expires_in;
		if (
			!response.ok ||
			payload.error ||
			!accessToken ||
			!refreshToken ||
			typeof expiresIn !== "number" ||
			!Number.isFinite(expiresIn) ||
			expiresIn <= 0
		) {
			throw new AppError(400, "df_oauth_failed", payload.error_description ?? "Diving Fish token exchange failed.", {
				upstreamStatus: response.status,
				oauthError: payload.error ?? "invalid_token_response",
			});
		}
		return {
			accessToken,
			refreshToken,
			expiresIn,
			scope: payload.scope?.trim() || DIVING_FISH_SCOPE,
		};
	}

	private async getDivingFishUserInfo(endpoint: string, accessToken: string): Promise<DivingFishUserInfo | null> {
		try {
			const response = await this.fetchImportUpstream(endpoint, {
				headers: {
					Accept: "application/json",
					Authorization: `Bearer ${accessToken}`,
				},
			});
			if (!response.ok) {
				return null;
			}
			return await this.readJsonResponse<DivingFishUserInfo>(response, "df_oauth_failed", "Diving Fish userinfo");
		} catch (error) {
			console.warn("[df_oauth_userinfo_failed]", error instanceof Error ? error.message : "unknown_error");
			return null;
		}
	}

	private async getDivingFishAccessToken(profileId: string, retryCount = 0): Promise<string> {
		const binding = await this.prisma.profileBinding.findUnique({
			where: {
				profileId_provider: {
					profileId,
					provider: "df",
				},
			},
		});
		const credentials = this.parseDivingFishCredential(binding?.credentialJson);
		if (!binding || !credentials.refreshToken) {
			throw new AppError(409, "df_oauth_required", "Connect a Diving Fish account before importing.");
		}
		const accessTokenExpiresAt = credentials.accessTokenExpiresAt ? Date.parse(credentials.accessTokenExpiresAt) : 0;
		if (credentials.accessToken && accessTokenExpiresAt > Date.now() + DIVING_FISH_ACCESS_TOKEN_SKEW_MS) {
			return credentials.accessToken;
		}
		if (credentials.refreshLease && Date.parse(credentials.refreshLease.expiresAt) > Date.now()) {
			throw new AppError(409, "df_oauth_refresh_in_progress", "Diving Fish authorization is refreshing. Retry shortly.");
		}

		const leaseId = randomToken(24);
		credentials.refreshLease = {
			id: leaseId,
			expiresAt: new Date(Date.now() + DIVING_FISH_REFRESH_LEASE_TTL_MS).toISOString(),
		};
		const claimed = await this.prisma.profileBinding.updateMany({
			where: {
				id: binding.id,
				updatedAt: binding.updatedAt,
			},
			data: {
				credentialJson: this.toCredentialJson(credentials),
			},
		});
		if (claimed.count !== 1) {
			if (retryCount < 1) {
				return this.getDivingFishAccessToken(profileId, retryCount + 1);
			}
			throw new AppError(409, "df_oauth_refresh_in_progress", "Diving Fish authorization changed. Retry shortly.");
		}

		const claimedBinding = await this.prisma.profileBinding.findUnique({ where: { id: binding.id } });
		const claimedCredentials = this.parseDivingFishCredential(claimedBinding?.credentialJson);
		if (!claimedBinding || claimedCredentials.refreshLease?.id !== leaseId || !claimedCredentials.refreshToken) {
			throw new AppError(409, "df_oauth_refresh_in_progress", "Diving Fish authorization changed. Retry shortly.");
		}

		try {
			const config = this.requireDivingFishOAuthConfig();
			const discovery = await this.getDivingFishDiscovery();
			const token = await this.requestDivingFishToken(discovery.tokenEndpoint, {
				grant_type: "refresh_token",
				refresh_token: claimedCredentials.refreshToken,
				client_id: config.clientId,
				client_secret: config.clientSecret,
			});
			const current = await this.prisma.profileBinding.findUnique({ where: { id: binding.id } });
			const currentCredentials = this.parseDivingFishCredential(current?.credentialJson);
			if (!current || currentCredentials.refreshLease?.id !== leaseId) {
				throw new AppError(409, "df_oauth_refresh_in_progress", "Diving Fish authorization changed. Retry shortly.");
			}
			const refreshedCredentials = this.createConnectedDivingFishCredential(token, currentCredentials);
			delete refreshedCredentials.refreshLease;
			const saved = await this.prisma.profileBinding.updateMany({
				where: {
					id: current.id,
					updatedAt: current.updatedAt,
				},
				data: {
					credentialJson: this.toCredentialJson(refreshedCredentials),
				},
			});
			if (saved.count !== 1) {
				throw new AppError(409, "df_oauth_refresh_in_progress", "Diving Fish authorization changed. Retry shortly.");
			}
			return token.accessToken;
		} catch (error) {
			await this.releaseDivingFishRefreshLease(binding.id, leaseId, this.isInvalidDivingFishGrant(error));
			if (this.isInvalidDivingFishGrant(error)) {
				throw new AppError(409, "df_oauth_required", "Diving Fish authorization expired. Connect the account again.");
			}
			throw error;
		}
	}

	private async releaseDivingFishRefreshLease(bindingId: string, leaseId: string, clearTokens: boolean) {
		const current = await this.prisma.profileBinding.findUnique({ where: { id: bindingId } });
		const credentials = this.parseDivingFishCredential(current?.credentialJson);
		if (!current || credentials.refreshLease?.id !== leaseId) {
			return;
		}
		delete credentials.refreshLease;
		if (clearTokens) {
			delete credentials.accessToken;
			delete credentials.accessTokenExpiresAt;
			delete credentials.refreshToken;
			delete credentials.scope;
		}
		await this.prisma.profileBinding.updateMany({
			where: {
				id: current.id,
				updatedAt: current.updatedAt,
			},
			data: {
				credentialJson: this.toCredentialJson(credentials),
			},
		});
	}

	private isInvalidDivingFishGrant(error: unknown): boolean {
		if (!(error instanceof AppError) || typeof error.details !== "object" || error.details === null) {
			return false;
		}
		return "oauthError" in error.details && error.details.oauthError === "invalid_grant";
	}

	private createConnectedDivingFishCredential(token: DivingFishToken, current: DivingFishCredential): DivingFishCredential {
		return {
			...current,
			version: 1,
			accessToken: token.accessToken,
			accessTokenExpiresAt: new Date(Date.now() + token.expiresIn * 1_000).toISOString(),
			refreshToken: token.refreshToken,
			scope: token.scope,
		};
	}

	private parseDivingFishCredential(value: unknown): DivingFishCredential {
		const source = this.asRecord(value);
		const result: DivingFishCredential = { version: 1 };
		if (!source) {
			return result;
		}
		if (typeof source.accessToken === "string") result.accessToken = source.accessToken;
		if (typeof source.accessTokenExpiresAt === "string") result.accessTokenExpiresAt = source.accessTokenExpiresAt;
		if (typeof source.refreshToken === "string") result.refreshToken = source.refreshToken;
		if (typeof source.scope === "string") result.scope = source.scope;

		const pending = this.asRecord(source.pending);
		if (
			pending &&
			typeof pending.stateHash === "string" &&
			typeof pending.codeVerifier === "string" &&
			typeof pending.nonce === "string" &&
			typeof pending.expiresAt === "string"
		) {
			result.pending = {
				stateHash: pending.stateHash,
				codeVerifier: pending.codeVerifier,
				nonce: pending.nonce,
				expiresAt: pending.expiresAt,
			};
		}
		const exchange = this.asRecord(source.exchange);
		if (exchange && typeof exchange.id === "string" && typeof exchange.expiresAt === "string") {
			result.exchange = { id: exchange.id, expiresAt: exchange.expiresAt };
		}
		const refreshLease = this.asRecord(source.refreshLease);
		if (refreshLease && typeof refreshLease.id === "string" && typeof refreshLease.expiresAt === "string") {
			result.refreshLease = { id: refreshLease.id, expiresAt: refreshLease.expiresAt };
		}
		return result;
	}

	private toCredentialJson(value: DivingFishCredential): Prisma.InputJsonObject {
		return JSON.parse(JSON.stringify(value)) as Prisma.InputJsonObject;
	}

	private asRecord(value: unknown): Record<string, unknown> | null {
		return typeof value === "object" && value !== null && !Array.isArray(value) ? (value as Record<string, unknown>) : null;
	}

	private async readJsonResponse<T>(response: Response, code: string, provider: string): Promise<T> {
		const text = await response.text();
		try {
			return JSON.parse(text) as T;
		} catch {
			throw new AppError(502, code, `${provider} returned an invalid response.`, {
				upstreamStatus: response.status,
			});
		}
	}

	private fetchImportUpstream(input: RequestInfo | URL, init?: RequestInit): Promise<Response> {
		const upstreamUrl = new URL(input instanceof Request ? input.url : input);
		upstreamUrl.hash = "";
		const proxyBaseUrl = this.env.OAUTH_UPSTREAM_URL;
		const proxyToken = this.env.OAUTH_UPSTREAM_TOKEN;
		if (!proxyBaseUrl && !proxyToken) {
			return fetch(input, init);
		}
		if (!proxyBaseUrl || !proxyToken) {
			throw new AppError(503, "oauth_upstream_unconfigured", "OAuth upstream proxy is unavailable.");
		}
		const route = IMPORT_UPSTREAM_ROUTES.get(upstreamUrl.toString());
		if (!route) {
			throw new AppError(500, "oauth_upstream_forbidden", "OAuth upstream route is not allowed.");
		}
		const headers = new Headers(init?.headers ?? (input instanceof Request ? input.headers : undefined));
		headers.set("X-Maimaid-OAuth-Proxy-Token", proxyToken);
		return fetch(new URL(route, proxyBaseUrl), {
			...init,
			method: init?.method ?? (input instanceof Request ? input.method : "GET"),
			headers,
		});
	}

	private isUuid(value: string): boolean {
		return /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(value);
	}

	private constantTimeEqual(left: string, right: string): boolean {
		if (left.length !== right.length) {
			return false;
		}
		let difference = 0;
		for (let index = 0; index < left.length; index += 1) {
			difference |= left.charCodeAt(index) ^ right.charCodeAt(index);
		}
		return difference === 0;
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
			const sheetsBySongId: CatalogSheetCandidate[] = [];
			for (const songIdBatch of chunk(songIds, this.lookupChunkSize())) {
				const songIdentifierCandidates = songIdBatch.map((item) => String(item));
				const rows = await this.prisma.sheet.findMany({
					where: {
						chartType: {
							in: chartTypes,
						},
						difficulty: {
							in: difficulties,
						},
						OR: [
							{ songId: { in: songIdBatch } },
							{ song: { songId: { in: songIdBatch } } },
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
				sheetsBySongId.push(...rows);
			}

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

			const sheetsByTitle: CatalogSheetCandidate[] = [];
			for (const titleBatch of chunk(unresolvedTitles, this.lookupChunkSize())) {
				const rows = await this.prisma.sheet.findMany({
					where: {
						chartType: {
							in: unresolvedChartTypes,
						},
						difficulty: {
							in: unresolvedDifficulties,
						},
						song: {
							title: {
								in: titleBatch,
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
				sheetsByTitle.push(...rows);
			}

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

	private lookupChunkSize(): number {
		return this.env.DATABASE_DIALECT === "sqlite" ? D1_LOOKUP_CHUNK_SIZE : POSTGRES_LOOKUP_CHUNK_SIZE;
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
