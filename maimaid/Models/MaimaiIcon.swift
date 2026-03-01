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
        "https://assets2.lxns.net/maimai/icon/\(id).png"
    }
}
