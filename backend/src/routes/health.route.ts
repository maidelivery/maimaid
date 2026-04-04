import { Hono } from "hono";
import { ok } from "../http/response.js";
import type { AppEnv } from "../types/hono.js";

export const healthRoute = new Hono<AppEnv>();

healthRoute.get("/", (c) => {
	return ok(c, {
		message: "ok",
		timestamp: new Date().toISOString(),
	});
});
