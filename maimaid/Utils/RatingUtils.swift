import Foundation

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
    
    static func calculateRating(internalLevel: Double, achievement: Double, fc: String?) -> Int {
        var rating = calculateRating(internalLevel: internalLevel, achievements: achievement)
        
        // AP Bonus check based on FC status
        if let fc = fc?.lowercased(), fc.contains("ap") {
            rating += 1
        }
        
        return rating
    }
    
    struct RatingCalculationInput: Sendable {
        let songId: String // Added for navigation
        let title: String
        let version: String?
        let releaseDate: String?
        let imageUrl: String?
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
    }
    
    struct RatingEntry: Identifiable, Sendable {
        let id = UUID()
        let songId: String // Added for navigation
        let songTitle: String
        let imageUrl: String?
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
    }
    
    static func calculateB50(input: [RatingCalculationInput], b35Count: Int = 35, b15Count: Int = 15) -> (total: Int, b35: [RatingEntry], b15: [RatingEntry]) {
        // Optimize version detection: O(N) single pass
        var latestVersion = ""
        var latestDate = ""
        for song in input {
            if let date = song.releaseDate, date > latestDate {
                latestDate = date
                latestVersion = song.version ?? ""
            }
        }
        
        var newEntries: [RatingEntry] = []
        var oldEntries: [RatingEntry] = []
        
        for song in input {
            let isNew = song.version == latestVersion
            for sheet in song.sheets {
                let level = sheet.internalLevel ?? sheet.level ?? 0.0
                let rating = calculateRating(internalLevel: level, achievement: sheet.rate, fc: sheet.fc)
                
                let entry = RatingEntry(
                    songId: song.songId,
                    songTitle: song.title,
                    imageUrl: song.imageUrl,
                    imageName: song.imageName,
                    diff: sheet.difficulty.uppercased(),
                    type: sheet.type.uppercased(),
                    achievement: sheet.rate,
                    level: level,
                    rating: rating,
                    isNew: isNew,
                    fc: sheet.fc,
                    fs: sheet.fs,
                    dxScore: sheet.dxScore
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
