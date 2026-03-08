import Foundation
import SwiftData

@Model
final class Sheet {
    var songIdentifier: String
    var type: String // "dx", "std", "utage"
    var difficulty: String // "basic", "advanced", "expert", "master", "remaster", or Utage Kanji
    var level: String
    var levelValue: Double?
    var internalLevel: String?
    var internalLevelValue: Double?
    var noteDesigner: String?
    
    // Note Counts
    var tap: Int?
    var hold: Int?
    var slide: Int?
    var touch: Int?
    var breakCount: Int?
    var total: Int?
    
    // Regional Availability
    var regionJp: Bool = true  // Base data is from JP
    var regionIntl: Bool = false
    var regionUsa: Bool = false
    var regionCn: Bool = false
    
    var songId: Int = 0
    
    var song: Song?
    @Relationship(deleteRule: .cascade) var scores: [Score] = []
    @Relationship(deleteRule: .cascade) var playRecords: [PlayRecord]?
    
    /// Returns the score for a specific user profile, or the first score if no profile specified (legacy compat)
    @available(*, deprecated, message: "Use ScoreService.shared.score(for:context:) instead")
    func score(for userProfileId: UUID? = nil) -> Score? {
        if let uid = userProfileId {
            return scores.first { $0.userProfileId == uid }
        }
        return scores.first { $0.userProfileId == nil }
    }
    
    init(songIdentifier: String, type: String, difficulty: String, level: String, levelValue: Double? = nil, internalLevel: String? = nil, internalLevelValue: Double? = nil, noteDesigner: String? = nil, tap: Int? = nil, hold: Int? = nil, slide: Int? = nil, touch: Int? = nil, breakCount: Int? = nil, total: Int? = nil, songId: Int = 0) {
        self.songIdentifier = songIdentifier
        self.type = type
        self.difficulty = difficulty
        self.level = level
        self.levelValue = levelValue
        self.internalLevel = internalLevel
        self.internalLevelValue = internalLevelValue
        self.noteDesigner = noteDesigner
        self.tap = tap
        self.hold = hold
        self.slide = slide
        self.touch = touch
        self.breakCount = breakCount
        self.total = total
        self.songId = songId
    }
}

