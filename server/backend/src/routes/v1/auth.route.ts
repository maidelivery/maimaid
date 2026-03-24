import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { AuthService } from "../../services/auth.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

const registerSchema = z.object({
  email: z.string().email(),
  password: z.string().min(8)
});

const loginSchema = registerSchema;
const refreshSchema = z.object({
  refreshToken: z.string().min(20)
});

const forgotPasswordSchema = z.object({
  email: z.string().email()
});

const resetPasswordSchema = z.object({
  token: z.string().min(20),
  newPassword: z.string().min(8)
});

export const authV1Route = new Hono<AppEnv>();

authV1Route.post("/register", async (c) => {
  const authService = di.resolve<AuthService>(TOKENS.AuthService);
  const body = registerSchema.parse(await c.req.json());
  const { user, tokens } = await authService.register(body.email, body.password);
  return ok(c, {
    user: {
      id: user.id,
      email: user.email,
      isAdmin: user.isAdmin
    },
    ...tokens
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
