import Foundation
import SwiftData

@Model
final class MaimaiIcon {
    @Attribute(.unique) var id: Int
    var name: String
    var descriptionText: String
    var genre: String

    init(id: Int, name: String, descriptionText: String, genre: String) {
        self.id = id
        self.name = name
        self.descriptionText = descriptionText
        self.genre = genre
    }

    var iconUrl: String {
        Self.avatarURL(for: id)
    }

    static func avatarURL(for id: Int) -> String {
        StaticAssetURL.presetAvatarURL(for: id)?.absoluteString ?? "https://assets2.lxns.net/maimai/icon/\(id).png"
    }

    static func isPresetAvatarURL(_ avatarURL: String?) -> Bool {
        StaticAssetURL.presetAvatarID(from: avatarURL) != nil
    }
}
