import AuthenticationServices
import SwiftData
import SwiftUI
import UIKit

@MainActor
private final class BackendWebAuthPresentationContextProvider: NSObject, ASWebAuthenticationPresentationContextProviding {
    func presentationAnchor(for session: ASWebAuthenticationSession) -> ASPresentationAnchor {
        UIApplication.shared.connectedScenes
            .compactMap { $0 as? UIWindowScene }
            .flatMap(\.windows)
            .first(where: \.isKeyWindow) ?? ASPresentationAnchor()
    }
}

struct BackendAuthView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]

    @State private var sessionManager = BackendSessionManager.shared
    @State private var newPassword = ""
    @State private var webAuthenticationSession: ASWebAuthenticationSession?

    @State private var isOpeningWebAuth = false
    @State private var isSigningOut = false
    @State private var isUpdatingRecoveryPassword = false
    @State private var isSyncing = false
    @State private var isResolvingAccountConflict = false
    @State private var showingLogoutOptions = false
    @State private var conflictState: AccountConflictState?

    @State private var toastMessage: String?
    @State private var isErrorToast = false

    private let webAuthPresentationContextProvider = BackendWebAuthPresentationContextProvider()

    private enum WebAuthMode: String {
        case login
        case register
        case forgot
    }

    private var config: SyncConfig? { configs.first }

    private var isBusy: Bool {
        isOpeningWebAuth || isSigningOut || isUpdatingRecoveryPassword || isSyncing || isResolvingAccountConflict
    }

    private var showRecoveryPasswordRequirementAsError: Bool {
        !newPassword.isEmpty && !meetsPasswordRequirement(newPassword)
    }

    var body: some View {
        ZStack(alignment: .bottom) {
            List {
                if !sessionManager.isConfigured {
                    configurationContent
                } else if sessionManager.isAuthenticated, let user = sessionManager.currentUser {
                    authenticatedContent(user: user)
                } else if sessionManager.isPasswordRecoveryFlow {
                    passwordRecoveryContent
                } else {
                    webAuthenticationContent
                }
            }
            .listStyle(.insetGrouped)
            .scrollDismissesKeyboard(.interactively)

            if let message = toastMessage {
                toastView(message: message)
                    .padding(.horizontal, 20)
                    .padding(.bottom, 20)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .zIndex(1)
            }
        }
        .navigationTitle("settings.cloud.title")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await sessionManager.checkSession()
            await evaluateAccountConflictIfNeeded()
        }
        .onAppear {
            consumePendingAuthMessageIfNeeded()
        }
        .onChange(of: sessionManager.pendingMessage) { _, _ in
            consumePendingAuthMessageIfNeeded()
        }
        .onChange(of: sessionManager.isAuthenticated) { _, _ in
            Task {
                await evaluateAccountConflictIfNeeded()
            }
        }
        .sheet(item: $conflictState) { state in
            SyncConflictResolutionSheet(
                context: .account(state),
                isApplying: isResolvingAccountConflict
            ) { action in
                Task {
                    await applyAccountResolutionAction(action)
                }
            }
            .interactiveDismissDisabled(true)
        }
    }

    private var configurationContent: some View {
        Group {
            Section {
                accountSummaryCard(
                    icon: "exclamationmark.icloud.fill",
                    iconTint: .orange,
                    title: String(localized: "settings.cloud.config.title"),
                    subtitle: String(localized: "settings.cloud.config.subtitle")
                )
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            .listRowBackground(Color.clear)
            .listSectionSeparator(.hidden)

            Section("settings.cloud.config.section") {
                VStack(alignment: .leading) {
                    Text("settings.cloud.config.step.copy")
                    Text("settings.cloud.config.step.fill")
                }
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.vertical, 4)
            }
        }
    }

    private func authenticatedContent(user: BackendAuthUser) -> some View {
        Group {
            Section {
                accountSummaryCard(
                    icon: "person.crop.circle.badge.checkmark",
                    iconTint: .blue,
                    title: user.email,
                    subtitle: String(localized: "settings.cloud.status.loggedIn")
                )
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            .listRowBackground(Color.clear)
            .listSectionSeparator(.hidden)

            Section("settings.cloud.section.sync") {
                actionRow(
                    title: "settings.cloud.backup",
                    icon: "icloud.and.arrow.up.fill",
                    tint: .blue
                ) {
                    Task { await performBackup() }
                }

                actionRow(
                    title: "settings.cloud.restore",
                    icon: "icloud.and.arrow.down.fill",
                    tint: .green
                ) {
                    Task { await performRestore() }
                }

                if isSyncing {
                    HStack {
                        ProgressView()
                        Text("settings.cloud.syncing")
                            .foregroundStyle(.secondary)
                    }
                }
            }

            Section(
                header: Text("settings.cloud.section.autoBackup"),
                footer: Text("settings.cloud.autoBackup.footer")
            ) {
                Picker(
                    "settings.cloud.autoBackup.interval",
                    selection: Binding(
                        get: { config?.cloudBackupInterval ?? 0 },
                        set: { value in
                            let currentConfig = ensureSyncConfig()
                            currentConfig.cloudBackupInterval = value
                            try? modelContext.save()
                            Task {
                                await BackendAutoBackup.scheduleNextBackup(container: modelContext.container)
                            }
                        }
                    )
                ) {
                    Text("settings.cloud.autoBackup.never").tag(0)
                    Text("1h").tag(1)
                    Text("3h").tag(3)
                    Text("6h").tag(6)
                    Text("12h").tag(12)
                    Text("24h").tag(24)
                }
                .pickerStyle(.menu)

                LabeledContent("settings.cloud.autoBackup.lastBackup", value: lastBackupDisplayText)
            }

            Section {
                Button("settings.cloud.logout", role: .destructive) {
                    showingLogoutOptions = true
                }
                .confirmationDialog(
                    "settings.cloud.logout.options.title",
                    isPresented: $showingLogoutOptions
                ) {
                    Button("settings.cloud.logout.option.keepLocal") {
                        Task {
                            await performLogout(clearLocalData: false)
                        }
                    }
                    Button("settings.cloud.logout.option.clearLocal", role: .destructive) {
                        Task {
                            await performLogout(clearLocalData: true)
                        }
                    }
                    Button("settings.cloud.logout.option.cancel", role: .cancel) {}
                } message: {
                    Text("settings.cloud.logout.options.message")
                }
                .disabled(isBusy)
            }
        }
    }

    private var webAuthenticationContent: some View {
        Group {
            Section {
                accountSummaryCard(
                    icon: "person.badge.key.fill",
                    iconTint: .blue,
                    title: String(localized: "settings.cloud.login.title"),
                    subtitle: String(localized: "settings.cloud.login.subtitle")
                )
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            .listRowBackground(Color.clear)
            .listSectionSeparator(.hidden)

            Section {
                actionRow(
                    title: "settings.cloud.login.button",
                    icon: "person.crop.circle.badge.checkmark",
                    tint: .blue
                ) {
                    startWebAuth(.login)
                }

                actionRow(
                    title: "settings.cloud.signup.button",
                    icon: "person.badge.plus.fill",
                    tint: .green
                ) {
                    startWebAuth(.register)
                }

                actionRow(
                    title: "settings.cloud.forgotPassword",
                    icon: "key.fill",
                    tint: .orange
                ) {
                    startWebAuth(.forgot)
                }
            } footer: {
                Text("settings.cloud.signup.subtitle")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var passwordRecoveryContent: some View {
        Group {
            Section {
                accountSummaryCard(
                    icon: "key.fill",
                    iconTint: .blue,
                    title: String(localized: "settings.cloud.recovery.title"),
                    subtitle: String(localized: "settings.cloud.recovery.subtitle")
                )
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            .listRowBackground(Color.clear)
            .listSectionSeparator(.hidden)

            Section {
                HStack {
                    settingsIcon(icon: "lock.rotation", tint: .gray)
                    SecureField("settings.cloud.recovery.newPassword", text: $newPassword)
                        .textContentType(.newPassword)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .onSubmit {
                            Task { await completePasswordRecovery() }
                        }
                }
                .padding(.vertical, 2)
            } header: {
                Text("settings.cloud.recovery.section")
            } footer: {
                Text("settings.cloud.signup.passwordRequirement")
                    .font(.footnote)
                    .foregroundStyle(showRecoveryPasswordRequirementAsError ? .red : .secondary)
                    .lineLimit(1)
                    .minimumScaleFactor(0.85)
            }

            Section {
                Button("settings.cloud.recovery.updateButton") {
                    Task { await completePasswordRecovery() }
                }
                .disabled(newPassword.isEmpty || isBusy)
            }
            .listSectionSeparator(.hidden)

            Section {
                Button("settings.cloud.recovery.cancel", role: .cancel) {
                    newPassword = ""
                    sessionManager.clearPasswordRecoveryFlow()
                }
                .disabled(isBusy)
            }
        }
    }

    private func accountSummaryCard(icon: String, iconTint: Color, title: String, subtitle: String) -> some View {
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

            Label("settings.cloud.privacy.hint", systemImage: "lock.shield.fill")
                .font(.footnote)
                .foregroundStyle(.secondary)
        }
        .padding(20)
        .background(Color(uiColor: .secondarySystemGroupedBackground), in: .rect(cornerRadius: 28))
    }

    private func actionRow(
        title: String,
        icon: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                settingsIcon(icon: icon, tint: tint)
                Text(LocalizedStringKey(title))
                    .foregroundStyle(.primary)
                Spacer()
                Image(systemName: "arrow.up.forward.app")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
            }
        }
        .buttonStyle(.plain)
        .disabled(isBusy)
        .opacity(isBusy ? 0.6 : 1)
    }

    private func settingsIcon(icon: String, tint: Color) -> some View {
        Image(systemName: icon)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 28, height: 28)
            .background(tint, in: .rect(cornerRadius: 8))
    }

    private func toastView(message: String) -> some View {
        HStack {
            Image(systemName: isErrorToast ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                .foregroundStyle(isErrorToast ? .red : .green)

            Text(LocalizedStringKey(message))
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.black.opacity(0.82), in: Capsule())
    }

    private var lastBackupDisplayText: String {
        guard let lastBackup = config?.lastCloudBackupDate else {
            return String(localized: "settings.cloud.autoBackup.never")
        }
        return lastBackup.formatted(date: .numeric, time: .shortened)
    }

    private func ensureSyncConfig() -> SyncConfig {
        if let config {
            return config
        }
        let newConfig = SyncConfig()
        modelContext.insert(newConfig)
        return newConfig
    }

    private func meetsPasswordRequirement(_ value: String) -> Bool {
        guard value.count >= 8 else { return false }

        let hasLowercase = value.contains { $0.isLowercase }
        let hasUppercase = value.contains { $0.isUppercase }
        let hasDigit = value.contains { $0.isNumber }
        let hasSymbol = value.contains { !$0.isLetter && !$0.isNumber && !$0.isWhitespace }

        return hasLowercase && hasUppercase && hasDigit && hasSymbol
    }

    @MainActor
    private func showToast(message: String, error: Bool = false) {
        isErrorToast = error
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            toastMessage = message
        }

        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard toastMessage == message else { return }
            withAnimation {
                toastMessage = nil
            }
        }
    }

    private func consumePendingAuthMessageIfNeeded() {
        guard let pending = sessionManager.pendingMessage else {
            return
        }

        if pending == "settings.cloud.message.loginSuccess", sessionManager.isAuthenticated {
            Task {
                await evaluateAccountConflictIfNeeded()
            }
        }

        let isError = sessionManager.pendingMessageIsError
        sessionManager.clearPendingMessage()
        showToast(message: pending, error: isError)
    }

    @MainActor
    private func evaluateAccountConflictIfNeeded() async {
        guard sessionManager.isAuthenticated, let userId = sessionManager.currentUser?.id else {
            conflictState = nil
            return
        }

        let state = AccountDataResolutionCoordinator.shared.detectConflictAfterAuth(
            context: modelContext,
            currentUserId: userId
        )
        if state.requiresResolution {
            conflictState = state
            return
        }

        conflictState = nil
        await BackendAutoBackup.scheduleNextBackup(container: modelContext.container)
    }

    @MainActor
    private func applyAccountResolutionAction(_ action: SyncConflictResolutionSheetAction) async {
        let option: AccountResolutionOption
        switch action {
        case .merge:
            option = .mergeLocalAndCloud
        case .keepLocal:
            option = .overwriteCloudWithLocal
        case .useRemote:
            option = .overwriteLocalWithCloud
        }
        await applyAccountResolution(option)
    }

    @MainActor
    private func applyAccountResolution(_ option: AccountResolutionOption) async {
        guard !isResolvingAccountConflict else { return }
        isResolvingAccountConflict = true
        defer { isResolvingAccountConflict = false }

        do {
            try await AccountDataResolutionCoordinator.shared.applyResolution(option, context: modelContext)
            conflictState = nil
            showToast(message: "settings.cloud.resolution.success")
            await BackendAutoBackup.scheduleNextBackup(container: modelContext.container)
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
    }

    @MainActor
    private func performLogout(clearLocalData: Bool) async {
        guard !isSigningOut else { return }
        isSigningOut = true
        defer { isSigningOut = false }

        await sessionManager.logout()
        conflictState = nil

        if clearLocalData {
            do {
                try AccountDataResolutionCoordinator.shared.clearLocalUserData(context: modelContext)
                showToast(message: "settings.cloud.logout.clearLocal.success")
            } catch {
                showToast(message: error.localizedDescription, error: true)
            }
        } else {
            AccountDataResolutionCoordinator.shared.clearPendingResolutionState(context: modelContext)
        }

        BackendAutoBackup.cancelScheduledBackup()
    }

    @MainActor
    private func startWebAuth(_ mode: WebAuthMode) {
        guard !isBusy else { return }
        guard let authURL = buildWebAuthURL(mode: mode) else {
            showToast(message: "settings.cloud.config.error.unconfigured", error: true)
            return
        }

        isOpeningWebAuth = true

        let session = ASWebAuthenticationSession(url: authURL, callbackURLScheme: "maimaid") { callbackURL, error in
            Task { @MainActor in
                isOpeningWebAuth = false
                webAuthenticationSession = nil

                if let callbackURL {
                    sessionManager.handleAuthRedirect(callbackURL)
                    consumePendingAuthMessageIfNeeded()
                    return
                }

                if let authError = error as? ASWebAuthenticationSessionError,
                    authError.code == .canceledLogin {
                    return
                }

                showToast(message: "settings.cloud.message.authLinkFailed", error: true)
            }
        }

        session.presentationContextProvider = webAuthPresentationContextProvider
        session.prefersEphemeralWebBrowserSession = false

        webAuthenticationSession = session

        guard session.start() else {
            isOpeningWebAuth = false
            webAuthenticationSession = nil
            showToast(message: "settings.cloud.message.authLinkFailed", error: true)
            return
        }
    }

    private func buildWebAuthURL(mode: WebAuthMode) -> URL? {
        guard let baseURL = BackendConfig.webAuthBaseURL else {
            return nil
        }
        guard var components = URLComponents(url: baseURL, resolvingAgainstBaseURL: false) else {
            return nil
        }

        var queryItems = components.queryItems ?? []
        queryItems.removeAll { item in
            item.name == "authMode" || item.name == "redirect_uri" || item.name == "client"
        }
        queryItems.append(URLQueryItem(name: "authMode", value: mode.rawValue))
        queryItems.append(URLQueryItem(name: "redirect_uri", value: "maimaid://auth/callback"))
        queryItems.append(URLQueryItem(name: "client", value: "ios"))
        components.queryItems = queryItems
        return components.url
    }

    @MainActor
    private func completePasswordRecovery() async {
        guard let resetToken = sessionManager.passwordResetToken, !newPassword.isEmpty else {
            showToast(message: "settings.cloud.message.recoveryLinkInvalid", error: true)
            return
        }
        guard meetsPasswordRequirement(newPassword) else {
            showToast(message: "settings.cloud.message.passwordRequirementNotMet", error: true)
            return
        }

        isUpdatingRecoveryPassword = true
        defer { isUpdatingRecoveryPassword = false }

        do {
            try await sessionManager.resetPassword(token: resetToken, newPassword: newPassword)
            newPassword = ""
            sessionManager.clearPasswordRecoveryFlow()
            showToast(message: "settings.cloud.message.passwordUpdated")
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
    }

    @MainActor
    private func performBackup() async {
        guard !isBusy else { return }

        isSyncing = true
        defer { isSyncing = false }

        do {
            try await BackendCloudSyncService.backupToCloud(context: modelContext)
            showToast(message: "settings.cloud.message.backupSuccess")
            await BackendAutoBackup.scheduleNextBackup(container: modelContext.container)
        } catch {
            let detail = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            showToast(message: String(localized: "settings.cloud.message.backupFailed") + ": " + detail, error: true)
        }
    }

    @MainActor
    private func performRestore() async {
        guard !isBusy else { return }

        isSyncing = true
        defer { isSyncing = false }

        do {
            try await BackendCloudSyncService.restoreFromCloud(context: modelContext)
            showToast(message: "settings.cloud.message.restoreSuccess")
        } catch {
            let detail = (error as? LocalizedError)?.errorDescription ?? error.localizedDescription
            showToast(message: String(localized: "settings.cloud.message.restoreFailed") + ": " + detail, error: true)
        }
    }
}
