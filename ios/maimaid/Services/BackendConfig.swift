import Foundation

enum BackendConfig {
    static var baseURL: URL? {
        guard let value = AppInfo.configuredString(for: BundleInfoKeys.backendURL) else {
            return nil
        }
        return URL(string: value)
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
