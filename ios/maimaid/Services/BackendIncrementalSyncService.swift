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
    let latestRevision: String
    let localOnlyCount: Int
    let differentCount: Int
    let conflicts: [ImportScoreConflictItem]
    var hasConflicts: Bool { !conflicts.isEmpty }

    fileprivate let snapshot: BackendSyncSnapshot
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
            path: "v1/sync:push",
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
            path: "v1/sync:push",
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
        let sinceRevision = force ? "0" : config.lastSyncRevision
        let response = try await fetchPullResponse(
            sinceRevision: sinceRevision,
            profileId: profileId
        )

        try applyProfileDeleteEvents(response.events, context: context)
        try await applySnapshot(response.snapshot, context: context)
        ScoreService.shared.repairDetachedRecordsIfNeeded(context: context, force: true)
        config.lastSyncRevision = response.latestRevision
        try context.save()
        ScoreService.shared.invalidateAllCaches()
    }

    static func previewImportConflicts(context: ModelContext, profileId: UUID) async throws -> ImportSyncConflictPreview {
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
            latestRevision: response.latestRevision,
            localOnlyCount: localOnlyCount,
            differentCount: differentCount,
            conflicts: conflicts,
            snapshot: snapshot
        )
    }

    static func updateLastSyncRevisionIfAvailable(_ latestRevision: String?, context: ModelContext) throws {
        guard let latestRevision else {
            return
        }
        let normalized = latestRevision.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else {
            return
        }

        let config = ensureSyncConfig(context: context)
        config.lastSyncRevision = normalized
        try context.save()
    }

    static func applyImportConflictResolution(
        _ option: ImportSyncResolutionOption,
        preview: ImportSyncConflictPreview,
        context: ModelContext
    ) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
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

        let config = ensureSyncConfig(context: context)
        config.lastSyncRevision = preview.latestRevision
        try context.save()

        if option != .overwriteLocalWithImport {
            try await BackendCloudSyncService.overwriteRemoteProfileData(
                context: context,
                profileId: preview.profileId
            )
        }

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

    private static func fetchPullResponse(
        sinceRevision: String,
        profileId: UUID? = nil
    ) async throws -> BackendSyncPullResponse {
        var queryItems = [URLQueryItem(name: "sinceRevision", value: sinceRevision)]
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

        return try await BackendAPIClient.request(
            path: "v1/sync:pull?\(query)",
            method: "GET",
            authentication: .required
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
                message: "Failed to map remote scores to local sheets."
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
                message: "Failed to map remote scores to local sheets."
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
                message: "Failed to map remote play records to local sheets."
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
