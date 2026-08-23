const LEGACY_DIVING_FISH_BATCH_SIZE = 100;

type TimestampedPlayRecord = {
	playTime?: string | undefined;
};

export const removeLegacyDivingFishPlayRecords = <T extends TimestampedPlayRecord>(records: readonly T[]): T[] => {
	const countByTimestamp = new Map<number, number>();
	for (const record of records) {
		if (!record.playTime) continue;
		const timestamp = Date.parse(record.playTime);
		if (!Number.isFinite(timestamp)) continue;
		countByTimestamp.set(timestamp, (countByTimestamp.get(timestamp) ?? 0) + 1);
	}

	const legacyTimestamps = new Set(
		Array.from(countByTimestamp.entries())
			.filter(([, count]) => count >= LEGACY_DIVING_FISH_BATCH_SIZE)
			.map(([timestamp]) => timestamp),
	);
	if (legacyTimestamps.size === 0) return Array.from(records);

	return records.filter((record) => {
		if (!record.playTime) return true;
		return !legacyTimestamps.has(Date.parse(record.playTime));
	});
};
