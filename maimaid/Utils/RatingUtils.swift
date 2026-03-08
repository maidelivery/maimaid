import Foundation
import SwiftUI
import SwiftData

struct RatingUtils {
    static func calculateRating(internalLevel: Double, achievements: Double) -> Int {
        let factor: Double

        if achievements >= 100.5000 {
            factor = 22.4
        } else if achievements >= 100.0000 {
            factor = 21.6
        } else if achievements >= 99.5000 {
            factor = 21.1
        } else if achievements >= 99.0000 {
            factor = 20.8
        } else if achievements >= 98.0000 {
            factor = 20.3
        } else if achievements >= 97.0000 {
            factor = 20.0
        } else if achievements >= 94.0000 {
            factor = 16.8
        } else if achievements >= 90.0000 {
            factor = 15.2
        } else if achievements >= 80.0000 {
            factor = 13.6
        } else if achievements >= 75.0000 {
            factor = 12.0
        } else if achievements >= 70.0000 {
            factor = 11.2
        } else if achievements >= 60.0000 {
            factor = 9.6
        } else if achievements >= 50.0000 {
            factor = 8.0
        } else if achievements >= 40.0000 {
            factor = 6.4
        } else if achievements >= 30.0000 {
            factor = 4.8
        } else if achievements >= 20.0000 {
            factor = 3.2
        } else if achievements >= 10.0000 {
            factor = 1.6
        } else {
            factor = 0.0
        }
        
        // Formula: floor(internalLevel * achievementsLimit * factor / 100)
        // Note: achievements in the formula is capped at 100.5
        let cappedAchievements = min(achievements, 100.5)
        let rating = Int(floor(internalLevel * cappedAchievements * factor / 100.0))
        
        return rating
    }
    
    static func calculateRank(achievement: Double) -> String {
        if achievement >= 100.5000 { return "SSS+" }
        if achievement >= 100.0000 { return "SSS" }
        if achievement >= 99.5000 { return "SS+" }
        if achievement >= 99.0000 { return "SS" }
        if achievement >= 98.0000 { return "S+" }
        if achievement >= 97.0000 { return "S" }
        if achievement >= 94.0000 { return "AAA" }
        if achievement >= 90.0000 { return "AA" }
        if achievement >= 80.0000 { return "A" }
        if achievement >= 75.0000 { return "BBB" }
        if achievement >= 70.0000 { return "BB" }
        if achievement >= 60.0000 { return "B" }
        if achievement >= 50.0000 { return "C" }
        return "D"
    }
    
    // MARK: - Rank Metadata
    
    struct RankThreshold: Identifiable, Sendable {
        let id = UUID()
        let rank: String
        let threshold: Double
    }
    
    static let rankThresholds: [RankThreshold] = [
        RankThreshold(rank: "AP+", threshold: 101.0),
        RankThreshold(rank: "SSS+", threshold: 100.5),
        RankThreshold(rank: "SSS", threshold: 100.0),
        RankThreshold(rank: "SS+", threshold: 99.5),
        RankThreshold(rank: "SS", threshold: 99.0),
        RankThreshold(rank: "S+", threshold: 98.0),
        RankThreshold(rank: "S", threshold: 97.0),
        RankThreshold(rank: "AAA", threshold: 94.0),
        RankThreshold(rank: "AA", threshold: 90.0),
        RankThreshold(rank: "A", threshold: 80.0),
        RankThreshold(rank: "BBB", threshold: 75.0),
        RankThreshold(rank: "BB", threshold: 70.0),
        RankThreshold(rank: "B", threshold: 60.0),
        RankThreshold(rank: "C", threshold: 50.0)
    ]
    
    static func colorForRank(_ rank: String) -> Color {
        switch rank {
        case "AP+":   return Color(red: 1.0, green: 0.84, blue: 0.0) // Brighter Gold (#FFD700)
        case "SSS+": return Color(red: 1.0, green: 0.7, blue: 0.0)
        case "SSS":  return Color(red: 1.0, green: 0.8, blue: 0.2)
        case "SS+":  return Color(red: 0.95, green: 0.75, blue: 0.1)
        case "SS":   return Color(red: 0.9, green: 0.7, blue: 0.0)
        case "S+":   return Color(red: 0.8, green: 0.6, blue: 0.0)
        case "S":    return Color(red: 0.7, green: 0.55, blue: 0.0)
        case "AAA":  return Color(red: 0.9, green: 0.3, blue: 0.3)
        case "AA":   return Color(red: 0.8, green: 0.3, blue: 0.3)
        case "A":    return Color(red: 0.7, green: 0.3, blue: 0.3)
        default:     return .secondary
        }
    }
    
    static func calculateRating(internalLevel: Double, achievement: Double, fc: String?) -> Int {
        var rating = calculateRating(internalLevel: internalLevel, achievements: achievement)
        
        // AP Bonus check based on FC status
        if let fc = fc?.lowercased(), fc.contains("ap") {
            rating += 1
        }
        
        return rating
    }
    
    struct RatingCalculationInput: Sendable {
        let songIdentifier: String // Internal ID
        let songId: Int // Numeric ID
        let title: String
        let version: String?
        let releaseDate: String?
        let imageName: String?
        let isRegionActive: Bool // Added to flag if region is explicitly true
        let sheets: [SheetCalculationInput]
    }
    
    struct SheetCalculationInput: Sendable {
        let difficulty: String
        let type: String
        let internalLevel: Double?
        let level: Double?
        let rate: Double
        let fc: String?
        let fs: String? // Added FS status
        let dxScore: Int // Added DX score
        let maxDxScore: Int // Added for stars calculation
        let songId: Int // Sheet-specific numeric ID
    }
    
    struct RatingEntry: Identifiable, Sendable {
        let id = UUID()
        let songIdentifier: String // Internal ID
        let songId: Int // Numeric ID
        let songTitle: String
        let imageName: String?
        let diff: String
        let type: String
        let achievement: Double
        let level: Double
        let rating: Int
        let isNew: Bool
        let isRegionActive: Bool // Added to flag if region is explicitly true
        let fc: String? // Added for UI
        let fs: String? // Added for UI
        let dxScore: Int // Added for UI
        let maxDxScore: Int // Added for stars
    }
    
    enum SongB50Category {
        case b15
        case b35
        case excluded
    }
    
    static func determineSongCategory(songVersion: String?, latestServerVersion: String, isRegionActive: Bool) -> SongB50Category {
        let sequence = UserDefaults.standard.stringArray(forKey: "MaimaiVersionSequence") ?? []
        let latestVerIndex = sequence.firstIndex(of: latestServerVersion) ?? sequence.count
        
        let songVersionStr = songVersion ?? ""
        let songVerIndex = sequence.firstIndex(of: songVersionStr) ?? 0
        
        if songVerIndex >= latestVerIndex {
            // Current or future versions: must have region explicitly active to be in B15
            return isRegionActive ? .b15 : .excluded
        } else {
            // Older versions default to B35.
            return .b35
        }
    }
    
    static func calculateB50(input: [RatingCalculationInput], b35Count: Int = 35, b15Count: Int = 15, latestVersion: String? = nil) -> (total: Int, b35: [RatingEntry], b15: [RatingEntry]) {
        let version = latestVersion ?? ThemeUtils.latestVersion
        
        var newEntries: [RatingEntry] = []
        var oldEntries: [RatingEntry] = []
        
        for song in input {
            let category = determineSongCategory(songVersion: song.version, latestServerVersion: version, isRegionActive: song.isRegionActive)
            if category == .excluded {
                continue
            }
            let isNew = (category == .b15)
            
            for sheet in song.sheets {
                let level = sheet.internalLevel ?? sheet.level ?? 0.0
                let rating = calculateRating(internalLevel: level, achievement: sheet.rate, fc: sheet.fc)
                
                let entry = RatingEntry(
                    songIdentifier: song.songIdentifier,
                    songId: sheet.songId > 0 ? sheet.songId : song.songId,
                    songTitle: song.title,
                    imageName: song.imageName,
                    diff: sheet.difficulty.uppercased(),
                    type: sheet.type.uppercased(),
                    achievement: sheet.rate,
                    level: level,
                    rating: rating,
                    isNew: isNew,
                    isRegionActive: song.isRegionActive,
                    fc: sheet.fc,
                    fs: sheet.fs,
                    dxScore: sheet.dxScore,
                    maxDxScore: sheet.maxDxScore
                )
                
                if isNew {
                    newEntries.append(entry)
                } else {
                    oldEntries.append(entry)
                }
            }
        }
        
        // Sorting is needed for Top N
        let topNew = Array(newEntries.sorted { $0.rating > $1.rating }.prefix(b15Count))
        let topOld = Array(oldEntries.sorted { $0.rating > $1.rating }.prefix(b35Count))
        
        let total = topNew.reduce(0) { $0 + $1.rating } + topOld.reduce(0) { $0 + $1.rating }
        
        return (total, topOld, topNew)
    }
    
    /// Safely fetch scores from the database, avoiding SwiftData's #Predicate optional UUID bug.
    /// When profileId is nil or filtered query returns empty, falls back to fetching ALL scores.
    static func fetchScoreMap(profileId: UUID?, context: ModelContext) -> [String: Score] {
        var scores: [Score] = []
        if let uid = profileId {
            let desc = FetchDescriptor<Score>(predicate: #Predicate { $0.userProfileId == uid })
            scores = (try? context.fetch(desc)) ?? []
        }
        if scores.isEmpty {
            let fallbackDesc = FetchDescriptor<Score>()
            scores = (try? context.fetch(fallbackDesc)) ?? []
        }
        var map: [String: Score] = [:]
        for score in scores {
            map[score.sheetId] = score
        }
        return map
    }
}

// MARK: - Song Array Extension for B50 Calculation

extension Array where Element: Song {
    /// Converts a `[Song]` into the `Sendable` input format required by `RatingUtils.calculateB50`.
    /// Consolidates duplicate `prepareCalculationInput()` logic from HomeView, BestTableView, RecommendationService.
    func toCalculationInput(userProfileId: UUID? = nil, server: GameServer? = nil, preloadedScores: [String: Score]? = nil) -> [RatingUtils.RatingCalculationInput] {
        let cutoff = server.map { ServerVersionService.shared.cutoffDate(for: $0) } ?? "9999-12-31"
        
        var validCount = 0
        var scoreFoundCount = 0
        var unplayableCount = 0
        
        let result = compactMap { song -> RatingUtils.RatingCalculationInput? in
            if !ServerVersionService.shared.isPlayable(song: song, cutoff: cutoff, server: server) {
                unplayableCount += 1
                return nil
            }
            validCount += 1
            
            let sheetsWithScores = song.sheets.compactMap { sheet -> RatingUtils.SheetCalculationInput? in
                var targetScore: Score? = sheet.score(for: userProfileId)
                
                // Fallback to preloaded dictionary if relationship is broken
                if targetScore == nil, let preloads = preloadedScores {
                    let key = "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
                    targetScore = preloads[key]
                }
                
                guard let score = targetScore else { return nil }
                scoreFoundCount += 1
                return RatingUtils.SheetCalculationInput(
                    difficulty: sheet.difficulty,
                    type: sheet.type,
                    internalLevel: sheet.internalLevelValue,
                    level: sheet.levelValue,
                    rate: score.rate,
                    fc: score.fc,
                    fs: score.fs,
                    dxScore: score.dxScore,
                    maxDxScore: (sheet.total ?? 0) * 3,
                    songId: sheet.songId
                )
            }
            
            let isRegionActive: Bool
            if let targetServer = server {
                isRegionActive = song.sheets.contains { sheet in
                    switch targetServer {
                    case .jp: return sheet.regionJp
                    case .intl: return sheet.regionIntl
                    case .cn: return sheet.regionCn
                    }
                }
            } else {
                isRegionActive = false
            }
            
            return RatingUtils.RatingCalculationInput(
                songIdentifier: song.songIdentifier,
                songId: song.songId,
                title: song.title,
                version: song.version,
                releaseDate: song.releaseDate,
                imageName: song.imageName,
                isRegionActive: isRegionActive,
                sheets: sheetsWithScores
            )
        }
        
        print("RatingUtils.toCalculationInput: Total songs: \(self.count), Valid(Playable): \(validCount), Unplayable: \(unplayableCount), Total Sheets w/ Scores Found: \(scoreFoundCount)")
        return result
    }
}

