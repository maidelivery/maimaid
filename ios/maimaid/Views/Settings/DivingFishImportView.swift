import SwiftData
import SwiftUI

struct DivingFishImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.openURL) private var openURL
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]

    @State private var isConnected = false
    @State private var canSyncScores = false
    @State private var connectedUsername: String?
    @State private var isImporting = false
    @State private var importStatus = ""
    @State private var totalRecords = 0
    @State private var importConflictPreview: ImportSyncConflictPreview?
    @State private var isResolvingImportConflict = false
    @State private var pendingUpsertedCount = 0
    @State private var operationTask: Task<Void, Never>?

    private var activeProfile: UserProfile? { activeProfiles.first }

    var body: some View {
        List {
            Section {
                Label {
                    VStack(alignment: .leading) {
                        Text(isConnected ? "import.df.oauth.connected" : "import.df.oauth.title")
                            .bold()
                        Text(
                            isConnected
                                ? (connectedUsername ?? String(localized: "import.df.oauth.connectedAccount"))
                                : String(localized: "import.df.oauth.description")
                        )
                            .foregroundStyle(.secondary)
                        if isConnected && !canSyncScores {
                            Text("import.df.oauth.writePending")
                                .foregroundStyle(.orange)
                        }
                    }
                } icon: {
                    Image(systemName: isConnected ? "checkmark.shield.fill" : "person.badge.key.fill")
                        .foregroundStyle(isConnected ? .green : .blue)
                }
            }

            Section {
                if isConnected {
                    actionRow(
                        title: String(localized: "import.df.action.quickSync"),
                        icon: "arrow.triangle.2.circlepath",
                        tint: .blue,
                        disabled: isImporting || isResolvingImportConflict,
                        action: startImport
                    )
                    actionRow(
                        title: String(localized: "import.df.oauth.reconnect"),
                        icon: "person.badge.key",
                        tint: .orange,
                        disabled: isImporting || isResolvingImportConflict,
                        action: startAuthorization
                    )
                    actionRow(
                        title: String(localized: "import.df.oauth.disconnect"),
                        icon: "personalhotspot.slash",
                        tint: .red,
                        disabled: isImporting || isResolvingImportConflict,
                        action: startDisconnect
                    )
                } else {
                    actionRow(
                        title: String(localized: "import.df.oauth.connectImport"),
                        icon: "person.badge.key",
                        tint: .blue,
                        disabled: isImporting || isResolvingImportConflict || activeProfile == nil,
                        action: startAuthorization
                    )
                }
            } header: {
                Text("import.df.oauth.actions")
            } footer: {
                Text("import.df.oauth.footer")
            }

            if !importStatus.isEmpty || isImporting {
                Section("import.status.header") {
                    if isImporting {
                        ProgressView()
                    }
                    if !importStatus.isEmpty {
                        Text(importStatus)
                            .foregroundStyle(
                                importStatus.contains(String(localized: "import.status.error")) ? .red : .secondary)
                    }
                    if totalRecords > 0 {
                        Text("import.df.oauth.fetched \(totalRecords)")
                            .foregroundStyle(.secondary)
                    }
                }
            }
        }
        .navigationTitle("import.df.title")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: activeProfile?.id) {
            await refreshBindingStatus()
        }
        .onDisappear {
            operationTask?.cancel()
        }
        .sheet(item: $importConflictPreview) { preview in
            SyncConflictResolutionSheet(
                context: .importPreview(preview),
                isApplying: isResolvingImportConflict
            ) { action in
                operationTask = Task {
                    await applyImportConflictResolutionAction(action, preview: preview)
                }
            }
            .interactiveDismissDisabled(true)
        }
    }

    private func startAuthorization() {
        operationTask?.cancel()
        operationTask = Task { await authorizeAndImport() }
    }

    private func startImport() {
        operationTask?.cancel()
        operationTask = Task {
            guard let profile = activeProfile else { return }
            await importAuthorizedData(profile: profile)
        }
    }

    private func startDisconnect() {
        operationTask?.cancel()
        operationTask = Task { await disconnect() }
    }

    private func actionRow(
        title: String,
        icon: String,
        tint: Color,
        disabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(tint, in: .rect(cornerRadius: 8))

                Text(title)
                    .foregroundStyle(.primary)

                Spacer()

                Image(systemName: "arrow.up.forward.app")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
            }
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .opacity(disabled ? 0.6 : 1)
    }

    @MainActor
    private func authorizeAndImport() async {
        guard let profile = activeProfile else { return }
        isImporting = true
        importStatus = String(localized: "import.df.oauth.preparing")
        defer { isImporting = false }

        do {
            try await prepareBackend(profile: profile)
            let authorization = try await BackendImportService.startDivingFishAuthorization(
                profileId: profile.id.uuidString.lowercased()
            )
            openURL(authorization.authorizationUrl)

            importStatus = String(localized: "import.df.oauth.waiting")
            while Date() < authorization.expiresAt {
                try Task.checkCancellation()
                try await Task.sleep(for: .seconds(1.5))
                let status = try await BackendImportService.divingFishAuthorizationStatus(
                    authorizationId: authorization.authorizationId
                )
                if status.status == "success" {
                    isConnected = true
                    await importAuthorizedData(profile: profile, managesBusyState: false)
                    return
                }
                if status.status == "failed" || status.status == "expired" {
                    throw BackendAPIError(
                        statusCode: nil,
                        code: status.errorCode,
                        message: String(localized: "import.df.oauth.failed")
                    )
                }
            }
            throw BackendAPIError(
                statusCode: nil, code: "expired", message: String(localized: "import.df.oauth.expired"))
        } catch is CancellationError {
            return
        } catch {
            await refreshBindingStatus()
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
    }

    @MainActor
    private func importAuthorizedData(profile: UserProfile, managesBusyState: Bool = true) async {
        if managesBusyState {
            isImporting = true
        }
        importStatus = String(localized: "import.df.status.connecting")
        totalRecords = 0
        defer {
            if managesBusyState {
                isImporting = false
            }
        }

        do {
            try await prepareBackend(profile: profile)
            let result = try await BackendImportService.importDivingFish(
                profileId: profile.id.uuidString.lowercased()
            )
            totalRecords = result.fetchedCount

            if result.upsertedCount == 0 {
                try await BackendCloudSyncService.restoreFromCloud(context: modelContext)
                profile.lastImportDateDF = Date()
                try modelContext.save()
                importStatus = String(localized: "import.status.noChanges")
                await refreshBindingStatus()
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
                return
            }

            importStatus = String(localized: "import.status.conflict.applying")
            try await BackendIncrementalSyncService.applyImportConflictResolution(
                .overwriteLocalWithImport,
                preview: preview,
                context: modelContext
            )
            try await BackendCloudSyncService.restoreFromCloud(context: modelContext)
            profile.lastImportDateDF = Date()
            try modelContext.save()
            importStatus = String(localized: "import.status.success \(result.upsertedCount)")
            await refreshBindingStatus()
        } catch is CancellationError {
            return
        } catch {
            await refreshBindingStatus()
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
    }

    @MainActor
    private func prepareBackend(profile: UserProfile) async throws {
        await BackendSessionManager.shared.checkSession()
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }
        try await BackendScoreSyncService.ensureProfileExists(profile: profile)
    }

    @MainActor
    private func refreshBindingStatus() async {
        guard let profile = activeProfile, BackendSessionManager.shared.isAuthenticated else {
            isConnected = false
            canSyncScores = false
            connectedUsername = nil
            return
        }
        guard let status = try? await BackendImportService.divingFishBindingStatus(
            profileId: profile.id.uuidString.lowercased()
        ) else { return }
        isConnected = status.connected
        canSyncScores = status.canWrite
        connectedUsername = status.externalUsername
    }

    @MainActor
    private func disconnect() async {
        guard let profile = activeProfile else { return }
        isImporting = true
        defer { isImporting = false }
        do {
            _ = try await BackendImportService.disconnectDivingFish(
                profileId: profile.id.uuidString.lowercased()
            )
            isConnected = false
            canSyncScores = false
            connectedUsername = nil
            importStatus = String(localized: "import.df.oauth.disconnected")
        } catch {
            importStatus = String(localized: "import.status.error.message \(error.localizedDescription)")
        }
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
            if let profile = activeProfile {
                profile.lastImportDateDF = Date()
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
