import Foundation

enum StaticAssetURL {
    nonisolated private static let legacyCoverBaseURL = URL(string: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/")
    nonisolated private static let legacyPresetAvatarBaseURL = URL(string: "https://assets2.lxns.net/maimai/icon/")
    nonisolated private static let coverBaseURLKey = "static.assets.coverBaseURL"
    nonisolated private static let coverFallbackBaseURLKey = "static.assets.coverFallbackBaseURL"
    nonisolated private static let presetAvatarBaseURLKey = "static.assets.presetAvatarBaseURL"
    nonisolated private static let presetAvatarFallbackBaseURLKey = "static.assets.presetAvatarFallbackBaseURL"

    nonisolated static func update(_ configuration: StaticAssetConfiguration?) {
        update(configuration?.coverBaseUrl, for: coverBaseURLKey)
        update(configuration?.coverFallbackBaseUrl, for: coverFallbackBaseURLKey)
        update(configuration?.presetAvatarBaseUrl, for: presetAvatarBaseURLKey)
        update(configuration?.presetAvatarFallbackBaseUrl, for: presetAvatarFallbackBaseURLKey)
    }

    nonisolated static func coverURL(for imageName: String) -> URL? {
        coverURLs(for: imageName).first
    }

    nonisolated static func coverURLs(for imageName: String) -> [URL] {
        candidateURLs(
            name: imageName,
            primaryKey: coverBaseURLKey,
            fallbackKey: coverFallbackBaseURLKey,
            legacyBaseURL: legacyCoverBaseURL
        )
    }

    nonisolated static func presetAvatarURL(for id: Int) -> URL? {
        presetAvatarURLs(for: id).first
    }

    nonisolated static func presetAvatarURLs(for id: Int) -> [URL] {
        candidateURLs(
            name: "\(id).png",
            primaryKey: presetAvatarBaseURLKey,
            fallbackKey: presetAvatarFallbackBaseURLKey,
            legacyBaseURL: legacyPresetAvatarBaseURL
        )
    }

    nonisolated static func presetAvatarID(from avatarURL: String?) -> Int? {
        guard let avatarURL, let url = URL(string: avatarURL), url.scheme == "https" else {
            return nil
        }
        let isLXNS = url.host == "assets2.lxns.net" && url.path.hasPrefix("/maimai/icon/")
        let isStaticWorker = url.path.contains("/lxns-icons/")
        guard isLXNS || isStaticWorker else { return nil }
        let fileName = url.lastPathComponent
        guard fileName.hasSuffix(".png") else { return nil }
        return Int(fileName.dropLast(4))
    }

    nonisolated private static func candidateURLs(
        name: String,
        primaryKey: String,
        fallbackKey: String,
        legacyBaseURL: URL?
    ) -> [URL] {
        let bases = [configuredURL(for: primaryKey), configuredURL(for: fallbackKey), legacyBaseURL]
        var seen = Set<URL>()
        return bases.compactMap { baseURL in
            guard let baseURL else { return nil }
            let url = baseURL.appending(path: name)
            return seen.insert(url).inserted ? url : nil
        }
    }

    nonisolated private static func configuredURL(for key: String) -> URL? {
        guard let value = UserDefaults.standard.string(forKey: key) else { return nil }
        return URL(string: normalizedImageTransformation(value))
    }

    nonisolated private static func update(_ value: String?, for key: String) {
        if let value {
            UserDefaults.standard.set(normalizedImageTransformation(value), forKey: key)
        } else {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }

    nonisolated private static func normalizedImageTransformation(_ value: String) -> String {
        value
            .replacing("/cdn-cgi/image/format=avif/", with: "/cdn-cgi/image/format=png/")
            .replacing("/cdn-cgi/image/f=avif/", with: "/cdn-cgi/image/format=png/")
            .replacing("/cdn-cgi/image/format=auto/", with: "/cdn-cgi/image/format=png/")
            .replacing("/cdn-cgi/image/f=auto/", with: "/cdn-cgi/image/format=png/")
            .replacing("/cdn-cgi/image/format=webp/", with: "/cdn-cgi/image/format=png/")
            .replacing("/cdn-cgi/image/f=webp/", with: "/cdn-cgi/image/format=png/")
    }
}
