import Foundation

enum BackendConfig {
    static var baseURL: URL? {
        guard let value = AppInfo.configuredString(for: BundleInfoKeys.backendURL) else {
            return nil
        }
        return URL(string: value)
    }

    static var webAuthBaseURL: URL? {
        if
            let configured = AppInfo.configuredString(for: BundleInfoKeys.backendAuthURL),
            let configuredURL = URL(string: configured)
        {
            return configuredURL
        }

        guard let baseURL else {
            return nil
        }

        // Local default: backend runs on :8787, dashboard auth page runs on :3000.
        if baseURL.port == 8787, let host = baseURL.host {
            if host == "localhost" || host == "127.0.0.1" {
                var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false)
                components?.port = 3000
                components?.query = nil
                components?.fragment = nil
                components?.path = "/"
                return components?.url
            }
        }

        return baseURL
    }
    
    static func endpoint(_ path: String) -> URL? {
        guard let baseURL else {
            return nil
        }

        let trimmed = path.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return baseURL
        }

        let segments = trimmed.split(separator: "?", maxSplits: 1, omittingEmptySubsequences: false)
        let pathSegment = String(segments[0])
        let pathURL = pathSegment.isEmpty ? baseURL : baseURL.appending(path: pathSegment)

        guard segments.count == 2 else {
            return pathURL
        }

        var components = URLComponents(url: pathURL, resolvingAgainstBaseURL: false)
        components?.percentEncodedQuery = String(segments[1])
        return components?.url
    }
}
