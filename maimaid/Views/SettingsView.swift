import SwiftUI
import SwiftData

struct SettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    
    private var config: SyncConfig? { configs.first }
    @State private var selectedTheme = 0
    
    var body: some View {
        NavigationStack {
            List {
                Section(header: Text("数据管理"), footer: Text("更新静态数据将同步最新的歌曲列表、别名以及官方 ID 映射。支持设置自动更新频率。")) {
                    NavigationLink(destination: StaticDataUpdateView()) {
                        HStack {
                            settingsRowLabel(
                                icon: MaimaiDataFetcher.shared.isSyncing ? "arrow.triangle.2.circlepath" : "arrow.down.circle.fill",
                                iconColor: .blue,
                                title: MaimaiDataFetcher.shared.isSyncing ? "正在更新静态数据..." : "更新所有静态数据"
                            )
                            Spacer()
                            if MaimaiDataFetcher.shared.isSyncing {
                                ProgressView()
                            } else if let lastDate = config?.lastStaticDataUpdateDate {
                                Text("\(lastDate.formatted(.dateTime.month().day().hour().minute()))")
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                    }
                    
                    NavigationLink {
                        DivingFishImportView()
                    } label: {
                        settingsRowLabel(icon: "fish.fill", iconColor: .blue, title: "从 Diving Fish 导入成绩")
                    }
                    
                    NavigationLink(destination: LxnsImportView()) {
                        HStack {
                            settingsRowLabel(icon: "snowflake", iconColor: .cyan, title: "从 LXNS 导入成绩")
                            Spacer()
                            if let c = config, !c.lxnsRefreshToken.isEmpty {
                                Text("已绑定").font(.caption).foregroundColor(.green)
                            }
                        }
                    }
                }
                
                Section(header: Text("成绩同步"), footer: Text("开启后，在保存成绩时，App 会自动将新成绩同步上传到你已绑定的外部查分器。")) {
                    Toggle("自动同步成绩", isOn: Binding(
                        get: { config?.isAutoUploadEnabled ?? false },
                        set: { newValue in
                            if let c = config {
                                c.isAutoUploadEnabled = newValue
                            } else {
                                let newConfig = SyncConfig(isAutoUploadEnabled: newValue)
                                modelContext.insert(newConfig)
                            }
                        }
                    ))
                }
                
                // Appearance Section
                Section("外观") {
                    Picker(selection: Binding(
                        get: { config?.themeRawValue ?? 0 },
                        set: { newValue in
                            if let c = config {
                                c.themeRawValue = newValue
                            } else {
                                let newConfig = SyncConfig(themeRawValue: newValue)
                                modelContext.insert(newConfig)
                            }
                        }
                    )) {
                        Text("跟随系统").tag(0)
                        Text("浅色").tag(1)
                        Text("深色").tag(2)
                    } label: {
                        settingsRowLabel(icon: "moon.fill", iconColor: .indigo, title: "主题")
                    }
                }
                
                
                // About Section
                Section("关于") {
                    settingsRow(icon: "info.circle.fill", iconColor: .gray, title: "版本", value: "1.0.0")
                }
            }
            .navigationTitle("设置")
        }
    }
    
    private func settingsRow(icon: String, iconColor: Color, title: String, value: String) -> some View {
        HStack {
            settingsRowLabel(icon: icon, iconColor: iconColor, title: title)
            Spacer()
            Text(value)
                .font(.system(size: 15))
                .foregroundColor(.secondary)
        }
    }
    
    private func settingsRowLabel(icon: String, iconColor: Color, title: String) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(.white)
                .frame(width: 28, height: 28)
                .background(iconColor, in: RoundedRectangle(cornerRadius: 6))
            
            Text(title)
                .font(.system(size: 16))
        }
    }
}

#Preview {
    SettingsView()
}
