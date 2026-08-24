import "reflect-metadata";
import { Hono, type Context } from "hono";
import { cors } from "hono/cors";
import { compress } from "hono/compress";
import { createMiddleware } from "hono/factory";
import { Scalar } from "@scalar/hono-api-reference";
import type { ContentfulStatusCode } from "hono/utils/http-status";
import type { PrismaClient } from "@prisma/client";
import { container } from "tsyringe";
import { ZodError } from "zod";
import { isAppError } from "./lib/errors.js";
import { buildValidationDetails } from "./http/validation.js";
import { healthRoute } from "./routes/health.route.js";
import { authV1Route } from "./routes/v1/auth.route.js";
import { profilesV1Route } from "./routes/v1/profiles.route.js";
import { catalogV1Route } from "./routes/v1/catalog.route.js";
import { scoresV1Route } from "./routes/v1/scores.route.js";
import { importsV1Route } from "./routes/v1/imports.route.js";
import { communityV1Route } from "./routes/v1/community.route.js";
import { adminV1Route } from "./routes/v1/admin.route.js";
import { syncV1Route } from "./routes/v1/sync.route.js";
import { jobsInternalRoute } from "./routes/internal/jobs.route.js";
import type { AppEnv } from "./types/hono.js";
import type { Env } from "./env.js";
import { TOKENS } from "./di/tokens.js";

type AppDependencies = {
	env: Env;
	prisma: PrismaClient;
};

type CreateAppOptions = {
	resolveDependencies?: (context: Context<AppEnv>) => AppDependencies;
};

const missingDependencies = (): never => {
	throw new Error("Application dependencies are unavailable for this request.");
};

export const createApp = (options: CreateAppOptions = {}) => {
	const resolveDependencies = options.resolveDependencies ?? missingDependencies;
	const app = new Hono<AppEnv>();
	const dependencyMiddleware = createMiddleware<AppEnv>(async (context, next) => {
		const childContainer = container.createChildContainer();
		const dependencies = resolveDependencies(context);
		childContainer.register(TOKENS.Env, { useValue: dependencies.env });
		childContainer.register(TOKENS.Prisma, { useValue: dependencies.prisma });
		context.set("resolve", (token) => childContainer.resolve(token));
		await next();
	});

	app.use("*", dependencyMiddleware);
	app.use(
		cors({
			origin: (origin, context) => {
				const corsAllowedOrigins = (context as Context<AppEnv>).var
					.resolve<Env>(TOKENS.Env)
					.CORS_ALLOWED_ORIGINS.split(",")
					.map((item) => item.trim())
					.filter((item) => item.length > 0);
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
	app.use("*", compress());

	app.route("/health", healthRoute);
	app.route("/v1/auth", authV1Route);
	app.route("/v1/profiles", profilesV1Route);
	app.route("/v1/catalog", catalogV1Route);
	app.route("/v1", scoresV1Route);
	app.route("/v1", importsV1Route);
	app.route("/v1/community", communityV1Route);
	app.route("/v1", adminV1Route);
	app.route("/v1", syncV1Route);
	app.route("/internal/jobs", jobsInternalRoute);

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
		const env = c.var.resolve<Env>(TOKENS.Env);
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
					details: buildValidationDetails(error.issues),
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

export const registerOpenApiRoutes = (app: ReturnType<typeof createApp>, openApiDocument: unknown) => {
	app.get("/openapi.json", (c) => c.json(openApiDocument));
	app.get(
		"/docs",
		Scalar({
			url: "/openapi.json",
			pageTitle: "maimaid backend API docs",
			theme: "kepler",
		}),
	);
};
