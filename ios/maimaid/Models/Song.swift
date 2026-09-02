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

    init(
        songIdentifier: String,
        category: String,
        title: String,
        artist: String,
        imageName: String,
        version: String? = nil,
        releaseDate: String? = nil,
        sortOrder: Int,
        bpm: Double? = nil,
        isNew: Bool,
        isLocked: Bool,
        comment: String? = nil,
        searchKeywords: String? = nil,
        aliases: [String] = []
    ) {
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

@Model
final class SongCollection {
    @Attribute(.unique) var id: UUID
    var name: String
    var sortIndex: Int
    var createdAt: Date
    var updatedAt: Date
    var clientUpdatedAt: Date?
    var deletedAt: Date?

    init(
        id: UUID = UUID(), name: String, sortIndex: Int = 0, createdAt: Date = .now, updatedAt: Date = .now,
        clientUpdatedAt: Date? = nil, deletedAt: Date? = nil
    ) {
        self.id = id
        self.name = name
        self.sortIndex = sortIndex
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.clientUpdatedAt = clientUpdatedAt
        self.deletedAt = deletedAt
    }
}

@Model
final class SongCollectionItem {
    @Attribute(.unique) var id: UUID
    var collectionId: UUID
    var songId: String
    var chartType: String
    var difficulty: String
    var position: Int
    var createdAt: Date
    var updatedAt: Date
    var clientUpdatedAt: Date?
    var deletedAt: Date?

    init(
        id: UUID = UUID(),
        collectionId: UUID,
        songId: String,
        chartType: String,
        difficulty: String,
        position: Int = 0,
        createdAt: Date = .now,
        updatedAt: Date = .now,
        clientUpdatedAt: Date? = nil,
        deletedAt: Date? = nil
    ) {
        self.id = id
        self.collectionId = collectionId
        self.songId = songId
        self.chartType = chartType
        self.difficulty = difficulty
        self.position = position
        self.createdAt = createdAt
        self.updatedAt = updatedAt
        self.clientUpdatedAt = clientUpdatedAt
        self.deletedAt = deletedAt
    }
}
