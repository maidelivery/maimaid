import Foundation

nonisolated struct LetterGameLogNarration: Equatable, Sendable {
    let logId: String
    let template: LetterGameLogTemplate
    let actor: String
    let character: String
    let count: Int
    let points: Int
    let streak: Int
    let cost: Int
    let balance: Int
    let hintCount: Int
    let hintType: String
    let hintResult: Bool?
    let fallbackMessage: String?

    init(
        logId: String,
        template: LetterGameLogTemplate,
        actor: String = "Player",
        character: String = "",
        count: Int = 0,
        points: Int = 0,
        streak: Int = 0,
        cost: Int = 0,
        balance: Int = 0,
        hintCount: Int = 0,
        hintType: String = "hint",
        hintResult: Bool? = nil,
        fallbackMessage: String? = nil
    ) {
        self.logId = logId
        self.template = template
        self.actor = actor
        self.character = character
        self.count = count
        self.points = points
        self.streak = streak
        self.cost = cost
        self.balance = balance
        self.hintCount = hintCount
        self.hintType = hintType
        self.hintResult = hintResult
        self.fallbackMessage = fallbackMessage
    }
}
