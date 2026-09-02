import Foundation

nonisolated struct LetterGameRoomMember: Codable, Equatable, Identifiable, Sendable {
    let memberId: String?
    let userId: String
    let status: String
    let seatOrder: Int
    let displayName: String?
    let avatarUrl: String?

    var id: String { memberId ?? userId }
    var name: String { displayName?.isEmpty == false ? displayName ?? userId : userId }

    private enum CodingKeys: String, CodingKey {
        case memberId = "id"
        case userId
        case status
        case seatOrder
        case displayName
        case avatarUrl
    }
}
