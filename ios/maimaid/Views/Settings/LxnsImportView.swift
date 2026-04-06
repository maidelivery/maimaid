import SwiftUI
import SwiftData

struct LxnsTokenResponse: Decodable {
    let success: Bool?
    let data: LxnsTokenData?
    let message: String?
}

struct LxnsTokenData: Decodable {
    let access_token: String
    let refresh_token: String
}

struct LxnsImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    @State private var generatedCodeVerifier: String = ""
    @State private var authCode: String = ""
    @State private var lxnsRefreshToken: String = ""
    
    @State private var isImporting = false
    @State private var importStatus: String = ""
    @State private var progress: Double = 0
    @State private var totalRecords: Int = 0
    @State private var currentStep: String = ""
    @State private var importConflictPreview: ImportSyncConflictPreview?
    @State private var isResolvingImportConflict = false
    @State private var pendingUpsertedCount: Int = 0
    @State private var hasValidatedSessionOnEntry = false
    @State private var isValidatingSession = false
    @State private var validatedAccessToken: String?
    @State private var validatedAccessTokenDate: Date?
    
    @Environment(\.openURL) var openURL
    
    private var hasBoundAccount: Bool { !lxnsRefreshToken.isEmpty }
    
    private var importStatusStyle: (tint: Color, icon: String) {
        let failedText = String(localized: "import.status.failed")
        let errorText = String(localized: "import.status.error")
        let expiredText = String(localized: "import.lxns.status.failed.expired")
        let isFailure = importStatus.contains(failedText) || importStatus.contains(errorText) || importStatus.contains(expiredText)
        return (isFailure ? .red : .cyan, isFailure ? "xmark.circle.fill" : "checkmark.circle.fill")
    }
    
    private var sessionStatusTint: Color {
        if isValidatingSession { return .orange }
        return .green
    }
    
    private var sessionStatusText: LocalizedStringKey {
        if isValidatingSession {
            return "import.lxns.status.checking"
        }
        return "import.lxns.status.connected"
    }
    
    private var hasUsableValidatedAccessToken: Bool {
        guard let validatedAccessToken,
              let validatedAccessTokenDate else { return false }
        return !validatedAccessToken.isEmpty && Date().timeIntervalSince(validatedAccessTokenDate) < 300
    }
    
    var body: some View {
        ZStack(alignment: .bottom) {
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()
            
            List {
                summarySection
                contentSection
                
                if isImporting || !importStatus.isEmpty {
                    statusSection
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
            .scrollDismissesKeyboard(.interactively)
        }
        .navigationTitle("import.lxns.title")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            syncCredentialStateFromStore()
            await validateSessionOnEntryIfNeeded()
        }
        .onChange(of: activeProfile?.id) { _, _ in
            syncCredentialStateFromStore()
            hasValidatedSessionOnEntry = false
            Task {
                await validateSessionOnEntryIfNeeded()
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
                icon: hasBoundAccount ? "snowflake.circle.fill" : "link.badge.plus",
                iconTint: hasBoundAccount ? .cyan : .indigo,
                title: String(localized: hasBoundAccount ? "import.lxns.bound.header" : "import.lxns.step1.header"),
                subtitle: hasBoundAccount
                    ? String(localized: "import.lxns.status.connected")
                    : String(localized: "import.lxns.step1.footer")
            )
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
        .listRowBackground(Color.clear)
        .listSectionSeparator(.hidden)
    }
    
    @ViewBuilder
    private var contentSection: some View {
        if let profile = activeProfile, !lxnsRefreshToken.isEmpty {
            Section("import.lxns.bound.header") {
                HStack(spacing: 12) {
                    settingsIcon(icon: isValidatingSession ? "clock.badge.checkmark.fill" : "checkmark.shield.fill", color: sessionStatusTint)
                    Text("import.lxns.status")
                    Spacer()
                    Text(sessionStatusText)
                        .foregroundStyle(sessionStatusTint)
                }
                
                actionRow(
                    title: isImporting ? "import.status.syncing" : "import.lxns.action.quickSync",
                    icon: "arrow.triangle.2.circlepath.circle.fill",
                    tint: .cyan
                ) {
                    Task {
                        await startQuickImport(profile: profile)
                    }
                }
                .disabled(isImporting || isValidatingSession || isResolvingImportConflict)
                .opacity(isImporting || isValidatingSession || isResolvingImportConflict ? 0.6 : 1.0)
            }
            
            Section("import.lxns.manage.header") {
                Button("import.lxns.action.relogin", role: .destructive) {
                    ProfileCredentialStore.shared.setLxnsRefreshToken("", for: profile.id)
                    lxnsRefreshToken = ""
                    validatedAccessToken = nil
                    validatedAccessTokenDate = nil
                    hasValidatedSessionOnEntry = false
                }
            }
        } else {
            Section {
                actionRow(
                    title: "import.lxns.action.openBrowser",
                    icon: "safari.fill",
                    tint: .indigo
                ) {
                    openAuthPage()
                }
                .disabled(isImporting)
                .opacity(isImporting ? 0.6 : 1.0)
            } header: {
                Text("import.lxns.step1.header")
            } footer: {
                Text("import.lxns.step1.footer")
            }
            
            Section {
                VStack(spacing: 14) {
                    credentialField(
                        title: "import.lxns.code.placeholder",
                        text: $authCode,
                        icon: "key.fill"
                    )
                    
                    Button {
                        Task {
                            await exchangeCodeAndImport()
                        }
                    } label: {
                        HStack {
                            Spacer()
                            if isImporting {
                                ProgressView()
                                    .tint(.white)
                            } else {
                                Text("import.lxns.action.startImport")
                                    .bold()
                            }
                            Spacer()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(authCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || isImporting || isResolvingImportConflict)
                }
                .padding(.vertical, 4)
                .listRowSeparator(.hidden)
            } header: {
                Text("import.lxns.step2.header")
            } footer: {
                Text("import.lxns.step2.footer")
            }
            .listSectionSeparator(.hidden)
        }
    }
    
    @ViewBuilder
    private var statusSection: some View {
        Section("import.status.header") {
            VStack(alignment: .leading, spacing: 14) {
                if isImporting && !currentStep.isEmpty {
                    HStack(spacing: 10) {
                        ProgressView()
                        Text(currentStep)
                            .foregroundStyle(.secondary)
                    }
                }
                
                if !importStatus.isEmpty {
                    Label {
                        Text(importStatus)
                            .foregroundStyle(importStatusStyle.tint)
                            .fixedSize(horizontal: false, vertical: true)
                    } icon: {
                        Image(systemName: importStatusStyle.icon)
                            .foregroundStyle(importStatusStyle.tint)
                    }
                }
                
                if totalRecords > 0 {
                    VStack(alignment: .leading, spacing: 8) {
                        ProgressView(value: progress, total: Double(totalRecords))
                            .tint(importStatusStyle.tint)
                        Text("\(Int(progress)) / \(totalRecords)")
                            .font(.caption.monospacedDigit())
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
    
    @MainActor
    private func openAuthPage() {
        if LxnsOAuthConfiguration.clientId == "YOUR_CLIENT_ID_HERE" {
            importStatus = String(localized: "import.lxns.error.clientId")
            return
        }
        
        let codeVerifier = AuthUtils.generateCodeVerifier()
        let codeChallenge = AuthUtils.generateCodeChallenge(verifier: codeVerifier)
        
        self.generatedCodeVerifier = codeVerifier
        
        var components = URLComponents(string: "https://maimai.lxns.net/oauth/authorize")!
        components.queryItems = [
            URLQueryItem(name: "response_type", value: "code"),
            URLQueryItem(name: "client_id", value: LxnsOAuthConfiguration.clientId),
            URLQueryItem(name: "redirect_uri", value: LxnsOAuthConfiguration.redirectUri),
            URLQueryItem(name: "scope", value: LxnsOAuthConfiguration.scope.replacing("+", with: " ")),
            URLQueryItem(name: "code_challenge", value: codeChallenge),
            URLQueryItem(name: "code_challenge_method", value: "S256"),
            URLQueryItem(name: "state", value: UUID().uuidString)
        ]
        
        if let authURL = components.url {
            openURL(authURL)
        }
    }
    
    @MainActor
    private func exchangeCodeAndImport() async {
        if generatedCodeVerifier.isEmpty {
            importStatus = String(localized: "import.lxns.error.security")
            return
        }
        
        isImporting = true
        importStatus = ""
        currentStep = String(localized: "import.lxns.status.exchanging")
        progress = 0
        totalRecords = 0
        
        do {
            guard let tokenURL = URL(string: "https://maimai.lxns.net/api/v0/oauth/token") else { throw URLError(.badURL) }
            
            var request = URLRequest(url: tokenURL)
            request.httpMethod = "POST"
            
            let parameters = [
                "grant_type": "authorization_code",
                "client_id": LxnsOAuthConfiguration.clientId,
                "redirect_uri": LxnsOAuthConfiguration.redirectUri,
                "code": authCode.trimmingCharacters(in: .whitespacesAndNewlines),
                "code_verifier": generatedCodeVerifier
            ]
            
            let bodyString = parameters.compactMap { key, value in
                let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
                return "\(key)=\(encodedValue)"
            }.joined(separator: "&")
            
            request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
            request.httpBody = bodyString.data(using: .utf8)
            
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else { throw URLError(.badServerResponse) }
            
            let tokenResponse = try JSONDecoder().decode(LxnsTokenResponse.self, from: data)
            
            if httpResponse.statusCode != 200 || tokenResponse.data?.access_token == nil {
                importStatus = String(localized: "import.lxns.status.failed.token \(tokenResponse.message ?? String(localized: "import.status.error.unknown"))")
                isImporting = false
                return
            }
            
            let accessToken = tokenResponse.data!.access_token
            let refreshToken = tokenResponse.data!.refresh_token
            validatedAccessToken = accessToken
            validatedAccessTokenDate = Date()
            
            if let profile = activeProfile {
                ProfileCredentialStore.shared.setLxnsRefreshToken(refreshToken, for: profile.id)
                lxnsRefreshToken = refreshToken
            }

            await importData(accessToken: accessToken)
        } catch {
            importStatus = String(localized: "import.lxns.status.failed.networkToken")
            isImporting = false
        }
    }
    
    @MainActor
    private func startQuickImport(profile: UserProfile) async {
        isImporting = true
        importStatus = ""
        progress = 0
        totalRecords = 0
        
        if hasUsableValidatedAccessToken, let validatedAccessToken {
            currentStep = String(localized: "import.lxns.status.fetching")
            await importData(accessToken: validatedAccessToken)
            return
        }
        
        currentStep = String(localized: "import.lxns.status.refreshing")
        
        switch await SyncManager.shared.refreshLxnsTokenResult(profileId: profile.id) {
        case .success(let token):
            validatedAccessToken = token
            validatedAccessTokenDate = Date()
            await importData(accessToken: token)
        case .expired:
            validatedAccessToken = nil
            validatedAccessTokenDate = nil
            importStatus = String(localized: "import.lxns.status.failed.expired")
            isImporting = false
        case .failed:
            importStatus = String(localized: "import.status.failed.network")
            isImporting = false
        }
    }
    
    @MainActor
    private func importData(accessToken: String) async {
        currentStep = String(localized: "import.lxns.status.fetching")
        guard let profile = activeProfile else {
            importStatus = String(localized: "import.status.error.unknown")
            isImporting = false
            return
        }
        await BackendSessionManager.shared.checkSession()
        guard BackendSessionManager.shared.isAuthenticated else {
            importStatus = String(localized: "community.alias.submit.loginRequired")
            isImporting = false
            return
        }

        do {
            try await BackendScoreSyncService.ensureProfileExists(profile: profile)

            let result = try await BackendImportService.importLxns(
                profileId: profile.id.uuidString.lowercased(),
                accessToken: accessToken
            )

            totalRecords = result.fetchedCount
            progress = Double(result.fetchedCount)
            importStatus = String(localized: "import.status.processing \(totalRecords)")

            if result.upsertedCount == 0 {
                try BackendIncrementalSyncService.updateLastSyncRevisionIfAvailable(
                    result.latestRevision,
                    context: modelContext
                )
                profile.lastImportDateLXNS = Date()
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
            profile.lastImportDateLXNS = Date()
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

            let targetProfileId = preview.profileId
            if let profile = try modelContext.fetch(
                FetchDescriptor<UserProfile>(predicate: #Predicate<UserProfile> { $0.id == targetProfileId })
            ).first {
                profile.lastImportDateLXNS = Date()
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

private extension LxnsImportView {
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
            
            Label("import.lxns.summary.footer", systemImage: "square.and.arrow.down")
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
    
    func credentialField(title: String, text: Binding<String>, icon: String) -> some View {
        HStack(spacing: 12) {
            settingsIcon(icon: icon, color: .gray)
            TextField(LocalizedStringKey(title), text: text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
        }
        .padding(.vertical, 2)
    }
    
    @MainActor
    func validateSessionOnEntryIfNeeded() async {
        guard !hasValidatedSessionOnEntry else { return }
        hasValidatedSessionOnEntry = true
        
        guard let profile = activeProfile, !lxnsRefreshToken.isEmpty else { return }
        
        isValidatingSession = true
        defer { isValidatingSession = false }
        
        switch await SyncManager.shared.refreshLxnsTokenResult(profileId: profile.id) {
        case .success(let accessToken):
            validatedAccessToken = accessToken
            validatedAccessTokenDate = Date()
            if importStatus == String(localized: "import.lxns.status.failed.expired") {
                importStatus = ""
            }
        case .expired:
            validatedAccessToken = nil
            validatedAccessTokenDate = nil
            ProfileCredentialStore.shared.setLxnsRefreshToken("", for: profile.id)
            lxnsRefreshToken = ""
            importStatus = String(localized: "import.lxns.status.failed.expired")
        case .failed:
            break
        }
    }

    @MainActor
    func syncCredentialStateFromStore() {
        guard let profile = activeProfile else {
            lxnsRefreshToken = ""
            return
        }
        lxnsRefreshToken = ProfileCredentialStore.shared.credentials(for: profile.id).lxnsRefreshToken
    }
}
