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
    
    @AppStorage("syncUpdateRemoteData") private var updateRemoteData = true
    @AppStorage("syncUpdateAliases") private var updateAliases = true
    @AppStorage("syncUpdateCovers") private var updateCovers = true
    
    var body: some View {
        Form {
            Section(header: Text("更新控制")) {
                VStack(alignment: .leading, spacing: 12) {
                    if MaimaiDataFetcher.shared.isSyncing {
                        Text(MaimaiDataFetcher.shared.currentStage.rawValue)
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
                                updateCovers: updateCovers
                            )
                            Task {
                                do {
                                    try await MaimaiDataFetcher.shared.fetchSongs(modelContext: modelContext, options: options)
                                } catch {
                                    print("Manual sync failed: \(error)")
                                }
                                loadStats()
                            }
                        } label: {
                            HStack {
                                Image(systemName: "arrow.down.circle.fill")
                                Text("立即更新静态数据")
                                Spacer()
                            }
                        }
                        .disabled(!updateRemoteData && !updateAliases && !updateCovers)
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.vertical, 8)
            }
            
            Section(header: Text("更新项选择")) {
                Toggle("基础数据", isOn: $updateRemoteData)
                Toggle("歌曲别名与歌曲 ID", isOn: $updateAliases)
                Toggle("歌曲封面", isOn: $updateCovers)
            }
            .disabled(MaimaiDataFetcher.shared.isSyncing)
            
            if !MaimaiDataFetcher.shared.syncLogs.isEmpty || MaimaiDataFetcher.shared.isSyncing {
                Section(header: Text("同步日志")) {
                    ScrollView {
                        Text(MaimaiDataFetcher.shared.syncLogs)
                            .font(.system(.caption, design: .monospaced))
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(4)
                    }
                    .frame(height: 200)
                }
            }
            
            Section(header: Text("自动更新"), footer: Text("设定后台自动检查更新的频率。设置为 0 则禁用。")) {
                let currentInterval = config?.backgroundSyncInterval ?? 0
                HStack {
                    Picker("更新间隔", selection: Binding(
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
                        Text("禁用").tag(0)
                        Text("1 天").tag(24)
                        Text("7 天").tag(168)
                        Text("14 天").tag(336)
                        Text("30 天").tag(720)
                    }
                    .pickerStyle(.menu)
                }
            }
            
            Section(header: Text("调试信息")) {
                if let lastDate = config?.lastStaticDataUpdateDate {
                    LabeledContent("最后刷新时间", value: lastDate.formatted(date: .numeric, time: .standard))
                    
                    let interval = Date().timeIntervalSince(lastDate)
                    let days = Int(interval / 86400)
                    let hours = Int(interval.truncatingRemainder(dividingBy: 86400) / 3600)
                    let timeString = days > 0 ? "\(days)天\(hours)小时前" : "\(hours)小时前"
                    LabeledContent("距离上次更新", value: timeString)
                } else {
                    LabeledContent("最后刷新时间", value: "从未更新")
                }
                
                LabeledContent("歌曲总数", value: "\(totalSongs)")
                LabeledContent("谱面总数", value: "\(totalSheets)")
                LabeledContent("分类数量", value: "\(totalCategories)")
                LabeledContent("宴会曲数量", value: "\(utageSongs)")
                LabeledContent("拥有别名曲目", value: "\(songsWithAliases)")
                LabeledContent("已知内部定数谱面", value: "\(sheetsWithInternalLevel)")
                
                if let appVersion = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String {
                    LabeledContent("App 版本", value: appVersion)
                }
            }
        }
        .navigationTitle("静态数据更新")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(MaimaiDataFetcher.shared.isSyncing)
        .navigationBarBackButtonHidden(MaimaiDataFetcher.shared.isSyncing)
        .onAppear {
            if !statsLoaded {
                loadStats()
                statsLoaded = true
            }
        }
    }
    
    private func loadStats() {
        let descriptor = FetchDescriptor<Song>()
        if let songs = try? modelContext.fetch(descriptor) {
            totalSongs = songs.count
            
            var sheets = 0
            var utage = 0
            var aliases = 0
            var internalLevels = 0
            var categories = Set<String>()
            
            for song in songs {
                categories.insert(song.category)
                sheets += song.sheets.count
                if song.category == "宴会場" {
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
            
            totalSheets = sheets
            utageSongs = utage
            songsWithAliases = aliases
            sheetsWithInternalLevel = internalLevels
            totalCategories = categories.count
        }
    }
}

#Preview {
    StaticDataUpdateView()
}
