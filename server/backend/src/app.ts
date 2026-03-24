import { Hono } from "hono";
import { cors } from "hono/cors";
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
import { jobsInternalRoute } from "./routes/internal/jobs.route.js";
import type { AppEnv } from "./types/hono.js";

export const createApp = () => {
  const app = new Hono<AppEnv>();

  app.use(
    "*",
    cors({
      origin: "*",
      allowMethods: ["GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"],
      allowHeaders: ["Content-Type", "Authorization"]
    })
  );

  app.route("/health", healthRoute);
  app.route("/v1/auth", authV1Route);
  app.route("/v1/profiles", profilesV1Route);
  app.route("/v1/catalog", catalogV1Route);
  app.route("/v1/scores", scoresV1Route);
  app.route("/v1/import", importsV1Route);
  app.route("/v1/community", communityV1Route);
  app.route("/v1/admin", adminV1Route);
  app.route("/internal/jobs", jobsInternalRoute);

  app.get("/", (c) =>
    c.json({
      name: "maimaid-backend",
      status: "ok",
      time: new Date().toISOString()
    })
  );

  app.notFound((c) =>
    c.json(
      {
        code: "not_found",
        message: "Route not found."
      },
      404
    )
  );

  app.onError((error, c) => {
    if (isAppError(error)) {
      return c.json(
        {
          code: error.code,
          message: error.message,
          details: error.details ?? null
        },
        error.status as ContentfulStatusCode
      );
    }

    if (error instanceof ZodError) {
      return c.json(
        {
          code: "validation_error",
          message: "Request validation failed.",
          details: error.flatten()
        },
        400
      );
    }

    return c.json(
      {
        code: "internal_error",
        message: error instanceof Error ? error.message : "Unknown error"
      },
      500
    );
  });

  return app;
};
