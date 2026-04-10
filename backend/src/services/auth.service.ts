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

const APP_SESSION_CODE_TTL_SECONDS = 120;
const EMAIL_VERIFICATION_TOKEN_TTL_MS = 15 * 60_000;

type AuthEmailType = "verify" | "reset";
type AuthChannel = "web" | "app";

export type AuthEmailLinkContext = {
	channel?: AuthChannel;
	redirectUri?: string;
};

@injectable()
export class AuthService {
	constructor(
		@inject(TOKENS.Prisma) private readonly prisma: PrismaClient,
		@inject(JwtService) private readonly jwtService: JwtService,
		@inject(TOKENS.Env) private readonly env: Env,
	) {}

	async register(
		email: string,
		password: string,
		emailLinkContext?: AuthEmailLinkContext,
	): Promise<{ user: User; verificationEmailSent: boolean }> {
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
					passwordHash,
				},
			});

			await tx.profile.create({
				data: {
					userId: createdUser.id,
					name: "Default",
					server: "jp",
					isActive: true,
				},
			});

			return createdUser;
		});

		const verificationEmailSent = await this.sendVerificationEmail(user.id, user.email, false, emailLinkContext);
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
			where: { id: userId },
		});
		if (!user || user.status !== "active") {
			throw new AppError(401, "invalid_credentials", "User is not active.");
		}
		return user;
	}

	async resendVerification(
		email: string,
		emailLinkContext?: AuthEmailLinkContext,
	): Promise<{ verificationEmailSent: boolean }> {
		const normalized = this.normalizeEmail(email);
		const user = await this.prisma.user.findUnique({ where: { email: normalized } });
		if (!user || user.emailVerifiedAt) {
			return { verificationEmailSent: true };
		}

		await this.sendVerificationEmail(user.id, user.email, false, emailLinkContext);
		return { verificationEmailSent: true };
	}

	async verifyEmail(token: string): Promise<User> {
		const normalizedToken = token.trim();
		if (!normalizedToken || normalizedToken.length < 20) {
			throw new AppError(400, "invalid_verification_token", "Verification token is invalid.");
		}

		const tokenHash = await sha256Hex(normalizedToken);
		const record = await this.prisma.emailVerificationToken.findUnique({
			where: { tokenHash },
		});
		if (!record || record.consumedAt || record.expiresAt < new Date()) {
			throw new AppError(400, "invalid_verification_token", "Verification token is invalid.");
		}

		const user = await this.prisma.$transaction(async (tx) => {
			await tx.emailVerificationToken.update({
				where: { id: record.id },
				data: { consumedAt: new Date() },
			});

			return tx.user.update({
				where: { id: record.userId },
				data: { emailVerifiedAt: new Date() },
			});
		});

		return user;
	}

	async refresh(refreshToken: string): Promise<{ user: User; tokens: TokenPair }> {
		const refreshHash = await sha256Hex(refreshToken);
		const record = await this.prisma.refreshToken.findUnique({
			where: { tokenHash: refreshHash },
			include: { user: true },
		});
		if (!record || record.revokedAt || record.expiresAt < new Date() || record.user.status !== "active") {
			throw new AppError(401, "invalid_refresh_token", "Invalid refresh token.");
		}

		await this.prisma.refreshToken.update({
			where: { id: record.id },
			data: { revokedAt: new Date() },
		});

		const tokens = await this.issueTokenPair(record.user);
		return { user: record.user, tokens };
	}

	async logout(refreshToken: string): Promise<void> {
		const refreshHash = await sha256Hex(refreshToken);
		await this.prisma.refreshToken.updateMany({
			where: {
				tokenHash: refreshHash,
				revokedAt: null,
			},
			data: {
				revokedAt: new Date(),
			},
		});
	}

	async forgotPassword(email: string, emailLinkContext?: AuthEmailLinkContext): Promise<{ resetEmailSent: boolean }> {
		const normalized = this.normalizeEmail(email);
		const user = await this.prisma.user.findUnique({ where: { email: normalized } });
		if (!user) {
			return { resetEmailSent: true };
		}

		const token = await this.createPasswordResetToken(user.id);
		const resetEmailSent = await this.sendResetPasswordEmail(user.email, token, emailLinkContext);
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

	async validatePasswordResetToken(token: string): Promise<{ email: string }> {
		const record = await this.getValidPasswordResetTokenRecord(token);
		const user = await this.prisma.user.findUnique({
			where: { id: record.userId },
			select: { email: true, status: true },
		});
		if (!user || user.status !== "active") {
			throw new AppError(400, "invalid_reset_token", "Password reset token is invalid.");
		}
		return { email: user.email };
	}

	async resetPassword(token: string, newPassword: string): Promise<void> {
		this.validatePassword(newPassword);
		const record = await this.getValidPasswordResetTokenRecord(token);
		const user = await this.prisma.user.findUnique({
			where: { id: record.userId },
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
				data: { passwordHash },
			}),
			this.prisma.refreshToken.updateMany({
				where: {
					userId: record.userId,
					revokedAt: null,
				},
				data: {
					revokedAt: new Date(),
				},
			}),
			this.prisma.passwordResetToken.update({
				where: { id: record.id },
				data: { consumedAt: new Date() },
			}),
		]);
	}

	async createSessionCodeForUser(userId: string): Promise<string> {
		const sessionCode = randomToken(36);
		const codeHash = await sha256Hex(sessionCode);
		const expiresAt = new Date(Date.now() + APP_SESSION_CODE_TTL_SECONDS * 1000);

		await this.prisma.authSessionCode.create({
			data: {
				userId,
				codeHash,
				expiresAt,
			},
		});

		return sessionCode;
	}

	async exchangeSessionCode(sessionCode: string): Promise<{ user: User; tokens: TokenPair }> {
		const record = await this.consumeSessionCode(sessionCode);
		const user = record.user;
		if (user.status !== "active") {
			throw new AppError(401, "invalid_session_code", "Session code is invalid.");
		}
		if (!user.emailVerifiedAt) {
			throw new AppError(403, "email_not_verified", "Email is not verified. Please check your inbox.");
		}
		const tokens = await this.issueTokenPair(user);
		return { user, tokens };
	}

	private async issueTokenPair(user: User): Promise<TokenPair> {
		const accessToken = await this.jwtService.signAccessToken({
			sub: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		});

		const refreshToken = randomToken();
		const refreshHash = await sha256Hex(refreshToken);
		const expiresAt = new Date(Date.now() + this.env.JWT_REFRESH_TTL_SECONDS * 1000);

		await this.prisma.refreshToken.create({
			data: {
				userId: user.id,
				tokenHash: refreshHash,
				expiresAt,
			},
		});

		return {
			accessToken,
			refreshToken,
			expiresIn: this.env.JWT_ACCESS_TTL_SECONDS,
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
		const tokenHash = await sha256Hex(token);
		const expiresAt = new Date(Date.now() + 15 * 60_000);

		await this.prisma.passwordResetToken.create({
			data: {
				userId,
				tokenHash,
				expiresAt,
			},
		});

		return token;
	}

	private async sendVerificationEmail(
		userId: string,
		email: string,
		enforceRateLimit: boolean,
		emailLinkContext?: AuthEmailLinkContext,
	): Promise<boolean> {
		const token = await this.createEmailVerificationToken(userId, enforceRateLimit);
		const verifyUrl = this.buildAuthActionUrl("verify-email", token, emailLinkContext);

		return this.sendEmail({
			to: email,
			subject: "maimaid email verification",
			html: this.renderAuthEmailTemplate({
				title: "Verify your email",
				description: "Confirm this email address to finish setting up your maimaid Dashboard account.",
				buttonLabel: "Verify email",
				actionUrl: verifyUrl,
			}),
			type: "verify",
		});
	}

	private async sendResetPasswordEmail(
		email: string,
		token: string,
		emailLinkContext?: AuthEmailLinkContext,
	): Promise<boolean> {
		const resetUrl = this.buildAuthActionUrl("password-reset", token, emailLinkContext);

		return this.sendEmail({
			to: email,
			subject: "maimaid password reset",
			html: this.renderAuthEmailTemplate({
				title: "Reset your password",
				description: "Use the link below to choose a new password for your maimaid Dashboard account.",
				buttonLabel: "Reset password",
				actionUrl: resetUrl,
			}),
			type: "reset",
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
					"Content-Type": "application/json",
				},
				body: JSON.stringify({
					from: this.env.RESEND_FROM_EMAIL,
					to: [input.to],
					subject: input.subject,
					html: input.html,
				}),
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
		const escapedTitle = this.escapeHtml(input.title);
		const escapedDescription = this.escapeHtml(input.description);
		const escapedButtonLabel = this.escapeHtml(input.buttonLabel);
		const escapedUrl = this.escapeHtml(input.actionUrl);

		return [
			"<!doctype html>",
			'<html lang="en">',
			"<head>",
			'<meta charset="utf-8" />',
			'<meta name="viewport" content="width=device-width, initial-scale=1" />',
			`<title>${escapedTitle}</title>`,
			"</head>",
			'<body style="margin:0;padding:0;background:#f4f4f5;">',
			'<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;border-collapse:collapse;background:#f4f4f5;">',
			"<tr>",
			'<td align="center" style="padding:24px 12px;">',
			'<table role="presentation" width="100%" cellspacing="0" cellpadding="0" style="width:100%;max-width:560px;border-collapse:separate;background:#ffffff;border:1px solid #e4e4e7;border-radius:12px;">',
			"<tr>",
			"<td style=\"padding:24px 24px 0 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#111827;font-size:22px;line-height:1.3;font-weight:700;\">",
			escapedTitle,
			"</td>",
			"</tr>",
			"<tr>",
			"<td style=\"padding:12px 24px 0 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#3f3f46;font-size:15px;line-height:1.6;\">",
			escapedDescription,
			"</td>",
			"</tr>",
			"<tr>",
			'<td style="padding:20px 24px 0 24px;">',
			`<a href="${escapedUrl}" style="display:inline-block;background:#111827;color:#ffffff;text-decoration:none;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:15px;line-height:1;font-weight:700;padding:12px 18px;border-radius:10px;">${escapedButtonLabel}</a>`,
			"</td>",
			"</tr>",
			"<tr>",
			"<td style=\"padding:16px 24px 0 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#0f5132;font-size:13px;line-height:1.7;\">",
			"If the button does not work, open this link:",
			"</td>",
			"</tr>",
			"<tr>",
			"<td style=\"padding:4px 24px 0 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:13px;line-height:1.7;word-break:break-all;\">",
			`<a href="${escapedUrl}" style="color:#2563eb;text-decoration:underline;">${escapedUrl}</a>`,
			"</td>",
			"</tr>",
			"<tr>",
			"<td style=\"padding:16px 24px 24px 24px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#71717a;font-size:12px;line-height:1.7;\">",
			"If you did not request this email, you can ignore it.",
			"</td>",
			"</tr>",
			"</table>",
			"</td>",
			"</tr>",
			"</table>",
			"</body>",
			"</html>",
		].join("");
	}

	private async createEmailVerificationToken(userId: string, enforceRateLimit: boolean): Promise<string> {
		if (enforceRateLimit) {
			const minuteAgo = new Date(Date.now() - 60_000);
			const recentCount = await this.prisma.emailVerificationToken.count({
				where: {
					userId,
					createdAt: {
						gte: minuteAgo,
					},
				},
			});
			if (recentCount > 0) {
				throw new AppError(429, "email_rate_limited", "You can request only one auth email per minute.");
			}
		}

		const token = randomToken();
		const tokenHash = await sha256Hex(token);
		const expiresAt = new Date(Date.now() + EMAIL_VERIFICATION_TOKEN_TTL_MS);
		await this.prisma.emailVerificationToken.create({
			data: {
				userId,
				tokenHash,
				expiresAt,
			},
		});
		return token;
	}

	private async getValidPasswordResetTokenRecord(token: string) {
		const normalizedToken = token.trim();
		if (!normalizedToken || normalizedToken.length < 20) {
			throw new AppError(400, "invalid_reset_token", "Password reset token is invalid.");
		}

		const tokenHash = await sha256Hex(normalizedToken);
		const record = await this.prisma.passwordResetToken.findUnique({
			where: { tokenHash },
		});
		if (!record || record.consumedAt || record.expiresAt < new Date()) {
			throw new AppError(400, "invalid_reset_token", "Password reset token is invalid.");
		}

		return record;
	}

	private async consumeSessionCode(sessionCode: string) {
		const normalizedCode = sessionCode.trim();
		if (!normalizedCode || normalizedCode.length < 20) {
			throw new AppError(400, "invalid_session_code", "Session code is invalid.");
		}

		const codeHash = await sha256Hex(normalizedCode);
		const record = await this.prisma.authSessionCode.findUnique({
			where: { codeHash },
			include: { user: true },
		});

		if (!record || record.consumedAt || record.expiresAt < new Date()) {
			throw new AppError(400, "invalid_session_code", "Session code is invalid.");
		}

		const consumed = await this.prisma.authSessionCode.updateMany({
			where: {
				id: record.id,
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
			throw new AppError(400, "invalid_session_code", "Session code is invalid.");
		}

		return record;
	}

	private resolvePublicBaseUrl(): string {
		const raw = this.env.APP_PUBLIC_URL?.trim() || `http://localhost:${this.env.PORT}`;
		return raw.replace(/\/+$/, "");
	}

	private escapeHtml(value: string): string {
		return value
			.replaceAll("&", "&amp;")
			.replaceAll("<", "&lt;")
			.replaceAll(">", "&gt;")
			.replaceAll('"', "&quot;")
			.replaceAll("'", "&#39;");
	}

	private buildAuthActionUrl(
		action: "verify-email" | "password-reset",
		token: string,
		emailLinkContext?: AuthEmailLinkContext,
	): string {
		const baseUrl = this.resolvePublicBaseUrl();
		const url = new URL(`${baseUrl}/v1/auth/${action}`);
		url.searchParams.set("token", token);

		if (emailLinkContext?.channel === "app") {
			url.searchParams.set("client", "app");
			url.searchParams.set("redirect_uri", this.resolveAppRedirectUri(emailLinkContext.redirectUri));
		}

		return url.toString();
	}

	private resolveAppRedirectUri(redirectUri?: string): string {
		const fallback = "maimaid://auth/callback";
		const trimmed = redirectUri?.trim() ?? "";
		if (!trimmed) {
			return fallback;
		}

		try {
			const parsed = new URL(trimmed);
			const isAllowedRedirect =
				parsed.protocol === "maimaid:" &&
				parsed.hostname === "auth" &&
				(parsed.pathname === "/callback" || parsed.pathname === "/callback/") &&
				!parsed.search &&
				!parsed.hash;

			if (isAllowedRedirect) {
				return fallback;
			}
		} catch {
			// Fallback to the default app callback URL.
		}

		return fallback;
	}
}
