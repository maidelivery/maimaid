import SwiftUI
import SwiftData

struct StaticDataUpdateView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    
    private var config: SyncConfig? { configs.first }
    
    @State private var showingSyncAlert = false
    
    var body: some View {
        Form {
            Section(header: Text("同步控制")) {
                VStack(alignment: .leading, spacing: 12) {
                    if MaimaiDataFetcher.shared.isSyncing {
                        HStack {
                            ProgressView()
                                .padding(.trailing, 8)
                            Text(MaimaiDataFetcher.shared.currentStage.rawValue)
                                .font(.system(size: 15, weight: .medium))
                        }
                        
                        ProgressView(value: MaimaiDataFetcher.shared.progress, total: 1.0)
                            .tint(.blue)
                        
                        Text(MaimaiDataFetcher.shared.statusMessage)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    } else {
                        Button {
                            Task {
                                try? await MaimaiDataFetcher.shared.fetchSongs(modelContext: modelContext)
                            }
                        } label: {
                            HStack {
                                Image(systemName: "arrow.down.circle.fill")
                                Text("立即更新静态数据")
                                Spacer()
                                if let lastUpdate = config?.lastStaticDataUpdateDate {
                                    Text("上次: \(lastUpdate.formatted(date: .abbreviated, time: .shortened))")
                                        .font(.caption2)
                                        .foregroundColor(.secondary)
                                }
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                    }
                }
                .padding(.vertical, 8)
            }
            
            Section(header: Text("自动更新"), footer: Text("设定后台自动检查更新的频率。设置为 0 则禁用。")) {
                if let config = config {
                    HStack {
                        Text("更新间隔")
                        Spacer()
                        Picker("更新间隔", selection: Binding(
                            get: { config.backgroundSyncInterval },
                            set: { config.backgroundSyncInterval = $0 }
                        )) {
                            Text("禁用").tag(0)
                            Text("1 小时").tag(1)
                            Text("4 小时").tag(4)
                            Text("12 小时").tag(12)
                            Text("24 小时").tag(24)
                        }
                        .pickerStyle(.menu)
                    }
                }
            }
            
            Section(header: Text("调试信息")) {
                LabeledContent("歌曲总数", value: "\(songsCount())")
                if let lastDate = config?.lastStaticDataUpdateDate {
                    LabeledContent("最后刷新时间", value: lastDate.formatted(date: .numeric, time: .standard))
                }
            }
        }
        .navigationTitle("静态数据更新")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    private func songsCount() -> Int {
        let descriptor = FetchDescriptor<Song>()
        return (try? modelContext.fetchCount(descriptor)) ?? 0
    }
}

#Preview {
    StaticDataUpdateView()
}
