import type { Context, Next } from "hono";
import { createMiddleware } from "hono/factory";
import { JwtService } from "../services/jwt.service.js";
import { AppError } from "../lib/errors.js";
import type { AppEnv, AuthContext } from "../types/hono.js";
import { container } from "tsyringe";

const resolveAuthContext = async (c: Context<AppEnv>): Promise<AuthContext | null> => {
	const authorization = c.req.header("Authorization");
	if (!authorization || !authorization.startsWith("Bearer ")) {
		return null;
	}

	const token = authorization.replace(/^Bearer\s+/i, "");
	const jwt = container.resolve(JwtService);
	const payload = await jwt.verifyAccessToken(token);
	return {
		userId: payload.sub,
		email: payload.email,
		isAdmin: payload.isAdmin,
	};
};

const requireAuthContext = async (c: Context<AppEnv>): Promise<AuthContext> => {
	const auth = await resolveAuthContext(c);
	if (!auth) {
		throw new AppError(401, "unauthorized", "Authentication required.");
	}
	c.set("auth", auth);
	return auth;
};

export const authOptional = createMiddleware<AppEnv>(async (c: Context<AppEnv>, next: Next) => {
	const auth = await resolveAuthContext(c);
	if (auth) {
		c.set("auth", auth);
	}
	await next();
});

export const authRequired = createMiddleware<AppEnv>(async (c: Context<AppEnv>, next: Next) => {
	await requireAuthContext(c);
	await next();
});

export const adminRequired = createMiddleware<AppEnv>(async (c: Context<AppEnv>, next: Next) => {
	const auth = await requireAuthContext(c);
	if (!auth.isAdmin) {
		throw new AppError(403, "forbidden", "Admin permission required.");
	}
	await next();
});
