import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import type { ImportService } from "../../services/import.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

const dfSchema = z.object({
  profileId: z.string().uuid(),
  username: z.string().optional(),
  qq: z.string().optional()
});

const lxnsSchema = z.object({
  profileId: z.string().uuid(),
  accessToken: z.string().min(8)
});

export const importsV1Route = new Hono<AppEnv>();

const dfTransformSchema = z
  .object({
    username: z.string().optional(),
    qq: z.string().optional()
  })
  .refine((value) => Boolean(value.username || value.qq), "username or qq is required.");

const lxnsTransformSchema = z.object({
  accessToken: z.string().min(8)
});

importsV1Route.post("/df/transform", async (c) => {
  const importService = di.resolve<ImportService>(TOKENS.ImportService);
  const body = dfTransformSchema.parse(await c.req.json());
  const payload: Parameters<ImportService["transformFromDivingFish"]>[0] = {};
  if (body.username !== undefined) payload.username = body.username;
  if (body.qq !== undefined) payload.qq = body.qq;
  const result = await importService.transformFromDivingFish(payload);
  return ok(c, result);
});

importsV1Route.post("/lxns/transform", async (c) => {
  const importService = di.resolve<ImportService>(TOKENS.ImportService);
  const body = lxnsTransformSchema.parse(await c.req.json());
  const result = await importService.transformFromLxns({
    accessToken: body.accessToken
  });
  return ok(c, result);
});

importsV1Route.post("/df", authRequired, async (c) => {
  const importService = di.resolve<ImportService>(TOKENS.ImportService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = dfSchema.parse(await c.req.json());
  const payload: Parameters<ImportService["importFromDivingFish"]>[0] = {
    userId: auth.userId,
    profileId: body.profileId
  };
  if (body.username !== undefined) payload.username = body.username;
  if (body.qq !== undefined) payload.qq = body.qq;
  const result = await importService.importFromDivingFish(payload);
  return ok(c, result);
});

importsV1Route.post("/lxns", authRequired, async (c) => {
  const importService = di.resolve<ImportService>(TOKENS.ImportService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = lxnsSchema.parse(await c.req.json());
  const result = await importService.importFromLxns({
    userId: auth.userId,
    profileId: body.profileId,
    accessToken: body.accessToken
  });
  return ok(c, result);
});
