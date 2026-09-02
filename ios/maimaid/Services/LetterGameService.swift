import Foundation
import Observation

// The service keeps the REST and WebSocket lifecycle together to serialize room state.
// swiftlint:disable file_length type_body_length

@MainActor
@Observable
final class LetterGameService {
    private(set) var publicRooms: [LetterGameRoom] = []
    private(set) var room: LetterGameRoom?
    private(set) var match: LetterGameMatchSnapshot?
    private(set) var isLoading = false
    private(set) var isConnected = false
    var errorMessage = ""
    var isShowingError = false

    private var socket: URLSessionWebSocketTask?
    private var receiveTask: Task<Void, Never>?
    private var hiddenFinishedMatchId: String?
    private var isLeavingRoom = false
    private let savedRoomCodeKey = "letterGame.savedRoomCode"

    var currentUserId: String? { BackendSessionManager.shared.currentUser?.id }
    var isAuthenticated: Bool { BackendSessionManager.shared.isAuthenticated }
    var isHost: Bool { room?.hostUserId == currentUserId }
    var isCurrentTurn: Bool { match?.turnUserId == currentUserId }

    func run() async {
        await BackendSessionManager.shared.checkSession()
        guard isAuthenticated else { return }
        if let savedCode = UserDefaults.standard.string(forKey: savedRoomCodeKey) {
            await restoreRoom(code: savedCode)
        } else {
            await refreshPublicRooms()
        }

        while !Task.isCancelled {
            do {
                try await Task.sleep(for: .seconds(5))
            } catch {
                break
            }
            if room == nil {
                await refreshPublicRooms(silently: true)
            } else {
                await refreshCurrentRoom()
            }
        }
    }

    func refreshPublicRooms(silently: Bool = false) async {
        if !silently { isLoading = true }
        defer { if !silently { isLoading = false } }
        do {
            struct Response: Decodable { let rooms: [LetterGameRoom] }
            let response: Response = try await BackendAPIClient.request(path: "v1/letter-game/rooms")
            publicRooms = response.rooms
        } catch {
            if !silently { present(error) }
        }
    }

    func createRoom(visibility: String) async {
        await performLoadingAction {
            struct Response: Decodable { let room: LetterGameRoom }
            let request = LetterGameCreateRequest(visibility: visibility)
            let response: Response = try await BackendAPIClient.request(path: "v1/letter-game/rooms", body: request)
            enter(response.room)
        }
    }

    func join(code: String) async {
        await performLoadingAction {
            struct Request: Encodable { let code: String }
            struct Response: Decodable { let room: LetterGameRoom }
            let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let response: Response = try await BackendAPIClient.request(
                path: "v1/letter-game/rooms:join",
                body: Request(code: normalized)
            )
            enter(response.room)
        }
    }

    func startMatch() async {
        guard let room else { return }
        await performLoadingAction {
            struct Response: Decodable { let match: LetterGameMatchSnapshot }
            let response: Response = try await BackendAPIClient.request(
                path: "v1/letter-game/rooms/\(room.id)/start",
                method: "POST"
            )
            hiddenFinishedMatchId = nil
            match = response.match
        }
    }

    func updateRoom(_ request: LetterGameCreateRequest) async -> Bool {
        guard let room else { return false }
        do {
            struct Response: Decodable { let room: LetterGameRoom }
            let response: Response = try await BackendAPIClient.request(
                path: "v1/letter-game/rooms/\(room.id)",
                method: "PATCH",
                body: request
            )
            self.room = response.room
            return true
        } catch {
            present(error)
            return false
        }
    }

    func approve(_ member: LetterGameRoomMember) async { await updateMember(member, action: "approve") }
    func reject(_ member: LetterGameRoomMember) async { await updateMember(member, action: "reject") }
    func kick(_ member: LetterGameRoomMember) async { await updateMember(member, action: "kick") }

    func reopenRoom() async {
        guard let room else { return }
        let finishedMatchId = match?.matchId
        await performLoadingAction {
            struct Response: Decodable { let room: LetterGameRoom }
            let response: Response = try await BackendAPIClient.request(
                path: "v1/letter-game/rooms/\(room.id)/reopen",
                method: "POST"
            )
            self.room = response.room
            hiddenFinishedMatchId = finishedMatchId
            match = nil
        }
    }

    func leaveRoom() async {
        guard let room, !isLeavingRoom else { return }
        isLeavingRoom = true
        isLoading = true
        defer {
            isLeavingRoom = false
            isLoading = false
        }
        do {
            _ = try await BackendAPIClient.requestData(
                path: "v1/letter-game/rooms/\(room.id)/leave",
                method: "POST",
                body: Optional<String>.none
            )
            clearRoom()
            await refreshPublicRooms(silently: true)
        } catch {
            present(error)
        }
    }

    func sendInput(_ input: String) async {
        guard let action = LetterGameInputAction(input: input) else { return }
        await sendAction(payload: action.payload)
    }

    func buyHint(song: LetterGameMatchSong, type: String, visibility: String) async {
        await sendAction(payload: [
            "kind": .string("buy_hint"),
            "slotId": .string(song.slotId),
            "hintType": .string(type),
            "visibility": .string(visibility)
        ])
    }

    func disconnect() {
        receiveTask?.cancel()
        receiveTask = nil
        socket?.cancel(with: .goingAway, reason: nil)
        socket = nil
        isConnected = false
    }

    private func restoreRoom(code: String) async {
        do {
            struct Response: Decodable { let room: LetterGameRoom }
            let normalized = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            let response: Response = try await BackendAPIClient.request(path: "v1/letter-game/rooms/\(normalized)")
            guard hasCurrentMembership(in: response.room) else {
                clearRoom()
                return
            }
            enter(response.room)
            await refreshLatestMatch()
        } catch {
            clearRoom()
        }
    }

    private func refreshCurrentRoom() async {
        guard let room else { return }
        let removalMessage = membershipRemovalMessage(for: room)
        do {
            struct Response: Decodable { let room: LetterGameRoom }
            let response: Response = try await BackendAPIClient.request(path: "v1/letter-game/rooms/\(room.id)")
            guard hasCurrentMembership(in: response.room) else {
                clearRoom()
                if !isLeavingRoom { presentMessage(removalMessage) }
                return
            }
            self.room = response.room
            connectIfAllowed()
            await refreshLatestMatch()
        } catch let error as BackendAPIError where [403, 404, 410].contains(error.statusCode) {
            clearRoom()
            if !isLeavingRoom { presentMessage(removalMessage) }
        } catch {
            // WebSocket state remains authoritative during transient polling failures.
        }
    }

    private func refreshLatestMatch() async {
        guard let latest = room?.latestMatch else { return }
        if latest.id == hiddenFinishedMatchId, latest.status != "active" { return }
        guard LetterGameMatchPresentationPolicy.shouldAccept(
            status: latest.status,
            matchId: latest.id,
            trackedMatchId: match?.matchId
        ) else { return }
        if match?.matchId == latest.id, match?.revision == latest.revision { return }
        await refreshMatch(id: latest.id)
    }

    private func refreshMatch(id: String) async {
        do {
            struct Response: Decodable { let match: LetterGameMatchSnapshot }
            let response: Response = try await BackendAPIClient.request(path: "v1/letter-game/matches/\(id)")
            acceptMatch(response.match)
        } catch {
            present(error)
        }
    }

    private func updateMember(_ member: LetterGameRoomMember, action: String) async {
        guard let room, let memberId = member.memberId else { return }
        await performLoadingAction {
            struct Response: Decodable { let room: LetterGameRoom }
            let response: Response = try await BackendAPIClient.request(
                path: "v1/letter-game/rooms/\(room.id)/members/\(memberId)/\(action)",
                method: "POST"
            )
            self.room = response.room
        }
    }

    private func hasCurrentMembership(in room: LetterGameRoom) -> Bool {
        room.members.contains {
            $0.userId == currentUserId && ["accepted", "pending"].contains($0.status)
        }
    }

    private func enter(_ room: LetterGameRoom) {
        self.room = room
        UserDefaults.standard.set(room.code, forKey: savedRoomCodeKey)
        connectIfAllowed()
    }

    private func clearRoom() {
        disconnect()
        room = nil
        match = nil
        hiddenFinishedMatchId = nil
        UserDefaults.standard.removeObject(forKey: savedRoomCodeKey)
    }

    private func connectIfAllowed() {
        guard let room,
              room.members.contains(where: { $0.userId == currentUserId && $0.status == "accepted" }),
              socket == nil,
              let baseURL = BackendConfig.baseURL,
              let token = BackendSessionManager.shared.accessTokenForRequest() else { return }

        var components = URLComponents(
            url: baseURL.appending(path: "v1/letter-game/rooms/\(room.code)/ws"),
            resolvingAgainstBaseURL: false
        )
        components?.scheme = baseURL.scheme == "https" ? "wss" : "ws"
        guard let url = components?.url else { return }

        var request = URLRequest(url: url)
        request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        let task = URLSession.shared.webSocketTask(with: request)
        socket = task
        task.resume()
        isConnected = true
        receiveTask = Task { [weak self] in
            await self?.receiveMessages(from: task)
        }
        if let match {
            Task { [weak self] in
                await self?.sendResume(match: match)
            }
        }
    }

    private func receiveMessages(from task: URLSessionWebSocketTask) async {
        while !Task.isCancelled {
            do {
                let message = try await task.receive()
                switch message {
                case .string(let text):
                    consume(Data(text.utf8))
                case .data(let data):
                    consume(data)
                @unknown default:
                    continue
                }
            } catch {
                if socket === task {
                    socket = nil
                    isConnected = false
                }
                break
            }
        }
    }

    // swiftlint:disable:next cyclomatic_complexity
    private func consume(_ data: Data) {
        struct Envelope: Decodable {
            let type: String
            let room: LetterGameRoom?
            let match: LetterGameMatchSnapshot?
            let code: String?
            let message: String?
            let reason: String?
        }
        guard let envelope = try? BackendAPIClient.decoder.decode(Envelope.self, from: data) else { return }
        switch envelope.type {
        case "room_snapshot":
            if let updated = envelope.room, updated.id == room?.id {
                guard hasCurrentMembership(in: updated) else {
                    let removalMessage = membershipRemovalMessage(for: room)
                    clearRoom()
                    if !isLeavingRoom { presentMessage(removalMessage) }
                    return
                }
                room = updated
            }
        case "match_snapshot":
            if let updated = envelope.match { acceptMatch(updated) }
        case "member_removed", "room_dissolved":
            let shouldPresentRemoval = !isLeavingRoom
            clearRoom()
            if envelope.type == "room_dissolved", shouldPresentRemoval {
                presentMessage(String(localized: "letterGame.dissolved"))
            } else if envelope.type == "member_removed", shouldPresentRemoval {
                presentMessage(memberRemovalMessage(reason: envelope.reason))
            }
        case "action_rejected":
            let key: String? = if envelope.code == "hint_already_known" {
                "letterGame.hintKnown"
            } else if envelope.code == "ambiguous_song_guess" {
                "letterGame.ambiguousGuess"
            } else {
                nil
            }
            let localizedMessage = key.map { String(localized: String.LocalizationValue($0)) }
            presentMessage(localizedMessage ?? envelope.message ?? envelope.code ?? "")
            if envelope.code == "stale_revision", let matchId = match?.matchId {
                Task { [weak self] in
                    await self?.refreshMatch(id: matchId)
                }
            }
        default:
            break
        }
    }

    private func acceptMatch(_ updated: LetterGameMatchSnapshot) {
        guard updated.matchId != hiddenFinishedMatchId || updated.status == "active",
              LetterGameMatchPresentationPolicy.shouldAccept(
                  status: updated.status,
                  matchId: updated.matchId,
                  trackedMatchId: match?.matchId
              ) else { return }
        if updated.status == "active" { hiddenFinishedMatchId = nil }
        match = updated
    }

    private func sendResume(match: LetterGameMatchSnapshot) async {
        await sendSocketMessage(
            LetterGameResumeMessage(type: "resume", matchId: match.matchId, lastRevision: match.revision)
        )
    }

    private func sendAction(payload: [String: LetterGameJSONValue]) async {
        guard let match else { return }
        await sendSocketMessage(
            LetterGameActionMessage(
                type: "action",
                matchId: match.matchId,
                actionId: UUID().uuidString,
                expectedRevision: match.revision,
                payload: payload
            )
        )
    }

    private func sendSocketMessage<Message: Encodable>(_ message: Message) async {
        guard let socket else {
            presentMessage(String(localized: "letterGame.disconnected"))
            return
        }
        do {
            let data = try BackendAPIClient.encoder.encode(message)
            guard let text = String(bytes: data, encoding: .utf8) else { return }
            try await socket.send(.string(text))
        } catch {
            present(error)
        }
    }

    private func performLoadingAction(_ action: () async throws -> Void) async {
        isLoading = true
        defer { isLoading = false }
        do { try await action() } catch { present(error) }
    }

    private func present(_ error: Error) {
        presentMessage(error.localizedDescription)
    }

    private func membershipRemovalMessage(for room: LetterGameRoom?) -> String {
        let status = room?.members.first(where: { $0.userId == currentUserId })?.status
        let key = status == "pending" ? "letterGame.joinRejected" : "letterGame.kicked"
        return String(localized: String.LocalizationValue(key))
    }

    private func memberRemovalMessage(reason: String?) -> String {
        let key = switch reason {
        case "left": "letterGame.exited"
        case "rejected": "letterGame.joinRejected"
        default: "letterGame.kicked"
        }
        return String(localized: String.LocalizationValue(key))
    }

    private func presentMessage(_ message: String) {
        guard !message.isEmpty else { return }
        errorMessage = message
        isShowingError = true
    }
}

private struct LetterGameResumeMessage: Encodable {
    let type: String
    let matchId: String
    let lastRevision: Int
}

private struct LetterGameActionMessage: Encodable {
    let type: String
    let matchId: String
    let actionId: String
    let expectedRevision: Int
    let payload: [String: LetterGameJSONValue]
}

// swiftlint:enable file_length type_body_length
