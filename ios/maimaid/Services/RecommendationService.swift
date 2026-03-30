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

    private let chartStatsService = ChartStatsService.shared

    private init() {}

    /// Generates recommendations considering B15/B35 thresholds and potential gain
    func getRecommendations(songs: [Song], configs: [SyncConfig], activeProfile: UserProfile? = nil, modelContext: ModelContext) async -> RecommendationResponse {
        print("RecommendationService: Generating recommendations for \(songs.count) songs...")

        await chartStatsService.fetchStats()

        let profile = activeProfile ?? ServerVersionService.shared.activeProfile(context: modelContext)

        let serverContext = profile.flatMap { GameServer(rawValue: $0.server) }
        let latestVersion: String
        if let server = serverContext {
            latestVersion = ServerVersionService.shared.latestVersion(for: server, songs: songs)
        } else {
            latestVersion = ThemeUtils.latestVersion
        }

        let b35Limit = profile?.b35Count ?? configs.first?.b35Count ?? 35
        let b15Limit = profile?.b15Count ?? configs.first?.b15Count ?? 15
        let b15RecLimit = profile?.b15RecLimit ?? configs.first?.b15RecLimit ?? 10
        let b35RecLimit = profile?.b35RecLimit ?? configs.first?.b35RecLimit ?? 10

        // 🔴 修复：使用 ScoreService 获取成绩（确保用户隔离）
        let scoreMap = ScoreService.shared.scoreMap(context: modelContext)
        let profileId = profile?.id

        let input = await songs.toCalculationInput(userProfileId: profileId, server: serverContext, preloadedScores: scoreMap)

        let b50 = await RatingUtils.calculateB50(input: input, b35Count: b35Limit, b15Count: b15Limit, latestVersion: latestVersion)
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

            for sheet in song.sheets {
                if sheet.type.lowercased().contains("utage") { continue }

                let internalLevelValue = sheet.internalLevelValue ?? sheet.levelValue ?? 0.0
                guard internalLevelValue > 0 else { continue }

                // 🔴 修复：使用 ScoreService 获取当前用户的成绩
                let currentScore = ScoreService.shared.score(for: sheet, context: modelContext)
                let currentRate = currentScore?.rate ?? 0.0
                guard currentRate < 100.5 else { continue }

                // Determine region/version at the chart level so newly added charts on old songs
                // can still enter B15 when the chart itself belongs to the latest version.
                let isRegionActive: Bool
                if let targetServer = serverContext {
                    switch targetServer {
                    case .jp: isRegionActive = sheet.regionJp
                    case .intl: isRegionActive = sheet.regionIntl
                    case .cn: isRegionActive = sheet.regionCn
                    }
                } else {
                    isRegionActive = false
                }

                let category = RatingUtils.determineSongCategory(
                    songVersion: sheet.version ?? song.version,
                    latestServerVersion: latestVersion,
                    isRegionActive: isRegionActive
                )
                if category == .excluded { continue }

                let isNew = (category == .b15)
                let threshold = isNew ? b15Threshold : b35Threshold

                let currentRating = currentScore.map { RatingUtils.calculateRating(internalLevel: internalLevelValue, achievement: $0.rate, fc: $0.fc) } ?? 0
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
                    let matchingStat = chartStatsService.getStat(for: sheet)
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
                        currentRate: currentScore?.rate,
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
    }
}
