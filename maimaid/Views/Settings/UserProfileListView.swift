import SwiftUI
import SwiftData

struct UserProfileListView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var profiles: [UserProfile]
    @Query private var songs: [Song]
    @State private var showingCreateProfile = false
    @State private var editingProfile: UserProfile?
    
    // Cache server versions to avoid recomputing per-row
    @State private var serverVersionCache: [GameServer: String] = [:]
    
    var body: some View {
        List {
            if profiles.isEmpty {
                ContentUnavailableView {
                    Label("userProfile.empty.title", systemImage: "person.crop.circle.badge.plus")
                } description: {
                    Text("userProfile.empty.description")
                }
            } else {
                ForEach(profiles.sorted(by: { $0.createdAt < $1.createdAt })) { profile in
                    profileRow(profile)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            switchToProfile(profile)
                        }
                        .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                            if !profile.isActive {
                                Button(role: .destructive) {
                                    deleteProfile(profile)
                                } label: {
                                    Label("userProfile.delete", systemImage: "trash")
                                }
                            }
                            
                            Button {
                                editingProfile = profile
                            } label: {
                                Label("userProfile.edit", systemImage: "pencil")
                            }
                            .tint(.blue)
                        }
                }
            }
        }
        .navigationTitle("userProfile.title")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showingCreateProfile = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .sheet(isPresented: $showingCreateProfile) {
            NavigationStack {
                UserProfileEditView(mode: .create)
            }
        }
        .sheet(item: $editingProfile) { profile in
            NavigationStack {
                UserProfileEditView(mode: .edit(profile))
            }
        }
        .task {
            await buildServerVersionCache()
        }
        .onChange(of: songs.count) { _, _ in
            Task {
                await buildServerVersionCache()
            }
        }
    }
    
    private func buildServerVersionCache() async {
        // Compute all server versions once, off the rendering path
        var cache: [GameServer: String] = [:]
        for server in GameServer.allCases {
            cache[server] = ServerVersionService.shared.latestVersion(for: server, songs: songs)
        }
        serverVersionCache = cache
    }
    
    private func profileRow(_ profile: UserProfile) -> some View {
        HStack(spacing: 14) {
            // Avatar
            ZStack {
                if let data = profile.avatarData, let uiImage = UIImage(data: data) {
                    Image(uiImage: uiImage)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: 48, height: 48)
                        .clipShape(Circle())
                } else {
                    Image(systemName: "person.crop.circle.fill")
                        .resizable()
                        .frame(width: 48, height: 48)
                        .foregroundColor(.blue.opacity(0.6))
                }
            }
            
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(profile.name.isEmpty ? String(localized: "userProfile.unnamed") : profile.name)
                        .font(.headline)
                    
                    if profile.isActive {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                            .font(.caption)
                    }
                }
                
                let server = GameServer(rawValue: profile.server) ?? .jp
                let version = serverVersionCache[server] ?? ThemeUtils.latestVersion
                
                HStack(spacing: 6) {
                    Text(server.displayName)
                        .font(.caption)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(serverColor(server).opacity(0.15))
                        .foregroundColor(serverColor(server))
                        .clipShape(Capsule())
                    
                    Text(ThemeUtils.versionAbbreviation(version))
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
            }
            
            Spacer()
            
            if profile.isActive {
                Text("userProfile.active")
                    .font(.caption2)
                    .foregroundColor(.green)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color.green.opacity(0.1))
                    .clipShape(Capsule())
            }
        }
        .padding(.vertical, 4)
    }
    
    private func switchToProfile(_ profile: UserProfile) {
        guard !profile.isActive else { return }
        for p in profiles {
            p.isActive = (p.id == profile.id)
        }
        try? modelContext.save()
        ScoreService.shared.notifyActiveProfileChanged()
    }
    
    private func deleteProfile(_ profile: UserProfile) {
        guard !profile.isActive else { return }
        
        let profileId = profile.id
        
        // Delete associated scores
        let scoreDescriptor = FetchDescriptor<Score>(predicate: #Predicate { $0.userProfileId == profileId })
        if let scores = try? modelContext.fetch(scoreDescriptor) {
            for score in scores {
                modelContext.delete(score)
            }
        }
        
        modelContext.delete(profile)
        try? modelContext.save()
        
        ScoreService.shared.notifyScoresChanged(for: profileId)
        ScoreService.shared.notifyActiveProfileChanged()
    }
    
    private func serverColor(_ server: GameServer) -> Color {
        switch server {
        case .jp:   return .red
        case .intl: return .blue
        case .cn:   return .orange
        }
    }
}
