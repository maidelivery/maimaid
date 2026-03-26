import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { AuthService } from "../../services/auth.service.js";
import type { MfaService } from "../../services/mfa.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { isAppError } from "../../lib/errors.js";
import { isPasswordComplexEnough, PASSWORD_COMPLEXITY_ERROR_MESSAGE } from "../../lib/auth-validation.js";
import type { AppEnv } from "../../types/hono.js";
import type { Env } from "../../env.js";

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1).refine((value) => isPasswordComplexEnough(value), {
    message: PASSWORD_COMPLEXITY_ERROR_MESSAGE
  })
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1),
  channel: z.enum(["web", "app"]).default("app")
});
const refreshSchema = z.object({
  refreshToken: z.string().min(20)
});

const forgotPasswordSchema = z.object({
  email: z.string().email()
});

const resendVerificationSchema = z.object({
  email: z.string().email()
});

const resetPasswordSchema = z.object({
  token: z.string().min(20),
  newPassword: z.string().min(1).refine((value) => isPasswordComplexEnough(value), {
    message: PASSWORD_COMPLEXITY_ERROR_MESSAGE
  })
});

const passkeyLoginStartSchema = z.object({
  channel: z.enum(["web", "app"]).default("web")
});

const passkeyLoginFinishSchema = z.object({
  challengeToken: z.string().min(20),
  response: z.unknown()
});

const totpCodeSchema = z.object({
  code: z.string().min(6).max(8)
});

const mfaTotpLoginSchema = z.object({
  challengeToken: z.string().min(20),
  code: z.string().min(6).max(8)
});

const mfaBackupCodeLoginSchema = z.object({
  challengeToken: z.string().min(20),
  code: z.string().trim().min(6).max(32)
});

const mfaPasskeyLoginSchema = z.object({
  challengeToken: z.string().min(20),
  response: z.unknown()
});

const mfaPasskeyStartSchema = z.object({
  challengeToken: z.string().min(20)
});

const passkeyFinishSchema = z.object({
  response: z.unknown()
});

const passkeyRenameSchema = z.object({
  name: z.string().trim().min(1).max(64)
});

const verifyEmailQuerySchema = z.object({
  token: z.string().min(20)
});

const passwordResetQuerySchema = z.object({
  token: z.string().min(20)
});

export const authV1Route = new Hono<AppEnv>();

authV1Route.post("/register", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = registerSchema.parse(await c.req.json());
  const { user, verificationEmailSent } = await authService.register(body.email, body.password);
  return ok(c, {
    user: {
      id: user.id,
      email: user.email,
      isAdmin: user.isAdmin
    },
    verificationEmailSent
  });
});

authV1Route.post("/login", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
  const body = loginSchema.parse(await c.req.json());
  const user = await authService.validateLoginCredentials(body.email, body.password);
  const requiresMfa = await mfaService.shouldEnforceMfa(user.id, body.channel);
  if (requiresMfa) {
    const challenge = await mfaService.createLoginChallenge(user, body.channel);
    return ok(c, {
      user: {
        id: user.id,
        email: user.email,
        isAdmin: user.isAdmin
      },
      mfaRequired: true,
      challengeToken: challenge.challengeToken,
      methods: challenge.methods
    });
  }
  const tokens = await authService.issueTokensForUser(user);
  return ok(c, {
    user: {
      id: user.id,
      email: user.email,
      isAdmin: user.isAdmin
    },
    mfaRequired: false,
    ...tokens
  });
});

authV1Route.post("/refresh", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = refreshSchema.parse(await c.req.json());
  const { user, tokens } = await authService.refresh(body.refreshToken);
  return ok(c, {
    user: {
      id: user.id,
      email: user.email,
      isAdmin: user.isAdmin
    },
    ...tokens
  });
});

authV1Route.post("/logout", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = refreshSchema.parse(await c.req.json());
  await authService.logout(body.refreshToken);
  return ok(c, { success: true });
});

authV1Route.get("/email-exists", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const email = c.req.query("email") ?? "";
  const exists = await authService.emailExists(email);
  return ok(c, { exists });
});

authV1Route.post("/resend-verification", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = resendVerificationSchema.parse(await c.req.json());
  const result = await authService.resendVerification(body.email);
  return ok(c, result);
});

authV1Route.get("/verify-email", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const parsed = verifyEmailQuerySchema.safeParse(c.req.query());
  if (!parsed.success) {
    return c.redirect(buildDashboardAuthUrl({ action: "verify-email", status: "error", code: "invalid_verification_token" }), 302);
  }

  try {
    await authService.verifyEmail(parsed.data.token);
    return c.redirect(buildDashboardAuthUrl({ action: "verify-email", status: "success", code: "email_verified" }), 302);
  } catch (error) {
    if (isAppError(error) && error.code === "invalid_verification_token") {
      return c.redirect(buildDashboardAuthUrl({ action: "verify-email", status: "error", code: "invalid_verification_token" }), 302);
    }
    throw error;
  }
});

authV1Route.get("/password-reset", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const parsed = passwordResetQuerySchema.safeParse(c.req.query());
  if (!parsed.success) {
    return c.redirect(buildDashboardAuthUrl({ action: "reset-password", status: "error", code: "invalid_reset_token" }), 302);
  }

  try {
    await authService.validatePasswordResetToken(parsed.data.token);
    return c.redirect(
      buildDashboardAuthUrl({
        action: "reset-password",
        status: "success",
        code: "recovery_ready",
        token: parsed.data.token
      }),
      302
    );
  } catch (error) {
    if (isAppError(error) && error.code === "invalid_reset_token") {
      return c.redirect(buildDashboardAuthUrl({ action: "reset-password", status: "error", code: "invalid_reset_token" }), 302);
    }
    throw error;
  }
});

authV1Route.post("/forgot-password", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = forgotPasswordSchema.parse(await c.req.json());
  const result = await authService.forgotPassword(body.email);
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
      isAdmin: user.isAdmin
    },
    mfaRequired: false,
    ...tokens
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
      isAdmin: user.isAdmin
    },
    mfaRequired: false,
    ...tokens
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
      isAdmin: user.isAdmin
    },
    mfaRequired: false,
    ...tokens
  });
});

authV1Route.post("/passkey/login/start", async (c) => {
  const mfaService = di.resolve<MfaService>(TOKENS.MfaService);
  const body = passkeyLoginStartSchema.parse(await c.req.json());
  const payload = await mfaService.startDirectPasskeyLogin(body.channel);
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
      isAdmin: user.isAdmin
    },
    mfaRequired: false,
    ...tokens
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
    isAdmin: auth.isAdmin
  });
});

const buildDashboardAuthUrl = (input: {
  action: "verify-email" | "reset-password";
  status: "success" | "error";
  code: string;
  token?: string;
}): string => {
  const env = di.resolve<Env>(TOKENS.Env);
  const baseUrl = (env.WEBAUTHN_ORIGIN?.trim() || env.APP_PUBLIC_URL?.trim() || `http://localhost:${env.PORT}`).replace(/\/+$/u, "");
  const callback = new URL(baseUrl);
  callback.searchParams.set("authAction", input.action);
  callback.searchParams.set("status", input.status);
  callback.searchParams.set("code", input.code);
  if (input.token) {
    callback.searchParams.set("token", input.token);
  }
  return callback.toString();
};
