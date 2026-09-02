import Foundation

struct ScoreQueryFilterSettings: Equatable, Sendable {
    var selectedDifficulties: Set<String> = []
    var selectedRanks: Set<String> = []
    var selectedFC: Set<String> = []
    var selectedFS: Set<String> = []

    var isEmpty: Bool {
        selectedDifficulties.isEmpty
            && selectedRanks.isEmpty
            && selectedFC.isEmpty
            && selectedFS.isEmpty
    }
}
