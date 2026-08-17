import openApiDocument from "../dist/openapi.prebuilt.json";
import { createApp, registerOpenApiRoutes } from "../src/app.js";
import { parseEnv } from "../src/env.js";
import { createPrismaClient } from "../src/lib/prisma.js";

const app = createApp({
	resolveDependencies: (context) => {
		const hyperdrive = context.env.HYPERDRIVE;
		if (!hyperdrive) {
			throw new Error("HYPERDRIVE binding is unavailable.");
		}

		const env = parseEnv({
			...context.env,
			NODE_ENV: "production",
			DATABASE_URL: hyperdrive.connectionString,
		});
		return {
			env,
			prisma: createPrismaClient(hyperdrive.connectionString),
		};
	},
});

registerOpenApiRoutes(app, openApiDocument);

export default app;
