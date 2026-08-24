import Foundation
import SwiftData

@Model
final class SyncConfig {
    // Sync Settings
    var isAutoUploadEnabled: Bool = false
    var backgroundSyncInterval: Int = 0 // 0 means disabled, otherwise in hours
    @Attribute(originalName: "supabaseBackupInterval")
    var cloudBackupInterval: Int = 0 // 0 means disabled, otherwise in hours
    
    // Theme Settings
    var themeRawValue: Int = 0 // 0: System, 1: Light, 2: Dark
    
    // Last Sync Info
    var lastImportDateDF: Date?
    var lastImportDateLXNS: Date?
    var lastStaticDataUpdateDate: Date?
    @Attribute(originalName: "lastSupabaseBackupDate")
    var lastCloudBackupDate: Date?
    var lastSyncRevision: String = "0"
    var remoteProfileVersionsData: Data?
    var pendingUpdatedProfileIdsData: Data?
    var pendingUpdatedProfileTokensData: Data?
    var pendingDeletedProfileIdsData: Data?
    var pendingDataProfileIdsData: Data?
    var pendingFullReplaceProfileIdsData: Data?
    var syncedDataProfileIdsData: Data?
    var localDataOwnerUserId: String?
    var pendingResolutionForUserId: String?
    var pendingResolutionDetectedAt: Date?
    
    // Legacy fields — kept for migration, will be read once to create default UserProfile
    var userName: String?
    var avatarUrl: String?
    var avatarData: Data?
    var isCustomProfile: Bool = false
    var playerRating: Int = 0
    var plate: String?
    var dfUsername: String = ""
    var lxnsRefreshToken: String = ""
    var lxnsClientId: String = ""
    var b35Count: Int = 35
    var b15Count: Int = 15
    var b35RecLimit: Int = 10
    var b15RecLimit: Int = 10
    
    // Migration flag
    var didMigrateToUserProfile: Bool = false
    
    init(isAutoUploadEnabled: Bool = false,
         backgroundSyncInterval: Int = 0,
         cloudBackupInterval: Int = 0,
         themeRawValue: Int = 0) {
        self.isAutoUploadEnabled = isAutoUploadEnabled
        self.backgroundSyncInterval = backgroundSyncInterval
        self.cloudBackupInterval = cloudBackupInterval
        self.themeRawValue = themeRawValue
    }

    func remoteProfileVersion(for profileId: UUID) -> Date? {
        remoteProfileVersions[profileId.uuidString.lowercased()]
    }

    func setRemoteProfileVersions(_ versions: [String: Date]) {
        guard !versions.isEmpty else { return }
        var merged = remoteProfileVersions
        for (profileId, updatedAt) in versions {
            merged[profileId.lowercased()] = updatedAt
        }
        remoteProfileVersionsData = try? JSONEncoder().encode(merged)
    }

    func clearRemoteProfileVersions() {
        remoteProfileVersionsData = nil
    }

    var pendingUpdatedProfileIds: Set<UUID> {
        get { decodeProfileIds(from: pendingUpdatedProfileIdsData) }
        set { pendingUpdatedProfileIdsData = encodeProfileIds(newValue) }
    }

    var pendingDeletedProfileIds: Set<UUID> {
        get { decodeProfileIds(from: pendingDeletedProfileIdsData) }
        set { pendingDeletedProfileIdsData = encodeProfileIds(newValue) }
    }

    @discardableResult
    func markProfilePendingUpdate(_ profileId: UUID) -> UUID {
        var profileIds = pendingUpdatedProfileIds
        profileIds.insert(profileId)
        pendingUpdatedProfileIds = profileIds
        let token = UUID()
        var tokens = pendingUpdatedProfileTokens
        tokens[profileId.uuidString.lowercased()] = token.uuidString.lowercased()
        pendingUpdatedProfileTokensData = try? JSONEncoder().encode(tokens)
        return token
    }

    func clearPendingProfileUpdate(_ profileId: UUID) {
        var profileIds = pendingUpdatedProfileIds
        profileIds.remove(profileId)
        pendingUpdatedProfileIds = profileIds
        var tokens = pendingUpdatedProfileTokens
        tokens.removeValue(forKey: profileId.uuidString.lowercased())
        pendingUpdatedProfileTokensData = tokens.isEmpty ? nil : try? JSONEncoder().encode(tokens)
    }

    func pendingProfileUpdateToken(for profileId: UUID) -> UUID? {
        let value = pendingUpdatedProfileTokens[profileId.uuidString.lowercased()]
        return value.flatMap(UUID.init(uuidString:))
    }

    @discardableResult
    func clearPendingProfileUpdate(_ profileId: UUID, matching token: UUID) -> Bool {
        guard pendingProfileUpdateToken(for: profileId) == token else {
            return false
        }
        clearPendingProfileUpdate(profileId)
        return true
    }

    func markProfilePendingDeletion(_ profileId: UUID) {
        clearPendingProfileUpdate(profileId)
        var profileIds = pendingDeletedProfileIds
        profileIds.insert(profileId)
        pendingDeletedProfileIds = profileIds
    }

    func clearPendingProfileDeletion(_ profileId: UUID) {
        var profileIds = pendingDeletedProfileIds
        profileIds.remove(profileId)
        pendingDeletedProfileIds = profileIds
    }

    var pendingDataProfileIds: Set<UUID> {
        get { decodeProfileIds(from: pendingDataProfileIdsData) }
        set { pendingDataProfileIdsData = encodeProfileIds(newValue) }
    }

    var pendingFullReplaceProfileIds: Set<UUID> {
        get { decodeProfileIds(from: pendingFullReplaceProfileIdsData) }
        set { pendingFullReplaceProfileIdsData = encodeProfileIds(newValue) }
    }

    var syncedDataProfileIds: Set<UUID> {
        get { decodeProfileIds(from: syncedDataProfileIdsData) }
        set { syncedDataProfileIdsData = encodeProfileIds(newValue) }
    }

    func markDataPending(_ profileId: UUID, fullReplace: Bool = false) {
        var pending = pendingDataProfileIds
        pending.insert(profileId)
        pendingDataProfileIds = pending
        if fullReplace {
            var replacements = pendingFullReplaceProfileIds
            replacements.insert(profileId)
            pendingFullReplaceProfileIds = replacements
        }
    }

    func markDataSynced(_ profileId: UUID) {
        var pending = pendingDataProfileIds
        pending.remove(profileId)
        pendingDataProfileIds = pending
        var replacements = pendingFullReplaceProfileIds
        replacements.remove(profileId)
        pendingFullReplaceProfileIds = replacements
        var synced = syncedDataProfileIds
        synced.insert(profileId)
        syncedDataProfileIds = synced
    }

    func resetDataSyncState() {
        pendingDataProfileIdsData = nil
        pendingFullReplaceProfileIdsData = nil
        syncedDataProfileIdsData = nil
    }

    private var remoteProfileVersions: [String: Date] {
        guard let remoteProfileVersionsData else { return [:] }
        return (try? JSONDecoder().decode([String: Date].self, from: remoteProfileVersionsData)) ?? [:]
    }

    private var pendingUpdatedProfileTokens: [String: String] {
        guard let pendingUpdatedProfileTokensData else { return [:] }
        return (try? JSONDecoder().decode([String: String].self, from: pendingUpdatedProfileTokensData)) ?? [:]
    }

    private func decodeProfileIds(from data: Data?) -> Set<UUID> {
        guard let data else { return [] }
        let values = (try? JSONDecoder().decode([String].self, from: data)) ?? []
        return Set(values.compactMap(UUID.init(uuidString:)))
    }

    private func encodeProfileIds(_ profileIds: Set<UUID>) -> Data? {
        let values = profileIds.map { $0.uuidString.lowercased() }.sorted()
        return values.isEmpty ? nil : try? JSONEncoder().encode(values)
    }
}
