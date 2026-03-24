import { compare, hash } from "bcryptjs";
import { inject, injectable } from "tsyringe";
import type { PrismaClient, User } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { JwtService } from "./jwt.service.js";
import type { Env } from "../env.js";
import { randomToken, sha256Hex } from "../lib/crypto.js";

type TokenPair = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

@injectable()
export class AuthService {
  constructor(
    @inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
    @inject(TOKENS.JwtService) private readonly jwtService: JwtService,
    @inject(TOKENS.Env) private readonly env: Env
  ) {}

  async emailExists(email: string): Promise<boolean> {
    const normalized = email.trim().toLowerCase();
    if (!normalized) {
      return false;
    }
    const count = await this.prisma.user.count({
      where: { email: normalized }
    });
    return count > 0;
  }

  async register(email: string, password: string): Promise<{ user: User; tokens: TokenPair }> {
    const normalized = this.normalizeEmail(email);
    this.validatePassword(password);

    const existed = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (existed) {
      throw new AppError(409, "email_exists", "Email already exists.");
    }

    const passwordHash = await hash(password, 12);
    const user = await this.prisma.user.create({
      data: {
        email: normalized,
        passwordHash
      }
    });

    await this.prisma.profile.create({
      data: {
        userId: user.id,
        name: "Default",
        server: "jp",
        isActive: true
      }
    });

    const tokens = await this.issueTokenPair(user);
    return { user, tokens };
  }

  async login(email: string, password: string): Promise<{ user: User; tokens: TokenPair }> {
    const normalized = this.normalizeEmail(email);
    const user = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (!user || user.status !== "active") {
      throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
    }

    const ok = await compare(password, user.passwordHash);
    if (!ok) {
      throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
    }

    const tokens = await this.issueTokenPair(user);
    return { user, tokens };
  }

  async refresh(refreshToken: string): Promise<{ user: User; tokens: TokenPair }> {
    const refreshHash = sha256Hex(refreshToken);
    const record = await this.prisma.refreshToken.findUnique({
      where: { tokenHash: refreshHash },
      include: { user: true }
    });
    if (!record || record.revokedAt || record.expiresAt < new Date() || record.user.status !== "active") {
      throw new AppError(401, "invalid_refresh_token", "Invalid refresh token.");
    }

    await this.prisma.refreshToken.update({
      where: { id: record.id },
      data: { revokedAt: new Date() }
    });

    const tokens = await this.issueTokenPair(record.user);
    return { user: record.user, tokens };
  }

  async logout(refreshToken: string): Promise<void> {
    const refreshHash = sha256Hex(refreshToken);
    await this.prisma.refreshToken.updateMany({
      where: {
        tokenHash: refreshHash,
        revokedAt: null
      },
      data: {
        revokedAt: new Date()
      }
    });
  }

  async forgotPassword(email: string): Promise<void> {
    const normalized = this.normalizeEmail(email);
    const user = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (!user) {
      return;
    }
    const token = randomToken();
    const tokenHash = sha256Hex(token);
    const expiresAt = new Date(Date.now() + 15 * 60_000);

    await this.prisma.passwordResetToken.create({
      data: {
        userId: user.id,
        tokenHash,
        expiresAt
      }
    });

    if (!this.env.RESEND_API_KEY) {
      return;
    }

    await fetch("https://api.resend.com/emails", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${this.env.RESEND_API_KEY}`,
        "Content-Type": "application/json"
      },
      body: JSON.stringify({
        from: this.env.RESEND_FROM_EMAIL,
        to: [user.email],
        subject: "maimaid password reset",
        html: `<p>Use this token to reset your password: <b>${token}</b></p>`
      })
    });
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    this.validatePassword(newPassword);
    const tokenHash = sha256Hex(token);
    const record = await this.prisma.passwordResetToken.findUnique({
      where: { tokenHash }
    });
    if (!record || record.consumedAt || record.expiresAt < new Date()) {
      throw new AppError(400, "invalid_reset_token", "Password reset token is invalid.");
    }

    const passwordHash = await hash(newPassword, 12);
    await this.prisma.$transaction([
      this.prisma.user.update({
        where: { id: record.userId },
        data: { passwordHash }
      }),
      this.prisma.passwordResetToken.update({
        where: { id: record.id },
        data: { consumedAt: new Date() }
      })
    ]);
  }

  private async issueTokenPair(user: User): Promise<TokenPair> {
    const accessToken = await this.jwtService.signAccessToken({
      sub: user.id,
      email: user.email,
      isAdmin: user.isAdmin
    });

    const refreshToken = randomToken();
    const refreshHash = sha256Hex(refreshToken);
    const expiresAt = new Date(Date.now() + this.env.JWT_REFRESH_TTL_SECONDS * 1000);

    await this.prisma.refreshToken.create({
      data: {
        userId: user.id,
        tokenHash: refreshHash,
        expiresAt
      }
    });

    return {
      accessToken,
      refreshToken,
      expiresIn: this.env.JWT_ACCESS_TTL_SECONDS
    };
  }

  private normalizeEmail(email: string): string {
    const normalized = email.trim().toLowerCase();
    if (!normalized || !normalized.includes("@")) {
      throw new AppError(400, "invalid_email", "A valid email is required.");
    }
    return normalized;
  }

  private validatePassword(password: string): void {
    if (password.length < 8) {
      throw new AppError(400, "invalid_password", "Password must contain at least 8 characters.");
    }
  }
}
