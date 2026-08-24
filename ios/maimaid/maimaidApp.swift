//
//  maimaidApp.swift
//  maimaid
//
//  Created by 西 宮缄 on 2/23/26.
//

import SwiftData
import SwiftUI

@main
struct maimaidApp: App {
    @Environment(\.scenePhase) private var scenePhase

    private let sharedModelContainer: ModelContainer = {
        do {
            return try ModelContainer(
                for: Song.self,
                Sheet.self,
                Score.self,
                PlayRecord.self,
                SyncConfig.self,
                MaimaiIcon.self,
                UserProfile.self,
                CommunityAliasCache.self,
                SongCollection.self,
                SongCollectionItem.self
            )
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }()

    var body: some Scene {
        WindowGroup {
            MainTabView()
                .onOpenURL { url in
                    BackendSessionManager.shared.handleAuthRedirect(url)
                }
        }
        .modelContainer(sharedModelContainer)
        .onChange(of: scenePhase) { _, newPhase in
            guard newPhase == .active else { return }

            Task { @MainActor in
                guard BackendSessionManager.shared.isConfigured else { return }

                await BackendSessionManager.shared.checkSession()
                guard BackendSessionManager.shared.isAuthenticated,
                      let userId = BackendSessionManager.shared.currentUser?.id else {
                    return
                }

                let context = sharedModelContainer.mainContext
                let conflictState = AccountDataResolutionCoordinator.shared.detectConflictAfterAuth(
                    context: context,
                    currentUserId: userId
                )
                if !conflictState.requiresResolution {
                    try? await BackendIncrementalSyncService.pullUpdates(context: context, force: false)
                }

                await MaimaiDataFetcher.shared.syncApprovedCommunityAliasesIfNeeded(
                    container: sharedModelContainer
                )
            }
        }
    }
}
