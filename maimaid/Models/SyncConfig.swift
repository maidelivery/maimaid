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
    
    // Sync Settings
    var isAutoUploadEnabled: Bool = false
    
    // Last Sync Info
    var lastImportDateDF: Date?
    var lastImportDateLXNS: Date?
    
    init(dfUsername: String = "", 
         dfImportToken: String = "", 
         lxnsRefreshToken: String = "", 
         isAutoUploadEnabled: Bool = false) {
        self.dfUsername = dfUsername
        self.dfImportToken = dfImportToken
        self.lxnsRefreshToken = lxnsRefreshToken
        self.isAutoUploadEnabled = isAutoUploadEnabled
    }
}
