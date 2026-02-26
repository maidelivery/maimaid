import Foundation
import SwiftData

@Model
final class SyncConfig {
    // Diving Fish Credentials
    var dfUsername: String = ""
    var dfImportToken: String = ""
    
    // LXNS Credentials
    var lxnsRefreshToken: String = ""
    var lxnsClientId: String = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338"
    
    // User Profile Info
    var userName: String?
    var avatarUrl: String?
    var avatarData: Data?
    var isCustomProfile: Bool = false
    var playerRating: Int = 0
    var plate: String?
    
    // Sync Settings
    var isAutoUploadEnabled: Bool = false
    var backgroundSyncInterval: Int = 0 // 0 means disabled, otherwise in hours
    
    // Theme Settings
    var themeRawValue: Int = 0 // 0: System, 1: Light, 2: Dark
    
    // Last Sync Info
    var lastImportDateDF: Date?
    var lastImportDateLXNS: Date?
    var lastStaticDataUpdateDate: Date?
    
    // Best Table Settings
    var b35Count: Int = 35
    var b15Count: Int = 15
    
    init(dfUsername: String = "", 
         dfImportToken: String = "", 
         lxnsRefreshToken: String = "", 
         isAutoUploadEnabled: Bool = false,
         backgroundSyncInterval: Int = 0,
         themeRawValue: Int = 0,
         b35Count: Int = 35,
         b15Count: Int = 15) {
        self.dfUsername = dfUsername
        self.dfImportToken = dfImportToken
        self.lxnsRefreshToken = lxnsRefreshToken
        self.isAutoUploadEnabled = isAutoUploadEnabled
        self.backgroundSyncInterval = backgroundSyncInterval
        self.themeRawValue = themeRawValue
        self.b35Count = b35Count
        self.b15Count = b15Count
    }
}
