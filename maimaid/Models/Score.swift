import Foundation
import SwiftData

@Model
final class Score {
    var sheetId: String // Combines songId, type, and difficulty for uniqueness
    var rate: Double // Percentage, e.g., 100.5000
    var rank: String // SSS+, SSS, SS, etc.
    var achievementDate: Date
    
    var sheet: Sheet?
    
    init(sheetId: String, rate: Double, rank: String, achievementDate: Date = Date()) {
        self.sheetId = sheetId
        self.rate = rate
        self.rank = rank
        self.achievementDate = achievementDate
    }
}
