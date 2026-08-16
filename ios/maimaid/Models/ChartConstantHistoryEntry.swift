import Foundation

struct ChartConstantHistoryEntry: Identifiable, Equatable {
    let version: String
    let constant: Double
    let change: Double?

    var id: String { "\(version):\(constant)" }

    nonisolated static func changes(
        from values: [String: Double]?,
        versionSequence: [String]
    ) -> [ChartConstantHistoryEntry] {
        let sortedValues = (values ?? [:])
            .compactMap { version, constant -> ChartConstantHistoryEntry? in
                let normalizedVersion = version.trimmingCharacters(in: .whitespacesAndNewlines)
                guard !normalizedVersion.isEmpty, constant.isFinite else { return nil }
                return ChartConstantHistoryEntry(
                    version: normalizedVersion,
                    constant: constant,
                    change: nil
                )
            }
            .sorted { lhs, rhs in
                let lhsOrder = versionOrder(lhs.version, in: versionSequence)
                let rhsOrder = versionOrder(rhs.version, in: versionSequence)
                if lhsOrder != rhsOrder {
                    return lhsOrder < rhsOrder
                }
                return lhs.version.localizedCaseInsensitiveCompare(rhs.version) == .orderedAscending
            }

        let changes = sortedValues.reduce(into: [ChartConstantHistoryEntry]()) { result, entry in
            if result.last?.constant != entry.constant {
                result.append(entry)
            }
        }
        guard Set(changes.map(\.constant)).count > 1 else { return [] }
        let directionalChanges = changes.enumerated().map { index, entry in
            let change = index > 0
                ? ((entry.constant - changes[index - 1].constant) * 10).rounded() / 10
                : nil
            return ChartConstantHistoryEntry(
                version: entry.version,
                constant: entry.constant,
                change: change
            )
        }
        return Array(directionalChanges.reversed())
    }

    nonisolated private static func versionOrder(_ version: String, in sequence: [String]) -> Int {
        if let index = sequence.firstIndex(where: { $0.caseInsensitiveCompare(version) == .orderedSame }) {
            return index
        }
        return sequence
            .enumerated()
            .filter { _, candidate in
                version.localizedCaseInsensitiveContains(candidate) ||
                    candidate.localizedCaseInsensitiveContains(version)
            }
            .max { $0.element.count < $1.element.count }?
            .offset ?? Int.max
    }
}
