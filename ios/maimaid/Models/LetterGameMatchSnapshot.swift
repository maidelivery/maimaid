import Foundation

nonisolated struct LetterGameMatchSnapshot: Codable, Equatable, Sendable {
    let matchId: String
    let status: String
    let revision: Int
    let turnUserId: String?
    let turnDeadline: String?
    let noProgressRounds: Int
    let players: [LetterGameMatchPlayer]
    let songs: [LetterGameMatchSong]
    let roomCode: String?
    let logs: [LetterGameLogEntry]

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        matchId = try container.decode(String.self, forKey: .matchId)
        status = try container.decode(String.self, forKey: .status)
        revision = try container.decodeIfPresent(Int.self, forKey: .revision) ?? 0
        turnUserId = try container.decodeIfPresent(String.self, forKey: .turnUserId)
        turnDeadline = try container.decodeIfPresent(String.self, forKey: .turnDeadline)
        noProgressRounds = try container.decodeIfPresent(Int.self, forKey: .noProgressRounds) ?? 0
        players = try container.decodeIfPresent([LetterGameMatchPlayer].self, forKey: .players) ?? []
        songs = try container.decodeIfPresent([LetterGameMatchSong].self, forKey: .songs) ?? []
        roomCode = try container.decodeIfPresent(String.self, forKey: .roomCode)
        logs = try container.decodeIfPresent([LetterGameLogEntry].self, forKey: .logs) ?? []
    }
}
