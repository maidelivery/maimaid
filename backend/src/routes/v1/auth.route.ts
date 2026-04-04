import { Hono, type Context } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { AuthEmailLinkContext, AuthService } from "../../services/auth.service.js";
import type { MfaService } from "../../services/mfa.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { isAppError } from "../../lib/errors.js";
import { isPasswordComplexEnough, MAX_PASSWORD_LENGTH, PASSWORD_COMPLEXITY_ERROR_MESSAGE } from "../../lib/auth-validation.js";
import type { AppEnv } from "../../types/hono.js";
import type { Env } from "../../env.js";
import type { RateLimitService } from "../../services/rate-limit.service.js";

const registerSchema = z.object({
	email: z.string().email(),
	password: z
		.string()
		.min(1)
		.max(MAX_PASSWORD_LENGTH)
		.refine((value) => isPasswordComplexEnough(value), {
			message: PASSWORD_COMPLEXITY_ERROR_MESSAGE,
		}),
	channel: z.enum(["web", "app"]).optional(),
	redirectUri: z.string().trim().optional(),
});

const loginSchema = z.object({
	email: z.string().email(),
	password: z.string().min(1).max(MAX_PASSWORD_LENGTH),
});
const refreshSchema = z.object({
	refreshToken: z.string().min(20),
});

const forgotPasswordSchema = z.object({
	email: z.string().email(),
	channel: z.enum(["web", "app"]).optional(),
	redirectUri: z.string().trim().optional(),
});

const resendVerificationSchema = z.object({
	email: z.string().email(),
	channel: z.enum(["web", "app"]).optional(),
	redirectUri: z.string().trim().optional(),
});

const resetPasswordSchema = z.object({
	token: z.string().min(20),
	newPassword: z
		.string()
		.min(1)
		.max(MAX_PASSWORD_LENGTH)
		.refine((value) => isPasswordComplexEnough(value), {
			message: PASSWORD_COMPLEXITY_ERROR_MESSAGE,
		}),
});

const passkeyLoginStartSchema = z.object({});

const passkeyLoginFinishSchema = z.object({
	challengeToken: z.string().min(20),
	response: z.unknown(),
});

const totpCodeSchema = z.object({
	code: z.string().min(6).max(8),
});

const mfaTotpLoginSchema = z.object({
	challengeToken: z.string().min(20),
	code: z.string().min(6).max(8),
});

const mfaBackupCodeLoginSchema = z.object({
	challengeToken: z.string().min(20),
	code: z.string().trim().min(6).max(32),
});

const mfaPasskeyLoginSchema = z.object({
	challengeToken: z.string().min(20),
	response: z.unknown(),
});

const mfaPasskeyStartSchema = z.object({
	challengeToken: z.string().min(20),
});

const passkeyFinishSchema = z.object({
	response: z.unknown(),
});

const passkeyRenameSchema = z.object({
	name: z.string().trim().min(1).max(64),
});

const verifyEmailQuerySchema = z.object({
	token: z.string().min(20),
});

const passwordResetQuerySchema = z.object({
	token: z.string().min(20),
});

const sessionExchangeSchema = z.object({
	sessionCode: z.string().min(20),
});

export const authV1Route = new Hono<AppEnv>();

const AUTH_RATE_LIMIT = {
	loginIp: { bucket: "auth.login.ip", limit: 10, windowSeconds: 60 },
	registerIp: { bucket: "auth.register.ip", limit: 5, windowSeconds: 600 },
	forgotIp: { bucket: "auth.forgot.ip", limit: 3, windowSeconds: 600 },
	forgotEmail: { bucket: "auth.forgot.email", limit: 3, windowSeconds: 600 },
	resendIp: { bucket: "auth.resend.ip", limit: 3, windowSeconds: 600 },
	resendEmail: { bucket: "auth.resend.email", limit: 3, windowSeconds: 600 },
	refreshIp: { bucket: "auth.refresh.ip", limit: 60, windowSeconds: 60 },
} as const;

const enforceRateLimit = async (
	c: Context<AppEnv>,
	input: {
		bucket: string;
		key: string;
		limit: number;
		windowSeconds: number;
	},
) => {
	const rateLimitService = di.resolve<RateLimitService>(TOKENS.RateLimitService);
	await rateLimitService.consume({
		bucket: input.bucket,
		key: input.key,
		limit: input.limit,
		windowSeconds: input.windowSeconds,
	});
};

authV1Route.post("/register", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const body = registerSchema.parse(await c.req.json());
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.registerIp,
		key: resolveClientIp(c),
	});
	const { user, verificationEmailSent } = await authService.register(
		body.email,
		body.password,
		resolveEmailLinkContext(body.channel, body.redirectUri, c.req.header("X-Maimaid-Client")),
	);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		verificationEmailSent,
	});
});

authV1Route.post("/login", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const body = loginSchema.parse(await c.req.json());
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.loginIp,
		key: resolveClientIp(c),
	});

	const user = await authService.validateLoginCredentials(body.email, body.password);
	const loginChannel = resolveLoginChannel(c.req.header("X-Maimaid-Client"));
	const requiresMfa = await mfaService.shouldEnforceMfa(user.id);
	if (requiresMfa) {
		const challenge = await mfaService.createLoginChallenge(user, loginChannel);
		return ok(c, {
			user: {
				id: user.id,
				email: user.email,
				isAdmin: user.isAdmin,
			},
			mfaRequired: true,
			challengeToken: challenge.challengeToken,
			methods: challenge.methods,
		});
	}
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.post("/refresh", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const body = refreshSchema.parse(await c.req.json());
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.refreshIp,
		key: resolveClientIp(c),
	});
	const { user, tokens } = await authService.refresh(body.refreshToken);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		...tokens,
	});
});

authV1Route.post("/session/exchange", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const body = sessionExchangeSchema.parse(await c.req.json());
	const { user, tokens } = await authService.exchangeSessionCode(body.sessionCode);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		...tokens,
	});
});

authV1Route.post("/session/create", authRequired, async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const user = await authService.findActiveUserById(auth.userId);
	if (!user.emailVerifiedAt) {
		return ok(c, { code: "email_not_verified", message: "Email is not verified." }, 403);
	}
	const sessionCode = await authService.createSessionCodeForUser(user.id);
	return ok(c, { sessionCode });
});

authV1Route.post("/logout", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const body = refreshSchema.parse(await c.req.json());
	await authService.logout(body.refreshToken);
	return ok(c, { success: true });
});

authV1Route.post("/resend-verification", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const body = resendVerificationSchema.parse(await c.req.json());
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.resendIp,
		key: resolveClientIp(c),
	});
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.resendEmail,
		key: body.email.trim().toLowerCase(),
	});
	const result = await authService.resendVerification(
		body.email,
		resolveEmailLinkContext(body.channel, body.redirectUri, c.req.header("X-Maimaid-Client")),
	);
	return ok(c, result);
});

authV1Route.get("/verify-email", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const query = c.req.query();
	const callbackTarget = resolveAuthCallbackTarget(query.client, query.redirect_uri);
	const parsed = verifyEmailQuerySchema.safeParse(query);
	if (!parsed.success) {
		return c.redirect(
			buildAuthCallbackUrl(callbackTarget, {
				action: "verify-email",
				status: "error",
				code: "invalid_verification_token",
			}),
			302,
		);
	}

	try {
		const user = await authService.verifyEmail(parsed.data.token);
		if (callbackTarget.kind === "app") {
			const sessionCode = await authService.createSessionCodeForUser(user.id);
			return c.redirect(
				buildAppSessionUrl(callbackTarget.redirectUri, {
					sessionCode,
				}),
				302,
			);
		}

		return c.redirect(
			buildAuthCallbackUrl(callbackTarget, {
				action: "verify-email",
				status: "success",
				code: "email_verified",
			}),
			302,
		);
	} catch (error) {
		if (isAppError(error) && error.code === "invalid_verification_token") {
			return c.redirect(
				buildAuthCallbackUrl(callbackTarget, {
					action: "verify-email",
					status: "error",
					code: "invalid_verification_token",
				}),
				302,
			);
		}
		throw error;
	}
});

authV1Route.get("/password-reset", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const query = c.req.query();
	const callbackTarget = resolveAuthCallbackTarget(query.client, query.redirect_uri);
	const parsed = passwordResetQuerySchema.safeParse(query);
	if (!parsed.success) {
		return c.redirect(
			buildPasswordResetCallbackUrl(callbackTarget, {
				action: "reset-password",
				status: "error",
				code: "invalid_reset_token",
			}),
			302,
		);
	}

	try {
		const resetContext = await authService.validatePasswordResetToken(parsed.data.token);
		return c.redirect(
			buildPasswordResetCallbackUrl(callbackTarget, {
				action: "reset-password",
				status: "success",
				code: "recovery_ready",
				token: parsed.data.token,
				email: resetContext.email,
			}),
			302,
		);
	} catch (error) {
		if (isAppError(error) && error.code === "invalid_reset_token") {
			return c.redirect(
				buildPasswordResetCallbackUrl(callbackTarget, {
					action: "reset-password",
					status: "error",
					code: "invalid_reset_token",
				}),
				302,
			);
		}
		throw error;
	}
});

authV1Route.post("/forgot-password", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const body = forgotPasswordSchema.parse(await c.req.json());
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.forgotIp,
		key: resolveClientIp(c),
	});
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.forgotEmail,
		key: body.email.trim().toLowerCase(),
	});
	const result = await authService.forgotPassword(
		body.email,
		resolveEmailLinkContext(body.channel, body.redirectUri, c.req.header("X-Maimaid-Client")),
	);
	return ok(c, { success: true, ...result });
});

authV1Route.post("/reset-password", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const body = resetPasswordSchema.parse(await c.req.json());
	await authService.resetPassword(body.token, body.newPassword);
	return ok(c, { success: true });
});

authV1Route.get("/mfa/status", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const status = await mfaService.status(auth.userId);
	return ok(c, status);
});

authV1Route.post("/mfa/totp/setup/start", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const user = await authService.findActiveUserById(auth.userId);
	const result = await mfaService.startTotpSetup(user);
	return ok(c, result);
});

authV1Route.post("/mfa/totp/setup/confirm", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const user = await authService.findActiveUserById(auth.userId);
	const body = totpCodeSchema.parse(await c.req.json());
	const result = await mfaService.confirmTotpSetup(user, body.code);
	return ok(c, result);
});

authV1Route.post("/mfa/totp/disable", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const result = await mfaService.disableTotp(auth.userId);
	return ok(c, result);
});

authV1Route.post("/mfa/passkey/register/start", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const user = await authService.findActiveUserById(auth.userId);
	const options = await mfaService.startPasskeyRegistration(user);
	return ok(c, options);
});

authV1Route.post("/mfa/passkey/register/finish", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const body = passkeyFinishSchema.parse(await c.req.json());
	const result = await mfaService.finishPasskeyRegistration(auth.userId, body.response);
	return ok(c, result);
});

authV1Route.get("/mfa/backup-codes", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const result = await mfaService.getBackupCodeStatus(auth.userId);
	return ok(c, result);
});

authV1Route.post("/mfa/backup-codes/regenerate", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const result = await mfaService.regenerateBackupCodes(auth.userId);
	return ok(c, result);
});

authV1Route.get("/mfa/passkeys", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const result = await mfaService.listPasskeys(auth.userId);
	return ok(c, result);
});

authV1Route.patch("/mfa/passkey/:credentialId", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const credentialId = c.req.param("credentialId");
	const body = passkeyRenameSchema.parse(await c.req.json());
	const result = await mfaService.renamePasskey(auth.userId, credentialId, body.name);
	return ok(c, result);
});

authV1Route.delete("/mfa/passkey/:credentialId", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const credentialId = c.req.param("credentialId");
	const result = await mfaService.removePasskey(auth.userId, credentialId);
	return ok(c, result);
});

authV1Route.post("/mfa/challenge/passkey/start", async (c) => {
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const body = mfaPasskeyStartSchema.parse(await c.req.json());
	const options = await mfaService.startPasskeyLogin(body.challengeToken);
	return ok(c, options);
});

authV1Route.post("/mfa/challenge/totp", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const body = mfaTotpLoginSchema.parse(await c.req.json());
	const user = await mfaService.verifyTotpLogin(body.challengeToken, body.code);
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.post("/mfa/challenge/backup-code", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const body = mfaBackupCodeLoginSchema.parse(await c.req.json());
	const user = await mfaService.verifyBackupCodeLogin(body.challengeToken, body.code);
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.post("/mfa/challenge/passkey", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const body = mfaPasskeyLoginSchema.parse(await c.req.json());
	const user = await mfaService.verifyPasskeyLogin(body.challengeToken, body.response);
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.post("/passkey/login/start", async (c) => {
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	passkeyLoginStartSchema.parse(await c.req.json());
	const payload = await mfaService.startDirectPasskeyLogin(resolveLoginChannel(c.req.header("X-Maimaid-Client")));
	return ok(c, payload);
});

authV1Route.post("/passkey/login/finish", async (c) => {
	const authService = di.resolve<AuthService>(TOKENS.AuthService);
	const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
	const body = passkeyLoginFinishSchema.parse(await c.req.json());
	const user = await mfaService.verifyPasskeyLogin(body.challengeToken, body.response);
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
		},
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.get("/me", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	return ok(c, {
		id: auth.userId,
		email: auth.email,
		isAdmin: auth.isAdmin,
	});
});

type AuthCallbackInput = {
	action: "verify-email" | "reset-password";
	status: "success" | "error";
	code: string;
	token?: string;
	email?: string;
};

type AuthCallbackTarget =
	| {
			kind: "dashboard";
	  }
	| {
			kind: "app";
			redirectUri: string;
	  };

const buildAuthCallbackUrl = (target: AuthCallbackTarget, input: AuthCallbackInput): string => {
	if (target.kind === "app") {
		return buildAppAuthUrl(target.redirectUri, input);
	}

	return buildDashboardAuthUrl(input);
};

const buildDashboardAuthUrl = (input: AuthCallbackInput): string => {
	const env = di.resolve<Env>(TOKENS.Env);
	const baseUrl = (env.WEBAUTHN_ORIGIN?.trim() || env.APP_PUBLIC_URL?.trim() || `http://localhost:${env.PORT}`).replace(
		/\/+$/u,
		"",
	);
	const callback = new URL(baseUrl);
	callback.searchParams.set("authAction", input.action);
	callback.searchParams.set("status", input.status);
	callback.searchParams.set("code", input.code);
	if (input.token) {
		callback.searchParams.set("token", input.token);
	}
	if (input.email) {
		callback.searchParams.set("email", input.email);
	}
	return callback.toString();
};

const buildPasswordResetCallbackUrl = (target: AuthCallbackTarget, input: AuthCallbackInput): string => {
	if (target.kind !== "app") {
		return buildAuthCallbackUrl(target, input);
	}

	const dashboardUrl = new URL(buildDashboardAuthUrl(input));
	dashboardUrl.searchParams.set("client", "ios");
	dashboardUrl.searchParams.set("authMode", "reset-password");
	dashboardUrl.searchParams.set("redirect_uri", target.redirectUri);
	return dashboardUrl.toString();
};

const buildAppAuthUrl = (redirectUri: string, input: AuthCallbackInput): string => {
	const callback = new URL(redirectUri);

	if (input.action === "reset-password") {
		callback.searchParams.set("type", "recovery");
		callback.searchParams.set("result", input.status);
		callback.searchParams.set("code", input.code);
		if (input.status === "success" && input.token) {
			callback.searchParams.set("token", input.token);
		}
		return callback.toString();
	}

	callback.searchParams.set("result", input.status);
	callback.searchParams.set("code", input.code);
	return callback.toString();
};

const buildAppSessionUrl = (
	redirectUri: string,
	payload: {
		sessionCode: string;
	},
): string => {
	const callback = new URL(redirectUri);
	callback.searchParams.set("type", "session");
	callback.searchParams.set("result", "success");
	callback.searchParams.set("sessionCode", payload.sessionCode);
	return callback.toString();
};

const resolveClientIp = (c: Context<AppEnv>): string => {
	const cfConnectingIp = c.req.header("cf-connecting-ip")?.trim();
	if (cfConnectingIp) {
		return cfConnectingIp;
	}

	const xForwardedFor = c.req.header("x-forwarded-for");
	if (xForwardedFor) {
		const first = xForwardedFor
			.split(",")
			.map((item) => item.trim())
			.find((item) => item.length > 0);
		if (first) {
			return first;
		}
	}

	const realIp = c.req.header("x-real-ip")?.trim();
	if (realIp) {
		return realIp;
	}

	return "unknown";
};

const resolveLoginChannel = (clientHeader: string | undefined): "web" | "app" => {
	return clientHeader?.trim().toLowerCase() === "web" ? "web" : "app";
};

const resolveEmailLinkContext = (
	channel: "web" | "app" | undefined,
	redirectUri: string | undefined,
	clientHeader: string | undefined,
): AuthEmailLinkContext => {
	if (channel === "app" || clientHeader?.trim().toLowerCase() === "app") {
		return {
			channel: "app",
			redirectUri: resolveAppRedirectUri(redirectUri),
		};
	}

	return {
		channel: "web",
	};
};

const resolveAuthCallbackTarget = (client: string | undefined, redirectUri: string | undefined): AuthCallbackTarget => {
	if ((client ?? "").trim().toLowerCase() !== "app") {
		return { kind: "dashboard" };
	}

	return {
		kind: "app",
		redirectUri: resolveAppRedirectUri(redirectUri),
	};
};

const resolveAppRedirectUri = (redirectUri: string | undefined): string => {
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
		// Fallback to default app callback URL.
	}

	return fallback;
};
