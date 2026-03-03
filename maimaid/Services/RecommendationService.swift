import Foundation
import SwiftData

struct RecommendationResult: Identifiable, Sendable {
    let id = UUID()
    let song: Song
    let sheet: Sheet
    let fitDiff: Double?
    let diffGap: Double? // internalLevel - fitDiff
    let currentRate: Double?
    let potentialRating: Int
    let potentialGain: Int
    let targetRank: String
    let targetAchievement: Double
    let isNew: Bool
    var comprehensiveScore: Double = 0 // Used for internal sorting
}

struct RecommendationResponse: Sendable {
    let b15: [RecommendationResult]
    let b35: [RecommendationResult]
}

@MainActor
class RecommendationService {
    static let shared = RecommendationService()
    
    private let statsURL = "https://www.diving-fish.com/api/maimaidxprober/chart_stats"
    private var cachedStats: [String: [ChartStat]]?
    private let cacheFile = FileManager.default.temporaryDirectory.appendingPathComponent("diving_fish_stats.json")
    
    struct ChartStat: Codable {
        let cnt: Int?
        let diff: String?
        let fit_diff: Double
        let avg: Double?
        let std_dev: Double?
    }
    
    private init() {}
    
    /// Fetches chart stats from Diving Fish API
    private func fetchStats() async throws -> [String: [ChartStat]] {
        if let cached = cachedStats { return cached }
        
        // Check local cache (valid for 24 hours)
        if let data = try? Data(contentsOf: cacheFile),
           let attr = try? FileManager.default.attributesOfItem(atPath: cacheFile.path),
           let date = attr[.modificationDate] as? Date,
           Date().timeIntervalSince(date) < 86400 {
            do {
                let decoded = try JSONDecoder().decode([String: [ChartStat]].self, from: data)
                self.cachedStats = decoded
                print("RecommendationService: Loaded \(decoded.count) songs from cache")
                return decoded
            } catch {
                print("RecommendationService: Cache decode failed, fetching fresh - \(error)")
            }
        }
        
        guard let url = URL(string: statsURL) else { throw URLError(.badURL) }
        
        do {
            print("RecommendationService: Fetching stats from \(statsURL)...")
            let (data, _) = try await URLSession.shared.data(from: url)
            
            // The API returns { "charts": { "song_id": [...] }, ... }
            let rawResponse = try JSONSerialization.jsonObject(with: data) as? [String: Any]
            let rawCharts = rawResponse?["charts"] as? [String: [[String: Any]]] ?? [:]
            
            var processedCharts: [String: [ChartStat]] = [:]
            
            for (songId, charts) in rawCharts {
                var validStats: [ChartStat] = []
                for chart in charts {
                    // Only process if fit_diff exists (skip empty objects)
                    if let fitDiff = chart["fit_diff"] as? Double {
                        validStats.append(ChartStat(
                            cnt: chart["cnt"] as? Int,
                            diff: (chart["diff"] as? String) ?? (chart["diff"] as? Int).map { String($0) },
                            fit_diff: fitDiff,
                            avg: chart["avg"] as? Double,
                            std_dev: chart["std_dev"] as? Double
                        ))
                    }
                }
                if !validStats.isEmpty {
                    processedCharts[songId] = validStats
                }
            }
            
            // Save the PROCESSED charts to cache, not the raw data with empty objects
            if let encoded = try? JSONEncoder().encode(processedCharts) {
                try? encoded.write(to: cacheFile)
            }
            
            self.cachedStats = processedCharts
            print("RecommendationService: Successfully processed \(processedCharts.count) songs from network")
            return processedCharts
        } catch {
            print("RecommendationService: Fetch failed - \(error)")
            try? FileManager.default.removeItem(at: cacheFile)
            throw error
        }
    }
    
    /// Generates recommendations considering B15/B35 thresholds and potential gain
    func getRecommendations(songs: [Song], configs: [SyncConfig]) async -> RecommendationResponse {
        print("RecommendationService: Generating recommendations for \(songs.count) songs...")
        do {
            let stats = try await fetchStats()
            
            let latestVersion = ThemeUtils.latestVersion
            let b35Limit = configs.first?.b35Count ?? 35
            let b15Limit = configs.first?.b15Count ?? 15
            let b15RecLimit = configs.first?.b15RecLimit ?? 10
            let b35RecLimit = configs.first?.b35RecLimit ?? 10
            
            // 1. Calculate current B50 thresholds
            let input = songs.toCalculationInput()
            
            let b50 = RatingUtils.calculateB50(input: input, b35Count: b35Limit, b15Count: b15Limit)
            let b15Threshold = b50.b15.last?.rating ?? 0
            let b35Threshold = b50.b35.last?.rating ?? 0
            
            // Calculate User's B15 Average Internal Level
            let b15Levels = b50.b15.map { $0.level }
            let avgB15InternalLevel = b15Levels.isEmpty ? 0.0 : b15Levels.reduce(0, +) / Double(b15Levels.count)
            
            var b15Recs: [RecommendationResult] = []
            var b35Recs: [RecommendationResult] = []
            
            // Milestones to check: from 97.0 (S) to 100.5 (SSS+)
            let targetMilestones: [(rank: String, achievement: Double)] = [
                ("S", 97.0), ("S+", 98.0), ("SS", 99.0), ("SS+", 99.5), ("SSS", 100.0), ("SSS+", 100.5)
            ]
            
            for song in songs {
                if song.category.lowercased().contains("utage") || song.category.contains("宴") {
                    continue
                }
                
                let songStats = stats[song.songId] ?? (song.lxnsId > 0 ? stats[String(song.lxnsId)] : nil)
                
                for sheet in song.sheets {
                    if sheet.type.lowercased().contains("utage") { continue }
                    
                    let internalLevelValue = sheet.internalLevelValue ?? sheet.levelValue ?? 0.0
                    guard internalLevelValue > 0 else { continue }
                    
                    let currentRate = sheet.score?.rate ?? 0.0
                    guard currentRate < 100.5 else { continue }
                    
                    let isNew = song.version == latestVersion
                    let threshold = isNew ? b15Threshold : b35Threshold
                    
                    let currentRating = sheet.score.map { RatingUtils.calculateRating(internalLevel: internalLevelValue, achievement: $0.rate, fc: $0.fc) } ?? 0
                    let isInB50 = (isNew ? b50.b15 : b50.b35).contains(where: { $0.songId == song.songId && $0.diff == sheet.difficulty.uppercased() && $0.type == sheet.type.uppercased() })
                    
                    // Find the MINIMUM rank that gives a gain
                    var bestTarget: (rank: String, achievement: Double)?
                    var bestPotentialRating: Int = 0
                    var bestGain: Int = 0
                    
                    for milestone in targetMilestones {
                        // Skip milestones the user has already reached (approximately)
                        if milestone.achievement <= currentRate + 0.0001 { continue }
                        
                        let potentialRating = RatingUtils.calculateRating(internalLevel: internalLevelValue, achievements: milestone.achievement)
                        let gain: Int
                        if isInB50 {
                            gain = max(0, potentialRating - currentRating)
                        } else if potentialRating > threshold {
                            gain = potentialRating - threshold
                        } else {
                            gain = 0
                        }
                        
                        if gain > 0 {
                            bestTarget = milestone
                            bestPotentialRating = potentialRating
                            bestGain = gain
                            break // Found the lowest milestone that works
                        }
                    }
                    
                    if let target = bestTarget, bestGain > 0 {
                        let matchingStat = songStats != nil ? findMatchingStat(sheet: sheet, stats: songStats!) : nil
                        let fitDiff = matchingStat?.fit_diff
                        let diffGap = fitDiff.map { internalLevelValue - $0 }
                        
                        var score: Double = 0
                        if isNew {
                            let proximity = max(0, 1.0 - abs(internalLevelValue - avgB15InternalLevel) / 2.0)
                            // Prioritize higher gain, then proximity
                            score = Double(bestGain) * 1.0 + proximity * 5.0 
                        }
                        
                        let result = RecommendationResult(
                            song: song,
                            sheet: sheet,
                            fitDiff: fitDiff,
                            diffGap: diffGap,
                            currentRate: sheet.score?.rate,
                            potentialRating: bestPotentialRating,
                            potentialGain: bestGain,
                            targetRank: target.rank,
                            targetAchievement: target.achievement,
                            isNew: isNew,
                            comprehensiveScore: score
                        )
                        
                        if isNew {
                            b15Recs.append(result)
                        } else {
                            b35Recs.append(result)
                        }
                    }
                }
            }
            
            // Sort B15: Use the comprehensive score (gain + proximity)
            let sortedB15 = b15Recs.sorted { $0.comprehensiveScore > $1.comprehensiveScore }
            
            // Sort B35: Prioritize by Gap (fit data), else potential gain
            let sortedB35 = b35Recs.sorted { r1, r2 in
                if let g1 = r1.diffGap, let g2 = r2.diffGap {
                    if abs(g1 - g2) < 0.1 { return r1.potentialGain > r2.potentialGain }
                    return g1 > g2
                }
                if r1.diffGap != nil { return true }
                if r2.diffGap != nil { return false }
                return r1.potentialGain > r2.potentialGain
            }
            
            return RecommendationResponse(
                b15: Array(sortedB15.prefix(b15RecLimit)),
                b35: Array(sortedB35.prefix(b35RecLimit))
            )
            
        } catch {
            print("RecommendationService: Error generating recommendations - \(error)")
            return RecommendationResponse(b15: [], b35: [])
        }
    }
    
    private func findMatchingStat(sheet: Sheet, stats: [ChartStat]) -> ChartStat? {
        let index: Int
        switch sheet.difficulty.lowercased() {
        case "basic": index = 0
        case "advanced": index = 1
        case "expert": index = 2
        case "master": index = 3
        case "remaster": index = 4
        default: return nil
        }
        
        if let match = stats.first(where: { $0.diff == String(index) }) {
            return match
        }
        
        let internalLevel = sheet.internalLevelValue ?? sheet.levelValue ?? 0.0
        return stats.min(by: { abs($0.fit_diff - internalLevel) < abs($1.fit_diff - internalLevel) })
    }
}
