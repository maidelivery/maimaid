import Foundation

nonisolated struct LetterGameFact: Codable, Equatable, Identifiable, Sendable {
    let type: String
    let visibility: String
    let value: LetterGameJSONValue

    var id: String { "\(type)-\(visibility)" }
}
