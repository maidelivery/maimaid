import Foundation

extension LetterGameLogMessage {
    static func localizedGuess(_ narration: LetterGameLogNarration) -> String {
        return switch narration.template {
        case .guessBlind1, .guessBlind2,
             .guessCorrect1, .guessCorrect2,
             .guessCorrectStreak1, .guessCorrectStreak2:
            localizedCorrectGuess(narration)
        case .guessIncorrect1, .guessIncorrect2,
             .guessIncorrectStreak1, .guessIncorrectStreak2:
            localizedIncorrectGuess(narration)
        default:
            narration.fallbackMessage ?? ""
        }
    }

    private static func localizedCorrectGuess(_ narration: LetterGameLogNarration) -> String {
        let actor = narration.actor
        let points = narration.points
        let streak = narration.streak

        return switch narration.template {
        case .guessBlind1:
            String(localized: "letterGame.log.guessBlind1 \(actor) \(points)")
        case .guessBlind2:
            String(localized: "letterGame.log.guessBlind2 \(actor) \(points)")
        case .guessCorrect1:
            String(localized: "letterGame.log.guessCorrect1 \(actor) \(points)")
        case .guessCorrect2:
            String(localized: "letterGame.log.guessCorrect2 \(actor) \(points)")
        case .guessCorrectStreak1:
            String(localized: "letterGame.log.guessCorrectStreak1 \(actor) \(streak) \(points)")
        case .guessCorrectStreak2:
            String(localized: "letterGame.log.guessCorrectStreak2 \(actor) \(streak) \(points)")
        default:
            narration.fallbackMessage ?? ""
        }
    }

    private static func localizedIncorrectGuess(_ narration: LetterGameLogNarration) -> String {
        let actor = narration.actor
        let streak = narration.streak

        return switch narration.template {
        case .guessIncorrect1:
            String(localized: "letterGame.log.guessIncorrect1 \(actor)")
        case .guessIncorrect2:
            String(localized: "letterGame.log.guessIncorrect2 \(actor)")
        case .guessIncorrectStreak1:
            String(localized: "letterGame.log.guessIncorrectStreak1 \(actor) \(streak)")
        case .guessIncorrectStreak2:
            String(localized: "letterGame.log.guessIncorrectStreak2 \(actor) \(streak)")
        default:
            narration.fallbackMessage ?? ""
        }
    }

    static func localizedRecovery(_ narration: LetterGameLogNarration) -> String {
        let actor = narration.actor
        let count = narration.count
        let points = narration.points

        return switch narration.template {
        case .progressRecovered1:
            String(localized: "letterGame.log.progressRecovered1 \(actor) \(count) \(points)")
        case .progressRecovered2:
            String(localized: "letterGame.log.progressRecovered2 \(actor) \(count) \(points)")
        default:
            narration.fallbackMessage ?? ""
        }
    }
}
