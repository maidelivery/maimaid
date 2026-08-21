import "reflect-metadata";
import type { PrismaClient } from "@prisma/client";
import { afterEach, describe, expect, it, vi } from "vitest";
import { parseEnv } from "../src/env.js";
import { sha256Hex } from "../src/lib/crypto.js";
import { ImportService } from "../src/services/import.service.js";

const createEnv = () =>
	parseEnv({
		NODE_ENV: "test",
		DATABASE_URL: "postgres://localhost:5432/maimaid",
		JWT_ACCESS_SECRET: "1234567890123456",
		OPAQUE_SERVER_SETUP: "opaque-server-setup",
		DIVING_FISH_OAUTH_CLIENT_ID: "diving-fish-client",
		DIVING_FISH_OAUTH_CLIENT_SECRET: "diving-fish-secret",
	});

const scoreService = {
	requireProfileOwnership: vi.fn().mockResolvedValue(undefined),
};

const syncService = {};

describe("ImportService Diving Fish OAuth", () => {
	afterEach(() => {
		vi.restoreAllMocks();
		vi.unstubAllGlobals();
	});

	it("creates a PKCE authorization URL with the fixed callback", async () => {
		const prisma = {
			divingFishOAuthSession: {
				deleteMany: vi.fn().mockResolvedValue({ count: 0 }),
				create: vi.fn().mockResolvedValue({ id: "11111111-1111-4111-8111-111111111111" }),
			},
		};
		const service = new ImportService(prisma as never, scoreService as never, syncService as never, createEnv());

		const result = await service.startDivingFishAuthorization({
			userId: "user-1",
			profileId: "22222222-2222-4222-8222-222222222222",
		});
		const url = new URL(result.authorizationUrl);
		const createCall = prisma.divingFishOAuthSession.create.mock.calls[0]?.[0];
		const codeVerifier = createCall?.data.codeVerifier;
		const expectedChallenge = new Uint8Array(
			await crypto.subtle.digest("SHA-256", new TextEncoder().encode(codeVerifier)),
		).toBase64({ alphabet: "base64url", omitPadding: true });

		expect(url.origin).toBe("https://auth.diving-fish.com");
		expect(url.pathname).toBe("/oauth/authorize");
		expect(url.searchParams.get("client_id")).toBe("diving-fish-client");
		expect(url.searchParams.get("redirect_uri")).toBe("https://api.rhythmeta.org/v1/imports:divingFishCallback");
		expect(url.searchParams.get("scope")).toBe("prober.records.read prober.records.write");
		expect(url.searchParams.get("code_challenge_method")).toBe("S256");
		expect(codeVerifier).toMatch(/^[A-Za-z0-9_-]{43,128}$/u);
		expect(codeVerifier).not.toContain("=");
		expect(url.searchParams.get("code_challenge")).toBe(expectedChallenge);
		expect(url.searchParams.get("state")).toBeTruthy();
		expect(prisma.divingFishOAuthSession.create).toHaveBeenCalledWith({
			data: expect.objectContaining({
				profileId: "22222222-2222-4222-8222-222222222222",
				stateHash: expect.any(String),
				codeVerifier: expect.any(String),
				expiresAt: expect.any(Date),
			}),
		});
	});

	it("exchanges the callback code and persists the rotating tokens", async () => {
		const state = "callback-state";
		const profileBinding = {
			upsert: vi.fn().mockResolvedValue({ id: "binding-1" }),
		};
		const divingFishOAuthSession = {
			findUnique: vi.fn().mockResolvedValue({
				id: "session-1",
				profileId: "22222222-2222-4222-8222-222222222222",
				stateHash: await sha256Hex(state),
				codeVerifier: "pkce-verifier",
				status: "pending",
				expiresAt: new Date(Date.now() + 60_000),
			}),
			updateMany: vi.fn().mockResolvedValue({ count: 1 }),
			update: vi.fn().mockResolvedValue({}),
		};
		const prisma = {
			profileBinding,
			divingFishOAuthSession,
			$transaction: vi.fn(async (operations: Array<Promise<unknown>>) => Promise.all(operations)),
		};
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({
					access_token: "access-token",
					refresh_token: "refresh-token",
					expires_in: 900,
					scope: "prober.records.read",
					sub: "df-user-1",
				}),
				{ status: 200, headers: { "Content-Type": "application/json" } },
			),
		);
		vi.stubGlobal("fetch", fetchMock);
		const service = new ImportService(
			prisma as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);

		await service.completeDivingFishAuthorization({ state, code: "authorization-code" });

		const request = fetchMock.mock.calls[0]?.[1] as RequestInit;
		const tokenBody = new URLSearchParams(request.body as string);
		expect(tokenBody.get("grant_type")).toBe("authorization_code");
		expect(tokenBody.get("redirect_uri")).toBe("https://api.rhythmeta.org/v1/imports:divingFishCallback");
		expect(tokenBody.get("code_verifier")).toBe("pkce-verifier");
		expect(tokenBody.get("client_secret")).toBe("diving-fish-secret");
		expect(profileBinding.upsert).toHaveBeenCalledWith(
			expect.objectContaining({
				create: expect.objectContaining({
					externalUserId: "df-user-1",
					credentialJson: expect.objectContaining({
						accessToken: "access-token",
						refreshToken: "refresh-token",
						scope: "prober.records.read",
					}),
				}),
			}),
		);
		expect(divingFishOAuthSession.updateMany).toHaveBeenCalledWith({
			where: {
				id: "session-1",
				status: "pending",
				expiresAt: { gt: expect.any(Date) },
			},
			data: { status: "exchanging" },
		});
		expect(divingFishOAuthSession.update).toHaveBeenCalledWith({
			where: { id: "session-1" },
			data: {
				status: "success",
				errorCode: null,
				completedAt: expect.any(Date),
			},
		});
	});

	it("rejects a callback when another request already claimed the authorization code", async () => {
		const state = "callback-state";
		const divingFishOAuthSession = {
			findUnique: vi.fn().mockResolvedValue({
				id: "session-1",
				profileId: "22222222-2222-4222-8222-222222222222",
				codeVerifier: "pkce-verifier",
				status: "pending",
				expiresAt: new Date(Date.now() + 60_000),
			}),
			updateMany: vi.fn().mockResolvedValue({ count: 0 }),
		};
		const fetchMock = vi.fn();
		vi.stubGlobal("fetch", fetchMock);
		const service = new ImportService(
			{ divingFishOAuthSession } as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);

		await expect(service.completeDivingFishAuthorization({ state, code: "authorization-code" })).rejects.toMatchObject({
			code: "df_oauth_invalid_state",
		});
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("persists a rotated refresh token before returning the refreshed access token", async () => {
		const profileBinding = {
			findUnique: vi.fn().mockResolvedValue({
				id: "binding-1",
				credentialJson: {
					accessToken: "expired-access-token",
					refreshToken: "old-refresh-token",
					expiresAt: new Date(0).toISOString(),
					scope: "prober.records.read",
				},
			}),
			update: vi.fn().mockResolvedValue({ id: "binding-1" }),
		};
		const transaction = {
			$queryRaw: vi.fn().mockResolvedValue([]),
			profileBinding,
		};
		const prisma = {
			$transaction: vi.fn(async (callback: (client: typeof transaction) => Promise<unknown>) => callback(transaction)),
		};
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(
					JSON.stringify({
						access_token: "new-access-token",
						refresh_token: "new-refresh-token",
						expires_in: 900,
						scope: "prober.records.read",
					}),
					{ status: 200, headers: { "Content-Type": "application/json" } },
				),
			),
		);
		const service = new ImportService(
			prisma as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);
		const refreshAccessToken = service as unknown as {
			divingFishAccessToken(profileId: string, requiredScope: string): Promise<string>;
		};

		await expect(
			refreshAccessToken.divingFishAccessToken("22222222-2222-4222-8222-222222222222", "prober.records.read"),
		).resolves.toBe("new-access-token");
		expect(profileBinding.update).toHaveBeenCalledWith({
			where: { id: "binding-1" },
			data: {
				credentialJson: expect.objectContaining({
					accessToken: "new-access-token",
					refreshToken: "new-refresh-token",
				}),
			},
		});
	});

	it("revokes the refresh token before deleting a Diving Fish binding", async () => {
		const profileBinding = {
			findUnique: vi.fn().mockResolvedValue({
				credentialJson: {
					accessToken: "access-token",
					refreshToken: "refresh-token",
					expiresAt: new Date(Date.now() + 60_000).toISOString(),
					scope: "prober.records.read",
				},
			}),
			deleteMany: vi.fn().mockResolvedValue({ count: 1 }),
		};
		const divingFishOAuthSession = {
			deleteMany: vi.fn().mockResolvedValue({ count: 1 }),
		};
		const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
		vi.stubGlobal("fetch", fetchMock);
		const service = new ImportService(
			{ profileBinding, divingFishOAuthSession } as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);

		await expect(
			service.disconnectDivingFish({
				userId: "user-1",
				profileId: "22222222-2222-4222-8222-222222222222",
			}),
		).resolves.toEqual({
			connected: false,
			canWrite: false,
			externalUserId: null,
			externalUsername: null,
			updatedAt: null,
		});
		const request = fetchMock.mock.calls[0]?.[1] as RequestInit;
		const revokeBody = new URLSearchParams(request.body as string);
		expect(fetchMock.mock.calls[0]?.[0]).toBe("https://auth.diving-fish.com/oauth/revoke");
		expect(revokeBody.get("token")).toBe("refresh-token");
		expect(revokeBody.get("token_type_hint")).toBe("refresh_token");
		expect(profileBinding.deleteMany).toHaveBeenCalledWith({
			where: {
				profileId: "22222222-2222-4222-8222-222222222222",
				provider: "df",
			},
		});
		expect(divingFishOAuthSession.deleteMany).toHaveBeenCalledWith({
			where: { profileId: "22222222-2222-4222-8222-222222222222" },
		});
		expect(fetchMock.mock.invocationCallOrder[0] ?? 0).toBeLessThan(profileBinding.deleteMany.mock.invocationCallOrder[0] ?? 0);
	});

	it("syncs scores with the OAuth bearer token and write scope", async () => {
		const transaction = {
			$queryRaw: vi.fn().mockResolvedValue([]),
			profileBinding: {
				findUnique: vi.fn().mockResolvedValue({
					id: "binding-1",
					credentialJson: {
						accessToken: "access-token",
						refreshToken: "refresh-token",
						expiresAt: new Date(Date.now() + 60_000).toISOString(),
						scope: "prober.records.read prober.records.write",
					},
				}),
			},
		};
		const prisma = {
			profile: { findUnique: vi.fn().mockResolvedValue({ server: "cn" }) },
			$transaction: vi.fn(async (callback: (client: typeof transaction) => Promise<unknown>) => callback(transaction)),
		};
		const fetchMock = vi.fn().mockResolvedValue(new Response(null, { status: 200 }));
		vi.stubGlobal("fetch", fetchMock);
		const service = new ImportService(
			prisma as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);

		await expect(
			service.syncScoresToDivingFish({
				userId: "user-1",
				profileId: "22222222-2222-4222-8222-222222222222",
				records: [
					{
						title: "Test Song",
						chartType: "dx",
						levelIndex: 3,
						achievements: 100.5,
						dxScore: 1234,
						fc: "fc",
						fs: null,
					},
				],
			}),
		).resolves.toEqual({ syncedCount: 1 });

		expect(fetchMock.mock.calls[0]?.[0]).toBe("https://www.diving-fish.com/api/maimaidxprober/player/update_records");
		const request = fetchMock.mock.calls[0]?.[1] as RequestInit;
		expect(new Headers(request.headers).get("Authorization")).toBe("Bearer access-token");
		expect(JSON.parse(request.body as string)).toEqual([
			{
				title: "Test Song",
				level_index: 3,
				achievements: 100.5,
				type: "DX",
				dxScore: 1234,
				fc: "fc",
				fs: null,
			},
		]);
	});

	it("rejects score sync when the granted token lacks write scope", async () => {
		const transaction = {
			$queryRaw: vi.fn().mockResolvedValue([]),
			profileBinding: {
				findUnique: vi.fn().mockResolvedValue({
					id: "binding-1",
					credentialJson: {
						accessToken: "access-token",
						refreshToken: "refresh-token",
						expiresAt: new Date(Date.now() + 60_000).toISOString(),
						scope: "prober.records.read",
					},
				}),
			},
		};
		const prisma = {
			profile: { findUnique: vi.fn().mockResolvedValue({ server: "cn" }) },
			$transaction: vi.fn(async (callback: (client: typeof transaction) => Promise<unknown>) => callback(transaction)),
		};
		const fetchMock = vi.fn();
		vi.stubGlobal("fetch", fetchMock);
		const service = new ImportService(
			prisma as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);

		await expect(
			service.syncScoresToDivingFish({
				userId: "user-1",
				profileId: "22222222-2222-4222-8222-222222222222",
				records: [
					{
						title: "Test Song",
						chartType: "standard",
						levelIndex: 2,
						achievements: 99,
						dxScore: 0,
					},
				],
			}),
		).rejects.toMatchObject({ code: "df_write_scope_required" });
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("rejects Diving Fish score sync for a non-CN profile", async () => {
		const prisma = {
			profile: { findUnique: vi.fn().mockResolvedValue({ server: "jp" }) },
		};
		const fetchMock = vi.fn();
		vi.stubGlobal("fetch", fetchMock);
		const service = new ImportService(
			prisma as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);

		await expect(
			service.syncScoresToDivingFish({
				userId: "user-1",
				profileId: "22222222-2222-4222-8222-222222222222",
				records: [
					{
						title: "Test Song",
						chartType: "standard",
						levelIndex: 2,
						achievements: 99,
						dxScore: 0,
					},
				],
			}),
		).rejects.toMatchObject({ code: "df_sync_cn_profile_required" });
		expect(fetchMock).not.toHaveBeenCalled();
	});

	it("persists a rotated token before reporting that write scope is unavailable", async () => {
		const profileBinding = {
			findUnique: vi.fn().mockResolvedValue({
				id: "binding-1",
				credentialJson: {
					accessToken: "expired-access-token",
					refreshToken: "old-refresh-token",
					expiresAt: new Date(0).toISOString(),
					scope: "prober.records.read",
				},
			}),
			update: vi.fn().mockResolvedValue({ id: "binding-1" }),
		};
		const transaction = {
			$queryRaw: vi.fn().mockResolvedValue([]),
			profileBinding,
		};
		const prisma = {
			$transaction: vi.fn(async (callback: (client: typeof transaction) => Promise<unknown>) => callback(transaction)),
		};
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(
				new Response(
					JSON.stringify({
						access_token: "new-access-token",
						refresh_token: "new-refresh-token",
						expires_in: 900,
						scope: "prober.records.read",
					}),
					{ status: 200, headers: { "Content-Type": "application/json" } },
				),
			),
		);
		const service = new ImportService(
			prisma as unknown as PrismaClient,
			scoreService as never,
			syncService as never,
			createEnv(),
		);
		const refreshAccessToken = service as unknown as {
			divingFishAccessToken(profileId: string, requiredScope: string): Promise<string>;
		};

		await expect(
			refreshAccessToken.divingFishAccessToken("22222222-2222-4222-8222-222222222222", "prober.records.write"),
		).rejects.toMatchObject({ code: "df_write_scope_required" });
		expect(profileBinding.update).toHaveBeenCalledWith({
			where: { id: "binding-1" },
			data: {
				credentialJson: expect.objectContaining({
					accessToken: "new-access-token",
					refreshToken: "new-refresh-token",
				}),
			},
		});
	});
});
