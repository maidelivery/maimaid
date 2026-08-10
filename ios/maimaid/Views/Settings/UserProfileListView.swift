import SwiftUI
import SwiftData

struct UserProfileListView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]
    @State private var showingCreateProfile = false
    @State private var editingProfile: UserProfile?
    
    // Cache server versions to avoid recomputing per-row
    @State private var serverVersionCache: [GameServer: String] = [:]

    private struct SongVersionSnapshot: Sendable {
        let version: String?
        let category: String
        let releaseDate: String?
        let hasAnyRegion: Bool
        let hasJpRegion: Bool
        let hasIntlRegion: Bool
        let hasCnRegion: Bool
    }
    
    var body: some View {
        List {
            if profiles.isEmpty {
                ContentUnavailableView {
                    Label("userProfile.empty.title", systemImage: "person.crop.circle.badge.plus")
                } description: {
                    Text("userProfile.empty.description")
                }
            } else {
                ForEach(profiles) { profile in
                    Button {
                        switchToProfile(profile)
                    } label: {
                        profileRow(profile)
                            .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
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
    }
    
    private func buildServerVersionCache() async {
        let container = modelContext.container
        let sequence = UserDefaults.app.maimaiVersionSequence
        let cutoffs = Dictionary(uniqueKeysWithValues: GameServer.allCases.map { server in
            (server.rawValue, ServerVersionService.shared.cutoffDate(for: server))
        })

        let cache = await Task.detached(priority: .utility) {
            let context = ModelContext(container)
            let songs = (try? context.fetch(FetchDescriptor<Song>())) ?? []
            let snapshots = songs.map { song in
                let sheets = song.sheets
                let hasJpRegion = sheets.contains { $0.regionJp }
                let hasIntlRegion = sheets.contains { $0.regionIntl }
                let hasCnRegion = sheets.contains { $0.regionCn }
                return SongVersionSnapshot(
                    version: song.version,
                    category: song.category,
                    releaseDate: song.releaseDate,
                    hasAnyRegion: hasJpRegion || hasIntlRegion || hasCnRegion,
                    hasJpRegion: hasJpRegion,
                    hasIntlRegion: hasIntlRegion,
                    hasCnRegion: hasCnRegion
                )
            }

            var result: [GameServer: String] = [:]
            for server in GameServer.allCases {
                result[server] = UserProfileListView.latestVersion(
                    for: server,
                    songs: snapshots,
                    sequence: sequence,
                    cutoff: cutoffs[server.rawValue] ?? "9999-12-31"
                )
            }
            return result
        }.value

        guard !Task.isCancelled else { return }
        serverVersionCache = cache
    }

    nonisolated private static func latestVersion(
        for server: GameServer,
        songs: [SongVersionSnapshot],
        sequence: [String],
        cutoff: String
    ) -> String {
        let orderedVersions: [String]
        if sequence.isEmpty {
            orderedVersions = Set(songs.compactMap(\.version)).sorted()
        } else {
            orderedVersions = sequence
        }

        var serverVersion = orderedVersions.first ?? sequence.last ?? ""
        for version in orderedVersions {
            let activeSongs = songs.filter { song in
                song.version == version &&
                !song.category.localizedStandardContains("utage") &&
                !song.category.contains("宴") &&
                song.hasAnyRegion
            }
            guard !activeSongs.isEmpty else { continue }

            let playableCount = activeSongs.count { song in
                let hasRegion: Bool
                switch server {
                case .jp: hasRegion = song.hasJpRegion
                case .intl: hasRegion = song.hasIntlRegion
                case .cn: hasRegion = song.hasCnRegion
                }
                if hasRegion { return true }
                guard let releaseDate = song.releaseDate, !releaseDate.isEmpty else { return true }
                return releaseDate <= cutoff
            }

            guard playableCount > 0 else { break }
            serverVersion = version
            if playableCount < activeSongs.count { break }
        }
        return serverVersion
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
                        .foregroundStyle(.blue.opacity(0.6))
                }
            }
            
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(profile.name.isEmpty ? String(localized: "userProfile.unnamed") : profile.name)
                        .font(.headline)
                    
                    if profile.isActive {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.green)
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
                        .foregroundStyle(serverColor(server))
                        .clipShape(Capsule())
                    
                    Text(ThemeUtils.versionAbbreviation(version))
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            
            Spacer()
            
            if profile.isActive {
                Text("userProfile.active")
                    .font(.caption2)
                    .foregroundStyle(.green)
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

        if BackendSessionManager.shared.isAuthenticated {
            Task {
                try? await BackendIncrementalSyncService.pushProfileUpdate(profile: profile, clientUpdatedAt: nil)
            }
        }
    }
    
    private func deleteProfile(_ profile: UserProfile) {
        guard !profile.isActive else { return }

        let profileId = profile.id
        if BackendSessionManager.shared.isAuthenticated {
            Task {
                do {
                    try await BackendIncrementalSyncService.deleteProfile(profileId: profileId, context: modelContext)
                } catch {
                    return
                }
                removeProfileLocally(profileId)
            }
            return
        }

        removeProfileLocally(profileId)
    }

    private func removeProfileLocally(_ profileId: UUID) {
        // Delete associated scores
        let scoreDescriptor = FetchDescriptor<Score>(predicate: #Predicate { $0.userProfileId == profileId })
        if let scores = try? modelContext.fetch(scoreDescriptor) {
            for score in scores {
                modelContext.delete(score)
            }
        }

        let recordDescriptor = FetchDescriptor<PlayRecord>(predicate: #Predicate { $0.userProfileId == profileId })
        if let records = try? modelContext.fetch(recordDescriptor) {
            for record in records {
                modelContext.delete(record)
            }
        }

        let profileDescriptor = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.id == profileId })
        if let targetProfile = (try? modelContext.fetch(profileDescriptor))?.first {
            modelContext.delete(targetProfile)
        }
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
