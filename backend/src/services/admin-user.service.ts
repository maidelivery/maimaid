import { hash } from "bcryptjs";
import { inject, injectable } from "tsyringe";
import type { PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { isPasswordComplexEnough, PASSWORD_COMPLEXITY_ERROR_MESSAGE } from "../lib/auth-validation.js";

@injectable()
export class AdminUserService {
	constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

	async listUsers(input: { limit: number; offset: number }) {
		const rows = await this.prisma.user.findMany({
			orderBy: { createdAt: "desc" },
			skip: input.offset,
			take: input.limit,
			include: {
				totpCredential: true,
				passkeyCredentials: {
					select: { id: true },
				},
				_count: {
					select: {
						profiles: true,
					},
				},
			},
		});

		const total = await this.prisma.user.count();
		return {
			total,
			rows: rows.map((row) => ({
				id: row.id,
				email: row.email,
				status: row.status,
				isAdmin: row.isAdmin,
				emailVerifiedAt: row.emailVerifiedAt,
				createdAt: row.createdAt,
				updatedAt: row.updatedAt,
				profileCount: row._count.profiles,
				mfa: {
					totpEnabled: Boolean(row.totpCredential?.enabledAt),
					passkeyCount: row.passkeyCredentials.length,
					enabled: Boolean(row.totpCredential?.enabledAt) || row.passkeyCredentials.length > 0,
				},
			})),
		};
	}

	async createUser(input: { email: string; password: string }) {
		const normalizedEmail = input.email.trim().toLowerCase();
		if (!normalizedEmail || !normalizedEmail.includes("@")) {
			throw new AppError(400, "invalid_email", "A valid email is required.");
		}
		if (!isPasswordComplexEnough(input.password)) {
			throw new AppError(400, "invalid_password", PASSWORD_COMPLEXITY_ERROR_MESSAGE);
		}

		const existed = await this.prisma.user.findUnique({
			where: { email: normalizedEmail },
		});
		if (existed) {
			throw new AppError(409, "email_exists", "Email already exists.");
		}

		const passwordHash = await hash(input.password, 12);
		const user = await this.prisma.$transaction(async (tx) => {
			const created = await tx.user.create({
				data: {
					email: normalizedEmail,
					passwordHash,
					isAdmin: false,
				},
			});
			await tx.profile.create({
				data: {
					userId: created.id,
					name: "Default",
					server: "jp",
					isActive: true,
				},
			});
			return created;
		});

		return {
			id: user.id,
			email: user.email,
			isAdmin: user.isAdmin,
			createdAt: user.createdAt,
		};
	}

	async deleteUser(userId: string) {
		const existed = await this.prisma.user.findUnique({
			where: { id: userId },
		});
		if (!existed) {
			throw new AppError(404, "user_not_found", "User not found.");
		}
		await this.prisma.user.delete({
			where: { id: userId },
		});
		return {
			deleted: true,
		};
	}
}
