import Foundation

enum LetterGameLogMessage {
    static func localizedString(for log: LetterGameLogEntry) -> String {
        let actor = if let actorName = log.actorName, !actorName.isEmpty {
            actorName
        } else {
            log.actorUserId ?? String(localized: "letterGame.player")
        }

        switch log.actionType {
        case "open_character":
            let character = log.character ?? ""
            let revealedCount = log.newlyRevealedCount ?? 0
            let points = log.points ?? 0
            return String(
                localized: "letterGame.logOpen \(actor) \(character) \(revealedCount) \(points)"
            )
        case "guess_song" where log.correct == true:
            return String(
                localized: "letterGame.logCorrect \(actor) \(log.points ?? 0)"
            )
        case "guess_song":
            return String(localized: "letterGame.logWrong \(actor)")
        case "buy_hint":
            return String(localized: "letterGame.logHint \(actor) \(log.hintCost ?? 0)")
        default:
            return log.message
        }
    }
}
