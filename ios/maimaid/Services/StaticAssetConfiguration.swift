import Foundation

struct StaticAssetConfiguration: Decodable, Sendable {
    let coverBaseUrl: String
    let presetAvatarBaseUrl: String
    let coverFallbackBaseUrl: String?
    let presetAvatarFallbackBaseUrl: String?
}
