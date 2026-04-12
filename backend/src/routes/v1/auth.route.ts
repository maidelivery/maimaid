import { Hono, type Context } from "hono";
import { z } from "zod";
import { TOKENS } from "../../di/tokens.js";
import { AuthEmailLinkContext, AuthService } from "../../services/auth.service.js";
import { MfaService } from "../../services/mfa.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook, type ValidationHook } from "../../http/validation.js";
import { isAppError } from "../../lib/errors.js";
import { isPasswordComplexEnough, MAX_PASSWORD_LENGTH, PASSWORD_COMPLEXITY_ERROR_MESSAGE } from "../../lib/auth-validation.js";
import type { AppEnv } from "../../types/hono.js";
import type { Env } from "../../env.js";
import { RateLimitService } from "../../services/rate-limit.service.js";
import { container } from "tsyringe";
import {
	INVALID_USERNAME_MESSAGE,
	USERNAME_MAX_LENGTH,
	USERNAME_MIN_LENGTH,
	serializeUserIdentity,
} from "../../lib/user-handle.js";

const registerSchema = z.object({
	email: z.email(),
	username: z.string().trim().min(USERNAME_MIN_LENGTH).max(USERNAME_MAX_LENGTH),
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

const opaquePayloadSchema = z
	.string()
	.trim()
	.min(1)
	.refine((value) => !value.includes("__proto__"), "Opaque payload is invalid.");

const registerStartSchema = z.object({
	email: z.email(),
	registrationRequest: opaquePayloadSchema,
});

const registerFinishSchema = z.object({
	email: z.email(),
	username: z.string().trim().min(USERNAME_MIN_LENGTH).max(USERNAME_MAX_LENGTH),
	registrationRecord: opaquePayloadSchema,
	passwordFingerprint: opaquePayloadSchema,
	channel: z.enum(["web", "app"]).optional(),
	redirectUri: z.string().trim().optional(),
});

const loginSchema = z.object({
	email: z.email(),
	password: z.string().min(1).max(MAX_PASSWORD_LENGTH),
});

const loginStartSchema = z.object({
	email: z.email(),
	startLoginRequest: opaquePayloadSchema,
});

const loginFinishSchema = z.object({
	challengeToken: z.string().min(20),
	finishLoginRequest: opaquePayloadSchema,
});

const refreshSchema = z.object({
	refreshToken: z.string().min(20),
});

const forgotPasswordSchema = z.object({
	email: z.email(),
	channel: z.enum(["web", "app"]).optional(),
	redirectUri: z.string().trim().optional(),
});

const resendVerificationSchema = z.object({
	email: z.email(),
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

const resetPasswordStartSchema = z.object({
	token: z.string().min(20),
	registrationRequest: opaquePayloadSchema,
});

const resetPasswordFinishSchema = z.object({
	token: z.string().min(20),
	registrationRecord: opaquePayloadSchema,
	passwordFingerprint: opaquePayloadSchema,
});

const enrollOpaqueStartSchema = z.object({
	registrationRequest: opaquePayloadSchema,
});

const enrollOpaqueFinishSchema = z.object({
	registrationRecord: opaquePayloadSchema,
	passwordFingerprint: opaquePayloadSchema,
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

const updateMeSchema = z.object({
	username: z
		.string()
		.trim()
		.min(USERNAME_MIN_LENGTH, INVALID_USERNAME_MESSAGE)
		.max(USERNAME_MAX_LENGTH, INVALID_USERNAME_MESSAGE),
});

const credentialIdParamSchema = z.object({
	credentialId: z.string().min(1),
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
	const rateLimitService = container.resolve(RateLimitService);
	await rateLimitService.consume({
		bucket: input.bucket,
		key: input.key,
		limit: input.limit,
		windowSeconds: input.windowSeconds,
	});
};

const invalidVerifyEmailQueryHook: ValidationHook<z.infer<typeof verifyEmailQuerySchema>, AppEnv> = (result, c) => {
	if (result.success) {
		return;
	}

	const rawQuery = result.data as Record<string, string | undefined>;
	const callbackTarget = resolveAuthCallbackTarget(rawQuery.client, rawQuery.redirect_uri);
	return c.redirect(
		buildAuthCallbackUrl(callbackTarget, {
			action: "verify-email",
			status: "error",
			code: "invalid_verification_token",
		}),
		302,
	);
};

const invalidPasswordResetQueryHook: ValidationHook<z.infer<typeof passwordResetQuerySchema>, AppEnv> = (result, c) => {
	if (result.success) {
		return;
	}

	const rawQuery = result.data as Record<string, string | undefined>;
	const callbackTarget = resolveAuthCallbackTarget(rawQuery.client, rawQuery.redirect_uri);
	return c.redirect(
		buildPasswordResetCallbackUrl(callbackTarget, {
			action: "reset-password",
			status: "error",
			code: "invalid_reset_token",
		}),
		302,
	);
};

authV1Route.post("/register", standardValidator("json", registerSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.registerIp,
		key: resolveClientIp(c),
	});
	const { user, verificationEmailSent } = await authService.register(
		body.email,
		body.password,
		body.username,
		resolveEmailLinkContext(body.channel, body.redirectUri, c.req.header("X-Maimaid-Client")),
	);
	return ok(c, {
		user: serializeAuthUser(user),
		verificationEmailSent,
	});
});

authV1Route.post("/register:start", standardValidator("json", registerStartSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.registerIp,
		key: resolveClientIp(c),
	});
	const payload = await authService.startOpaqueRegistration(body.email, body.registrationRequest);
	return ok(c, payload);
});

authV1Route.post("/register:finish", standardValidator("json", registerFinishSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	const { user, verificationEmailSent } = await authService.finishOpaqueRegistration(
		body.email,
		body.username,
		body.registrationRecord,
		body.passwordFingerprint,
		resolveEmailLinkContext(body.channel, body.redirectUri, c.req.header("X-Maimaid-Client")),
	);
	return ok(c, {
		user: serializeAuthUser(user),
		verificationEmailSent,
	});
});

authV1Route.post("/login", standardValidator("json", loginSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const mfaService = c.var.resolve(MfaService);
	const body = c.req.valid("json");
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
			user: serializeAuthUser(user),
			mfaRequired: true,
			challengeToken: challenge.challengeToken,
			methods: challenge.methods,
		});
	}
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: serializeAuthUser(user),
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.post("/login:start", standardValidator("json", loginStartSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.loginIp,
		key: resolveClientIp(c),
	});
	const payload = await authService.startOpaqueLogin(body.email, body.startLoginRequest);
	return ok(c, payload);
});

authV1Route.post("/login:finish", standardValidator("json", loginFinishSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const mfaService = c.var.resolve(MfaService);
	const body = c.req.valid("json");
	const user = await authService.finishOpaqueLogin(body.challengeToken, body.finishLoginRequest);
	const loginChannel = resolveLoginChannel(c.req.header("X-Maimaid-Client"));
	const requiresMfa = await mfaService.shouldEnforceMfa(user.id);
	if (requiresMfa) {
		const challenge = await mfaService.createLoginChallenge(user, loginChannel);
		return ok(c, {
			user: serializeAuthUser(user),
			mfaRequired: true,
			challengeToken: challenge.challengeToken,
			methods: challenge.methods,
		});
	}
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: serializeAuthUser(user),
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.post("/refresh", standardValidator("json", refreshSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	await enforceRateLimit(c, {
		...AUTH_RATE_LIMIT.refreshIp,
		key: resolveClientIp(c),
	});
	const { user, tokens } = await authService.refresh(body.refreshToken);
	return ok(c, {
		user: serializeAuthUser(user),
		...tokens,
	});
});

authV1Route.post("/session:exchange", standardValidator("json", sessionExchangeSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	const { user, tokens } = await authService.exchangeSessionCode(body.sessionCode);
	return ok(c, {
		user: serializeAuthUser(user),
		...tokens,
	});
});

authV1Route.post("/session:create", authRequired, async (c) => {
	const authService = c.var.resolve(AuthService);
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

authV1Route.post("/logout", standardValidator("json", refreshSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	await authService.logout(body.refreshToken);
	return ok(c, { success: true });
});

authV1Route.post(
	"/password:enrollOpaque:start",
	authRequired,
	standardValidator("json", enrollOpaqueStartSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const authService = c.var.resolve(AuthService);
		const body = c.req.valid("json");
		const payload = await authService.startPasswordEnrollmentOpaque(auth.userId, body.registrationRequest);
		return ok(c, payload);
	},
);

authV1Route.post(
	"/password:enrollOpaque:finish",
	authRequired,
	standardValidator("json", enrollOpaqueFinishSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const authService = c.var.resolve(AuthService);
		const body = c.req.valid("json");
		await authService.finishPasswordEnrollmentOpaque(auth.userId, body.registrationRecord, body.passwordFingerprint);
		return ok(c, { success: true });
	},
);

authV1Route.post("/verification:resend", standardValidator("json", resendVerificationSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
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

authV1Route.get("/verify-email", standardValidator("query", verifyEmailQuerySchema, invalidVerifyEmailQueryHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const rawQuery = c.req.query();
	const query = c.req.valid("query");
	const callbackTarget = resolveAuthCallbackTarget(rawQuery.client, rawQuery.redirect_uri);

	try {
		const user = await authService.verifyEmail(query.token);
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

authV1Route.get(
	"/password-reset",
	standardValidator("query", passwordResetQuerySchema, invalidPasswordResetQueryHook),
	async (c) => {
		const authService = c.var.resolve(AuthService);
		const rawQuery = c.req.query();
		const query = c.req.valid("query");
		const callbackTarget = resolveAuthCallbackTarget(rawQuery.client, rawQuery.redirect_uri);

		try {
			const resetContext = await authService.validatePasswordResetToken(query.token);
			return c.redirect(
				buildPasswordResetCallbackUrl(callbackTarget, {
					action: "reset-password",
					status: "success",
					code: "recovery_ready",
					token: query.token,
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
	},
);

authV1Route.post("/forgot-password", standardValidator("json", forgotPasswordSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
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

authV1Route.post("/reset-password:start", standardValidator("json", resetPasswordStartSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	const payload = await authService.startOpaquePasswordReset(body.token, body.registrationRequest);
	return ok(c, payload);
});

authV1Route.post("/reset-password:finish", standardValidator("json", resetPasswordFinishSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	await authService.finishOpaquePasswordReset(body.token, body.registrationRecord, body.passwordFingerprint);
	return ok(c, { success: true });
});

authV1Route.post("/reset-password", standardValidator("json", resetPasswordSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	await authService.resetPassword(body.token, body.newPassword);
	return ok(c, { success: true });
});

authV1Route.get("/mfa/status", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = c.var.resolve(MfaService);
	const status = await mfaService.status(auth.userId);
	return ok(c, status);
});

authV1Route.post("/mfa/totp:startSetup", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const authService = c.var.resolve(AuthService);
	const mfaService = c.var.resolve(MfaService);
	const user = await authService.findActiveUserById(auth.userId);
	const result = await mfaService.startTotpSetup(user);
	return ok(c, result);
});

authV1Route.post(
	"/mfa/totp:confirmSetup",
	authRequired,
	standardValidator("json", totpCodeSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const authService = c.var.resolve(AuthService);
		const mfaService = c.var.resolve(MfaService);
		const user = await authService.findActiveUserById(auth.userId);
		const body = c.req.valid("json");
		const result = await mfaService.confirmTotpSetup(user, body.code);
		return ok(c, result);
	},
);

authV1Route.post("/mfa/totp:disable", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = c.var.resolve(MfaService);
	const result = await mfaService.disableTotp(auth.userId);
	return ok(c, result);
});

authV1Route.post("/mfa/passkeys:startRegistration", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const authService = c.var.resolve(AuthService);
	const mfaService = c.var.resolve(MfaService);
	const user = await authService.findActiveUserById(auth.userId);
	const options = await mfaService.startPasskeyRegistration(user);
	return ok(c, options);
});

authV1Route.post(
	"/mfa/passkeys:finishRegistration",
	authRequired,
	standardValidator("json", passkeyFinishSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const mfaService = c.var.resolve(MfaService);
		const body = c.req.valid("json");
		const result = await mfaService.finishPasskeyRegistration(auth.userId, body.response);
		return ok(c, result);
	},
);

authV1Route.get("/mfa/backup-codes", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = c.var.resolve(MfaService);
	const result = await mfaService.getBackupCodeStatus(auth.userId);
	return ok(c, result);
});

authV1Route.post("/mfa/backup-codes:regenerate", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = c.var.resolve(MfaService);
	const result = await mfaService.regenerateBackupCodes(auth.userId);
	return ok(c, result);
});

authV1Route.get("/mfa/passkeys", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const mfaService = c.var.resolve(MfaService);
	const result = await mfaService.listPasskeys(auth.userId);
	return ok(c, result);
});

authV1Route.patch(
	"/mfa/passkey/:credentialId",
	authRequired,
	standardValidator("param", credentialIdParamSchema, validationHook),
	standardValidator("json", passkeyRenameSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const mfaService = c.var.resolve(MfaService);
		const params = c.req.valid("param");
		const body = c.req.valid("json");
		const result = await mfaService.renamePasskey(auth.userId, params.credentialId, body.name);
		return ok(c, result);
	},
);

authV1Route.delete(
	"/mfa/passkey/:credentialId",
	authRequired,
	standardValidator("param", credentialIdParamSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const mfaService = c.var.resolve(MfaService);
		const params = c.req.valid("param");
		const result = await mfaService.removePasskey(auth.userId, params.credentialId);
		return ok(c, result);
	},
);

authV1Route.post(
	"/mfa/challenges:startPasskeyLogin",
	standardValidator("json", mfaPasskeyStartSchema, validationHook),
	async (c) => {
		const mfaService = c.var.resolve(MfaService);
		const body = c.req.valid("json");
		const options = await mfaService.startPasskeyLogin(body.challengeToken);
		return ok(c, options);
	},
);

authV1Route.post("/mfa/challenges:verifyTotp", standardValidator("json", mfaTotpLoginSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const mfaService = c.var.resolve(MfaService);
	const body = c.req.valid("json");
	const user = await mfaService.verifyTotpLogin(body.challengeToken, body.code);
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: serializeAuthUser(user),
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.post(
	"/mfa/challenges:verifyBackupCode",
	standardValidator("json", mfaBackupCodeLoginSchema, validationHook),
	async (c) => {
		const authService = c.var.resolve(AuthService);
		const mfaService = c.var.resolve(MfaService);
		const body = c.req.valid("json");
		const user = await mfaService.verifyBackupCodeLogin(body.challengeToken, body.code);
		const tokens = await authService.issueTokensForUser(user);
		return ok(c, {
			user: serializeAuthUser(user),
			mfaRequired: false,
			...tokens,
		});
	},
);

authV1Route.post(
	"/mfa/challenges:verifyPasskey",
	standardValidator("json", mfaPasskeyLoginSchema, validationHook),
	async (c) => {
		const authService = c.var.resolve(AuthService);
		const mfaService = c.var.resolve(MfaService);
		const body = c.req.valid("json");
		const user = await mfaService.verifyPasskeyLogin(body.challengeToken, body.response);
		const tokens = await authService.issueTokensForUser(user);
		return ok(c, {
			user: serializeAuthUser(user),
			mfaRequired: false,
			...tokens,
		});
	},
);

authV1Route.post("/passkeys:startLogin", standardValidator("json", passkeyLoginStartSchema, validationHook), async (c) => {
	const mfaService = c.var.resolve(MfaService);
	c.req.valid("json");
	const payload = await mfaService.startDirectPasskeyLogin(resolveLoginChannel(c.req.header("X-Maimaid-Client")));
	return ok(c, payload);
});

authV1Route.post("/passkeys:finishLogin", standardValidator("json", passkeyLoginFinishSchema, validationHook), async (c) => {
	const authService = c.var.resolve(AuthService);
	const mfaService = c.var.resolve(MfaService);
	const body = c.req.valid("json");
	const user = await mfaService.verifyPasskeyLogin(body.challengeToken, body.response);
	const tokens = await authService.issueTokensForUser(user);
	return ok(c, {
		user: serializeAuthUser(user),
		mfaRequired: false,
		...tokens,
	});
});

authV1Route.get("/me", authRequired, async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const authService = c.var.resolve(AuthService);
	const user = await authService.findActiveUserById(auth.userId);
	return ok(c, serializeAuthUser(user));
});

authV1Route.patch("/me", authRequired, standardValidator("json", updateMeSchema, validationHook), async (c) => {
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const authService = c.var.resolve(AuthService);
	const body = c.req.valid("json");
	const user = await authService.updateUsername(auth.userId, body.username);
	return ok(c, serializeAuthUser(user));
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
	const env = container.resolve<Env>(TOKENS.Env);
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
	dashboardUrl.searchParams.set("client", "app");
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

const serializeAuthUser = (user: {
	id: string;
	email: string;
	username: string;
	usernameDiscriminator: string;
	isAdmin: boolean;
}) => serializeUserIdentity(user);
