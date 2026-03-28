import Foundation
import Observation
import SwiftData

enum AccountResolutionOption: String, CaseIterable, Identifiable {
    case mergeLocalAndCloud
    case overwriteCloudWithLocal
    case overwriteLocalWithCloud

    var id: String { rawValue }
}

struct AccountConflictState: Identifiable {
    let id = UUID()
    let ownerUserId: String?
    let currentUserId: String
    let hasLocalData: Bool
    let requiresResolution: Bool
}

private struct BackendProfilesListResponse: Decodable {
    let profiles: [BackendProfileListItem]
}

private struct BackendProfileListItem: Decodable {
    let id: String
    let isActive: Bool
}

private struct BackendProfilePatchPayload: Encodable {
    let isActive: Bool
}

private struct BackendProfilePatchResponse: Decodable {
    let profile: BackendProfileListItem
}

private struct BackendProfileDeleteAck: Decodable {
    let profileId: String
}

@MainActor
@Observable
final class AccountDataResolutionCoordinator {
    static let shared = AccountDataResolutionCoordinator()

    private init() {}

    private(set) var latestConflictState: AccountConflictState?

    func detectConflictAfterAuth(context: ModelContext, currentUserId: String) -> AccountConflictState {
        let config = ensureSyncConfig(context: context)
        let hasLocalData = hasAnyLocalUserData(context: context)
        let ownerUserId = config.localDataOwnerUserId
        let requiresResolution = hasLocalData
            && ownerUserId != nil
            && ownerUserId != currentUserId

        if requiresResolution {
            config.pendingResolutionForUserId = currentUserId
            config.pendingResolutionDetectedAt = Date.now
            try? context.save()
        } else {
            var didMutate = false
            if hasLocalData && ownerUserId == nil {
                config.localDataOwnerUserId = currentUserId
                didMutate = true
            }
            if config.pendingResolutionForUserId != nil || config.pendingResolutionDetectedAt != nil {
                config.pendingResolutionForUserId = nil
                config.pendingResolutionDetectedAt = nil
                didMutate = true
            }
            if didMutate {
                try? context.save()
            }
        }

        let state = AccountConflictState(
            ownerUserId: ownerUserId,
            currentUserId: currentUserId,
            hasLocalData: hasLocalData,
            requiresResolution: requiresResolution
        )
        latestConflictState = state
        return state
    }

    func hasPendingResolution(context: ModelContext, currentUserId: String) -> Bool {
        let config = ensureSyncConfig(context: context)
        if config.pendingResolutionForUserId == currentUserId {
            return true
        }
        guard hasAnyLocalUserData(context: context) else {
            return false
        }
        guard let owner = config.localDataOwnerUserId else {
            return false
        }
        return owner != currentUserId
    }

    func clearPendingResolutionState(context: ModelContext) {
        let config = ensureSyncConfig(context: context)
        config.pendingResolutionForUserId = nil
        config.pendingResolutionDetectedAt = nil
        try? context.save()
        latestConflictState = nil
    }

    func clearLocalUserData(context: ModelContext) throws {
        try deleteAllLocalUserData(context: context)
        let config = ensureSyncConfig(context: context)
        config.localDataOwnerUserId = nil
        config.pendingResolutionForUserId = nil
        config.pendingResolutionDetectedAt = nil
        config.lastSyncRevision = "0"
        try context.save()
        ScoreService.shared.invalidateAllCaches()
        latestConflictState = nil
    }

    func applyResolution(_ option: AccountResolutionOption, context: ModelContext) async throws {
        guard
            BackendSessionManager.shared.isAuthenticated,
            let currentUserId = BackendSessionManager.shared.currentUser?.id
        else {
            throw BackendAPIError.unauthorized
        }

        let config = ensureSyncConfig(context: context)
        let previousOwner = config.localDataOwnerUserId

        switch option {
        case .mergeLocalAndCloud:
            try await mergeLocalAndCloud(
                context: context,
                currentUserId: currentUserId,
                previousOwnerUserId: previousOwner
            )
        case .overwriteCloudWithLocal:
            try await overwriteCloudWithLocal(
                context: context,
                currentUserId: currentUserId,
                previousOwnerUserId: previousOwner
            )
        case .overwriteLocalWithCloud:
            try await overwriteLocalWithCloud(context: context)
        }

        let previousRevision = config.lastSyncRevision
        config.lastSyncRevision = "0"
        try context.save()

        do {
            try await BackendIncrementalSyncService.pullUpdates(context: context, force: true)
        } catch {
            config.lastSyncRevision = previousRevision
            try? context.save()
            throw error
        }

        config.localDataOwnerUserId = currentUserId
        config.pendingResolutionForUserId = nil
        config.pendingResolutionDetectedAt = nil
        try context.save()
        ScoreService.shared.invalidateAllCaches()
        latestConflictState = nil
    }
}

private extension AccountDataResolutionCoordinator {
    private struct ScoreDraft {
        let key: String
        let sheetId: String
        let sheet: Sheet?
        let rate: Double
        let rank: String
        let dxScore: Int
        let fc: String?
        let fs: String?
        let achievementDate: Date
    }

    private struct RecordDraft {
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

    private func mergeLocalAndCloud(
        context: ModelContext,
        currentUserId: String,
        previousOwnerUserId: String?
    ) async throws {
        if previousOwnerUserId != nil && previousOwnerUserId != currentUserId {
            try remapLocalProfileIdentifiers(context: context)
        }

        let snapshot = try await BackendCloudSyncService.fetchCloudSnapshot()
        try mergeCloudSnapshotIntoLocal(snapshot: snapshot, context: context)
        try context.save()
        ScoreService.shared.invalidateAllCaches()

        try await overwriteCloudFromLocal(context: context)
    }

    private func overwriteCloudWithLocal(
        context: ModelContext,
        currentUserId: String,
        previousOwnerUserId: String?
    ) async throws {
        if previousOwnerUserId != nil && previousOwnerUserId != currentUserId {
            try remapLocalProfileIdentifiers(context: context)
        }
        try context.save()
        ScoreService.shared.invalidateAllCaches()
        try await overwriteCloudFromLocal(context: context)
    }

    private func overwriteLocalWithCloud(context: ModelContext) async throws {
        let snapshot = try await BackendCloudSyncService.fetchCloudSnapshot()
        try deleteAllLocalUserData(context: context)
        try applyCloudSnapshot(snapshot: snapshot, context: context)
        try context.save()
        ScoreService.shared.invalidateAllCaches()
    }

    private func overwriteCloudFromLocal(context: ModelContext) async throws {
        try await clearRemoteProfiles()
        try await BackendCloudSyncService.backupToCloud(context: context)
    }

    private func clearRemoteProfiles() async throws {
        let response: BackendProfilesListResponse = try await BackendAPIClient.request(
            path: "v1/profiles",
            method: "GET",
            authentication: .required
        )

        for profile in response.profiles where profile.isActive {
            let escaped = profile.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? profile.id
            let _: BackendProfilePatchResponse = try await BackendAPIClient.request(
                path: "v1/profiles/\(escaped)",
                method: "PATCH",
                body: BackendProfilePatchPayload(isActive: false),
                authentication: .required
            )
        }

        for profile in response.profiles {
            let escaped = profile.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? profile.id
            let _: BackendProfileDeleteAck = try await BackendAPIClient.request(
                path: "v1/profiles/\(escaped)",
                method: "DELETE",
                authentication: .required
            )
        }
    }

    private func mergeCloudSnapshotIntoLocal(snapshot: CloudSnapshot, context: ModelContext) throws {
        let localProfiles = try context.fetch(FetchDescriptor<UserProfile>())
        var localBuckets = Dictionary(grouping: localProfiles) { profileMergeKey(name: $0.name, server: $0.server) }
        for key in localBuckets.keys {
            localBuckets[key]?.sort { $0.createdAt < $1.createdAt }
        }

        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = buildSheetMap(for: sheets, separators: ["-", "_"])

        var allProfiles = localProfiles
        for remoteProfile in snapshot.profiles {
            let key = profileMergeKey(name: remoteProfile.name, server: remoteProfile.server)
            if var bucket = localBuckets[key], let localProfile = bucket.first {
                bucket.removeFirst()
                localBuckets[key] = bucket
                mergeProfileMetadata(localProfile: localProfile, remoteProfile: remoteProfile)
                try mergeScores(
                    profile: localProfile,
                    remoteScores: snapshot.scoresByProfileId[remoteProfile.id] ?? [],
                    scoreSheetMap: scoreSheetMap,
                    context: context
                )
                try mergeRecords(
                    profile: localProfile,
                    remoteRecords: snapshot.recordsByProfileId[remoteProfile.id] ?? [],
                    recordSheetMap: recordSheetMap,
                    context: context
                )
            } else {
                let created = UserProfile(
                    id: remoteProfile.id,
                    name: remoteProfile.name,
                    server: remoteProfile.server,
                    avatarData: nil,
                    avatarUrl: remoteProfile.avatarUrl,
                    isActive: remoteProfile.isActive,
                    createdAt: remoteProfile.createdAt,
                    dfUsername: remoteProfile.dfUsername,
                    dfImportToken: remoteProfile.dfImportToken,
                    lxnsRefreshToken: remoteProfile.lxnsRefreshToken,
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
                allProfiles.append(created)
                try mergeScores(
                    profile: created,
                    remoteScores: snapshot.scoresByProfileId[remoteProfile.id] ?? [],
                    scoreSheetMap: scoreSheetMap,
                    context: context
                )
                try mergeRecords(
                    profile: created,
                    remoteRecords: snapshot.recordsByProfileId[remoteProfile.id] ?? [],
                    recordSheetMap: recordSheetMap,
                    context: context
                )
            }
        }

        enforceSingleActiveProfile(profiles: allProfiles)
    }

    private func applyCloudSnapshot(snapshot: CloudSnapshot, context: ModelContext) throws {
        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = buildSheetMap(for: sheets, separators: ["-", "_"])

        var createdProfiles: [UserProfile] = []
        for remoteProfile in snapshot.profiles {
            let profile = UserProfile(
                id: remoteProfile.id,
                name: remoteProfile.name,
                server: remoteProfile.server,
                avatarData: nil,
                avatarUrl: remoteProfile.avatarUrl,
                isActive: remoteProfile.isActive,
                createdAt: remoteProfile.createdAt,
                dfUsername: remoteProfile.dfUsername,
                dfImportToken: remoteProfile.dfImportToken,
                lxnsRefreshToken: remoteProfile.lxnsRefreshToken,
                playerRating: remoteProfile.playerRating,
                plate: remoteProfile.plate,
                lastImportDateDF: remoteProfile.lastImportDateDf,
                lastImportDateLXNS: remoteProfile.lastImportDateLxns,
                b35Count: remoteProfile.b35Count,
                b15Count: remoteProfile.b15Count,
                b35RecLimit: remoteProfile.b35RecLimit,
                b15RecLimit: remoteProfile.b15RecLimit
            )
            context.insert(profile)
            createdProfiles.append(profile)
            try mergeScores(
                profile: profile,
                remoteScores: snapshot.scoresByProfileId[remoteProfile.id] ?? [],
                scoreSheetMap: scoreSheetMap,
                context: context
            )
            try mergeRecords(
                profile: profile,
                remoteRecords: snapshot.recordsByProfileId[remoteProfile.id] ?? [],
                recordSheetMap: recordSheetMap,
                context: context
            )
        }

        enforceSingleActiveProfile(profiles: createdProfiles)
    }

    private func mergeScores(
        profile: UserProfile,
        remoteScores: [CloudSnapshotScore],
        scoreSheetMap: [String: Sheet],
        context: ModelContext
    ) throws {
        let profileId = profile.id
        let localDescriptor = FetchDescriptor<Score>(
            predicate: #Predicate<Score> { $0.userProfileId == profileId }
        )
        let localScores = (try? context.fetch(localDescriptor)) ?? []

        var scoreByKey: [String: ScoreDraft] = [:]

        for localScore in localScores {
            guard let draft = scoreDraft(from: localScore, scoreSheetMap: scoreSheetMap) else {
                continue
            }
            if let existing = scoreByKey[draft.key] {
                if isScoreDraft(draft, betterThan: existing) {
                    scoreByKey[draft.key] = draft
                }
            } else {
                scoreByKey[draft.key] = draft
            }
        }

        for remoteScore in remoteScores {
            guard let draft = scoreDraft(from: remoteScore, scoreSheetMap: scoreSheetMap) else {
                continue
            }
            if let existing = scoreByKey[draft.key] {
                if isScoreDraft(draft, betterThan: existing) {
                    scoreByKey[draft.key] = draft
                }
            } else {
                scoreByKey[draft.key] = draft
            }
        }

        for score in localScores {
            context.delete(score)
        }

        for draft in scoreByKey.values {
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
    }

    private func mergeRecords(
        profile: UserProfile,
        remoteRecords: [CloudSnapshotPlayRecord],
        recordSheetMap: [String: Sheet],
        context: ModelContext
    ) throws {
        let profileId = profile.id
        let localDescriptor = FetchDescriptor<PlayRecord>(
            predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId }
        )
        let localRecords = (try? context.fetch(localDescriptor)) ?? []

        var uniqueRecords: [String: RecordDraft] = [:]
        for localRecord in localRecords {
            guard let draft = recordDraft(from: localRecord, recordSheetMap: recordSheetMap) else {
                continue
            }
            uniqueRecords[draft.uniqueKey] = draft
        }

        for remoteRecord in remoteRecords {
            guard let draft = recordDraft(from: remoteRecord, recordSheetMap: recordSheetMap) else {
                continue
            }
            uniqueRecords[draft.uniqueKey] = draft
        }

        let mergedRecords = uniqueRecords.values.sorted { $0.playDate > $1.playDate }
        for record in localRecords {
            context.delete(record)
        }

        for draft in mergedRecords {
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

    private func mergeProfileMetadata(localProfile: UserProfile, remoteProfile: CloudSnapshotProfile) {
        if localProfile.name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            localProfile.name = remoteProfile.name
        }
        if localProfile.server.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            localProfile.server = remoteProfile.server
        }
        if localProfile.avatarUrl == nil {
            localProfile.avatarUrl = remoteProfile.avatarUrl
        }
        if localProfile.playerRating < remoteProfile.playerRating {
            localProfile.playerRating = remoteProfile.playerRating
        }
        if (localProfile.plate?.isEmpty ?? true), let remotePlate = remoteProfile.plate, !remotePlate.isEmpty {
            localProfile.plate = remotePlate
        }
        if localProfile.dfUsername.isEmpty {
            localProfile.dfUsername = remoteProfile.dfUsername
        }
        if localProfile.dfImportToken.isEmpty {
            localProfile.dfImportToken = remoteProfile.dfImportToken
        }
        if localProfile.lxnsRefreshToken.isEmpty {
            localProfile.lxnsRefreshToken = remoteProfile.lxnsRefreshToken
        }
        if localProfile.b35Count == 35 {
            localProfile.b35Count = remoteProfile.b35Count
        }
        if localProfile.b15Count == 15 {
            localProfile.b15Count = remoteProfile.b15Count
        }
        if localProfile.b35RecLimit == 10 {
            localProfile.b35RecLimit = remoteProfile.b35RecLimit
        }
        if localProfile.b15RecLimit == 10 {
            localProfile.b15RecLimit = remoteProfile.b15RecLimit
        }
        localProfile.lastImportDateDF = latestDate(localProfile.lastImportDateDF, remoteProfile.lastImportDateDf)
        localProfile.lastImportDateLXNS = latestDate(localProfile.lastImportDateLXNS, remoteProfile.lastImportDateLxns)
        localProfile.isActive = localProfile.isActive || remoteProfile.isActive
    }

    private func latestDate(_ lhs: Date?, _ rhs: Date?) -> Date? {
        switch (lhs, rhs) {
        case let (left?, right?):
            return left > right ? left : right
        case let (left?, nil):
            return left
        case let (nil, right?):
            return right
        default:
            return nil
        }
    }

    private func isScoreDraft(_ lhs: ScoreDraft, betterThan rhs: ScoreDraft) -> Bool {
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

    private func scoreDraft(from localScore: Score, scoreSheetMap: [String: Sheet]) -> ScoreDraft? {
        let resolvedSheet = localScore.sheet ?? resolveSheet(for: localScore.sheetId, sheetMap: scoreSheetMap)
        let normalizedSheetId = resolvedSheet.map(canonicalScoreSheetId(for:)) ?? localScore.sheetId
        let key = normalizedScoreKey(normalizedSheetId)
        return ScoreDraft(
            key: key,
            sheetId: normalizedSheetId,
            sheet: resolvedSheet,
            rate: localScore.rate,
            rank: localScore.rank,
            dxScore: localScore.dxScore,
            fc: localScore.fc,
            fs: localScore.fs,
            achievementDate: localScore.achievementDate
        )
    }

    private func scoreDraft(from remoteScore: CloudSnapshotScore, scoreSheetMap: [String: Sheet]) -> ScoreDraft? {
        guard let sheet = resolveSheet(for: remoteScore.sheet, sheetMap: scoreSheetMap) else {
            return nil
        }
        let sheetId = canonicalScoreSheetId(for: sheet)
        return ScoreDraft(
            key: normalizedScoreKey(sheetId),
            sheetId: sheetId,
            sheet: sheet,
            rate: remoteScore.achievements,
            rank: remoteScore.rank,
            dxScore: remoteScore.dxScore,
            fc: remoteScore.fc,
            fs: remoteScore.fs,
            achievementDate: remoteScore.achievedAt
        )
    }

    private func normalizedScoreKey(_ sheetId: String) -> String {
        sheetId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacing("-", with: "_")
    }

    private func recordDraft(from localRecord: PlayRecord, recordSheetMap: [String: Sheet]) -> RecordDraft? {
        let resolvedSheet = localRecord.sheet ?? resolveSheet(for: localRecord.sheetId, sheetMap: recordSheetMap)
        let sheetId = resolvedSheet.map(canonicalRecordSheetId(for:)) ?? localRecord.sheetId
        let uniqueKey = recordUniqueKey(
            normalizedSheetKey: normalizedRecordKey(sheetId),
            playDate: localRecord.playDate,
            rate: localRecord.rate,
            rank: localRecord.rank,
            dxScore: localRecord.dxScore,
            fc: localRecord.fc,
            fs: localRecord.fs
        )

        return RecordDraft(
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

    private func recordDraft(from remoteRecord: CloudSnapshotPlayRecord, recordSheetMap: [String: Sheet]) -> RecordDraft? {
        guard let sheet = resolveSheet(for: remoteRecord.sheet, sheetMap: recordSheetMap) else {
            return nil
        }
        let sheetId = canonicalRecordSheetId(for: sheet)
        let uniqueKey = recordUniqueKey(
            normalizedSheetKey: normalizedRecordKey(sheetId),
            playDate: remoteRecord.playTime,
            rate: remoteRecord.achievements,
            rank: remoteRecord.rank,
            dxScore: remoteRecord.dxScore,
            fc: remoteRecord.fc,
            fs: remoteRecord.fs
        )

        return RecordDraft(
            uniqueKey: uniqueKey,
            sheetId: sheetId,
            sheet: sheet,
            rate: remoteRecord.achievements,
            rank: remoteRecord.rank,
            dxScore: remoteRecord.dxScore,
            fc: remoteRecord.fc,
            fs: remoteRecord.fs,
            playDate: remoteRecord.playTime
        )
    }

    private func normalizedRecordKey(_ sheetId: String) -> String {
        sheetId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased().replacing("_", with: "-")
    }

    private func recordUniqueKey(
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
        let fcKey = fc?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        let fsKey = fs?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() ?? ""
        return "\(normalizedSheetKey)|\(timeBucket)|\(rateBucket)|\(rank.lowercased())|\(dxScore)|\(fcKey)|\(fsKey)"
    }

    private func remapLocalProfileIdentifiers(context: ModelContext) throws {
        let profiles = try context.fetch(FetchDescriptor<UserProfile>())
        guard !profiles.isEmpty else {
            return
        }

        let oldToNew = Dictionary(uniqueKeysWithValues: profiles.map { ($0.id, UUID()) })
        for profile in profiles {
            if let newId = oldToNew[profile.id] {
                profile.id = newId
            }
        }

        let scores = try context.fetch(FetchDescriptor<Score>())
        for score in scores {
            guard let oldId = score.userProfileId, let newId = oldToNew[oldId] else {
                continue
            }
            score.userProfileId = newId
        }

        let records = try context.fetch(FetchDescriptor<PlayRecord>())
        for record in records {
            guard let oldId = record.userProfileId, let newId = oldToNew[oldId] else {
                continue
            }
            record.userProfileId = newId
        }
    }

    private func deleteAllLocalUserData(context: ModelContext) throws {
        let allScores = try context.fetch(FetchDescriptor<Score>())
        for score in allScores {
            context.delete(score)
        }

        let allRecords = try context.fetch(FetchDescriptor<PlayRecord>())
        for record in allRecords {
            context.delete(record)
        }

        let allProfiles = try context.fetch(FetchDescriptor<UserProfile>())
        for profile in allProfiles {
            context.delete(profile)
        }
    }

    private func enforceSingleActiveProfile(profiles: [UserProfile]) {
        guard !profiles.isEmpty else {
            return
        }

        if let currentActive = profiles.first(where: \.isActive) {
            for profile in profiles {
                profile.isActive = profile.id == currentActive.id
            }
            return
        }

        let fallback = profiles.min { $0.createdAt < $1.createdAt } ?? profiles[0]
        for profile in profiles {
            profile.isActive = profile.id == fallback.id
        }
    }

    private func hasAnyLocalUserData(context: ModelContext) -> Bool {
        var profileDescriptor = FetchDescriptor<UserProfile>()
        profileDescriptor.fetchLimit = 1
        if let profiles = try? context.fetch(profileDescriptor), !profiles.isEmpty {
            return true
        }

        var scoreDescriptor = FetchDescriptor<Score>()
        scoreDescriptor.fetchLimit = 1
        if let scores = try? context.fetch(scoreDescriptor), !scores.isEmpty {
            return true
        }

        var recordDescriptor = FetchDescriptor<PlayRecord>()
        recordDescriptor.fetchLimit = 1
        if let records = try? context.fetch(recordDescriptor), !records.isEmpty {
            return true
        }

        return false
    }

    private func ensureSyncConfig(context: ModelContext) -> SyncConfig {
        if let config = try? context.fetch(FetchDescriptor<SyncConfig>()).first {
            return config
        }
        let config = SyncConfig()
        context.insert(config)
        return config
    }

    private func profileMergeKey(name: String, server: String) -> String {
        let normalizedName = name
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .split(whereSeparator: \.isWhitespace)
            .map(String.init)
            .joined(separator: " ")
            .lowercased()
        let normalizedServer = server.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return "\(normalizedServer)|\(normalizedName)"
    }

    private func canonicalScoreSheetId(for sheet: Sheet) -> String {
        "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
    }

    private func canonicalRecordSheetId(for sheet: Sheet) -> String {
        "\(sheet.songIdentifier)-\(sheet.type)-\(sheet.difficulty)"
    }

    private func resolveSheet(for existingSheetId: String, sheetMap: [String: Sheet]) -> Sheet? {
        let key = existingSheetId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let sheet = sheetMap[key] {
            return sheet
        }
        let swapped = key.contains("_") ? key.replacing("_", with: "-") : key.replacing("-", with: "_")
        return sheetMap[swapped]
    }

    private func buildSheetMap(for sheets: [Sheet], separators: [String]) -> [String: Sheet] {
        var map: [String: Sheet] = [:]
        for sheet in sheets {
            let identifiers = candidateSongIdentifiers(for: sheet)
            let chartTypeCandidates = normalizeChartTypeCandidates(sheet.type)
            let difficultyCandidates = normalizeDifficultyCandidates(sheet.difficulty)
            for identifier in identifiers {
                for separator in separators {
                    for chartType in chartTypeCandidates {
                        for difficulty in difficultyCandidates {
                            let key = "\(identifier)\(separator)\(chartType)\(separator)\(difficulty)".lowercased()
                            map[key] = sheet
                        }
                    }
                }
            }
        }
        return map
    }

    private func candidateSongIdentifiers(for sheet: Sheet) -> Set<String> {
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

    private func resolveSheet(for remote: CloudSnapshotSheet?, sheetMap: [String: Sheet]) -> Sheet? {
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
                        let key = "\(identifier)\(separator)\(chartType)\(separator)\(difficulty)".lowercased()
                        if let sheet = sheetMap[key] {
                            return sheet
                        }
                    }
                }
            }
        }
        return nil
    }

    private func normalizeIdentifierCandidates(_ value: String) -> [String] {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return []
        }
        let lowered = trimmed.lowercased()
        if lowered == trimmed {
            return [trimmed]
        }
        return [trimmed, lowered]
    }

    private func normalizeChartTypeCandidates(_ value: String) -> [String] {
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

    private func normalizeDifficultyCandidates(_ value: String) -> [String] {
        let lowered = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let normalized = lowered
            .replacing(" ", with: "")
            .replacing("_", with: "")
            .replacing(":", with: "")

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
}
