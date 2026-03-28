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

private struct BackendSyncProfileUpsertPayload: Encodable {
    let profileId: String
    let name: String
    let server: String
    let isActive: Bool
    let playerRating: Int
    let plate: String?
    let avatarUrl: String?
    let dfUsername: String
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
        ScoreService.shared.repairDetachedRecordsIfNeeded(context: context, force: true)
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

            if let avatarData = await BackendSyncShared.downloadAvatarData(from: remote.avatarUrl) {
                profile.avatarData = avatarData
            }
        }

        if let syncedActiveProfileId = profilePairs.first(where: { $0.1.isActive })?.0 {
            let allProfiles = try context.fetch(FetchDescriptor<UserProfile>())
            for profile in allProfiles {
                profile.isActive = (profile.id == syncedActiveProfileId)
            }
        }

        for profileId in profileIds {
            let scoreDescriptor = FetchDescriptor<Score>(
                predicate: #Predicate<Score> { $0.userProfileId == profileId }
            )
            let recordDescriptor = FetchDescriptor<PlayRecord>(
                predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId }
            )
            let localScores = try context.fetch(scoreDescriptor)
            for score in localScores {
                context.delete(score)
            }
            let localRecords = try context.fetch(recordDescriptor)
            for record in localRecords {
                context.delete(record)
            }
        }

        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["-", "_"])

        let resolvedRecords = snapshot.records.compactMap { remoteRecord -> (UUID, Sheet, BackendSyncRemotePlayRecord)? in
            guard let profileId = UUID(uuidString: remoteRecord.profileId) else { return nil }
            guard profileIds.contains(profileId) else { return nil }
            guard
                let remoteSheet = remoteRecord.sheet,
                let sheet = BackendSyncShared.resolveSheet(
                    songIdentifier: remoteSheet.songIdentifier,
                    songId: remoteSheet.songId,
                    chartType: remoteSheet.chartType,
                    difficulty: remoteSheet.difficulty,
                    sheetMap: recordSheetMap
                )
            else { return nil }
            return (profileId, sheet, remoteRecord)
        }

        let resolvedScores = snapshot.scores.compactMap { remoteScore -> (UUID, Sheet, BackendSyncRemoteScore)? in
            guard let profileId = UUID(uuidString: remoteScore.profileId) else { return nil }
            guard profileIds.contains(profileId) else { return nil }
            guard
                let remoteSheet = remoteScore.sheet,
                let sheet = BackendSyncShared.resolveSheet(
                    songIdentifier: remoteSheet.songIdentifier,
                    songId: remoteSheet.songId,
                    chartType: remoteSheet.chartType,
                    difficulty: remoteSheet.difficulty,
                    sheetMap: scoreSheetMap
                )
            else { return nil }
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
                sheetId: BackendSyncShared.canonicalRecordSheetId(for: sheet),
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
                sheetId: BackendSyncShared.canonicalScoreSheetId(for: sheet),
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
}
