import SwiftUI
import SwiftData

struct StaticDataUpdateView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    
    private var config: SyncConfig? { configs.first }
    
    @State private var statsLoaded = false
    @State private var totalSongs = 0
    @State private var totalSheets = 0
    @State private var utageSongs = 0
    @State private var songsWithAliases = 0
    @State private var sheetsWithInternalLevel = 0
    @State private var totalCategories = 0
    @State private var totalIcons = 0
    @State private var totalDanCategories = 0
    @State private var totalDanSections = 0
    @State private var isLoadingStats = false
    @State private var syncErrorMessage: String?
    
    @AppStorage("syncUpdateRemoteData") private var updateRemoteData = true
    @AppStorage("syncUpdateAliases") private var updateAliases = true
    @AppStorage("syncUpdateCovers") private var updateCovers = true
    @AppStorage("syncUpdateIcons") private var updateIcons = true
    @AppStorage("syncUpdateDanData") private var updateDanData = true
    
    var body: some View {
        Form {
            Section(header: Text("update.control.header")) {
                VStack(alignment: .leading, spacing: 12) {
                    if MaimaiDataFetcher.shared.isSyncing {
                        Text(LocalizedStringKey(MaimaiDataFetcher.shared.currentStage.rawValue))
                            .font(.system(size: 15, weight: .medium))
                        
                        ProgressView(value: MaimaiDataFetcher.shared.progress, total: 1.0)
                            .tint(.blue)
                        
                        HStack {
                            Text(MaimaiDataFetcher.shared.statusMessage)
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                            
                            Spacer()
                            
                            if MaimaiDataFetcher.shared.progress > 0 {
                                Text(MaimaiDataFetcher.shared.formattedETA)
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(.secondary)
                            }
                        }
                    } else {
                        Button {
                            let options = MaimaiDataFetcher.SyncOptions(
                                updateRemoteData: updateRemoteData,
                                updateAliases: updateAliases,
                                updateCovers: updateCovers,
                                updateIcons: updateIcons,
                                updateDanData: updateDanData
                            )
                            Task {
                                do {
                                    syncErrorMessage = nil
                                    try await MaimaiDataFetcher.shared.fetchSongs(modelContext: modelContext, options: options)
                                } catch {
                                    print("Manual sync failed: \(error)")
                                    syncErrorMessage = error.localizedDescription
                                }
                                await loadStatsAsync()
                            }
                        } label: {
                            HStack {
                                Image(systemName: "arrow.down.circle.fill")
                                Text("update.action.now")
                                Spacer()
                            }
                        }
                        .disabled(!updateRemoteData && !updateAliases && !updateCovers && !updateIcons && !updateDanData)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        
                        if !updateRemoteData && !updateAliases && !updateCovers && !updateIcons && !updateDanData {
                            Text("update.control.selectAtLeastOne")
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                    }
                    
                    if let syncErrorMessage, !syncErrorMessage.isEmpty {
                        Text(String(localized: "update.control.error \(syncErrorMessage)"))
                            .font(.system(size: 12))
                            .foregroundColor(.red)
                    }
                }
                .padding(.vertical, 8)
            }
            
            Section(header: Text("update.options.header"), footer: Text("update.options.footer")) {
                Toggle("update.option.remoteData", isOn: $updateRemoteData)
                Toggle("update.option.aliasesAndIds", isOn: $updateAliases)
                Toggle("update.option.coversMissing", isOn: $updateCovers)
                Toggle("update.option.iconsDataAndImages", isOn: $updateIcons)
                Toggle("update.option.danData", isOn: $updateDanData)
            }
            .disabled(MaimaiDataFetcher.shared.isSyncing)
            
            if !MaimaiDataFetcher.shared.syncLogs.isEmpty || MaimaiDataFetcher.shared.isSyncing {
                Section(header: Text("update.logs.header")) {
                    ScrollView {
                        Text(MaimaiDataFetcher.shared.syncLogs)
                            .font(.system(.caption, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(4)
                    }
                    .frame(height: 200)
                }
            }
            
            Section(header: Text("update.auto.header"), footer: Text("update.auto.footer")) {
                let currentInterval = config?.backgroundSyncInterval ?? 0
                HStack {
                    Picker("update.auto.interval", selection: Binding(
                        get: { currentInterval },
                        set: { newValue in
                            if let config = config {
                                config.backgroundSyncInterval = newValue
                            } else {
                                let newConfig = SyncConfig()
                                newConfig.backgroundSyncInterval = newValue
                                modelContext.insert(newConfig)
                            }
                        }
                    )) {
                        Text("update.interval.disabled").tag(0)
                        Text(String(localized: "update.interval.days.1")).tag(24)
                        Text(String(localized: "update.interval.days.7")).tag(168)
                        Text(String(localized: "update.interval.days.14")).tag(336)
                        Text(String(localized: "update.interval.days.30")).tag(720)
                    }
                    .pickerStyle(.menu)
                }
            }
            
            Section(header: Text("update.debug.header")) {
                if isLoadingStats {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                } else {
                    if let lastDate = config?.lastStaticDataUpdateDate {
                        LabeledContent("update.debug.lastUpdate", value: lastDate.formatted(date: .numeric, time: .standard))
                        
                        let interval = Date().timeIntervalSince(lastDate)
                        let days = Int(interval / 86400)
                        let hours = Int(interval.truncatingRemainder(dividingBy: 86400) / 3600)
                        let timeString = days > 0
                            ? String(localized: "update.debug.timeAgo.days \(days) \(hours)")
                            : String(localized: "update.debug.timeAgo.hours \(hours)")
                        LabeledContent("update.debug.timeSince", value: timeString)
                    } else {
                        LabeledContent("update.debug.lastUpdate", value: String(localized: "update.debug.never"))
                    }
                    
                    LabeledContent("update.debug.totalSongs", value: "\(totalSongs)")
                    LabeledContent("update.debug.totalSheets", value: "\(totalSheets)")
                    LabeledContent("update.debug.totalCategories", value: "\(totalCategories)")
                    LabeledContent("update.debug.utageSongs", value: "\(utageSongs)")
                    LabeledContent("update.debug.songsWithAliases", value: "\(songsWithAliases)")
                    LabeledContent("update.debug.knownInternalLevels", value: "\(sheetsWithInternalLevel)")
                    LabeledContent("update.debug.totalIcons", value: "\(totalIcons)")
                    LabeledContent("update.debug.totalDanCategories", value: "\(totalDanCategories)")
                    LabeledContent("update.debug.totalDanSections", value: "\(totalDanSections)")
                    
                    if let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
                        LabeledContent("update.debug.appVersion", value: appVersion)
                    }
                }
            }
        }
        .navigationTitle("update.title")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(MaimaiDataFetcher.shared.isSyncing)
        .navigationBarBackButtonHidden(MaimaiDataFetcher.shared.isSyncing)
        .task {
            if !statsLoaded {
                await loadStatsAsync()
                statsLoaded = true
            }
        }
    }
    
    private func loadStatsAsync() async {
        isLoadingStats = true
        
        let descriptor = FetchDescriptor<Song>()
        if let songs = try? modelContext.fetch(descriptor) {
            var sheets = 0
            var utage = 0
            var aliases = 0
            var internalLevels = 0
            var categories = Set<String>()
            
            for song in songs {
                categories.insert(song.category)
                sheets += song.sheets.count
                
                let lowerCategory = song.category.lowercased()
                if lowerCategory.contains("utage") || song.category.contains("宴") {
                    utage += 1
                }
                
                if !song.aliases.isEmpty {
                    aliases += 1
                }
                
                for sheet in song.sheets {
                    if sheet.internalLevelValue != nil {
                        internalLevels += 1
                    }
                }
            }
            
            totalSongs = songs.count
            totalSheets = sheets
            utageSongs = utage
            songsWithAliases = aliases
            sheetsWithInternalLevel = internalLevels
            totalCategories = categories.count
        }
        
        let iconDescriptor = FetchDescriptor<MaimaiIcon>()
        totalIcons = (try? modelContext.fetch(iconDescriptor))?.count ?? 0
        
        let danCategories = MaimaiDataFetcher.shared.loadCachedDanData()
        totalDanCategories = danCategories.count
        totalDanSections = danCategories.reduce(0) { $0 + $1.sections.count }
        
        isLoadingStats = false
    }
}

#Preview {
    StaticDataUpdateView()
}
