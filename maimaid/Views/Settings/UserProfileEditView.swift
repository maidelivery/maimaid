import SwiftUI
import SwiftData

struct UserProfileEditView: View {
    enum Mode: Identifiable {
        case create
        case edit(UserProfile)
        
        var id: String {
            switch self {
            case .create: return "create"
            case .edit(let p): return p.id.uuidString
            }
        }
    }
    
    let mode: Mode
    
    @Environment(\.modelContext) private var modelContext
    @Environment(\.dismiss) private var dismiss
    @Query private var profiles: [UserProfile]
    @Query private var songs: [Song]
    
    @State private var name: String = ""
    @State private var selectedServer: GameServer = .jp
    @State private var dfUsername: String = ""
    @State private var dfImportToken: String = ""
    @State private var lxnsRefreshToken: String = ""
    @State private var b35Count: Int = 35
    @State private var b15Count: Int = 15
    
    private var isEditing: Bool {
        if case .edit = mode { return true }
        return false
    }
    
    private var existingProfile: UserProfile? {
        if case .edit(let p) = mode { return p }
        return nil
    }
    
    private var detectedLatestVersion: String {
        ServerVersionService.shared.latestVersion(for: selectedServer, songs: songs)
    }
    
    var body: some View {
        Form {
            Section("userProfile.section.basic") {
                TextField("userProfile.name", text: $name)
                
                Picker("userProfile.server", selection: $selectedServer) {
                    ForEach(GameServer.allCases) { server in
                        Text(server.displayName).tag(server)
                    }
                }
                
                // Show detected latest version for selected server
                HStack {
                    Text("userProfile.latestVersion")
                        .foregroundColor(.secondary)
                    Spacer()
                    Text(ThemeUtils.versionAbbreviation(detectedLatestVersion))
                        .foregroundColor(.blue)
                        .font(.subheadline.bold())
                }
            }
            
            Section("userProfile.section.b50") {
                HStack {
                    Text("bestTable.settings.old")
                    Spacer()
                    TextField("", value: $b35Count, format: .number)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 60)
                }
                
                HStack {
                    Text("bestTable.settings.new")
                    Spacer()
                    TextField("", value: $b15Count, format: .number)
                        .keyboardType(.numberPad)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 60)
                }
            }
            
            Section("userProfile.section.credentials") {
                TextField("settings.sync.dfUsername", text: $dfUsername)
                    .autocapitalization(.none)
                SecureField("settings.sync.dfToken", text: $dfImportToken)
                SecureField("settings.sync.lxnsToken", text: $lxnsRefreshToken)
            }
        }
        .navigationTitle(isEditing ? "userProfile.editTitle" : "userProfile.createTitle")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("userProfile.cancel") {
                    dismiss()
                }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("userProfile.save") {
                    save()
                    dismiss()
                }
                .disabled(name.trimmingCharacters(in: .whitespaces).isEmpty)
            }
        }
        .onAppear {
            if let p = existingProfile {
                name = p.name
                selectedServer = GameServer(rawValue: p.server) ?? .jp
                dfUsername = p.dfUsername
                dfImportToken = p.dfImportToken
                lxnsRefreshToken = p.lxnsRefreshToken
                b35Count = p.b35Count
                b15Count = p.b15Count
            }
        }
    }
    
    private func save() {
        if let profile = existingProfile {
            // Update existing
            profile.name = name.trimmingCharacters(in: .whitespaces)
            profile.server = selectedServer.rawValue
            profile.dfUsername = dfUsername
            profile.dfImportToken = dfImportToken
            profile.lxnsRefreshToken = lxnsRefreshToken
            profile.b35Count = max(1, b35Count)
            profile.b15Count = max(1, b15Count)
        } else {
            // Create new
            let isFirstProfile = profiles.isEmpty
            let profile = UserProfile(
                name: name.trimmingCharacters(in: .whitespaces),
                server: selectedServer.rawValue,
                isActive: isFirstProfile,
                dfUsername: dfUsername,
                dfImportToken: dfImportToken,
                lxnsRefreshToken: lxnsRefreshToken,
                b35Count: max(1, b35Count),
                b15Count: max(1, b15Count)
            )
            modelContext.insert(profile)
        }
        try? modelContext.save()
    }
}
