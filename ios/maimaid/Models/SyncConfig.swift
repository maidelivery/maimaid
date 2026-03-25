import Foundation
import SwiftData

@Model
final class SyncConfig {
    // Sync Settings
    var isAutoUploadEnabled: Bool = false
    var backgroundSyncInterval: Int = 0 // 0 means disabled, otherwise in hours
    @Attribute(originalName: "supabaseBackupInterval")
    var cloudBackupInterval: Int = 0 // 0 means disabled, otherwise in hours
    
    // Theme Settings
    var themeRawValue: Int = 0 // 0: System, 1: Light, 2: Dark
    
    // Last Sync Info
    var lastImportDateDF: Date?
    var lastImportDateLXNS: Date?
    var lastStaticDataUpdateDate: Date?
    @Attribute(originalName: "lastSupabaseBackupDate")
    var lastCloudBackupDate: Date?
    var lastSyncRevision: String = "0"
    
    // Legacy fields — kept for migration, will be read once to create default UserProfile
    var userName: String?
    var avatarUrl: String?
    var avatarData: Data?
    var isCustomProfile: Bool = false
    var playerRating: Int = 0
    var plate: String?
    var dfUsername: String = ""
    var dfImportToken: String = ""
    var lxnsRefreshToken: String = ""
    var lxnsClientId: String = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338"
    var b35Count: Int = 35
    var b15Count: Int = 15
    var b35RecLimit: Int = 10
    var b15RecLimit: Int = 10
    
    // Migration flag
    var didMigrateToUserProfile: Bool = false
    
    init(isAutoUploadEnabled: Bool = false,
         backgroundSyncInterval: Int = 0,
         cloudBackupInterval: Int = 0,
         themeRawValue: Int = 0) {
        self.isAutoUploadEnabled = isAutoUploadEnabled
        self.backgroundSyncInterval = backgroundSyncInterval
        self.cloudBackupInterval = cloudBackupInterval
        self.themeRawValue = themeRawValue
    }
}
