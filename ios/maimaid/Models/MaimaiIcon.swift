import Foundation
import SwiftData

@Model
final class MaimaiIcon {
    private static let iconBaseURL = "https://assets2.lxns.net/maimai/icon/"

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
        "\(iconBaseURL)\(id).png"
    }

    static func isPresetAvatarURL(_ avatarURL: String?) -> Bool {
        guard let avatarURL, let url = URL(string: avatarURL) else {
            return false
        }
        return url.scheme == "https"
            && url.host == "assets2.lxns.net"
            && url.path.hasPrefix("/maimai/icon/")
            && url.path.hasSuffix(".png")
    }
}
