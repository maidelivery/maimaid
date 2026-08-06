import { timingSafeEqual } from "node:crypto";
import type { Context, Next } from "hono";
import { createMiddleware } from "hono/factory";
import { JwtService } from "../services/jwt.service.js";
import { AppError } from "../lib/errors.js";
import { getEnv } from "../env.js";
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

/** Constant-time, and length-safe: `timingSafeEqual` throws on unequal lengths. */
const secretMatches = (candidate: string, expected: string) => {
	const candidateBytes = Buffer.from(candidate, "utf8");
	const expectedBytes = Buffer.from(expected, "utf8");
	if (candidateBytes.length !== expectedBytes.length) {
		return false;
	}
	return timingSafeEqual(candidateBytes, expectedBytes);
};

/**
 * Guards `/internal/jobs/*`. Accepts either an admin access token, as before, or
 * the `INTERNAL_JOB_TOKEN` shared secret.
 *
 * The shared secret exists for GitHub Actions. The alternative was giving CI a
 * real admin account: an access token lives 15 minutes, so CI would have to hold
 * long-lived login credentials and run an OPAQUE login on every build, and any
 * leak would expose a human account rather than one revocable value. The token
 * only reaches these job endpoints — it is not accepted anywhere under `/v1`.
 *
 * Unset by default, in which case only the admin-JWT path works and nothing
 * changes.
 */
export const internalJobAuthRequired = createMiddleware<AppEnv>(async (c: Context<AppEnv>, next: Next) => {
	const expected = getEnv().INTERNAL_JOB_TOKEN;
	const authorization = c.req.header("Authorization");
	const presented = authorization?.startsWith("Bearer ") ? authorization.replace(/^Bearer\s+/i, "") : null;

	if (expected && presented && secretMatches(presented, expected)) {
		await next();
		return;
	}

	const auth = await requireAuthContext(c);
	if (!auth.isAdmin) {
		throw new AppError(403, "forbidden", "Admin permission required.");
	}
	await next();
});
