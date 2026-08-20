import SwiftUI
import SwiftData

struct DivingFishImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.openURL) private var openURL
    @Environment(\.scenePhase) private var scenePhase
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    @State private var isConnected = false
    @State private var connectedUsername: String?
    @State private var isAuthorizing = false
    @State private var isImporting = false
    @State private var importStatus: String = ""
    @State private var progress: Double = 0
    @State private var totalRecords: Int = 0
    @State private var importConflictPreview: ImportSyncConflictPreview?
    @State private var isResolvingImportConflict = false
    @State private var pendingUpsertedCount: Int = 0
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    private var hasBoundAccount: Bool { isConnected }
    
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
        .task(id: activeProfile?.id) {
            await refreshConnection()
        }
        .onChange(of: scenePhase) { _, phase in
            guard phase == .active else { return }
            Task {
                await refreshConnection()
            }
        }
        .sheet(item: $importConflictPreview) { preview in
            SyncConflictResolutionSheet(
                context: .importPreview(preview),
                isApplying: isResolvingImportConflict
            ) { action in
                Task {
                    await applyImportConflictResolutionAction(action, preview: preview)
                }
            }
            .interactiveDismissDisabled(true)
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
                    ? (connectedUsername ?? String(localized: "common.unknown"))
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
                    Text(connectedUsername ?? String(localized: "common.unknown"))
                        .foregroundStyle(.secondary)
                }
                
                actionRow(
                    title: isImporting ? "import.status.syncing" : "import.df.action.quickSync",
                    icon: "arrow.triangle.2.circlepath.circle.fill",
                    tint: .blue
                ) {
                    Task {
                        await importData()
                    }
                }
                .disabled(isImporting || isAuthorizing || isResolvingImportConflict)
                .opacity(isImporting || isAuthorizing || isResolvingImportConflict ? 0.6 : 1.0)
            }
            
            Section {
                Button("import.df.action.update", systemImage: "person.badge.key") {
                    Task {
                        await authorizeDivingFish()
                    }
                }
                .disabled(isImporting || isAuthorizing || isResolvingImportConflict)
            } header: {
                Text("import.df.rebind.header")
            } footer: {
                Text("import.df.rebind.footer")
            }
        } else {
            Section {
                Button("import.df.action.bindImport", systemImage: "person.badge.key") {
                    Task {
                        await authorizeDivingFish()
                    }
                }
                .disabled(isImporting || isAuthorizing || isResolvingImportConflict)
            } footer: {
                Text("import.df.setup.footer")
            }
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
                } else if isAuthorizing {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text("import.df.status.connecting")
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
    
    @MainActor
    private func refreshConnection() async {
        guard let profile = activeProfile else {
            isConnected = false
            connectedUsername = nil
            return
        }
        await BackendSessionManager.shared.checkSession()
        guard BackendSessionManager.shared.isAuthenticated else {
            isConnected = false
            connectedUsername = nil
            return
        }
        do {
            let connection = try await BackendImportService.divingFishConnection(
                profileId: profile.id.uuidString.lowercased()
            )
            isConnected = connection.connected
            connectedUsername = connection.externalUsername
        } catch {
            isConnected = false
            connectedUsername = nil
        }
    }

    @MainActor
    private func authorizeDivingFish() async {
        guard let profile = activeProfile else {
            importStatus = String(localized: "import.status.error.unknown")
            return
        }
        isAuthorizing = true
        importStatus = String(localized: "import.df.status.connecting")
        defer { isAuthorizing = false }

        do {
            await BackendSessionManager.shared.checkSession()
            guard BackendSessionManager.shared.isAuthenticated else {
                importStatus = String(localized: "community.alias.submit.loginRequired")
                return
            }
            try await BackendScoreSyncService.ensureProfileExists(profile: profile)
            let authorization = try await BackendImportService.authorizeDivingFish(
                profileId: profile.id.uuidString.lowercased()
            )
            guard let authorizationURL = URL(string: authorization.authorizationUrl),
                  authorizationURL.scheme == "https" else {
                throw BackendAPIError.badResponse
            }
            openURL(authorizationURL)
            importStatus = String(localized: "import.df.setup.footer")
        } catch {
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
    }

    @MainActor
    private func importData() async {
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

            let result = try await BackendImportService.importDivingFish(
                profileId: profile.id.uuidString.lowercased()
            )
            totalRecords = result.fetchedCount
            progress = Double(result.fetchedCount)

            if result.upsertedCount == 0 {
                try await BackendCloudSyncService.restoreFromCloud(context: modelContext)
                profile.lastImportDateDF = .now
                try modelContext.save()
                pendingUpsertedCount = 0
                importStatus = String(localized: "import.status.noChanges")
                isImporting = false
                return
            }

            importStatus = String(localized: "import.status.conflict.checking")
            let preview = try await BackendIncrementalSyncService.previewImportConflicts(
                context: modelContext,
                profileId: profile.id
            )
            if preview.hasConflicts {
                pendingUpsertedCount = result.upsertedCount
                importConflictPreview = preview
                importStatus = String(localized: "import.status.conflict.detected \(preview.conflicts.count)")
                isImporting = false
                return
            }

            importStatus = String(localized: "import.status.conflict.applying")
            try await BackendIncrementalSyncService.applyImportConflictResolution(
                .overwriteLocalWithImport,
                preview: preview,
                context: modelContext
            )
            try await BackendCloudSyncService.restoreFromCloud(context: modelContext)
            profile.lastImportDateDF = .now
            try modelContext.save()

            importStatus = String(localized: "import.status.success \(result.upsertedCount)")
        } catch {
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
        
        isImporting = false
    }

    @MainActor
    private func applyImportConflictResolutionAction(
        _ action: SyncConflictResolutionSheetAction,
        preview: ImportSyncConflictPreview
    ) async {
        guard !isResolvingImportConflict else { return }
        isResolvingImportConflict = true
        defer { isResolvingImportConflict = false }

        let option: ImportSyncResolutionOption
        switch action {
        case .merge:
            option = .mergeLocalAndImport
        case .keepLocal:
            option = .keepLocalAndImportRemoteOnly
        case .useRemote:
            option = .overwriteLocalWithImport
        }

        do {
            importStatus = String(localized: "import.status.conflict.applying")
            try await BackendIncrementalSyncService.applyImportConflictResolution(
                option,
                preview: preview,
                context: modelContext
            )
            try await BackendCloudSyncService.restoreFromCloud(context: modelContext)

            let targetProfileId = preview.profileId
            if let profile = try modelContext.fetch(
                FetchDescriptor<UserProfile>(predicate: #Predicate<UserProfile> { $0.id == targetProfileId })
            ).first {
                profile.lastImportDateDF = .now
            }
            try modelContext.save()
            importStatus = String(localized: "import.status.success \(pendingUpsertedCount)")
            importConflictPreview = nil
            pendingUpsertedCount = 0
        } catch {
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
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
            
            Label("import.df.summary.footer", systemImage: "square.and.arrow.down")
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
    
}
