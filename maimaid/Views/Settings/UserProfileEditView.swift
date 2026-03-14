import SwiftUI
import SwiftData
import PhotosUI

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
    @State private var plate: String = ""
    @State private var selectedServer: GameServer = .jp
    @State private var dfUsername: String = ""
    @State private var dfImportToken: String = ""
    @State private var lxnsRefreshToken: String = ""
    @State private var b35Count: Int = 35
    @State private var b15Count: Int = 15
    
    @State private var avatarUrl: String?
    @State private var selectedItem: PhotosPickerItem?
    @State private var selectedImageData: Data?
    
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
            Section {
                HStack {
                    Spacer()
                    VStack(spacing: 12) {
                        PhotosPicker(selection: $selectedItem, matching: .images) {
                            ZStack {
                                if let data = selectedImageData, let uiImage = UIImage(data: data) {
                                    Image(uiImage: uiImage)
                                        .resizable()
                                        .aspectRatio(contentMode: .fill)
                                        .frame(width: 100, height: 100)
                                        .clipShape(Circle())
                                } else if let urlString = avatarUrl {
                                    // Try to load local version if it's a preset URL
                                    let localImage: UIImage? = {
                                        if let idString = urlString.components(separatedBy: "/").last?.replacingOccurrences(of: ".png", with: ""),
                                           let id = Int(idString) {
                                            return ImageDownloader.shared.loadImage(iconId: id)
                                        }
                                        return nil
                                    }()
                                    
                                    if let uiImage = localImage {
                                        Image(uiImage: uiImage)
                                            .resizable()
                                            .aspectRatio(contentMode: .fill)
                                            .frame(width: 100, height: 100)
                                            .clipShape(Circle())
                                    } else if let url = URL(string: urlString) {
                                        AsyncImage(url: url) { image in
                                            image.resizable()
                                                .aspectRatio(contentMode: .fill)
                                        } placeholder: {
                                            ProgressView()
                                        }
                                        .frame(width: 100, height: 100)
                                        .clipShape(Circle())
                                    } else {
                                        Circle()
                                            .fill(Color.secondary.opacity(0.1))
                                            .frame(width: 100, height: 100)
                                        
                                        Image(systemName: "person.fill")
                                            .font(.system(size: 40))
                                            .foregroundColor(.secondary)
                                    }
                                } else {
                                    Circle()
                                        .fill(Color.secondary.opacity(0.1))
                                        .frame(width: 100, height: 100)
                                    
                                    Image(systemName: "person.fill")
                                        .font(.system(size: 40))
                                        .foregroundColor(.secondary)
                                }
                                
                                // Overlay hint
                                VStack {
                                    Spacer()
                                    Text("profile.edit.changeAvatar")
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(.white)
                                        .padding(.vertical, 4)
                                        .frame(maxWidth: .infinity)
                                        .background(Color.black.opacity(0.4))
                                }
                                .clipShape(Circle())
                            }
                            .frame(width: 100, height: 100)
                            .overlay(Circle().stroke(Color.primary.opacity(0.1), lineWidth: 1))
                            .shadow(color: .black.opacity(0.05), radius: 5, y: 2)
                        }
                        
                        if selectedImageData != nil || avatarUrl != nil {
                            Button("profile.edit.clearAvatar", role: .destructive) {
                                selectedImageData = nil
                                avatarUrl = nil
                            }
                            .font(.subheadline)
                        }
                    }
                    Spacer()
                }
                .listRowBackground(Color.clear)
                .padding(.vertical, 10)
            }
            
            Section("userProfile.section.basic") {
                TextField("userProfile.name", text: $name)
                TextField("profile.edit.titleName", text: $plate)
                
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
            
            if isEditing {
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
            
            Section("profile.edit.presetIcon") {
                NavigationLink {
                    MaimaiIconPicker(avatarUrl: $avatarUrl, selectedImageData: $selectedImageData)
                } label: {
                    HStack {
                        Text("profile.edit.presetIcon.select")
                        Spacer()
                        if let avatarUrl = avatarUrl, avatarUrl.contains("lxns.net") {
                            if let idString = avatarUrl.components(separatedBy: "/").last?.replacingOccurrences(of: ".png", with: ""),
                               let id = Int(idString),
                               let localImage = ImageDownloader.shared.loadImage(iconId: id) {
                                Image(uiImage: localImage)
                                    .resizable()
                                    .aspectRatio(contentMode: .fill)
                                    .frame(width: 30, height: 30)
                                    .clipShape(Circle())
                            } else {
                                AsyncImage(url: URL(string: avatarUrl)) { image in
                                    image.resizable()
                                        .aspectRatio(contentMode: .fill)
                                } placeholder: {
                                    Color.gray.opacity(0.1)
                                }
                                .frame(width: 30, height: 30)
                                .clipShape(Circle())
                            }
                        }
                    }
                }
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
                plate = p.plate ?? ""
                selectedServer = GameServer(rawValue: p.server) ?? .jp
                dfUsername = p.dfUsername
                dfImportToken = p.dfImportToken
                lxnsRefreshToken = p.lxnsRefreshToken
                b35Count = p.b35Count
                b15Count = p.b15Count
                selectedImageData = p.avatarData
                avatarUrl = p.avatarUrl
            }
        }
        .onChange(of: selectedItem) { _, newItem in
            Task {
                if let data = try? await newItem?.loadTransferable(type: Data.self) {
                    selectedImageData = data
                }
            }
        }
    }
    
    private func save() {
        if let profile = existingProfile {
            // Update existing
            profile.name = name.trimmingCharacters(in: .whitespaces)
            profile.plate = plate.trimmingCharacters(in: .whitespaces)
            profile.server = selectedServer.rawValue
            profile.dfUsername = dfUsername
            profile.dfImportToken = dfImportToken
            profile.lxnsRefreshToken = lxnsRefreshToken
            profile.b35Count = max(1, b35Count)
            profile.b15Count = max(1, b15Count)
            profile.avatarData = selectedImageData
            profile.avatarUrl = avatarUrl
        } else {
            // Create new
            let isFirstProfile = profiles.isEmpty
            let profile = UserProfile(
                name: name.trimmingCharacters(in: .whitespaces),
                server: selectedServer.rawValue,
                avatarData: selectedImageData,
                avatarUrl: avatarUrl,
                isActive: isFirstProfile,
                dfUsername: dfUsername,
                dfImportToken: dfImportToken,
                lxnsRefreshToken: lxnsRefreshToken,
                playerRating: 0,
                plate: plate.trimmingCharacters(in: .whitespaces),
                b35Count: max(1, b35Count),
                b15Count: max(1, b15Count)
            )
            modelContext.insert(profile)
        }
        try? modelContext.save()
    }
}
