import Foundation

extension LetterGameLogMessage {
    static func localizedBasicOpen(_ narration: LetterGameLogNarration) -> String {
        let actor = narration.actor
        let character = narration.character
        let count = narration.count
        let points = narration.points
        let streak = narration.streak

        return switch narration.template {
        case .openNoProgress1:
            String(localized: "letterGame.log.openNoProgress1 \(actor) \(character)")
        case .openNoProgress2:
            String(localized: "letterGame.log.openNoProgress2 \(actor) \(character)")
        case .openNoProgressStreak1:
            String(localized: "letterGame.log.openNoProgressStreak1 \(actor) \(streak)")
        case .openNoProgressStreak2:
            String(localized: "letterGame.log.openNoProgressStreak2 \(actor) \(streak)")
        case .openRepeated1:
            String(localized: "letterGame.log.openRepeated1 \(actor) \(character) \(count) \(points)")
        case .openRepeated2:
            String(localized: "letterGame.log.openRepeated2 \(actor) \(character) \(count) \(points)")
        case .openFew1:
            String(localized: "letterGame.log.openFew1 \(actor) \(count) \(points)")
        case .openFew2:
            String(localized: "letterGame.log.openFew2 \(actor) \(count) \(points)")
        default:
            narration.fallbackMessage ?? ""
        }
    }

    static func localizedOpenMomentum(_ narration: LetterGameLogNarration) -> String {
        let actor = narration.actor
        let count = narration.count
        let points = narration.points
        let streak = narration.streak

        return switch narration.template {
        case .openMany1:
            String(localized: "letterGame.log.openMany1 \(actor) \(count) \(points)")
        case .openMany2:
            String(localized: "letterGame.log.openMany2 \(actor) \(count) \(points)")
        case .openSuccessStreak1:
            String(localized: "letterGame.log.openSuccessStreak1 \(actor) \(streak) \(count) \(points)")
        case .openSuccessStreak2:
            String(localized: "letterGame.log.openSuccessStreak2 \(actor) \(streak) \(count) \(points)")
        default:
            narration.fallbackMessage ?? ""
        }
    }
}
