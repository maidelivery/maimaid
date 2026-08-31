import Foundation
import Observation

struct LetterGameRoomSettings: Codable, Equatable {
    let turnDurationSeconds: Int
    let stalledRoundLimit: Int
    let songCountOverride: Int?
    let publicHintCost: Int
    let privateHintCost: Int
    let selectionMode: String
}

struct LetterGameRoomMember: Codable, Equatable, Identifiable {
    let userId: String
    let status: String
    let seatOrder: Int
    var id: String { userId }
}

struct LetterGameLatestMatch: Codable, Equatable {
    let id: String
    let sequence: Int
    let status: String
    let revision: Int
}

struct LetterGameRoom: Codable, Equatable, Identifiable {
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
}

struct LetterGameMatchPlayer: Codable, Equatable, Identifiable {
    let userId: String
    let score: Int
    let turnOrder: Int
    let status: String
    let scoringEligible: Bool
    var id: String { userId }
}

struct LetterGameFact: Codable, Equatable, Identifiable {
    let type: String
    let visibility: String
    let value: AnyCodable
    var id: String { "\(type)-\(visibility)" }
}

struct LetterGameMatchSong: Codable, Equatable, Identifiable {
    let slotId: String
    let title: String
    let remainingCharacterCount: Int
    let status: String
    let completionReason: String?
    let completedByUserId: String?
    let facts: [LetterGameFact]
    var id: String { slotId }
}

struct LetterGameMatchSnapshot: Codable, Equatable {
    let matchId: String
    let status: String
    let revision: Int
    let turnUserId: String?
    let turnDeadline: Date?
    let noProgressRounds: Int
    let players: [LetterGameMatchPlayer]
    let songs: [LetterGameMatchSong]
}

private struct LetterGameRoomResponse: Codable { let room: LetterGameRoom }
private struct LetterGameMatchResponse: Codable { let match: LetterGameMatchSnapshot }
private struct LetterGameRoomsResponse: Codable { let rooms: [LetterGameRoom] }
private struct LetterGameCreateRequest: Codable {
    let visibility: String
    let hostMode: String
    let turnDurationSeconds: Int
    let stalledRoundLimit: Int
    let songCount: Int?
    let publicHintCost: Int
    let privateHintCost: Int
    let selectionMode: String
    let selectionConfig: [String: String]
}

struct AnyCodable: Codable, Equatable {
    let value: AnyHashable?

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if container.decodeNil() { value = nil }
        else if let bool = try? container.decode(Bool.self) { value = bool }
        else if let number = try? container.decode(Double.self) { value = number }
        else if let string = try? container.decode(String.self) { value = string }
        else { value = nil }
    }

    func encode(to encoder: Encoder) throws {
        var container = encoder.singleValueContainer()
        switch value {
        case let value as Bool: try container.encode(value)
        case let value as Double: try container.encode(value)
        case let value as String: try container.encode(value)
        default: try container.encodeNil()
        }
    }
}

@MainActor
@Observable
final class LetterGameService {
    var room: LetterGameRoom?
    var match: LetterGameMatchSnapshot?
    var errorMessage: String?
    private var socket: URLSessionWebSocketTask?
    private var receiveTask: Task<Void, Never>?

    func loadPublicRooms() async throws -> [LetterGameRoom] {
        let response: LetterGameRoomsResponse = try await BackendAPIClient.request(path: "v1/letter-game/rooms")
        return response.rooms
    }

    func createPrivateRoom() async {
        do {
            let response: LetterGameRoomResponse = try await BackendAPIClient.request(
                path: "v1/letter-game/rooms",
                body: LetterGameCreateRequest(
                    visibility: "private",
                    hostMode: "fixed",
                    turnDurationSeconds: 30,
                    stalledRoundLimit: 3,
                    songCount: nil,
                    publicHintCost: 5,
                    privateHintCost: 10,
                    selectionMode: "filtered_random",
                    selectionConfig: [:]
                )
            )
            room = response.room
            connect()
        } catch { errorMessage = error.localizedDescription }
    }

    func join(code: String) async {
        struct JoinRequest: Codable { let code: String }
        do {
            let response: LetterGameRoomResponse = try await BackendAPIClient.request(path: "v1/letter-game/rooms:join", body: JoinRequest(code: code.uppercased()))
            room = response.room
            connect()
        } catch { errorMessage = error.localizedDescription }
    }

    func start() async {
        guard let room else { return }
        do {
            let response: LetterGameMatchResponse = try await BackendAPIClient.request(path: "v1/letter-game/rooms/\(room.id)/start", method: "POST")
            match = response.match
        } catch { errorMessage = error.localizedDescription }
    }

    func connect() {
        guard let room, let baseURL = BackendConfig.baseURL, let token = BackendSessionManager.shared.accessTokenForRequest() else { return }
        var components = URLComponents(url: baseURL.appending(path: "v1/letter-game/rooms/\(room.code)/ws"), resolvingAgainstBaseURL: false)
        components?.scheme = baseURL.scheme == "https" ? "wss" : "ws"
        guard let url = components?.url else { return }
        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        socket?.cancel(with: .goingAway, reason: nil)
        let task = URLSession.shared.webSocketTask(with: request)
        socket = task
        task.resume()
        receiveTask?.cancel()
        receiveTask = Task { [weak self] in
            guard let self else { return }
            while !Task.isCancelled {
                do {
                    let message = try await task.receive()
                    guard case let .string(text) = message, let data = text.data(using: .utf8) else { continue }
                    await self.consume(data: data)
                } catch { break }
            }
        }
    }

    func sendAction(actionId: String = UUID().uuidString, payload: [String: String]) async {
        guard let socket, let match else { return }
        let body: [String: Any] = ["type": "action", "matchId": match.matchId, "actionId": actionId, "expectedRevision": match.revision, "payload": payload]
        guard let data = try? JSONSerialization.data(withJSONObject: body) else { return }
        try? await socket.send(.string(String(decoding: data, as: UTF8.self)))
    }

    private func consume(data: Data) async {
        struct Envelope: Decodable { let type: String; let room: LetterGameRoom?; let match: LetterGameMatchSnapshot?; let message: String? }
        guard let envelope = try? BackendAPIClient.decoder.decode(Envelope.self, from: data) else { return }
        if envelope.type == "room_snapshot" { room = envelope.room }
        if envelope.type == "match_snapshot" { match = envelope.match }
        if envelope.type == "action_rejected" { errorMessage = envelope.message }
    }

}
