import Foundation
import Security
import SwiftData

struct ProfileCredentials: Equatable {
    var lxnsRefreshToken: String

    static let empty = ProfileCredentials(lxnsRefreshToken: "")
}

enum LxnsOAuthConfiguration {
    static let clientId = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338"
    static let redirectUri = "urn:ietf:wg:oauth:2.0:oob"
    static let scope = "read_user_profile+read_player+write_player+read_user_token"
}

final class ProfileCredentialStore {
    static let shared = ProfileCredentialStore()

    private static let service = "in.shikoch.maimaid.profile.credentials"

    private struct StoredCredentials: Codable {
        let lxnsRefreshToken: String
    }

    private init() {}

    func credentials(for profileId: UUID) -> ProfileCredentials {
        guard let decoded = loadStoredCredentials(for: profileId) else {
            return .empty
        }
        return ProfileCredentials(
            lxnsRefreshToken: decoded.lxnsRefreshToken
        )
    }

    func setCredentials(_ credentials: ProfileCredentials, for profileId: UUID) {
        let sanitized = ProfileCredentials(
            lxnsRefreshToken: credentials.lxnsRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines)
        )

        if sanitized == .empty {
            clearCredentials(for: profileId)
            return
        }

        let payload = StoredCredentials(
            lxnsRefreshToken: sanitized.lxnsRefreshToken
        )
        guard let data = try? JSONEncoder().encode(payload) else {
            return
        }

        let query = keychainQuery(for: profileId)
        let update: [CFString: Any] = [
            kSecValueData: data,
            kSecAttrAccessible: kSecAttrAccessibleWhenUnlockedThisDeviceOnly,
            kSecAttrSynchronizable: kCFBooleanFalse as Any
        ]

        let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            var insert = keychainQuery(for: profileId)
            insert[kSecValueData] = data
            insert[kSecAttrAccessible] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            insert[kSecAttrSynchronizable] = kCFBooleanFalse as Any
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    func setLxnsRefreshToken(_ token: String, for profileId: UUID) {
        var current = credentials(for: profileId)
        current.lxnsRefreshToken = token
        setCredentials(current, for: profileId)
    }

    func clearCredentials(for profileId: UUID) {
        SecItemDelete(keychainQuery(for: profileId) as CFDictionary)
    }

    func hasLxnsBinding(for profileId: UUID) -> Bool {
        !credentials(for: profileId).lxnsRefreshToken.isEmpty
    }

    @MainActor
    func migrateLegacyCredentialsIfNeeded(context: ModelContext) {
        let profiles = (try? context.fetch(FetchDescriptor<UserProfile>())) ?? []
        var didMutateModel = false
        var activeProfileId = profiles.first(where: \.isActive)?.id ?? profiles.first?.id

        for profile in profiles {
            let legacyLxns = profile.lxnsRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines)

            if !legacyLxns.isEmpty {
                var merged = credentials(for: profile.id)
                var shouldSave = false
                if !legacyLxns.isEmpty && merged.lxnsRefreshToken.isEmpty {
                    merged.lxnsRefreshToken = legacyLxns
                    shouldSave = true
                }
                if shouldSave {
                    setCredentials(merged, for: profile.id)
                }
            }

            if !profile.lxnsRefreshToken.isEmpty || !profile.lxnsClientId.isEmpty {
                profile.lxnsRefreshToken = ""
                profile.lxnsClientId = ""
                didMutateModel = true
            }
        }

        if let config = (try? context.fetch(FetchDescriptor<SyncConfig>()))?.first {
            let legacyLxns = config.lxnsRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines)
            if activeProfileId == nil {
                activeProfileId = profiles.first?.id
            }

            if let targetProfileId = activeProfileId, !legacyLxns.isEmpty {
                var merged = credentials(for: targetProfileId)
                var shouldSave = false
                if !legacyLxns.isEmpty && merged.lxnsRefreshToken.isEmpty {
                    merged.lxnsRefreshToken = legacyLxns
                    shouldSave = true
                }
                if shouldSave {
                    setCredentials(merged, for: targetProfileId)
                }
            }

            if !config.lxnsRefreshToken.isEmpty || !config.lxnsClientId.isEmpty {
                config.lxnsRefreshToken = ""
                config.lxnsClientId = ""
                didMutateModel = true
            }
        }

        if didMutateModel {
            try? context.save()
        }
    }

    private func keychainQuery(for profileId: UUID) -> [CFString: Any] {
        [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: Self.service,
            kSecAttrAccount: profileId.uuidString.lowercased(),
            kSecAttrSynchronizable: kCFBooleanFalse as Any
        ]
    }

    private func loadStoredCredentials(for profileId: UUID) -> StoredCredentials? {
        var query = keychainQuery(for: profileId)
        query[kSecReturnData] = true
        query[kSecMatchLimit] = kSecMatchLimitOne

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else {
            return nil
        }
        return try? JSONDecoder().decode(StoredCredentials.self, from: data)
    }
}
