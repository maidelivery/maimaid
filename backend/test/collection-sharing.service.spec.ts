import "reflect-metadata";
import { describe, expect, it, vi } from "vitest";
import { CollectionSharingService } from "../src/services/collection-sharing.service.js";

describe("CollectionSharingService", () => {
	it("returns only public collection structure in stored order", async () => {
		const findFirst = vi.fn().mockResolvedValue({
			name: "Practice",
			items: [
				{ songId: "100", chartType: "dx", difficulty: "master" },
				{ songId: "200", chartType: "std", difficulty: "expert" },
			],
		});
		const service = new CollectionSharingService({ songCollection: { findFirst } } as never);

		await expect(service.findPublicCollection("018f05e0-8674-7d98-a678-8fd69a4a2d63")).resolves.toEqual({
			name: "Practice",
			entries: [
				{ songId: "100", chartType: "dx", difficulty: "master" },
				{ songId: "200", chartType: "std", difficulty: "expert" },
			],
		});
		expect(findFirst).toHaveBeenCalledWith({
			where: { id: "018f05e0-8674-7d98-a678-8fd69a4a2d63", deletedAt: null },
			select: {
				name: true,
				items: {
					where: { deletedAt: null },
					orderBy: [{ position: "asc" }, { createdAt: "asc" }],
					select: { songId: true, chartType: true, difficulty: true },
				},
			},
		});
	});

	it("returns null for missing or deleted collections", async () => {
		const service = new CollectionSharingService({
			songCollection: { findFirst: vi.fn().mockResolvedValue(null) },
		} as never);

		await expect(service.findPublicCollection("018f05e0-8674-7d98-a678-8fd69a4a2d63")).resolves.toBeNull();
	});
});
