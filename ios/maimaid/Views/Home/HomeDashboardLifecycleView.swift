import SwiftUI

struct HomeDashboardLifecycleView<Content: View>: View {
    let songsCount: Int
    let didPerformInitialSync: Bool
    let scoreCount: Int
    let activeB15Count: Int?
    let activeB35Count: Int?
    let configuredB15Count: Int?
    let configuredB35Count: Int?
    let activeProfileID: UUID?
    let activeServer: String?
    let onAppear: () -> Void
    let onSongsCountChanged: () -> Void
    let onInitialSyncChanged: () -> Void
    let onTask: () async -> Void
    let onScoresCountChanged: () -> Void
    let onScoresChanged: (Notification) -> Void
    let onCatalogChanged: () -> Void
    let onB50ConfigurationChanged: () -> Void
    let onActiveProfileChanged: () -> Void
    let onDisappear: () -> Void
    @ViewBuilder let content: Content

    var body: some View {
        content
            .onAppear(perform: onAppear)
            .onChange(of: songsCount) { _, _ in
                onSongsCountChanged()
            }
            .onChange(of: didPerformInitialSync) { _, _ in
                onInitialSyncChanged()
            }
            .task {
                await onTask()
            }
            .onChange(of: scoreCount) { _, _ in
                onScoresCountChanged()
            }
            .onReceive(NotificationCenter.default.publisher(for: .maimaiScoresDidChange), perform: onScoresChanged)
            .onReceive(NotificationCenter.default.publisher(for: .maimaiCatalogDidChange)) { _ in
                onCatalogChanged()
            }
            .onChange(of: activeB15Count) { _, _ in
                onB50ConfigurationChanged()
            }
            .onChange(of: activeB35Count) { _, _ in
                onB50ConfigurationChanged()
            }
            .onChange(of: configuredB15Count) { _, _ in
                onB50ConfigurationChanged()
            }
            .onChange(of: configuredB35Count) { _, _ in
                onB50ConfigurationChanged()
            }
            .onChange(of: activeProfileID) { _, _ in
                onActiveProfileChanged()
            }
            .onChange(of: activeServer) { _, _ in
                onB50ConfigurationChanged()
            }
            .onDisappear(perform: onDisappear)
    }
}
