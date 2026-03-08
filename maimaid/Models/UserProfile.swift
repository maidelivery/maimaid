import Foundation
import SwiftData

@Model
final class UserProfile {
    @Attribute(.unique) var id: UUID
    var name: String
    var server: String          // "jp", "intl", "cn", "usa"
    var avatarData: Data?
    var isActive: Bool
    var createdAt: Date
    
    // Credentials (per-user)
    var dfUsername: String = ""
    var dfImportToken: String = ""
    var lxnsRefreshToken: String = ""
    var lxnsClientId: String = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338"
    
    // Player info
    var playerRating: Int = 0
    var plate: String?
    
    // Sync timestamps (per-user)
    var lastImportDateDF: Date?
    var lastImportDateLXNS: Date?
    
    // B50 settings (per-user)
    var b35Count: Int = 35
    var b15Count: Int = 15
    var b35RecLimit: Int = 10
    var b15RecLimit: Int = 10
    
    init(
        id: UUID = UUID(),
        name: String = "",
        server: String = "jp",
        avatarData: Data? = nil,
        isActive: Bool = false,
        createdAt: Date = Date(),
        dfUsername: String = "",
        dfImportToken: String = "",
        lxnsRefreshToken: String = "",
        playerRating: Int = 0,
        plate: String? = nil,
        lastImportDateDF: Date? = nil,
        lastImportDateLXNS: Date? = nil,
        b35Count: Int = 35,
        b15Count: Int = 15,
        b35RecLimit: Int = 10,
        b15RecLimit: Int = 10
    ) {
        self.id = id
        self.name = name
        self.server = server
        self.avatarData = avatarData
        self.isActive = isActive
        self.createdAt = createdAt
        self.dfUsername = dfUsername
        self.dfImportToken = dfImportToken
        self.lxnsRefreshToken = lxnsRefreshToken
        self.playerRating = playerRating
        self.plate = plate
        self.lastImportDateDF = lastImportDateDF
        self.lastImportDateLXNS = lastImportDateLXNS
        self.b35Count = b35Count
        self.b15Count = b15Count
        self.b35RecLimit = b35RecLimit
        self.b15RecLimit = b15RecLimit
    }
}
