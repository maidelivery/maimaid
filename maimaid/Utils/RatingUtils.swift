import SwiftUI
import SwiftData

// MARK: - RatingUtils Main Type

enum RatingUtils {
    // MARK: - RatingEntry
    
    struct RatingEntry: Identifiable, Sendable {
        let id: UUID
        let songId: Int
        let songIdentifier: String
        let songTitle: String
        let imageName: String?
        let achievement: Double
        let rating: Int
        let level: Double
        let diff: String
        let type: String
        let dxScore: Int
        let maxDxScore: Int
        let fc: String?
        let fs: String?
        
        init(
            id: UUID = UUID(),
            songId: Int,
            songIdentifier: String,
            songTitle: String,
            imageName: String?,
            achievement: Double,
            rating: Int,
            level: Double,
            diff: String,
            type: String,
            dxScore: Int = 0,
            maxDxScore: Int = 0,
            fc: String? = nil,
            fs: String? = nil
        ) {
            self.id = id
            self.songId = songId
            self.songIdentifier = songIdentifier
            self.songTitle = songTitle
            self.imageName = imageName
            self.achievement = achievement
            self.rating = rating
            self.level = level
            self.diff = diff
            self.type = type
            self.dxScore = dxScore
            self.maxDxScore = maxDxScore
            self.fc = fc
            self.fs = fs
        }
    }
    
    // MARK: - Rank Thresholds
    
    struct RankThreshold: Identifiable {
        let id = UUID()
        let rank: String
        let threshold: Double
    }
    
    static let rankThresholds: [RankThreshold] = [
        RankThreshold(rank: "D", threshold: 0.0),
        RankThreshold(rank: "C", threshold: 50.0),
        RankThreshold(rank: "B", threshold: 60.0),
        RankThreshold(rank: "BB", threshold: 70.0),
        RankThreshold(rank: "BBB", threshold: 75.0),
        RankThreshold(rank: "A", threshold: 80.0),
        RankThreshold(rank: "AA", threshold: 90.0),
        RankThreshold(rank: "AAA", threshold: 94.0),
        RankThreshold(rank: "S", threshold: 97.0),
        RankThreshold(rank: "S+", threshold: 98.0),
        RankThreshold(rank: "SS", threshold: 99.0),
        RankThreshold(rank: "SS+", threshold: 99.5),
        RankThreshold(rank: "SSS", threshold: 100.0),
        RankThreshold(rank: "SSS+", threshold: 100.5),
        RankThreshold(rank: "AP+", threshold: 100.5)
    ]
    
    // MARK: - Song Category
    
    enum SongCategory: Sendable {
        case b15  // New songs (current version)
        case b35  // Old songs
        case excluded
    }
    
    // MARK: - Rank Calculation
    
    static func calculateRank(achievement: Double) -> String {
        if achievement >= 100.5 { return "SSS+" }
        if achievement >= 100.0 { return "SSS" }
        if achievement >= 99.5 { return "SS+" }
        if achievement >= 99.0 { return "SS" }
        if achievement >= 98.0 { return "S+" }
        if achievement >= 97.0 { return "S" }
        if achievement >= 94.0 { return "AAA" }
        if achievement >= 90.0 { return "AA" }
        if achievement >= 80.0 { return "A" }
        if achievement >= 75.0 { return "BBB" }
        if achievement >= 70.0 { return "BB" }
        if achievement >= 60.0 { return "B" }
        if achievement >= 50.0 { return "C" }
        return "D"
    }
    
    // MARK: - Rating Calculation
    
    static func calculateRating(internalLevel: Double, achievement: Double, fc: String? = nil) -> Int {
        guard internalLevel > 0, achievement > 0 else { return 0 }
        
        let baseRating = internalLevel * achievement * 0.01
        
        // Apply modifiers based on achievement
        let modifier: Double
        switch achievement {
        case 100.5...:
            modifier = 0.225
        case 100.0..<100.5:
            modifier = 0.215
        case 99.5..<100.0:
            modifier = 0.14
        case 99.0..<99.5:
            modifier = 0.1275
        case 98.0..<99.0:
            modifier = 0.115
        case 97.0..<98.0:
            modifier = 0.105
        case 94.0..<97.0:
            modifier = 0.10
        case 90.0..<94.0:
            modifier = 0.095
        case 80.0..<90.0:
            modifier = 0.09
        case 75.0..<80.0:
            modifier = 0.085
        case 70.0..<75.0:
            modifier = 0.08
        case 60.0..<70.0:
            modifier = 0.075
        case 50.0..<60.0:
            modifier = 0.07
        default:
            modifier = 0.06
        }
        
        let rating = baseRating * modifier
        
        // FC bonus
        var fcBonus: Double = 0
        if let fc = fc?.lowercased() {
            if fc.contains("app") { fcBonus = 0.10 }
            else if fc.contains("ap") { fcBonus = 0.08 }
            else if fc.contains("fcp") { fcBonus = 0.04 }
            else if fc.contains("fc") { fcBonus = 0.02 }
        }
        
        return Int(floor((rating + fcBonus) * 100))
    }
    
    static func calculateRating(internalLevel: Double, achievements: Double) -> Int {
        return calculateRating(internalLevel: internalLevel, achievement: achievements, fc: nil)
    }
    
    // MARK: - Rank Colors
    
    static func colorForRank(_ rank: String) -> Color {
        switch rank {
        case "SSS+", "SSS":
            return Color(red: 1.0, green: 0.85, blue: 0.0) // Gold
        case "SS+", "SS":
            return Color(red: 1.0, green: 0.75, blue: 0.0)
        case "S+", "S":
            return Color(red: 1.0, green: 0.6, blue: 0.0) // Orange
        case "AAA":
            return Color(red: 0.8, green: 0.6, blue: 1.0) // Purple
        case "AA":
            return Color(red: 0.6, green: 0.8, blue: 1.0) // Light Blue
        case "A":
            return Color(red: 0.5, green: 0.9, blue: 0.5) // Green
        default:
            return .secondary
        }
    }
    
    // MARK: - Song Category Determination
    
    static func determineSongCategory(
        songVersion: String?,
        latestServerVersion: String?,
        isRegionActive: Bool
    ) -> SongCategory {
        guard isRegionActive else { return .excluded }
        
        guard let latest = latestServerVersion, let songVer = songVersion else {
            // If version info is unavailable, default to B35 (old)
            return .b35
        }
        
        // Compare versions - if song version matches latest server version, it's "new"
        let versionSequence = UserDefaults.standard.stringArray(forKey: "MaimaiVersionSequence") ?? []
        
        guard let songIndex = versionSequence.firstIndex(where: { songVer.contains($0) || $0.contains(songVer) }),
              let latestIndex = versionSequence.firstIndex(where: { latest.contains($0) || $0.contains(latest) }) else {
            return .b35
        }
        
        // If song is from the latest version, it's a "new" song (B15)
        if songIndex >= latestIndex {
            return .b15
        }
        
        return .b35
    }
    
    // MARK: - B50 Calculation
    
    struct CalculationInput: Sendable {
        let songs: [SongCalculationData]
        let userProfileId: UUID?
        let server: GameServer?
        let scoreMap: [String: ScoreCalculationData]
    }
    
    struct SongCalculationData: Sendable {
        let songId: Int
        let songIdentifier: String
        let title: String
        let artist: String
        let imageName: String
        let version: String?
        let category: String
        let isLocked: Bool
        let sheets: [SheetCalculationData]
    }
    
    struct SheetCalculationData: Sendable {
        let songIdentifier: String
        let type: String
        let difficulty: String
        let internalLevelValue: Double?
        let regionJp: Bool
        let regionIntl: Bool
        let regionCn: Bool
    }
    
    struct ScoreCalculationData: Sendable {
        let sheetId: String
        let rate: Double
        let rank: String
        let dxScore: Int
        let fc: String?
        let fs: String?
    }
    
    static func calculateB50(
        input: CalculationInput,
        b35Count: Int,
        b15Count: Int,
        latestVersion: String?
    ) async -> (total: Int, b35: [RatingEntry], b15: [RatingEntry]) {
        var allEntries: [(entry: RatingEntry, isNew: Bool)] = []
        
        for songData in input.songs {
            // Skip utage category
            if songData.category.lowercased().contains("utage") || songData.category.contains("宴") {
                continue
            }
            
            let isRegionActive: Bool
            if let server = input.server {
                isRegionActive = songData.sheets.contains { sheet in
                    switch server {
                    case .jp: return sheet.regionJp
                    case .intl: return sheet.regionIntl
                    case .cn: return sheet.regionCn
                    }
                }
            } else {
                isRegionActive = false
            }
            
            let category = determineSongCategory(
                songVersion: songData.version,
                latestServerVersion: latestVersion,
                isRegionActive: isRegionActive
            )
            
            guard category != .excluded else { continue }
            
            for sheetData in songData.sheets {
                // Skip utage sheets
                if sheetData.type.lowercased().contains("utage") { continue }
                
                let sheetId = "\(sheetData.songIdentifier)_\(sheetData.type)_\(sheetData.difficulty)"
                guard let scoreData = input.scoreMap[sheetId] else { continue }
                
                let internalLevel = sheetData.internalLevelValue ?? 0
                guard internalLevel > 0 else { continue }
                
                let rating = calculateRating(
                    internalLevel: internalLevel,
                    achievement: scoreData.rate,
                    fc: scoreData.fc
                )
                
                guard rating > 0 else { continue }
                
                let entry = RatingEntry(
                    songId: songData.songId,
                    songIdentifier: songData.songIdentifier,
                    songTitle: songData.title,
                    imageName: songData.imageName,
                    achievement: scoreData.rate,
                    rating: rating,
                    level: internalLevel,
                    diff: sheetData.difficulty,
                    type: sheetData.type,
                    dxScore: scoreData.dxScore,
                    fc: scoreData.fc,
                    fs: scoreData.fs
                )
                
                allEntries.append((entry: entry, isNew: category == .b15))
            }
        }
        
        let b15Entries = allEntries
            .filter { $0.isNew }
            .sorted { $0.entry.rating > $1.entry.rating }
            .prefix(b15Count)
            .map { $0.entry }
        
        let b35Entries = allEntries
            .filter { !$0.isNew }
            .sorted { $0.entry.rating > $1.entry.rating }
            .prefix(b35Count)
            .map { $0.entry }
        
        let total = b15Entries.reduce(0) { $0 + $1.rating } + b35Entries.reduce(0) { $0 + $1.rating }
        
        return (total: total, b35: Array(b35Entries), b15: Array(b15Entries))
    }
}

// MARK: - Song Array Extension for Calculation Input

extension Array where Element == Song {
    func toCalculationInput(
        userProfileId: UUID?,
        server: GameServer?,
        preloadedScores: [String: Score]
    ) -> RatingUtils.CalculationInput {
        let songsData = self.map { song in
            RatingUtils.SongCalculationData(
                songId: song.songId,
                songIdentifier: song.songIdentifier,
                title: song.title,
                artist: song.artist,
                imageName: song.imageName,
                version: song.version,
                category: song.category,
                isLocked: song.isLocked,
                sheets: song.sheets.map { sheet in
                    RatingUtils.SheetCalculationData(
                        songIdentifier: sheet.songIdentifier,
                        type: sheet.type,
                        difficulty: sheet.difficulty,
                        internalLevelValue: sheet.internalLevelValue ?? sheet.levelValue,
                        regionJp: sheet.regionJp,
                        regionIntl: sheet.regionIntl,
                        regionCn: sheet.regionCn
                    )
                }
            )
        }
        
        let scoresData = preloadedScores.mapValues { score in
            RatingUtils.ScoreCalculationData(
                sheetId: score.sheetId,
                rate: score.rate,
                rank: score.rank,
                dxScore: score.dxScore,
                fc: score.fc,
                fs: score.fs
            )
        }
        
        return RatingUtils.CalculationInput(
            songs: songsData,
            userProfileId: userProfileId,
            server: server,
            scoreMap: scoresData
        )
    }
}

// MARK: - RatingUtils Extension for Score Map

extension RatingUtils {
    /// 🔴 推荐使用：通过 ScoreService 获取成绩映射
    /// 确保成绩获取严格在当前用户作用域下
    static func fetchScoreMap(context: ModelContext) -> [String: Score] {
        // 使用 ModelContext 作为参数，而不是传入 profileId
        // 这样 ScoreService 内部会自动获取当前活跃用户
        return ScoreService.shared.scoreMap(context: context)
    }
    
    // 保留旧方法用于迁移兼容，但标记为废弃
    @available(*, deprecated, message: "Use fetchScoreMap(context:) instead for proper user isolation")
    static func fetchScoreMap(profileId: UUID?, context: ModelContext) -> [String: Score] {
        var scores: [Score] = []
        if let uid = profileId {
            let desc = FetchDescriptor<Score>(predicate: #Predicate { $0.userProfileId == uid })
            scores = (try? context.fetch(desc)) ?? []
        } else {
            let fallbackDesc = FetchDescriptor<Score>()
            let allScores = (try? context.fetch(fallbackDesc)) ?? []
            scores = allScores.filter { $0.userProfileId == nil }
        }
        
        var map: [String: Score] = [:]
        for score in scores {
            map[score.sheetId] = score
        }
        return map
    }
}
