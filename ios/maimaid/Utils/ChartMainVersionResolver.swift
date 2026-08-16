import Foundation

enum ChartMainVersionResolver {
    static func resolve(
        sheets: [Sheet],
        server: GameServer,
        fallback: String?
    ) -> String? {
        let candidates = sheets.compactMap { sheet -> (difficulty: String, version: String)? in
            guard let version = ServerChartPolicy.metadata(for: sheet, on: server)
                .version?
                .trimmingCharacters(in: .whitespacesAndNewlines),
                  !version.isEmpty else {
                return nil
            }
            return (sheet.difficulty, version)
        }
        let primaryCandidates = candidates
            .filter { $0.difficulty.caseInsensitiveCompare("remaster") != .orderedSame }
            .ifEmpty(candidates)

        return primaryCandidates.min { lhs, rhs in
            let lhsOrder = ThemeUtils.versionSortOrder(lhs.version)
            let rhsOrder = ThemeUtils.versionSortOrder(rhs.version)
            if lhsOrder != rhsOrder {
                return lhsOrder < rhsOrder
            }
            return difficultyOrder(lhs.difficulty) < difficultyOrder(rhs.difficulty)
        }?.version ?? normalizedVersion(fallback)
    }

    private static func difficultyOrder(_ difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "basic": 0
        case "advanced": 1
        case "expert": 2
        case "master": 3
        default: 4
        }
    }

    private static func normalizedVersion(_ version: String?) -> String? {
        guard let version = version?.trimmingCharacters(in: .whitespacesAndNewlines),
              !version.isEmpty else {
            return nil
        }
        return version
    }
}

private extension Array {
    func ifEmpty(_ fallback: [Element]) -> [Element] {
        isEmpty ? fallback : self
    }
}
