import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { AdminUserService } from "../src/services/admin-user.service.js";

describe("AdminUserService", () => {
	it("auto-generates a handle when creating a user", async () => {
		const userCreate = vi.fn().mockImplementation(async ({ data }: { data: Record<string, unknown> }) => ({
			id: "user-1",
			email: data.email,
			username: data.username,
			usernameDiscriminator: data.usernameDiscriminator,
			isAdmin: false,
			createdAt: new Date("2026-04-12T10:00:00Z"),
		}));
		const profileCreate = vi.fn().mockResolvedValue({});
		const prisma = {
			user: {
				findUnique: vi.fn().mockResolvedValue(null),
			},
			$transaction: vi.fn(async (callback: (tx: unknown) => Promise<unknown>) =>
				callback({
					user: {
						findMany: vi.fn().mockResolvedValue([]),
						create: userCreate,
					},
					profile: {
						create: profileCreate,
					},
				}),
			),
		};

		const service = new AdminUserService(prisma as never);
		const result = await service.createUser({
			email: "TeSt.User@example.com",
			password: "Password1!",
		});

		expect(result).toMatchObject({
			email: "test.user@example.com",
			username: "test.user",
			usernameDiscriminator: "0001",
			handle: "test.user#0001",
		});
		expect(userCreate).toHaveBeenCalledWith(
			expect.objectContaining({
				data: expect.objectContaining({
					username: "test.user",
					usernameNormalized: "test.user",
					usernameDiscriminator: "0001",
				}),
			}),
		);
		expect(profileCreate).toHaveBeenCalledTimes(1);
	});

	it("includes handle in admin user rows", async () => {
		const prisma = {
			user: {
				findMany: vi.fn().mockResolvedValue([
					{
						id: "user-1",
						email: "alice@example.com",
						username: "Alice",
						usernameDiscriminator: "0042",
						status: "active",
						isAdmin: false,
						emailVerifiedAt: null,
						createdAt: new Date("2026-04-12T10:00:00Z"),
						updatedAt: new Date("2026-04-12T10:05:00Z"),
						totpCredential: { enabledAt: new Date("2026-04-12T10:06:00Z") },
						passkeyCredentials: [{ id: "pk-1" }],
						_count: {
							profiles: 2,
						},
					},
				]),
				count: vi.fn().mockResolvedValue(1),
			},
		};

		const service = new AdminUserService(prisma as never);
		const result = await service.listUsers({ limit: 20, offset: 0 });

		expect(result.total).toBe(1);
		expect(result.rows[0]).toMatchObject({
			email: "alice@example.com",
			handle: "Alice#0042",
			status: "active",
			profileCount: 2,
			mfa: {
				enabled: true,
				totpEnabled: true,
				passkeyCount: 1,
			},
		});
	});
});
