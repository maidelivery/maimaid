import Foundation
import SwiftData

struct UserProfileFormValues: Sendable {
    let name: String
    let plate: String
    let server: GameServer
    let avatarData: Data?
    let avatarURL: String?
}

enum UserProfileMutationError: LocalizedError {
    case profileNotFound
    case activeProfileCannotBeDeleted

    var errorDescription: String? {
        switch self {
        case .profileNotFound:
            String(localized: "userProfile.error.profileNotFound")
        case .activeProfileCannotBeDeleted:
            String(localized: "userProfile.error.activeDelete")
        }
    }
}

@MainActor
enum UserProfileMutationService {
    static func save(
        profileId: UUID?,
        values: UserProfileFormValues,
        context: ModelContext
    ) throws -> UUID {
        let profiles = try context.fetch(
            FetchDescriptor<UserProfile>(sortBy: [SortDescriptor(\UserProfile.createdAt)])
        )
        let profile: UserProfile

        if let profileId {
            guard let existing = profiles.first(where: { $0.id == profileId }) else {
                throw UserProfileMutationError.profileNotFound
            }
            profile = existing
        } else {
            profile = UserProfile(isActive: profiles.isEmpty)
            context.insert(profile)
        }

        profile.name = values.name.trimmingCharacters(in: .whitespacesAndNewlines)
        profile.plate = values.plate.trimmingCharacters(in: .whitespacesAndNewlines).nilIfEmpty
        profile.server = values.server.rawValue
        profile.avatarData = values.avatarData
        profile.avatarUrl = values.avatarURL

        enforceSingleActiveProfile(in: profiles + (profileId == nil ? [profile] : []))
        let config = ensureSyncConfig(context: context)
        config.markProfilePendingUpdate(profile.id)
        try context.save()

        ScoreService.shared.notifyActiveProfileChanged()
        return profile.id
    }

    static func activate(profileId: UUID, context: ModelContext) throws -> Bool {
        let profiles = try context.fetch(FetchDescriptor<UserProfile>())
        guard let target = profiles.first(where: { $0.id == profileId }) else {
            throw UserProfileMutationError.profileNotFound
        }
        guard !target.isActive || profiles.count(where: \UserProfile.isActive) != 1 else {
            return false
        }

        for profile in profiles {
            profile.isActive = profile.id == profileId
        }
        let config = ensureSyncConfig(context: context)
        config.markProfilePendingUpdate(profileId)
        try context.save()
        ScoreService.shared.notifyActiveProfileChanged()
        return true
    }

    static func delete(profileId: UUID, context: ModelContext) throws -> Bool {
        let descriptor = FetchDescriptor<UserProfile>(
            predicate: #Predicate<UserProfile> { $0.id == profileId }
        )
        guard let profile = try context.fetch(descriptor).first else {
            return false
        }
        guard !profile.isActive else {
            throw UserProfileMutationError.activeProfileCannotBeDeleted
        }

        try removeLocalData(profileId: profileId, context: context)
        let config = ensureSyncConfig(context: context)
        config.markProfilePendingDeletion(profileId)
        try context.save()
        ProfileCredentialStore.shared.clearCredentials(for: profileId)
        ScoreService.shared.notifyScoresChanged(for: profileId)
        ScoreService.shared.notifyActiveProfileChanged()
        return true
    }

    static func synchronizeProfileUpdate(profileId: UUID, context: ModelContext) async {
        guard BackendSessionManager.shared.isAuthenticated else { return }
        do {
            try await BackendSyncOperationGate.shared.withLock {
                try await BackendIncrementalSyncService.pushPendingProfileUpdateUnlocked(
                    profileId: profileId,
                    context: context
                )
            }
        } catch is CancellationError {
            return
        } catch {
            return
        }
    }

    static func synchronizeProfileDeletion(profileId: UUID, context: ModelContext) async {
        guard BackendSessionManager.shared.isAuthenticated else { return }
        do {
            try await BackendSyncOperationGate.shared.withLock {
                try await BackendIncrementalSyncService.deleteProfileUnlocked(
                    profileId: profileId,
                    context: context
                )
            }
        } catch is CancellationError {
            return
        } catch {
            return
        }
    }

    private static func enforceSingleActiveProfile(in profiles: [UserProfile]) {
        guard !profiles.isEmpty else { return }
        let selected = profiles.first(where: \UserProfile.isActive) ?? profiles[0]
        for profile in profiles {
            profile.isActive = profile.id == selected.id
        }
    }

    private static func removeLocalData(profileId: UUID, context: ModelContext) throws {
        let scores = try context.fetch(
            FetchDescriptor<Score>(predicate: #Predicate<Score> { $0.userProfileId == profileId })
        )
        let records = try context.fetch(
            FetchDescriptor<PlayRecord>(predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId })
        )
        let profiles = try context.fetch(
            FetchDescriptor<UserProfile>(predicate: #Predicate<UserProfile> { $0.id == profileId })
        )

        for score in scores {
            context.delete(score)
        }
        for record in records {
            context.delete(record)
        }
        for profile in profiles {
            context.delete(profile)
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

private extension String {
    var nilIfEmpty: String? {
        isEmpty ? nil : self
    }
}
