import { inject, injectable } from "tsyringe";
import type { PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";

// Tombstones must survive long enough for an offline device to pull deletes.
// Devices that remain offline longer than this window require a full snapshot.
const TOMBSTONE_RETENTION_DAYS = 90;

@injectable()
export class SongCollectionCleanupService {
	constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

	async removeExpiredTombstones(now = new Date()) {
		const cutoff = new Date(now.getTime() - TOMBSTONE_RETENTION_DAYS * 24 * 60 * 60 * 1000);
		return this.prisma.$transaction(async (transaction) => {
			const items = await transaction.songCollectionItem.deleteMany({
				where: { deletedAt: { not: null, lt: cutoff } },
			});
			const collections = await transaction.songCollection.deleteMany({
				where: { deletedAt: { not: null, lt: cutoff } },
			});
			return { items: items.count, collections: collections.count, cutoff };
		});
	}
}
