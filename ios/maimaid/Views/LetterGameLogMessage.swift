import Foundation

enum LetterGameLogMessage {
    static func localizedString(for narration: LetterGameLogNarration) -> String {
        switch narration.template {
        case .openNoProgress1, .openNoProgress2,
             .openNoProgressStreak1, .openNoProgressStreak2,
             .openRepeated1, .openRepeated2,
             .openFew1, .openFew2:
            localizedBasicOpen(narration)
        case .openMany1, .openMany2, .openSuccessStreak1, .openSuccessStreak2:
            localizedOpenMomentum(narration)
        case .guessBlind1, .guessBlind2,
             .guessCorrect1, .guessCorrect2,
             .guessCorrectStreak1, .guessCorrectStreak2,
             .guessIncorrect1, .guessIncorrect2,
             .guessIncorrectStreak1, .guessIncorrectStreak2:
            localizedGuess(narration)
        case .progressRecovered1, .progressRecovered2:
            localizedRecovery(narration)
        case .hintPublic1, .hintPublic2,
             .hintPrivate1, .hintPrivate2,
             .hintLowScore1, .hintLowScore2,
             .hintNormal1, .hintNormal2:
            localizedHint(narration)
        case .fallback:
            narration.fallbackMessage ?? ""
        }
    }
}
