import SwiftUI
import Supabase
import SwiftData

struct SupabaseAuthView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]

    @State private var supabaseManager = SupabaseManager.shared
    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var isSyncing = false
    @State private var toastMessage: String? = nil
    @State private var isError = false
    @State private var isSignUpMode = false
    @FocusState private var focusedField: Field?

    private enum Field {
        case email
        case password
    }

    private var isBusy: Bool {
        isLoading || isSyncing
    }
    
    private var config: SyncConfig? { configs.first }

    var body: some View {
        ZStack(alignment: .bottom) {
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()

            List {
                if !supabaseManager.isConfigured {
                    configurationContent
                } else if supabaseManager.isAuthenticated, let user = supabaseManager.currentUser {
                    authenticatedContent(user: user)
                } else {
                    authenticationContent
                }
            }
            .listStyle(.insetGrouped)
            .scrollContentBackground(.hidden)
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
    }

    // MARK: - Toast helper

    private func showToast(message: String, error: Bool = false) {
        isError = error
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            toastMessage = message
        }

        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            if toastMessage == message {
                withAnimation {
                    toastMessage = nil
                }
            }
        }

        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(error ? .error : .success)
    }

    // MARK: - Authenticated View

    @ViewBuilder
    private func authenticatedContent(user: User) -> some View {
        Section {
            accountSummaryCard(
                icon: "person.crop.circle.badge.checkmark",
                title: user.email ?? String(localized: "common.unknown"),
                subtitle: String(localized: "settings.cloud.status.loggedIn")
            )
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
        .listRowBackground(Color.clear)
        .listSectionSeparator(.hidden)

        Section {
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
                HStack(spacing: 12) {
                    ProgressView()
                    Text("settings.cloud.syncing")
                        .foregroundStyle(.secondary)
                }
            }
        } header: {
            Text("settings.cloud.section.sync")
        }
        
        Section(header: Text("settings.cloud.section.autoBackup"), footer: Text("settings.cloud.autoBackup.footer")) {
            let currentInterval = config?.supabaseBackupInterval ?? 0
            
            Picker("settings.cloud.autoBackup.interval", selection: Binding(
                get: { currentInterval },
                set: { newValue in
                    if let config {
                        config.supabaseBackupInterval = newValue
                    } else {
                        let newConfig = SyncConfig(supabaseBackupInterval: newValue)
                        modelContext.insert(newConfig)
                    }
                    
                    try? modelContext.save()
                    Task {
                        await SupabaseAutoBackup.scheduleNextBackup(container: modelContext.container)
                    }
                }
            )) {
                Text("update.interval.disabled").tag(0)
                Text(String(localized: "update.interval.days.1")).tag(24)
                Text(String(localized: "update.interval.days.7")).tag(168)
                Text(String(localized: "update.interval.days.14")).tag(336)
                Text(String(localized: "update.interval.days.30")).tag(720)
            }
            .pickerStyle(.menu)
            
            LabeledContent("settings.cloud.autoBackup.lastBackup", value: lastBackupDisplayText)
        }

        Section {
            Button(role: .destructive) {
                focusedField = nil
                Task { await logout() }
            } label: {
                HStack {
                    Text("settings.cloud.logout")
                    Spacer()
                    if isLoading {
                        ProgressView()
                            .tint(.red)
                    }
                }
            }
            .disabled(isBusy)
        }
    }

    private func actionRow(
        title: String,
        icon: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
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
        .disabled(isBusy)
        .opacity(isBusy ? 0.6 : 1.0)
    }

    // MARK: - Authentication View

    @ViewBuilder
    private var configurationContent: some View {
        Section {
            accountSummaryCard(
                icon: "exclamationmark.icloud.fill",
                title: String(localized: "settings.cloud.config.title"),
                subtitle: String(localized: "settings.cloud.config.subtitle")
            )
        }
        .listRowInsets(EdgeInsets(top: 8, leading: 0, bottom: 8, trailing: 0))
        .listRowBackground(Color.clear)
        .listSectionSeparator(.hidden)

        Section("settings.cloud.config.section") {
            VStack(alignment: .leading, spacing: 8) {
                Text("settings.cloud.config.step.copy")
                Text("settings.cloud.config.step.fill")
                if let description = supabaseManager.configurationErrorDescription {
                    Text(description)
                        .foregroundStyle(.secondary)
                }
            }
            .font(.subheadline)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.vertical, 4)
        }
    }

    @ViewBuilder
    private var authenticationContent: some View {
        Section {
            accountSummaryCard(
                icon: "cloud.fill",
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
                            .fontWeight(.semibold)
                    }
                    Spacer()
                }
            }
            .buttonStyle(.borderedProminent)
            .controlSize(.large)
            .disabled(email.isEmpty || password.isEmpty || isBusy)
            .listRowBackground(Color.clear)
        }
        .listSectionSeparator(.hidden)
    }

    private func credentialField(
        title: String,
        text: Binding<String>,
        icon: String,
        field: Field,
        isSecure: Bool
    ) -> some View {
        HStack(spacing: 12) {
            settingsIcon(icon: icon, color: .gray)

            Group {
                if isSecure {
                    SecureField(LocalizedStringKey(title), text: text)
                        .textContentType(.password)
                } else {
                    TextField(LocalizedStringKey(title), text: text)
                        .keyboardType(.emailAddress)
                        .textContentType(.emailAddress)
                }
            }
            .focused($focusedField, equals: field)
            .textInputAutocapitalization(.never)
            .autocorrectionDisabled()
            .submitLabel(isSecure ? .go : .next)
            .onSubmit {
                if field == .email {
                    focusedField = .password
                } else {
                    focusedField = nil
                    Task {
                        if isSignUpMode {
                            await signUp()
                        } else {
                            await signIn()
                        }
                    }
                }
            }
        }
        .padding(.vertical, 2)
    }

    private func accountSummaryCard(icon: String, title: String, subtitle: String) -> some View {
        VStack(alignment: .leading, spacing: 16) {
            HStack(spacing: 14) {
                Image(systemName: icon)
                    .font(.system(size: 30, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 56, height: 56)
                    .background(Color.accentColor, in: RoundedRectangle(cornerRadius: 16, style: .continuous))

                VStack(alignment: .leading, spacing: 4) {
                    Text(title)
                        .font(.headline)
                        .foregroundStyle(.primary)

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
        .background(
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .fill(Color(uiColor: .secondarySystemGroupedBackground))
        )
        .overlay {
            RoundedRectangle(cornerRadius: 20, style: .continuous)
                .stroke(Color(uiColor: .separator).opacity(0.12), lineWidth: 1)
        }
    }

    private func settingsIcon(icon: String, color: Color) -> some View {
        Image(systemName: icon)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 28, height: 28)
            .background(color, in: RoundedRectangle(cornerRadius: 8, style: .continuous))
    }

    private func toastView(message: String) -> some View {
        HStack(spacing: 10) {
            Image(systemName: isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                .foregroundStyle(isError ? .red : .green)

            Text(LocalizedStringKey(message))
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(.black.opacity(0.82), in: Capsule())
    }

    // MARK: - Actions
    
    private var lastBackupDisplayText: String {
        guard let lastBackup = config?.lastSupabaseBackupDate else {
            return String(localized: "settings.cloud.autoBackup.never")
        }
        return lastBackup.formatted(date: .numeric, time: .shortened)
    }

    func signIn() async {
        guard !email.isEmpty, !password.isEmpty else { return }
        guard let client = supabaseManager.client else {
            showToast(message: supabaseManager.configurationErrorDescription ?? String(localized: "settings.cloud.config.error.unconfigured"), error: true)
            return
        }

        isLoading = true
        do {
            let _ = try await client.auth.signIn(email: email, password: password)
            showToast(message: "settings.cloud.message.loginSuccess")
            await supabaseManager.checkSession()
            await SupabaseAutoBackup.scheduleNextBackup(container: modelContext.container)
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
        isLoading = false
    }

    func signUp() async {
        guard !email.isEmpty, !password.isEmpty else { return }
        guard let client = supabaseManager.client else {
            showToast(message: supabaseManager.configurationErrorDescription ?? String(localized: "settings.cloud.config.error.unconfigured"), error: true)
            return
        }

        isLoading = true
        do {
            let _ = try await client.auth.signUp(email: email, password: password)
            showToast(message: "settings.cloud.message.signupSuccess")
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
        isLoading = false
    }

    func logout() async {
        guard let client = supabaseManager.client else { return }

        isLoading = true
        do {
            try await client.auth.signOut()
        } catch {
            print("Logout error: \(error)")
        }
        isLoading = false
        await supabaseManager.checkSession()
        SupabaseAutoBackup.cancelScheduledBackup()
    }

    func performBackup() async {
        guard !isBusy else { return }

        isSyncing = true
        do {
            try await supabaseManager.backupToCloud(context: modelContext)
            showToast(message: "settings.cloud.message.backupSuccess")
            await SupabaseAutoBackup.scheduleNextBackup(container: modelContext.container)
        } catch {
            showToast(message: "settings.cloud.message.backupFailed", error: true)
        }
        isSyncing = false
    }

    func performRestore() async {
        guard !isBusy else { return }

        isSyncing = true
        do {
            try await supabaseManager.restoreFromCloud(context: modelContext)
            showToast(message: "settings.cloud.message.restoreSuccess")
        } catch {
            showToast(message: "settings.cloud.message.restoreFailed", error: true)
        }
        isSyncing = false
    }
}
