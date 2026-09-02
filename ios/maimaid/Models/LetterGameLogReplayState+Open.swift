import Foundation

nonisolated extension LetterGameLogReplayState {
    mutating func narrateOpen(
        _ log: LetterGameLogEntry,
        actorKey: String,
        actor: String,
        previousNoProgress: Int,
        previousSuccess: Int
    ) -> LetterGameLogNarration {
        let count = max(log.newlyRevealedCount ?? 0, 0)
        let points = max(log.points ?? count, 0)
        let normalizedCharacter = (log.character ?? "").uppercased().first
        let repeated = normalizedCharacter.map { !openedCharacters.insert($0).inserted } ?? false
        let narration = openNarration(
            log,
            actor: actor,
            previousNoProgress: previousNoProgress,
            previousSuccess: previousSuccess,
            repeated: repeated
        )

        if count == 0 {
            noProgressStreak = previousNoProgress + 1
            successStreaks[actorKey] = 0
            failureStreaks[actorKey, default: 0] += 1
        } else {
            noProgressStreak = 0
            successStreaks[actorKey] = previousSuccess + 1
            failureStreaks[actorKey] = 0
        }
        balances[actorKey] = log.balance ?? (balances[actorKey, default: 0] + points)
        return narration
    }

    private func openNarration(
        _ log: LetterGameLogEntry,
        actor: String,
        previousNoProgress: Int,
        previousSuccess: Int,
        repeated: Bool
    ) -> LetterGameLogNarration {
        let count = max(log.newlyRevealedCount ?? 0, 0)
        let points = max(log.points ?? count, 0)
        let character = log.character ?? ""

        if count == 0 {
            let templates: [LetterGameLogTemplate] = previousNoProgress >= 2
                ? [.openNoProgressStreak1, .openNoProgressStreak2]
                : [.openNoProgress1, .openNoProgress2]
            return choose(
                log,
                templates: templates,
                actor: actor,
                character: character,
                streak: previousNoProgress + 1
            )
        }
        if previousNoProgress >= 2 {
            return choose(
                log,
                templates: [.progressRecovered1, .progressRecovered2],
                actor: actor,
                count: previousNoProgress,
                points: points
            )
        }
        if repeated {
            return choose(
                log,
                templates: [.openRepeated1, .openRepeated2],
                actor: actor,
                character: character,
                count: count,
                points: points
            )
        }
        return positiveOpenNarration(
            log,
            actor: actor,
            count: count,
            points: points,
            previousSuccess: previousSuccess
        )
    }

    private func positiveOpenNarration(
        _ log: LetterGameLogEntry,
        actor: String,
        count: Int,
        points: Int,
        previousSuccess: Int
    ) -> LetterGameLogNarration {
        if previousSuccess >= 2 {
            return choose(
                log,
                templates: [.openSuccessStreak1, .openSuccessStreak2],
                actor: actor,
                count: count,
                points: points,
                streak: previousSuccess + 1
            )
        }
        let templates: [LetterGameLogTemplate] = count >= 4
            ? [.openMany1, .openMany2]
            : [.openFew1, .openFew2]
        return choose(
            log,
            templates: templates,
            actor: actor,
            count: count,
            points: points
        )
    }
}
