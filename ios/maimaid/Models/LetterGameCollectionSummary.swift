import Foundation

nonisolated struct LetterGameCollectionSummary: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let name: String
    let songCount: Int
}
