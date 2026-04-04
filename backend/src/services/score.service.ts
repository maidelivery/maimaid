import { inject, injectable } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";
import { AppError } from "../lib/errors.js";
import { difficultyByLevelIndex, normalizeChartType, normalizeLxnsSongId } from "../utils/compat.js";

export type ScoreLocator = {
	sheetId?: bigint;
	songIdentifier?: string;
	songId?: number;
	title?: string;
	chartType?: string;
	type?: string;
	difficulty?: string;
	levelIndex?: number;
};

export type UpsertScoreInput = ScoreLocator & {
	achievements: number;
	rank?: string;
	dxScore?: number;
	fc?: string | null;
	fs?: string | null;
	achievedAt?: string | Date;
	sourcePayload?: unknown;
};

export type PlayRecordInput = ScoreLocator & {
	achievements: number;
	rank?: string;
	dxScore?: number;
	fc?: string | null;
	fs?: string | null;
	playTime?: string | Date;
	sourcePayload?: unknown;
};

export type UpdateBestScoreInput = {
	achievements?: number;
	rank?: string;
	dxScore?: number;
	fc?: string | null;
	fs?: string | null;
	achievedAt?: string | Date;
};

const FC_ORDER = ["fc", "fcp", "ap", "app"];
const FS_ORDER = ["sync", "fs", "fsp", "fsd", "fsdp"];

type ResolvedSheet = {
	id: bigint;
	songIdentifier: string;
	chartType: string;
	difficulty: string;
	song: {
		title: string;
	} | null;
};

@injectable()
export class ScoreService {
	constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

	async listBestScores(profileId: string) {
		return this.prisma.bestScore.findMany({
			where: { profileId },
			include: {
				sheet: {
					include: { song: true },
				},
			},
			orderBy: [{ updatedAt: "desc" }],
		});
	}

	async listPlayRecords(profileId: string, limit: number) {
		return this.prisma.playRecord.findMany({
			where: { profileId },
			include: {
				sheet: {
					include: { song: true },
				},
			},
			orderBy: [{ playTime: "desc" }],
			take: limit,
		});
	}

	async updateBestScore(scoreId: string, userId: string, input: UpdateBestScoreInput) {
		const existing = await this.prisma.bestScore.findFirst({
			where: {
				id: scoreId,
				profile: {
					userId,
				},
			},
			include: {
				sheet: {
					include: { song: true },
				},
			},
		});
		if (!existing) {
			throw new AppError(404, "score_not_found", "Score not found.");
		}

		const achievements =
			input.achievements !== undefined ? this.normalizeAchievements(input.achievements) : existing.achievements.toNumber();
		const rank = input.rank?.trim() || this.rankByAchievements(achievements);
		const dxScore = input.dxScore !== undefined ? this.normalizeDxScore(input.dxScore) : existing.dxScore;
		const fc = input.fc !== undefined ? this.normalizeFc(input.fc) : existing.fc;
		const fs = input.fs !== undefined ? this.normalizeFs(input.fs) : existing.fs;
		const achievedAt =
			input.achievedAt !== undefined ? (this.normalizeDate(input.achievedAt) ?? existing.achievedAt) : existing.achievedAt;

		return this.prisma.bestScore.update({
			where: { id: existing.id },
			data: {
				achievements,
				rank,
				dxScore,
				fc,
				fs,
				achievedAt,
			},
			include: {
				sheet: {
					include: { song: true },
				},
			},
		});
	}

	async deleteBestScore(scoreId: string, userId: string) {
		const existing = await this.prisma.bestScore.findFirst({
			where: {
				id: scoreId,
				profile: {
					userId,
				},
			},
			select: {
				id: true,
				profileId: true,
			},
		});
		if (!existing) {
			throw new AppError(404, "score_not_found", "Score not found.");
		}
		await this.prisma.bestScore.delete({
			where: { id: existing.id },
		});
		return {
			deleted: true,
			profileId: existing.profileId,
		};
	}

	async deletePlayRecord(recordId: string, userId: string) {
		const existing = await this.prisma.playRecord.findFirst({
			where: {
				id: recordId,
				profile: {
					userId,
				},
			},
			select: {
				id: true,
				profileId: true,
			},
		});
		if (!existing) {
			throw new AppError(404, "play_record_not_found", "Play record not found.");
		}
		await this.prisma.playRecord.delete({
			where: { id: existing.id },
		});
		return {
			deleted: true,
			profileId: existing.profileId,
		};
	}

	async bulkUpsertBestScores(profileId: string, scores: UpsertScoreInput[], source: string) {
		const applied: Array<{ sheetId: bigint; action: "created" | "updated" }> = [];
		const skipped: Array<{ reason: string; locator: ScoreLocator }> = [];

		const sheets = await this.resolveSheets(scores);

		type PreparedScore = {
			locator: ScoreLocator;
			sheetId: bigint;
			achievements: number;
			rank: string;
			dxScore: number;
			fc: string | null;
			fs: string | null;
			achievedAt: Date;
			sourcePayload: Prisma.InputJsonValue | Prisma.NullableJsonNullValueInput;
		};

		const groupedBySheetId = new Map<string, PreparedScore[]>();
		for (const [index, score] of scores.entries()) {
			const sheet = sheets[index];
			if (!sheet) {
				skipped.push({ reason: "sheet_not_found", locator: score });
				continue;
			}

			const achievements = this.normalizeAchievements(score.achievements);
			const prepared: PreparedScore = {
				locator: score,
				sheetId: sheet.id,
				achievements,
				rank: score.rank ?? this.rankByAchievements(achievements),
				dxScore: this.normalizeDxScore(score.dxScore),
				fc: this.normalizeFc(score.fc),
				fs: this.normalizeFs(score.fs),
				achievedAt: this.normalizeDate(score.achievedAt) ?? new Date(),
				sourcePayload: this.toSourcePayload(score.sourcePayload),
			};
			const key = sheet.id.toString();
			const current = groupedBySheetId.get(key) ?? [];
			current.push(prepared);
			groupedBySheetId.set(key, current);
		}

		if (groupedBySheetId.size === 0) {
			return {
				applied,
				skipped,
			};
		}

		const sheetIds = Array.from(groupedBySheetId.values()).map((entries) => entries[0]!.sheetId);
		const existingRows = await this.prisma.bestScore.findMany({
			where: {
				profileId,
				sheetId: {
					in: sheetIds,
				},
			},
			select: {
				id: true,
				sheetId: true,
				achievements: true,
				dxScore: true,
				fc: true,
				fs: true,
				achievedAt: true,
			},
		});
		const existingBySheetId = new Map(existingRows.map((row) => [row.sheetId.toString(), row]));

		const createRows: Prisma.BestScoreCreateManyInput[] = [];
		const updateOps: Prisma.PrismaPromise<unknown>[] = [];

		for (const [sheetIdKey, entries] of groupedBySheetId.entries()) {
			const first = entries[0];
			if (!first) {
				continue;
			}

			let incomingAchievements = first.achievements;
			let incomingDxScore = first.dxScore;
			let incomingFc = first.fc;
			let incomingFs = first.fs;
			let achievedAtForMax = first.achievedAt;
			let latestSourcePayload = first.sourcePayload;

			for (let index = 1; index < entries.length; index += 1) {
				const entry = entries[index]!;
				if (entry.achievements > incomingAchievements) {
					incomingAchievements = entry.achievements;
					achievedAtForMax = entry.achievedAt;
				}
				incomingDxScore = Math.max(incomingDxScore, entry.dxScore);
				incomingFc = this.pickBetterProgress(incomingFc, entry.fc, FC_ORDER);
				incomingFs = this.pickBetterProgress(incomingFs, entry.fs, FS_ORDER);
				latestSourcePayload = entry.sourcePayload;
			}

			const existing = existingBySheetId.get(sheetIdKey);
			if (!existing) {
				const createRank = entries.length === 1 ? first.rank : this.rankByAchievements(incomingAchievements);
				createRows.push({
					profileId,
					sheetId: first.sheetId,
					achievements: incomingAchievements,
					rank: createRank,
					dxScore: incomingDxScore,
					fc: incomingFc,
					fs: incomingFs,
					achievedAt: achievedAtForMax,
					source,
					sourcePayload: latestSourcePayload,
				});
				for (const [entryIndex] of entries.entries()) {
					applied.push({
						sheetId: first.sheetId,
						action: entryIndex === 0 ? "created" : "updated",
					});
				}
				continue;
			}

			const mergedAchievements = Math.max(existing.achievements.toNumber(), incomingAchievements);
			const mergedDxScore = Math.max(existing.dxScore, incomingDxScore);
			const mergedFc = this.pickBetterProgress(existing.fc, incomingFc, FC_ORDER);
			const mergedFs = this.pickBetterProgress(existing.fs, incomingFs, FS_ORDER);
			const mergedAchievedAt = mergedAchievements > existing.achievements.toNumber() ? achievedAtForMax : existing.achievedAt;

			updateOps.push(
				this.prisma.bestScore.update({
					where: { id: existing.id },
					data: {
						achievements: mergedAchievements,
						rank: this.rankByAchievements(mergedAchievements),
						dxScore: mergedDxScore,
						fc: mergedFc,
						fs: mergedFs,
						achievedAt: mergedAchievedAt,
						source,
						sourcePayload: latestSourcePayload,
					},
				}),
			);
			for (let index = 0; index < entries.length; index += 1) {
				applied.push({ sheetId: first.sheetId, action: "updated" });
			}
		}

		if (createRows.length > 0) {
			await this.prisma.bestScore.createMany({
				data: createRows,
			});
		}
		if (updateOps.length > 0) {
			await this.prisma.$transaction(updateOps);
		}

		return {
			applied,
			skipped,
		};
	}

	async replaceBestScores(profileId: string, scores: UpsertScoreInput[], source: string) {
		const deleted = await this.prisma.bestScore.deleteMany({
			where: { profileId },
		});
		const result = await this.bulkUpsertBestScores(profileId, scores, source);
		return {
			deletedCount: deleted.count,
			...result,
		};
	}

	async bulkInsertPlayRecords(profileId: string, records: PlayRecordInput[], source: string) {
		const created: Array<{ sheetId: bigint }> = [];
		const skipped: Array<{ reason: string; locator: ScoreLocator }> = [];

		const sheets = await this.resolveSheets(records);

		type PreparedRecord = {
			locator: ScoreLocator;
			sheetId: bigint;
			achievements: number;
			rank: string;
			dxScore: number;
			fc: string | null;
			fs: string | null;
			playTime: Date;
			sourcePayload: Prisma.InputJsonValue | Prisma.NullableJsonNullValueInput;
		};

		const preparedRecords: PreparedRecord[] = [];
		for (const [index, record] of records.entries()) {
			const sheet = sheets[index];
			if (!sheet) {
				skipped.push({ reason: "sheet_not_found", locator: record });
				continue;
			}
			const achievements = this.normalizeAchievements(record.achievements);
			preparedRecords.push({
				locator: record,
				sheetId: sheet.id,
				achievements,
				rank: record.rank ?? this.rankByAchievements(achievements),
				dxScore: this.normalizeDxScore(record.dxScore),
				fc: this.normalizeFc(record.fc),
				fs: this.normalizeFs(record.fs),
				playTime: this.normalizeDate(record.playTime) ?? new Date(),
				sourcePayload: this.toSourcePayload(record.sourcePayload),
			});
		}

		if (preparedRecords.length === 0) {
			return {
				created,
				skipped,
			};
		}

		let minPlayTime = preparedRecords[0]!.playTime;
		let maxPlayTime = preparedRecords[0]!.playTime;
		const sheetIds = new Set<bigint>();
		for (const record of preparedRecords) {
			sheetIds.add(record.sheetId);
			if (record.playTime < minPlayTime) {
				minPlayTime = record.playTime;
			}
			if (record.playTime > maxPlayTime) {
				maxPlayTime = record.playTime;
			}
		}

		const existingRows = await this.prisma.playRecord.findMany({
			where: {
				profileId,
				sheetId: { in: Array.from(sheetIds) },
				playTime: {
					gte: minPlayTime,
					lte: maxPlayTime,
				},
			},
			select: {
				sheetId: true,
				playTime: true,
				achievements: true,
				dxScore: true,
				fc: true,
				fs: true,
			},
		});
		const existingKeys = new Set(
			existingRows.map((row) =>
				this.playRecordDuplicateKey({
					sheetId: row.sheetId,
					playTime: row.playTime,
					achievements: row.achievements.toNumber(),
					dxScore: row.dxScore,
					fc: row.fc,
					fs: row.fs,
				}),
			),
		);

		const createRows: Prisma.PlayRecordCreateManyInput[] = [];
		const seenKeys = new Set<string>();
		for (const record of preparedRecords) {
			const duplicateKey = this.playRecordDuplicateKey({
				sheetId: record.sheetId,
				playTime: record.playTime,
				achievements: record.achievements,
				dxScore: record.dxScore,
				fc: record.fc,
				fs: record.fs,
			});
			if (existingKeys.has(duplicateKey) || seenKeys.has(duplicateKey)) {
				skipped.push({ reason: "duplicated_play_record", locator: record.locator });
				continue;
			}
			seenKeys.add(duplicateKey);
			createRows.push({
				profileId,
				sheetId: record.sheetId,
				achievements: record.achievements,
				rank: record.rank,
				dxScore: record.dxScore,
				fc: record.fc,
				fs: record.fs,
				playTime: record.playTime,
				source,
				sourcePayload: record.sourcePayload,
			});
			created.push({ sheetId: record.sheetId });
		}

		if (createRows.length > 0) {
			await this.prisma.playRecord.createMany({
				data: createRows,
			});
		}

		return {
			created,
			skipped,
		};
	}

	async replacePlayRecords(profileId: string, records: PlayRecordInput[], source: string) {
		const deleted = await this.prisma.playRecord.deleteMany({
			where: { profileId },
		});
		const result = await this.bulkInsertPlayRecords(profileId, records, source);
		return {
			deletedCount: deleted.count,
			...result,
		};
	}

	async requireProfileOwnership(profileId: string, userId: string) {
		const profile = await this.prisma.profile.findFirst({
			where: { id: profileId, userId },
		});
		if (!profile) {
			throw new AppError(404, "profile_not_found", "Profile not found.");
		}
		return profile;
	}

	normalizeLxnsSongId(songId: number): number {
		return normalizeLxnsSongId(songId);
	}

	private async resolveSheets(locators: ScoreLocator[]): Promise<Array<ResolvedSheet | null>> {
		const resolved = new Array<ResolvedSheet | null>(locators.length).fill(null);

		const sheetIds = new Set<bigint>();
		const normalizedByIndex = new Map<number, { type: string; difficulty: string }>();
		const identifierCandidates = new Set<string>();

		for (const [index, locator] of locators.entries()) {
			if (locator.sheetId !== undefined) {
				sheetIds.add(locator.sheetId);
				continue;
			}

			const type = normalizeChartType(locator.chartType ?? locator.type);
			const difficulty = this.normalizeDifficulty(locator.difficulty, locator.levelIndex);
			if (!type || !difficulty) {
				continue;
			}
			normalizedByIndex.set(index, { type, difficulty });

			if (locator.songIdentifier) {
				identifierCandidates.add(locator.songIdentifier);
			}
			if (typeof locator.songId === "number" && Number.isFinite(locator.songId) && locator.songId > 0) {
				identifierCandidates.add(String(Math.trunc(locator.songId)));
			}
		}

		if (sheetIds.size > 0) {
			const rows = await this.prisma.sheet.findMany({
				where: {
					id: {
						in: Array.from(sheetIds),
					},
				},
				select: {
					id: true,
					songIdentifier: true,
					chartType: true,
					difficulty: true,
					song: {
						select: {
							title: true,
						},
					},
				},
			});
			const byId = new Map(rows.map((row) => [row.id.toString(), row]));
			for (const [index, locator] of locators.entries()) {
				if (locator.sheetId === undefined) {
					continue;
				}
				resolved[index] = byId.get(locator.sheetId.toString()) ?? null;
			}
		}

		const byIdentifierKey = new Map<string, ResolvedSheet>();
		if (identifierCandidates.size > 0) {
			const typeSet = Array.from(new Set(Array.from(normalizedByIndex.values()).map((item) => item.type)));
			const difficultySet = Array.from(new Set(Array.from(normalizedByIndex.values()).map((item) => item.difficulty)));
			if (typeSet.length > 0 && difficultySet.length > 0) {
				const rows = await this.prisma.sheet.findMany({
					where: {
						songIdentifier: {
							in: Array.from(identifierCandidates),
						},
						chartType: {
							in: typeSet,
						},
						difficulty: {
							in: difficultySet,
						},
					},
					select: {
						id: true,
						songIdentifier: true,
						chartType: true,
						difficulty: true,
						song: {
							select: {
								title: true,
							},
						},
					},
				});
				for (const row of rows) {
					const key = this.sheetIdentifierKey(row.songIdentifier, row.chartType, row.difficulty);
					if (!byIdentifierKey.has(key)) {
						byIdentifierKey.set(key, row);
					}
				}
			}
		}

		const unresolvedByTitle: number[] = [];
		for (const [index, locator] of locators.entries()) {
			if (resolved[index] !== null) {
				continue;
			}
			const normalized = normalizedByIndex.get(index);
			if (!normalized) {
				continue;
			}

			if (locator.songIdentifier) {
				const key = this.sheetIdentifierKey(locator.songIdentifier, normalized.type, normalized.difficulty);
				const hit = byIdentifierKey.get(key);
				if (hit) {
					resolved[index] = hit;
					continue;
				}
			}

			if (typeof locator.songId === "number" && Number.isFinite(locator.songId) && locator.songId > 0) {
				const songIdAsIdentifier = String(Math.trunc(locator.songId));
				if (songIdAsIdentifier !== locator.songIdentifier) {
					const key = this.sheetIdentifierKey(songIdAsIdentifier, normalized.type, normalized.difficulty);
					const hit = byIdentifierKey.get(key);
					if (hit) {
						resolved[index] = hit;
						continue;
					}
				}
			}

			if (locator.title) {
				unresolvedByTitle.push(index);
			}
		}

		if (unresolvedByTitle.length > 0) {
			const titles = Array.from(
				new Set(
					unresolvedByTitle
						.map((index) => locators[index]?.title)
						.filter((value): value is string => typeof value === "string" && value.length > 0),
				),
			);
			const typeSet = Array.from(
				new Set(
					unresolvedByTitle
						.map((index) => normalizedByIndex.get(index)?.type)
						.filter((value): value is string => Boolean(value)),
				),
			);
			const difficultySet = Array.from(
				new Set(
					unresolvedByTitle
						.map((index) => normalizedByIndex.get(index)?.difficulty)
						.filter((value): value is string => Boolean(value)),
				),
			);
			if (titles.length > 0 && typeSet.length > 0 && difficultySet.length > 0) {
				const rows = await this.prisma.sheet.findMany({
					where: {
						chartType: {
							in: typeSet,
						},
						difficulty: {
							in: difficultySet,
						},
						song: {
							title: {
								in: titles,
							},
						},
					},
					select: {
						id: true,
						songIdentifier: true,
						chartType: true,
						difficulty: true,
						song: {
							select: {
								title: true,
							},
						},
					},
				});
				const byTitleKey = new Map<string, ResolvedSheet>();
				for (const row of rows) {
					const title = row.song?.title;
					if (!title) {
						continue;
					}
					const key = this.sheetTitleKey(title, row.chartType, row.difficulty);
					if (!byTitleKey.has(key)) {
						byTitleKey.set(key, row);
					}
				}

				for (const index of unresolvedByTitle) {
					if (resolved[index] !== null) {
						continue;
					}
					const locator = locators[index];
					const normalized = normalizedByIndex.get(index);
					if (!locator?.title || !normalized) {
						continue;
					}
					const key = this.sheetTitleKey(locator.title, normalized.type, normalized.difficulty);
					resolved[index] = byTitleKey.get(key) ?? null;
				}
			}
		}

		return resolved;
	}

	private normalizeDifficulty(difficulty?: string, levelIndex?: number): string | null {
		if (difficulty) {
			return difficulty.trim().toLowerCase();
		}
		if (levelIndex === undefined) {
			return null;
		}
		return difficultyByLevelIndex(levelIndex);
	}

	private normalizeAchievements(value: number): number {
		if (!Number.isFinite(value)) {
			throw new AppError(400, "invalid_achievements", "achievements must be a number.");
		}
		if (value < 0) {
			return 0;
		}
		if (value > 101) {
			return 101;
		}
		return Number(value.toFixed(4));
	}

	private normalizeDxScore(value?: number): number {
		if (value === undefined || value === null || !Number.isFinite(value)) {
			return 0;
		}
		return Math.trunc(value);
	}

	private normalizeFc(value?: string | null): string | null {
		if (!value) {
			return null;
		}
		const normalized = value.toLowerCase().trim();
		if (!FC_ORDER.includes(normalized)) {
			return null;
		}
		return normalized;
	}

	private normalizeFs(value?: string | null): string | null {
		if (!value) {
			return null;
		}
		const normalized = value.toLowerCase().trim();
		if (!FS_ORDER.includes(normalized)) {
			return null;
		}
		return normalized;
	}

	private normalizeDate(value?: string | Date): Date | null {
		if (!value) {
			return null;
		}
		const date = value instanceof Date ? value : new Date(value);
		if (Number.isNaN(date.getTime())) {
			return null;
		}
		return date;
	}

	private rankByAchievements(rate: number): string {
		if (rate >= 100.5) return "SSS+";
		if (rate >= 100.0) return "SSS";
		if (rate >= 99.5) return "SS+";
		if (rate >= 99.0) return "SS";
		if (rate >= 98.0) return "S+";
		if (rate >= 97.0) return "S";
		if (rate >= 94.0) return "AAA";
		if (rate >= 90.0) return "AA";
		if (rate >= 80.0) return "A";
		if (rate >= 75.0) return "BBB";
		if (rate >= 70.0) return "BB";
		if (rate >= 60.0) return "B";
		if (rate >= 50.0) return "C";
		return "D";
	}

	private pickBetterProgress(
		currentValue: string | null | undefined,
		nextValue: string | null | undefined,
		order: string[],
	): string | null {
		const currentIndex = currentValue ? order.indexOf(currentValue.toLowerCase()) : -1;
		const nextIndex = nextValue ? order.indexOf(nextValue.toLowerCase()) : -1;
		if (nextIndex > currentIndex && nextValue) {
			return nextValue;
		}
		if (currentValue) {
			return currentValue;
		}
		return null;
	}

	private toSourcePayload(payload: unknown): Prisma.InputJsonValue | Prisma.NullableJsonNullValueInput {
		if (payload === undefined) {
			return Prisma.JsonNull;
		}
		return payload as Prisma.InputJsonValue;
	}

	private sheetIdentifierKey(songIdentifier: string, type: string, difficulty: string) {
		return `${songIdentifier}:${type}:${difficulty}`;
	}

	private sheetTitleKey(title: string, type: string, difficulty: string) {
		return `${title}:${type}:${difficulty}`;
	}

	private playRecordDuplicateKey(input: {
		sheetId: bigint;
		playTime: Date;
		achievements: number;
		dxScore: number;
		fc: string | null;
		fs: string | null;
	}) {
		return [
			input.sheetId.toString(),
			input.playTime.toISOString(),
			input.achievements.toFixed(4),
			input.dxScore.toString(),
			input.fc ?? "",
			input.fs ?? "",
		].join("|");
	}
}
