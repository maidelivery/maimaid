import { inject, singleton } from "tsyringe";
import { Prisma, type PrismaClient } from "@prisma/client";
import { TOKENS } from "../di/tokens.js";

const CHART_SLOT_COUNT = 5;
const DIST_BUCKET_COUNT = 14;
const FC_BUCKET_COUNT = 5;
const DEFAULT_DATA_JSON_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json";
const DEFAULT_SONGID_JSON_URL = "https://maimaid.shikoch.in/songid.json";

const DIFFICULTY_INDEX_MAP: Record<string, number> = {
	basic: 0,
	advanced: 1,
	expert: 2,
	master: 3,
	remaster: 4,
};

const RANK_DIST_LABELS = ["d", "c", "b", "bb", "bbb", "a", "aa", "aaa", "s", "sp", "ss", "ssp", "sss", "sssp"];

export type ChartFitChartEntry = {
	cnt: number;
	diff: string;
	fit_diff: number;
	avg: number;
	avg_dx: number;
	std_dev: number;
	dist: number[];
	fc_dist: number[];
};

export type ChartFitDiffDataEntry = {
	achievements: number;
	dist: number[];
	fc_dist: number[];
};

export type ChartFitPayload = {
	charts: Record<string, Array<ChartFitChartEntry | Record<string, never>>>;
	diff_data: Record<string, ChartFitDiffDataEntry>;
};

type NormalizedChartFitPayload = {
	charts: Record<string, Array<ChartFitChartEntry | null>>;
	diffData: Record<string, ChartFitDiffDataEntry>;
};

type ChartAccumulator = {
	songId: number;
	levelIndex: number;
	diffLabel: string;
	cnt: number;
	sumAchievements: number;
	sumAchievementsSquared: number;
	sumDxScore: number;
	dist: number[];
	fcDist: number[];
};

type DiffAccumulator = {
	cnt: number;
	sumAchievements: number;
	distCounts: number[];
	fcCounts: number[];
};

type ChartFitSourceInput = {
	dataJson?: unknown;
	songidJson?: unknown;
};

export const CHART_FIT_DIFF_WEIGHTS: Record<string, [number, number, number, number]> = {
	"1": [0.7, 0.1, 0.1, 0.1],
	"2": [0.7, 0.1, 0.1, 0.1],
	"3": [0.7, 0.1, 0.1, 0.1],
	"4": [0.7, 0.1, 0.1, 0.1],
	"5": [0.7, 0.1, 0.1, 0.1],
	"6": [0.7, 0.1, 0.1, 0.1],
	"7": [0.7, 0.1, 0.1, 0.1],
	"7+": [0.7, 0.1, 0.1, 0.1],
	"8": [0.7, 0.1, 0.1, 0.1],
	"8+": [0.7, 0.1, 0.1, 0.1],
	"9": [0.7, 0.1, 0.1, 0.1],
	"9+": [0.7, 0.1, 0.1, 0.1],
	"10": [0.7, 0.1, 0.1, 0.1],
	"10+": [0.7, 0.1, 0.1, 0.1],
	"11": [0.7, 0.1, 0.1, 0.1],
	"11+": [0.7, 0.1, 0.1, 0.1],
	"15": [0.7, 0.1, 0.1, 0.1],
	"12": [0.5, 0.2, 0.2, 0.1],
	"12+": [0.4, 0.2, 0.2, 0.2],
	"13": [0.3, 0.2, 0.2, 0.3],
	"13+": [0.3, 0.1, 0.25, 0.35],
	"14": [0.3, 0.0, 0.3, 0.4],
	"14+": [0.2, 0.0, 0.35, 0.45],
};

const emptyDistCounts = () => Array.from({ length: DIST_BUCKET_COUNT }, () => 0);
const emptyFcCounts = () => Array.from({ length: FC_BUCKET_COUNT }, () => 0);

const toRecord = (value: unknown): Record<string, unknown> | null =>
	typeof value === "object" && value !== null ? (value as Record<string, unknown>) : null;

const toFiniteNumber = (value: unknown): number | null => {
	const numeric = typeof value === "number" ? value : typeof value === "string" ? Number(value) : Number.NaN;
	if (!Number.isFinite(numeric)) {
		return null;
	}
	return numeric;
};

const toFiniteArray = (value: unknown, expectedLength: number): number[] | null => {
	if (!Array.isArray(value) || value.length !== expectedLength) {
		return null;
	}

	const parsed: number[] = [];
	for (const item of value) {
		const numeric = toFiniteNumber(item);
		if (numeric === null) {
			return null;
		}
		parsed.push(numeric);
	}
	return parsed;
};

const clampPositiveInt = (value: number) => Math.max(0, Math.round(value));

const normalizeDifficultyForCurve = (difficultyRaw: string) => {
	let normalized = difficultyRaw.trim();
	if (normalized.endsWith("?")) {
		normalized = normalized.slice(0, -1);
	}
	return normalized;
};

const toBaseDifficultyValue = (difficultyRaw: string): number | null => {
	const normalized = normalizeDifficultyForCurve(difficultyRaw);
	if (!normalized) {
		return null;
	}

	if (normalized.endsWith("+")) {
		const base = Number(normalized.slice(0, -1));
		if (!Number.isFinite(base)) {
			return null;
		}
		return base + 0.75;
	}

	const base = Number(normalized);
	if (!Number.isFinite(base)) {
		return null;
	}
	return base + 0.25;
};

export const chartFitAchievementCurve = (diff: number) => {
	if (diff <= -4) {
		return -0.5;
	}
	if (diff < -1) {
		return -0.1 + 0.1 * diff;
	}
	if (diff < 1) {
		return 0.2 * diff;
	}
	if (diff < 4) {
		return 0.1 + 0.1 * diff;
	}
	return 0.5;
};

export const chartFitPercentCurve = (diff: number) => {
	if (diff < -0.6) {
		return -0.25 + 0.25 * diff;
	}
	if (diff < -0.2) {
		return -0.1 + 0.5 * diff;
	}
	if (diff < 0.3) {
		return diff;
	}
	if (diff < 0.9) {
		return 0.15 + 0.5 * diff;
	}
	return 0.42 + 0.2 * diff;
};

export const chartFitGetDiff = (difficultyRaw: string, diffAch: number, diffS: number, diffSSS: number, diffSSSP: number) => {
	const normalized = normalizeDifficultyForCurve(difficultyRaw);
	const weights = CHART_FIT_DIFF_WEIGHTS[normalized];
	if (!weights) {
		return 0;
	}

	const baseDifficulty = toBaseDifficultyValue(difficultyRaw);
	if (baseDifficulty === null) {
		return 0;
	}

	return (
		baseDifficulty -
		chartFitAchievementCurve(diffAch) * weights[0] -
		chartFitPercentCurve(diffS) * weights[1] -
		chartFitPercentCurve(diffSSS) * weights[2] -
		chartFitPercentCurve(diffSSSP) * weights[3]
	);
};

const parseDiffDataEntry = (value: unknown): ChartFitDiffDataEntry | null => {
	const record = toRecord(value);
	if (!record) {
		return null;
	}

	const achievements = toFiniteNumber(record.achievements);
	const dist = toFiniteArray(record.dist, DIST_BUCKET_COUNT);
	const fcDist = toFiniteArray(record.fc_dist, FC_BUCKET_COUNT);
	if (achievements === null || !dist || !fcDist) {
		return null;
	}

	return {
		achievements,
		dist,
		fc_dist: fcDist,
	};
};

const parseChartEntry = (value: unknown): ChartFitChartEntry | null => {
	const record = toRecord(value);
	if (!record || Object.keys(record).length === 0) {
		return null;
	}

	const cntRaw = toFiniteNumber(record.cnt);
	const fitDiff = toFiniteNumber(record.fit_diff);
	const avg = toFiniteNumber(record.avg);
	const avgDx = toFiniteNumber(record.avg_dx);
	const stdDev = toFiniteNumber(record.std_dev);
	const diffRaw = record.diff;
	const distRaw = toFiniteArray(record.dist, DIST_BUCKET_COUNT);
	const fcDistRaw = toFiniteArray(record.fc_dist, FC_BUCKET_COUNT);
	if (
		cntRaw === null ||
		fitDiff === null ||
		avg === null ||
		avgDx === null ||
		stdDev === null ||
		!distRaw ||
		!fcDistRaw ||
		(typeof diffRaw !== "string" && typeof diffRaw !== "number")
	) {
		return null;
	}

	return {
		cnt: clampPositiveInt(cntRaw),
		diff: String(diffRaw),
		fit_diff: fitDiff,
		avg,
		avg_dx: avgDx,
		std_dev: stdDev,
		dist: distRaw.map((item) => clampPositiveInt(item)),
		fc_dist: fcDistRaw.map((item) => clampPositiveInt(item)),
	};
};

const cloneEntry = (value: ChartFitChartEntry): ChartFitChartEntry => ({
	cnt: value.cnt,
	diff: value.diff,
	fit_diff: value.fit_diff,
	avg: value.avg,
	avg_dx: value.avg_dx,
	std_dev: value.std_dev,
	dist: [...value.dist],
	fc_dist: [...value.fc_dist],
});

const weightedAverage = (leftValue: number, leftWeight: number, rightValue: number, rightWeight: number) => {
	const total = leftWeight + rightWeight;
	if (total <= 0) {
		return 0;
	}
	return (leftValue * leftWeight + rightValue * rightWeight) / total;
};

const mergeChartEntries = (left: ChartFitChartEntry | null, right: ChartFitChartEntry | null) => {
	if (!left && !right) {
		return null;
	}
	if (left && !right) {
		return cloneEntry(left);
	}
	if (right && !left) {
		return cloneEntry(right);
	}

	const leftEntry = left!;
	const rightEntry = right!;
	const totalCnt = leftEntry.cnt + rightEntry.cnt;
	if (totalCnt <= 0) {
		return null;
	}

	return {
		cnt: totalCnt,
		diff: leftEntry.diff || rightEntry.diff,
		fit_diff: weightedAverage(leftEntry.fit_diff, leftEntry.cnt, rightEntry.fit_diff, rightEntry.cnt),
		avg: weightedAverage(leftEntry.avg, leftEntry.cnt, rightEntry.avg, rightEntry.cnt),
		avg_dx: weightedAverage(leftEntry.avg_dx, leftEntry.cnt, rightEntry.avg_dx, rightEntry.cnt),
		std_dev: weightedAverage(leftEntry.std_dev, leftEntry.cnt, rightEntry.std_dev, rightEntry.cnt),
		dist: leftEntry.dist.map((value, index) => clampPositiveInt(value + rightEntry.dist[index]!)),
		fc_dist: leftEntry.fc_dist.map((value, index) => clampPositiveInt(value + rightEntry.fc_dist[index]!)),
	};
};

const normalizeTitle = (value: string) => value.normalize("NFKC").trim().toLocaleLowerCase().replace(/\s+/gu, " ");

const normalizeType = (value: string | null | undefined) => {
	if (!value) {
		return "";
	}
	const lower = value.trim().toLocaleLowerCase();
	if (lower === "std" || lower === "sd" || lower === "standard") {
		return "standard";
	}
	if (lower === "dx") {
		return "dx";
	}
	return lower;
};

const sheetDifficultyToIndex = (value: string | null | undefined): number | null => {
	if (!value) {
		return null;
	}
	const mapped = DIFFICULTY_INDEX_MAP[value.trim().toLocaleLowerCase()];
	return mapped ?? null;
};

const rankToDistIndex = (achievements: number) => {
	if (achievements >= 100.5) return 13;
	if (achievements >= 100.0) return 12;
	if (achievements >= 99.5) return 11;
	if (achievements >= 99.0) return 10;
	if (achievements >= 98.0) return 9;
	if (achievements >= 97.0) return 8;
	if (achievements >= 94.0) return 7;
	if (achievements >= 90.0) return 6;
	if (achievements >= 80.0) return 5;
	if (achievements >= 75.0) return 4;
	if (achievements >= 70.0) return 3;
	if (achievements >= 60.0) return 2;
	if (achievements >= 50.0) return 1;
	return 0;
};

const fcToDistIndex = (value: string | null | undefined) => {
	const normalized = value?.trim().toLocaleLowerCase() ?? "";
	if (normalized === "fc") return 1;
	if (normalized === "fcp") return 2;
	if (normalized === "ap") return 3;
	if (normalized === "app") return 4;
	return 0;
};

const sumRange = (values: number[], fromInclusive: number) => {
	let total = 0;
	for (let index = fromInclusive; index < values.length; index += 1) {
		total += values[index]!;
	}
	return total;
};

const safeRelativeDiff = (value: number, baseline: number) => {
	if (!Number.isFinite(value) || !Number.isFinite(baseline) || baseline === 0) {
		return 0;
	}
	return (value - baseline) / baseline;
};

const toJsonValue = (value: unknown): Prisma.InputJsonValue => JSON.parse(JSON.stringify(value)) as Prisma.InputJsonValue;

const toChartArrayOutput = (entries: Array<ChartFitChartEntry | null>) => {
	const maxLength = Math.max(CHART_SLOT_COUNT, entries.length);
	const output: Array<ChartFitChartEntry | Record<string, never>> = Array.from({ length: maxLength }, () => ({}));
	for (let index = 0; index < entries.length; index += 1) {
		const entry = entries[index];
		if (entry) {
			output[index] = entry;
		}
	}
	return output;
};

const buildDiffDataFromCharts = (charts: Record<string, Array<ChartFitChartEntry | null>>) => {
	const aggregate = new Map<string, DiffAccumulator>();

	for (const entries of Object.values(charts)) {
		for (const entry of entries) {
			if (!entry || entry.cnt <= 0) {
				continue;
			}

			const key = entry.diff;
			const existing = aggregate.get(key) ?? {
				cnt: 0,
				sumAchievements: 0,
				distCounts: emptyDistCounts(),
				fcCounts: emptyFcCounts(),
			};

			existing.cnt += entry.cnt;
			existing.sumAchievements += entry.avg * entry.cnt;
			for (let index = 0; index < DIST_BUCKET_COUNT; index += 1) {
				existing.distCounts[index]! += clampPositiveInt(entry.dist[index] ?? 0);
			}
			for (let index = 0; index < FC_BUCKET_COUNT; index += 1) {
				existing.fcCounts[index]! += clampPositiveInt(entry.fc_dist[index] ?? 0);
			}

			aggregate.set(key, existing);
		}
	}

	const result: Record<string, ChartFitDiffDataEntry> = {};
	for (const [diff, value] of aggregate.entries()) {
		if (value.cnt <= 0) {
			continue;
		}

		result[diff] = {
			achievements: value.sumAchievements / value.cnt,
			dist: value.distCounts.map((item) => item / value.cnt),
			fc_dist: value.fcCounts.map((item) => item / value.cnt),
		};
	}

	return result;
};

export const normalizeChartStatsPayload = (raw: unknown): NormalizedChartFitPayload => {
	const root = toRecord(raw);
	if (!root) {
		return {
			charts: {},
			diffData: {},
		};
	}

	const chartRoot = toRecord(root.charts);
	const diffDataRoot = toRecord(root.diff_data);

	const charts: Record<string, Array<ChartFitChartEntry | null>> = {};
	if (chartRoot) {
		for (const [songId, payload] of Object.entries(chartRoot)) {
			if (!Array.isArray(payload)) {
				continue;
			}
			charts[songId] = payload.map((item) => parseChartEntry(item));
		}
	}

	const diffData: Record<string, ChartFitDiffDataEntry> = {};
	if (diffDataRoot) {
		for (const [diff, payload] of Object.entries(diffDataRoot)) {
			const parsed = parseDiffDataEntry(payload);
			if (parsed) {
				diffData[diff] = parsed;
			}
		}
	}

	return {
		charts,
		diffData,
	};
};

export const mergeChartStatsPayloads = (
	primaryRaw: unknown,
	secondaryRaw: unknown,
	options?: { secondaryMinCnt?: number },
): ChartFitPayload => {
	const primary = normalizeChartStatsPayload(primaryRaw);
	const secondary = normalizeChartStatsPayload(secondaryRaw);
	const secondaryMinCnt = Math.max(0, options?.secondaryMinCnt ?? 1000);

	const mergedCharts: Record<string, Array<ChartFitChartEntry | null>> = {};
	const songIds = new Set([...Object.keys(primary.charts), ...Object.keys(secondary.charts)]);

	for (const songId of songIds) {
		const left = primary.charts[songId] ?? [];
		const right = secondary.charts[songId] ?? [];
		const maxLength = Math.max(CHART_SLOT_COUNT, left.length, right.length);

		const merged = Array.from({ length: maxLength }, (_, index) => {
			const leftEntry = left[index] ?? null;
			const rightEntryCandidate = right[index] ?? null;
			const rightEntry = rightEntryCandidate && rightEntryCandidate.cnt >= secondaryMinCnt ? rightEntryCandidate : null;
			return mergeChartEntries(leftEntry, rightEntry);
		});

		if (merged.some((item) => item !== null)) {
			mergedCharts[songId] = merged;
		}
	}

	const diffData = buildDiffDataFromCharts(mergedCharts);

	return {
		charts: Object.fromEntries(Object.entries(mergedCharts).map(([songId, entries]) => [songId, toChartArrayOutput(entries)])),
		diff_data: diffData,
	};
};

@singleton()
export class ChartFitService {
	constructor(@inject(TOKENS.Prisma) private readonly prisma: PrismaClient) {}

	async refreshSnapshot(input?: ChartFitSourceInput) {
		const computed = await this.computeSelfChartStats(input);
		await this.prisma.chartFitSnapshot.create({
			data: {
				payloadJson: toJsonValue(computed.payload),
				metaJson: toJsonValue(computed.meta),
			},
		});
		return computed;
	}

	async getLatestSnapshotOrRefresh(input?: ChartFitSourceInput) {
		const latest = await this.prisma.chartFitSnapshot.findFirst({
			orderBy: { createdAt: "desc" },
		});

		if (latest) {
			const normalized = normalizeChartStatsPayload(latest.payloadJson);
			const payload: ChartFitPayload = {
				charts: Object.fromEntries(
					Object.entries(normalized.charts).map(([songId, entries]) => [songId, toChartArrayOutput(entries)]),
				),
				diff_data: buildDiffDataFromCharts(normalized.charts),
			};
			return {
				payload,
				meta: toRecord(latest.metaJson) ?? {},
				createdAt: latest.createdAt,
			};
		}

		const refreshed = await this.refreshSnapshot(input ?? (await this.fetchDefaultSourceInput()));
		return {
			payload: refreshed.payload,
			meta: refreshed.meta,
			createdAt: new Date(),
		};
	}

	mergePayloads(primaryRaw: unknown, secondaryRaw: unknown, secondaryMinCnt = 1000) {
		return mergeChartStatsPayloads(primaryRaw, secondaryRaw, {
			secondaryMinCnt,
		});
	}

	private async computeSelfChartStats(input?: ChartFitSourceInput) {
		const mapping = this.buildSongIdMapping(input?.dataJson, input?.songidJson);

		const rows = await this.prisma.bestScore.findMany({
			select: {
				achievements: true,
				dxScore: true,
				fc: true,
				sheet: {
					select: {
						songIdentifier: true,
						chartType: true,
						difficulty: true,
						level: true,
						song: {
							select: {
								title: true,
							},
						},
					},
				},
			},
		});

		const chartMap = new Map<string, ChartAccumulator>();
		let skippedMissingSongId = 0;
		let skippedInvalidLevel = 0;
		let skippedInvalidAchievement = 0;

		for (const row of rows) {
			const title = row.sheet.song?.title?.trim() ?? "";
			const chartType = normalizeType(row.sheet.chartType);
			const mappedSongId = this.resolveSongIdFromMapping(mapping, {
				title,
				chartType,
				songIdentifier: row.sheet.songIdentifier,
			});

			if (!mappedSongId) {
				skippedMissingSongId += 1;
				continue;
			}

			const levelIndex = sheetDifficultyToIndex(row.sheet.difficulty);
			if (levelIndex === null) {
				skippedInvalidLevel += 1;
				continue;
			}

			const achievements = Number(row.achievements);
			if (!Number.isFinite(achievements)) {
				skippedInvalidAchievement += 1;
				continue;
			}

			const diffLabel = row.sheet.level?.trim() ?? "";
			if (!diffLabel) {
				skippedInvalidLevel += 1;
				continue;
			}

			const key = `${mappedSongId}:${levelIndex}`;
			const current = chartMap.get(key) ?? {
				songId: mappedSongId,
				levelIndex,
				diffLabel,
				cnt: 0,
				sumAchievements: 0,
				sumAchievementsSquared: 0,
				sumDxScore: 0,
				dist: emptyDistCounts(),
				fcDist: emptyFcCounts(),
			};

			current.cnt += 1;
			current.sumAchievements += achievements;
			current.sumAchievementsSquared += achievements * achievements;
			current.sumDxScore += Number.isFinite(row.dxScore) ? row.dxScore : 0;
			current.diffLabel = diffLabel;
			current.dist[rankToDistIndex(achievements)]! += 1;
			current.fcDist[fcToDistIndex(row.fc)]! += 1;

			chartMap.set(key, current);
		}

		const provisionalByDiff = new Map<string, DiffAccumulator>();
		const provisionalEntries: Array<{ songId: number; levelIndex: number; entry: ChartFitChartEntry }> = [];

		for (const chart of chartMap.values()) {
			if (chart.cnt <= 0) {
				continue;
			}

			const avg = chart.sumAchievements / chart.cnt;
			const variance = Math.max(0, chart.sumAchievementsSquared / chart.cnt - avg * avg);
			const stdDev = Math.sqrt(variance);
			const avgDx = chart.sumDxScore / chart.cnt;

			const entry: ChartFitChartEntry = {
				cnt: chart.cnt,
				diff: chart.diffLabel,
				fit_diff: 0,
				avg,
				avg_dx: avgDx,
				std_dev: stdDev,
				dist: [...chart.dist],
				fc_dist: [...chart.fcDist],
			};
			provisionalEntries.push({ songId: chart.songId, levelIndex: chart.levelIndex, entry });

			const diffAggregate = provisionalByDiff.get(chart.diffLabel) ?? {
				cnt: 0,
				sumAchievements: 0,
				distCounts: emptyDistCounts(),
				fcCounts: emptyFcCounts(),
			};
			diffAggregate.cnt += chart.cnt;
			diffAggregate.sumAchievements += chart.sumAchievements;
			for (let index = 0; index < DIST_BUCKET_COUNT; index += 1) {
				diffAggregate.distCounts[index]! += chart.dist[index] ?? 0;
			}
			for (let index = 0; index < FC_BUCKET_COUNT; index += 1) {
				diffAggregate.fcCounts[index]! += chart.fcDist[index] ?? 0;
			}
			provisionalByDiff.set(chart.diffLabel, diffAggregate);
		}

		const diffData: Record<string, ChartFitDiffDataEntry> = {};
		for (const [diffLabel, aggregate] of provisionalByDiff.entries()) {
			if (aggregate.cnt <= 0) {
				continue;
			}
			diffData[diffLabel] = {
				achievements: aggregate.sumAchievements / aggregate.cnt,
				dist: aggregate.distCounts.map((value) => value / aggregate.cnt),
				fc_dist: aggregate.fcCounts.map((value) => value / aggregate.cnt),
			};
		}

		const chartsNormalized: Record<string, Array<ChartFitChartEntry | null>> = {};
		for (const item of provisionalEntries) {
			const baseline = diffData[item.entry.diff];
			const cnt = item.entry.cnt;

			const chartDistRatio = item.entry.dist.map((value) => value / cnt);
			const chartSRate = sumRange(chartDistRatio, 8);
			const chartSSSRate = sumRange(chartDistRatio, 12);
			const chartSSSPRate = chartDistRatio[13] ?? 0;

			const baselineSRate = baseline ? sumRange(baseline.dist, 8) : 0;
			const baselineSSSRate = baseline ? sumRange(baseline.dist, 12) : 0;
			const baselineSSSPRate = baseline?.dist[13] ?? 0;

			item.entry.fit_diff = chartFitGetDiff(
				item.entry.diff,
				item.entry.avg - (baseline?.achievements ?? item.entry.avg),
				safeRelativeDiff(chartSRate, baselineSRate),
				safeRelativeDiff(chartSSSRate, baselineSSSRate),
				safeRelativeDiff(chartSSSPRate, baselineSSSPRate),
			);

			const key = String(item.songId);
			const current = chartsNormalized[key] ?? Array.from({ length: CHART_SLOT_COUNT }, () => null);
			if (current.length < CHART_SLOT_COUNT) {
				for (let index = current.length; index < CHART_SLOT_COUNT; index += 1) {
					current.push(null);
				}
			}
			current[item.levelIndex] = item.entry;
			chartsNormalized[key] = current;
		}

		const payload: ChartFitPayload = {
			charts: Object.fromEntries(
				Object.entries(chartsNormalized).map(([songId, entries]) => [songId, toChartArrayOutput(entries)]),
			),
			diff_data: diffData,
		};

		const meta = {
			generatedAt: new Date().toISOString(),
			totalBestScores: rows.length,
			usedCharts: provisionalEntries.length,
			skippedMissingSongId,
			skippedInvalidLevel,
			skippedInvalidAchievement,
			rankLabels: RANK_DIST_LABELS,
		};

		return {
			payload,
			meta,
		};
	}

	private async fetchDefaultSourceInput(): Promise<ChartFitSourceInput> {
		const [dataJson, songidJson] = await Promise.all([
			this.fetchJson(DEFAULT_DATA_JSON_URL),
			this.fetchJson(DEFAULT_SONGID_JSON_URL),
		]);
		return {
			dataJson,
			songidJson,
		};
	}

	private async fetchJson(url: string): Promise<unknown | undefined> {
		try {
			const response = await fetch(url, { method: "GET" });
			if (!response.ok) {
				return undefined;
			}
			const contentType = response.headers.get("content-type") ?? "";
			const raw = await response.text();
			const looksJson =
				contentType.toLowerCase().includes("json") || raw.trimStart().startsWith("{") || raw.trimStart().startsWith("[");
			if (!looksJson) {
				return undefined;
			}
			return JSON.parse(raw) as unknown;
		} catch {
			return undefined;
		}
	}

	private buildSongIdMapping(dataJson: unknown, songidJson: unknown) {
		const byTitleAndType = new Map<string, number>();
		const byTitle = new Map<string, number[]>();

		const dataRecord = toRecord(dataJson);
		const songsRaw = Array.isArray(dataRecord?.songs) ? dataRecord.songs : [];
		for (const song of songsRaw) {
			const row = toRecord(song);
			if (!row) {
				continue;
			}

			const songIdRaw = toFiniteNumber(row.songId);
			const titleRaw = typeof row.title === "string" ? row.title : "";
			if (songIdRaw === null || !titleRaw.trim()) {
				continue;
			}
			const songId = Math.trunc(songIdRaw);

			const normalizedTitle = normalizeTitle(titleRaw);
			const sheets = Array.isArray(row.sheets) ? row.sheets : [];
			for (const sheet of sheets) {
				const sheetRecord = toRecord(sheet);
				const sheetTypeRaw = typeof sheetRecord?.type === "string" ? sheetRecord.type : "";
				const normalizedType = normalizeType(sheetTypeRaw);
				if (!normalizedType) {
					continue;
				}
				byTitleAndType.set(`${normalizedTitle}|${normalizedType}`, songId);
			}
		}

		const songIdRows = Array.isArray(songidJson) ? songidJson : [];
		for (const item of songIdRows) {
			const row = toRecord(item);
			if (!row) {
				continue;
			}
			const idRaw = toFiniteNumber(row.id);
			const nameRaw = typeof row.name === "string" ? row.name : "";
			if (idRaw === null || !nameRaw.trim()) {
				continue;
			}

			const normalizedTitle = normalizeTitle(nameRaw);
			const existing = byTitle.get(normalizedTitle) ?? [];
			const normalizedId = Math.trunc(idRaw);
			if (!existing.includes(normalizedId)) {
				existing.push(normalizedId);
				existing.sort((left, right) => left - right);
				byTitle.set(normalizedTitle, existing);
			}
		}

		return {
			byTitleAndType,
			byTitle,
		};
	}

	private resolveSongIdFromMapping(
		mapping: ReturnType<ChartFitService["buildSongIdMapping"]>,
		input: { title: string; chartType: string; songIdentifier: string },
	) {
		const normalizedTitle = normalizeTitle(input.title);
		if (!normalizedTitle) {
			return null;
		}

		const normalizedType = normalizeType(input.chartType);
		const byTypeHit = mapping.byTitleAndType.get(`${normalizedTitle}|${normalizedType}`);
		if (byTypeHit && byTypeHit > 0) {
			return byTypeHit;
		}

		const candidates = mapping.byTitle.get(normalizedTitle) ?? [];
		if (candidates.length === 0) {
			return null;
		}

		const localSongId = Number(input.songIdentifier);
		if (Number.isFinite(localSongId)) {
			const localSongIdInt = Math.trunc(localSongId);
			if (candidates.includes(localSongIdInt)) {
				return localSongIdInt;
			}
			if (localSongIdInt < 10000 && candidates.includes(localSongIdInt + 10000)) {
				return localSongIdInt + 10000;
			}
			if (localSongIdInt > 10000 && candidates.includes(localSongIdInt % 10000)) {
				return localSongIdInt % 10000;
			}
		}

		const preferred =
			normalizedType === "dx" ? candidates.find((id) => id >= 10000) : candidates.find((id) => id > 0 && id < 10000);

		return preferred ?? candidates[0] ?? null;
	}
}
