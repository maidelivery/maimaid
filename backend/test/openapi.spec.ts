import { Hono } from "hono";
import { describe, expect, it } from "vitest";
import type { Env } from "../src/env.js";
import { ok } from "../src/http/response.js";
import { buildOpenApiDocument } from "../src/openapi.js";
import type { AppEnv } from "../src/types/hono.js";

const createEnv = (): Env => ({
  NODE_ENV: "development",
  HOST: "0.0.0.0",
  PORT: 8787,
  APP_PUBLIC_URL: undefined,
  CORS_ALLOWED_ORIGINS: "",
  DATABASE_URL: "postgres://localhost:5432/maimaid",
  JWT_ISSUER: "maimaid-backend",
  JWT_AUDIENCE: "maimaid-clients",
  JWT_ACCESS_SECRET: "1234567890123456",
  JWT_ACCESS_TTL_SECONDS: 900,
  JWT_REFRESH_TTL_SECONDS: 60 * 60 * 24 * 30,
  MFA_CHALLENGE_TTL_SECONDS: 300,
  WEBAUTHN_RP_ID: undefined,
  WEBAUTHN_RP_NAME: "maimaid",
  WEBAUTHN_ORIGIN: undefined,
  RESEND_API_KEY: undefined,
  RESEND_FROM_EMAIL: "no-reply@example.com",
  S3_ENDPOINT: undefined,
  S3_PUBLIC_ENDPOINT: undefined,
  S3_REGION: "auto",
  S3_BUCKET: "maimaid-assets",
  S3_ACCESS_KEY_ID: undefined,
  S3_SECRET_ACCESS_KEY: undefined,
  CATALOG_SOURCE_URL: "https://example.com/catalog.json",
  STATIC_SYNC_INTERVAL_HOURS: 6
});

describe("openapi document generation", () => {
  it("converts route params to OpenAPI format and filters wildcard routes", () => {
    const app = new Hono<AppEnv>();
    app.use("*", async (_, next) => {
      await next();
    });
    app.patch("/v1/items/:itemId", (c) => c.json({ ok: true }));
    app.get("/health", (c) => c.json({ ok: true }));

    const document = buildOpenApiDocument(app, createEnv());

    expect(document.openapi).toBe("3.1.0");
    expect(document.paths["/v1/items/{itemId}"]).toBeDefined();
    expect(document.paths["/*"]).toBeUndefined();
    expect(document.paths["/v1/items/{itemId}"]?.patch).toBeDefined();
    expect((document.paths["/v1/items/{itemId}"] as Record<string, unknown>)?.patch).toMatchObject({
      parameters: [
        {
          name: "itemId",
          in: "path",
          required: true
        }
      ]
    });
    expect(document.servers[0]?.url).toBe("http://localhost:8787");
  });

  it("infers top-level response keys from ok() payloads", () => {
    const app = new Hono<AppEnv>();
    app.get("/v1/example", (c) => {
      const authed = Boolean(c.req.query("authed"));
      if (!authed) {
        return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
      }
      return ok(c, { items: [], total: 0 }, 200);
    });

    const document = buildOpenApiDocument(app, createEnv());
    const operation = (document.paths["/v1/example"] as Record<string, any>)?.get;
    expect(operation).toBeDefined();

    const response200 = operation.responses["200"].content["application/json"].schema;
    const response401 = operation.responses["401"].content["application/json"].schema;

    expect(Object.keys(response200.properties)).toEqual(["items", "total"]);
    expect(Object.keys(response401.properties)).toEqual(["code", "message"]);
  });

  it("extracts typed schemas from real route source files", async () => {
    process.env.DATABASE_URL = process.env.DATABASE_URL ?? "postgres://localhost:5432/maimaid";
    process.env.JWT_ACCESS_SECRET = process.env.JWT_ACCESS_SECRET ?? "1234567890123456";

    const { createApp } = await import("../src/app.js");
    const app = createApp();
    const document = buildOpenApiDocument(app, createEnv());

    const submitOperation = (document.paths["/v1/community/aliases/submit"] as Record<string, any>)?.post;
    const submitBodySchema = submitOperation.requestBody.content["application/json"].schema;
    expect(Object.keys(submitBodySchema.properties)).toEqual([
      "songIdentifier",
      "aliasText",
      "deviceLocalDate",
      "tzOffsetMinutes"
    ]);

    const votingOperation = (document.paths["/v1/community/aliases/voting-board"] as Record<string, any>)?.get;
    const limitParam = votingOperation.parameters.find((item: any) => item.name === "limit");
    expect(limitParam.schema.type).toBe("number");

    const rowItemSchema = votingOperation.responses["200"].content["application/json"].schema.properties.rows.items;
    expect(Object.keys(rowItemSchema.properties)).toContain("candidateId");
    expect(Object.keys(rowItemSchema.properties)).toContain("aliasText");
  });
});
