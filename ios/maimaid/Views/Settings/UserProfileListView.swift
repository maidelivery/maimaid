import SwiftUI
import SwiftData

struct UserProfileListView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \UserProfile.createdAt) private var profiles: [UserProfile]
    @State private var showingCreateProfile = false
    @State private var editingMode: UserProfileEditView.Mode?
    @State private var deletingProfileId: UUID?
    @State private var errorMessage: String?
    
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
                        switchToProfile(profile.id)
                    } label: {
                        profileRow(profile)
                            .contentShape(.rect)
                    }
                    .buttonStyle(.plain)
                    .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                        if !profile.isActive {
                            Button(role: .destructive) {
                                deletingProfileId = profile.id
                            } label: {
                                Label("userProfile.delete", systemImage: "trash")
                            }
                        }

                        Button {
                            editingMode = .edit(profile.id)
                        } label: {
                            Label("userProfile.edit", systemImage: "pencil")
                        }
                        .tint(.blue)
                    }
                }
            }
        }
        .navigationTitle("userProfile.title")
        .confirmationDialog(
            "userProfile.delete.confirm.title",
            isPresented: Binding(
                get: { deletingProfileId != nil },
                set: { if !$0 { deletingProfileId = nil } }
            ),
            titleVisibility: .visible,
            presenting: deletingProfileId
        ) { profileId in
            Button("userProfile.delete", role: .destructive) {
                deletingProfileId = nil
                deleteProfile(profileId)
            }
            Button("userProfile.cancel", role: .cancel) {
                deletingProfileId = nil
            }
        } message: { _ in
            Text("userProfile.delete.confirm.message")
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button("userProfile.createTitle", systemImage: "plus") {
                    showingCreateProfile = true
                }
            }
        }
        .sheet(isPresented: $showingCreateProfile) {
            NavigationStack {
                UserProfileEditView(mode: .create)
            }
        }
        .sheet(item: $editingMode) { mode in
            NavigationStack {
                UserProfileEditView(mode: mode)
            }
        }
        .alert(
            "userProfile.error.title",
            isPresented: Binding(
                get: { errorMessage != nil },
                set: { if !$0 { errorMessage = nil } }
            )
        ) {
            Button("common.ok", role: .cancel) {}
        } message: {
            Text(errorMessage ?? "")
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
    
    private func switchToProfile(_ profileId: UUID) {
        do {
            let didActivate = try UserProfileMutationService.activate(
                profileId: profileId,
                context: modelContext
            )
            if didActivate {
                Task {
                    await UserProfileMutationService.synchronizeProfileUpdate(
                        profileId: profileId,
                        context: modelContext
                    )
                }
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func deleteProfile(_ profileId: UUID) {
        do {
            let didDelete = try UserProfileMutationService.delete(
                profileId: profileId,
                context: modelContext
            )
            if didDelete {
                Task {
                    await UserProfileMutationService.synchronizeProfileDeletion(
                        profileId: profileId,
                        context: modelContext
                    )
                }
            }
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func serverColor(_ server: GameServer) -> Color {
        switch server {
        case .jp:   return .red
        case .intl: return .blue
        case .cn:   return .orange
        }
    }
}
