import { Hono } from "hono";
import { z } from "zod";
import { di } from "../../di/container.js";
import { TOKENS } from "../../di/tokens.js";
import { authRequired } from "../../middleware/auth.js";
import type { ProfileService } from "../../services/profile.service.js";
import { ok } from "../../http/response.js";
import type { AppEnv } from "../../types/hono.js";

const createProfileSchema = z.object({
  name: z.string().min(1).max(40),
  server: z.enum(["jp", "intl", "usa", "cn"]).default("jp")
});

const upsertProfileSchema = z.object({
  profileId: z.string().uuid(),
  name: z.string().min(1).max(40),
  server: z.enum(["jp", "intl", "usa", "cn"]).default("jp"),
  isActive: z.boolean().optional(),
  playerRating: z.number().int().nonnegative().optional(),
  plate: z.string().nullable().optional(),
  avatarUrl: z.string().url().nullable().optional(),
  dfUsername: z.string().optional(),
  dfImportToken: z.string().optional(),
  lxnsRefreshToken: z.string().optional(),
  b35Count: z.number().int().positive().optional(),
  b15Count: z.number().int().positive().optional(),
  b35RecLimit: z.number().int().positive().optional(),
  b15RecLimit: z.number().int().positive().optional(),
  createdAt: z.coerce.date().optional()
});

const updateProfileSchema = z
  .object({
    name: z.string().min(1).max(40).optional(),
    server: z.enum(["jp", "intl", "usa", "cn"]).optional(),
    isActive: z.boolean().optional(),
    playerRating: z.number().int().nonnegative().optional(),
    plate: z.string().nullable().optional(),
    dfUsername: z.string().optional(),
    dfImportToken: z.string().optional(),
    lxnsRefreshToken: z.string().optional(),
    b35Count: z.number().int().positive().optional(),
    b15Count: z.number().int().positive().optional(),
    b35RecLimit: z.number().int().positive().optional(),
    b15RecLimit: z.number().int().positive().optional()
  })
  .refine((value) => Object.keys(value).length > 0, "No profile field to update.");

const avatarUploadSchema = z.object({
  contentType: z.string().default("image/png")
});

export const profilesV1Route = new Hono<AppEnv>();

profilesV1Route.get("/:profileId/avatar", async (c) => {
  const profileService = di.resolve<ProfileService>(TOKENS.ProfileService);
  const profileId = c.req.param("profileId");
  const avatar = await profileService.getAvatar(profileId);

  const ifNoneMatch = c.req.header("if-none-match");
  if (ifNoneMatch && avatar.etag && ifNoneMatch === avatar.etag) {
    return new Response(null, { status: 304 });
  }

  const headers = new Headers({
    "content-type": avatar.contentType ?? "image/png",
    "cache-control": "public, max-age=300"
  });
  if (avatar.etag) {
    headers.set("etag", avatar.etag);
  }
  if (avatar.lastModified) {
    headers.set("last-modified", avatar.lastModified.toUTCString());
  }

  const payload = Uint8Array.from(avatar.data);
  return new Response(payload.buffer, { status: 200, headers });
});

profilesV1Route.use("*", authRequired);

profilesV1Route.get("/", async (c) => {
  const profileService = di.resolve<ProfileService>(TOKENS.ProfileService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const profiles = await profileService.list(auth.userId);
  return ok(c, { profiles });
});

profilesV1Route.post("/", async (c) => {
  const profileService = di.resolve<ProfileService>(TOKENS.ProfileService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = createProfileSchema.parse(await c.req.json());
  const profile = await profileService.create(auth.userId, body);
  return ok(c, { profile }, 201);
});

profilesV1Route.post("/upsert", async (c) => {
  const profileService = di.resolve<ProfileService>(TOKENS.ProfileService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = upsertProfileSchema.parse(await c.req.json());
  const payload: Parameters<ProfileService["upsertByClientId"]>[2] = {
    name: body.name,
    server: body.server
  };
  if (body.isActive !== undefined) payload.isActive = body.isActive;
  if (body.playerRating !== undefined) payload.playerRating = body.playerRating;
  if (body.plate !== undefined) payload.plate = body.plate;
  if (body.avatarUrl !== undefined) payload.avatarUrl = body.avatarUrl;
  if (body.dfUsername !== undefined) payload.dfUsername = body.dfUsername;
  if (body.dfImportToken !== undefined) payload.dfImportToken = body.dfImportToken;
  if (body.lxnsRefreshToken !== undefined) payload.lxnsRefreshToken = body.lxnsRefreshToken;
  if (body.b35Count !== undefined) payload.b35Count = body.b35Count;
  if (body.b15Count !== undefined) payload.b15Count = body.b15Count;
  if (body.b35RecLimit !== undefined) payload.b35RecLimit = body.b35RecLimit;
  if (body.b15RecLimit !== undefined) payload.b15RecLimit = body.b15RecLimit;
  if (body.createdAt !== undefined) payload.createdAt = body.createdAt;
  const profile = await profileService.upsertByClientId(auth.userId, body.profileId, payload);
  return ok(c, { profile });
});

profilesV1Route.patch("/:profileId", async (c) => {
  const profileService = di.resolve<ProfileService>(TOKENS.ProfileService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = updateProfileSchema.parse(await c.req.json());
  const profileId = c.req.param("profileId");
  const payload: Parameters<ProfileService["update"]>[2] = {};
  if (body.name !== undefined) payload.name = body.name;
  if (body.server !== undefined) payload.server = body.server;
  if (body.isActive !== undefined) payload.isActive = body.isActive;
  if (body.playerRating !== undefined) payload.playerRating = body.playerRating;
  if (body.plate !== undefined) payload.plate = body.plate;
  if (body.dfUsername !== undefined) payload.dfUsername = body.dfUsername;
  if (body.dfImportToken !== undefined) payload.dfImportToken = body.dfImportToken;
  if (body.lxnsRefreshToken !== undefined) payload.lxnsRefreshToken = body.lxnsRefreshToken;
  if (body.b35Count !== undefined) payload.b35Count = body.b35Count;
  if (body.b15Count !== undefined) payload.b15Count = body.b15Count;
  if (body.b35RecLimit !== undefined) payload.b35RecLimit = body.b35RecLimit;
  if (body.b15RecLimit !== undefined) payload.b15RecLimit = body.b15RecLimit;
  const profile = await profileService.update(auth.userId, profileId, payload);
  return ok(c, { profile });
});

profilesV1Route.post("/:profileId/avatar/upload-url", async (c) => {
  const profileService = di.resolve<ProfileService>(TOKENS.ProfileService);
  const auth = c.get("auth");
  if (!auth) {
    return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
  }
  const body = avatarUploadSchema.parse(await c.req.json());
  const profileId = c.req.param("profileId");
  const result = await profileService.createAvatarUploadUrl(auth.userId, profileId, body.contentType);
  return ok(c, result);
});
