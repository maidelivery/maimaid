import Foundation

struct StaticAssetConfiguration: Decodable, Sendable {
    let coverBaseUrl: String
    let coverFallbackBaseUrl: String
    let presetAvatarBaseUrl: String
    let presetAvatarFallbackBaseUrl: String
}
