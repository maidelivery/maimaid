import SwiftUI
import SwiftData

struct MainTabView: View {
    @Environment(\.modelContext) private var modelContext
    
    @State private var searchText = ""
    @Query private var configs: [SyncConfig]
    @Query private var profiles: [UserProfile]
    
    private var preferredScheme: ColorScheme? {
        switch configs.first?.themeRawValue ?? 0 {
        case 1: return .light
        case 2: return .dark
        default: return nil
        }
    }
    
    var body: some View {
        TabView {
            Tab("tab.home", systemImage: "house") {
                HomeView()
            }
            
            Tab("tab.scan", systemImage: "camera.viewfinder") {
                ScannerView()
            }
            
            Tab("tab.settings", systemImage: "gearshape") {
                SettingsView()
            }
            
            Tab("tab.search", systemImage: "magnifyingglass", role: .search) {
                SongsView(searchText: searchText)
                    .searchable(text: $searchText, prompt: "search.placeholder")
            }
        }
        .preferredColorScheme(preferredScheme)
        .task {
            // Migrate legacy SyncConfig data to UserProfile (one-time)
            migrateToUserProfileIfNeeded()
            
            // Reconnect orphaned scores due to Model relationship changes
            fixOrphanedScores()
            
            // Force a data sync if regions are missing (e.g. regionCn is false for all)
            await checkAndForceDataSync()
            
            // Background Sync Check
            if let config = configs.first, config.backgroundSyncInterval > 0 {
                let lastSync = config.lastStaticDataUpdateDate ?? .distantPast
                let intervalSeconds = Double(config.backgroundSyncInterval * 3600)
                
                if Date().timeIntervalSince(lastSync) > intervalSeconds {
                    print("MainTabView: Background static data sync triggered (interval: \(config.backgroundSyncInterval)h)")
                    try? await MaimaiDataFetcher.shared.fetchSongs(modelContext: modelContext)
                }
            }
        }
        
    }
    
    // MARK: - Migration
    
    private func migrateToUserProfileIfNeeded() {
        guard let config = configs.first, !config.didMigrateToUserProfile else { return }
        
        // Only migrate if there are no profiles yet
        guard profiles.isEmpty else {
            config.didMigrateToUserProfile = true
            try? modelContext.save()
            return
        }
        
        // Create default profile from legacy SyncConfig data
        let profile = UserProfile(
            name: config.userName ?? String(localized: "userProfile.defaultName"),
            server: "jp", // Default to JP server
            avatarData: config.avatarData,
            isActive: true,
            dfUsername: config.dfUsername,
            dfImportToken: config.dfImportToken,
            lxnsRefreshToken: config.lxnsRefreshToken,
            playerRating: config.playerRating,
            plate: config.plate,
            b35Count: config.b35Count,
            b15Count: config.b15Count,
            b35RecLimit: config.b35RecLimit,
            b15RecLimit: config.b15RecLimit
        )
        modelContext.insert(profile)
        
        // Associate existing scores with the new profile
        let scoreDescriptor = FetchDescriptor<Score>()
        if let scores = try? modelContext.fetch(scoreDescriptor) {
            for score in scores {
                if score.userProfileId == nil {
                    score.userProfileId = profile.id
                }
            }
        }
        
        config.didMigrateToUserProfile = true
        try? modelContext.save()
        
        print("MainTabView: Migrated legacy config to default UserProfile (id: \(profile.id))")
    }
    
    private func fixOrphanedScores() {
        if UserDefaults.app.didFixOrphanedScoresMigration { return }
        
        let scoreDescriptor = FetchDescriptor<Score>()
        guard let scores = try? modelContext.fetch(scoreDescriptor) else { return }
        
        let sheetDescriptor = FetchDescriptor<Sheet>()
        guard let sheets = try? modelContext.fetch(sheetDescriptor) else { return }
        
        var sheetMap: [String: Sheet] = [:]
        for sheet in sheets {
            let key = "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
            sheetMap[key] = sheet
        }
        
        var fixedCount = 0
        for score in scores {
            if let sheet = sheetMap[score.sheetId] {
                // Ensure the relationship is established
                if !sheet.scores.contains(where: { $0.id == score.id }) {
                    sheet.scores.append(score)
                    score.sheet = sheet
                    fixedCount += 1
                } else if score.sheet == nil {
                    // Just fix the back-reference if needed
                    score.sheet = sheet
                    fixedCount += 1
                }
            }
        }
        
        if fixedCount > 0 {
            try? modelContext.save()
            print("MainTabView: Re-attached \(fixedCount) orphaned scores to their sheets.")
        }
        UserDefaults.app.didFixOrphanedScoresMigration = true
    }
    
    private func checkAndForceDataSync() async {
        if UserDefaults.app.didForceRegionSyncMigration { return }
        
        // If the database has sheets but none have regionCn set to true, it means they are using
        // the default unmigrated values. We need to force a sync to populate the actual regions.
        var descriptor = FetchDescriptor<Sheet>()
        descriptor.fetchLimit = 100 // Just check a sample
        if let sheets = try? modelContext.fetch(descriptor), !sheets.isEmpty {
            let hasAnyCn = sheets.contains { $0.regionCn }
            if !hasAnyCn {
                print("MainTabView: No songs with regionCn=true found. Forcing data sync to populate regions...")
                // We run this detached to not block the main UI if it takes long
                let container = modelContext.container
                Task.detached {
                    let backgroundContext = SwiftData.ModelContext(container)
                    try? await MaimaiDataFetcher.shared.fetchSongs(modelContext: backgroundContext)
                }
            }
        }
        UserDefaults.app.didForceRegionSyncMigration = true
    }
}
