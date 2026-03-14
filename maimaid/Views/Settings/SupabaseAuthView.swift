import SwiftUI
import Supabase

struct SupabaseAuthView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    
    @State private var email = ""
    @State private var password = ""
    @State private var isLoading = false
    @State private var isSyncing = false
    @State private var toastMessage: String? = nil
    @State private var isError = false
    @State private var isSignUpMode = false
    
    var body: some View {
        ZStack {
            // Background
            Color(uiColor: .systemGroupedBackground)
                .ignoresSafeArea()
            
            VStack(spacing: 0) {
                ScrollView {
                    VStack(spacing: 24) {
                        if SupabaseManager.shared.isAuthenticated, let user = SupabaseManager.shared.currentUser {
                            authenticatedContent(user: user)
                        } else {
                            authenticationContent
                        }
                    }
                    .padding(20)
                }
            }
            
            // Toast Overlay
            if let message = toastMessage {
                VStack {
                    Spacer()
                    HStack(spacing: 10) {
                        Image(systemName: isError ? "exclamationmark.circle.fill" : "checkmark.circle.fill")
                            .foregroundColor(isError ? .red : .green)
                        Text(LocalizedStringKey(message))
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.white)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.8), in: Capsule())
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .padding(.bottom, 32)
                    .zIndex(100)
                }
            }
        }
        .navigationTitle("settings.cloud.title")
        .navigationBarTitleDisplayMode(.inline)
    }
    
    // MARK: - Toast helper
    private func showToast(message: String, error: Bool = false) {
        isError = error
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            toastMessage = message
        }
        
        // Hide toast after delay
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
            if toastMessage == message {
                withAnimation {
                    toastMessage = nil
                }
            }
        }
        
        // Haptic Feedback
        let generator = UINotificationFeedbackGenerator()
        generator.notificationOccurred(error ? .error : .success)
    }
    
    // MARK: - Authenticated View
    
    @ViewBuilder
    private func authenticatedContent(user: User) -> some View {
        VStack(spacing: 24) {
            // Profile Card
            VStack(spacing: 16) {
                Image(systemName: "person.crop.circle.fill.badge.checkmark")
                    .font(.system(size: 60))
                    .foregroundStyle(.blue.gradient)
                    .padding(.top, 8)
                
                VStack(spacing: 4) {
                    Text(user.email ?? "Unknown")
                        .font(.system(size: 20, weight: .bold))
                    
                    Text("settings.cloud.status.loggedIn")
                        .font(.system(size: 14))
                        .foregroundColor(.secondary)
                }
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 24)
            .background(RoundedRectangle(cornerRadius: 24).fill(.ultraThinMaterial))
            
            // Actions
            VStack(spacing: 12) {
                Text("settings.cloud.section.sync")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(.secondary)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.leading, 8)
                
                actionCard(title: "settings.cloud.backup", icon: "icloud.and.arrow.up.fill", color: .blue) {
                    Task { await performBackup() }
                }
                
                actionCard(title: "settings.cloud.restore", icon: "icloud.and.arrow.down.fill", color: .green) {
                    Task { await performRestore() }
                }
            }
            
            if isSyncing {
                HStack(spacing: 12) {
                    ProgressView()
                    Text("settings.cloud.syncing")
                        .font(.system(size: 15))
                        .foregroundColor(.secondary)
                }
                .padding()
            }
            
            Spacer(minLength: 40)
            
            Button(role: .destructive) {
                Task { await logout() }
            } label: {
                HStack {
                    Image(systemName: "rectangle.portrait.and.arrow.right")
                    Text("settings.cloud.logout")
                }
                .font(.system(size: 17, weight: .semibold))
                .frame(maxWidth: .infinity)
                .padding(.vertical, 16)
                .background(RoundedRectangle(cornerRadius: 16).fill(.red.opacity(0.1)))
            }
        }
    }
    
    private func actionCard(title: String, icon: String, color: Color, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 16) {
                ZStack {
                    Circle()
                        .fill(color.opacity(0.1))
                        .frame(width: 40, height: 40)
                    Image(systemName: icon)
                        .foregroundColor(color)
                        .font(.system(size: 18))
                }
                
                Text(LocalizedStringKey(title))
                    .font(.system(size: 17, weight: .medium))
                    .foregroundColor(.primary)
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(.secondary.opacity(0.5))
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 20).fill(.ultraThinMaterial))
        }
        .disabled(isSyncing)
        .opacity(isSyncing ? 0.6 : 1.0)
    }
    
    // MARK: - Authentication View
    
    @ViewBuilder
    private var authenticationContent: some View {
        VStack(spacing: 28) {
            // Welcome Section
            VStack(spacing: 12) {
                Image(systemName: "cloud.fill")
                    .font(.system(size: 50))
                    .foregroundStyle(.blue.gradient)
                
                Text(isSignUpMode ? "settings.cloud.signup.title" : "settings.cloud.login.title")
                    .font(.system(size: 28, weight: .bold))
                
                Text(isSignUpMode ? "settings.cloud.signup.subtitle" : "settings.cloud.login.subtitle")
                    .font(.system(size: 15))
                    .foregroundColor(.secondary)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 20)
            }
            .padding(.top, 20)
            
            // Input Fields
            VStack(spacing: 1) {
                inputField(title: "settings.cloud.email", text: $email, icon: "envelope.fill", isSecure: false)
                Divider().padding(.leading, 50)
                inputField(title: "settings.cloud.password", text: $password, icon: "lock.fill", isSecure: true)
            }
            .background(RoundedRectangle(cornerRadius: 20).fill(.ultraThinMaterial))
            
            VStack(spacing: 20) {
                // Secondary Actions
                HStack {
                    if !isSignUpMode {
                        Button {
                            // Reset password logic
                        } label: {
                            Text("settings.cloud.forgotPassword")
                                .font(.system(size: 14, weight: .medium))
                                .foregroundColor(.blue)
                        }
                    }
                    
                    Spacer()
                    
                    Button {
                        withAnimation {
                            isSignUpMode.toggle()
                            toastMessage = nil
                        }
                    } label: {
                        Text(isSignUpMode ? "settings.cloud.mode.login" : "settings.cloud.mode.signup")
                            .font(.system(size: 14, weight: .medium))
                            .foregroundColor(.blue)
                    }
                }
                .padding(.horizontal, 4)
                
                // Submit Button
                Button {
                    Task {
                        if isSignUpMode {
                            await signUp()
                        } else {
                            await signIn()
                        }
                    }
                } label: {
                    HStack {
                        if isLoading {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Text(isSignUpMode ? "settings.cloud.signup.button" : "settings.cloud.login.button")
                                .font(.system(size: 17, weight: .bold))
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(
                        RoundedRectangle(cornerRadius: 16)
                            .fill((email.isEmpty || password.isEmpty || isLoading) ? Color.gray.opacity(0.3).gradient : Color.blue.gradient)
                    )
                    .foregroundColor(.white)
                }
                .disabled(email.isEmpty || password.isEmpty || isLoading)
                .padding(.top, 8)
            }
            
            // Privacy Hint
            HStack(spacing: 12) {
                Image(systemName: "lock.shield.fill")
                    .font(.system(size: 20))
                    .foregroundColor(.blue)
                
                Text("settings.cloud.privacy.hint")
                    .font(.system(size: 12))
                    .foregroundColor(.secondary)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 16).fill(.blue.opacity(0.05)))
        }
    }
    
    private func inputField(title: String, text: Binding<String>, icon: String, isSecure: Bool) -> some View {
        HStack(spacing: 16) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(.secondary)
                .frame(width: 24)
            
            Group {
                if isSecure {
                    SecureField(LocalizedStringKey(title), text: text)
                } else {
                    TextField(LocalizedStringKey(title), text: text)
                        .keyboardType(icon.contains("envelope") ? .emailAddress : .default)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                }
            }
            .font(.system(size: 16))
        }
        .padding(16)
    }
    
    // MARK: - Actions
    
    func signIn() async {
        isLoading = true
        do {
            let _ = try await SupabaseManager.shared.client.auth.signIn(email: email, password: password)
            showToast(message: "settings.cloud.message.loginSuccess")
            await SupabaseManager.shared.checkSession()
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
        isLoading = false
    }
    
    func signUp() async {
        isLoading = true
        do {
            let _ = try await SupabaseManager.shared.client.auth.signUp(email: email, password: password)
            showToast(message: "settings.cloud.message.signupSuccess")
        } catch {
            showToast(message: error.localizedDescription, error: true)
        }
        isLoading = false
    }
    
    func logout() async {
        isLoading = true
        do {
            try await SupabaseManager.shared.client.auth.signOut()
        } catch {
            print("Logout error: \(error)")
        }
        isLoading = false
        await SupabaseManager.shared.checkSession()
    }
    
    func performBackup() async {
        isSyncing = true
        do {
            try await SupabaseManager.shared.backupToCloud(context: modelContext)
            showToast(message: "settings.cloud.message.backupSuccess")
        } catch {
            showToast(message: "settings.cloud.message.backupFailed", error: true)
        }
        isSyncing = false
    }
    
    func performRestore() async {
        isSyncing = true
        do {
            try await SupabaseManager.shared.restoreFromCloud(context: modelContext)
            showToast(message: "settings.cloud.message.restoreSuccess")
        } catch {
            showToast(message: "settings.cloud.message.restoreFailed", error: true)
        }
        isSyncing = false
    }
}
