//
//  maimaidApp.swift
//  maimaid
//
//  Created by 西 宮缄 on 2/23/26.
//

import SwiftUI
import SwiftData
import BackgroundTasks

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
                CommunityAliasCache.self
            )
        } catch {
            fatalError("Failed to create ModelContainer: \(error)")
        }
    }()
    
    var body: some Scene {
        WindowGroup {
            MainTabView()
                .onOpenURL { url in
                    Task {
                        await SupabaseManager.shared.handleAuthRedirect(url)
                    }
                }
        }
        .modelContainer(sharedModelContainer)
        .backgroundTask(.appRefresh(StaticDataAutoUpdate.taskIdentifier)) {
            await StaticDataAutoUpdate.handleBackgroundRefresh(container: sharedModelContainer)
        }
        .backgroundTask(.appRefresh(SupabaseAutoBackup.taskIdentifier)) {
            await SupabaseAutoBackup.handleBackgroundBackup(container: sharedModelContainer)
        }
        .onChange(of: scenePhase) { _, newPhase in
            switch newPhase {
            case .active:
                Task {
                    await StaticDataAutoUpdate.refreshIfNeeded(
                        container: sharedModelContainer,
                        reason: "scenePhase.active"
                    )
                    await SupabaseAutoBackup.backupIfNeeded(
                        container: sharedModelContainer,
                        reason: "scenePhase.active"
                    )
                    await MaimaiDataFetcher.shared.syncApprovedCommunityAliasesIfNeeded(
                        container: sharedModelContainer
                    )
                }
            case .background:
                Task {
                    await StaticDataAutoUpdate.scheduleNextRefresh(container: sharedModelContainer)
                    await SupabaseAutoBackup.scheduleNextBackup(container: sharedModelContainer)
                }
            default:
                break
            }
        }
    }
}

enum StaticDataAutoUpdate {
    static let taskIdentifier = "in.shikoch.maimaid.static-data-refresh"
    private static let minimumLeadTime: TimeInterval = 15 * 60
    
    @MainActor
    static func refreshIfNeeded(container: ModelContainer, reason: String) async {
        guard !MaimaiDataFetcher.shared.isSyncing else { return }
        
        let context = ModelContext(container)
        guard let config = loadConfig(in: context) else { return }
        guard config.backgroundSyncInterval > 0 else {
            cancelScheduledRefresh()
            return
        }
        
        let options = configuredOptions()
        guard options.hasEnabledWork else {
            cancelScheduledRefresh()
            return
        }
        
        guard isRefreshDue(config: config) else { return }
        
        print("StaticDataAutoUpdate: foreground refresh triggered (\(reason))")
        
        do {
            try await MaimaiDataFetcher.shared.fetchSongs(modelContext: context, options: options)
        } catch {
            print("StaticDataAutoUpdate: foreground refresh failed: \(error)")
        }
    }
    
    @MainActor
    static func handleBackgroundRefresh(container: ModelContainer) async {
        guard !MaimaiDataFetcher.shared.isSyncing else {
            await scheduleNextRefresh(container: container)
            return
        }
        
        let context = ModelContext(container)
        guard let config = loadConfig(in: context), config.backgroundSyncInterval > 0 else {
            cancelScheduledRefresh()
            return
        }
        
        let options = configuredOptions()
        guard options.hasEnabledWork else {
            cancelScheduledRefresh()
            return
        }
        
        if isRefreshDue(config: config) {
            print("StaticDataAutoUpdate: background refresh triggered")
            
            do {
                try await MaimaiDataFetcher.shared.fetchSongs(modelContext: context, options: options)
            } catch {
                print("StaticDataAutoUpdate: background refresh failed: \(error)")
            }
        }
        
        await scheduleNextRefresh(container: container)
    }
    
    @MainActor
    static func scheduleNextRefresh(container: ModelContainer) async {
        let context = ModelContext(container)
        guard let config = loadConfig(in: context), config.backgroundSyncInterval > 0 else {
            cancelScheduledRefresh()
            return
        }
        
        let options = configuredOptions()
        guard options.hasEnabledWork else {
            cancelScheduledRefresh()
            return
        }
        
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        let dueDate = nextDueDate(for: config)
        request.earliestBeginDate = max(dueDate, Date().addingTimeInterval(minimumLeadTime))
        
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
        
        do {
            try BGTaskScheduler.shared.submit(request)
            print("StaticDataAutoUpdate: scheduled next refresh for \(request.earliestBeginDate ?? Date())")
        } catch {
            print("StaticDataAutoUpdate: failed to schedule refresh: \(error)")
        }
    }
    
    static func cancelScheduledRefresh() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
    }
    
    @MainActor
    private static func loadConfig(in context: ModelContext) -> SyncConfig? {
        (try? context.fetch(FetchDescriptor<SyncConfig>()))?.first
    }
    
    private static func isRefreshDue(config: SyncConfig, now: Date = Date()) -> Bool {
        now >= nextDueDate(for: config)
    }
    
    private static func nextDueDate(for config: SyncConfig) -> Date {
        let lastUpdate = config.lastStaticDataUpdateDate ?? .distantPast
        return lastUpdate.addingTimeInterval(TimeInterval(config.backgroundSyncInterval * 3600))
    }
    
    private static func configuredOptions() -> MaimaiDataFetcher.SyncOptions {
        let defaults = UserDefaults.app
        return .init(
            updateRemoteData: defaults.bool(forKey: AppStorageKeys.syncUpdateRemoteData, defaultValue: true),
            updateAliases: defaults.bool(forKey: AppStorageKeys.syncUpdateAliases, defaultValue: true),
            updateCovers: defaults.bool(forKey: AppStorageKeys.syncUpdateCovers, defaultValue: true),
            updateIcons: defaults.bool(forKey: AppStorageKeys.syncUpdateIcons, defaultValue: true),
            updateDanData: defaults.bool(forKey: AppStorageKeys.syncUpdateDanData, defaultValue: true),
            updateChartStats: defaults.bool(forKey: AppStorageKeys.syncUpdateChartStats, defaultValue: true),
            updateUtageChartStats: defaults.bool(forKey: AppStorageKeys.syncUpdateUtageChartStats, defaultValue: true)
        )
    }
}

private extension MaimaiDataFetcher.SyncOptions {
    var hasEnabledWork: Bool {
        updateRemoteData || updateAliases || updateCovers || updateIcons || updateDanData || updateChartStats || updateUtageChartStats
    }
}

private extension UserDefaults {
    func bool(forKey key: String, defaultValue: Bool) -> Bool {
        object(forKey: key) as? Bool ?? defaultValue
    }
}

enum SupabaseAutoBackup {
    static let taskIdentifier = "in.shikoch.maimaid.supabase-backup"
    private static let minimumLeadTime: TimeInterval = 15 * 60
    
    @MainActor
    static func backupIfNeeded(container: ModelContainer, reason: String) async {
        let context = ModelContext(container)
        guard let config = loadConfig(in: context) else { return }
        guard config.supabaseBackupInterval > 0 else {
            cancelScheduledBackup()
            return
        }
        guard SupabaseManager.shared.isConfigured else {
            cancelScheduledBackup()
            return
        }
        
        await SupabaseManager.shared.checkSession()
        guard SupabaseManager.shared.isAuthenticated else {
            cancelScheduledBackup()
            return
        }
        guard isBackupDue(config: config) else { return }
        
        print("SupabaseAutoBackup: foreground backup triggered (\(reason))")
        
        do {
            try await SupabaseManager.shared.backupToCloud(context: context)
        } catch {
            print("SupabaseAutoBackup: foreground backup failed: \(error)")
        }
    }
    
    @MainActor
    static func handleBackgroundBackup(container: ModelContainer) async {
        let context = ModelContext(container)
        guard let config = loadConfig(in: context), config.supabaseBackupInterval > 0 else {
            cancelScheduledBackup()
            return
        }
        guard SupabaseManager.shared.isConfigured else {
            cancelScheduledBackup()
            return
        }
        
        await SupabaseManager.shared.checkSession()
        guard SupabaseManager.shared.isAuthenticated else {
            cancelScheduledBackup()
            return
        }
        
        if isBackupDue(config: config) {
            print("SupabaseAutoBackup: background backup triggered")
            
            do {
                try await SupabaseManager.shared.backupToCloud(context: context)
            } catch {
                print("SupabaseAutoBackup: background backup failed: \(error)")
            }
        }
        
        await scheduleNextBackup(container: container)
    }
    
    @MainActor
    static func scheduleNextBackup(container: ModelContainer) async {
        let context = ModelContext(container)
        guard let config = loadConfig(in: context), config.supabaseBackupInterval > 0 else {
            cancelScheduledBackup()
            return
        }
        guard SupabaseManager.shared.isConfigured else {
            cancelScheduledBackup()
            return
        }
        
        await SupabaseManager.shared.checkSession()
        guard SupabaseManager.shared.isAuthenticated else {
            cancelScheduledBackup()
            return
        }
        
        let request = BGAppRefreshTaskRequest(identifier: taskIdentifier)
        let dueDate = nextDueDate(for: config)
        request.earliestBeginDate = max(dueDate, Date().addingTimeInterval(minimumLeadTime))
        
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
        
        do {
            try BGTaskScheduler.shared.submit(request)
            print("SupabaseAutoBackup: scheduled next backup for \(request.earliestBeginDate ?? Date())")
        } catch {
            print("SupabaseAutoBackup: failed to schedule backup: \(error)")
        }
    }
    
    static func cancelScheduledBackup() {
        BGTaskScheduler.shared.cancel(taskRequestWithIdentifier: taskIdentifier)
    }
    
    @MainActor
    private static func loadConfig(in context: ModelContext) -> SyncConfig? {
        (try? context.fetch(FetchDescriptor<SyncConfig>()))?.first
    }
    
    private static func isBackupDue(config: SyncConfig, now: Date = Date()) -> Bool {
        now >= nextDueDate(for: config)
    }
    
    private static func nextDueDate(for config: SyncConfig) -> Date {
        let lastBackup = config.lastSupabaseBackupDate ?? .distantPast
        return lastBackup.addingTimeInterval(TimeInterval(config.supabaseBackupInterval * 3600))
    }
}
