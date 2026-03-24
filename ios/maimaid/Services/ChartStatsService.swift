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
    
    var formattedAvg: String {
        guard let avg = avg else { return "N/A" }
        return String(format: "%.4f%%", avg)
    }
    
    var formattedFitDiff: String {
        guard let fit_diff = fit_diff else { return "N/A" }
        return String(format: "%.2f", fit_diff)
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

nonisolated struct ChartStatsResponse: Codable, Sendable {
    let charts: [String: [ChartStat]]
}

@Observable
@MainActor
class ChartStatsService {
    static let shared = ChartStatsService()
    
    private init() {
        loadCachedStats()
    }
    
    private var cache: [String: [ChartStat]] = [:]
    private var fetchTask: Task<Void, Error>?
    private var lastFetchDate: Date? = nil
    
    func fetchStats(forceRefresh: Bool = false) async {
        ensureCacheLoaded()

        // Only fetch once per session or if cache is empty
        if !forceRefresh,
           !cache.isEmpty,
           let lastFetchDate,
           Date().timeIntervalSince(lastFetchDate) < 3600 {
            return
        }
        
        if let existingTask = fetchTask {
            _ = try? await existingTask.value
            return
        }
        
        let task = Task {
            guard let url = URL(string: "https://www.diving-fish.com/api/maimaidxprober/chart_stats") else { return }
            let response = try await Task.detached(priority: .utility) {
                let (data, _) = try await URLSession.shared.data(from: url)
                return try JSONDecoder().decode(ChartStatsResponse.self, from: data)
            }.value

            self.cache = response.charts
            self.lastFetchDate = Date()
            self.saveCachedStats()
        }
        
        self.fetchTask = task
        
        do {
            _ = try await task.value
        } catch {
            print("Failed to fetch chart stats: \(error)")
        }
        
        self.fetchTask = nil
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

        cache = response.charts
    }

    private func saveCachedStats() {
        guard let data = try? JSONEncoder().encode(ChartStatsResponse(charts: cache)) else {
            return
        }

        UserDefaults.app.maimaiChartStatsData = data
    }
}
