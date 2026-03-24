import SwiftUI
import SwiftData

struct BackendAuthView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @State private var sessionManager = BackendSessionManager.shared

    @State private var isSignUpMode = false
    @State private var email = ""
    @State private var password = ""
    @State private var resetToken = ""
    @State private var newPassword = ""

    @State private var isWorking = false
    @State private var alertMessage: String?

    private var config: SyncConfig? {
        configs.first
    }

    private var hasResetInputs: Bool {
        !resetToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !newPassword.isEmpty
    }

    var body: some View {
        List {
            if !sessionManager.isConfigured {
                Section {
                    Text("settings.cloud.config.error.unconfigured")
                        .foregroundStyle(.secondary)
                }
            } else if sessionManager.isAuthenticated, let user = sessionManager.currentUser {
                signedInSection(user: user)
            } else {
                signInSection
            }
        }
        .navigationTitle("settings.cloud.title")
        .navigationBarTitleDisplayMode(.inline)
        .task {
            await sessionManager.checkSession()
        }
        .alert(
            "settings.cloud.title",
            isPresented: Binding(
                get: { alertMessage != nil },
                set: { value in
                    if !value {
                        alertMessage = nil
                    }
                }
            )
        ) {
            Button("OK", role: .cancel) {
                alertMessage = nil
            }
        } message: {
            Text(alertMessage ?? "")
        }
    }

    @ViewBuilder
    private func signedInSection(user: BackendAuthUser) -> some View {
        Section("settings.cloud.section.sync") {
            LabeledContent("settings.cloud.email", value: user.email)

            Button {
                Task {
                    await triggerBackup()
                }
            } label: {
                rowLabel(title: "settings.cloud.backup", icon: "icloud.and.arrow.up")
            }
            .disabled(isWorking)

            Button {
                Task {
                    await triggerRestore()
                }
            } label: {
                rowLabel(title: "settings.cloud.restore", icon: "icloud.and.arrow.down")
            }
            .disabled(isWorking)
        }

        Section("settings.cloud.section.autoBackup") {
            Picker(
                "settings.cloud.autoBackup.interval",
                selection: Binding(
                    get: { config?.cloudBackupInterval ?? 0 },
                    set: { value in
                        let current = ensureSyncConfig()
                        current.cloudBackupInterval = value
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

            LabeledContent("settings.cloud.autoBackup.lastBackup", value: lastBackupText)
        }

        Section {
            Button("settings.cloud.logout", role: .destructive) {
                Task {
                    await sessionManager.logout()
                    BackendAutoBackup.cancelScheduledBackup()
                }
            }
            .disabled(isWorking)
        }
    }

    private var signInSection: some View {
        Group {
            Section {
                Picker("", selection: $isSignUpMode) {
                    Text("settings.cloud.mode.login").tag(false)
                    Text("settings.cloud.mode.signup").tag(true)
                }
                .pickerStyle(.segmented)

                TextField("settings.cloud.email", text: $email)
                    .keyboardType(.emailAddress)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                SecureField("settings.cloud.password", text: $password)

                Button {
                    Task {
                        await submitAuth()
                    }
                } label: {
                    rowLabel(
                        title: isSignUpMode ? "settings.cloud.signup.button" : "settings.cloud.login.button",
                        icon: isSignUpMode ? "person.badge.plus" : "person.badge.key"
                    )
                }
                .disabled(isWorking || email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || password.isEmpty)
            } header: {
                Text(isSignUpMode ? "settings.cloud.signup.title" : "settings.cloud.login.title")
            }

            Section {
                Button("settings.cloud.forgotPassword") {
                    Task {
                        await submitForgotPassword()
                    }
                }
                .disabled(isWorking || email.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)

                TextField("Token", text: $resetToken)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()

                SecureField("settings.cloud.recovery.newPassword", text: $newPassword)

                Button("settings.cloud.recovery.updateButton") {
                    Task {
                        await submitResetPassword()
                    }
                }
                .disabled(isWorking || !hasResetInputs)
            } header: {
                Text("settings.cloud.recovery.title")
            }
        }
    }

    private var lastBackupText: String {
        guard let date = config?.lastCloudBackupDate else {
            return String(localized: "settings.cloud.autoBackup.never")
        }
        return date.formatted(date: .abbreviated, time: .shortened)
    }

    private func rowLabel(title: LocalizedStringKey, icon: String) -> some View {
        HStack {
            if isWorking {
                ProgressView()
                    .controlSize(.small)
            } else {
                Image(systemName: icon)
            }
            Text(title)
        }
    }

    private func ensureSyncConfig() -> SyncConfig {
        if let config {
            return config
        }
        let created = SyncConfig()
        modelContext.insert(created)
        return created
    }

    @MainActor
    private func submitAuth() async {
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalizedEmail.isEmpty, !password.isEmpty else { return }

        isWorking = true
        defer { isWorking = false }

        do {
            if isSignUpMode {
                try await sessionManager.register(email: normalizedEmail, password: password)
                alertMessage = String(localized: "settings.cloud.message.signupSuccess")
            } else {
                try await sessionManager.login(email: normalizedEmail, password: password)
                alertMessage = String(localized: "settings.cloud.message.loginSuccess")
            }
            await BackendAutoBackup.scheduleNextBackup(container: modelContext.container)
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    @MainActor
    private func submitForgotPassword() async {
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !normalizedEmail.isEmpty else { return }

        isWorking = true
        defer { isWorking = false }

        do {
            try await sessionManager.forgotPassword(email: normalizedEmail)
            alertMessage = String(localized: "settings.cloud.message.resetEmailSent")
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    @MainActor
    private func submitResetPassword() async {
        let trimmedToken = resetToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedToken.isEmpty, !newPassword.isEmpty else { return }

        isWorking = true
        defer { isWorking = false }

        do {
            try await sessionManager.resetPassword(token: trimmedToken, newPassword: newPassword)
            resetToken = ""
            newPassword = ""
            alertMessage = String(localized: "settings.cloud.message.passwordUpdated")
        } catch {
            alertMessage = error.localizedDescription
        }
    }

    @MainActor
    private func triggerBackup() async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await BackendCloudSyncService.backupToCloud(context: modelContext)
            alertMessage = String(localized: "settings.cloud.message.backupSuccess")
            await BackendAutoBackup.scheduleNextBackup(container: modelContext.container)
        } catch {
            alertMessage = String(localized: "settings.cloud.message.backupFailed") + ": \(error.localizedDescription)"
        }
    }

    @MainActor
    private func triggerRestore() async {
        isWorking = true
        defer { isWorking = false }
        do {
            try await BackendCloudSyncService.restoreFromCloud(context: modelContext)
            alertMessage = String(localized: "settings.cloud.message.restoreSuccess")
        } catch {
            alertMessage = String(localized: "settings.cloud.message.restoreFailed") + ": \(error.localizedDescription)"
        }
    }
}
