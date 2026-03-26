import Foundation
import SwiftData

private struct BackendSyncPushResponse: Decodable {
    let latestRevision: String
}

private struct BackendProfileDeleteResponse: Decodable {
    let profileId: String
}

private struct BackendSyncPullResponse: Decodable {
    let events: [BackendSyncEvent]
    let latestRevision: String
    let snapshot: BackendSyncSnapshot
}

private struct BackendSyncEvent: Decodable {
    let revision: String
    let profileId: String?
    let entityType: String
    let entityId: String
    let op: String
    let createdAt: Date
}

private struct BackendSyncSnapshot: Decodable {
    let profiles: [BackendSyncRemoteProfile]
    let scores: [BackendSyncRemoteScore]
    let records: [BackendSyncRemotePlayRecord]
}

private struct BackendSyncRemoteProfile: Codable {
    let id: String
    let name: String
    let server: String
    let avatarUrl: String?
    let isActive: Bool
    let playerRating: Int
    let plate: String?
    let dfUsername: String
    let dfImportToken: String
    let lxnsRefreshToken: String
    let b35Count: Int
    let b15Count: Int
    let b35RecLimit: Int
    let b15RecLimit: Int
    let createdAt: Date
    let lastImportDateDf: Date?
    let lastImportDateLxns: Date?
}

private struct BackendSyncRemoteSheet: Decodable {
    let songIdentifier: String
    let songId: Int
    let chartType: String
    let difficulty: String
    let song: BackendSyncRemoteSong?
}

private struct BackendSyncRemoteSong: Decodable {
    let title: String
}

private struct BackendSyncRemoteScore: Decodable {
    let profileId: String
    let achievements: BackendSyncFlexibleDouble
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let achievedAt: Date
    let sheet: BackendSyncRemoteSheet?
}

private struct BackendSyncRemotePlayRecord: Decodable {
    let profileId: String
    let achievements: BackendSyncFlexibleDouble
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let playTime: Date
    let sheet: BackendSyncRemoteSheet?
}

private struct BackendSyncFlexibleDouble: Decodable {
    let value: Double

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let number = try? container.decode(Double.self) {
            value = number
            return
        }
        if let intValue = try? container.decode(Int.self) {
            value = Double(intValue)
            return
        }
        if let string = try? container.decode(String.self), let parsed = Double(string) {
            value = parsed
            return
        }
        throw DecodingError.typeMismatch(
            Double.self,
            .init(codingPath: decoder.codingPath, debugDescription: "Expected numeric value.")
        )
    }
}

private struct BackendSyncProfileUpsertPayload: Encodable {
    let profileId: String
    let name: String
    let server: String
    let isActive: Bool
    let playerRating: Int
    let plate: String?
    let avatarUrl: String?
    let dfUsername: String
    let dfImportToken: String
    let lxnsRefreshToken: String
    let b35Count: Int
    let b15Count: Int
    let b35RecLimit: Int
    let b15RecLimit: Int
    let createdAt: Date
    let clientUpdatedAt: Date?
}

private struct BackendSyncScoreEntry: Encodable {
    let songIdentifier: String?
    let songId: Int?
    let title: String?
    let type: String?
    let difficulty: String?
    let levelIndex: Int?
    let achievements: Double
    let rank: String?
    let dxScore: Int?
    let fc: String?
    let fs: String?
    let achievedAt: String?
}

private struct BackendSyncPlayRecordEntry: Encodable {
    let songIdentifier: String?
    let songId: Int?
    let title: String?
    let type: String?
    let difficulty: String?
    let levelIndex: Int?
    let achievements: Double
    let rank: String?
    let dxScore: Int?
    let fc: String?
    let fs: String?
    let playTime: String?
}

private struct BackendSyncScoreSet: Encodable {
    let profileId: String
    let scores: [BackendSyncScoreEntry]
}

private struct BackendSyncRecordSet: Encodable {
    let profileId: String
    let records: [BackendSyncPlayRecordEntry]
}

private struct BackendSyncPushPayload: Encodable {
    let idempotencyKey: String
    let profileUpserts: [BackendSyncProfileUpsertPayload]
    let scoreUpserts: [BackendSyncScoreSet]
    let playRecordUpserts: [BackendSyncRecordSet]
}

@MainActor
enum BackendIncrementalSyncService {
    static func pushScoreUpdate(profile: UserProfile, sheet: Sheet, score: Score) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }
        let profileId = profile.id.uuidString.lowercased()
        let chartType = sheet.type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let difficulty = sheet.difficulty.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let levelIndex = ThemeUtils.mapDifficultyToIndex(sheet.difficulty)
        let songId = sheet.song?.songId ?? 0
        let normalizedSongId = songId > 0 ? songId : nil

        let payload = BackendSyncPushPayload(
            idempotencyKey: UUID().uuidString.lowercased(),
            profileUpserts: [],
            scoreUpserts: [
                BackendSyncScoreSet(
                    profileId: profileId,
                    scores: [
                        BackendSyncScoreEntry(
                            songIdentifier: sheet.songIdentifier,
                            songId: normalizedSongId,
                            title: sheet.song?.title,
                            type: chartType,
                            difficulty: difficulty,
                            levelIndex: levelIndex,
                            achievements: score.rate,
                            rank: score.rank,
                            dxScore: score.dxScore,
                            fc: score.fc,
                            fs: score.fs,
                            achievedAt: score.achievementDate.ISO8601Format()
                        )
                    ]
                )
            ],
            playRecordUpserts: [
                BackendSyncRecordSet(
                    profileId: profileId,
                    records: [
                        BackendSyncPlayRecordEntry(
                            songIdentifier: sheet.songIdentifier,
                            songId: normalizedSongId,
                            title: sheet.song?.title,
                            type: chartType,
                            difficulty: difficulty,
                            levelIndex: levelIndex,
                            achievements: score.rate,
                            rank: score.rank,
                            dxScore: score.dxScore,
                            fc: score.fc,
                            fs: score.fs,
                            playTime: Date.now.ISO8601Format()
                        )
                    ]
                )
            ]
        )

        let response: BackendSyncPushResponse = try await BackendAPIClient.request(
            path: "v1/sync/push",
            method: "POST",
            body: payload,
            authentication: .required
        )
        if let context = profile.modelContext {
            let config = ensureSyncConfig(context: context)
            config.lastSyncRevision = response.latestRevision
            try context.save()
            try await pullUpdates(context: context, profileId: profile.id, force: false)
        }
    }

    static func pushProfileUpdate(profile: UserProfile, clientUpdatedAt: Date?) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }
        let profileId = profile.id.uuidString.lowercased()
        let resolvedAvatarURL = try await BackendCloudSyncService.uploadAvatarIfNeeded(for: profile)

        let payload = BackendSyncPushPayload(
            idempotencyKey: UUID().uuidString.lowercased(),
            profileUpserts: [
                BackendSyncProfileUpsertPayload(
                    profileId: profileId,
                    name: profile.name,
                    server: profile.server,
                    isActive: profile.isActive,
                    playerRating: profile.playerRating,
                    plate: profile.plate,
                    avatarUrl: resolvedAvatarURL,
                    dfUsername: profile.dfUsername,
                    dfImportToken: profile.dfImportToken,
                    lxnsRefreshToken: profile.lxnsRefreshToken,
                    b35Count: profile.b35Count,
                    b15Count: profile.b15Count,
                    b35RecLimit: profile.b35RecLimit,
                    b15RecLimit: profile.b15RecLimit,
                    createdAt: profile.createdAt,
                    clientUpdatedAt: clientUpdatedAt
                )
            ],
            scoreUpserts: [],
            playRecordUpserts: []
        )

        let response: BackendSyncPushResponse = try await BackendAPIClient.request(
            path: "v1/sync/push",
            method: "POST",
            body: payload,
            authentication: .required
        )
        guard let context = profile.modelContext else {
            return
        }
        let config = ensureSyncConfig(context: context)
        config.lastSyncRevision = response.latestRevision
        try context.save()
        try await pullUpdates(context: context, profileId: profile.id, force: false)
    }

    static func pullUpdates(context: ModelContext, profileId: UUID? = nil, force: Bool = false) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }
        let config = ensureSyncConfig(context: context)
        let since = force ? "0" : config.lastSyncRevision
        var queryItems = [URLQueryItem(name: "sinceRevision", value: since)]
        if let profileId {
            queryItems.append(URLQueryItem(name: "profileId", value: profileId.uuidString.lowercased()))
        }
        let query = queryItems
            .compactMap { item in
                guard let value = item.value?.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) else {
                    return nil
                }
                return "\(item.name)=\(value)"
            }
            .joined(separator: "&")

        let response: BackendSyncPullResponse = try await BackendAPIClient.request(
            path: "v1/sync/pull?\(query)",
            method: "GET",
            authentication: .required
        )

        try applyProfileDeleteEvents(response.events, context: context)
        try await applySnapshot(response.snapshot, context: context)
        config.lastSyncRevision = response.latestRevision
        try context.save()
        ScoreService.shared.invalidateAllCaches()
    }

    static func deleteProfile(profileId: UUID, context: ModelContext) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }
        let escapedProfileId = profileId.uuidString.lowercased().addingPercentEncoding(withAllowedCharacters: .urlPathAllowed)
            ?? profileId.uuidString.lowercased()
        let _: BackendProfileDeleteResponse = try await BackendAPIClient.request(
            path: "v1/profiles/\(escapedProfileId)",
            method: "DELETE",
            authentication: .required
        )
        try? await pullUpdates(context: context, profileId: profileId, force: false)
    }

    private static func applyProfileDeleteEvents(_ events: [BackendSyncEvent], context: ModelContext) throws {
        let deletedProfileIds: Set<UUID> = Set(
            events.compactMap { event -> UUID? in
                guard event.entityType == "profile", event.op == "delete" else {
                    return nil
                }
                if let profileId = event.profileId, let uuid = UUID(uuidString: profileId) {
                    return uuid
                }
                return UUID(uuidString: event.entityId)
            }
        )

        guard !deletedProfileIds.isEmpty else {
            return
        }

        for profileId in deletedProfileIds {
            let scoreDescriptor = FetchDescriptor<Score>(predicate: #Predicate { $0.userProfileId == profileId })
            let recordDescriptor = FetchDescriptor<PlayRecord>(predicate: #Predicate { $0.userProfileId == profileId })
            let profileDescriptor = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.id == profileId })

            if let scores = try? context.fetch(scoreDescriptor) {
                for score in scores {
                    context.delete(score)
                }
            }
            if let records = try? context.fetch(recordDescriptor) {
                for record in records {
                    context.delete(record)
                }
            }
            if let profile = (try? context.fetch(profileDescriptor))?.first {
                context.delete(profile)
            }
        }
    }

    private static func applySnapshot(_ snapshot: BackendSyncSnapshot, context: ModelContext) async throws {
        guard !snapshot.profiles.isEmpty else {
            return
        }

        let profilePairs: [(UUID, BackendSyncRemoteProfile)] = snapshot.profiles.compactMap {
            guard let uuid = UUID(uuidString: $0.id) else { return nil }
            return (uuid, $0)
        }
        let profileMap = Dictionary(uniqueKeysWithValues: profilePairs)
        let profileIds = Set(profileMap.keys)
        if profileIds.isEmpty {
            return
        }

        let existingProfiles = try context.fetch(FetchDescriptor<UserProfile>())
        let existingById = Dictionary(uniqueKeysWithValues: existingProfiles.map { ($0.id, $0) })

        for (profileId, remote) in profileMap {
            let profile: UserProfile
            if let existing = existingById[profileId] {
                existing.name = remote.name
                existing.server = remote.server
                existing.avatarUrl = remote.avatarUrl
                existing.isActive = remote.isActive
                existing.playerRating = remote.playerRating
                existing.plate = remote.plate
                existing.dfUsername = remote.dfUsername
                existing.dfImportToken = remote.dfImportToken
                existing.lxnsRefreshToken = remote.lxnsRefreshToken
                existing.b35Count = remote.b35Count
                existing.b15Count = remote.b15Count
                existing.b35RecLimit = remote.b35RecLimit
                existing.b15RecLimit = remote.b15RecLimit
                existing.lastImportDateDF = remote.lastImportDateDf
                existing.lastImportDateLXNS = remote.lastImportDateLxns
                profile = existing
            } else {
                let created = UserProfile(
                    id: profileId,
                    name: remote.name,
                    server: remote.server,
                    avatarData: nil,
                    avatarUrl: remote.avatarUrl,
                    isActive: remote.isActive,
                    createdAt: remote.createdAt,
                    dfUsername: remote.dfUsername,
                    dfImportToken: remote.dfImportToken,
                    lxnsRefreshToken: remote.lxnsRefreshToken,
                    playerRating: remote.playerRating,
                    plate: remote.plate,
                    lastImportDateDF: remote.lastImportDateDf,
                    lastImportDateLXNS: remote.lastImportDateLxns,
                    b35Count: remote.b35Count,
                    b15Count: remote.b15Count,
                    b35RecLimit: remote.b35RecLimit,
                    b15RecLimit: remote.b15RecLimit
                )
                context.insert(created)
                profile = created
            }

            if let avatarData = await downloadAvatarData(from: remote.avatarUrl) {
                profile.avatarData = avatarData
            }
        }

        if let syncedActiveProfileId = profilePairs.first(where: { $0.1.isActive })?.0 {
            let allProfiles = try context.fetch(FetchDescriptor<UserProfile>())
            for profile in allProfiles {
                profile.isActive = (profile.id == syncedActiveProfileId)
            }
        }

        let localScores = try context.fetch(FetchDescriptor<Score>())
        for score in localScores {
            if let userProfileId = score.userProfileId, profileIds.contains(userProfileId) {
                context.delete(score)
            }
        }
        let localRecords = try context.fetch(FetchDescriptor<PlayRecord>())
        for record in localRecords {
            if let userProfileId = record.userProfileId, profileIds.contains(userProfileId) {
                context.delete(record)
            }
        }

        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = buildSheetMap(for: sheets, separators: ["-", "_"])

        let resolvedRecords = snapshot.records.compactMap { remoteRecord -> (UUID, Sheet, BackendSyncRemotePlayRecord)? in
            guard let profileId = UUID(uuidString: remoteRecord.profileId) else { return nil }
            guard profileIds.contains(profileId) else { return nil }
            guard let sheet = resolveSheet(for: remoteRecord.sheet, sheetMap: recordSheetMap) else { return nil }
            return (profileId, sheet, remoteRecord)
        }

        let resolvedScores = snapshot.scores.compactMap { remoteScore -> (UUID, Sheet, BackendSyncRemoteScore)? in
            guard let profileId = UUID(uuidString: remoteScore.profileId) else { return nil }
            guard profileIds.contains(profileId) else { return nil }
            guard let sheet = resolveSheet(for: remoteScore.sheet, sheetMap: scoreSheetMap) else { return nil }
            return (profileId, sheet, remoteScore)
        }

        if !snapshot.records.isEmpty && resolvedRecords.isEmpty {
            throw BackendAPIError(
                statusCode: nil,
                code: "sync_sheet_mapping_failed",
                message: "Failed to map remote play records to local sheets."
            )
        }
        if !snapshot.scores.isEmpty && resolvedScores.isEmpty {
            throw BackendAPIError(
                statusCode: nil,
                code: "sync_sheet_mapping_failed",
                message: "Failed to map remote scores to local sheets."
            )
        }

        for (profileId, sheet, remoteRecord) in resolvedRecords {
            let playRecord = PlayRecord(
                sheetId: "\(sheet.songIdentifier)-\(sheet.type)-\(sheet.difficulty)",
                rate: remoteRecord.achievements.value,
                rank: remoteRecord.rank,
                dxScore: remoteRecord.dxScore,
                fc: remoteRecord.fc,
                fs: remoteRecord.fs,
                playDate: remoteRecord.playTime,
                userProfileId: profileId
            )
            playRecord.sheet = sheet
            context.insert(playRecord)
            if sheet.playRecords == nil {
                sheet.playRecords = []
            }
            sheet.playRecords?.append(playRecord)
        }

        for (profileId, sheet, remoteScore) in resolvedScores {
            let score = Score(
                sheetId: "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)",
                rate: remoteScore.achievements.value,
                rank: remoteScore.rank,
                dxScore: remoteScore.dxScore,
                fc: remoteScore.fc,
                fs: remoteScore.fs,
                achievementDate: remoteScore.achievedAt,
                userProfileId: profileId
            )
            score.sheet = sheet
            context.insert(score)
            sheet.scores.append(score)
        }
    }

    private static func ensureSyncConfig(context: ModelContext) -> SyncConfig {
        if let config = try? context.fetch(FetchDescriptor<SyncConfig>()).first {
            return config
        }
        let config = SyncConfig()
        context.insert(config)
        return config
    }

    private static func buildSheetMap(for sheets: [Sheet], separators: [String]) -> [String: Sheet] {
        var map: [String: Sheet] = [:]
        for sheet in sheets {
            let identifiers = candidateSongIdentifiers(for: sheet)
            let chartTypeCandidates = normalizeChartTypeCandidates(sheet.type)
            let difficultyCandidates = normalizeDifficultyCandidates(sheet.difficulty)
            for identifier in identifiers {
                for separator in separators {
                    for chartType in chartTypeCandidates {
                        for difficulty in difficultyCandidates {
                            let key = "\(identifier)\(separator)\(chartType)\(separator)\(difficulty)"
                            map[key] = sheet
                        }
                    }
                }
            }
        }
        return map
    }

    private static func candidateSongIdentifiers(for sheet: Sheet) -> Set<String> {
        var ids: Set<String> = []
        if !sheet.songIdentifier.isEmpty {
            ids.insert(sheet.songIdentifier)
        }
        if sheet.songId > 0 {
            ids.insert(String(sheet.songId))
        }
        if let song = sheet.song {
            ids.insert(song.songIdentifier)
            if song.songId > 0 {
                ids.insert(String(song.songId))
            }
        }
        return ids
    }

    private static func resolveSheet(for remote: BackendSyncRemoteSheet?, sheetMap: [String: Sheet]) -> Sheet? {
        guard let remote else {
            return nil
        }
        let identifierCandidates = [remote.songIdentifier, String(remote.songId)]
            .flatMap { normalizeIdentifierCandidates($0) }
            .filter { !$0.isEmpty && $0 != "0" }
        let chartTypeCandidates = normalizeChartTypeCandidates(remote.chartType)
        let difficultyCandidates = normalizeDifficultyCandidates(remote.difficulty)

        for identifier in identifierCandidates {
            for separator in ["_", "-"] {
                for chartType in chartTypeCandidates {
                    for difficulty in difficultyCandidates {
                        let key = "\(identifier)\(separator)\(chartType)\(separator)\(difficulty)"
                        if let sheet = sheetMap[key] {
                            return sheet
                        }
                    }
                }
            }
        }
        return nil
    }

    private static func normalizeIdentifierCandidates(_ value: String) -> [String] {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return []
        }
        let lowercased = trimmed.lowercased()
        if lowercased == trimmed {
            return [trimmed]
        }
        return [trimmed, lowercased]
    }

    private static func normalizeChartTypeCandidates(_ value: String) -> [String] {
        let normalized = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if normalized == "standard" || normalized == "std" || normalized == "sd" {
            return ["std", "standard"]
        }
        if normalized == "dx" {
            return ["dx"]
        }
        if normalized == "utage" {
            return ["utage"]
        }
        return normalized.isEmpty ? [] : [normalized]
    }

    private static func normalizeDifficultyCandidates(_ value: String) -> [String] {
        let lowered = value
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
        let normalized = lowered
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: "_", with: "")
            .replacingOccurrences(of: ":", with: "")

        if normalized == "remaster" {
            return ["remaster", "re:master", "re_master"]
        }
        if normalized.isEmpty {
            return []
        }
        if normalized == lowered {
            return [normalized]
        }
        return [normalized, lowered]
    }

    private static func downloadAvatarData(from avatarURLString: String?) async -> Data? {
        guard let avatarURLString, let avatarURL = URL(string: avatarURLString) else {
            return nil
        }

        var request = URLRequest(url: avatarURL)
        request.httpMethod = "GET"
        request.timeoutInterval = 30

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                return nil
            }
            guard (200...299).contains(httpResponse.statusCode), !data.isEmpty else {
                return nil
            }
            return data
        } catch {
            return nil
        }
    }
}
