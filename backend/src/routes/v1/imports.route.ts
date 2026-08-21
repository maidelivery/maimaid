import { Hono } from "hono";
import { z } from "zod";
import { ImportService, type DivingFishScoreSyncRecord } from "../../services/import.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";
import { isAppError } from "../../lib/errors.js";

const dfSchema = z.object({
	profileId: z.uuid(),
});

const dfScoreSyncSchema = z.object({
	profileId: z.uuid(),
	records: z
		.array(
			z.object({
				title: z.string().trim().min(1).max(512),
				chartType: z.enum(["standard", "dx"]),
				levelIndex: z.number().int().min(0).max(4),
				achievements: z.number().min(0).max(101),
				dxScore: z.number().int().min(0),
				fc: z.string().nullable().optional(),
				fs: z.string().nullable().optional(),
			}),
		)
		.min(1)
		.max(100),
});

const lxnsSchema = z.object({
	profileId: z.uuid(),
	accessToken: z.string().min(8),
});

export const importsV1Route = new Hono<AppEnv>();

const lxnsTransformSchema = z.object({
	accessToken: z.string().min(8),
});

const lxnsOauthTokenSchema = z.object({
	code: z.string().min(1),
	codeVerifier: z.string().min(20),
});

const dfAuthorizationStatusSchema = z.object({ authorizationId: z.uuid() });
const dfBindingStatusSchema = z.object({ profileId: z.uuid() });
const dfCallbackSchema = z.object({
	state: z.string().optional(),
	code: z.string().optional(),
	error: z.string().optional(),
	error_description: z.string().optional(),
});

const escapeHtml = (value: string) =>
	value.replaceAll("&", "&amp;").replaceAll("<", "&lt;").replaceAll(">", "&gt;").replaceAll('"', "&quot;");

const callbackPage = (title: string, message: string) => `<!doctype html>
<html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escapeHtml(title)}</title><style>body{font-family:system-ui,sans-serif;max-width:36rem;margin:12vh auto;padding:1.5rem;line-height:1.6;color:#18181b}h1{font-size:1.5rem}</style></head>
<body><h1>${escapeHtml(title)}</h1><p>${escapeHtml(message)}</p></body></html>`;

importsV1Route.get("/imports:divingFishCallback", standardValidator("query", dfCallbackSchema, validationHook), async (c) => {
	const importService = c.var.resolve(ImportService);
	const query = c.req.valid("query");
	try {
		const payload: Parameters<ImportService["completeDivingFishAuthorization"]>[0] = {};
		if (query.state !== undefined) payload.state = query.state;
		if (query.code !== undefined) payload.code = query.code;
		if (query.error !== undefined) payload.error = query.error;
		if (query.error_description !== undefined) payload.errorDescription = query.error_description;
		await importService.completeDivingFishAuthorization(payload);
		return c.html(callbackPage("Diving Fish 授权完成", "账号已连接，可以返回 maimaid 继续导入。"));
	} catch (error) {
		const message = isAppError(error) ? error.message : "Diving Fish 授权失败，请返回 maimaid 重试。";
		return c.html(callbackPage("Diving Fish 授权失败", message), isAppError(error) ? 400 : 500);
	}
});

importsV1Route.post(
	"/imports:authorizeDivingFish",
	authRequired,
	standardValidator("json", dfSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		const result = await c.var.resolve(ImportService).startDivingFishAuthorization({
			userId: auth.userId,
			profileId: c.req.valid("json").profileId,
		});
		return ok(c, result);
	},
);

importsV1Route.get(
	"/imports:divingFishAuthorizationStatus",
	authRequired,
	standardValidator("query", dfAuthorizationStatusSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		return ok(
			c,
			await c.var.resolve(ImportService).getDivingFishAuthorizationStatus({
				userId: auth.userId,
				authorizationId: c.req.valid("query").authorizationId,
			}),
		);
	},
);

importsV1Route.get(
	"/imports:divingFishBinding",
	authRequired,
	standardValidator("query", dfBindingStatusSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		return ok(
			c,
			await c.var.resolve(ImportService).getDivingFishBindingStatus({
				userId: auth.userId,
				profileId: c.req.valid("query").profileId,
			}),
		);
	},
);

importsV1Route.delete(
	"/imports:divingFishBinding",
	authRequired,
	standardValidator("json", dfSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		return ok(
			c,
			await c.var.resolve(ImportService).disconnectDivingFish({
				userId: auth.userId,
				profileId: c.req.valid("json").profileId,
			}),
		);
	},
);

importsV1Route.post(
	"/imports:transformLxns",
	authRequired,
	standardValidator("json", lxnsTransformSchema, validationHook),
	async (c) => {
		const importService = c.var.resolve(ImportService);
		const body = c.req.valid("json");
		const result = await importService.transformFromLxns({
			accessToken: body.accessToken,
		});
		return ok(c, result);
	},
);

importsV1Route.post(
	"/imports:exchangeLxnsToken",
	authRequired,
	standardValidator("json", lxnsOauthTokenSchema, validationHook),
	async (c) => {
		const importService = c.var.resolve(ImportService);
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const body = c.req.valid("json");
		const result = await importService.exchangeLxnsAuthorizationCode({
			code: body.code,
			codeVerifier: body.codeVerifier,
		});
		return ok(c, result);
	},
);

importsV1Route.post("/imports:importDf", authRequired, standardValidator("json", dfSchema, validationHook), async (c) => {
	const importService = c.var.resolve(ImportService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const body = c.req.valid("json");
	const payload: Parameters<ImportService["importFromDivingFish"]>[0] = {
		userId: auth.userId,
		profileId: body.profileId,
	};
	const result = await importService.importFromDivingFish(payload);
	return ok(c, result);
});

importsV1Route.post(
	"/imports:syncDivingFishScores",
	authRequired,
	standardValidator("json", dfScoreSyncSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		const body = c.req.valid("json");
		return ok(
			c,
			await c.var.resolve(ImportService).syncScoresToDivingFish({
				userId: auth.userId,
				profileId: body.profileId,
				records: body.records as DivingFishScoreSyncRecord[],
			}),
		);
	},
);

importsV1Route.post("/imports:importLxns", authRequired, standardValidator("json", lxnsSchema, validationHook), async (c) => {
	const importService = c.var.resolve(ImportService);
	const auth = c.get("auth");
	if (!auth) {
		return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
	}
	const body = c.req.valid("json");
	const result = await importService.importFromLxns({
		userId: auth.userId,
		profileId: body.profileId,
		accessToken: body.accessToken,
	});
	return ok(c, result);
});
