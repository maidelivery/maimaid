import { Hono } from "hono";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { StaticBundleService } from "../../services/static-bundle.service.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

export const staticV1Route = new Hono<AppEnv>();

staticV1Route.get("/manifest", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const manifest = await staticBundleService.manifest();
  return ok(c, manifest);
});

staticV1Route.get("/bundle/:version", async (c) => {
  const staticBundleService = di.resolve<StaticBundleService>(TOKENS.StaticBundleService);
  const version = c.req.param("version");
  const bundle = await staticBundleService.getBundle(version);
  return ok(c, {
    version: bundle.version,
    md5: bundle.md5,
    createdAt: bundle.createdAt,
    payload: bundle.payloadJson
  });
});
