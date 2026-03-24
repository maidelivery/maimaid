import type { Context, Next } from "hono";
import { createMiddleware } from "hono/factory";
import { di } from "../di/container.js";
import { TOKENS } from "../di/tokens.js";
import type { JwtService } from "../services/jwt.service.js";
import { AppError } from "../lib/errors.js";
import type { AppEnv, AuthContext } from "../types/hono.js";

export const authOptional = createMiddleware<AppEnv>(async (c: Context<AppEnv>, next: Next) => {
  const authorization = c.req.header("Authorization");
  if (!authorization || !authorization.startsWith("Bearer ")) {
    await next();
    return;
  }
  const token = authorization.replace(/^Bearer\s+/i, "");
  const jwt = di.resolve<JwtService>(TOKENS.JwtService);
  const payload = await jwt.verifyAccessToken(token);
  const auth: AuthContext = {
    userId: payload.sub,
    email: payload.email,
    isAdmin: payload.isAdmin
  };
  c.set("auth", auth);
  await next();
});

export const authRequired = createMiddleware<AppEnv>(async (c: Context<AppEnv>, next: Next) => {
  await authOptional(c, async () => undefined);
  const auth = c.get("auth");
  if (!auth) {
    throw new AppError(401, "unauthorized", "Authentication required.");
  }
  await next();
});

export const adminRequired = createMiddleware<AppEnv>(async (c: Context<AppEnv>, next: Next) => {
  await authRequired(c, async () => undefined);
  const auth = c.get("auth");
  if (!auth?.isAdmin) {
    throw new AppError(403, "forbidden", "Admin permission required.");
  }
  await next();
});
