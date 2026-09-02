import Foundation

nonisolated extension LetterGameLogReplayState {
    mutating func narrateGuess(
        _ log: LetterGameLogEntry,
        actorKey: String,
        actor: String
    ) -> LetterGameLogNarration {
        let previousNoProgress = noProgressStreak
        let previousSuccess = successStreaks[actorKey, default: 0]
        let previousFailure = failureStreaks[actorKey, default: 0]
        let points = max(log.points ?? 0, 0)
        let isCorrect = log.correct == true
        let narration = if isCorrect {
            correctGuessNarration(
                log,
                actor: actor,
                previousNoProgress: previousNoProgress,
                previousSuccess: previousSuccess
            )
        } else {
            incorrectGuessNarration(log, actor: actor, previousFailure: previousFailure)
        }

        if isCorrect {
            noProgressStreak = 0
            successStreaks[actorKey] = previousSuccess + 1
            failureStreaks[actorKey] = 0
            balances[actorKey] = log.balance ?? (balances[actorKey, default: 0] + points)
        } else {
            noProgressStreak = previousNoProgress + 1
            successStreaks[actorKey] = 0
            failureStreaks[actorKey] = previousFailure + 1
        }
        return narration
    }

    private func correctGuessNarration(
        _ log: LetterGameLogEntry,
        actor: String,
        previousNoProgress: Int,
        previousSuccess: Int
    ) -> LetterGameLogNarration {
        let points = max(log.points ?? 0, 0)
        if previousNoProgress >= 2 {
            return choose(
                log,
                templates: [.progressRecovered1, .progressRecovered2],
                actor: actor,
                count: previousNoProgress,
                points: points
            )
        }
        if log.blind == true {
            return choose(
                log,
                templates: [.guessBlind1, .guessBlind2],
                actor: actor,
                points: points
            )
        }
        if previousSuccess >= 2 {
            return choose(
                log,
                templates: [.guessCorrectStreak1, .guessCorrectStreak2],
                actor: actor,
                points: points,
                streak: previousSuccess + 1
            )
        }
        return choose(
            log,
            templates: [.guessCorrect1, .guessCorrect2],
            actor: actor,
            points: points
        )
    }

    private func incorrectGuessNarration(
        _ log: LetterGameLogEntry,
        actor: String,
        previousFailure: Int
    ) -> LetterGameLogNarration {
        let templates: [LetterGameLogTemplate] = previousFailure >= 1
            ? [.guessIncorrectStreak1, .guessIncorrectStreak2]
            : [.guessIncorrect1, .guessIncorrect2]
        return choose(
            log,
            templates: templates,
            actor: actor,
            streak: previousFailure + 1
        )
    }
}
