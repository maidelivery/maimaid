import "reflect-metadata";
import * as opaque from "@serenity-kit/opaque";
import { container } from "tsyringe";
import { afterEach, describe, expect, it, vi } from "vitest";
import { TOKENS } from "../src/di/tokens.js";
import type { Env } from "../src/env.js";
import { AdminUserService } from "../src/services/admin-user.service.js";

const OPAQUE_SERVER_SETUP = await (async () => {
	await opaque.ready;
	return opaque.server.createSetup();
})();

const createEnv = (): Env => ({
	NODE_ENV: "test",
	HOST: "0.0.0.0",
	PORT: 8787,
	APP_PUBLIC_URL: "https://api.example.com",
	CORS_ALLOWED_ORIGINS: "",
	DATABASE_URL: "postgres://localhost:5432/maimaid",
	JWT_ISSUER: "maimaid-backend",
	JWT_AUDIENCE: "maimaid-clients",
	JWT_ACCESS_SECRET: "1234567890123456",
	JWT_ACCESS_TTL_SECONDS: 900,
	JWT_REFRESH_TTL_SECONDS: 60 * 60 * 24 * 30,
	MFA_CHALLENGE_TTL_SECONDS: 300,
	WEBAUTHN_RP_ID: undefined,
	WEBAUTHN_RP_NAME: "maimaid",
	WEBAUTHN_ORIGIN: undefined,
	RESEND_API_KEY: undefined,
	RESEND_FROM_EMAIL: "no-reply@example.com",
	S3_ENDPOINT: undefined,
	S3_PUBLIC_ENDPOINT: undefined,
	S3_REGION: "auto",
	S3_BUCKET: "maimaid-assets",
	S3_ACCESS_KEY_ID: undefined,
	S3_SECRET_ACCESS_KEY: undefined,
	CATALOG_SOURCE_URL: "https://example.com/catalog.json",
	STATIC_SYNC_INTERVAL_HOURS: 6,
	OPAQUE_SERVER_SETUP,
});

afterEach(() => {
	vi.restoreAllMocks();
});

describe("AdminUserService", () => {
	it("creates users through opaque start and finish without storing raw passwords", async () => {
		const env = createEnv();
		vi.spyOn(container, "resolve").mockImplementation((token: unknown) => {
			if (token === TOKENS.Env) {
				return env as never;
			}
			throw new Error(`Unexpected token: ${String(token)}`);
		});

		const userCreate = vi.fn().mockImplementation(async ({ data }: { data: Record<string, unknown> }) => ({
			id: "user-1",
			email: data.email,
			username: data.username,
			usernameNormalized: data.usernameNormalized,
			usernameDiscriminator: data.usernameDiscriminator,
			passwordHash: data.passwordHash,
			opaqueRegistrationRecord: data.opaqueRegistrationRecord,
			passwordFingerprintHash: data.passwordFingerprintHash,
			isAdmin: false,
			status: "active",
			emailVerifiedAt: null,
			createdAt: new Date("2026-04-12T10:00:00Z"),
			updatedAt: new Date("2026-04-12T10:00:00Z"),
		}));
		const profileCreate = vi.fn().mockResolvedValue({});
		const prisma = {
			user: {
				findUnique: vi.fn().mockResolvedValue(null),
			},
			$transaction: vi.fn(async (callback: (tx: unknown) => Promise<unknown>) =>
				callback({
					user: {
						findMany: vi.fn().mockResolvedValue([]),
						create: userCreate,
					},
					profile: {
						create: profileCreate,
					},
				}),
			),
		};

		const service = new AdminUserService(prisma as never);
		const password = "Password1!";
		const registrationStart = opaque.client.startRegistration({ password });
		const startPayload = await service.startOpaqueCreateUser({
			email: "TeSt.User@example.com",
			registrationRequest: registrationStart.registrationRequest,
		});
		const registrationFinish = opaque.client.finishRegistration({
			password,
			clientRegistrationState: registrationStart.clientRegistrationState,
			registrationResponse: startPayload.registrationResponse,
			keyStretching: "memory-constrained",
		});

		const result = await service.finishOpaqueCreateUser({
			email: "TeSt.User@example.com",
			registrationRecord: registrationFinish.registrationRecord,
			passwordFingerprint: "fingerprint-1",
		});

		expect(result).toMatchObject({
			email: "test.user@example.com",
			username: "test.user",
			usernameDiscriminator: "0001",
			handle: "test.user#0001",
		});
		expect(userCreate).toHaveBeenCalledWith(
			expect.objectContaining({
				data: expect.objectContaining({
					email: "test.user@example.com",
					username: "test.user",
					usernameNormalized: "test.user",
					usernameDiscriminator: "0001",
					passwordHash: null,
					opaqueRegistrationRecord: registrationFinish.registrationRecord,
					passwordFingerprintHash: expect.any(String),
				}),
			}),
		);
		expect(profileCreate).toHaveBeenCalledTimes(1);
	});

	it("includes handle in admin user rows", async () => {
		const prisma = {
			user: {
				findMany: vi.fn().mockResolvedValue([
					{
						id: "user-1",
						email: "alice@example.com",
						username: "Alice",
						usernameDiscriminator: "0042",
						status: "active",
						isAdmin: false,
						emailVerifiedAt: null,
						createdAt: new Date("2026-04-12T10:00:00Z"),
						updatedAt: new Date("2026-04-12T10:05:00Z"),
						totpCredential: { enabledAt: new Date("2026-04-12T10:06:00Z") },
						passkeyCredentials: [{ id: "pk-1" }],
						_count: {
							profiles: 2,
						},
					},
				]),
				count: vi.fn().mockResolvedValue(1),
			},
		};

		const service = new AdminUserService(prisma as never);
		const result = await service.listUsers({ limit: 20, offset: 0 });

		expect(result.total).toBe(1);
		expect(result.rows[0]).toMatchObject({
			email: "alice@example.com",
			handle: "Alice#0042",
			status: "active",
			profileCount: 2,
			mfa: {
				enabled: true,
				totpEnabled: true,
				passkeyCount: 1,
			},
		});
	});
});
