import "reflect-metadata";
import { afterEach, describe, expect, it, vi } from "vitest";
import { sha256Hex } from "../src/lib/crypto.js";
import { AppError } from "../src/lib/errors.js";
import { ImportService } from "../src/services/import.service.js";

const profileId = "018f05e0-8674-7d98-a678-8fd69a4a2d63";
const userId = "018f05e0-8674-7d98-a678-8fd69a4a2d64";
const clientId = "test-client-id";
const redirectUri = "https://api.rhythmeta.org/v1/imports:divingFishCallback";

const discoveryResponse = () =>
	new Response(
		JSON.stringify({
			issuer: "https://auth.diving-fish.com",
			authorization_endpoint: "https://auth.diving-fish.com/oauth/authorize",
			token_endpoint: "https://auth.diving-fish.com/oauth/token",
			userinfo_endpoint: "https://auth.diving-fish.com/oauth/userinfo",
		}),
		{ status: 200, headers: { "Content-Type": "application/json" } },
	);

const createService = (database: object, scoreService: object, syncService: object = {}, envOverrides: object = {}) =>
	new ImportService(
		database as never,
		scoreService as never,
		syncService as never,
		{
			DIVING_FISH_CLIENT_ID: clientId,
			DIVING_FISH_CLIENT_SECRET: "test-client-secret",
			DIVING_FISH_REDIRECT_URI: redirectUri,
			...envOverrides,
		} as never,
	);

afterEach(() => {
	vi.unstubAllGlobals();
});

describe("ImportService Diving Fish OAuth", () => {
	it("chunks large catalog mapping lookups for D1", async () => {
		const records = Array.from({ length: 51 }, (_, index) => ({
			title: `Song ${index + 1}`,
			type: "dx",
			level_index: 3,
			achievements: 100,
			song_id: index + 1,
		}));
		const sheetFindMany = vi.fn().mockImplementation(
			async (args: {
				where: {
					OR: Array<
						| { songId: { in: number[] } }
						| { song: { songId: { in: number[] } } }
						| { songIdentifier: { in: string[] } }
					>;
				};
			}) => {
				const songIds = args.where.OR[0];
				if (!("songId" in songIds)) {
					return [];
				}
				return songIds.songId.in.map((songId) => ({
					songIdentifier: String(songId),
					chartType: "dx",
					difficulty: "master",
					songId,
					song: { songId, title: `Song ${songId}` },
				}));
			},
		);
		const service = createService(
			{ sheet: { findMany: sheetFindMany } },
			{},
			{},
			{ DATABASE_DIALECT: "sqlite" },
		);
		vi.stubGlobal(
			"fetch",
			vi.fn().mockResolvedValue(new Response(JSON.stringify({ records }), { status: 200 })),
		);

		const result = await service.transformFromDivingFish({ accessToken: "token" });

		expect(result.mappedCount).toBe(51);
		expect(sheetFindMany).toHaveBeenCalledTimes(3);
		for (const call of sheetFindMany.mock.calls) {
			const query = call[0] as { where: { OR: Array<{ songId?: { in: number[] } }> } };
			expect(query.where.OR[0]?.songId?.in.length).toBeLessThanOrEqual(25);
		}
	});

	it("creates a server-bound authorization URL with PKCE", async () => {
		const upsert = vi.fn().mockResolvedValue({ id: "binding" });
		const database = {
			profileBinding: {
				findUnique: vi.fn().mockResolvedValue(null),
				upsert,
			},
		};
		const scoreService = { requireProfileOwnership: vi.fn().mockResolvedValue({ id: profileId }) };
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(discoveryResponse()));
		const service = createService(database, scoreService);

		const result = await service.createDivingFishAuthorization({ userId, profileId });

		const authorizationUrl = new URL(result.authorizationUrl);
		expect(authorizationUrl.origin + authorizationUrl.pathname).toBe("https://auth.diving-fish.com/oauth/authorize");
		expect(authorizationUrl.searchParams.get("client_id")).toBe(clientId);
		expect(authorizationUrl.searchParams.get("redirect_uri")).toBe(redirectUri);
		expect(authorizationUrl.searchParams.get("scope")).toBe("openid profile prober.records.read");
		expect(authorizationUrl.searchParams.get("code_challenge_method")).toBe("S256");
		expect(authorizationUrl.searchParams.get("code_challenge")).toMatch(/^[A-Za-z0-9_-]{43}$/);
		expect(authorizationUrl.searchParams.has("client_secret")).toBe(false);

		const state = authorizationUrl.searchParams.get("state");
		expect(state).toMatch(new RegExp(`^${profileId}\\.`));
		const savedCredential = upsert.mock.calls[0]?.[0].create.credentialJson as {
			pending: { stateHash: string; codeVerifier: string };
		};
		expect(savedCredential.pending.stateHash).toBe(await sha256Hex(state ?? ""));
		expect(savedCredential.pending.codeVerifier.length).toBeGreaterThanOrEqual(43);
		expect(savedCredential.pending.codeVerifier).toMatch(/^[A-Za-z0-9._~-]+$/);
		expect(JSON.stringify(savedCredential)).not.toContain(state);
	});

	it("consumes state once and persists the authorization tokens", async () => {
		const state = `${profileId}.state-token`;
		let current = {
			id: "binding-id",
			profileId,
			externalUserId: null,
			externalUsername: null,
			updatedAt: new Date("2026-08-20T01:00:00.000Z"),
			credentialJson: {
				version: 1,
				pending: {
					stateHash: await sha256Hex(state),
					codeVerifier: "code-verifier",
					nonce: "nonce",
					expiresAt: new Date(Date.now() + 60_000).toISOString(),
				},
			},
		};
		const updateMany = vi.fn().mockImplementation(async (args: { data: { credentialJson: object } }) => {
			current = {
				...current,
				...args.data,
				updatedAt: new Date(current.updatedAt.getTime() + 1),
			};
			return { count: 1 };
		});
		const database = {
			profileBinding: {
				findUnique: vi.fn().mockImplementation(async () => current),
				updateMany,
			},
		};
		const fetchMock = vi.fn().mockImplementation(async (input: string | URL | Request, init?: RequestInit) => {
			const url = String(input);
			if (url.endsWith("/.well-known/openid-configuration")) return discoveryResponse();
			if (url.endsWith("/oauth/token")) {
				expect(String(init?.body)).toContain("code_verifier=code-verifier");
				return new Response(
					JSON.stringify({
						token_type: "Bearer",
						access_token: "access-token",
						expires_in: 900,
						refresh_token: "refresh-token",
						scope: "openid profile prober.records.read",
					}),
					{ status: 200 },
				);
			}
			if (url.endsWith("/oauth/userinfo")) {
				return new Response(JSON.stringify({ sub: "df-user", preferred_username: "player" }), { status: 200 });
			}
			throw new Error(`Unexpected URL: ${url}`);
		});
		vi.stubGlobal("fetch", fetchMock);
		const service = createService(database, {});

		const result = await service.handleDivingFishCallback({ code: "auth-code", state, error: undefined });

		expect(result).toEqual({ profileId, externalUsername: "player" });
		expect(updateMany).toHaveBeenCalledTimes(2);
		const consumedCredential = updateMany.mock.calls[0]?.[0].data.credentialJson;
		expect(consumedCredential.pending).toBeUndefined();
		const connectedCredential = updateMany.mock.calls[1]?.[0].data.credentialJson;
		expect(connectedCredential).toMatchObject({
			accessToken: "access-token",
			refreshToken: "refresh-token",
		});
		expect(connectedCredential.exchange).toBeUndefined();
	});

	it("persists a rotated refresh token before fetching records", async () => {
		const events: string[] = [];
		let current = {
			id: "binding-id",
			profileId,
			externalUserId: "df-user",
			externalUsername: "player",
			updatedAt: new Date("2026-08-20T01:00:00.000Z"),
			credentialJson: {
				version: 1,
				accessToken: "expired-access-token",
				accessTokenExpiresAt: "2026-08-20T00:00:00.000Z",
				refreshToken: "old-refresh-token",
			},
		};
		const updateMany = vi.fn().mockImplementation(async (args: { data: { credentialJson: object } }) => {
			current = {
				...current,
				...args.data,
				updatedAt: new Date(current.updatedAt.getTime() + 1),
			};
			if ((current.credentialJson as { refreshToken?: string }).refreshToken === "new-refresh-token") {
				events.push("persist-new-refresh-token");
			}
			return { count: 1 };
		});
		const database = {
			profileBinding: {
				findUnique: vi.fn().mockImplementation(async () => current),
				updateMany,
			},
			importRun: {
				create: vi.fn().mockResolvedValue({ id: "run-id" }),
				update: vi.fn().mockResolvedValue({ id: "run-id" }),
			},
			importRawPayload: { create: vi.fn().mockResolvedValue({ id: "raw-id" }) },
		};
		const scoreService = {
			requireProfileOwnership: vi.fn().mockResolvedValue({ id: profileId }),
			bulkUpsertBestScores: vi.fn().mockResolvedValue({ applied: [], skipped: [] }),
			bulkInsertPlayRecords: vi.fn().mockResolvedValue({ created: [] }),
		};
		const syncService = { recordEvent: vi.fn().mockResolvedValue(undefined) };
		vi.stubGlobal(
			"fetch",
			vi.fn().mockImplementation(async (input: string | URL | Request, init?: RequestInit) => {
				const url = String(input);
				if (url.endsWith("/.well-known/openid-configuration")) return discoveryResponse();
				if (url.endsWith("/oauth/token")) {
					expect(String(init?.body)).toContain("refresh_token=old-refresh-token");
					return new Response(
						JSON.stringify({
							access_token: "new-access-token",
							expires_in: 900,
							refresh_token: "new-refresh-token",
						}),
						{ status: 200 },
					);
				}
				if (url.includes("/api/maimaidxprober/player/records")) {
					events.push("fetch-records");
					expect(new Headers(init?.headers).get("Authorization")).toBe("Bearer new-access-token");
					return new Response(JSON.stringify({ records: [] }), { status: 200 });
				}
				throw new Error(`Unexpected URL: ${url}`);
			}),
		);
		const service = createService(database, scoreService, syncService);

		await service.importFromDivingFish({ userId, profileId });

		expect(events).toEqual(["persist-new-refresh-token", "fetch-records"]);
	});

	it("turns a non-JSON upstream response into a stable import error", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("error code: 525\n", { status: 525 })));
		const service = createService({}, {});

		await expect(service.transformFromDivingFish({ accessToken: "token" })).rejects.toMatchObject<AppError>({
			code: "df_import_failed",
			status: 502,
		});
	});
});

describe("ImportService LXNS OAuth", () => {
	it("exchanges an authorization code", async () => {
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({
					success: true,
					data: {
						access_token: "access-token",
						refresh_token: "refresh-token",
					},
				}),
				{ status: 200, headers: { "Content-Type": "application/json" } },
			),
		);
		vi.stubGlobal("fetch", fetchMock);
		const service = createService({}, {});

		await expect(
			service.exchangeLxnsAuthorizationCode({ code: "authorization-code", codeVerifier: "code-verifier-with-enough-length" }),
		).resolves.toEqual({ accessToken: "access-token", refreshToken: "refresh-token" });
		expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("Accept")).toBe("application/json");
	});

	it("turns a non-JSON upstream response into a stable availability error", async () => {
		vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response("error code: 525\n", { status: 525 })));
		const service = createService({}, {});

		await expect(
			service.exchangeLxnsAuthorizationCode({ code: "authorization-code", codeVerifier: "code-verifier-with-enough-length" }),
		).rejects.toMatchObject<AppError>({ code: "lxns_oauth_unavailable", status: 502 });
	});

	it("turns a fetch failure into a stable availability error", async () => {
		vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new TypeError("fetch failed")));
		const service = createService({}, {});

		await expect(
			service.exchangeLxnsAuthorizationCode({ code: "authorization-code", codeVerifier: "code-verifier-with-enough-length" }),
		).rejects.toMatchObject<AppError>({ code: "lxns_oauth_unavailable", status: 502 });
	});

	it("routes upstream requests through the authenticated OAuth proxy", async () => {
		const fetchMock = vi.fn().mockResolvedValue(
			new Response(
				JSON.stringify({
					data: { access_token: "access-token", refresh_token: "refresh-token" },
				}),
				{ status: 200, headers: { "Content-Type": "application/json" } },
			),
		);
		vi.stubGlobal("fetch", fetchMock);
		const service = createService(
			{},
			{},
			{},
			{
				OAUTH_UPSTREAM_URL: "https://oauth-proxy.example/",
				OAUTH_UPSTREAM_TOKEN: "proxy-token-with-at-least-32-characters",
			},
		);

		await service.exchangeLxnsAuthorizationCode({
			code: "authorization-code",
			codeVerifier: "code-verifier-with-enough-length",
		});

		expect(String(fetchMock.mock.calls[0]?.[0])).toBe("https://oauth-proxy.example/lxns/token");
		expect(new Headers(fetchMock.mock.calls[0]?.[1]?.headers).get("X-Maimaid-OAuth-Proxy-Token")).toBe(
			"proxy-token-with-at-least-32-characters",
		);
	});
});
