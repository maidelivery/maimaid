import { inject, singleton } from "tsyringe";
import type { PrismaClient, User } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import type { Env } from "../env.js";
import { AppError } from "../lib/errors.js";
import { randomToken, sha256Hex } from "../lib/crypto.js";
import * as OTPAuth from "otpauth";
import {
	generateAuthenticationOptions,
	generateRegistrationOptions,
	verifyAuthenticationResponse,
	verifyRegistrationResponse,
} from "@simplewebauthn/server";

type LoginChannel = "web" | "app";
type LoginMethods = {
	totp: boolean;
	passkey: boolean;
	backupCode: boolean;
};
type PasskeyTransport = "ble" | "cable" | "hybrid" | "internal" | "nfc" | "smart-card" | "usb";
type PasskeySummary = {
	credentialId: string;
	name: string | null;
	transports: string[];
	createdAt: Date;
	updatedAt: Date;
};
const PASSKEY_TRANSPORTS = new Set<PasskeyTransport>(["ble", "cable", "hybrid", "internal", "nfc", "smart-card", "usb"]);
const BACKUP_CODE_COUNT = 10;
const BACKUP_CODE_GROUP_SIZE = 4;

@singleton()
export class MfaService {
	constructor(
		@inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
		@inject(TOKENS.Env) private readonly env: Env,
	) {}

	async status(userId: string) {
		const [totp, passkeys, backupCodeCount] = await Promise.all([
			this.prisma.userTotpCredential.findUnique({
				where: { userId },
			}),
			this.prisma.userPasskeyCredential.count({
				where: { userId },
			}),
			this.prisma.userMfaBackupCode.count({
				where: {
					userId,
					consumedAt: null,
				},
			}),
		]);

		return {
			totpEnabled: Boolean(totp?.enabledAt),
			passkeyCount: passkeys,
			backupCodeCount,
			mfaEnabled: Boolean(totp?.enabledAt) || passkeys > 0,
		};
	}

	async createLoginChallenge(user: User, channel: LoginChannel) {
		const methods = await this.listAvailableMethods(user.id);
		const challengeToken = randomToken(36);
		await this.prisma.mfaChallenge.create({
			data: {
				userId: user.id,
				tokenHash: await sha256Hex(challengeToken),
				purpose: "login",
				channel,
				challenge: null,
				passkeyAllowIds: [],
				expiresAt: this.nextExpiry(),
			},
		});
		return {
			challengeToken,
			methods,
		};
	}

	async listAvailableMethods(userId: string): Promise<LoginMethods> {
		const status = await this.status(userId);
		return {
			totp: status.totpEnabled,
			passkey: status.passkeyCount > 0,
			backupCode: status.backupCodeCount > 0,
		};
	}

	async startTotpSetup(user: User) {
		const secret = new OTPAuth.Secret({ size: 20 });
		const secretBase32 = secret.base32;
		const totp = this.createTotp(secretBase32, user.email);

		await this.prisma.userTotpCredential.upsert({
			where: { userId: user.id },
			update: {
				secretBase32,
				enabledAt: null,
			},
			create: {
				userId: user.id,
				secretBase32,
				enabledAt: null,
			},
		});

		return {
			secretBase32,
			otpauthUrl: totp.toString(),
		};
	}

	async confirmTotpSetup(user: User, code: string) {
		const credential = await this.prisma.userTotpCredential.findUnique({
			where: { userId: user.id },
		});
		if (!credential) {
			throw new AppError(404, "totp_not_initialized", "TOTP setup has not started.");
		}

		const totp = this.createTotp(credential.secretBase32, user.email);
		const valid = totp.validate({ token: code.trim(), window: 1 });
		if (valid === null) {
			throw new AppError(400, "invalid_totp_code", "TOTP code is invalid.");
		}

		await this.prisma.userTotpCredential.update({
			where: { userId: user.id },
			data: {
				enabledAt: new Date(),
			},
		});
		return { enabled: true };
	}

	async disableTotp(userId: string) {
		await this.prisma.$transaction([
			this.prisma.userTotpCredential.deleteMany({
				where: { userId },
			}),
			this.prisma.userMfaBackupCode.deleteMany({
				where: { userId },
			}),
		]);
		return { disabled: true };
	}

	async getBackupCodeStatus(userId: string) {
		const [activeCount, latest] = await Promise.all([
			this.prisma.userMfaBackupCode.count({
				where: {
					userId,
					consumedAt: null,
				},
			}),
			this.prisma.userMfaBackupCode.findFirst({
				where: {
					userId,
					consumedAt: null,
				},
				orderBy: {
					createdAt: "desc",
				},
				select: {
					createdAt: true,
				},
			}),
		]);

		return {
			activeCount,
			latestGeneratedAt: latest?.createdAt.toISOString() ?? null,
		};
	}

	async regenerateBackupCodes(userId: string) {
		const totp = await this.prisma.userTotpCredential.findUnique({
			where: { userId },
			select: {
				enabledAt: true,
			},
		});
		if (!totp?.enabledAt) {
			throw new AppError(400, "totp_not_enabled", "TOTP is not enabled.");
		}

		const codes = this.generateBackupCodes(BACKUP_CODE_COUNT);
		const now = new Date();
		await this.prisma.$transaction([
			this.prisma.userMfaBackupCode.deleteMany({
				where: { userId },
			}),
			this.prisma.userMfaBackupCode.createMany({
				data: await Promise.all(
					codes.map(async (code) => ({
						userId,
						codeHash: await sha256Hex(this.normalizeBackupCode(code)),
						createdAt: now,
					})),
				),
			}),
		]);

		return {
			codes,
			activeCount: codes.length,
			generatedAt: now.toISOString(),
		};
	}

	async listPasskeys(userId: string) {
		const rows = await this.prisma.userPasskeyCredential.findMany({
			where: { userId },
			orderBy: { createdAt: "desc" },
			select: {
				credentialId: true,
				name: true,
				transports: true,
				createdAt: true,
				updatedAt: true,
			},
		});
		return {
			passkeys: rows.map((item) => this.serializePasskey(item)),
		};
	}

	async startPasskeyRegistration(user: User): Promise<Record<string, unknown>> {
		const existing = await this.prisma.userPasskeyCredential.findMany({
			where: { userId: user.id },
			select: { credentialId: true },
		});
		const options = await generateRegistrationOptions({
			rpID: this.resolveRpId(),
			rpName: this.env.WEBAUTHN_RP_NAME,
			userID: new TextEncoder().encode(user.id),
			userName: user.email,
			userDisplayName: user.email,
			timeout: 60_000,
			attestationType: "none",
			authenticatorSelection: {
				residentKey: "preferred",
				userVerification: "preferred",
			},
			excludeCredentials: existing.map((item) => ({
				id: item.credentialId,
			})),
		});

		await this.prisma.mfaChallenge.create({
			data: {
				userId: user.id,
				tokenHash: await sha256Hex(randomToken(36)),
				purpose: "passkey_registration",
				channel: "web",
				challenge: options.challenge,
				passkeyAllowIds: [],
				expiresAt: this.nextExpiry(),
			},
		});
		return options as unknown as Record<string, unknown>;
	}

	async finishPasskeyRegistration(userId: string, response: unknown) {
		const challenge = await this.prisma.mfaChallenge.findFirst({
			where: {
				userId,
				purpose: "passkey_registration",
				consumedAt: null,
				expiresAt: {
					gt: new Date(),
				},
			},
			orderBy: { createdAt: "desc" },
		});
		if (!challenge?.challenge) {
			throw new AppError(400, "passkey_challenge_missing", "Passkey registration challenge not found.");
		}

		const verification = await verifyRegistrationResponse({
			response: response as Parameters<typeof verifyRegistrationResponse>[0]["response"],
			expectedChallenge: challenge.challenge,
			expectedOrigin: this.resolveExpectedOrigin(),
			expectedRPID: this.resolveRpId(),
			requireUserVerification: true,
		});
		if (!verification.verified || !verification.registrationInfo) {
			throw new AppError(400, "passkey_registration_failed", "Passkey registration failed.");
		}

		const created = await this.prisma.userPasskeyCredential.create({
			data: {
				userId,
				credentialId: verification.registrationInfo.credential.id,
				publicKey: Buffer.from(verification.registrationInfo.credential.publicKey),
				counter: verification.registrationInfo.credential.counter,
				transports: verification.registrationInfo.credential.transports ?? [],
			},
			select: {
				credentialId: true,
				name: true,
				transports: true,
				createdAt: true,
				updatedAt: true,
			},
		});

		await this.prisma.mfaChallenge.update({
			where: { id: challenge.id },
			data: {
				consumedAt: new Date(),
			},
		});
		return {
			success: true,
			passkey: this.serializePasskey(created),
		};
	}

	async renamePasskey(userId: string, credentialId: string, name: string) {
		const trimmedName = name.trim();
		if (!trimmedName) {
			throw new AppError(400, "invalid_passkey_name", "Passkey name is required.");
		}

		await this.prisma.userPasskeyCredential.updateMany({
			where: {
				userId,
				credentialId,
			},
			data: {
				name: trimmedName,
			},
		});

		const updated = await this.prisma.userPasskeyCredential.findFirst({
			where: {
				userId,
				credentialId,
			},
			select: {
				credentialId: true,
				name: true,
				transports: true,
				createdAt: true,
				updatedAt: true,
			},
		});

		if (!updated) {
			throw new AppError(404, "passkey_not_found", "Passkey credential not found.");
		}

		return {
			updated: true,
			passkey: this.serializePasskey(updated),
		};
	}

	async removePasskey(userId: string, credentialId: string) {
		const deleted = await this.prisma.userPasskeyCredential.deleteMany({
			where: {
				userId,
				credentialId,
			},
		});
		return { deleted: deleted.count > 0 };
	}

	async startPasskeyLogin(challengeToken: string): Promise<Record<string, unknown>> {
		const challenge = await this.findLoginChallenge(challengeToken);
		const credentials = await this.prisma.userPasskeyCredential.findMany({
			where: { userId: challenge.userId },
		});
		if (credentials.length === 0) {
			throw new AppError(400, "passkey_not_configured", "No passkey is configured.");
		}

		const options = await generateAuthenticationOptions({
			rpID: this.resolveRpId(),
			userVerification: "preferred",
			timeout: 60_000,
			allowCredentials: credentials.map((item) => ({
				id: item.credentialId,
				transports: this.normalizeTransports(item.transports),
			})),
		});

		await this.prisma.mfaChallenge.update({
			where: { id: challenge.id },
			data: {
				challenge: options.challenge,
				passkeyAllowIds: credentials.map((item) => item.credentialId),
			},
		});
		return options as unknown as Record<string, unknown>;
	}

	async startDirectPasskeyLogin(channel: LoginChannel): Promise<{ challengeToken: string; options: Record<string, unknown> }> {
		await this.cleanupExpiredDirectPasskeyChallenges();

		const credentials = await this.prisma.userPasskeyCredential.findMany();
		if (credentials.length === 0) {
			throw new AppError(400, "passkey_not_configured", "No passkey is configured.");
		}

		const options = await generateAuthenticationOptions({
			rpID: this.resolveRpId(),
			userVerification: "preferred",
			timeout: 60_000,
			allowCredentials: credentials.map((item) => ({
				id: item.credentialId,
				transports: this.normalizeTransports(item.transports),
			})),
		});

		const challengeToken = randomToken(36);
		await this.prisma.directPasskeyChallenge.create({
			data: {
				tokenHash: await sha256Hex(challengeToken),
				challenge: options.challenge,
				channel,
				expiresAt: this.nextExpiry(),
			},
		});

		return {
			challengeToken,
			options: options as unknown as Record<string, unknown>,
		};
	}

	async verifyTotpLogin(challengeToken: string, code: string) {
		const challenge = await this.findLoginChallenge(challengeToken);
		const user = await this.prisma.user.findUnique({
			where: { id: challenge.userId },
		});
		if (!user) {
			throw new AppError(404, "user_not_found", "User not found.");
		}
		const credential = await this.prisma.userTotpCredential.findUnique({
			where: { userId: user.id },
		});
		if (!credential?.enabledAt) {
			throw new AppError(400, "totp_not_enabled", "TOTP is not enabled.");
		}

		const totp = this.createTotp(credential.secretBase32, user.email);
		const valid = totp.validate({ token: code.trim(), window: 1 });
		if (valid === null) {
			throw new AppError(400, "invalid_totp_code", "TOTP code is invalid.");
		}

		await this.consumeChallenge(challenge.id);
		return user;
	}

	async verifyBackupCodeLogin(challengeToken: string, code: string) {
		const challenge = await this.findLoginChallenge(challengeToken);
		const normalizedCode = this.normalizeBackupCode(code);
		if (!normalizedCode) {
			throw new AppError(400, "invalid_backup_code", "Backup code is invalid.");
		}

		const totp = await this.prisma.userTotpCredential.findUnique({
			where: { userId: challenge.userId },
			select: {
				enabledAt: true,
			},
		});
		if (!totp?.enabledAt) {
			throw new AppError(400, "totp_not_enabled", "TOTP is not enabled.");
		}

		const consumed = await this.prisma.userMfaBackupCode.updateMany({
			where: {
				userId: challenge.userId,
				codeHash: await sha256Hex(normalizedCode),
				consumedAt: null,
			},
			data: {
				consumedAt: new Date(),
			},
		});
		if (consumed.count < 1) {
			throw new AppError(400, "invalid_backup_code", "Backup code is invalid.");
		}

		const user = await this.prisma.user.findUnique({
			where: { id: challenge.userId },
		});
		if (!user) {
			throw new AppError(404, "user_not_found", "User not found.");
		}

		await this.consumeChallenge(challenge.id);
		return user;
	}

	async verifyPasskeyLogin(challengeToken: string, response: unknown) {
		const token = challengeToken.trim();
		const tokenHash = await sha256Hex(token);
		const challenge = await this.prisma.mfaChallenge.findUnique({
			where: { tokenHash },
		});

		if (challenge && challenge.purpose === "login") {
			if (challenge.consumedAt || challenge.expiresAt <= new Date()) {
				throw new AppError(400, "expired_mfa_challenge", "MFA challenge is expired.");
			}
			if (!challenge.challenge) {
				throw new AppError(400, "passkey_challenge_missing", "Passkey challenge was not initialized.");
			}

			const credentialId = this.extractCredentialId(response);
			if (!credentialId || !challenge.passkeyAllowIds.includes(credentialId)) {
				throw new AppError(400, "invalid_passkey_credential", "Passkey credential is not allowed.");
			}

			const credential = await this.prisma.userPasskeyCredential.findFirst({
				where: {
					userId: challenge.userId,
					credentialId,
				},
			});
			if (!credential) {
				throw new AppError(404, "passkey_not_found", "Passkey credential not found.");
			}

			await this.verifyPasskeyResponse(challenge.challenge, credential, response);
			await this.consumeChallenge(challenge.id);

			const user = await this.prisma.user.findUnique({
				where: { id: challenge.userId },
			});
			if (!user) {
				throw new AppError(404, "user_not_found", "User not found.");
			}
			return user;
		}

		return this.verifyDirectPasskeyLogin(token, response);
	}

	async shouldEnforceMfa(userId: string) {
		const status = await this.status(userId);
		return status.mfaEnabled;
	}

	private async findLoginChallenge(challengeToken: string) {
		const tokenHash = await sha256Hex(challengeToken.trim());
		const challenge = await this.prisma.mfaChallenge.findUnique({
			where: { tokenHash },
		});
		if (!challenge || challenge.purpose !== "login") {
			throw new AppError(400, "invalid_mfa_challenge", "MFA challenge is invalid.");
		}
		if (challenge.consumedAt || challenge.expiresAt <= new Date()) {
			throw new AppError(400, "expired_mfa_challenge", "MFA challenge is expired.");
		}
		return challenge;
	}

	private async consumeChallenge(challengeId: string) {
		await this.prisma.mfaChallenge.update({
			where: { id: challengeId },
			data: { consumedAt: new Date() },
		});
	}

	private createTotp(secretBase32: string, email: string) {
		return new OTPAuth.TOTP({
			issuer: this.env.WEBAUTHN_RP_NAME,
			label: email,
			algorithm: "SHA1",
			digits: 6,
			period: 30,
			secret: OTPAuth.Secret.fromBase32(secretBase32),
		});
	}

	private nextExpiry() {
		return new Date(Date.now() + this.env.MFA_CHALLENGE_TTL_SECONDS * 1000);
	}

	private resolveRpId() {
		const configured = this.env.WEBAUTHN_RP_ID?.trim();
		if (configured) {
			return configured;
		}
		const origin = this.resolveExpectedOrigin();
		return new URL(origin).hostname;
	}

	private resolveExpectedOrigin() {
		if (this.env.WEBAUTHN_ORIGIN) {
			return this.env.WEBAUTHN_ORIGIN;
		}
		const base = this.env.APP_PUBLIC_URL?.trim() || `http://localhost:${this.env.PORT}`;
		return base.replace(/\/+$/u, "");
	}

	private extractCredentialId(response: unknown): string | null {
		if (typeof response !== "object" || response === null) {
			return null;
		}
		if ("id" in response && typeof response.id === "string") {
			return response.id;
		}
		return null;
	}

	private normalizeTransports(input: string[]): PasskeyTransport[] {
		return input.filter((item): item is PasskeyTransport => PASSKEY_TRANSPORTS.has(item as PasskeyTransport));
	}

	private generateBackupCodes(count: number): string[] {
		const values = new Set<string>();
		while (values.size < count) {
			const raw = crypto.getRandomValues(new Uint8Array(BACKUP_CODE_GROUP_SIZE)).toHex().toUpperCase();
			const code = `${raw.slice(0, BACKUP_CODE_GROUP_SIZE)}-${raw.slice(BACKUP_CODE_GROUP_SIZE, BACKUP_CODE_GROUP_SIZE * 2)}`;
			values.add(code);
		}
		return Array.from(values);
	}

	private normalizeBackupCode(code: string) {
		return code
			.trim()
			.toUpperCase()
			.replace(/[^A-Z0-9]/gu, "");
	}

	private serializePasskey(input: PasskeySummary) {
		return {
			credentialId: input.credentialId,
			name: input.name,
			transports: input.transports,
			createdAt: input.createdAt.toISOString(),
			updatedAt: input.updatedAt.toISOString(),
		};
	}

	private async cleanupExpiredDirectPasskeyChallenges() {
		await this.prisma.directPasskeyChallenge.deleteMany({
			where: {
				OR: [
					{
						expiresAt: {
							lte: new Date(),
						},
					},
					{
						consumedAt: {
							not: null,
						},
					},
				],
			},
		});
	}

	private async findDirectPasskeyChallenge(challengeToken: string) {
		const token = challengeToken.trim();
		if (!token) {
			throw new AppError(400, "invalid_mfa_challenge", "MFA challenge is invalid.");
		}

		const challenge = await this.prisma.directPasskeyChallenge.findUnique({
			where: {
				tokenHash: await sha256Hex(token),
			},
		});
		if (!challenge || challenge.consumedAt) {
			throw new AppError(400, "invalid_mfa_challenge", "MFA challenge is invalid.");
		}
		if (challenge.expiresAt <= new Date()) {
			throw new AppError(400, "expired_mfa_challenge", "MFA challenge is expired.");
		}
		return challenge;
	}

	private async consumeDirectPasskeyChallenge(challengeId: string) {
		const consumed = await this.prisma.directPasskeyChallenge.updateMany({
			where: {
				id: challengeId,
				consumedAt: null,
				expiresAt: {
					gt: new Date(),
				},
			},
			data: {
				consumedAt: new Date(),
			},
		});
		if (consumed.count !== 1) {
			throw new AppError(400, "invalid_mfa_challenge", "MFA challenge is invalid.");
		}
	}

	private async verifyDirectPasskeyLogin(challengeToken: string, response: unknown) {
		await this.cleanupExpiredDirectPasskeyChallenges();
		const challenge = await this.findDirectPasskeyChallenge(challengeToken);

		const credentialId = this.extractCredentialId(response);
		if (!credentialId) {
			throw new AppError(400, "invalid_passkey_credential", "Passkey credential is not allowed.");
		}

		const credential = await this.prisma.userPasskeyCredential.findUnique({
			where: { credentialId },
		});
		if (!credential) {
			throw new AppError(404, "passkey_not_found", "Passkey credential not found.");
		}

		await this.verifyPasskeyResponse(challenge.challenge, credential, response);
		await this.consumeDirectPasskeyChallenge(challenge.id);

		const user = await this.prisma.user.findUnique({
			where: { id: credential.userId },
		});
		if (!user || user.status !== "active") {
			throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
		}
		if (!user.emailVerifiedAt) {
			throw new AppError(403, "email_not_verified", "Email is not verified. Please check your inbox.");
		}
		return user;
	}

	private async verifyPasskeyResponse(
		expectedChallenge: string,
		credential: {
			id: string;
			credentialId: string;
			publicKey: Uint8Array;
			counter: number;
			transports: string[];
		},
		response: unknown,
	) {
		const verification = await verifyAuthenticationResponse({
			response: response as Parameters<typeof verifyAuthenticationResponse>[0]["response"],
			expectedChallenge,
			expectedOrigin: this.resolveExpectedOrigin(),
			expectedRPID: this.resolveRpId(),
			credential: {
				id: credential.credentialId,
				publicKey: new Uint8Array(credential.publicKey),
				counter: credential.counter,
				transports: this.normalizeTransports(credential.transports),
			},
			requireUserVerification: true,
		});

		if (!verification.verified) {
			throw new AppError(400, "passkey_login_failed", "Passkey verification failed.");
		}

		await this.prisma.userPasskeyCredential.update({
			where: { id: credential.id },
			data: {
				counter: verification.authenticationInfo.newCounter,
			},
		});
	}
}
