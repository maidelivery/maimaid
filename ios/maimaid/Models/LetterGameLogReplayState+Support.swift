import Foundation

nonisolated extension LetterGameLogReplayState {
    func choose(
        _ log: LetterGameLogEntry,
        templates: [LetterGameLogTemplate],
        actor: String,
        character: String = "",
        count: Int = 0,
        points: Int = 0,
        streak: Int = 0,
        cost: Int = 0,
        balance: Int = 0,
        hintCount: Int = 0,
        hintType: String = "hint",
        hintResult: Bool? = nil
    ) -> LetterGameLogNarration {
        guard let firstTemplate = templates.first else {
            return LetterGameLogNarration(
                logId: log.id,
                template: .fallback,
                actor: actor,
                fallbackMessage: log.message
            )
        }
        let key = "\(log.id)|\(log.actionType ?? "null")|\(firstTemplate.rawValue)"
        let template = templates[Self.stableIndex(key, size: templates.count)]
        return LetterGameLogNarration(
            logId: log.id,
            template: template,
            actor: actor,
            character: character,
            count: count,
            points: points,
            streak: streak,
            cost: cost,
            balance: balance,
            hintCount: hintCount,
            hintType: hintType,
            hintResult: hintResult
        )
    }

    func nonBlank(_ value: String?) -> String? {
        guard let value, !value.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else {
            return nil
        }
        return value
    }

    private static func stableIndex(_ value: String, size: Int) -> Int {
        guard size > 0 else { return 0 }
        var hash: UInt32 = 0x811C9DC5
        for codeUnit in value.utf16 {
            hash = (hash ^ UInt32(codeUnit)) &* 16_777_619
        }
        return Int(hash & UInt32(Int32.max)) % size
    }
}
