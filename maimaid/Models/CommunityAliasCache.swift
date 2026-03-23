import Foundation
import SwiftData

@Model
final class CommunityAliasCache {
    @Attribute(.unique) var remoteId: String
    var songIdentifier: String
    var aliasText: String
    var status: String
    var voteOpenAt: Date?
    var voteCloseAt: Date?
    var approvedAt: Date?
    var updatedAt: Date
    var createdAt: Date

    init(
        remoteId: String,
        songIdentifier: String,
        aliasText: String,
        status: String,
        voteOpenAt: Date? = nil,
        voteCloseAt: Date? = nil,
        approvedAt: Date? = nil,
        updatedAt: Date = Date(),
        createdAt: Date = Date()
    ) {
        self.remoteId = remoteId
        self.songIdentifier = songIdentifier
        self.aliasText = aliasText
        self.status = status
        self.voteOpenAt = voteOpenAt
        self.voteCloseAt = voteCloseAt
        self.approvedAt = approvedAt
        self.updatedAt = updatedAt
        self.createdAt = createdAt
    }
}
