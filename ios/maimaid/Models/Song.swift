import Foundation
import SwiftData

@Model
final class Song {
    @Attribute(.unique) var songIdentifier: String
    var category: String
    var title: String
    var artist: String
    var imageName: String
    var version: String?
    var releaseDate: String?
    var sortOrder: Int
    var bpm: Double?
    var isNew: Bool
    var isLocked: Bool
    var comment: String?
    var searchKeywords: String?
    var aliases: [String] = []
    var songId: Int = 0
    var isFavorite: Bool = false
    
    @Relationship(deleteRule: .cascade, inverse: \Sheet.song)
    var sheets: [Sheet] = []
    
    init(songIdentifier: String, category: String, title: String, artist: String, imageName: String, version: String? = nil, releaseDate: String? = nil, sortOrder: Int, bpm: Double? = nil, isNew: Bool, isLocked: Bool, comment: String? = nil, searchKeywords: String? = nil, aliases: [String] = []) {
        self.songIdentifier = songIdentifier
        self.category = category
        self.title = title
        self.artist = artist
        self.imageName = imageName
        self.version = version
        self.releaseDate = releaseDate
        self.sortOrder = sortOrder
        self.bpm = bpm
        self.isNew = isNew
        self.isLocked = isLocked
        self.comment = comment
        self.searchKeywords = searchKeywords
        self.aliases = aliases
        self.isFavorite = false
    }
}
