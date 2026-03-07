import SwiftUI
import SwiftData

struct MainTabView: View {
    @Environment(\.modelContext) private var modelContext
    
    @State private var searchText = ""
    @Query private var configs: [SyncConfig]
    
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
}
