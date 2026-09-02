import Foundation

nonisolated struct LetterGameLogEntry: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let message: String
    let actorUserId: String?
    let actorName: String?
    let actionType: String?
    let character: String?
    let newlyRevealedCount: Int?
    let points: Int?
    let correct: Bool?
    let blind: Bool?
    let balance: Int?
    let hintType: String?
    let hintVisibility: String?
    let hintCost: Int?
    let hintResult: Bool?
    let songNumber: Int?
}
