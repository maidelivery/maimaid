import { Prisma } from "@prisma/client";
import { assignUserHandle, buildUsernameBaseFromEmail } from "../src/lib/user-handle.js";
import { getPrismaClient } from "../src/lib/prisma.js";

const prisma = getPrismaClient();

async function main() {
	const users = await prisma.$queryRaw<
		Array<{
			id: string;
			email: string;
			username: string | null;
			usernameNormalized: string | null;
			usernameDiscriminator: string | null;
		}>
	>(Prisma.sql`
		SELECT
			id,
			email,
			username,
			"usernameNormalized",
			"usernameDiscriminator"
		FROM users
		WHERE username IS NULL
			OR "usernameNormalized" IS NULL
			OR "usernameDiscriminator" IS NULL
		ORDER BY "createdAt" ASC, id ASC
	`);

	for (const user of users) {
		const handle = await prisma.$transaction(async (tx) => {
			const current = await tx.user.findUnique({
				where: { id: user.id },
				select: {
					id: true,
					usernameNormalized: true,
					usernameDiscriminator: true,
				},
			});
			if (!current) {
				return null;
			}

			const assigned = await assignUserHandle(tx, {
				requestedUsername: user.username?.trim() || buildUsernameBaseFromEmail(user.email),
				existingUser: current,
			});

			const updated = await tx.user.update({
				where: { id: user.id },
				data: assigned,
				select: {
					username: true,
					usernameDiscriminator: true,
				},
			});

			return {
				email: user.email,
				handle: `${updated.username}#${updated.usernameDiscriminator}`,
			};
		});

		if (handle) {
			console.log(`backfilled ${handle.email} -> ${handle.handle}`);
		}
	}
}

main()
	.catch((error) => {
		console.error(error);
		process.exitCode = 1;
	})
	.finally(async () => {
		await prisma.$disconnect();
	});
