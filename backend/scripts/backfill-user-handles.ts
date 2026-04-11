import { PrismaClient } from "@prisma/client";
import { Prisma } from "@prisma/client";
import { assignUserHandle, buildUsernameBaseFromEmail } from "../src/lib/user-handle.js";

const prisma = new PrismaClient();

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
			username_normalized AS "usernameNormalized",
			username_discriminator AS "usernameDiscriminator"
		FROM users
		WHERE username IS NULL
			OR username_normalized IS NULL
			OR username_discriminator IS NULL
		ORDER BY created_at ASC, id ASC
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

			await tx.user.update({
				where: { id: user.id },
				data: assigned,
			});

			return {
				email: user.email,
				handle: `${assigned.username}#${assigned.usernameDiscriminator}`,
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
