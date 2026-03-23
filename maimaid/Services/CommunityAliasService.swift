import Foundation
import Supabase
import SwiftData

nonisolated enum CommunityAliasSubmitStatus: String, Codable, Sendable {
    case created
    case rejectedDuplicate = "rejected_duplicate"
    case quotaExceeded = "quota_exceeded"
    case unauthenticated
    case invalidRequest = "invalid_request"
    case error
}

nonisolated struct CommunityAliasSubmitCandidate: Codable, Sendable {
    let id: String
    let songIdentifier: String
    let aliasText: String
    let status: String
    let createdAt: String

    enum CodingKeys: String, CodingKey {
        case id
        case songIdentifier
        case aliasText
        case status
        case createdAt
    }
}

nonisolated struct CommunityAliasExistingCandidate: Codable, Identifiable, Sendable {
    let candidateId: String
    let aliasText: String
    let status: String
    let similarity: Double
    let bucket: String
    let supportCount: Int
    let opposeCount: Int

    var id: String { candidateId }
}

nonisolated struct CommunityAliasSubmitResponse: Codable, Sendable {
    let status: CommunityAliasSubmitStatus
    let message: String
    let candidate: CommunityAliasSubmitCandidate?
    let existingCandidates: [CommunityAliasExistingCandidate]?
    let similarAliases: [String]?
    let quotaRemaining: Int?
}

nonisolated struct CommunityAliasVotingBoardItem: Codable, Identifiable, Sendable {
    let candidateId: UUID
    let songIdentifier: String
    let aliasText: String
    let submitterId: UUID
    let voteOpenAt: Date?
    let voteCloseAt: Date?
    let supportCount: Int
    let opposeCount: Int
    let myVote: Int?
    let createdAt: Date

    var id: UUID { candidateId }

    enum CodingKeys: String, CodingKey {
        case candidateId = "candidate_id"
        case songIdentifier = "song_identifier"
        case aliasText = "alias_text"
        case submitterId = "submitter_id"
        case voteOpenAt = "vote_open_at"
        case voteCloseAt = "vote_close_at"
        case supportCount = "support_count"
        case opposeCount = "oppose_count"
        case myVote = "my_vote"
        case createdAt = "created_at"
    }
}

nonisolated struct CommunityAliasMyCandidate: Codable, Identifiable, Sendable {
    let candidateId: UUID
    let songIdentifier: String
    let aliasText: String
    let status: String
    let voteOpenAt: Date?
    let voteCloseAt: Date?
    let supportCount: Int
    let opposeCount: Int
    let createdAt: Date
    let updatedAt: Date

    var id: UUID { candidateId }

    enum CodingKeys: String, CodingKey {
        case candidateId = "candidate_id"
        case songIdentifier = "song_identifier"
        case aliasText = "alias_text"
        case status
        case voteOpenAt = "vote_open_at"
        case voteCloseAt = "vote_close_at"
        case supportCount = "support_count"
        case opposeCount = "oppose_count"
        case createdAt = "created_at"
        case updatedAt = "updated_at"
    }
}

nonisolated struct CommunityAliasVoteResult: Codable, Sendable {
    let candidateId: UUID
    let supportCount: Int
    let opposeCount: Int
    let myVote: Int

    enum CodingKeys: String, CodingKey {
        case candidateId = "candidate_id"
        case supportCount = "support_count"
        case opposeCount = "oppose_count"
        case myVote = "my_vote"
    }
}

nonisolated struct CommunityAliasApprovedSyncRow: Codable, Sendable {
    let candidateId: UUID
    let songIdentifier: String
    let aliasText: String
    let updatedAt: Date
    let approvedAt: Date?

    enum CodingKeys: String, CodingKey {
        case candidateId = "candidate_id"
        case songIdentifier = "song_identifier"
        case aliasText = "alias_text"
        case updatedAt = "updated_at"
        case approvedAt = "approved_at"
    }
}

nonisolated private struct CommunityAliasSubmitAliasPayload: Codable, Sendable {
    let songIdentifier: String
    let aliasText: String
    let deviceLocalDate: String
    let tzOffsetMinutes: Int
}

nonisolated private struct CommunityAliasVotingBoardParams: Codable, Sendable {
    let p_limit: Int
    let p_offset: Int
}

nonisolated private struct CommunityAliasMySongCandidatesParams: Codable, Sendable {
    let p_song_identifier: String
    let p_limit: Int
}

nonisolated private struct CommunityAliasVoteParams: Codable, Sendable {
    let p_candidate_id: UUID
    let p_vote: Int
}

nonisolated private struct CommunityAliasApprovedSyncParams: Codable, Sendable {
    let p_since: Date?
    let p_limit: Int
}

nonisolated private struct CommunityAliasEdgeErrorPayload: Decodable, Sendable {
    let message: String?
    let error: String?
}

@MainActor
final class CommunityAliasService {
    static let shared = CommunityAliasService()

    private init() {}

    private var manager: SupabaseManager { SupabaseManager.shared }
    private(set) var lastVoteErrorMessage: String?

    var isConfigured: Bool { manager.isConfigured }
    var isAuthenticated: Bool { manager.isAuthenticated }

    private func base64URLDecode(_ value: String) -> Data? {
        var base64 = value
            .replacingOccurrences(of: "-", with: "+")
            .replacingOccurrences(of: "_", with: "/")
        let remainder = base64.count % 4
        if remainder > 0 {
            base64 += String(repeating: "=", count: 4 - remainder)
        }
        return Data(base64Encoded: base64)
    }

    private func tokenIssuerHost(_ token: String) -> String? {
        let segments = token.split(separator: ".")
        guard segments.count >= 2 else { return nil }
        guard let payloadData = base64URLDecode(String(segments[1])) else { return nil }
        guard
            let json = try? JSONSerialization.jsonObject(with: payloadData) as? [String: Any],
            let issuer = json["iss"] as? String,
            let host = URL(string: issuer)?.host
        else {
            return nil
        }
        return host
    }

    private func tokenMatchesCurrentProject(_ token: String) -> Bool {
        guard let expectedHost = SupabaseConfig.projectURL?.host else { return true }
        guard let issuerHost = tokenIssuerHost(token) else { return true }
        return issuerHost == expectedHost
    }

    private func resolveAccessToken(client: SupabaseClient, forceRefresh: Bool = false) async -> String? {
        if forceRefresh, let refreshed = try? await client.auth.refreshSession() {
            await manager.checkSession()
            return tokenMatchesCurrentProject(refreshed.accessToken) ? refreshed.accessToken : nil
        }
        if let refreshed = try? await client.auth.refreshSession() {
            await manager.checkSession()
            return tokenMatchesCurrentProject(refreshed.accessToken) ? refreshed.accessToken : nil
        }
        if let session = try? await client.auth.session {
            return tokenMatchesCurrentProject(session.accessToken) ? session.accessToken : nil
        }
        return nil
    }

    func submitAlias(songIdentifier: String, aliasText: String) async -> CommunityAliasSubmitResponse {
        guard let client = manager.client else {
            return .init(
                status: .error,
                message: String(localized: "community.alias.service.submit.unconfigured"),
                candidate: nil,
                existingCandidates: nil,
                similarAliases: nil,
                quotaRemaining: nil
            )
        }

        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = .current
        formatter.dateFormat = "yyyy-MM-dd"

        let payload = CommunityAliasSubmitAliasPayload(
            songIdentifier: songIdentifier,
            aliasText: aliasText,
            deviceLocalDate: formatter.string(from: Date()),
            tzOffsetMinutes: TimeZone.current.secondsFromGMT() / 60
        )

        guard let accessToken = await resolveAccessToken(client: client) else {
            return .init(
                status: .unauthenticated,
                message: String(localized: "community.alias.service.submit.sessionInvalid"),
                candidate: nil,
                existingCandidates: nil,
                similarAliases: nil,
                quotaRemaining: nil
            )
        }

        do {
            let response: CommunityAliasSubmitResponse = try await client.functions.invoke(
                "community-alias-submit",
                options: FunctionInvokeOptions(
                    headers: ["Authorization": "Bearer \(accessToken)"],
                    body: payload
                )
            )
            return response
        } catch {
            if case let FunctionsError.httpError(code: code, data: data) = error, code == 401 {
                // Auto-recover from stale/invalid JWT once by forcing a refresh and retry.
                if let refreshedToken = await resolveAccessToken(client: client, forceRefresh: true) {
                    do {
                        let retried: CommunityAliasSubmitResponse = try await client.functions.invoke(
                            "community-alias-submit",
                            options: FunctionInvokeOptions(
                                headers: ["Authorization": "Bearer \(refreshedToken)"],
                                body: payload
                            )
                        )
                        return retried
                    } catch {
                        if case let FunctionsError.httpError(code: retryCode, data: retryData) = error, retryCode == 401 {
                            await manager.checkSession()
                            let retryMessage = parseEdgeUnauthorizedMessage(data: retryData)
                            return .init(
                                status: .unauthenticated,
                                message: retryMessage ?? String(localized: "community.alias.service.submit.sessionExpiredRelogin"),
                                candidate: nil,
                                existingCandidates: nil,
                                similarAliases: nil,
                                quotaRemaining: nil
                            )
                        }
                    }
                }

                await manager.checkSession()
                let message = parseEdgeUnauthorizedMessage(data: data)
                return .init(
                    status: .unauthenticated,
                    message: message ?? String(localized: "community.alias.service.submit.sessionExpiredRetry"),
                    candidate: nil,
                    existingCandidates: nil,
                    similarAliases: nil,
                    quotaRemaining: nil
                )
            }

            return .init(
                status: .error,
                message: String(localized: "community.alias.service.submit.requestFailed \(error.localizedDescription)"),
                candidate: nil,
                existingCandidates: nil,
                similarAliases: nil,
                quotaRemaining: nil
            )
        }
    }

    private func parseEdgeUnauthorizedMessage(data: Data) -> String? {
        guard !data.isEmpty else { return nil }
        guard let payload = try? JSONDecoder().decode(CommunityAliasEdgeErrorPayload.self, from: data) else {
            return nil
        }
        return payload.message ?? payload.error
    }

    func fetchVotingBoard(limit: Int = 120, offset: Int = 0) async -> [CommunityAliasVotingBoardItem] {
        guard let client = manager.client else { return [] }

        do {
            let params = CommunityAliasVotingBoardParams(p_limit: max(1, limit), p_offset: max(0, offset))
            let rows: [CommunityAliasVotingBoardItem] = try await client
                .rpc("community_alias_get_voting_board", params: params)
                .execute()
                .value
            return rows
        } catch {
            print("CommunityAliasService.fetchVotingBoard failed: \(error)")
            return []
        }
    }

    func fetchMySongCandidates(songIdentifier: String, limit: Int = 50) async -> [CommunityAliasMyCandidate] {
        guard let client = manager.client else { return [] }
        guard await resolveAccessToken(client: client) != nil else { return [] }

        do {
            let params = CommunityAliasMySongCandidatesParams(
                p_song_identifier: songIdentifier,
                p_limit: max(1, limit)
            )
            let rows: [CommunityAliasMyCandidate] = try await client
                .rpc("community_alias_get_my_song_candidates", params: params)
                .execute()
                .value
            return rows
        } catch {
            print("CommunityAliasService.fetchMySongCandidates failed: \(error)")
            return []
        }
    }

    func vote(candidateId: UUID, support: Bool) async -> CommunityAliasVoteResult? {
        guard let client = manager.client else { return nil }
        guard await resolveAccessToken(client: client) != nil else {
            lastVoteErrorMessage = String(localized: "community.alias.service.vote.sessionInvalid")
            return nil
        }

        lastVoteErrorMessage = nil

        do {
            let params = CommunityAliasVoteParams(p_candidate_id: candidateId, p_vote: support ? 1 : -1)
            let response = try await client
                .rpc("community_alias_vote", params: params)
                .execute()

            if let rows = try? JSONDecoder().decode([CommunityAliasVoteResult].self, from: response.data),
               let first = rows.first {
                return first
            }

            if let single = try? JSONDecoder().decode(CommunityAliasVoteResult.self, from: response.data) {
                return single
            }

            if let text = String(data: response.data, encoding: .utf8),
               !text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                lastVoteErrorMessage = String(localized: "community.alias.service.vote.responseAbnormal \(truncate(text, max: 120))")
            } else {
                lastVoteErrorMessage = String(localized: "community.alias.service.vote.emptyResponse")
            }
            return nil
        } catch {
            let message = mapVoteErrorMessage(error)
            lastVoteErrorMessage = message
            print("CommunityAliasService.vote failed: \(error)")
            print("CommunityAliasService.vote failed detail: \(String(reflecting: error))")
            return nil
        }
    }

    private func mapVoteErrorMessage(_ error: Error) -> String {
        let rawParts = extractVoteErrorParts(error)
        let raw = rawParts.joined(separator: " | ").lowercased()
        if raw.contains("voting window is closed") {
            return String(localized: "community.alias.service.vote.error.windowClosed")
        }
        if raw.contains("candidate is not in voting status") {
            return String(localized: "community.alias.service.vote.error.notVoting")
        }
        if raw.contains("not authenticated") || raw.contains("jwt") || raw.contains("401") {
            return String(localized: "community.alias.service.vote.error.authExpired")
        }
        if raw.contains("could not choose the best candidate function")
            || raw.contains("does not exist")
            || raw.contains("community_alias_vote(") {
            return String(localized: "community.alias.service.vote.error.rpcSignature")
        }
        if raw.contains("candidate_id") && raw.contains("ambiguous") {
            return String(localized: "community.alias.service.vote.error.rpcAmbiguous")
        }
        if raw.contains("permission denied") {
            return String(localized: "community.alias.service.vote.error.permissionDenied")
        }
        if raw.contains("violates row-level security policy") || raw.contains("row-level security") {
            return String(localized: "community.alias.service.vote.error.rls")
        }

        if let firstMeaningful = rawParts.first(where: { !$0.isEmpty && !$0.contains("operation couldn’t be completed") }) {
            return String(localized: "community.alias.service.vote.error.detail \(truncate(firstMeaningful, max: 120))")
        }
        return String(localized: "community.alias.service.vote.error.generic")
    }

    private func extractVoteErrorParts(_ error: Error) -> [String] {
        var parts: [String] = []
        if let postgrest = error as? PostgrestError {
            parts.append(postgrest.message)
            if let detail = postgrest.detail, !detail.isEmpty {
                parts.append(detail)
            }
            if let hint = postgrest.hint, !hint.isEmpty {
                parts.append(hint)
            }
            if let code = postgrest.code, !code.isEmpty {
                parts.append("code=\(code)")
            }
        }

        let localized = error.localizedDescription.trimmingCharacters(in: .whitespacesAndNewlines)
        if !localized.isEmpty {
            parts.append(localized)
        }

        let debug = String(describing: error).trimmingCharacters(in: .whitespacesAndNewlines)
        if !debug.isEmpty {
            parts.append(debug)
        }

        var deduped: [String] = []
        for part in parts {
            if !deduped.contains(part) {
                deduped.append(part)
            }
        }
        return deduped
    }

    private func truncate(_ text: String, max: Int) -> String {
        guard text.count > max else { return text }
        let prefix = text.prefix(max)
        return "\(prefix)…"
    }

    func syncApprovedAliasesIfNeeded(container: ModelContainer, minimumInterval: TimeInterval = 10 * 60) async {
        if let lastPoll = UserDefaults.app.communityAliasLastPollAt,
           Date().timeIntervalSince(lastPoll) < minimumInterval {
            return
        }

        UserDefaults.app.communityAliasLastPollAt = Date()
        let context = ModelContext(container)
        await syncApprovedAliasesIntoSongs(modelContext: context)
    }

    func syncApprovedAliasesIntoSongs(modelContext: ModelContext, force: Bool = false) async {
        guard let client = manager.client else { return }

        let rawSince = force ? nil : UserDefaults.app.communityAliasApprovedSyncAt
        let now = Date()
        let since: Date?
        if let rawSince, rawSince > now.addingTimeInterval(5 * 60) {
            // Recover from previous test/manual fast-forward that wrote future updated_at
            // into local sync watermark and would otherwise block newer rows.
            UserDefaults.app.communityAliasApprovedSyncAt = nil
            since = nil
        } else {
            since = rawSince
        }
        let params = CommunityAliasApprovedSyncParams(p_since: since, p_limit: 1000)

        let rows: [CommunityAliasApprovedSyncRow]
        do {
            rows = try await client
                .rpc("community_alias_sync_approved_since", params: params)
                .execute()
                .value
        } catch {
            print("CommunityAliasService.syncApprovedAliasesIntoSongs failed: \(error)")
            return
        }

        var didMutate = false
        if force {
            let remoteIdSet = Set(rows.map { $0.candidateId.uuidString })
            if reconcileApprovedCacheForForceSync(remoteIdSet: remoteIdSet, modelContext: modelContext) {
                didMutate = true
            }
        }

        guard !rows.isEmpty else {
            if didMutate {
                do {
                    try modelContext.save()
                } catch {
                    print("CommunityAliasService.sync save failed: \(error)")
                }
            }
            return
        }

        var maxUpdatedAt = since ?? .distantPast

        for row in rows {
            let remoteId = row.candidateId.uuidString
            let cache = fetchCache(remoteId: remoteId, modelContext: modelContext)
                ?? CommunityAliasCache(
                    remoteId: remoteId,
                    songIdentifier: row.songIdentifier,
                    aliasText: row.aliasText,
                    status: "approved",
                    voteOpenAt: nil,
                    voteCloseAt: nil,
                    approvedAt: row.approvedAt,
                    updatedAt: row.updatedAt,
                    createdAt: row.approvedAt ?? row.updatedAt
                )

            cache.songIdentifier = row.songIdentifier
            cache.aliasText = row.aliasText
            cache.status = "approved"
            cache.approvedAt = row.approvedAt
            cache.updatedAt = row.updatedAt
            didMutate = true

            if cache.modelContext == nil {
                modelContext.insert(cache)
            }

            if let song = fetchSong(songIdentifier: row.songIdentifier, modelContext: modelContext) {
                let exists = song.aliases.contains {
                    $0.localizedCaseInsensitiveCompare(row.aliasText) == .orderedSame
                }
                if !exists {
                    song.aliases.append(row.aliasText)
                    didMutate = true
                }
            }

            if row.updatedAt > maxUpdatedAt {
                maxUpdatedAt = row.updatedAt
            }
        }

        if didMutate {
            do {
                try modelContext.save()
                if maxUpdatedAt != .distantPast {
                    UserDefaults.app.communityAliasApprovedSyncAt = min(maxUpdatedAt, now)
                }
            } catch {
                print("CommunityAliasService.sync save failed: \(error)")
            }
        } else if maxUpdatedAt != .distantPast {
            // Even when nothing changed locally, keep incremental watermark moving.
            UserDefaults.app.communityAliasApprovedSyncAt = min(maxUpdatedAt, now)
        }
    }

    private func reconcileApprovedCacheForForceSync(
        remoteIdSet: Set<String>,
        modelContext: ModelContext
    ) -> Bool {
        let approvedStatus = "approved"
        let descriptor = FetchDescriptor<CommunityAliasCache>(
            predicate: #Predicate { item in
                item.status == approvedStatus
            }
        )
        guard let localApproved = try? modelContext.fetch(descriptor), !localApproved.isEmpty else {
            return false
        }

        var changed = false
        for item in localApproved where !remoteIdSet.contains(item.remoteId) {
            modelContext.delete(item)
            changed = true
        }
        return changed
    }

    private func fetchCache(remoteId: String, modelContext: ModelContext) -> CommunityAliasCache? {
        let descriptor = FetchDescriptor<CommunityAliasCache>(
            predicate: #Predicate { item in
                item.remoteId == remoteId
            }
        )
        return try? modelContext.fetch(descriptor).first
    }

    private func fetchSong(songIdentifier: String, modelContext: ModelContext) -> Song? {
        let descriptor = FetchDescriptor<Song>(
            predicate: #Predicate { song in
                song.songIdentifier == songIdentifier
            }
        )
        return try? modelContext.fetch(descriptor).first
    }
}
