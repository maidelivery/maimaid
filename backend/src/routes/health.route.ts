import { Hono } from "hono";
import type { PrismaClient } from "@prisma/client";
import { ok } from "../http/response.js";
import type { AppEnv } from "../types/hono.js";
import { TOKENS } from "../di/tokens.js";

export const healthRoute = new Hono<AppEnv>();

healthRoute.get("/", (c) => {
	return ok(c, {
		message: "ok",
		timestamp: new Date().toISOString(),
	});
});

healthRoute.get("/database", async (c) => {
	const prisma = c.var.resolve<PrismaClient>(TOKENS.Prisma);
	await prisma.$queryRaw`SELECT 1`;
	return ok(c, {
		message: "ok",
		timestamp: new Date().toISOString(),
	});
});
