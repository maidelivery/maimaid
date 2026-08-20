import Foundation
import SwiftData

private let importedSyncBatchSize = 2_000

private struct ImportedSyncBatch {
    let scores: [BackendSyncScoreEntry]
    let records: [BackendSyncPlayRecordEntry]
}

private func importedSyncBatches(
    scores: [BackendSyncScoreEntry],
    records: [BackendSyncPlayRecordEntry],
    batchSize: Int = importedSyncBatchSize
) -> [ImportedSyncBatch] {
    guard batchSize > 0 else { return [] }
    let scoreBatches = stride(from: 0, to: scores.count, by: batchSize).map {
        Array(scores[$0 ..< min($0 + batchSize, scores.count)])
    }
    let recordBatches = stride(from: 0, to: records.count, by: batchSize).map {
        Array(records[$0 ..< min($0 + batchSize, records.count)])
    }
    let batchCount = max(scoreBatches.count, recordBatches.count)
    return (0 ..< batchCount).map { index in
        ImportedSyncBatch(
            scores: index < scoreBatches.count ? scoreBatches[index] : [],
            records: index < recordBatches.count ? recordBatches[index] : []
        )
    }
}

@MainActor
final class BackendSyncOperationGate {
    static let shared = BackendSyncOperationGate()

    private var isLocked = false
    private var waiters: [CheckedContinuation<Void, Never>] = []

    private init() {}

    func withLock<T>(_ operation: @MainActor () async throws -> T) async rethrows -> T {
        await acquire()
        defer { release() }
        return try await operation()
    }

    private func acquire() async {
        if !isLocked {
            isLocked = true
            return
        }
        await withCheckedContinuation { continuation in
            waiters.append(continuation)
        }
    }

    private func release() {
        guard !waiters.isEmpty else {
            isLocked = false
            return
        }
        waiters.removeFirst().resume()
    }
}

private struct BackendSyncPushResponse: Decodable {
    let latestRevision: String
    let conflicts: [BackendSyncConflict]?
    let profileVersions: [String: Date]?
}

private struct BackendSyncConflict: Decodable {
    let profileId: String
    let reason: String
    let serverProfile: BackendSyncRemoteProfile?
}

private struct BackendProfileDeleteResponse: Decodable {
    let profileId: String
}

private struct BackendProfilesResponse: Decodable {
    let profiles: [BackendSyncRemoteProfile]
}

private struct BackendSyncPullResponse: Decodable {
    let events: [BackendSyncEvent]
    let latestRevision: String
    let snapshot: BackendSyncSnapshot
}

private struct BackendPlayRecordsPage: Decodable {
    let records: [BackendSyncRemotePlayRecord]
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
    let updatedAt: Date
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
    let forceProfileOverwrite: Bool
    let profileUpserts: [BackendSyncProfileUpsertPayload]
    let scoreUpserts: [BackendSyncScoreSet]
    let playRecordUpserts: [BackendSyncRecordSet]
}

enum ImportSyncResolutionOption: String, CaseIterable, Identifiable {
    case mergeLocalAndImport
    case keepLocalAndImportRemoteOnly
    case overwriteLocalWithImport

    var id: String { rawValue }
}

struct ImportScoreValue: Equatable {
    let rate: Double
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let achievementDate: Date
}

enum ImportScoreConflictKind: String {
    case localOnly
    case differentValue
}

struct ImportScoreConflictItem: Identifiable {
    let id: String
    let key: String
    let kind: ImportScoreConflictKind
    let songTitle: String
    let songImageName: String?
    let chartType: String
    let difficulty: String
    let local: ImportScoreValue?
    let remote: ImportScoreValue?
}

struct ImportSyncConflictPreview: Identifiable {
    let id = UUID()
    let profileId: UUID
    let baseRevision: String
    let latestRevision: String
    let localOnlyCount: Int
    let differentCount: Int
    let conflicts: [ImportScoreConflictItem]
    var hasConflicts: Bool { !conflicts.isEmpty }

    fileprivate let snapshot: BackendSyncSnapshot
}

@MainActor
enum BackendIncrementalSyncService {
    static func ensureProfileExists(profile: UserProfile) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            guard BackendSessionManager.shared.isAuthenticated else {
                throw BackendAPIError.unauthorized
            }
            let profileId = profile.id.uuidString.lowercased()
            if let remoteVersion = try await fetchRemoteProfileVersion(profileId: profileId) {
                if let context = profile.modelContext {
                    let config = ensureSyncConfig(context: context)
                    config.setRemoteProfileVersions([profileId: remoteVersion])
                    try context.save()
                }
                return
            }
            try await pushProfileUpdateUnlocked(
                profile: profile,
                clientUpdatedAt: nil,
                pullAfterUpdate: false
            )
        }
    }

    static func pushScoreUpdate(profile: UserProfile, sheet: Sheet, score: Score) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            try await pushScoreUpdateUnlocked(profile: profile, sheet: sheet, score: score)
        }
    }

    private static func pushScoreUpdateUnlocked(profile: UserProfile, sheet: Sheet, score: Score) async throws {
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
            forceProfileOverwrite: false,
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

        let _: BackendSyncPushResponse = try await BackendAPIClient.request(
            path: "v1/sync:push",
            method: "POST",
            body: payload,
            authentication: .required
        )
    }

    static func pushProfileUpdate(profile: UserProfile, clientUpdatedAt: Date?) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            try await pushProfileUpdateUnlocked(
                profile: profile,
                clientUpdatedAt: clientUpdatedAt,
                pullAfterUpdate: true
            )
        }
    }

    private static func pushProfileUpdateUnlocked(
        profile: UserProfile,
        clientUpdatedAt: Date?,
        pullAfterUpdate: Bool
    ) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }
        let profileId = profile.id.uuidString.lowercased()
        let context = profile.modelContext
        let config = context.map(ensureSyncConfig(context:))
        let knownVersion = clientUpdatedAt ?? config?.remoteProfileVersion(for: profile.id)

        var response: BackendSyncPushResponse = try await BackendAPIClient.request(
            path: "v1/sync:push",
            method: "POST",
            body: BackendSyncPushPayload(
                idempotencyKey: UUID().uuidString.lowercased(),
                forceProfileOverwrite: false,
                profileUpserts: [profileUpsert(profile, avatarURL: profile.avatarUrl, clientUpdatedAt: knownVersion)],
                scoreUpserts: [],
                playRecordUpserts: []
            ),
            authentication: .required
        )
        config?.setRemoteProfileVersions(response.profileVersions ?? [:])
        if let context { try context.save() }
        try throwIfProfileConflict(response)

        let avatarUpload = try await BackendCloudSyncService.uploadAvatarIfNeeded(
            for: profile,
            clientUpdatedAt: response.profileVersions?[profileId] ?? knownVersion
        )
        if let avatarVersion = avatarUpload.updatedAt {
            response = try await BackendAPIClient.request(
                path: "v1/sync:push",
                method: "POST",
                body: BackendSyncPushPayload(
                    idempotencyKey: UUID().uuidString.lowercased(),
                    forceProfileOverwrite: false,
                    profileUpserts: [
                        profileUpsert(profile, avatarURL: avatarUpload.avatarURL, clientUpdatedAt: avatarVersion)
                    ],
                    scoreUpserts: [],
                    playRecordUpserts: []
                ),
                authentication: .required
            )
            try throwIfProfileConflict(response)
        }

        guard let context, let config else { return }
        config.setRemoteProfileVersions(response.profileVersions ?? [:])
        try context.save()
        if pullAfterUpdate {
            try await pullUpdatesUnlocked(context: context, force: false)
        } else {
            config.lastSyncRevision = response.latestRevision
            try context.save()
        }
    }

    static func pushAllLocalData(
        context: ModelContext,
        forceProfileOverwrite: Bool = false
    ) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            try await pushAllLocalDataUnlocked(
                context: context,
                forceProfileOverwrite: forceProfileOverwrite
            )
        }
    }

    static func pushAllLocalDataUnlocked(
        context: ModelContext,
        forceProfileOverwrite: Bool = false
    ) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let profiles = try context.fetch(FetchDescriptor<UserProfile>())
        guard !profiles.isEmpty else { return }
        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["-", "_"])
        let config = ensureSyncConfig(context: context)

        let profileUpserts = profiles.map { profile in
            profileUpsert(
                profile,
                avatarURL: profile.avatarUrl,
                clientUpdatedAt: config.remoteProfileVersion(for: profile.id)
            )
        }
        let scoreUpserts = try profiles.compactMap { profile -> BackendSyncScoreSet? in
            let profileId = profile.id
            let scores = try context.fetch(
                FetchDescriptor<Score>(predicate: #Predicate<Score> { $0.userProfileId == profileId })
            ).compactMap { score in
                buildScoreEntry(from: score, scoreSheetMap: scoreSheetMap)
            }
            return scores.isEmpty ? nil : BackendSyncScoreSet(
                profileId: profileId.uuidString.lowercased(),
                scores: scores
            )
        }
        let playRecordUpserts = try profiles.compactMap { profile -> BackendSyncRecordSet? in
            let profileId = profile.id
            let records = try context.fetch(
                FetchDescriptor<PlayRecord>(predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId })
            ).compactMap { record in
                buildPlayRecordEntry(from: record, recordSheetMap: recordSheetMap)
            }
            return records.isEmpty ? nil : BackendSyncRecordSet(
                profileId: profileId.uuidString.lowercased(),
                records: records
            )
        }

        var response: BackendSyncPushResponse = try await BackendAPIClient.request(
            path: "v1/sync:push",
            method: "POST",
            body: BackendSyncPushPayload(
                idempotencyKey: UUID().uuidString.lowercased(),
                forceProfileOverwrite: forceProfileOverwrite,
                profileUpserts: profileUpserts,
                scoreUpserts: scoreUpserts,
                playRecordUpserts: playRecordUpserts
            ),
            authentication: .required
        )
        config.setRemoteProfileVersions(response.profileVersions ?? [:])
        try context.save()
        try throwIfProfileConflict(response)

        var avatarUpserts: [BackendSyncProfileUpsertPayload] = []
        for profile in profiles {
            let profileId = profile.id.uuidString.lowercased()
            let avatarUpload = try await BackendCloudSyncService.uploadAvatarIfNeeded(
                for: profile,
                clientUpdatedAt: config.remoteProfileVersion(for: profile.id)
            )
            guard let avatarVersion = avatarUpload.updatedAt else { continue }
            avatarUpserts.append(
                profileUpsert(profile, avatarURL: avatarUpload.avatarURL, clientUpdatedAt: avatarVersion)
            )
            config.setRemoteProfileVersions([profileId: avatarVersion])
        }

        if !avatarUpserts.isEmpty {
            try context.save()
            response = try await BackendAPIClient.request(
                path: "v1/sync:push",
                method: "POST",
                body: BackendSyncPushPayload(
                    idempotencyKey: UUID().uuidString.lowercased(),
                    forceProfileOverwrite: forceProfileOverwrite,
                    profileUpserts: avatarUpserts,
                    scoreUpserts: [],
                    playRecordUpserts: []
                ),
                authentication: .required
            )
            config.setRemoteProfileVersions(response.profileVersions ?? [:])
            try context.save()
            try throwIfProfileConflict(response)
        }
    }

    private static func profileUpsert(
        _ profile: UserProfile,
        avatarURL: String?,
        clientUpdatedAt: Date?
    ) -> BackendSyncProfileUpsertPayload {
        BackendSyncProfileUpsertPayload(
            profileId: profile.id.uuidString.lowercased(),
            name: profile.name,
            server: profile.server,
            isActive: profile.isActive,
            playerRating: profile.playerRating,
            plate: profile.plate,
            avatarUrl: avatarURL,
            dfUsername: profile.dfUsername,
            b35Count: profile.b35Count,
            b15Count: profile.b15Count,
            b35RecLimit: profile.b35RecLimit,
            b15RecLimit: profile.b15RecLimit,
            createdAt: profile.createdAt,
            clientUpdatedAt: clientUpdatedAt
        )
    }

    static func pullUpdates(
        context: ModelContext,
        profileId: UUID? = nil,
        force: Bool = false,
        removeLocalProfilesAbsentFromSnapshot: Bool = false
    ) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            try await pullUpdatesUnlocked(
                context: context,
                profileId: profileId,
                force: force,
                removeLocalProfilesAbsentFromSnapshot: removeLocalProfilesAbsentFromSnapshot
            )
        }
    }

    static func pullUpdatesUnlocked(
        context: ModelContext,
        profileId: UUID? = nil,
        force: Bool = false,
        removeLocalProfilesAbsentFromSnapshot: Bool = false
    ) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }
        let config = ensureSyncConfig(context: context)
        let sinceRevision = force ? "0" : config.lastSyncRevision
        let response = try await fetchPullResponse(
            sinceRevision: sinceRevision,
            profileId: profileId
        )

        let shouldRemoveLocalProfiles = removeLocalProfilesAbsentFromSnapshot && profileId == nil
        if shouldRemoveLocalProfiles {
            try applyProfileDeleteEvents(response.events, context: context)
        }
        try await applySnapshot(
            response.snapshot,
            context: context,
            removeLocalProfilesAbsentFromSnapshot: shouldRemoveLocalProfiles
        )
        ScoreService.shared.repairDetachedRecordsIfNeeded(context: context, force: true)
        config.lastSyncRevision = response.latestRevision
        config.setRemoteProfileVersions(
            Dictionary(uniqueKeysWithValues: response.snapshot.profiles.map { ($0.id.lowercased(), $0.updatedAt) })
        )
        try context.save()
        ScoreService.shared.invalidateAllCaches()
        ScoreService.shared.notifyScoresChanged(for: profileId)
    }

    static func previewImportConflicts(context: ModelContext, profileId: UUID) async throws -> ImportSyncConflictPreview {
        try await BackendSyncOperationGate.shared.withLock {
            try await previewImportConflictsUnlocked(context: context, profileId: profileId)
        }
    }

    private static func previewImportConflictsUnlocked(
        context: ModelContext,
        profileId: UUID
    ) async throws -> ImportSyncConflictPreview {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let config = ensureSyncConfig(context: context)
        let response = try await fetchPullResponse(
            sinceRevision: config.lastSyncRevision,
            profileId: profileId
        )
        let snapshot = filteredSnapshot(response.snapshot, profileId: profileId)
        let conflicts = try buildImportScoreConflicts(
            snapshot: snapshot,
            profileId: profileId,
            context: context
        )
        let localOnlyCount = conflicts.filter { $0.kind == .localOnly }.count
        let differentCount = conflicts.count - localOnlyCount

        return ImportSyncConflictPreview(
            profileId: profileId,
            baseRevision: config.lastSyncRevision,
            latestRevision: response.latestRevision,
            localOnlyCount: localOnlyCount,
            differentCount: differentCount,
            conflicts: conflicts,
            snapshot: snapshot
        )
    }

    static func applyImportConflictResolution(
        _ option: ImportSyncResolutionOption,
        preview: ImportSyncConflictPreview,
        context: ModelContext
    ) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            try await applyImportConflictResolutionUnlocked(option, preview: preview, context: context)
        }
    }

    private static func applyImportConflictResolutionUnlocked(
        _ option: ImportSyncResolutionOption,
        preview: ImportSyncConflictPreview,
        context: ModelContext
    ) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let config = ensureSyncConfig(context: context)
        guard config.lastSyncRevision == preview.baseRevision else {
            throw BackendAPIError(
                statusCode: 409,
                code: "stale_sync_preview",
                message: String(localized: "settings.cloud.error.profileConflict")
            )
        }

        switch option {
        case .overwriteLocalWithImport:
            try applyImportSnapshot(
                preview.snapshot,
                profileId: preview.profileId,
                context: context,
                mode: .overwrite
            )
        case .mergeLocalAndImport:
            try applyImportSnapshot(
                preview.snapshot,
                profileId: preview.profileId,
                context: context,
                mode: .preferBest
            )
        case .keepLocalAndImportRemoteOnly:
            try applyImportSnapshot(
                preview.snapshot,
                profileId: preview.profileId,
                context: context,
                mode: .preferLocal
            )
        }

        ScoreService.shared.repairDetachedRecordsIfNeeded(context: context, force: true)

        config.setRemoteProfileVersions(
            Dictionary(uniqueKeysWithValues: preview.snapshot.profiles.map { ($0.id.lowercased(), $0.updatedAt) })
        )
        config.lastSyncRevision = preview.latestRevision
        try context.save()

        if option != .overwriteLocalWithImport {
            try await pushImportedProfileDataUnlocked(
                profileId: preview.profileId,
                importedSnapshot: preview.snapshot,
                context: context
            )
        }

        ScoreService.shared.invalidateAllCaches()
        ScoreService.shared.notifyScoresChanged(for: preview.profileId)
    }

    static func deleteProfile(profileId: UUID, context: ModelContext) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            try await deleteProfileUnlocked(profileId: profileId, context: context)
        }
    }

    private static func deleteProfileUnlocked(profileId: UUID, context: ModelContext) async throws {
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
        try? await pullUpdatesUnlocked(context: context, force: false)
    }

    private static func fetchPullResponse(
        sinceRevision: String,
        profileId: UUID? = nil
    ) async throws -> BackendSyncPullResponse {
        var queryItems = [
            URLQueryItem(name: "sinceRevision", value: sinceRevision),
            URLQueryItem(name: "includeSnapshot", value: "true"),
            URLQueryItem(name: "includeRecords", value: "false"),
            URLQueryItem(name: "limit", value: "500")
        ]
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
            path: "v1/sync:pull?\(query)",
            method: "GET",
            authentication: .required
        )
        let profileIds: [String]
        if let profileId {
            profileIds = [profileId.uuidString.lowercased()]
        } else {
            profileIds = response.snapshot.profiles.map(\.id).map { $0.lowercased() }
        }
        let records = try await fetchPlayRecords(profileIds: profileIds)
        return BackendSyncPullResponse(
            events: response.events,
            latestRevision: response.latestRevision,
            snapshot: BackendSyncSnapshot(
                profiles: response.snapshot.profiles,
                scores: response.snapshot.scores,
                records: records
            )
        )
    }

    private static func fetchPlayRecords(profileIds: [String]) async throws -> [BackendSyncRemotePlayRecord] {
        var records: [BackendSyncRemotePlayRecord] = []
        for profileId in profileIds {
            var offset = 0
            while true {
                let escapedProfileId = profileId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? profileId
                let response: BackendPlayRecordsPage = try await BackendAPIClient.request(
                    path: "v1/play-records?profileId=\(escapedProfileId)&limit=\(importedSyncBatchSize)&offset=\(offset)",
                    method: "GET",
                    authentication: .required
                )
                guard !response.records.isEmpty else { break }
                records.append(contentsOf: response.records)
                offset += response.records.count
                if response.records.count < importedSyncBatchSize { break }
            }
        }
        return records
    }

    private static func fetchRemoteProfileVersion(profileId: String) async throws -> Date? {
        let response: BackendProfilesResponse = try await BackendAPIClient.request(
            path: "v1/profiles",
            method: "GET",
            authentication: .required
        )
        return response.profiles.first { $0.id.lowercased() == profileId }?.updatedAt
    }

    private static func throwIfProfileConflict(_ response: BackendSyncPushResponse) throws {
        guard let conflict = response.conflicts?.first else { return }
        throw BackendAPIError(
            statusCode: 409,
            code: conflict.reason,
            message: String(localized: "settings.cloud.error.profileConflict")
        )
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

        try removeLocalProfiles(with: deletedProfileIds, context: context)
    }

    private static func applySnapshot(
        _ snapshot: BackendSyncSnapshot,
        context: ModelContext,
        removeLocalProfilesAbsentFromSnapshot: Bool = false
    ) async throws {
        let profilePairs: [(UUID, BackendSyncRemoteProfile)] = snapshot.profiles.compactMap {
            guard let uuid = UUID(uuidString: $0.id) else { return nil }
            return (uuid, $0)
        }
        let profileMap = Dictionary(uniqueKeysWithValues: profilePairs)
        let profileIds = Set(profileMap.keys)

        let existingProfiles = try context.fetch(FetchDescriptor<UserProfile>())
        if removeLocalProfilesAbsentFromSnapshot {
            let localOnlyProfileIds = Set(existingProfiles.map(\.id)).subtracting(profileIds)
            try removeLocalProfiles(with: localOnlyProfileIds, context: context)
        }

        guard !profileIds.isEmpty else {
            return
        }

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
                message: String(localized: "settings.cloud.error.playRecordMappingFailed")
            )
        }
        if !snapshot.scores.isEmpty && resolvedScores.isEmpty {
            throw BackendAPIError(
                statusCode: nil,
                code: "sync_sheet_mapping_failed",
                message: String(localized: "settings.cloud.error.scoreMappingFailed")
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

    private static func removeLocalProfiles(with profileIds: Set<UUID>, context: ModelContext) throws {
        for profileId in profileIds {
            let scoreDescriptor = FetchDescriptor<Score>(predicate: #Predicate { $0.userProfileId == profileId })
            let recordDescriptor = FetchDescriptor<PlayRecord>(predicate: #Predicate { $0.userProfileId == profileId })
            let profileDescriptor = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.id == profileId })

            for score in try context.fetch(scoreDescriptor) {
                context.delete(score)
            }
            for record in try context.fetch(recordDescriptor) {
                context.delete(record)
            }
            if let profile = try context.fetch(profileDescriptor).first {
                context.delete(profile)
            }
            ProfileCredentialStore.shared.clearCredentials(for: profileId)
        }
    }

    private static func pushImportedProfileDataUnlocked(
        profileId: UUID,
        importedSnapshot: BackendSyncSnapshot,
        context: ModelContext
    ) async throws {
        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["-", "_"])

        var importedScoresByKey: [String: ImportScoreDraft] = [:]
        for remoteScore in importedSnapshot.scores where remoteScore.profileId.lowercased() == profileId.uuidString.lowercased() {
            guard let draft = scoreDraft(from: remoteScore, scoreSheetMap: scoreSheetMap) else { continue }
            if let existing = importedScoresByKey[draft.key] {
                if isScoreDraftBetter(draft, than: existing) {
                    importedScoresByKey[draft.key] = draft
                }
            } else {
                importedScoresByKey[draft.key] = draft
            }
        }

        let localScores = try context.fetch(
            FetchDescriptor<Score>(predicate: #Predicate<Score> { $0.userProfileId == profileId })
        )
        let scoreEntries = localScores.compactMap { score -> BackendSyncScoreEntry? in
            guard let draft = scoreDraft(from: score, scoreSheetMap: scoreSheetMap) else { return nil }
            if let imported = importedScoresByKey[draft.key], isSameScoreValue(draft, imported) {
                return nil
            }
            return buildScoreEntry(from: score, scoreSheetMap: scoreSheetMap)
        }

        let importedRecordKeys = Set(
            importedSnapshot.records.compactMap { recordDraft(from: $0, recordSheetMap: recordSheetMap)?.uniqueKey }
        )
        let localRecords = try context.fetch(
            FetchDescriptor<PlayRecord>(predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId })
        )
        let recordEntries = localRecords.compactMap { record -> BackendSyncPlayRecordEntry? in
            guard let draft = recordDraft(from: record, recordSheetMap: recordSheetMap) else { return nil }
            guard !importedRecordKeys.contains(draft.uniqueKey) else { return nil }
            return buildPlayRecordEntry(from: record, recordSheetMap: recordSheetMap)
        }

        let profileToken = profileId.uuidString.lowercased()
        let config = ensureSyncConfig(context: context)
        for batch in importedSyncBatches(scores: scoreEntries, records: recordEntries) {
            guard !batch.scores.isEmpty || !batch.records.isEmpty else { continue }
            let response: BackendSyncPushResponse = try await BackendAPIClient.request(
                path: "v1/sync:push",
                method: "POST",
                body: BackendSyncPushPayload(
                    idempotencyKey: UUID().uuidString.lowercased(),
                    forceProfileOverwrite: false,
                    profileUpserts: [],
                    scoreUpserts: batch.scores.isEmpty
                        ? []
                        : [BackendSyncScoreSet(profileId: profileToken, scores: batch.scores)],
                    playRecordUpserts: batch.records.isEmpty
                        ? []
                        : [BackendSyncRecordSet(profileId: profileToken, records: batch.records)]
                ),
                authentication: .required
            )
            config.lastSyncRevision = response.latestRevision
            config.setRemoteProfileVersions(response.profileVersions ?? [:])
            try context.save()
            try throwIfProfileConflict(response)
        }
    }

    private static func buildScoreEntry(
        from score: Score,
        scoreSheetMap: [String: Sheet]
    ) -> BackendSyncScoreEntry? {
        guard let sheet = score.sheet ?? BackendSyncShared.resolveSheet(for: score.sheetId, sheetMap: scoreSheetMap) else {
            return nil
        }
        let songId = sheet.songId > 0 ? sheet.songId : nil
        return BackendSyncScoreEntry(
            songIdentifier: sheet.songIdentifier,
            songId: songId,
            title: sheet.song?.title,
            type: sheet.type.lowercased(),
            difficulty: sheet.difficulty.lowercased(),
            levelIndex: ThemeUtils.mapDifficultyToIndex(sheet.difficulty),
            achievements: score.rate,
            rank: score.rank,
            dxScore: score.dxScore,
            fc: score.fc,
            fs: score.fs,
            achievedAt: score.achievementDate.ISO8601Format()
        )
    }

    private static func buildPlayRecordEntry(
        from record: PlayRecord,
        recordSheetMap: [String: Sheet]
    ) -> BackendSyncPlayRecordEntry? {
        guard let sheet = record.sheet ?? BackendSyncShared.resolveSheet(for: record.sheetId, sheetMap: recordSheetMap) else {
            return nil
        }
        let songId = sheet.songId > 0 ? sheet.songId : nil
        return BackendSyncPlayRecordEntry(
            songIdentifier: sheet.songIdentifier,
            songId: songId,
            title: sheet.song?.title,
            type: sheet.type.lowercased(),
            difficulty: sheet.difficulty.lowercased(),
            levelIndex: ThemeUtils.mapDifficultyToIndex(sheet.difficulty),
            achievements: record.rate,
            rank: record.rank,
            dxScore: record.dxScore,
            fc: record.fc,
            fs: record.fs,
            playTime: record.playDate.ISO8601Format()
        )
    }

    private enum ImportMergeMode {
        case overwrite
        case preferBest
        case preferLocal
    }

    private struct ImportScoreDraft {
        let key: String
        let sheetId: String
        let sheet: Sheet?
        let songTitle: String
        let chartType: String
        let difficulty: String
        let rate: Double
        let rank: String
        let dxScore: Int
        let fc: String?
        let fs: String?
        let achievementDate: Date
    }

    private struct ImportRecordDraft {
        let uniqueKey: String
        let sheetId: String
        let sheet: Sheet?
        let rate: Double
        let rank: String
        let dxScore: Int
        let fc: String?
        let fs: String?
        let playDate: Date
    }

    private static func filteredSnapshot(_ snapshot: BackendSyncSnapshot, profileId: UUID) -> BackendSyncSnapshot {
        let profileToken = profileId.uuidString.lowercased()
        return BackendSyncSnapshot(
            profiles: snapshot.profiles.filter { $0.id.lowercased() == profileToken },
            scores: snapshot.scores.filter { $0.profileId.lowercased() == profileToken },
            records: snapshot.records.filter { $0.profileId.lowercased() == profileToken }
        )
    }

    private static func buildImportScoreConflicts(
        snapshot: BackendSyncSnapshot,
        profileId: UUID,
        context: ModelContext
    ) throws -> [ImportScoreConflictItem] {
        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["_", "-"])

        let localScores = try context.fetch(
            FetchDescriptor<Score>(predicate: #Predicate<Score> { $0.userProfileId == profileId })
        )
        var localByKey: [String: ImportScoreDraft] = [:]
        for localScore in localScores {
            guard let draft = scoreDraft(from: localScore, scoreSheetMap: scoreSheetMap) else {
                continue
            }
            if let existing = localByKey[draft.key] {
                if isScoreDraftBetter(draft, than: existing) {
                    localByKey[draft.key] = draft
                }
            } else {
                localByKey[draft.key] = draft
            }
        }

        let remoteScores = snapshot.scores.filter { $0.profileId.lowercased() == profileId.uuidString.lowercased() }
        var remoteByKey: [String: ImportScoreDraft] = [:]
        for remoteScore in remoteScores {
            guard let draft = scoreDraft(from: remoteScore, scoreSheetMap: scoreSheetMap) else {
                continue
            }
            if let existing = remoteByKey[draft.key] {
                if isScoreDraftBetter(draft, than: existing) {
                    remoteByKey[draft.key] = draft
                }
            } else {
                remoteByKey[draft.key] = draft
            }
        }

        if !remoteScores.isEmpty && remoteByKey.isEmpty {
            throw BackendAPIError(
                statusCode: nil,
                code: "sync_sheet_mapping_failed",
                message: String(localized: "settings.cloud.error.scoreMappingFailed")
            )
        }

        let allKeys = Set(localByKey.keys).union(remoteByKey.keys)
        let sortedKeys = allKeys.sorted()
        var conflicts: [ImportScoreConflictItem] = []
        conflicts.reserveCapacity(sortedKeys.count)

        for key in sortedKeys {
            let local = localByKey[key]
            let remote = remoteByKey[key]
            guard local != nil || remote != nil else {
                continue
            }

            let kind: ImportScoreConflictKind?
            if local != nil && remote == nil {
                kind = .localOnly
            } else if let local, let remote, !isSameScoreValue(local, remote) {
                kind = .differentValue
            } else {
                kind = nil
            }

            guard let kind else {
                continue
            }

            let songTitle = local?.songTitle ?? remote?.songTitle ?? key
            let songImageName = local?.sheet?.song?.imageName ?? remote?.sheet?.song?.imageName
            let chartType = local?.chartType ?? remote?.chartType ?? "-"
            let difficulty = local?.difficulty ?? remote?.difficulty ?? "-"
            let item = ImportScoreConflictItem(
                id: "\(key)|\(kind.rawValue)",
                key: key,
                kind: kind,
                songTitle: songTitle,
                songImageName: songImageName,
                chartType: chartType,
                difficulty: difficulty,
                local: local.map(importScoreValue(from:)),
                remote: remote.map(importScoreValue(from:))
            )
            conflicts.append(item)
        }

        return conflicts.sorted { lhs, rhs in
            let songOrder = lhs.songTitle.localizedStandardCompare(rhs.songTitle)
            if songOrder != .orderedSame {
                return songOrder == .orderedAscending
            }
            let typeOrder = lhs.chartType.localizedStandardCompare(rhs.chartType)
            if typeOrder != .orderedSame {
                return typeOrder == .orderedAscending
            }
            return lhs.difficulty.localizedStandardCompare(rhs.difficulty) == .orderedAscending
        }
    }

    private static func applyImportSnapshot(
        _ snapshot: BackendSyncSnapshot,
        profileId: UUID,
        context: ModelContext,
        mode: ImportMergeMode
    ) throws {
        let remoteProfile = snapshot.profiles.first { remoteProfile in
            UUID(uuidString: remoteProfile.id) == profileId
        }

        let allProfiles = try context.fetch(FetchDescriptor<UserProfile>())
        var profile = allProfiles.first { $0.id == profileId }

        if let remoteProfile {
            if let existing = profile {
                existing.name = remoteProfile.name
                existing.server = remoteProfile.server
                existing.avatarUrl = remoteProfile.avatarUrl
                existing.isActive = remoteProfile.isActive
                existing.playerRating = remoteProfile.playerRating
                existing.plate = remoteProfile.plate
                existing.dfUsername = remoteProfile.dfUsername
                existing.b35Count = remoteProfile.b35Count
                existing.b15Count = remoteProfile.b15Count
                existing.b35RecLimit = remoteProfile.b35RecLimit
                existing.b15RecLimit = remoteProfile.b15RecLimit
                existing.lastImportDateDF = remoteProfile.lastImportDateDf
                existing.lastImportDateLXNS = remoteProfile.lastImportDateLxns
            } else {
                let created = UserProfile(
                    id: profileId,
                    name: remoteProfile.name,
                    server: remoteProfile.server,
                    avatarData: nil,
                    avatarUrl: remoteProfile.avatarUrl,
                    isActive: remoteProfile.isActive,
                    createdAt: remoteProfile.createdAt,
                    dfUsername: remoteProfile.dfUsername,
                    playerRating: remoteProfile.playerRating,
                    plate: remoteProfile.plate,
                    lastImportDateDF: remoteProfile.lastImportDateDf,
                    lastImportDateLXNS: remoteProfile.lastImportDateLxns,
                    b35Count: remoteProfile.b35Count,
                    b15Count: remoteProfile.b15Count,
                    b35RecLimit: remoteProfile.b35RecLimit,
                    b15RecLimit: remoteProfile.b15RecLimit
                )
                context.insert(created)
                profile = created
            }
        }

        if let remoteProfile, remoteProfile.isActive {
            let refreshedProfiles = try context.fetch(FetchDescriptor<UserProfile>())
            for existingProfile in refreshedProfiles {
                existingProfile.isActive = existingProfile.id == profileId
            }
        }

        guard profile != nil else {
            return
        }

        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["-", "_"])

        let localScores = try context.fetch(
            FetchDescriptor<Score>(predicate: #Predicate<Score> { $0.userProfileId == profileId })
        )
        var localScoreByKey: [String: ImportScoreDraft] = [:]
        for localScore in localScores {
            guard let draft = scoreDraft(from: localScore, scoreSheetMap: scoreSheetMap) else {
                continue
            }
            if let existing = localScoreByKey[draft.key] {
                if isScoreDraftBetter(draft, than: existing) {
                    localScoreByKey[draft.key] = draft
                }
            } else {
                localScoreByKey[draft.key] = draft
            }
        }

        let remoteScores = snapshot.scores.filter { $0.profileId.lowercased() == profileId.uuidString.lowercased() }
        var remoteScoreByKey: [String: ImportScoreDraft] = [:]
        for remoteScore in remoteScores {
            guard let draft = scoreDraft(from: remoteScore, scoreSheetMap: scoreSheetMap) else {
                continue
            }
            if let existing = remoteScoreByKey[draft.key] {
                if isScoreDraftBetter(draft, than: existing) {
                    remoteScoreByKey[draft.key] = draft
                }
            } else {
                remoteScoreByKey[draft.key] = draft
            }
        }

        if !remoteScores.isEmpty && remoteScoreByKey.isEmpty {
            throw BackendAPIError(
                statusCode: nil,
                code: "sync_sheet_mapping_failed",
                message: String(localized: "settings.cloud.error.scoreMappingFailed")
            )
        }

        let mergedScores: [String: ImportScoreDraft]
        switch mode {
        case .overwrite:
            mergedScores = remoteScoreByKey
        case .preferBest:
            var merged = localScoreByKey
            for (key, remoteDraft) in remoteScoreByKey {
                guard let localDraft = merged[key] else {
                    merged[key] = remoteDraft
                    continue
                }
                if isScoreDraftBetter(remoteDraft, than: localDraft) {
                    merged[key] = remoteDraft
                }
            }
            mergedScores = merged
        case .preferLocal:
            var merged = localScoreByKey
            for (key, remoteDraft) in remoteScoreByKey where merged[key] == nil {
                merged[key] = remoteDraft
            }
            mergedScores = merged
        }

        for localScore in localScores {
            context.delete(localScore)
        }
        for draft in mergedScores.values.sorted(by: { $0.key < $1.key }) {
            let score = Score(
                sheetId: draft.sheetId,
                rate: draft.rate,
                rank: draft.rank,
                dxScore: draft.dxScore,
                fc: draft.fc,
                fs: draft.fs,
                achievementDate: draft.achievementDate,
                userProfileId: profileId
            )
            score.sheet = draft.sheet
            context.insert(score)
            if let sheet = draft.sheet {
                sheet.scores.append(score)
            }
        }

        let localRecords = try context.fetch(
            FetchDescriptor<PlayRecord>(predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId })
        )
        var localRecordByKey: [String: ImportRecordDraft] = [:]
        for localRecord in localRecords {
            guard let draft = recordDraft(from: localRecord, recordSheetMap: recordSheetMap) else {
                continue
            }
            localRecordByKey[draft.uniqueKey] = draft
        }

        let remoteRecords = snapshot.records.filter { $0.profileId.lowercased() == profileId.uuidString.lowercased() }
        var remoteRecordByKey: [String: ImportRecordDraft] = [:]
        for remoteRecord in remoteRecords {
            guard let draft = recordDraft(from: remoteRecord, recordSheetMap: recordSheetMap) else {
                continue
            }
            remoteRecordByKey[draft.uniqueKey] = draft
        }

        if !remoteRecords.isEmpty && remoteRecordByKey.isEmpty {
            throw BackendAPIError(
                statusCode: nil,
                code: "sync_sheet_mapping_failed",
                message: String(localized: "settings.cloud.error.playRecordMappingFailed")
            )
        }

        let mergedRecords: [String: ImportRecordDraft]
        switch mode {
        case .overwrite:
            mergedRecords = remoteRecordByKey
        case .preferBest:
            var merged = localRecordByKey
            for (key, remoteDraft) in remoteRecordByKey {
                merged[key] = remoteDraft
            }
            mergedRecords = merged
        case .preferLocal:
            var merged = localRecordByKey
            for (key, remoteDraft) in remoteRecordByKey where merged[key] == nil {
                merged[key] = remoteDraft
            }
            mergedRecords = merged
        }

        for localRecord in localRecords {
            context.delete(localRecord)
        }
        for draft in mergedRecords.values.sorted(by: { $0.playDate > $1.playDate }) {
            let record = PlayRecord(
                sheetId: draft.sheetId,
                rate: draft.rate,
                rank: draft.rank,
                dxScore: draft.dxScore,
                fc: draft.fc,
                fs: draft.fs,
                playDate: draft.playDate,
                userProfileId: profileId
            )
            record.sheet = draft.sheet
            context.insert(record)
            if let sheet = draft.sheet {
                if sheet.playRecords == nil {
                    sheet.playRecords = []
                }
                sheet.playRecords?.append(record)
            }
        }
    }

    private static func importScoreValue(from draft: ImportScoreDraft) -> ImportScoreValue {
        ImportScoreValue(
            rate: draft.rate,
            rank: draft.rank,
            dxScore: draft.dxScore,
            fc: draft.fc,
            fs: draft.fs,
            achievementDate: draft.achievementDate
        )
    }

    private static func scoreDraft(
        from localScore: Score,
        scoreSheetMap: [String: Sheet]
    ) -> ImportScoreDraft? {
        let resolvedSheet = localScore.sheet ?? BackendSyncShared.resolveSheet(
            for: localScore.sheetId,
            sheetMap: scoreSheetMap
        )
        let sheetId = resolvedSheet.map(BackendSyncShared.canonicalScoreSheetId(for:)) ?? localScore.sheetId
        let key = normalizedScoreKey(sheetId)
        let songTitle = resolvedSheet?.song?.title
            ?? resolvedSheet?.songIdentifier
            ?? localScore.sheetId
        let chartType = resolvedSheet?.type ?? "-"
        let difficulty = resolvedSheet?.difficulty ?? "-"

        return ImportScoreDraft(
            key: key,
            sheetId: sheetId,
            sheet: resolvedSheet,
            songTitle: songTitle,
            chartType: chartType,
            difficulty: difficulty,
            rate: localScore.rate,
            rank: localScore.rank,
            dxScore: localScore.dxScore,
            fc: localScore.fc,
            fs: localScore.fs,
            achievementDate: localScore.achievementDate
        )
    }

    private static func scoreDraft(
        from remoteScore: BackendSyncRemoteScore,
        scoreSheetMap: [String: Sheet]
    ) -> ImportScoreDraft? {
        guard
            let remoteSheet = remoteScore.sheet,
            let sheet = BackendSyncShared.resolveSheet(
                songIdentifier: remoteSheet.songIdentifier,
                songId: remoteSheet.songId,
                chartType: remoteSheet.chartType,
                difficulty: remoteSheet.difficulty,
                sheetMap: scoreSheetMap
            )
        else {
            return nil
        }

        let sheetId = BackendSyncShared.canonicalScoreSheetId(for: sheet)
        let key = normalizedScoreKey(sheetId)
        let songTitle = sheet.song?.title ?? remoteSheet.song?.title ?? sheet.songIdentifier

        return ImportScoreDraft(
            key: key,
            sheetId: sheetId,
            sheet: sheet,
            songTitle: songTitle,
            chartType: sheet.type,
            difficulty: sheet.difficulty,
            rate: remoteScore.achievements.value,
            rank: remoteScore.rank,
            dxScore: remoteScore.dxScore,
            fc: remoteScore.fc,
            fs: remoteScore.fs,
            achievementDate: remoteScore.achievedAt
        )
    }

    private static func recordDraft(
        from localRecord: PlayRecord,
        recordSheetMap: [String: Sheet]
    ) -> ImportRecordDraft? {
        let resolvedSheet = localRecord.sheet ?? BackendSyncShared.resolveSheet(
            for: localRecord.sheetId,
            sheetMap: recordSheetMap
        )
        let sheetId = resolvedSheet.map(BackendSyncShared.canonicalRecordSheetId(for:)) ?? localRecord.sheetId
        let uniqueKey = recordUniqueKey(
            normalizedSheetKey: normalizedRecordKey(sheetId),
            playDate: localRecord.playDate,
            rate: localRecord.rate,
            rank: localRecord.rank,
            dxScore: localRecord.dxScore,
            fc: localRecord.fc,
            fs: localRecord.fs
        )

        return ImportRecordDraft(
            uniqueKey: uniqueKey,
            sheetId: sheetId,
            sheet: resolvedSheet,
            rate: localRecord.rate,
            rank: localRecord.rank,
            dxScore: localRecord.dxScore,
            fc: localRecord.fc,
            fs: localRecord.fs,
            playDate: localRecord.playDate
        )
    }

    private static func recordDraft(
        from remoteRecord: BackendSyncRemotePlayRecord,
        recordSheetMap: [String: Sheet]
    ) -> ImportRecordDraft? {
        guard
            let remoteSheet = remoteRecord.sheet,
            let sheet = BackendSyncShared.resolveSheet(
                songIdentifier: remoteSheet.songIdentifier,
                songId: remoteSheet.songId,
                chartType: remoteSheet.chartType,
                difficulty: remoteSheet.difficulty,
                sheetMap: recordSheetMap
            )
        else {
            return nil
        }
        let sheetId = BackendSyncShared.canonicalRecordSheetId(for: sheet)
        let uniqueKey = recordUniqueKey(
            normalizedSheetKey: normalizedRecordKey(sheetId),
            playDate: remoteRecord.playTime,
            rate: remoteRecord.achievements.value,
            rank: remoteRecord.rank,
            dxScore: remoteRecord.dxScore,
            fc: remoteRecord.fc,
            fs: remoteRecord.fs
        )

        return ImportRecordDraft(
            uniqueKey: uniqueKey,
            sheetId: sheetId,
            sheet: sheet,
            rate: remoteRecord.achievements.value,
            rank: remoteRecord.rank,
            dxScore: remoteRecord.dxScore,
            fc: remoteRecord.fc,
            fs: remoteRecord.fs,
            playDate: remoteRecord.playTime
        )
    }

    private static func isSameScoreValue(_ lhs: ImportScoreDraft, _ rhs: ImportScoreDraft) -> Bool {
        // Import conflict matching follows display semantics:
        // compare achievements at 4 decimal places and ignore timestamp-only deltas.
        return normalizedAchievementBucket(lhs.rate) == normalizedAchievementBucket(rhs.rate)
            && normalizedToken(lhs.rank) == normalizedToken(rhs.rank)
            && lhs.dxScore == rhs.dxScore
            && normalizedToken(lhs.fc) == normalizedToken(rhs.fc)
            && normalizedToken(lhs.fs) == normalizedToken(rhs.fs)
    }

    private static func isScoreDraftBetter(_ lhs: ImportScoreDraft, than rhs: ImportScoreDraft) -> Bool {
        if lhs.rate != rhs.rate {
            return lhs.rate > rhs.rate
        }
        if lhs.achievementDate != rhs.achievementDate {
            return lhs.achievementDate > rhs.achievementDate
        }
        if lhs.dxScore != rhs.dxScore {
            return lhs.dxScore > rhs.dxScore
        }
        let fcOrder = ThemeUtils.fcOrder(lhs.fc)
        let otherFCOrder = ThemeUtils.fcOrder(rhs.fc)
        if fcOrder != otherFCOrder {
            return fcOrder > otherFCOrder
        }
        let fsOrder = ThemeUtils.fsOrder(lhs.fs)
        let otherFSOrder = ThemeUtils.fsOrder(rhs.fs)
        if fsOrder != otherFSOrder {
            return fsOrder > otherFSOrder
        }
        return false
    }

    private static func normalizedScoreKey(_ sheetId: String) -> String {
        sheetId
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacing("-", with: "_")
    }

    private static func normalizedRecordKey(_ sheetId: String) -> String {
        sheetId
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
            .replacing("_", with: "-")
    }

    private static func recordUniqueKey(
        normalizedSheetKey: String,
        playDate: Date,
        rate: Double,
        rank: String,
        dxScore: Int,
        fc: String?,
        fs: String?
    ) -> String {
        let rateBucket = Int((rate * 10_000).rounded())
        let timeBucket = Int((playDate.timeIntervalSince1970 * 1_000).rounded())
        let fcKey = normalizedToken(fc)
        let fsKey = normalizedToken(fs)
        return "\(normalizedSheetKey)|\(timeBucket)|\(rateBucket)|\(normalizedToken(rank))|\(dxScore)|\(fcKey)|\(fsKey)"
    }

    private static func normalizedToken(_ value: String?) -> String {
        guard let value else {
            return ""
        }
        return value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private static func normalizedAchievementBucket(_ value: Double) -> Int {
        Int((value * 10_000).rounded())
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
