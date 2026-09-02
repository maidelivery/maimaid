import Foundation

extension LetterGameLogMessage {
    static func localizedHint(_ narration: LetterGameLogNarration) -> String {
        let actor = narration.actor
        let cost = narration.cost
        let balance = narration.balance
        let number = narration.hintCount
        let type = localizedHintType(narration.hintType)
        let result = localizedHintResult(narration.hintResult)
        let hiddenResult = ""

        return switch narration.template {
        case .hintPublic1:
            String(
                localized: "letterGame.log.hintPublic1 \(actor) \(cost) \(balance) \(number) \(type) \(result)"
            )
        case .hintPublic2:
            String(
                localized: "letterGame.log.hintPublic2 \(actor) \(cost) \(balance) \(number) \(type) \(result)"
            )
        case .hintPrivate1:
            String(
                localized: "letterGame.log.hintPrivate1 \(actor) \(cost) \(balance) \(number) \(type) \(hiddenResult)"
            )
        case .hintPrivate2:
            String(
                localized: "letterGame.log.hintPrivate2 \(actor) \(cost) \(balance) \(number) \(type) \(hiddenResult)"
            )
        case .hintLowScore1:
            String(
                localized: "letterGame.log.hintLowScore1 \(actor) \(cost) \(balance) \(number) \(type) \(result)"
            )
        case .hintLowScore2:
            String(
                localized: "letterGame.log.hintLowScore2 \(actor) \(cost) \(balance) \(number) \(type) \(result)"
            )
        case .hintNormal1:
            String(
                localized: "letterGame.log.hintNormal1 \(actor) \(cost) \(balance) \(number) \(type) \(result)"
            )
        case .hintNormal2:
            String(
                localized: "letterGame.log.hintNormal2 \(actor) \(cost) \(balance) \(number) \(type) \(result)"
            )
        default:
            narration.fallbackMessage ?? ""
        }
    }

    private static func localizedHintType(_ type: String) -> String {
        switch type {
        case "version": String(localized: "letterGame.hintVersion")
        case "constant": String(localized: "letterGame.hintConstant")
        case "white_chart": String(localized: "letterGame.hintRemasterLog")
        default: String(localized: "letterGame.buyHint")
        }
    }

    private static func localizedHintResult(_ result: Bool?) -> String {
        switch result {
        case true: String(localized: "letterGame.hintRemasterYes")
        case false: String(localized: "letterGame.hintRemasterNo")
        case nil: ""
        }
    }
}
