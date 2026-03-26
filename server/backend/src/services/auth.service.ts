import { compare, hash } from "bcryptjs";
import { inject, injectable } from "tsyringe";
import type { PrismaClient, User } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { JwtService } from "./jwt.service.js";
import type { Env } from "../env.js";
import { randomToken, sha256Hex } from "../lib/crypto.js";
import { isPasswordComplexEnough, PASSWORD_COMPLEXITY_ERROR_MESSAGE } from "../lib/auth-validation.js";

type TokenPair = {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
};

type AuthEmailType = "verify" | "reset";

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

  async register(email: string, password: string): Promise<{ user: User; verificationEmailSent: boolean }> {
    const normalized = this.normalizeEmail(email);
    this.validatePassword(password);

    const existed = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (existed) {
      throw new AppError(409, "email_exists", "Email already exists.");
    }

    const passwordHash = await hash(password, 12);
    const user = await this.prisma.$transaction(async (tx) => {
      const createdUser = await tx.user.create({
        data: {
          email: normalized,
          passwordHash
        }
      });

      await tx.profile.create({
        data: {
          userId: createdUser.id,
          name: "Default",
          server: "jp",
          isActive: true
        }
      });

      return createdUser;
    });

    const verificationEmailSent = await this.sendVerificationEmail(user.id, user.email, false);
    return { user, verificationEmailSent };
  }

  async login(email: string, password: string): Promise<{ user: User; tokens: TokenPair }> {
    const user = await this.validateLoginCredentials(email, password);
    const tokens = await this.issueTokensForUser(user);
    return { user, tokens };
  }

  async validateLoginCredentials(email: string, password: string): Promise<User> {
    const normalized = this.normalizeEmail(email);
    const user = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (!user || user.status !== "active") {
      throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
    }

    const ok = await compare(password, user.passwordHash);
    if (!ok) {
      throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
    }

    if (!user.emailVerifiedAt) {
      throw new AppError(403, "email_not_verified", "Email is not verified. Please check your inbox.");
    }

    return user;
  }

  async issueTokensForUser(user: User): Promise<TokenPair> {
    return this.issueTokenPair(user);
  }

  async findActiveUserById(userId: string): Promise<User> {
    const user = await this.prisma.user.findUnique({
      where: { id: userId }
    });
    if (!user || user.status !== "active") {
      throw new AppError(401, "invalid_credentials", "User is not active.");
    }
    return user;
  }

  async resendVerification(email: string): Promise<{ verificationEmailSent: boolean }> {
    const normalized = this.normalizeEmail(email);
    const user = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (!user) {
      throw new AppError(404, "email_not_registered", "Email is not registered.");
    }
    if (user.emailVerifiedAt) {
      throw new AppError(409, "email_already_verified", "Email is already verified.");
    }

    const verificationEmailSent = await this.sendVerificationEmail(user.id, user.email, true);
    return { verificationEmailSent };
  }

  async verifyEmail(token: string): Promise<void> {
    const normalizedToken = token.trim();
    if (!normalizedToken || normalizedToken.length < 20) {
      throw new AppError(400, "invalid_verification_token", "Verification token is invalid.");
    }

    const tokenHash = sha256Hex(normalizedToken);
    const record = await this.prisma.emailVerificationToken.findUnique({
      where: { tokenHash }
    });
    if (!record || record.consumedAt || record.expiresAt < new Date()) {
      throw new AppError(400, "invalid_verification_token", "Verification token is invalid.");
    }

    await this.prisma.$transaction([
      this.prisma.emailVerificationToken.update({
        where: { id: record.id },
        data: { consumedAt: new Date() }
      }),
      this.prisma.user.update({
        where: { id: record.userId },
        data: { emailVerifiedAt: new Date() }
      })
    ]);
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

  async forgotPassword(email: string): Promise<{ resetEmailSent: boolean }> {
    const normalized = this.normalizeEmail(email);
    const user = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (!user) {
      return { resetEmailSent: true };
    }

    const token = await this.createPasswordResetToken(user.id);
    const resetEmailSent = await this.sendResetPasswordEmail(user.email, token);
    return { resetEmailSent };
  }

  async findActiveVerifiedUserByEmail(email: string): Promise<User> {
    const normalized = this.normalizeEmail(email);
    const user = await this.prisma.user.findUnique({ where: { email: normalized } });
    if (!user || user.status !== "active") {
      throw new AppError(401, "invalid_credentials", "Email or password is incorrect.");
    }
    if (!user.emailVerifiedAt) {
      throw new AppError(403, "email_not_verified", "Email is not verified. Please check your inbox.");
    }
    return user;
  }

  async validatePasswordResetToken(token: string): Promise<void> {
    await this.getValidPasswordResetTokenRecord(token);
  }

  async resetPassword(token: string, newPassword: string): Promise<void> {
    this.validatePassword(newPassword);
    const record = await this.getValidPasswordResetTokenRecord(token);
    const user = await this.prisma.user.findUnique({
      where: { id: record.userId }
    });
    if (!user) {
      throw new AppError(404, "user_not_found", "User not found.");
    }

    const sameAsCurrent = await compare(newPassword, user.passwordHash);
    if (sameAsCurrent) {
      throw new AppError(400, "password_reused", "New password must be different from your current password.");
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
    if (!isPasswordComplexEnough(password)) {
      throw new AppError(400, "invalid_password", PASSWORD_COMPLEXITY_ERROR_MESSAGE);
    }
  }

  private async createPasswordResetToken(userId: string): Promise<string> {
    const token = randomToken();
    const tokenHash = sha256Hex(token);
    const expiresAt = new Date(Date.now() + 15 * 60_000);

    await this.prisma.passwordResetToken.create({
      data: {
        userId,
        tokenHash,
        expiresAt
      }
    });

    return token;
  }

  private async sendVerificationEmail(
    userId: string,
    email: string,
    enforceRateLimit: boolean
  ): Promise<boolean> {
    const token = await this.createEmailVerificationToken(userId, enforceRateLimit);
    const baseUrl = this.resolvePublicBaseUrl();
    const verifyUrl = `${baseUrl}/v1/auth/verify-email?token=${encodeURIComponent(token)}`;

    return this.sendEmail({
      to: email,
      subject: "maimaid email verification",
      html: this.renderAuthEmailTemplate({
        title: "Verify your email",
        description: "Confirm this email address to finish setting up your maimaid Dashboard account.",
        buttonLabel: "Verify email",
        actionUrl: verifyUrl
      }),
      type: "verify"
    });
  }

  private async sendResetPasswordEmail(email: string, token: string): Promise<boolean> {
    const baseUrl = this.resolvePublicBaseUrl();
    const resetUrl = `${baseUrl}/v1/auth/password-reset?token=${encodeURIComponent(token)}`;

    return this.sendEmail({
      to: email,
      subject: "maimaid password reset",
      html: this.renderAuthEmailTemplate({
        title: "Reset your password",
        description: "Use the link below to choose a new password for your maimaid Dashboard account.",
        buttonLabel: "Reset password",
        actionUrl: resetUrl
      }),
      type: "reset"
    });
  }

  private async sendEmail(input: { to: string; subject: string; html: string; type: AuthEmailType }): Promise<boolean> {
    if (!this.env.RESEND_API_KEY) {
      return false;
    }

    try {
      const response = await fetch("https://api.resend.com/emails", {
        method: "POST",
        headers: {
          Authorization: `Bearer ${this.env.RESEND_API_KEY}`,
          "Content-Type": "application/json"
        },
        body: JSON.stringify({
          from: this.env.RESEND_FROM_EMAIL,
          to: [input.to],
          subject: input.subject,
          html: input.html
        })
      });

      return response.ok;
    } catch {
      return false;
    }
  }

  private renderAuthEmailTemplate(input: {
    title: string;
    description: string;
    buttonLabel: string;
    actionUrl: string;
  }): string {
    const escapedUrl = input.actionUrl;
    return (
      "<!doctype html>" +
      '<html lang="en">' +
      "<head>" +
      '<meta charset="utf-8" />' +
      '<meta name="viewport" content="width=device-width, initial-scale=1" />' +
      `<title>${input.title}</title>` +
      "</head>" +
      '<body style="margin:0;padding:24px;background:#f4f4f5;color:#111827;font-family:-apple-system,BlinkMacSystemFont,\'Segoe UI\',Roboto,Helvetica,Arial,sans-serif;">' +
      '<table role="presentation" width="100%" cellspacing="0" cellpadding="0">' +
      "<tr><td align=\"center\">" +
      '<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="max-width:520px;background:#ffffff;border:1px solid #e4e4e7;border-radius:12px;padding:24px;">' +
      `<tr><td style="font-size:20px;line-height:1.3;font-weight:600;padding-bottom:12px;">${input.title}</td></tr>` +
      `<tr><td style="font-size:14px;line-height:1.6;color:#3f3f46;padding-bottom:20px;">${input.description}</td></tr>` +
      "<tr><td style=\"padding-bottom:16px;\">" +
      `<a href="${escapedUrl}" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;font-size:14px;font-weight:600;padding:10px 14px;border-radius:8px;">${input.buttonLabel}</a>` +
      "</td></tr>" +
      `<tr><td style="font-size:12px;line-height:1.6;color:#71717a;word-break:break-all;">If the button does not work, open this link:<br/><a href="${escapedUrl}" style="color:#2563eb;text-decoration:underline;">${escapedUrl}</a></td></tr>` +
      '<tr><td style="font-size:12px;line-height:1.6;color:#a1a1aa;padding-top:16px;">If you did not request this email, you can ignore it.</td></tr>' +
      "</table>" +
      "</td></tr>" +
      "</table>" +
      "</body>" +
      "</html>"
    );
  }

  private async createEmailVerificationToken(userId: string, enforceRateLimit: boolean): Promise<string> {
    if (enforceRateLimit) {
      const minuteAgo = new Date(Date.now() - 60_000);
      const recentCount = await this.prisma.emailVerificationToken.count({
        where: {
          userId,
          createdAt: {
            gte: minuteAgo
          }
        }
      });
      if (recentCount > 0) {
        throw new AppError(429, "email_rate_limited", "You can request only one auth email per minute.");
      }
    }

    const token = randomToken();
    const tokenHash = sha256Hex(token);
    const expiresAt = new Date(Date.now() + 24 * 60 * 60_000);
    await this.prisma.emailVerificationToken.create({
      data: {
        userId,
        tokenHash,
        expiresAt
      }
    });
    return token;
  }

  private async getValidPasswordResetTokenRecord(token: string) {
    const normalizedToken = token.trim();
    if (!normalizedToken || normalizedToken.length < 20) {
      throw new AppError(400, "invalid_reset_token", "Password reset token is invalid.");
    }

    const tokenHash = sha256Hex(normalizedToken);
    const record = await this.prisma.passwordResetToken.findUnique({
      where: { tokenHash }
    });
    if (!record || record.consumedAt || record.expiresAt < new Date()) {
      throw new AppError(400, "invalid_reset_token", "Password reset token is invalid.");
    }

    return record;
  }

  private resolvePublicBaseUrl(): string {
    const raw = this.env.APP_PUBLIC_URL?.trim() || `http://localhost:${this.env.PORT}`;
    return raw.replace(/\/+$/, "");
  }
}
