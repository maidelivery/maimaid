import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { AuthService } from "../../services/auth.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { isAppError } from "../../lib/errors.js";
import type { AppEnv } from "../../types/hono.js";

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
});

const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
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
  newPassword: z.string().min(1)
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
  const body = loginSchema.parse(await c.req.json());
  const { user, tokens } = await authService.login(body.email, body.password);
  return ok(c, {
    user: {
      id: user.id,
      email: user.email,
      isAdmin: user.isAdmin
    },
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
    const openAppUrl = buildAppAuthCallbackUrl({
      type: "signup",
      result: "error",
      code: "invalid_verification_token"
    });
    return c.html(
      renderAuthResultHtml({
        success: false,
        title: "Email verification failed",
        message: "Verification token is missing or invalid.",
        openAppUrl
      }),
      400
    );
  }

  try {
    await authService.verifyEmail(parsed.data.token);
    const openAppUrl = buildAppAuthCallbackUrl({
      type: "signup",
      result: "success",
      code: "email_verified"
    });
    return c.html(
      renderAuthResultHtml({
        success: true,
        title: "Email verification completed",
        message: "Email verified successfully. You can now sign in.",
        openAppUrl
      })
    );
  } catch (error) {
    if (isAppError(error) && error.code === "invalid_verification_token") {
      const openAppUrl = buildAppAuthCallbackUrl({
        type: "signup",
        result: "error",
        code: "invalid_verification_token"
      });
      return c.html(
        renderAuthResultHtml({
          success: false,
          title: "Email verification failed",
          message: "Verification token is invalid or expired.",
          openAppUrl
        }),
        400
      );
    }
    throw error;
  }
});

authV1Route.get("/password-reset", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const parsed = passwordResetQuerySchema.safeParse(c.req.query());
  if (!parsed.success) {
    const openAppUrl = buildAppAuthCallbackUrl({
      type: "recovery",
      result: "error",
      code: "invalid_reset_token"
    });
    return c.html(
      renderAuthResultHtml({
        success: false,
        title: "Password reset link invalid",
        message: "Reset link is missing or invalid.",
        openAppUrl
      }),
      400
    );
  }

  try {
    await authService.validatePasswordResetToken(parsed.data.token);
    const openAppUrl = buildAppAuthCallbackUrl({
      type: "recovery",
      result: "success",
      code: "recovery_ready",
      token: parsed.data.token
    });
    return c.html(
      renderAuthResultHtml({
        success: true,
        title: "Password reset link ready",
        message: "Returning to maimaid. Please set your new password in the app.",
        openAppUrl
      })
    );
  } catch (error) {
    if (isAppError(error) && error.code === "invalid_reset_token") {
      const openAppUrl = buildAppAuthCallbackUrl({
        type: "recovery",
        result: "error",
        code: "invalid_reset_token"
      });
      return c.html(
        renderAuthResultHtml({
          success: false,
          title: "Password reset link invalid",
          message: "Reset link is invalid or expired.",
          openAppUrl
        }),
        400
      );
    }
    throw error;
  }
});

authV1Route.post("/forgot-password", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = forgotPasswordSchema.parse(await c.req.json());
  await authService.forgotPassword(body.email);
  return ok(c, { success: true });
});

authV1Route.post("/reset-password", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = resetPasswordSchema.parse(await c.req.json());
  await authService.resetPassword(body.token, body.newPassword);
  return ok(c, { success: true });
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

const renderAuthResultHtml = (input: {
  success: boolean;
  title: string;
  message: string;
  openAppUrl: string;
}): string => {
  const emoji = input.success ? "✅" : "⚠️";
  const title = input.title;
  const message = input.message;
  const openAppUrl = input.openAppUrl;
  const background = input.success ? "#166534" : "#9f1239";

  return `<!doctype html>
<html lang="en">
  <head>
    <meta charset="utf-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1" />
    <title>${title}</title>
    <style>
      :root { color-scheme: light dark; }
      body {
        margin: 0;
        min-height: 100vh;
        display: grid;
        place-items: center;
        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif;
        background: #0b1020;
        color: #f8fafc;
      }
      main {
        max-width: 440px;
        margin: 24px;
        padding: 28px;
        border-radius: 16px;
        border: 1px solid rgba(255, 255, 255, 0.16);
        background: rgba(15, 23, 42, 0.88);
        backdrop-filter: blur(10px);
      }
      .badge {
        display: inline-block;
        border-radius: 999px;
        padding: 4px 12px;
        background: ${background};
        font-size: 13px;
        font-weight: 600;
        margin-bottom: 14px;
      }
      h1 {
        margin: 0 0 10px;
        font-size: 22px;
        line-height: 1.25;
      }
      p {
        margin: 0;
        font-size: 15px;
        line-height: 1.5;
        color: rgba(248, 250, 252, 0.9);
      }
      .action {
        display: inline-block;
        margin-top: 16px;
        border-radius: 10px;
        padding: 10px 14px;
        color: #ffffff;
        background: #2563eb;
        text-decoration: none;
        font-weight: 600;
      }
      .tip {
        margin-top: 12px;
        font-size: 13px;
        color: rgba(248, 250, 252, 0.72);
      }
    </style>
  </head>
  <body>
    <main>
      <div class="badge">${emoji} maimaid</div>
      <h1>${title}</h1>
      <p>${message}</p>
      <a class="action" href="${openAppUrl}">Open maimaid app</a>
      <p class="tip">If the app did not open automatically, tap the button above.</p>
    </main>
    <script>
      setTimeout(() => {
        window.location.href = ${JSON.stringify(openAppUrl)};
      }, 350);
    </script>
  </body>
</html>`;
};

const buildAppAuthCallbackUrl = (input: {
  type: "signup" | "recovery";
  result: "success" | "error";
  code: string;
  token?: string;
}): string => {
  const callback = new URL("maimaid://auth/callback");
  callback.searchParams.set("type", input.type);
  callback.searchParams.set("result", input.result);
  callback.searchParams.set("code", input.code);
  if (input.token) {
    callback.searchParams.set("token", input.token);
  }
  return callback.toString();
};
