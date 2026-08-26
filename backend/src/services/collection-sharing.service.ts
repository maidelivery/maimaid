import { inject, injectable } from "tsyringe";
import type { PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";

export type PublicSongCollection = {
	name: string;
	entries: Array<{
		songId: string;
		chartType: string;
		difficulty: string;
	}>;
};

@injectable()
export class CollectionSharingService {
	constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

	async findPublicCollection(collectionId: string): Promise<PublicSongCollection | null> {
		const collection = await this.prisma.songCollection.findFirst({
			where: { id: collectionId, deletedAt: null },
			select: {
				name: true,
				items: {
					where: { deletedAt: null },
					orderBy: [{ position: "asc" }, { createdAt: "asc" }],
					select: {
						songId: true,
						chartType: true,
						difficulty: true,
					},
				},
			},
		});
		if (!collection) {
			return null;
		}

		return {
			name: collection.name,
			entries: collection.items.map((item) => ({
				songId: item.songId,
				chartType: item.chartType,
				difficulty: item.difficulty,
			})),
		};
	}
}
