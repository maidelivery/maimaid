import openApiDocument from "../dist/openapi.prebuilt.json";
import { createApp, registerOpenApiRoutes } from "../src/app.js";
import { parseEnv } from "../src/env.js";
import type { PrismaClient } from "@prisma/client";
import { createD1PrismaClient } from "./d1-prisma.js";

const app = createApp({
	resolveDependencies: (context) => {
		const d1 = context.env.MAIMAID_D1;
		if (!d1) {
			throw new Error("MAIMAID_D1 binding is unavailable.");
		}
		const dataBucket = context.env.MAIMAID_DATA;
		if (!dataBucket) {
			throw new Error("MAIMAID_DATA binding is unavailable.");
		}

		const env = parseEnv({
			...context.env,
			NODE_ENV: "production",
			DATABASE_URL: "file:d1",
			DATABASE_DIALECT: "sqlite",
		});
		return {
			env,
			prisma: createD1PrismaClient(d1 as never, dataBucket as never) as unknown as PrismaClient,
		};
	},
});

registerOpenApiRoutes(app, openApiDocument);

const dispatchScheduledJobs = async (env: Record<string, unknown>) => {
	const internalJobToken = typeof env.INTERNAL_JOB_TOKEN === "string" ? env.INTERNAL_JOB_TOKEN : "";
	if (!internalJobToken) {
		console.warn("[worker-cron] INTERNAL_JOB_TOKEN is not configured; skipping job dispatch");
		return;
	}
	const response = await app.request(
		new Request("https://maimaid-backend.internal/internal/jobs/dispatch", {
			method: "POST",
			headers: {
				"content-type": "application/json",
				authorization: `Bearer ${internalJobToken}`,
			},
			body: JSON.stringify({ limit: 5 }),
		}),
		env,
	);
	if (!response.ok) {
		throw new Error(`[worker-cron] job dispatch failed: ${response.status} ${await response.text()}`);
	}
};

export default {
	fetch: app.fetch,
	async scheduled(_controller: unknown, env: Record<string, unknown>) {
		await dispatchScheduledJobs(env);
	},
};
