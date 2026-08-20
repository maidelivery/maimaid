import { Hono } from "hono";
import { z } from "zod";
import { ImportService } from "../../services/import.service.js";
import { authRequired } from "../../middleware/auth.js";
import { ok } from "../../http/response.js";
import { standardValidator, validationHook } from "../../http/validation.js";
import type { AppEnv } from "../../types/hono.js";
import { isAppError } from "../../lib/errors.js";

const dfSchema = z.object({
	profileId: z.uuid(),
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

const callbackPage = (success: boolean, message: string) => {
	const escapedMessage = message
		.replaceAll("&", "&amp;")
		.replaceAll("<", "&lt;")
		.replaceAll(">", "&gt;")
		.replaceAll('"', "&quot;")
		.replaceAll("'", "&#39;");
	return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark">
<title>Diving Fish authorization</title>
<style>
body { margin: 0; min-height: 100vh; display: grid; place-items: center; font: 16px system-ui, sans-serif; }
main { width: min(32rem, calc(100% - 3rem)); }
h1 { font-size: 1.5rem; }
p { line-height: 1.6; color: ${success ? "#16803a" : "#b42318"}; }
</style>
</head>
<body><main><h1>${success ? "Diving Fish connected" : "Authorization failed"}</h1><p>${escapedMessage}</p></main></body>
</html>`;
};

importsV1Route.get("/imports:divingFishCallback", async (c) => {
	const importService = c.var.resolve(ImportService);
	try {
		await importService.handleDivingFishCallback({
			code: c.req.query("code"),
			state: c.req.query("state"),
			error: c.req.query("error"),
		});
		return c.html(callbackPage(true, "Return to maimaid and start the import."), 200, {
			"Cache-Control": "no-store",
			"Referrer-Policy": "no-referrer",
		});
	} catch (error) {
		const message = isAppError(error) ? error.message : "Diving Fish authorization could not be completed.";
		if (!isAppError(error)) {
			console.error("[df_oauth_callback_failed]", error);
		}
		return c.html(callbackPage(false, message), isAppError(error) ? 400 : 500, {
			"Cache-Control": "no-store",
			"Referrer-Policy": "no-referrer",
		});
	}
});

importsV1Route.post(
	"/imports:authorizeDivingFish",
	authRequired,
	standardValidator("json", dfSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const result = await c.var.resolve(ImportService).createDivingFishAuthorization({
			userId: auth.userId,
			profileId: c.req.valid("json").profileId,
		});
		return ok(c, result);
	},
);

importsV1Route.get(
	"/imports:divingFishConnection",
	authRequired,
	standardValidator("query", dfSchema, validationHook),
	async (c) => {
		const auth = c.get("auth");
		if (!auth) {
			return ok(c, { code: "unauthorized", message: "Authentication required." }, 401);
		}
		const result = await c.var.resolve(ImportService).getDivingFishConnection({
			userId: auth.userId,
			profileId: c.req.valid("query").profileId,
		});
		return ok(c, result);
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
	const result = await importService.importFromDivingFish({
		userId: auth.userId,
		profileId: body.profileId,
	});
	return ok(c, result);
});

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
