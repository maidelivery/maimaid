import Foundation
import SwiftData

@Model
final class Sheet {
    var songId: String
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
    
    var song: Song?
    @Relationship(deleteRule: .cascade) var score: Score?
    
    init(songId: String, type: String, difficulty: String, level: String, levelValue: Double? = nil, internalLevel: String? = nil, internalLevelValue: Double? = nil, noteDesigner: String? = nil, tap: Int? = nil, hold: Int? = nil, slide: Int? = nil, touch: Int? = nil, breakCount: Int? = nil, total: Int? = nil) {
        self.songId = songId
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
    }
}
