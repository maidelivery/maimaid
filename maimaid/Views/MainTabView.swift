import SwiftUI
import SwiftData

struct MainTabView: View {
    @Environment(\.modelContext) private var modelContext
    
    @State private var searchText = ""
    
    // First Launch Sync
    @State private var requiresInitialSync = !UserDefaults.standard.bool(forKey: "didPerformInitialSync")
    @State private var syncProgress = ""
    var body: some View {
        ZStack {
            TabView {
                Tab("Home", systemImage: "house") {
                    HomeView()
                }
                
                Tab("Scan", systemImage: "camera.viewfinder") {
                    ScannerView()
                }
                
                Tab("Settings", systemImage: "gearshape") {
                    SettingsView()
                }
                
                Tab("搜索", systemImage: "magnifyingglass", role: .search) {
                    ContentView(searchText: searchText)
                        .searchable(text: $searchText, prompt: "歌曲、艺术家、别名...")
                }
            }
            
            // Initial Sync Overlay
            if requiresInitialSync {
                ZStack {
                    Color.black.opacity(0.5)
                        .ignoresSafeArea()
                    
                    VStack(spacing: 24) {
                        Image(systemName: "arrow.down.circle.fill")
                            .font(.system(size: 56))
                            .foregroundColor(.blue)
                            .symbolEffect(.pulse)
                        
                        VStack(spacing: 8) {
                            Text("首次同步")
                                .font(.title3)
                                .fontWeight(.bold)
                            
                            Text("正在获取歌曲数据...")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                        
                        ProgressView()
                            .controlSize(.regular)
                        
                        if !syncProgress.isEmpty {
                            Text(syncProgress)
                                .font(.caption2)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(36)
                    .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 28))
                    .padding(40)
                }
                .transition(.opacity)
            }
        }
        .task {
            if requiresInitialSync {
                do {
                    try await MaimaiDataFetcher.shared.fetchSongs(modelContext: modelContext)
                    withAnimation {
                        requiresInitialSync = false
                    }
                } catch {
                    syncProgress = "同步失败: \(error.localizedDescription)"
                }
            }
        }
    }
}
