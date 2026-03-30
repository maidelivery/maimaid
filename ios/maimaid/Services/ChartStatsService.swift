import Foundation
import SwiftUI

nonisolated struct ChartStat: Codable, Sendable {
    let cnt: Double?
    let diff: String?
    let fit_diff: Double?
    let avg: Double?
    let avg_dx: Double?
    let std_dev: Double?
    let dist: [Int]?
    let fc_dist: [Int]?

    var isPlaceholder: Bool {
        cnt == nil
            && diff == nil
            && fit_diff == nil
            && avg == nil
            && avg_dx == nil
            && std_dev == nil
            && dist == nil
            && fc_dist == nil
    }
    
    var formattedAvg: String {
        guard let avg = avg else { return "N/A" }
        return "\(avg.formatted(.number.precision(.fractionLength(4))))%"
    }
    
    var formattedFitDiff: String {
        guard let fit_diff = fit_diff else { return "N/A" }
        return fit_diff.formatted(.number.precision(.fractionLength(2)))
    }

    // Skip decoding if fields are missing (handles empty {} in API)
    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        cnt = try? container.decode(Double.self, forKey: .cnt)
        diff = try? container.decode(String.self, forKey: .diff)
        fit_diff = try? container.decode(Double.self, forKey: .fit_diff)
        avg = try? container.decode(Double.self, forKey: .avg)
        avg_dx = try? container.decode(Double.self, forKey: .avg_dx)
        std_dev = try? container.decode(Double.self, forKey: .std_dev)
        dist = try? container.decode([Int].self, forKey: .dist)
        fc_dist = try? container.decode([Int].self, forKey: .fc_dist)
    }

    private enum CodingKeys: String, CodingKey {
        case cnt, diff, fit_diff, avg, avg_dx, std_dev, dist, fc_dist
    }
}

nonisolated struct ChartStatsDiffData: Codable, Sendable {
    let achievements: Double?
    let dist: [Double]?
    let fc_dist: [Double]?
}

nonisolated struct ChartStatsResponse: Codable, Sendable {
    let charts: [String: [ChartStat]]
    let diff_data: [String: ChartStatsDiffData]?

    init(charts: [String: [ChartStat]], diff_data: [String: ChartStatsDiffData]? = nil) {
        self.charts = charts
        self.diff_data = diff_data
    }
}

@Observable
@MainActor
class ChartStatsService {
    static let shared = ChartStatsService()
    
    private init() {
        loadCachedStats()
    }
    
    private var cache: [String: [ChartStat]] = [:]
    private var lastFetchDate: Date? = nil
    
    func fetchStats(forceRefresh: Bool = false) async {
        ensureCacheLoaded()
        if forceRefresh && !cache.isEmpty {
            saveCachedStats()
        }
    }

    func replaceStats(with response: ChartStatsResponse) {
        replaceStats(charts: response.charts)
    }

    func replaceStats(charts: [String: [ChartStat]]) {
        cache = Self.normalizeCharts(charts)
        lastFetchDate = Date.now
        saveCachedStats()
    }

    func allStats() -> [String: [ChartStat]] {
        ensureCacheLoaded()
        return cache
    }
    
    func getStats(for songId: Int) -> [ChartStat]? {
        ensureCacheLoaded()
        return cache["\(songId)"]
    }
    
    func getStat(for sheet: Sheet) -> ChartStat? {
        ensureCacheLoaded()

        // Diving Fish uses the level string (e.g. "14", "14+") as the 'diff' field in chart_stats
        // unless it's a specific internal ID mapping. 
        // Based on the 'charts' response seen in logs:
        // "diff": "13", "diff": "14+", etc.
        
        let diffValue = sheet.level
        let songId = sheet.songId > 0 ? sheet.songId : (sheet.song?.songId ?? 0)
        if songId == 0 { return nil }
        
        // Strategy 1: Try with DX offset if song is DX
        if sheet.type.lowercased() == "dx" {
            let dxId = songId + 10000
            if let songStats = cache["\(dxId)"],
               let stat = songStats.first(where: { $0.diff == diffValue }) {
                return stat
            }
        }
        
        // Strategy 2: Try with base ID
        if let songStats = cache["\(songId)"],
           let stat = songStats.first(where: { $0.diff == diffValue }) {
            return stat
        }
        
        return nil
    }

    private func ensureCacheLoaded() {
        if cache.isEmpty {
            loadCachedStats()
        }
    }

    private func loadCachedStats() {
        guard cache.isEmpty,
              let data = UserDefaults.app.maimaiChartStatsData,
              let response = try? JSONDecoder().decode(ChartStatsResponse.self, from: data) else {
            return
        }

        cache = Self.normalizeCharts(response.charts)
        lastFetchDate = Date.now
    }

    private func saveCachedStats() {
        guard let data = try? JSONEncoder().encode(ChartStatsResponse(charts: cache, diff_data: nil)) else {
            return
        }

        UserDefaults.app.maimaiChartStatsData = data
    }

    private static func normalizeCharts(_ charts: [String: [ChartStat]]) -> [String: [ChartStat]] {
        var normalized: [String: [ChartStat]] = [:]
        for (songId, stats) in charts {
            let filtered = stats.filter { !$0.isPlaceholder }
            if !filtered.isEmpty {
                normalized[songId] = filtered
            }
        }
        return normalized
    }
}
