import { PrismaClient } from "@prisma/client";
import { PrismaPg } from "@prisma/adapter-pg";

export const createPrismaClient = (connectionString: string): PrismaClient => {
	const adapter = new PrismaPg({ connectionString });
	return new PrismaClient({ adapter });
};
