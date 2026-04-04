import { PrismaClient } from "@prisma/client";
import { PrismaPg } from "@prisma/adapter-pg";
import { Pool } from "pg";
import { getEnv } from "../env.js";

declare global {
	var __maimaid_prisma__: PrismaClient | undefined;
	var __maimaid_pg_pool__: Pool | undefined;
}

const getPool = (): Pool => {
	if (!globalThis.__maimaid_pg_pool__) {
		const env = getEnv();
		globalThis.__maimaid_pg_pool__ = new Pool({
			connectionString: env.DATABASE_URL,
		});
	}
	return globalThis.__maimaid_pg_pool__;
};

export const getPrismaClient = (): PrismaClient => {
	if (!globalThis.__maimaid_prisma__) {
		const adapter = new PrismaPg(getPool() as unknown as ConstructorParameters<typeof PrismaPg>[0]);
		globalThis.__maimaid_prisma__ = new PrismaClient({ adapter });
	}
	return globalThis.__maimaid_prisma__;
};
