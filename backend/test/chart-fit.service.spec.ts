import "reflect-metadata";
import { describe, expect, it } from "vitest";
import {
	chartFitAchievementCurve,
	chartFitGetDiff,
	chartFitPercentCurve,
	mergeChartStatsPayloads,
	normalizeChartStatsPayload,
} from "../src/services/chart-fit.service.js";

const makeChart = (
	overrides?: Partial<{
		cnt: number;
		diff: string;
		fit_diff: number;
		avg: number;
		avg_dx: number;
		std_dev: number;
		dist: number[];
		fc_dist: number[];
	}>,
) => ({
	cnt: overrides?.cnt ?? 1000,
	diff: overrides?.diff ?? "13+",
	fit_diff: overrides?.fit_diff ?? 13.7,
	avg: overrides?.avg ?? 98.4,
	avg_dx: overrides?.avg_dx ?? 11111,
	std_dev: overrides?.std_dev ?? 1.2,
	dist: overrides?.dist ?? [1, 2, 3, 4, 5, 6, 7, 8, 20, 20, 30, 40, 300, 554],
	fc_dist: overrides?.fc_dist ?? [100, 200, 300, 150, 250],
});

describe("chart fit curve helpers", () => {
	it("matches DF piecewise achievement curve", () => {
		expect(chartFitAchievementCurve(-5)).toBe(-0.5);
		expect(chartFitAchievementCurve(-2)).toBeCloseTo(-0.3, 6);
		expect(chartFitAchievementCurve(0.5)).toBeCloseTo(0.1, 6);
		expect(chartFitAchievementCurve(2)).toBeCloseTo(0.3, 6);
		expect(chartFitAchievementCurve(9)).toBe(0.5);
	});

	it("matches DF piecewise percent curve", () => {
		expect(chartFitPercentCurve(-1)).toBeCloseTo(-0.5, 6);
		expect(chartFitPercentCurve(-0.4)).toBeCloseTo(-0.3, 6);
		expect(chartFitPercentCurve(0.2)).toBeCloseTo(0.2, 6);
		expect(chartFitPercentCurve(0.6)).toBeCloseTo(0.45, 6);
		expect(chartFitPercentCurve(2)).toBeCloseTo(0.82, 6);
	});

	it("supports ? difficulty suffix exactly like DF", () => {
		expect(chartFitGetDiff("13+?", 0, 0, 0, 0)).toBeCloseTo(13.75, 6);
		expect(chartFitGetDiff("14?", 0, 0, 0, 0)).toBeCloseTo(14.25, 6);
	});
});

describe("chart fit payload normalization and merge", () => {
	it("treats empty chart objects as placeholders", () => {
		const normalized = normalizeChartStatsPayload({
			charts: {
				"42": [{}, makeChart()],
			},
			diff_data: {},
		});

		expect(normalized.charts["42"]?.[0]).toBeNull();
		expect(normalized.charts["42"]?.[1]?.cnt).toBe(1000);
	});

	it("applies secondary cnt threshold before weighted merge", () => {
		const primary = {
			charts: {
				"100": [makeChart({ cnt: 2000, fit_diff: 13.5, avg: 98.0, dist: [0, 0, 0, 0, 0, 0, 0, 0, 20, 30, 40, 50, 800, 1060] })],
			},
			diff_data: {},
		};

		const secondaryUnderThreshold = {
			charts: {
				"100": [makeChart({ cnt: 999, fit_diff: 14.8, avg: 99.7 })],
			},
			diff_data: {},
		};

		const ignored = mergeChartStatsPayloads(primary, secondaryUnderThreshold, { secondaryMinCnt: 1000 });
		const ignoredEntry = ignored.charts["100"]?.[0] as Record<string, unknown>;
		expect(ignoredEntry.cnt).toBe(2000);
		expect(ignoredEntry.fit_diff).toBe(13.5);

		const secondaryUsed = {
			charts: {
				"100": [makeChart({ cnt: 1000, fit_diff: 14.5, avg: 99.0, dist: [0, 0, 0, 0, 0, 0, 0, 0, 10, 10, 20, 30, 300, 630] })],
			},
			diff_data: {},
		};

		const merged = mergeChartStatsPayloads(primary, secondaryUsed, { secondaryMinCnt: 1000 });
		const mergedEntry = merged.charts["100"]?.[0] as Record<string, unknown>;
		expect(mergedEntry.cnt).toBe(3000);
		expect(Number(mergedEntry.fit_diff)).toBeCloseTo((13.5 * 2000 + 14.5 * 1000) / 3000, 6);

		const diffData = merged.diff_data["13+"];
		expect(diffData).toBeDefined();
		expect(diffData.dist.length).toBe(14);
		expect(diffData.fc_dist.length).toBe(5);
	});
});
