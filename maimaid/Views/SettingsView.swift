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
                Section(header: Text("settings.data.header"), footer: Text("settings.data.footer")) {
                    NavigationLink(destination: StaticDataUpdateView()) {
                        HStack {
                            settingsRowLabel(
                                icon: MaimaiDataFetcher.shared.isSyncing ? "arrow.triangle.2.circlepath" : "arrow.down.circle.fill",
                                iconColor: .blue,
                                title: MaimaiDataFetcher.shared.isSyncing ? "settings.data.syncing" : "settings.data.updateAll"
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
                        settingsRowLabel(icon: "fish.fill", iconColor: .blue, title: "settings.data.importDivingFish")
                    }
                    
                    NavigationLink(destination: LxnsImportView()) {
                        HStack {
                            settingsRowLabel(icon: "snowflake", iconColor: .cyan, title: "settings.data.importLxns")
                            Spacer()
                            if let c = config, !c.lxnsRefreshToken.isEmpty {
                                Text("settings.data.bound").font(.caption).foregroundColor(.green)
                            }
                        }
                    }
                }
                
                Section(header: Text("settings.sync.header"), footer: Text("settings.sync.footer")) {
                    Toggle("settings.sync.autoUpload", isOn: Binding(
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
                Section("settings.appearance.header") {
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
                        Text("settings.appearance.system").tag(0)
                        Text("settings.appearance.light").tag(1)
                        Text("settings.appearance.dark").tag(2)
                    } label: {
                        settingsRowLabel(icon: "moon.fill", iconColor: .indigo, title: "settings.appearance.theme")
                    }
                }
                
                
                // About Section
                Section("settings.about.header") {
                    settingsRow(icon: "info.circle.fill", iconColor: .gray, title: "settings.about.version", value: "1.0.0")
                }
            }
            .navigationTitle("settings.title")
        }
    }
    
    private func settingsRow(icon: String, iconColor: Color, title: LocalizedStringKey, value: String) -> some View {
        HStack {
            settingsRowLabel(icon: icon, iconColor: iconColor, title: title)
            Spacer()
            Text(value)
                .font(.system(size: 15))
                .foregroundColor(.secondary)
        }
    }
    
    private func settingsRowLabel(icon: String, iconColor: Color, title: LocalizedStringKey) -> some View {
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
