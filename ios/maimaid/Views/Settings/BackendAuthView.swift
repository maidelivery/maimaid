import SwiftData
import SwiftUI

struct BackendAuthView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]

    @State private var sessionManager = BackendSessionManager.shared
    @State private var email = ""
    @State private var password = ""
    @State private var newPassword = ""
    @State private var isSignUpMode = false

    @State private var isLoading = false
    @State private var isSendingPasswordReset = false
    @State private var isSendingVerification = false
    @State private var isUpdatingRecoveryPassword = false
    @State private var isSyncing = false

    @State private var toastMessage: String?
    @State private var isErrorToast = false
    @State private var now = Date()
    @State private var lastAuthEmailSentAt = UserDefaults.app.object(forKey: Self.authEmailLastSentAtKey) as? Date
    @FocusState private var focusedField: Field?

    private static let authEmailLastSentAtKey = "backend.auth.email.lastSentAt"

    private enum Field {
        case email
        case password
        case newPassword
    }

    private var config: SyncConfig? { configs.first }

    private var isBusy: Bool {
        isLoading || isSendingPasswordReset || isSendingVerification || isUpdatingRecoveryPassword || isSyncing
    }

    private var sanitizedEmail: String {
        email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
    }

    private var emailCooldownRemainingSeconds: Int {
        guard let lastAuthEmailSentAt else { return 0 }
        let remaining = Int(ceil(lastAuthEmailSentAt.addingTimeInterval(60).timeIntervalSince(now)))
        return max(0, remaining)
    }

    private var isEmailRateLimited: Bool {
        emailCooldownRemainingSeconds > 0
    }

    private var isSignUpPasswordValid: Bool {
        meetsPasswordRequirement(password)
    }

    private var showSignUpPasswordRequirementAsError: Bool {
        isSignUpMode && !password.isEmpty && !isSignUpPasswordValid
    }

    private var showRecoveryPasswordRequirementAsError: Bool {
        !newPassword.isEmpty && !meetsPasswordRequirement(newPassword)
    }

    private var canSendForgotPassword: Bool {
        !sanitizedEmail.isEmpty && !isBusy
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
                    authenticationContent
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
        .animation(.easeInOut(duration: 0.2), value: isSignUpMode)
        .navigationTitle("settings.cloud.title")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await sessionManager.checkSession()
        }
        .task {
            await runClockLoop()
        }
        .onAppear {
            consumePendingAuthMessageIfNeeded()
        }
        .onChange(of: sessionManager.pendingMessage) { _, _ in
            consumePendingAuthMessageIfNeeded()
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
                    focusedField = nil
                    Task {
                        isLoading = true
                        await sessionManager.logout()
                        isLoading = false
                        BackendAutoBackup.cancelScheduledBackup()
                    }
                }
                .disabled(isBusy)
            }
        }
    }

    private var authenticationContent: some View {
        Group {
            Section {
                accountSummaryCard(
                    icon: "cloud.fill",
                    iconTint: .blue,
                    title: String(localized: isSignUpMode ? "settings.cloud.signup.title" : "settings.cloud.login.title"),
                    subtitle: String(localized: isSignUpMode ? "settings.cloud.signup.subtitle" : "settings.cloud.login.subtitle")
                )
            }
            .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
            .listRowBackground(Color.clear)
            .listSectionSeparator(.hidden)

            Section {
                Picker("", selection: $isSignUpMode) {
                    Text("settings.cloud.mode.login").tag(false)
                    Text("settings.cloud.mode.signup").tag(true)
                }
                .pickerStyle(.segmented)
            }

            Section {
                credentialField(
                    title: "settings.cloud.email",
                    text: $email,
                    icon: "envelope.fill",
                    field: .email,
                    isSecure: false
                )

                credentialField(
                    title: "settings.cloud.password",
                    text: $password,
                    icon: "lock.fill",
                    field: .password,
                    isSecure: true
                )
            } footer: {
                if isSignUpMode {
                    Text("settings.cloud.signup.passwordRequirement")
                        .font(.footnote)
                        .foregroundStyle(showSignUpPasswordRequirementAsError ? .red : .secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                } else {
                    Button("settings.cloud.forgotPassword") {
                        focusedField = nil
                        Task { await sendPasswordResetEmail() }
                    }
                    .buttonStyle(.plain)
                    .font(.footnote)
                    .foregroundStyle(
                        canSendForgotPassword
                            ? AnyShapeStyle(.tint)
                            : AnyShapeStyle(.secondary)
                    )
                    .disabled(!canSendForgotPassword)
                }
            }

            Section {
                Button {
                    focusedField = nil
                    Task {
                        if isSignUpMode {
                            await signUp()
                        } else {
                            await signIn()
                        }
                    }
                } label: {
                    HStack {
                        Spacer()
                        if isLoading {
                            ProgressView()
                                .tint(.white)
                        } else {
                            Text(isSignUpMode ? "settings.cloud.signup.button" : "settings.cloud.login.button")
                                .bold()
                        }
                        Spacer()
                    }
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
                .disabled(
                    sanitizedEmail.isEmpty
                        || password.isEmpty
                        || isBusy
                        || (isSignUpMode && isEmailRateLimited)
                        || (isSignUpMode && !isSignUpPasswordValid)
                )
                .listRowBackground(Color.clear)
            }
            .listSectionSeparator(.hidden)

            if isSignUpMode {
                Section {
                    Button {
                        focusedField = nil
                        Task { await resendVerificationEmail() }
                    } label: {
                        HStack {
                            Text("settings.cloud.resendVerification")
                            Spacer()
                            if isSendingVerification {
                                ProgressView()
                                    .controlSize(.small)
                            }
                        }
                    }
                    .disabled(sanitizedEmail.isEmpty || isBusy)
                }
                .listSectionSeparator(.hidden)
            }

            if isEmailRateLimited {
                Section {
                    Text("\(String(localized: "settings.cloud.message.emailRateLimited")) (\(emailCooldownRemainingSeconds)s)")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
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
                credentialField(
                    title: "settings.cloud.recovery.newPassword",
                    text: $newPassword,
                    icon: "lock.rotation",
                    field: .newPassword,
                    isSecure: true
                )
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
                    focusedField = nil
                    Task { await completePasswordRecovery() }
                }
                .disabled(newPassword.isEmpty || isBusy)
            }
            .listSectionSeparator(.hidden)

            Section {
                Button("settings.cloud.recovery.cancel", role: .cancel) {
                    focusedField = nil
                    newPassword = ""
                    sessionManager.clearPasswordRecoveryFlow()
                }
                .disabled(isBusy)
            }
        }
    }

    private func credentialField(
        title: String,
        text: Binding<String>,
        icon: String,
        field: Field,
        isSecure: Bool
    ) -> some View {
        HStack {
            settingsIcon(icon: icon, tint: .gray)
            Group {
                if isSecure {
                    SecureField(LocalizedStringKey(title), text: text)
                        .textContentType(.password)
                } else {
                    TextField(LocalizedStringKey(title), text: text)
                        .keyboardType(field == .email ? .emailAddress : .default)
                        .textContentType(field == .email ? .emailAddress : .none)
                }
            }
            .focused($focusedField, equals: field)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(field == .email ? .next : .go)
            .onSubmit {
                switch field {
                case .email:
                    focusedField = .password
                case .password:
                    focusedField = nil
                    Task {
                        if isSignUpMode {
                            await signUp()
                        } else {
                            await signIn()
                        }
                    }
                case .newPassword:
                    focusedField = nil
                    Task { await completePasswordRecovery() }
                }
            }
        }
        .padding(.vertical, 2)
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

    private func isValidEmailFormat(_ value: String) -> Bool {
        let emailPattern = #"^[A-Z0-9._%+\-]+@[A-Z0-9.\-]+\.[A-Z]{2,}$"#
        return value.range(
            of: emailPattern,
            options: [.regularExpression, .caseInsensitive]
        ) != nil
    }

    private func canSendAuthEmailNow() -> Bool {
        guard !isEmailRateLimited else {
            showToast(message: "settings.cloud.message.emailRateLimited", error: true)
            return false
        }
        return true
    }

    private func markAuthEmailSentNow() {
        let sentAt = Date()
        lastAuthEmailSentAt = sentAt
        now = sentAt
        UserDefaults.app.set(sentAt, forKey: Self.authEmailLastSentAtKey)
    }

    @MainActor
    private func validateEmailRegistrationState(requiresExistingAccount: Bool) async -> Bool {
        guard isValidEmailFormat(sanitizedEmail) else {
            showToast(message: "settings.cloud.message.emailInvalidFormat", error: true)
            return false
        }

        do {
            let exists = try await sessionManager.emailExists(sanitizedEmail)
            guard exists == requiresExistingAccount else {
                showToast(
                    message: requiresExistingAccount
                        ? "settings.cloud.message.emailNotRegistered"
                        : "settings.cloud.message.emailAlreadyRegistered",
                    error: true
                )
                return false
            }
            return true
        } catch {
            showToast(message: error.localizedDescription, error: true)
            return false
        }
    }

    @MainActor
    private func runClockLoop() async {
        while !Task.isCancelled {
            now = Date()
            do {
                try await Task.sleep(for: .seconds(1))
            } catch {
                break
            }
        }
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

        let isError = sessionManager.pendingMessageIsError
        sessionManager.clearPendingMessage()
        showToast(message: pending, error: isError)
    }

    @MainActor
    private func signIn() async {
        guard !sanitizedEmail.isEmpty, !password.isEmpty else { return }
        guard isValidEmailFormat(sanitizedEmail) else {
            showToast(message: "settings.cloud.message.emailInvalidFormat", error: true)
            return
        }

        isLoading = true
        defer { isLoading = false }

        do {
            try await sessionManager.login(email: sanitizedEmail, password: password)
            showToast(message: "settings.cloud.message.loginSuccess")
            await BackendAutoBackup.scheduleNextBackup(container: modelContext.container)
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
    }

    @MainActor
    private func signUp() async {
        guard !sanitizedEmail.isEmpty, !password.isEmpty else { return }
        guard isSignUpPasswordValid else {
            showToast(message: "settings.cloud.message.passwordRequirementNotMet", error: true)
            return
        }
        guard await validateEmailRegistrationState(requiresExistingAccount: false) else { return }
        guard canSendAuthEmailNow() else { return }

        isLoading = true
        defer { isLoading = false }

        do {
            let emailSent = try await sessionManager.register(email: sanitizedEmail, password: password)
            if emailSent {
                markAuthEmailSentNow()
            }
            showToast(
                message: emailSent ? "settings.cloud.message.signupVerificationSent" : "settings.cloud.message.signupSuccess"
            )
            password = ""
            isSignUpMode = false
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
    }

    @MainActor
    private func resendVerificationEmail() async {
        guard !sanitizedEmail.isEmpty else { return }
        guard await validateEmailRegistrationState(requiresExistingAccount: true) else { return }
        guard canSendAuthEmailNow() else { return }

        isSendingVerification = true
        defer { isSendingVerification = false }

        do {
            _ = try await sessionManager.resendVerification(email: sanitizedEmail)
            markAuthEmailSentNow()
            showToast(message: "settings.cloud.message.verificationEmailResent")
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
    }

    @MainActor
    private func sendPasswordResetEmail() async {
        guard !sanitizedEmail.isEmpty else { return }
        guard await validateEmailRegistrationState(requiresExistingAccount: true) else { return }
        guard canSendAuthEmailNow() else { return }

        isSendingPasswordReset = true
        defer { isSendingPasswordReset = false }

        do {
            try await sessionManager.forgotPassword(email: sanitizedEmail)
            markAuthEmailSentNow()
            showToast(message: "settings.cloud.message.resetEmailSent")
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
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
            showToast(message: "settings.cloud.message.backupFailed", error: true)
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
            showToast(message: "settings.cloud.message.restoreFailed", error: true)
        }
    }
}
