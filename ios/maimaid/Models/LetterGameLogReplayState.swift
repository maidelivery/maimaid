import Foundation

nonisolated struct LetterGameLogReplayState {
    var successStreaks: [String: Int] = [:]
    var failureStreaks: [String: Int] = [:]
    var balances: [String: Int] = [:]
    var hintCounts: [String: Int] = [:]
    var openedCharacters: Set<Character> = []
    var noProgressStreak = 0

    mutating func narrate(_ log: LetterGameLogEntry) -> LetterGameLogNarration {
        let actorKey = nonBlank(log.actorUserId) ?? nonBlank(log.actorName) ?? "unknown"
        let actor = nonBlank(log.actorName) ?? nonBlank(log.actorUserId) ?? "Player"
        let previousNoProgress = noProgressStreak
        let previousSuccess = successStreaks[actorKey, default: 0]

        switch log.actionType {
        case "open_character":
            return narrateOpen(
                log,
                actorKey: actorKey,
                actor: actor,
                previousNoProgress: previousNoProgress,
                previousSuccess: previousSuccess
            )
        case "guess_song":
            return narrateGuess(
                log,
                actorKey: actorKey,
                actor: actor
            )
        case "buy_hint":
            return narrateHint(log, actorKey: actorKey, actor: actor)
        default:
            return LetterGameLogNarration(
                logId: log.id,
                template: .fallback,
                actor: actor,
                fallbackMessage: log.message
            )
        }
    }
}
