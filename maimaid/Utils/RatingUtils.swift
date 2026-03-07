import Foundation
import SwiftUI

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
        let fc: String? // Added for UI
        let fs: String? // Added for UI
        let dxScore: Int // Added for UI
        let maxDxScore: Int // Added for stars
    }
    
    static func calculateB50(input: [RatingCalculationInput], b35Count: Int = 35, b15Count: Int = 15) -> (total: Int, b35: [RatingEntry], b15: [RatingEntry]) {
        let latestVersion = ThemeUtils.latestVersion
        
        var newEntries: [RatingEntry] = []
        var oldEntries: [RatingEntry] = []
        
        for song in input {
            let isNew = song.version == latestVersion
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
}

// MARK: - Song Array Extension for B50 Calculation

extension Array where Element: Song {
    /// Converts a `[Song]` into the `Sendable` input format required by `RatingUtils.calculateB50`.
    /// Consolidates duplicate `prepareCalculationInput()` logic from HomeView, BestTableView, RecommendationService.
    func toCalculationInput() -> [RatingUtils.RatingCalculationInput] {
        map { song in
            RatingUtils.RatingCalculationInput(
                songIdentifier: song.songIdentifier,
                songId: song.songId,
                title: song.title,
                version: song.version,
                releaseDate: song.releaseDate,
                imageName: song.imageName,
                sheets: song.sheets.compactMap { sheet in
                    guard let score = sheet.score else { return nil }
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
            )
        }
    }
}
