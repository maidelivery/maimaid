import Foundation

nonisolated enum LetterGameLogNarrator {
    static func narrate(_ logs: [LetterGameLogEntry]) -> [LetterGameLogNarration] {
        var replay = LetterGameLogReplayState()
        return logs.map { replay.narrate($0) }
    }
}
