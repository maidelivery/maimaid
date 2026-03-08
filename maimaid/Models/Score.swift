import Foundation
import SwiftData

@Model
final class Score {
    var sheetId: String // Combines songId, type, and difficulty for uniqueness
    var rate: Double // Percentage, e.g., 100.5000
    var rank: String // SSS+, SSS, SS, etc.
    var achievementDate: Date
    var dxScore: Int = 0
    var fc: String?
    var fs: String?
    var userProfileId: UUID? // Links score to a specific user profile
    
    var sheet: Sheet?
    
    init(sheetId: String, rate: Double, rank: String, dxScore: Int = 0, fc: String? = nil, fs: String? = nil, achievementDate: Date = Date(), userProfileId: UUID? = nil) {
        self.sheetId = sheetId
        self.rate = rate
        self.rank = rank
        self.dxScore = dxScore
        self.fc = fc
        self.fs = fs
        self.achievementDate = achievementDate
        self.userProfileId = userProfileId
    }
}
