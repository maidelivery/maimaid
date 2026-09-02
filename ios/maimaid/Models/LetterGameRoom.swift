import Foundation

nonisolated struct LetterGameRoom: Codable, Equatable, Identifiable, Sendable {
    let id: String
    let code: String
    let visibility: String
    let hostMode: String
    let hostUserId: String
    let status: String
    let settings: LetterGameRoomSettings
    let memberCount: Int
    let members: [LetterGameRoomMember]
    let latestMatch: LetterGameLatestMatch?

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        id = try container.decode(String.self, forKey: .id)
        code = try container.decode(String.self, forKey: .code)
        visibility = try container.decode(String.self, forKey: .visibility)
        hostMode = try container.decodeIfPresent(String.self, forKey: .hostMode) ?? "fixed"
        hostUserId = try container.decode(String.self, forKey: .hostUserId)
        status = try container.decode(String.self, forKey: .status)
        settings = try container.decodeIfPresent(LetterGameRoomSettings.self, forKey: .settings) ?? .init()
        memberCount = try container.decodeIfPresent(Int.self, forKey: .memberCount) ?? 0
        members = try container.decodeIfPresent([LetterGameRoomMember].self, forKey: .members) ?? []
        latestMatch = try container.decodeIfPresent(LetterGameLatestMatch.self, forKey: .latestMatch)
    }
}
