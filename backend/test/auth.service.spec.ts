import "reflect-metadata";
import * as opaque from "@serenity-kit/opaque";
import type { PrismaClient, User } from "@prisma/client";
import { describe, expect, it, vi } from "vitest";
import type { Env } from "../src/env.js";
import { hashPasswordFingerprint } from "../src/lib/opaque-password.js";
import { AuthService } from "../src/services/auth.service.js";
import { JwtService } from "../src/services/jwt.service.js";

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

type AuthServicePrivateAPI = {
	buildAuthActionUrl: (
		action: "verify-email" | "password-reset",
		token: string,
		emailLinkContext?: {
			channel?: "web" | "app";
			redirectUri?: string;
		},
	) => string;
};

const createUserRecord = (overrides: Partial<User> = {}): User => ({
	id: "user-1",
	email: "alice@example.com",
	username: "Alice",
	usernameNormalized: "alice",
	usernameDiscriminator: "0001",
	passwordHash: "hash",
	opaqueRegistrationRecord: null,
	passwordFingerprintHash: null,
	status: "active",
	isAdmin: false,
	emailVerifiedAt: new Date("2026-04-12T10:00:00Z"),
	createdAt: new Date("2026-04-12T10:00:00Z"),
	updatedAt: new Date("2026-04-12T10:00:00Z"),
	...overrides,
});

const createOpaqueRegistrationRecord = async (email: string, password: string) => {
	const normalizedEmail = email.trim().toLowerCase();
	const start = opaque.client.startRegistration({ password });
	const response = opaque.server.createRegistrationResponse({
		serverSetup: OPAQUE_SERVER_SETUP,
		userIdentifier: normalizedEmail,
		registrationRequest: start.registrationRequest,
	});
	const finish = opaque.client.finishRegistration({
		password,
		clientRegistrationState: start.clientRegistrationState,
		registrationResponse: response.registrationResponse,
		keyStretching: "memory-constrained",
	});

	return finish.registrationRecord;
};

describe("AuthService", () => {
	it("builds app auth email links with client=app", () => {
		const env = createEnv();
		const service = new AuthService({} as PrismaClient, new JwtService(env), env);
		const buildAuthActionUrl = (service as unknown as AuthServicePrivateAPI).buildAuthActionUrl.bind(service);

		const url = new URL(
			buildAuthActionUrl("password-reset", "reset-token", {
				channel: "app",
				redirectUri: "maimaid://auth/callback",
			}),
		);

		expect(url.pathname).toBe("/v1/auth/password-reset");
		expect(url.searchParams.get("token")).toBe("reset-token");
		expect(url.searchParams.get("client")).toBe("app");
		expect(url.searchParams.get("redirect_uri")).toBe("maimaid://auth/callback");
	});

	it("stores username and opaque password fields during registration finish", async () => {
		const createdUser = createUserRecord({
			passwordHash: null,
			opaqueRegistrationRecord: "opaque-record",
			passwordFingerprintHash: "fingerprint-hash",
		});
		const tx = {
			user: {
				findMany: vi.fn().mockResolvedValue([]),
				create: vi.fn().mockResolvedValue(createdUser),
			},
			profile: {
				create: vi.fn().mockResolvedValue({}),
			},
		};
		const prisma = {
			user: {
				findUnique: vi.fn().mockResolvedValue(null),
			},
			$transaction: vi.fn(async (callback: (client: unknown) => Promise<unknown>) => callback(tx)),
		};
		const env = createEnv();
		const service = new AuthService(prisma as never, new JwtService(env), env);
		vi.spyOn(service as never, "sendVerificationEmail").mockResolvedValue(true);

		const result = await service.finishOpaqueRegistration(
			"Alice@example.com",
			"Alice",
			"opaque-record",
			"fingerprint-1",
		);

		expect(result.verificationEmailSent).toBe(true);
		expect(tx.user.create).toHaveBeenCalledWith(
			expect.objectContaining({
				data: expect.objectContaining({
					email: "alice@example.com",
					username: "Alice",
					usernameNormalized: "alice",
					usernameDiscriminator: "0001",
					passwordHash: null,
					opaqueRegistrationRecord: "opaque-record",
					passwordFingerprintHash: expect.any(String),
				}),
			}),
		);
	});

	it("preserves the discriminator when only username casing changes", async () => {
		const tx = {
			user: {
				findUnique: vi.fn().mockResolvedValue({
					id: "user-1",
					status: "active",
					usernameNormalized: "alice",
					usernameDiscriminator: "0042",
				}),
				update: vi.fn().mockImplementation(async ({ data }: { data: Record<string, unknown> }) => ({
					...createUserRecord({
						username: data.username as string,
						usernameNormalized: data.usernameNormalized as string,
						usernameDiscriminator: data.usernameDiscriminator as string,
					}),
				})),
			},
			$transaction: vi.fn(async (callback: (client: unknown) => Promise<unknown>) => callback(tx)),
		};
		const env = createEnv();
		const service = new AuthService(tx as never, new JwtService(env), env);

		const result = await service.updateUsername("user-1", "ALICE");

		expect(result.username).toBe("ALICE");
		expect(result.usernameDiscriminator).toBe("0042");
		expect(tx.user.update).toHaveBeenCalledWith(
			expect.objectContaining({
				data: expect.objectContaining({
					usernameDiscriminator: "0042",
				}),
			}),
		);
	});

	it("reassigns the next available discriminator when renaming to a different username", async () => {
		const tx = {
			user: {
				findUnique: vi.fn().mockResolvedValue({
					id: "user-1",
					status: "active",
					usernameNormalized: "alice",
					usernameDiscriminator: "0042",
				}),
				findMany: vi.fn().mockResolvedValue([{ usernameDiscriminator: "0001" }]),
				update: vi.fn().mockImplementation(async ({ data }: { data: Record<string, unknown> }) => ({
					...createUserRecord({
						username: data.username as string,
						usernameNormalized: data.usernameNormalized as string,
						usernameDiscriminator: data.usernameDiscriminator as string,
					}),
				})),
			},
			$transaction: vi.fn(async (callback: (client: unknown) => Promise<unknown>) => callback(tx)),
		};
		const env = createEnv();
		const service = new AuthService(tx as never, new JwtService(env), env);

		const result = await service.updateUsername("user-1", "Bob");

		expect(result).toMatchObject({
			username: "Bob",
			usernameNormalized: "bob",
			usernameDiscriminator: "0002",
		});
	});

	it("returns legacy-bcrypt from opaque login start for unmigrated users", async () => {
		const prisma = {
			user: {
				findUnique: vi.fn().mockResolvedValue(
					createUserRecord({
						passwordHash: "legacy-hash",
						opaqueRegistrationRecord: null,
					}),
				),
			},
		};
		const env = createEnv();
		const service = new AuthService(prisma as never, new JwtService(env), env);

		const result = await service.startOpaqueLogin("alice@example.com", "opaque-start-request");

		expect(result).toEqual({ protocol: "legacy-bcrypt" });
	});

	it("completes an opaque login challenge and consumes it", async () => {
		const password = "Password1!";
		const registrationRecord = await createOpaqueRegistrationRecord("alice@example.com", password);
		const user = createUserRecord({
			passwordHash: null,
			opaqueRegistrationRecord: registrationRecord,
		});
		let storedChallenge:
			| {
					id: string;
					tokenHash: string;
					serverLoginState: string;
					expiresAt: Date;
					consumedAt: Date | null;
					user: User;
			  }
			| null = null;

		const prisma = {
			user: {
				findUnique: vi.fn().mockResolvedValue(user),
			},
			opaqueLoginChallenge: {
				create: vi.fn().mockImplementation(async ({ data }: { data: Record<string, unknown> }) => {
					storedChallenge = {
						id: "challenge-1",
						tokenHash: data.tokenHash as string,
						serverLoginState: data.serverLoginState as string,
						expiresAt: data.expiresAt as Date,
						consumedAt: null,
						user,
					};
					return storedChallenge;
				}),
				findUnique: vi.fn().mockImplementation(async ({ where }: { where: { tokenHash: string } }) => {
					if (!storedChallenge || where.tokenHash !== storedChallenge.tokenHash) {
						return null;
					}
					return storedChallenge;
				}),
				updateMany: vi.fn().mockImplementation(async () => {
					if (storedChallenge) {
						storedChallenge = {
							...storedChallenge,
							consumedAt: new Date(),
						};
					}
					return { count: 1 };
				}),
			},
		};
		const env = createEnv();
		const service = new AuthService(prisma as never, new JwtService(env), env);

		const clientStart = opaque.client.startLogin({ password });
		const startPayload = await service.startOpaqueLogin(user.email, clientStart.startLoginRequest);
		expect(startPayload.protocol).toBe("opaque");
		if (startPayload.protocol !== "opaque") {
			throw new Error("Expected opaque login flow.");
		}

		const clientFinish = opaque.client.finishLogin({
			password,
			clientLoginState: clientStart.clientLoginState,
			loginResponse: startPayload.loginResponse,
			keyStretching: "memory-constrained",
		});
		expect(clientFinish).toBeDefined();

		const loggedInUser = await service.finishOpaqueLogin(startPayload.challengeToken, clientFinish!.finishLoginRequest);

		expect(loggedInUser.id).toBe(user.id);
		expect(prisma.opaqueLoginChallenge.updateMany).toHaveBeenCalledTimes(1);
	});

	it("enrolls opaque password material and clears legacy password hashes", async () => {
		const user = createUserRecord({
			passwordHash: "legacy-hash",
			opaqueRegistrationRecord: null,
			passwordFingerprintHash: null,
		});
		const prisma = {
			user: {
				findUnique: vi.fn().mockResolvedValue(user),
				update: vi.fn().mockResolvedValue({}),
			},
		};
		const env = createEnv();
		const service = new AuthService(prisma as never, new JwtService(env), env);

		await service.finishPasswordEnrollmentOpaque(user.id, "opaque-record", "fingerprint-1");

		expect(prisma.user.update).toHaveBeenCalledWith({
			where: { id: user.id },
			data: {
				opaqueRegistrationRecord: "opaque-record",
				passwordFingerprintHash: expect.any(String),
				passwordHash: null,
			},
		});
	});

	it("rejects reusing the same password fingerprint during opaque enrollment", async () => {
		const fingerprintHash = await hashPasswordFingerprint("fingerprint-1");
		const user = createUserRecord({
			passwordFingerprintHash: fingerprintHash,
		});
		const prisma = {
			user: {
				findUnique: vi.fn().mockResolvedValue(user),
				update: vi.fn(),
			},
		};
		const env = createEnv();
		const service = new AuthService(prisma as never, new JwtService(env), env);

		await expect(
			service.finishPasswordEnrollmentOpaque(user.id, "opaque-record", "fingerprint-1"),
		).rejects.toMatchObject({
			code: "password_reused",
		});
		expect(prisma.user.update).not.toHaveBeenCalled();
	});

	it("replaces password state and revokes refresh tokens during opaque password reset", async () => {
		const passwordResetRecord = {
			id: "reset-1",
			userId: "user-1",
			tokenHash: "hash",
			expiresAt: new Date(Date.now() + 60_000),
			consumedAt: null,
			createdAt: new Date("2026-04-12T10:00:00Z"),
		};
		const user = createUserRecord({
			passwordHash: "legacy-hash",
			opaqueRegistrationRecord: null,
		});
		const prisma = {
			passwordResetToken: {
				findUnique: vi.fn().mockResolvedValue(passwordResetRecord),
				update: vi.fn().mockResolvedValue({}),
			},
			user: {
				findUnique: vi.fn().mockResolvedValue(user),
				update: vi.fn().mockResolvedValue({}),
			},
			refreshToken: {
				updateMany: vi.fn().mockResolvedValue({ count: 2 }),
			},
			$transaction: vi.fn(async (operations: Promise<unknown>[]) => Promise.all(operations)),
		};
		const env = createEnv();
		const service = new AuthService(prisma as never, new JwtService(env), env);

		await service.finishOpaquePasswordReset("reset-token-value-12345", "opaque-record", "fingerprint-2");

		expect(prisma.user.update).toHaveBeenCalledWith({
			where: { id: passwordResetRecord.userId },
			data: {
				passwordHash: null,
				opaqueRegistrationRecord: "opaque-record",
				passwordFingerprintHash: expect.any(String),
			},
		});
		expect(prisma.refreshToken.updateMany).toHaveBeenCalledWith({
			where: {
				userId: passwordResetRecord.userId,
				revokedAt: null,
			},
			data: {
				revokedAt: expect.any(Date),
			},
		});
		expect(prisma.passwordResetToken.update).toHaveBeenCalledWith({
			where: { id: passwordResetRecord.id },
			data: { consumedAt: expect.any(Date) },
		});
	});
});
