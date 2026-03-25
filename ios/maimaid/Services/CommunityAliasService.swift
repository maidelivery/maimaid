import Foundation
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
    let submitterId: String
    let voteOpenAt: Date?
    let voteCloseAt: Date?
    let supportCount: Int
    let opposeCount: Int
    let myVote: Int?
    let createdAt: Date

    var id: UUID { candidateId }
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
}

nonisolated struct CommunityAliasVoteResult: Codable, Sendable {
    let candidateId: UUID
    let supportCount: Int
    let opposeCount: Int
    let myVote: Int?
}

nonisolated struct CommunityAliasApprovedSyncRow: Codable, Sendable {
    let candidateId: UUID
    let songIdentifier: String
    let aliasText: String
    let updatedAt: Date
    let approvedAt: Date?
}

private struct CommunityAliasSubmitPayload: Encodable {
    let songIdentifier: String
    let aliasText: String
    let deviceLocalDate: String
    let tzOffsetMinutes: Int
}

private struct CommunityAliasVotePayload: Encodable {
    let candidateId: String
    let vote: Int
}

private struct CommunityAliasRowsResponse<Row: Decodable>: Decodable {
    let rows: [Row]
}

private struct CommunityAliasDailyCountResponse: Decodable {
    let count: Int
}

@MainActor
final class CommunityAliasService {
    static let shared = CommunityAliasService()

    private init() {}

    private(set) var lastVoteErrorMessage: String?

    var isConfigured: Bool { BackendSessionManager.shared.isConfigured }
    var isAuthenticated: Bool { BackendSessionManager.shared.isAuthenticated }

    func submitAlias(songIdentifier: String, aliasText: String) async -> CommunityAliasSubmitResponse {
        guard isConfigured else {
            return .init(
                status: .error,
                message: String(localized: "settings.cloud.config.error.unconfigured"),
                candidate: nil,
                existingCandidates: nil,
                similarAliases: nil,
                quotaRemaining: nil
            )
        }

        guard isAuthenticated else {
            return .init(
                status: .unauthenticated,
                message: String(localized: "community.alias.submit.loginRequired"),
                candidate: nil,
                existingCandidates: nil,
                similarAliases: nil,
                quotaRemaining: nil
            )
        }

        let localDate = Date.now.formatted(
            .iso8601
            .year()
            .month()
            .day()
            .dateSeparator(.dash)
        )

        do {
            let payload = CommunityAliasSubmitPayload(
                songIdentifier: songIdentifier,
                aliasText: aliasText,
                deviceLocalDate: localDate,
                tzOffsetMinutes: TimeZone.current.secondsFromGMT() / 60
            )
            let response: CommunityAliasSubmitResponse = try await BackendAPIClient.request(
                path: "v1/community/aliases/submit",
                method: "POST",
                body: payload,
                authentication: .required
            )
            return response
        } catch let apiError as BackendAPIError {
            if apiError.statusCode == 401 {
                return .init(
                    status: .unauthenticated,
                    message: String(localized: "community.alias.submit.loginRequired"),
                    candidate: nil,
                    existingCandidates: nil,
                    similarAliases: nil,
                    quotaRemaining: nil
                )
            }

            return .init(
                status: .error,
                message: apiError.message,
                candidate: nil,
                existingCandidates: nil,
                similarAliases: nil,
                quotaRemaining: nil
            )
        } catch {
            return .init(
                status: .error,
                message: error.localizedDescription,
                candidate: nil,
                existingCandidates: nil,
                similarAliases: nil,
                quotaRemaining: nil
            )
        }
    }

    func fetchVotingBoard(limit: Int = 120, offset: Int = 0) async -> [CommunityAliasVotingBoardItem] {
        guard isConfigured else { return [] }
        let safeLimit = max(1, min(limit, 200))
        let safeOffset = max(0, offset)
        do {
            let response: CommunityAliasRowsResponse<CommunityAliasVotingBoardItem> = try await BackendAPIClient.request(
                path: "v1/community/aliases/voting-board?limit=\(safeLimit)&offset=\(safeOffset)",
                method: "GET",
                authentication: .optional
            )
            return response.rows
        } catch {
            print("CommunityAliasService.fetchVotingBoard failed: \(error)")
            return []
        }
    }

    func fetchMySongCandidates(songIdentifier: String, limit: Int = 50) async -> [CommunityAliasMyCandidate] {
        guard isConfigured, isAuthenticated else { return [] }
        let safeLimit = max(1, min(limit, 200))
        let escapedSongIdentifier = songIdentifier.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? songIdentifier
        do {
            let response: CommunityAliasRowsResponse<CommunityAliasMyCandidate> = try await BackendAPIClient.request(
                path: "v1/community/aliases/my-candidates?songIdentifier=\(escapedSongIdentifier)&limit=\(safeLimit)",
                method: "GET",
                authentication: .required
            )
            return response.rows
        } catch {
            print("CommunityAliasService.fetchMySongCandidates failed: \(error)")
            return []
        }
    }

    func fetchMyDailySubmissionCount(localDate: Date = Date()) async -> Int? {
        guard isConfigured, isAuthenticated else { return nil }
        let formattedDate = localDate.formatted(
            .iso8601
            .year()
            .month()
            .day()
            .dateSeparator(.dash)
        )
        do {
            let response: CommunityAliasDailyCountResponse = try await BackendAPIClient.request(
                path: "v1/community/aliases/daily-count?localDate=\(formattedDate)",
                method: "GET",
                authentication: .required
            )
            return max(0, response.count)
        } catch {
            print("CommunityAliasService.fetchMyDailySubmissionCount failed: \(error)")
            return nil
        }
    }

    func vote(candidateId: UUID, support: Bool) async -> CommunityAliasVoteResult? {
        guard isConfigured, isAuthenticated else {
            lastVoteErrorMessage = String(localized: "community.alias.service.vote.sessionInvalid")
            return nil
        }

        lastVoteErrorMessage = nil

        do {
            let payload = CommunityAliasVotePayload(candidateId: candidateId.uuidString.lowercased(), vote: support ? 1 : -1)
            let response: CommunityAliasVoteResult = try await BackendAPIClient.request(
                path: "v1/community/aliases/vote",
                method: "POST",
                body: payload,
                authentication: .required
            )
            return response
        } catch let apiError as BackendAPIError {
            lastVoteErrorMessage = apiError.message
            return nil
        } catch {
            lastVoteErrorMessage = error.localizedDescription
            return nil
        }
    }

    func syncApprovedAliasesIfNeeded(container: ModelContainer, minimumInterval: TimeInterval = 10 * 60) async {
        if let lastPoll = UserDefaults.app.communityAliasLastPollAt,
           Date.now.timeIntervalSince(lastPoll) < minimumInterval {
            return
        }

        UserDefaults.app.communityAliasLastPollAt = Date.now
        let context = ModelContext(container)
        await syncApprovedAliasesIntoSongs(modelContext: context)
    }

    func syncApprovedAliasesIntoSongs(modelContext: ModelContext, force: Bool = false) async {
        guard isConfigured else { return }

        let rawSince = force ? nil : UserDefaults.app.communityAliasApprovedSyncAt
        let now = Date.now
        let since: Date?
        if let rawSince, rawSince > now.addingTimeInterval(5 * 60) {
            UserDefaults.app.communityAliasApprovedSyncAt = nil
            since = nil
        } else {
            since = rawSince
        }

        let path: String
        if let since {
            let encoded = since.ISO8601Format().addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? since.ISO8601Format()
            path = "v1/community/aliases/approved-sync?since=\(encoded)&limit=1000"
        } else {
            path = "v1/community/aliases/approved-sync?limit=1000"
        }

        let response: CommunityAliasRowsResponse<CommunityAliasApprovedSyncRow>
        do {
            response = try await BackendAPIClient.request(path: path, method: "GET", authentication: .none)
        } catch {
            print("CommunityAliasService.syncApprovedAliasesIntoSongs failed: \(error)")
            return
        }

        let rows = response.rows
        var didMutate = false

        if force {
            let remoteIdSet = Set(rows.map { $0.candidateId.uuidString })
            if reconcileApprovedCacheForForceSync(remoteIdSet: remoteIdSet, modelContext: modelContext) {
                didMutate = true
            }
        }

        guard !rows.isEmpty else {
            if didMutate {
                try? modelContext.save()
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
