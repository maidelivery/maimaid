import Foundation

nonisolated struct LetterGameMatchPlayer: Codable, Equatable, Identifiable, Sendable {
    let userId: String
    let score: Int
    let turnOrder: Int
    let status: String
    let scoringEligible: Bool
    let displayName: String?
    let avatarUrl: String?

    var id: String { userId }
    var name: String { displayName?.isEmpty == false ? displayName ?? userId : userId }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        userId = try container.decode(String.self, forKey: .userId)
        score = try container.decodeIfPresent(Int.self, forKey: .score) ?? 0
        turnOrder = try container.decodeIfPresent(Int.self, forKey: .turnOrder) ?? 0
        status = try container.decodeIfPresent(String.self, forKey: .status) ?? "active"
        scoringEligible = try container.decodeIfPresent(Bool.self, forKey: .scoringEligible) ?? true
        displayName = try container.decodeIfPresent(String.self, forKey: .displayName)
        avatarUrl = try container.decodeIfPresent(String.self, forKey: .avatarUrl)
    }
}
