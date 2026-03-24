import { serve } from "@hono/node-server";
import { createApp } from "./app.js";
import { di } from "./di/container.js";
import { TOKENS } from "./di/tokens.js";
import { getEnv } from "./env.js";
import type { PrismaClient } from "@prisma/client";
import type { CatalogService } from "./services/catalog.service.js";

const env = getEnv();
const app = createApp();

const bootstrapCatalogIfEmpty = async () => {
  const prisma = di.resolve<PrismaClient>(TOKENS.Prisma);
  const existingSheets = await prisma.sheet.count();
  if (existingSheets > 0) {
    return;
  }

  const catalogService = di.resolve<CatalogService>(TOKENS.CatalogService);
  console.log("[bootstrap] catalog is empty, syncing from source...");
  const result = await catalogService.syncCatalog(false);
  console.log(
    `[bootstrap] catalog sync complete (applied=${result.applied}) snapshot=${result.snapshot.id.toString()}`
  );
};

const start = async () => {
  try {
    await bootstrapCatalogIfEmpty();
  } catch (error) {
    console.error("[bootstrap] catalog sync failed:", error);
  }

  serve(
    {
      fetch: app.fetch,
      hostname: env.HOST,
      port: env.PORT
    },
    (info) => {
      console.log(`maimaid-backend listening on http://${env.HOST}:${info.port}`);
    }
  );
};

void start();
