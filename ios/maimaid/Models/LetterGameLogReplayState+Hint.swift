import Foundation

nonisolated extension LetterGameLogReplayState {
    mutating func narrateHint(
        _ log: LetterGameLogEntry,
        actorKey: String,
        actor: String
    ) -> LetterGameLogNarration {
        let cost = max(log.hintCost ?? log.points ?? 0, 0)
        let beforeBalance = balances[actorKey] ?? log.balance.map { $0 + cost } ?? 0
        let afterBalance = log.balance ?? (beforeBalance - cost)
        let hasBalance = balances[actorKey] != nil || log.balance != nil
        balances[actorKey] = afterBalance
        let hintCount = hintCounts[actorKey, default: 0] + 1
        hintCounts[actorKey] = hintCount

        let templates: [LetterGameLogTemplate]
        if hasBalance && afterBalance <= cost * 2 && cost > 0 {
            templates = [.hintLowScore1, .hintLowScore2]
        } else if log.hintVisibility == "private" {
            templates = [.hintPrivate1, .hintPrivate2]
        } else if log.hintVisibility == "public" {
            templates = [.hintPublic1, .hintPublic2]
        } else {
            templates = [.hintNormal1, .hintNormal2]
        }

        return choose(
            log,
            templates: templates,
            actor: actor,
            cost: cost,
            balance: afterBalance,
            hintCount: hintCount,
            hintType: nonBlank(log.hintType) ?? "hint",
            hintResult: log.hintResult
        )
    }
}
