import Foundation

nonisolated struct LetterGameLatestMatch: Codable, Equatable, Sendable {
    let id: String
    let sequence: Int
    let status: String
    let revision: Int
}
