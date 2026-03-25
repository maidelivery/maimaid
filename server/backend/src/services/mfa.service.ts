import { inject, injectable } from "tsyringe";
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
  verifyRegistrationResponse
} from "@simplewebauthn/server";

type LoginChannel = "web" | "app";
type PasskeyTransport = "ble" | "cable" | "hybrid" | "internal" | "nfc" | "smart-card" | "usb";
const PASSKEY_TRANSPORTS = new Set<PasskeyTransport>([
  "ble",
  "cable",
  "hybrid",
  "internal",
  "nfc",
  "smart-card",
  "usb"
]);

@injectable()
export class MfaService {
  constructor(
    @inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
    @inject(TOKENS.Env) private readonly env: Env
  ) {}

  async status(userId: string) {
    const [totp, passkeys] = await Promise.all([
      this.prisma.userTotpCredential.findUnique({
        where: { userId }
      }),
      this.prisma.userPasskeyCredential.count({
        where: { userId }
      })
    ]);

    return {
      totpEnabled: Boolean(totp?.enabledAt),
      passkeyCount: passkeys,
      mfaEnabled: Boolean(totp?.enabledAt) || passkeys > 0
    };
  }

  async createLoginChallenge(user: User, channel: LoginChannel) {
    const methods = await this.listAvailableMethods(user.id);
    const challengeToken = randomToken(36);
    await this.prisma.mfaChallenge.create({
      data: {
        userId: user.id,
        tokenHash: sha256Hex(challengeToken),
        purpose: "login",
        channel,
        challenge: null,
        passkeyAllowIds: [],
        expiresAt: this.nextExpiry()
      }
    });
    return {
      challengeToken,
      methods
    };
  }

  async listAvailableMethods(userId: string) {
    const status = await this.status(userId);
    return {
      totp: status.totpEnabled,
      passkey: status.passkeyCount > 0
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
        enabledAt: null
      },
      create: {
        userId: user.id,
        secretBase32,
        enabledAt: null
      }
    });

    return {
      secretBase32,
      otpauthUrl: totp.toString()
    };
  }

  async confirmTotpSetup(user: User, code: string) {
    const credential = await this.prisma.userTotpCredential.findUnique({
      where: { userId: user.id }
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
        enabledAt: new Date()
      }
    });
    return { enabled: true };
  }

  async disableTotp(userId: string) {
    await this.prisma.userTotpCredential.deleteMany({
      where: { userId }
    });
    return { disabled: true };
  }

  async startPasskeyRegistration(user: User): Promise<Record<string, unknown>> {
    const existing = await this.prisma.userPasskeyCredential.findMany({
      where: { userId: user.id },
      select: { credentialId: true }
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
        userVerification: "preferred"
      },
      excludeCredentials: existing.map((item) => ({
        id: item.credentialId
      }))
    });

    await this.prisma.mfaChallenge.create({
      data: {
        userId: user.id,
        tokenHash: sha256Hex(randomToken(36)),
        purpose: "passkey_registration",
        channel: "web",
        challenge: options.challenge,
        passkeyAllowIds: [],
        expiresAt: this.nextExpiry()
      }
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
          gt: new Date()
        }
      },
      orderBy: { createdAt: "desc" }
    });
    if (!challenge?.challenge) {
      throw new AppError(400, "passkey_challenge_missing", "Passkey registration challenge not found.");
    }

    const verification = await verifyRegistrationResponse({
      response: response as Parameters<typeof verifyRegistrationResponse>[0]["response"],
      expectedChallenge: challenge.challenge,
      expectedOrigin: this.resolveExpectedOrigin(),
      expectedRPID: this.resolveRpId(),
      requireUserVerification: true
    });
    if (!verification.verified || !verification.registrationInfo) {
      throw new AppError(400, "passkey_registration_failed", "Passkey registration failed.");
    }

    await this.prisma.userPasskeyCredential.create({
      data: {
        userId,
        credentialId: verification.registrationInfo.credential.id,
        publicKey: Buffer.from(verification.registrationInfo.credential.publicKey),
        counter: verification.registrationInfo.credential.counter,
        transports: verification.registrationInfo.credential.transports ?? []
      }
    });

    await this.prisma.mfaChallenge.update({
      where: { id: challenge.id },
      data: {
        consumedAt: new Date()
      }
    });
    return { success: true };
  }

  async removePasskey(userId: string, credentialId: string) {
    const deleted = await this.prisma.userPasskeyCredential.deleteMany({
      where: {
        userId,
        credentialId
      }
    });
    return { deleted: deleted.count > 0 };
  }

  async startPasskeyLogin(challengeToken: string): Promise<Record<string, unknown>> {
    const challenge = await this.findLoginChallenge(challengeToken);
    const credentials = await this.prisma.userPasskeyCredential.findMany({
      where: { userId: challenge.userId }
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
        transports: this.normalizeTransports(item.transports)
      }))
    });

    await this.prisma.mfaChallenge.update({
      where: { id: challenge.id },
      data: {
        challenge: options.challenge,
        passkeyAllowIds: credentials.map((item) => item.credentialId)
      }
    });
    return options as unknown as Record<string, unknown>;
  }

  async verifyTotpLogin(challengeToken: string, code: string) {
    const challenge = await this.findLoginChallenge(challengeToken);
    const user = await this.prisma.user.findUnique({
      where: { id: challenge.userId }
    });
    if (!user) {
      throw new AppError(404, "user_not_found", "User not found.");
    }
    const credential = await this.prisma.userTotpCredential.findUnique({
      where: { userId: user.id }
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

  async verifyPasskeyLogin(challengeToken: string, response: unknown) {
    const challenge = await this.findLoginChallenge(challengeToken);
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
        credentialId
      }
    });
    if (!credential) {
      throw new AppError(404, "passkey_not_found", "Passkey credential not found.");
    }

    const verification = await verifyAuthenticationResponse({
      response: response as Parameters<typeof verifyAuthenticationResponse>[0]["response"],
      expectedChallenge: challenge.challenge,
      expectedOrigin: this.resolveExpectedOrigin(),
      expectedRPID: this.resolveRpId(),
      credential: {
        id: credential.credentialId,
        publicKey: new Uint8Array(credential.publicKey),
        counter: credential.counter,
        transports: this.normalizeTransports(credential.transports)
      },
      requireUserVerification: true
    });

    if (!verification.verified) {
      throw new AppError(400, "passkey_login_failed", "Passkey verification failed.");
    }

    await this.prisma.userPasskeyCredential.update({
      where: { id: credential.id },
      data: {
        counter: verification.authenticationInfo.newCounter
      }
    });
    await this.consumeChallenge(challenge.id);

    const user = await this.prisma.user.findUnique({
      where: { id: challenge.userId }
    });
    if (!user) {
      throw new AppError(404, "user_not_found", "User not found.");
    }
    return user;
  }

  async shouldEnforceMfa(userId: string, channel: LoginChannel) {
    if (channel !== "web") {
      return false;
    }
    const status = await this.status(userId);
    return status.mfaEnabled;
  }

  private async findLoginChallenge(challengeToken: string) {
    const tokenHash = sha256Hex(challengeToken.trim());
    const challenge = await this.prisma.mfaChallenge.findUnique({
      where: { tokenHash }
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
      data: { consumedAt: new Date() }
    });
  }

  private createTotp(secretBase32: string, email: string) {
    return new OTPAuth.TOTP({
      issuer: this.env.WEBAUTHN_RP_NAME,
      label: email,
      algorithm: "SHA1",
      digits: 6,
      period: 30,
      secret: OTPAuth.Secret.fromBase32(secretBase32)
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
}
