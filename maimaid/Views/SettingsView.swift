import SwiftUI
import SwiftData
import UniformTypeIdentifiers

struct SettingsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    
    private var config: SyncConfig? { configs.first }
    @State private var selectedTheme = 0
    @AppStorage("showScannerBoundingBox") private var showScannerBoundingBox: Bool = false
    
    // Data transfer states
    @State private var showFileImporter = false
    @State private var showExportSheet = false
    @State private var exportFileURL: URL? = nil
    @State private var alertTitle = ""
    @State private var alertMessage = ""
    @State private var showAlert = false
    @State private var isExporting = false
    @State private var isImporting = false
    @State private var showImportConfirm = false
    @State private var pendingImportData: Data? = nil
    
    // iCloud states
    @State private var iCloudEnabled = DataTransferService.isICloudEnabled
    @State private var isBackingUp = false
    @State private var showRestoreConfirm = false
    @Environment(\.scenePhase) private var scenePhase
    
    private var hasStaticData: Bool {
        config?.lastStaticDataUpdateDate != nil
    }
    
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
                    
                }
                
                // User Management Section
                Section(header: Text("settings.userManagement.header"), footer: Text("settings.userManagement.footer")) {
                    NavigationLink {
                        UserProfileListView()
                    } label: {
                        settingsRowLabel(icon: "person.2.fill", iconColor: .purple, title: "settings.userManagement.title")
                    }
                }
                
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
                            if let c = config, !c.lxnsRefreshToken.isEmpty {
                                Text("settings.data.bound").font(.caption).foregroundColor(.green)
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
                
                // Data Transfer Section
                Section(header: Text("settings.transfer.header"), footer: Text("settings.transfer.footer")) {
                    // Export
                    Button {
                        performExport()
                    } label: {
                        HStack {
                            settingsRowLabel(icon: "square.and.arrow.up.fill", iconColor: .green, title: "settings.transfer.export")
                            Spacer()
                            if isExporting {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(!hasStaticData || isExporting)
                    
                    // Import
                    Button {
                        showFileImporter = true
                    } label: {
                        HStack {
                            settingsRowLabel(icon: "square.and.arrow.down.fill", iconColor: .orange, title: "settings.transfer.import")
                            Spacer()
                            if isImporting {
                                ProgressView()
                            }
                        }
                    }
                    .disabled(!hasStaticData || isImporting)
                }
                
                // iCloud Backup Section
                Section(header: Text("settings.icloud.header"), footer: Text(DataTransferService.isICloudAvailable ? "settings.icloud.footer" : "settings.icloud.unavailable")) {
                    Toggle(isOn: $iCloudEnabled) {
                        HStack {
                            settingsRowLabel(icon: "icloud.fill", iconColor: .blue, title: "settings.icloud.autoBackup")
                            Spacer()
                            if isBackingUp {
                                ProgressView()
                                    .padding(.trailing, 8)
                            }
                        }
                    }
                    .disabled(!hasStaticData || !DataTransferService.isICloudAvailable)
                    .onChange(of: iCloudEnabled) { _, newValue in
                        DataTransferService.isICloudEnabled = newValue
                        if newValue {
                            Task { await performICloudBackup() }
                        }
                    }
                    
                    if DataTransferService.isICloudAvailable {
                        // Show last backup date
                        if let lastDate = DataTransferService.lastICloudBackupDate {
                            HStack {
                                settingsRowLabel(icon: "clock.fill", iconColor: .gray, title: "settings.icloud.lastBackup")
                                Spacer()
                                Text(lastDate.formatted(.dateTime.month().day().hour().minute()))
                                    .font(.caption2)
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        // Manual backup button
                        Button {
                            Task { await performICloudBackup() }
                        } label: {
                            settingsRowLabel(icon: "arrow.clockwise.icloud.fill", iconColor: .teal, title: "settings.icloud.backupNow")
                        }
                        .disabled(!hasStaticData || !iCloudEnabled || isBackingUp)
                        
                        // Restore from iCloud
                        if DataTransferService.hasICloudBackup() {
                            Button {
                                showRestoreConfirm = true
                            } label: {
                                settingsRowLabel(icon: "icloud.and.arrow.down.fill", iconColor: .purple, title: "settings.icloud.restore")
                            }
                            .disabled(!hasStaticData)
                        }
                    }
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
                    settingsRow(icon: "info.circle.fill", iconColor: .gray, title: "settings.about.version", value: "1.0.0")
                }
            }
            .navigationTitle("settings.title")
            .fileImporter(isPresented: $showFileImporter, allowedContentTypes: [.json]) { result in
                handleFileImport(result)
            }
            .sheet(isPresented: $showExportSheet) {
                if let url = exportFileURL {
                    ShareSheetView(items: [url])
                }
            }
            .alert(alertTitle, isPresented: $showAlert) {
                Button("OK", role: .cancel) {}
            } message: {
                Text(alertMessage)
            }
            .alert("settings.transfer.importConfirmMessage", isPresented: $showImportConfirm) {
                Button("settings.transfer.importAction", role: .destructive) {
                    confirmImport()
                }
                Button("settings.transfer.cancel", role: .cancel) {
                    pendingImportData = nil
                }
            } message: {
                Text("settings.transfer.importConfirmMessage")
            }
            .alert("settings.icloud.restoreConfirm", isPresented: $showRestoreConfirm) {
                Button("settings.icloud.restoreAction", role: .destructive) {
                    performICloudRestore()
                }
                Button("settings.transfer.cancel", role: .cancel) {}
            } message: {
                Text("settings.icloud.restoreConfirmMessage")
            }
            .onChange(of: scenePhase) { _, newPhase in
                if newPhase == .background && DataTransferService.isICloudEnabled && hasStaticData {
                    Task { await performICloudBackup() }
                }
            }
        }
    }
    
    // MARK: - Export
    
    private func performExport() {
        isExporting = true
        do {
            let data = try DataTransferService.exportData(context: modelContext)
            
            let formatter = DateFormatter()
            formatter.dateFormat = "yyyyMMdd_HHmmss"
            let fileName = "maimaid_backup_\(formatter.string(from: Date())).json"
            
            let tempURL = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
            try data.write(to: tempURL)
            
            exportFileURL = tempURL
            showExportSheet = true
        } catch {
            alertTitle = NSLocalizedString("settings.transfer.error", comment: "")
            alertMessage = error.localizedDescription
            showAlert = true
        }
        isExporting = false
    }
    
    // MARK: - Import
    
    private func handleFileImport(_ result: Result<URL, Error>) {
        switch result {
        case .success(let url):
            guard url.startAccessingSecurityScopedResource() else {
                alertTitle = NSLocalizedString("settings.transfer.error", comment: "")
                alertMessage = "Cannot access the selected file."
                showAlert = true
                return
            }
            defer { url.stopAccessingSecurityScopedResource() }
            
            do {
                let data = try Data(contentsOf: url)
                // Validate before confirming
                let decoder = JSONDecoder()
                decoder.dateDecodingStrategy = .iso8601
                _ = try decoder.decode(TransferData.self, from: data)
                
                pendingImportData = data
                showImportConfirm = true
            } catch {
                alertTitle = NSLocalizedString("settings.transfer.error", comment: "")
                alertMessage = error.localizedDescription
                showAlert = true
            }
        case .failure(let error):
            alertTitle = NSLocalizedString("settings.transfer.error", comment: "")
            alertMessage = error.localizedDescription
            showAlert = true
        }
    }
    
    private func confirmImport() {
        guard let data = pendingImportData else { return }
        isImporting = true
        
        do {
            let summary = try DataTransferService.importData(from: data, context: modelContext)
            alertTitle = NSLocalizedString("settings.transfer.importSuccess", comment: "")
            alertMessage = String(
                format: NSLocalizedString("settings.transfer.importSummary", comment: ""),
                summary.scoresImported, summary.favoritesRestored
            )
            showAlert = true
        } catch {
            alertTitle = NSLocalizedString("settings.transfer.error", comment: "")
            alertMessage = error.localizedDescription
            showAlert = true
        }
        
        pendingImportData = nil
        isImporting = false
    }
    
    // MARK: - iCloud Backup
    
    private func performICloudBackup() async {
        await MainActor.run { isBackingUp = true }
        do {
            try await DataTransferService.backupToICloud(context: modelContext)
        } catch {
            await MainActor.run {
                alertTitle = NSLocalizedString("settings.transfer.error", comment: "")
                alertMessage = error.localizedDescription
                showAlert = true
            }
        }
        await MainActor.run { isBackingUp = false }
    }
    
    private func performICloudRestore() {
        do {
            if let summary = try DataTransferService.restoreFromICloud(context: modelContext) {
                alertTitle = NSLocalizedString("settings.icloud.restoreSuccess", comment: "")
                alertMessage = String(
                    format: NSLocalizedString("settings.transfer.importSummary", comment: ""),
                    summary.scoresImported, summary.favoritesRestored
                )
                showAlert = true
            }
        } catch {
            alertTitle = NSLocalizedString("settings.transfer.error", comment: "")
            alertMessage = error.localizedDescription
            showAlert = true
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

// MARK: - Share Sheet

struct ShareSheetView: UIViewControllerRepresentable {
    let items: [Any]
    
    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    
    func updateUIViewController(_ uiViewController: UIActivityViewController, context: Context) {}
}
