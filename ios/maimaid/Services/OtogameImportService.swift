import Foundation
import SwiftData

struct OtogameImportResult: Sendable {
    let fetchedCount: Int
    let importedCount: Int
    let duplicateCount: Int
    let unmatchedCount: Int
}

enum OtogameImportError: LocalizedError {
    case invalidAuthorization
    case unauthorized
    case japaneseProfileRequired
    case invalidResponse
    case requestFailed(Int)

    var errorDescription: String? {
        switch self {
        case .invalidAuthorization, .unauthorized:
            String(localized: "import.otogame.result.loginRequired")
        case .japaneseProfileRequired:
            String(localized: "import.otogame.profileRequired")
        case .invalidResponse:
            String(localized: "import.otogame.result.failed")
        case .requestFailed(let statusCode):
            String(localized: "import.otogame.error.http \(statusCode)")
        }
    }
}

private enum OtogameAPIError: Error {
    case freeHistoryLimitReached
}

@MainActor
private final class OtogameAPIClient {
    private let endpoint = URL(string: "https://u.otogame.net/api/game/maimai/playlog")

    func fetchPlaylogs(authorizationHeader: String, page: Int) async throws -> OtogamePlaylogResponse {
        guard authorizationHeader.trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased().hasPrefix("bearer ") else {
            throw OtogameImportError.invalidAuthorization
        }
        guard let endpoint,
              var components = URLComponents(url: endpoint, resolvingAgainstBaseURL: false) else {
            throw OtogameImportError.invalidResponse
        }
        components.queryItems = [URLQueryItem(name: "page", value: String(page))]
        guard let url = components.url else {
            throw OtogameImportError.invalidResponse
        }

        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        request.timeoutInterval = 15
        request.cachePolicy = .reloadIgnoringLocalCacheData
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.setValue(authorizationHeader, forHTTPHeaderField: "Authorization")
        request.setValue("https://u.otogame.net/maimai/music", forHTTPHeaderField: "Referer")

        let (data, response) = try await URLSession.shared.data(for: request)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw OtogameImportError.invalidResponse
        }
        switch httpResponse.statusCode {
        case 200:
            do {
                return try JSONDecoder().decode(OtogamePlaylogResponse.self, from: data)
            } catch {
                throw OtogameImportError.invalidResponse
            }
        case 401, 403:
            throw OtogameImportError.unauthorized
        case 400 where page == OtogameImportPolicy.fullHistoryProbePage:
            throw OtogameAPIError.freeHistoryLimitReached
        default:
            throw OtogameImportError.requestFailed(httpResponse.statusCode)
        }
    }
}

@MainActor
final class OtogameImportService {
    static let shared = OtogameImportService()

    private let apiClient = OtogameAPIClient()

    private init() {}

    func importRecent(
        authorizationHeader: String,
        expectedProfileID: UUID,
        context: ModelContext
    ) async throws -> OtogameImportResult {
        try validateActiveProfile(expectedID: expectedProfileID, context: context)

        let profileID = expectedProfileID
        let recordDescriptor = FetchDescriptor<PlayRecord>(
            predicate: #Predicate<PlayRecord> { $0.userProfileId == profileID }
        )
        let existingRecordIDs = Set(try context.fetch(recordDescriptor).map(\.id))
        let fetched = try await fetchNewPlaylogs(
            authorizationHeader: authorizationHeader,
            profileID: profileID,
            existingRecordIDs: existingRecordIDs,
            context: context
        )

        try validateActiveProfile(expectedID: profileID, context: context)
        let matcher = OtogameSheetMatcher(
            songs: try context.fetch(FetchDescriptor<Song>()),
            sheets: try context.fetch(FetchDescriptor<Sheet>())
        )
        let mapped = fetched.playlogs.compactMap { pending -> MappedPlaylog? in
            guard let sheet = matcher.match(pending.playlog) else {
                return nil
            }
            let achievement = OtogameImportPolicy.achievement(pending.playlog.achievement)
            let maximumDXScore = (sheet.total ?? 0) * 3
            guard achievement.isFinite,
                  (0...101).contains(achievement),
                  pending.playlog.deluxeScore >= 0,
                  maximumDXScore == 0 || pending.playlog.deluxeScore <= maximumDXScore,
                  pending.playlog.playDate > 0 else {
                return nil
            }
            let rank = OtogameImportPolicy.rank(
                for: pending.playlog.scoreRank,
                achievement: achievement
            )
            return MappedPlaylog(
                id: pending.id,
                sheet: sheet,
                achievement: achievement,
                rank: rank,
                deluxeScore: pending.playlog.deluxeScore,
                fullCombo: OtogameImportPolicy.fullCombo(for: pending.playlog.comboStatus),
                fullSync: OtogameImportPolicy.fullSync(for: pending.playlog.syncStatus),
                playedAt: Date(timeIntervalSince1970: TimeInterval(pending.playlog.playDate))
            )
        }

        try validateActiveProfile(expectedID: profileID, context: context)
        var importedCount = 0
        var duplicateCount = fetched.duplicateCount
        var knownRecordIDs = existingRecordIDs
        for item in mapped {
            guard knownRecordIDs.insert(item.id).inserted else {
                duplicateCount += 1
                continue
            }
            _ = ScoreService.shared.recordPlay(
                id: item.id,
                sheet: item.sheet,
                rate: item.achievement,
                rank: item.rank,
                dxScore: item.deluxeScore,
                fc: item.fullCombo,
                fs: item.fullSync,
                playDate: item.playedAt,
                context: context
            )
            ScoreService.shared.saveScore(
                sheet: item.sheet,
                rate: item.achievement,
                rank: OtogameImportPolicy.calculatedRank(for: item.achievement),
                dxScore: item.deluxeScore,
                fc: item.fullCombo,
                fs: item.fullSync,
                achievementDate: item.playedAt,
                context: context
            )
            importedCount += 1
        }

        do {
            try validateActiveProfile(expectedID: profileID, context: context)
            try context.save()
        } catch {
            context.rollback()
            throw error
        }
        ScoreService.shared.notifyScoresChanged(for: profileID)
		BackendIncrementalSyncService.markDataPending(profileId: profileID, context: context)

        return OtogameImportResult(
            fetchedCount: fetched.fetchedCount,
            importedCount: importedCount,
            duplicateCount: duplicateCount,
            unmatchedCount: fetched.playlogs.count - mapped.count
        )
    }

    private func fetchNewPlaylogs(
        authorizationHeader: String,
        profileID: UUID,
        existingRecordIDs: Set<UUID>,
        context: ModelContext
    ) async throws -> FetchedPlaylogs {
        var playlogs: [PendingPlaylog] = []
        var seenRecordIDs = Set<UUID>()
        var fetchedCount = 0
        var duplicateCount = 0
        var page = 1

        while true {
            try Task.checkCancellation()
            let response: OtogamePlaylogResponse
            do {
                response = try await apiClient.fetchPlaylogs(
                    authorizationHeader: authorizationHeader,
                    page: page
                )
            } catch OtogameAPIError.freeHistoryLimitReached {
                break
            }
            try validateActiveProfile(expectedID: profileID, context: context)
            guard !response.data.data.isEmpty else {
                break
            }

            for playlog in response.data.data {
                fetchedCount += 1
                let recordID = OtogameImportPolicy.stableRecordID(profileID: profileID, playlog: playlog)
                if existingRecordIDs.contains(recordID) {
                    duplicateCount += 1
                    continue
                }
                if seenRecordIDs.insert(recordID).inserted {
                    playlogs.append(PendingPlaylog(id: recordID, playlog: playlog))
                } else {
                    duplicateCount += 1
                }
            }
            if page >= max(response.data.pagination.totalPage, 1) {
                break
            }
            page += 1
        }
        return FetchedPlaylogs(
            playlogs: playlogs,
            fetchedCount: fetchedCount,
            duplicateCount: duplicateCount
        )
    }

    private func validateActiveProfile(expectedID: UUID, context: ModelContext) throws {
        let descriptor = FetchDescriptor<UserProfile>(predicate: #Predicate<UserProfile> { $0.isActive })
        guard let profile = try context.fetch(descriptor).first,
              profile.id == expectedID,
              OtogameImportPolicy.isEligibleServer(profile.server) else {
            throw OtogameImportError.japaneseProfileRequired
        }
    }
}

private struct PendingPlaylog {
    let id: UUID
    let playlog: OtogamePlaylog
}

private struct FetchedPlaylogs {
    let playlogs: [PendingPlaylog]
    let fetchedCount: Int
    let duplicateCount: Int
}

@MainActor
private struct MappedPlaylog {
    let id: UUID
    let sheet: Sheet
    let achievement: Double
    let rank: String
    let deluxeScore: Int
    let fullCombo: String?
    let fullSync: String?
    let playedAt: Date
}
