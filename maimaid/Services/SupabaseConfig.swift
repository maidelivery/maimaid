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
        guard let value = Bundle.main.object(forInfoDictionaryKey: key) as? String else {
            return nil
        }

        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty, !trimmed.hasPrefix("$("), !trimmed.hasPrefix("__") else {
            return nil
        }

        return trimmed
    }

    static var projectURL: URL? {
        guard let value = infoValue(forKey: "SUPABASE_URL") else { return nil }
        return URL(string: value)
    }

    static var publishableKey: String? {
        infoValue(forKey: "SUPABASE_PUBLISHABLE_KEY")
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
