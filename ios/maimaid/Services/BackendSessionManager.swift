import Foundation
import Observation
import Security

struct BackendAuthUser: Codable, Equatable {
    let id: String
    let email: String
    let isAdmin: Bool
}

private struct BackendTokenBundle: Codable {
    let user: BackendAuthUser
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int
}

private struct BackendAuthPayload: Codable {
    let user: BackendAuthUser
    let accessToken: String
    let refreshToken: String
    let expiresIn: Int
}

private struct BackendSuccessResponse: Codable {
    let success: Bool?
}

private struct BackendMePayload: Codable {
    let id: String
    let email: String
    let isAdmin: Bool
}

private struct BackendRefreshRequest: Encodable {
    let refreshToken: String
}

private struct KeychainTokenStore {
    private static let account = "in.shikoch.maimaid.backend.tokens"
    private static let service = "in.shikoch.maimaid"

    static func load() -> BackendTokenBundle? {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account,
            kSecReturnData: true,
            kSecMatchLimit: kSecMatchLimitOne
        ]

        var item: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &item)
        guard status == errSecSuccess, let data = item as? Data else {
            return nil
        }
        return try? BackendAPIClient.decoder.decode(BackendTokenBundle.self, from: data)
    }

    static func save(_ bundle: BackendTokenBundle) {
        guard let data = try? BackendAPIClient.encoder.encode(bundle) else {
            return
        }

        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account
        ]

        let update: [CFString: Any] = [
            kSecValueData: data
        ]

        let status = SecItemUpdate(query as CFDictionary, update as CFDictionary)
        if status == errSecItemNotFound {
            let insert: [CFString: Any] = [
                kSecClass: kSecClassGenericPassword,
                kSecAttrService: service,
                kSecAttrAccount: account,
                kSecValueData: data
            ]
            SecItemAdd(insert as CFDictionary, nil)
        }
    }

    static func clear() {
        let query: [CFString: Any] = [
            kSecClass: kSecClassGenericPassword,
            kSecAttrService: service,
            kSecAttrAccount: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}

@MainActor
@Observable
final class BackendSessionManager {
    static let shared = BackendSessionManager()

    private(set) var currentUser: BackendAuthUser?
    private(set) var pendingMessage: String?
    private(set) var pendingMessageIsError = false

    private var accessToken: String?
    private var refreshToken: String?
    private var isRefreshing = false

    var isConfigured: Bool {
        BackendConfig.baseURL != nil
    }

    var isAuthenticated: Bool {
        currentUser != nil && accessToken != nil && refreshToken != nil
    }

    private init() {
        if let cached = KeychainTokenStore.load() {
            currentUser = cached.user
            accessToken = cached.accessToken
            refreshToken = cached.refreshToken
        }
    }

    func clearPendingMessage() {
        pendingMessage = nil
        pendingMessageIsError = false
    }

    func accessTokenForRequest() -> String? {
        accessToken
    }

    func clearSession() {
        currentUser = nil
        accessToken = nil
        refreshToken = nil
        KeychainTokenStore.clear()
    }

    func checkSession() async {
        guard isConfigured else {
            clearSession()
            return
        }
        guard accessToken != nil else {
            currentUser = nil
            return
        }

        do {
            let me: BackendMePayload = try await BackendAPIClient.request(
                path: "v1/auth/me",
                method: "GET",
                authentication: .required
            )
            currentUser = BackendAuthUser(id: me.id, email: me.email, isAdmin: me.isAdmin)
            persistIfPossible()
        } catch {
            if await refreshSessionSilently() {
                do {
                    let me: BackendMePayload = try await BackendAPIClient.request(
                        path: "v1/auth/me",
                        method: "GET",
                        authentication: .required
                    )
                    currentUser = BackendAuthUser(id: me.id, email: me.email, isAdmin: me.isAdmin)
                    persistIfPossible()
                } catch {
                    clearSession()
                }
            } else {
                clearSession()
            }
        }
    }

    func login(email: String, password: String) async throws {
        let payload: BackendAuthPayload = try await BackendAPIClient.request(
            path: "v1/auth/login",
            method: "POST",
            body: ["email": email, "password": password],
            authentication: .none
        )
        applyAuthPayload(payload)
    }

    func register(email: String, password: String) async throws {
        let payload: BackendAuthPayload = try await BackendAPIClient.request(
            path: "v1/auth/register",
            method: "POST",
            body: ["email": email, "password": password],
            authentication: .none
        )
        applyAuthPayload(payload)
    }

    func forgotPassword(email: String) async throws {
        let _: BackendSuccessResponse = try await BackendAPIClient.request(
            path: "v1/auth/forgot-password",
            method: "POST",
            body: ["email": email],
            authentication: .none
        )
    }

    func resetPassword(token: String, newPassword: String) async throws {
        let _: BackendSuccessResponse = try await BackendAPIClient.request(
            path: "v1/auth/reset-password",
            method: "POST",
            body: ["token": token, "newPassword": newPassword],
            authentication: .none
        )
    }

    func logout() async {
        guard let refreshToken else {
            clearSession()
            return
        }

        do {
            let _: BackendSuccessResponse = try await BackendAPIClient.request(
                path: "v1/auth/logout",
                method: "POST",
                body: BackendRefreshRequest(refreshToken: refreshToken),
                authentication: .none
            )
        } catch {
            // Ignore remote logout failure and clear local session anyway.
        }

        clearSession()
    }

    func refreshSessionSilently() async -> Bool {
        if isRefreshing {
            return false
        }
        guard let refreshToken else {
            clearSession()
            return false
        }

        isRefreshing = true
        defer { isRefreshing = false }

        do {
            let payload: BackendAuthPayload = try await BackendAPIClient.request(
                path: "v1/auth/refresh",
                method: "POST",
                body: BackendRefreshRequest(refreshToken: refreshToken),
                authentication: .none
            )
            applyAuthPayload(payload)
            return true
        } catch {
            clearSession()
            return false
        }
    }

    private func applyAuthPayload(_ payload: BackendAuthPayload) {
        currentUser = payload.user
        accessToken = payload.accessToken
        refreshToken = payload.refreshToken
        persistIfPossible(expiresIn: payload.expiresIn)
    }

    private func persistIfPossible(expiresIn: Int = 0) {
        guard let currentUser, let accessToken, let refreshToken else {
            return
        }
        KeychainTokenStore.save(
            BackendTokenBundle(
                user: currentUser,
                accessToken: accessToken,
                refreshToken: refreshToken,
                expiresIn: expiresIn
            )
        )
    }
}
