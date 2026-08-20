import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { ProfileService } from "../src/services/profile.service.js";

describe("ProfileService", () => {
	it("removes the R2 avatar after deleting its profile record", async () => {
		const profileId = "018f05e0-8674-7d98-a678-8fd69a4a2d63";
		const database = {
			profile: {
				findFirst: vi.fn().mockResolvedValue({ id: profileId, userId: "user", isActive: false }),
				findUnique: vi.fn().mockResolvedValue(null),
				delete: vi.fn().mockResolvedValue(undefined),
			},
		};
		const storage = { deleteAvatar: vi.fn().mockResolvedValue(undefined) };
		const service = new ProfileService(database as never, storage as never, { DATABASE_DIALECT: "postgresql" } as never);

		await service.remove("user", profileId);

		expect(database.profile.delete).toHaveBeenCalledWith({ where: { id: profileId } });
		expect(storage.deleteAvatar).toHaveBeenCalledWith(profileId);
	});

	it("matches optimistic-lock profile versions at API millisecond precision", async () => {
		const expectedUpdatedAt = new Date("2026-08-13T10:20:30.123Z");
		const updateManyAndReturn = vi.fn().mockResolvedValue([
			{
				id: "018f05e0-8674-7d98-a678-8fd69a4a2d63",
				userId: "018f05e0-8674-7d98-a678-8fd69a4a2d64",
				isActive: false,
			},
		]);
		const database = {
			profile: {
				findUnique: vi.fn().mockResolvedValue({
					id: "018f05e0-8674-7d98-a678-8fd69a4a2d63",
					userId: "018f05e0-8674-7d98-a678-8fd69a4a2d64",
				}),
				updateManyAndReturn,
			},
		};
		const service = new ProfileService(database as never, {} as never, { DATABASE_DIALECT: "postgresql" } as never);

		await service.upsertByClientId(
			"018f05e0-8674-7d98-a678-8fd69a4a2d64",
			"018f05e0-8674-7d98-a678-8fd69a4a2d63",
			{ name: "Test", server: "jp" },
			expectedUpdatedAt,
			false,
			database as never,
		);

		expect(updateManyAndReturn).toHaveBeenCalledWith({
			where: {
				id: "018f05e0-8674-7d98-a678-8fd69a4a2d63",
				userId: "018f05e0-8674-7d98-a678-8fd69a4a2d64",
				updatedAt: {
					gte: expectedUpdatedAt,
					lt: new Date("2026-08-13T10:20:30.124Z"),
				},
			},
			data: {
				name: "Test",
				server: "jp",
			},
		});
	});

	it("uses single-row writes for optimistic locking on D1", async () => {
		const userId = "018f05e0-8674-7d98-a678-8fd69a4a2d64";
		const profileId = "018f05e0-8674-7d98-a678-8fd69a4a2d63";
		const expectedUpdatedAt = new Date("2026-08-13T10:20:30.123Z");
		const updated = { id: profileId, userId, name: "Test", server: "jp", isActive: false };
		const database = {
			profile: {
				findUnique: vi.fn().mockResolvedValue({ id: profileId, userId }),
				findFirst: vi.fn().mockResolvedValue({ id: profileId }),
				update: vi.fn().mockResolvedValue(updated),
				updateManyAndReturn: vi.fn(),
			},
		};
		const service = new ProfileService(database as never, {} as never, { DATABASE_DIALECT: "sqlite" } as never);

		const result = await service.upsertByClientId(
			userId,
			profileId,
			{ name: "Test", server: "jp" },
			expectedUpdatedAt,
			false,
			database as never,
		);

		expect(result).toBe(updated);
		expect(database.profile.findFirst).toHaveBeenCalledWith({
			where: {
				id: profileId,
				userId,
				updatedAt: {
					gte: expectedUpdatedAt,
					lt: new Date("2026-08-13T10:20:30.124Z"),
				},
			},
			select: { id: true },
		});
		expect(database.profile.update).toHaveBeenCalledWith({
			where: { id: profileId },
			data: { name: "Test", server: "jp" },
		});
		expect(database.profile.updateManyAndReturn).not.toHaveBeenCalled();
	});

	it("deactivates D1 profiles with single-row updates", async () => {
		const userId = "018f05e0-8674-7d98-a678-8fd69a4a2d64";
		const activeProfileId = "018f05e0-8674-7d98-a678-8fd69a4a2d63";
		const otherProfileId = "018f05e0-8674-7d98-a678-8fd69a4a2d65";
		const database = {
			profile: {
				findMany: vi.fn().mockResolvedValue([{ id: otherProfileId }]),
				update: vi.fn().mockResolvedValue({ id: otherProfileId, isActive: false }),
				updateManyAndReturn: vi.fn(),
			},
		};
		const service = new ProfileService(database as never, {} as never, { DATABASE_DIALECT: "sqlite" } as never);

		const result = await service.deactivateOtherProfiles(userId, activeProfileId, database as never);

		expect(result).toEqual([{ id: otherProfileId, isActive: false }]);
		expect(database.profile.update).toHaveBeenCalledWith({
			where: { id: otherProfileId },
			data: { isActive: false },
		});
		expect(database.profile.updateManyAndReturn).not.toHaveBeenCalled();
	});

	it("matches avatar upload profile versions at API millisecond precision", async () => {
		const userId = "018f05e0-8674-7d98-a678-8fd69a4a2d64";
		const profileId = "018f05e0-8674-7d98-a678-8fd69a4a2d63";
		const expectedUpdatedAt = new Date("2026-08-13T10:20:30.123Z");
		const updateManyAndReturn = vi.fn().mockResolvedValue([
			{
				id: profileId,
				updatedAt: expectedUpdatedAt,
			},
		]);
		const database = {
			profile: {
				findFirst: vi.fn().mockResolvedValue({ id: profileId, userId }),
				updateManyAndReturn,
			},
		};
		const storage = {
			createAvatarUploadUrl: vi.fn().mockResolvedValue({
				key: `avatars/profiles/${profileId}`,
				uploadUrl: "https://storage.example/avatar",
			}),
		};
		const service = new ProfileService(database as never, storage as never, { DATABASE_DIALECT: "postgresql" } as never);

		const result = await service.createAvatarUploadUrl(userId, profileId, "image/png", expectedUpdatedAt);

		expect(updateManyAndReturn).toHaveBeenCalledWith({
			where: {
				id: profileId,
				userId,
				updatedAt: {
					gte: expectedUpdatedAt,
					lt: new Date("2026-08-13T10:20:30.124Z"),
				},
			},
			data: { avatarObjectKey: `avatars/profiles/${profileId}` },
		});
		expect(result).toEqual({
			key: `avatars/profiles/${profileId}`,
			uploadUrl: "https://storage.example/avatar",
			updatedAt: "2026-08-13T10:20:30.123Z",
		});
	});
});
