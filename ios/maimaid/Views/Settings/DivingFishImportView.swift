import SwiftUI
import SwiftData

struct DivingFishImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    @State private var username: String = ""
    @State private var importToken: String = ""
    @State private var isImporting = false
    @State private var importStatus: String = ""
    @State private var progress: Double = 0
    @State private var totalRecords: Int = 0
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    private var hasBoundAccount: Bool { !(activeProfile?.dfUsername.isEmpty ?? true) }
    
    private var statusTint: Color {
        let failedText = String(localized: "import.status.failed")
        let errorText = String(localized: "import.status.error")
        return importStatus.contains(failedText) || importStatus.contains(errorText) ? .red : .blue
    }
    
    var body: some View {
        ZStack(alignment: .bottom) {
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()
            
            List {
                summarySection
                formSection
                
                if !importStatus.isEmpty || isImporting {
                    statusSection
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationTitle("import.df.title")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            if let profile = activeProfile {
                username = profile.dfUsername
                importToken = profile.dfImportToken
            }
        }
    }
    
    @ViewBuilder
    private var summarySection: some View {
        Section {
            accountSummaryCard(
                icon: hasBoundAccount ? "fish.circle.fill" : "person.crop.circle.badge.plus",
                iconTint: hasBoundAccount ? .blue : .orange,
                title: String(localized: hasBoundAccount ? "import.df.bound.header" : "import.df.setup.header"),
                subtitle: hasBoundAccount
                    ? (activeProfile?.dfUsername ?? String(localized: "common.unknown"))
                    : String(localized: "import.df.setup.footer")
            )
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
        .listRowBackground(Color.clear)
        .listSectionSeparator(.hidden)
    }
    
    @ViewBuilder
    private var formSection: some View {
        if hasBoundAccount {
            Section("import.df.bound.header") {
                HStack(spacing: 12) {
                    settingsIcon(icon: "person.text.rectangle.fill", color: .blue)
                    Text("import.df.username")
                    Spacer()
                    Text(activeProfile?.dfUsername ?? String(localized: "common.unknown"))
                        .foregroundStyle(.secondary)
                }
                
                actionRow(
                    title: isImporting ? "import.status.syncing" : "import.df.action.quickSync",
                    icon: "arrow.triangle.2.circlepath.circle.fill",
                    tint: .blue
                ) {
                    Task {
                        if let name = activeProfile?.dfUsername {
                            await importData(userName: name)
                        }
                    }
                }
                .disabled(isImporting)
                .opacity(isImporting ? 0.6 : 1.0)
            }
            
            Section {
                credentialField(
                    title: "import.df.username.placeholder",
                    text: $username,
                    icon: "person.fill"
                )
                
                credentialField(
                    title: "import.df.token.placeholder",
                    text: $importToken,
                    icon: "key.fill",
                    isSecure: true
                )
                
                Button("import.df.action.update") {
                    updateConfig()
                }
                .disabled(username.isEmpty)
            } header: {
                Text("import.df.rebind.header")
            } footer: {
                Text("import.df.rebind.footer")
            }
        } else {
            Section {
                credentialField(
                    title: "import.df.username.placeholder",
                    text: $username,
                    icon: "person.fill"
                )
                
                credentialField(
                    title: "import.df.token.setup.placeholder",
                    text: $importToken,
                    icon: "key.fill",
                    isSecure: true
                )
            } footer: {
                Text("import.df.setup.footer")
            }
            
            Section {
                Button {
                    updateConfig()
                    Task {
                        await importData(userName: username)
                    }
                } label: {
                    HStack {
                        Spacer()
                        if isImporting {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text("import.df.action.bindImport")
                                .fontWeight(.semibold)
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(username.isEmpty || isImporting)
                .listRowBackground(Color.clear)
            }
            .listSectionSeparator(.hidden)
        }
    }
    
    @ViewBuilder
    private var statusSection: some View {
        Section("import.status.header") {
            VStack(alignment: .leading, spacing: 14) {
                if !importStatus.isEmpty {
                    Label {
                        Text(importStatus)
                            .foregroundStyle(statusTint)
                            .fixedSize(horizontal: false, vertical: true)
                    } icon: {
                        Image(systemName: statusTint == .red ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                            .foregroundStyle(statusTint)
                    }
                }
                
                if totalRecords > 0 {
                    VStack(alignment: .leading, spacing: 8) {
                        ProgressView(value: progress, total: Double(totalRecords))
                            .tint(statusTint)
                        Text("\(Int(progress)) / \(totalRecords)")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                } else if isImporting {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("import.status.importing")
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
    
    private func updateConfig() {
        if let profile = activeProfile {
            profile.dfUsername = username
            if !importToken.isEmpty {
                profile.dfImportToken = importToken
            }
        }
        
        if configs.isEmpty {
            let newConfig = SyncConfig()
            newConfig.dfUsername = username
            if !importToken.isEmpty {
                newConfig.dfImportToken = importToken
            }
            modelContext.insert(newConfig)
        }
    }
    
    @MainActor
    private func importData(userName: String? = nil) async {
        let targetUser = userName ?? username
        guard !targetUser.isEmpty else { return }
        guard let profile = activeProfile else {
            importStatus = String(localized: "import.status.error.unknown")
            return
        }
        
        isImporting = true
        importStatus = String(localized: "import.df.status.connecting")
        progress = 0
        totalRecords = 0

        do {
            await BackendSessionManager.shared.checkSession()
            guard BackendSessionManager.shared.isAuthenticated else {
                importStatus = String(localized: "community.alias.submit.loginRequired")
                isImporting = false
                return
            }

            try await BackendScoreSyncService.ensureProfileExists(profile: profile)

            let isQQ = Int(targetUser) != nil && targetUser.count > 5
            let result = try await BackendImportService.importDivingFish(
                profileId: profile.id.uuidString.lowercased(),
                username: isQQ ? nil : targetUser,
                qq: isQQ ? targetUser : nil
            )
            totalRecords = result.fetchedCount
            progress = Double(result.fetchedCount)

            try await BackendIncrementalSyncService.pullUpdates(
                context: modelContext,
                profileId: profile.id,
                force: false
            )
            profile.lastImportDateDF = Date()
            try modelContext.save()

            importStatus = String(localized: "import.status.success \(result.upsertedCount)")
        } catch {
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
        
        isImporting = false
    }
}

private extension DivingFishImportView {
    func settingsIcon(icon: String, color: Color) -> some View {
        Image(systemName: icon)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 32, height: 32)
            .background(color.gradient, in: RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
    
    func accountSummaryCard(icon: String, iconTint: Color, title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                RoundedRectangle(cornerRadius: 18, style: .continuous)
                    .fill(iconTint.gradient)
                    .frame(width: 72, height: 72)
                    .overlay {
                        Image(systemName: icon)
                            .font(.system(size: 30, weight: .semibold))
                            .foregroundStyle(.white)
                    }
                
                VStack(alignment: .leading, spacing: 6) {
                    Text(title)
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                    Text(subtitle)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                }
                
                Spacer(minLength: 0)
            }
            
            Divider()
            
            Label("settings.sync.footer", systemImage: "lock.shield.fill")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(20)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 28, style: .continuous))
    }
    
    func actionRow(title: String, icon: String, tint: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                settingsIcon(icon: icon, color: tint)
                Text(LocalizedStringKey(title))
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "arrow.up.forward.app")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
            }
        }
        .buttonStyle(.plain)
    }
    
    func credentialField(title: String, text: Binding<String>, icon: String, isSecure: Bool = false) -> some View {
        HStack(spacing: 12) {
            settingsIcon(icon: icon, color: .gray)
            Group {
                if isSecure {
                    SecureField(LocalizedStringKey(title), text: text)
                        .textInputAutocapitalization(.never)
                } else {
                    TextField(LocalizedStringKey(title), text: text)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                }
            }
        }
        .padding(.vertical, 2)
    }
}
