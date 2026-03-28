import SwiftUI
import SwiftData
import UniformTypeIdentifiers

struct SettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    private var config: SyncConfig? { configs.first }
    private var activeProfile: UserProfile? { activeProfiles.first }
    private var hasLxnsBoundAccount: Bool {
        guard let activeProfile else {
            return false
        }
        return ProfileCredentialStore.shared.hasLxnsBinding(for: activeProfile.id)
    }
    @State private var selectedTheme = 0
    @AppStorage(AppStorageKeys.showScannerBoundingBox) private var showScannerBoundingBox: Bool = false
    
    @State private var alertTitle = ""
    @State private var alertMessage = ""
    @State private var showAlert = false

    private var appVersionText: String {
        AppInfo.versionDisplayString
    }
    
    var body: some View {
        NavigationStack {
            List {
                // User Management Section
                Section(header: Text("settings.userManagement.header"), footer: Text("settings.userManagement.footer")) {
                    NavigationLink {
                        UserProfileListView()
                    } label: {
                        settingsRowLabel(icon: "person.2.fill", iconColor: .purple, title: "settings.userManagement.title")
                    }
                }
                
                // Cloud Account & Sync Section
                Section(header: Text("settings.cloud.section.sync"), footer: Text("settings.cloud.privacy.hint")) {
                    NavigationLink {
                        StaticDataUpdateView()
                    } label: {
                        settingsRowLabel(icon: "arrow.down.circle.fill", iconColor: .blue, title: "update.title")
                    }
                    NavigationLink {
                        BackendAuthView()
                    } label: {
                        settingsRowLabel(icon: "cloud.fill", iconColor: .indigo, title: "settings.cloud.title")
                    }
                }
                
                // Data Import Section
                Section(header: Text("settings.sync.header"), footer: Text("settings.sync.footer")) {
                    NavigationLink {
                        DivingFishImportView()
                    } label: {
                        settingsRowLabel(icon: "fish.fill", iconColor: .blue, title: "settings.data.importDivingFish")
                    }
                    
                    NavigationLink(destination: LxnsImportView()) {
                        HStack {
                            settingsRowLabel(icon: "snowflake", iconColor: .cyan, title: "settings.data.importLxns")
                            Spacer()
                            if hasLxnsBoundAccount {
                                Text("settings.data.bound").font(.caption).foregroundStyle(.green)
                            }
                        }
                    }
                    
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
                    
                    Toggle(isOn: $showScannerBoundingBox) {
                        settingsRowLabel(icon: "viewfinder", iconColor: .green, title: "settings.appearance.showBoundingBox")
                    }
                }
                
                // About Section
                Section("settings.about.header") {
                    settingsRow(icon: "info.circle.fill", iconColor: .gray, title: "settings.about.version", value: appVersionText)
                }
            }
            .navigationTitle("settings.title")
            .alert(alertTitle, isPresented: $showAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(alertMessage)
            }
        }
    }
    
    // MARK: - Helper Views
    
    private func settingsRow(icon: String, iconColor: Color, title: LocalizedStringKey, value: String) -> some View {
        HStack {
            settingsRowLabel(icon: icon, iconColor: iconColor, title: title)
            Spacer()
            Text(value)
                .font(.system(size: 15))
                .foregroundStyle(.secondary)
        }
    }
    
    private func settingsRowLabel(icon: String, iconColor: Color, title: LocalizedStringKey) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundStyle(.white)
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

// MARK: - Share Sheet

struct ShareSheetView: UIViewControllerRepresentable {
    let items: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
