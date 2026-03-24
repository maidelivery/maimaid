import Foundation

enum SupabaseConfigError: LocalizedError {
    case missingURL
    case missingPublishableKey

    var errorDescription: String? {
        switch self {
        case .missingURL:
            return String(localized: "supabase.config.error.missingUrl")
        case .missingPublishableKey:
            return String(localized: "supabase.config.error.missingPublishableKey")
        }
    }
}

struct SupabaseConfig {
    private static func infoValue(forKey key: String) -> String? {
        AppInfo.configuredString(for: key)
    }

    static var projectURL: URL? {
        guard let value = infoValue(forKey: BundleInfoKeys.supabaseURL) else { return nil }
        return URL(string: value)
    }

    static var publishableKey: String? {
        infoValue(forKey: BundleInfoKeys.supabasePublishableKey)
    }

    static var configurationError: SupabaseConfigError? {
        if projectURL == nil {
            return .missingURL
        }

        if publishableKey == nil {
            return .missingPublishableKey
        }

        return nil
    }

    static var isConfigured: Bool {
        configurationError == nil
    }
}
