import { existsSync, readFileSync } from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { Hono } from "hono";
import { cors } from "hono/cors";
import { Scalar } from "@scalar/hono-api-reference";
import type { ContentfulStatusCode } from "hono/utils/http-status";
import { ZodError } from "zod";
import { isAppError } from "./lib/errors.js";
import { healthRoute } from "./routes/health.route.js";
import { authV1Route } from "./routes/v1/auth.route.js";
import { profilesV1Route } from "./routes/v1/profiles.route.js";
import { catalogV1Route } from "./routes/v1/catalog.route.js";
import { scoresV1Route } from "./routes/v1/scores.route.js";
import { importsV1Route } from "./routes/v1/imports.route.js";
import { communityV1Route } from "./routes/v1/community.route.js";
import { adminV1Route } from "./routes/v1/admin.route.js";
import { syncV1Route } from "./routes/v1/sync.route.js";
import { staticV1Route } from "./routes/v1/static.route.js";
import { jobsInternalRoute } from "./routes/internal/jobs.route.js";
import type { AppEnv } from "./types/hono.js";
import { getEnv } from "./env.js";
import { buildOpenApiDocument } from "./openapi.js";

const resolvePrebuiltOpenApiPath = () => {
	const currentFilePath = fileURLToPath(import.meta.url);
	return path.join(path.dirname(currentFilePath), "openapi.prebuilt.json");
};

const loadPrebuiltOpenApiDocument = (env: ReturnType<typeof getEnv>) => {
	if (env.NODE_ENV !== "production") {
		return null;
	}

	const prebuiltPath = resolvePrebuiltOpenApiPath();
	if (!existsSync(prebuiltPath)) {
		return null;
	}

	try {
		const raw = readFileSync(prebuiltPath, "utf8");
		const parsed = JSON.parse(raw) as unknown;
		if (typeof parsed !== "object" || parsed === null) {
			return null;
		}
		return parsed;
	} catch (error) {
		console.warn("[openapi] failed to load prebuilt openapi document, fallback to runtime generation", error);
		return null;
	}
};

export const createApp = () => {
	const env = getEnv();
	const corsAllowedOrigins = env.CORS_ALLOWED_ORIGINS.split(",")
		.map((item) => item.trim())
		.filter((item) => item.length > 0);
	const app = new Hono<AppEnv>();

	app.use(
		"*",
		cors({
			origin: (origin) => {
				if (!origin) {
					return null;
				}
				if (corsAllowedOrigins.includes(origin)) {
					return origin;
				}
				return null;
			},
			allowMethods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
			allowHeaders: ["Content-Type", "Authorization", "X-Maimaid-Client"],
		}),
	);

	app.route("/health", healthRoute);
	app.route("/v1/auth", authV1Route);
	app.route("/v1/profiles", profilesV1Route);
	app.route("/v1/catalog", catalogV1Route);
	app.route("/v1/scores", scoresV1Route);
	app.route("/v1/import", importsV1Route);
	app.route("/v1/community", communityV1Route);
	app.route("/v1/admin", adminV1Route);
	app.route("/v1/sync", syncV1Route);
	app.route("/v1/static", staticV1Route);
	app.route("/internal/jobs", jobsInternalRoute);

	const openApiDocument = loadPrebuiltOpenApiDocument(env) ?? buildOpenApiDocument(app, env);

	app.get("/openapi.json", (c) => c.json(openApiDocument));

	app.get(
		"/docs",
		Scalar({
			url: "/openapi.json",
			pageTitle: "maimaid backend API docs",
			theme: "kepler",
		}),
	);

	app.get("/", (c) =>
		c.json({
			name: "maimaid-backend",
			status: "ok",
			time: new Date().toISOString(),
		}),
	);

	app.notFound((c) =>
		c.json(
			{
				code: "not_found",
				message: "Route not found.",
			},
			404,
		),
	);

	app.onError((error, c) => {
		if (isAppError(error)) {
			return c.json(
				{
					code: error.code,
					message: error.message,
					details: error.details ?? null,
				},
				error.status as ContentfulStatusCode,
			);
		}

		if (error instanceof ZodError) {
			return c.json(
				{
					code: "validation_error",
					message: "Request validation failed.",
					details: error.flatten(),
				},
				400,
			);
		}

		console.error("[internal_error]", {
			method: c.req.method,
			url: c.req.url,
			error,
		});

		return c.json(
			{
				code: "internal_error",
				message: env.NODE_ENV === "production" ? "Internal error." : error instanceof Error ? error.message : "Unknown error",
			},
			500,
		);
	});

	return app;
};
