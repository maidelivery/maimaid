import SwiftUI
import SwiftData

struct HomeView: View {
    private static var prefetchedSongsDescriptor: FetchDescriptor<Song> {
        var descriptor = FetchDescriptor<Song>()
        descriptor.relationshipKeyPathsForPrefetching = [\Song.sheets]
        return descriptor
    }

    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query(Self.prefetchedSongsDescriptor) private var songs: [Song]
    @Query(sort: \MaimaiIcon.id) private var icons: [MaimaiIcon]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    @Query private var allScores: [Score]
    @AppStorage(UserDefaultsKeys.didPerformInitialSync) private var didPerformInitialSync = false
    @AppStorage(AppStorageKeys.didShowOnboarding) private var didShowOnboarding = false
    
    @State private var showingEditProfile = false
    @State private var showingOnboarding = false
    @State private var computedB50Total: Int = 0
    @State private var standardB50Total: Int = 0
    
    // Cache invalidation: track fingerprints to avoid redundant recalculation
    @State private var cachedScoreFingerprint = ""
    @State private var lastB50Fingerprint = ""
    @State private var hasComputedB50 = false
    @State private var b50UpdateTask: Task<Void, Never>?
    
    private var config: SyncConfig? { configs.first }
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    private var currentB35Count: Int {
        activeProfile?.b35Count ?? config?.b35Count ?? 35
    }
    
    private var currentB15Count: Int {
        activeProfile?.b15Count ?? config?.b15Count ?? 15
    }
    
    private var totalB50Count: Int {
        currentB35Count + currentB15Count
    }
    
    private var displayRating: Int {
        max(standardB50Total, activeProfile?.playerRating ?? 0)
    }
    
    /// Generates a lightweight fingerprint to detect changes relevant to B50 calculation.
    private func b50Fingerprint(scoreFingerprint: String) -> String {
        let songCount = songs.count
        let b35 = currentB35Count
        let b15 = currentB15Count
        let profileId = activeProfile?.id.uuidString ?? "none"
        let server = activeProfile?.server ?? "jp"
        return "\(songCount)_\(b35)_\(b15)_\(profileId)_\(server)_\(scoreFingerprint)"
    }
    
    let columns = [
        GridItem(.flexible(), spacing: 16),
        GridItem(.flexible(), spacing: 16)
    ]
    
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: 16) {
                    // Profile Section
                    profileHeader
                    
                    // Main "Best Table" Button
                    bestTableButton
                    
                    // Function Grid
                    functionGrid
                }
                .padding(16)
            }
            .background(Color(.systemGroupedBackground))
            .navigationTitle("home.title")
            .sheet(isPresented: $showingEditProfile) {
                NavigationStack {
                    if let profile = activeProfile {
                        UserProfileEditView(mode: .edit(profile))
                    } else {
                        UserProfileEditView(mode: .create)
                    }
                }
            }
            .sheet(isPresented: $showingOnboarding) {
                FirstLaunchView(onCompleted: {
                    didShowOnboarding = true
                    showingOnboarding = false
                })
                .presentationDetents([.large])
                .presentationDragIndicator(.hidden)
                .interactiveDismissDisabled(true)
            }
            .onAppear {
                evaluateOnboardingGate()
            }
            .onChange(of: songs.count) { _, _ in
                evaluateOnboardingGate()
                scheduleB50Update()
            }
            .onChange(of: didPerformInitialSync) { _, _ in
                evaluateOnboardingGate()
            }
            .task {
                refreshScoreFingerprintCache()
                await updateB50IfNeeded()
            }
            .onChange(of: allScores.count) { _, _ in
                refreshScoreFingerprintCache()
                scheduleB50Update()
            }
            .onChange(of: activeProfile?.b15Count) { _, _ in
                scheduleB50Update()
            }
            .onChange(of: activeProfile?.b35Count) { _, _ in
                scheduleB50Update()
            }
            .onChange(of: config?.b15Count) { _, _ in
                scheduleB50Update()
            }
            .onChange(of: config?.b35Count) { _, _ in
                scheduleB50Update()
            }
            .onChange(of: activeProfile?.id) { _, _ in
                refreshScoreFingerprintCache()
                scheduleB50Update()
            }
            .onChange(of: activeProfile?.server) { _, _ in
                scheduleB50Update()
            }
            .onDisappear {
                b50UpdateTask?.cancel()
            }
        }
    }

    private func evaluateOnboardingGate() {
        let hasLocalStaticData = !songs.isEmpty

        // Fallback for existing users: if they already have local song data, treat initial sync as completed.
        if !didPerformInitialSync && hasLocalStaticData {
            didPerformInitialSync = true
            didShowOnboarding = true
        }

        let shouldRequireInitialDownload = !didPerformInitialSync && !hasLocalStaticData

        if shouldRequireInitialDownload {
            showingOnboarding = true
        } else if showingOnboarding && !MaimaiDataFetcher.shared.isSyncing {
            showingOnboarding = false
        }
    }
    
    private func refreshScoreFingerprintCache() {
        cachedScoreFingerprint = makeScoreFingerprint(profileId: activeProfile?.id)
    }

    private func makeScoreFingerprint(profileId: UUID?) -> String {
        var scoreCount = 0
        var totalRate = 0.0
        var totalDxScore = 0
        var latestUpdate: TimeInterval = 0

        for score in allScores where score.userProfileId == profileId {
            scoreCount += 1
            totalRate += score.rate
            totalDxScore += score.dxScore
            latestUpdate = max(latestUpdate, score.achievementDate.timeIntervalSince1970)
        }

        let normalizedRate = Int((totalRate * 10_000).rounded())
        return "\(scoreCount)_\(normalizedRate)_\(totalDxScore)_\(Int(latestUpdate))"
    }

    private func scheduleB50Update() {
        b50UpdateTask?.cancel()
        b50UpdateTask = Task { @MainActor in
            await updateB50IfNeeded()
        }
    }

    private func updateB50IfNeeded() async {
        let fingerprint = b50Fingerprint(scoreFingerprint: cachedScoreFingerprint)
        guard !hasComputedB50 || fingerprint != lastB50Fingerprint else { return }
        
        // Small delay to avoid competing with navigation animations.
        try? await Task.sleep(for: .milliseconds(100))
        guard !Task.isCancelled else { return }

        // Re-check after sleep in case state changed while this task was suspended.
        let currentFingerprint = b50Fingerprint(scoreFingerprint: cachedScoreFingerprint)
        guard currentFingerprint == fingerprint else { return }

        let profileId = activeProfile?.id
        let server = activeProfile.flatMap { GameServer(rawValue: $0.server) }
        let scoreMap = await RatingUtils.fetchScoreMap(context: modelContext)
        let input = await songs.toCalculationInput(userProfileId: profileId, server: server, preloadedScores: scoreMap)

        guard !Task.isCancelled else { return }

        let b35Limit = currentB35Count
        let b15Limit = currentB15Count
        let serverVersion = activeServerLatestVersion

        let totals = await Task.detached(priority: .userInitiated) {
            if b35Limit == 35 && b15Limit == 15 {
                let shared = await RatingUtils.calculateB50(
                    input: input,
                    b35Count: 35,
                    b15Count: 15,
                    latestVersion: serverVersion
                )
                return (custom: shared.total, standard: shared.total)
            }

            async let custom = RatingUtils.calculateB50(
                input: input,
                b35Count: b35Limit,
                b15Count: b15Limit,
                latestVersion: serverVersion
            )
            async let standard = RatingUtils.calculateB50(
                input: input,
                b35Count: 35,
                b15Count: 15,
                latestVersion: serverVersion
            )

            let customResult = await custom
            let standardResult = await standard
            return (custom: customResult.total, standard: standardResult.total)
        }.value

        guard !Task.isCancelled else { return }

        computedB50Total = totals.custom
        standardB50Total = totals.standard
        lastB50Fingerprint = fingerprint
        hasComputedB50 = true
    }
    
    private var activeServerLatestVersion: String? {
        guard let profile = activeProfile, let server = GameServer(rawValue: profile.server) else { return nil }
        return ServerVersionService.shared.latestVersion(for: server, songs: songs)
    }
    
    // MARK: - Subviews
    
    private var bestTableButton: some View {
        NavigationLink(destination: BestTableView()) {
            HStack {
                Image(systemName: "trophy.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(.orange)
                
                VStack(alignment: .leading, spacing: 2) {
                    Text("home.bestTable.button.title \(totalB50Count)")
                        .font(.system(size: 16, weight: .bold))
                    Text("home.bestTable.button.subtitle \(currentB35Count) \(currentB15Count)")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.secondary.opacity(0.5))
            }
            .padding(.horizontal, 20)
            .padding(.vertical, 14)
            .background(Color.orange.opacity(0.1), in: RoundedRectangle(cornerRadius: 16))
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color.orange.opacity(0.2), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }
    
    private var functionGrid: some View {
        LazyVGrid(columns: columns, spacing: 16) {
            NavigationLink(destination: RandomSongView()) {
                functionCard(
                    icon: "dice.fill",
                    title: "home.randomSong.title",
                    subtitle: "home.randomSong.subtitle",
                    gradient: [Color.purple, Color.pink]
                )
            }
            .buttonStyle(.plain)
            
            NavigationLink(destination: RecommendationListView()) {
                functionCard(
                    icon: "sparkles",
                    title: "home.recommendation.title",
                    subtitle: "home.recommendation.subtitle",
                    gradient: [Color.orange, Color.red]
                )
            }
            .buttonStyle(.plain)
            
            NavigationLink(destination: ScoreQueryView()) {
                functionCard(
                    icon: "list.bullet.rectangle.portrait.fill",
                    title: "home.scoreQuery.title",
                    subtitle: "home.scoreQuery.subtitle",
                    gradient: [Color.indigo, Color.purple]
                )
            }
            .buttonStyle(.plain)
            
            NavigationLink(destination: PlateProgressView()) {
                functionCard(
                    icon: "chart.bar.xaxis",
                    title: "home.plateProgress.title",
                    subtitle: "home.plateProgress.subtitle",
                    gradient: [Color.green, Color.blue]
                )
            }
            .buttonStyle(.plain)
            
            NavigationLink(destination: DanListView()) {
                functionCard(
                    icon: "checkmark.seal.fill",
                    title: "home.dan.title",
                    subtitle: "home.dan.subtitle",
                    gradient: [Color.red, Color.orange]
                )
            }
            .buttonStyle(.plain)

            NavigationLink(destination: CommunityAliasVotingBoardView()) {
                functionCard(
                    icon: "person.3.sequence.fill",
                    title: "home.communityAlias.title",
                    subtitle: "home.communityAlias.subtitle",
                    gradient: [Color.teal, Color.green]
                )
            }
            .buttonStyle(.plain)
            
            NavigationLink(destination: UsefulLinksView()) {
                functionCard(
                    icon: "link",
                    title: "home.usefulLinks.title",
                    subtitle: "home.usefulLinks.subtitle",
                    gradient: [Color.blue, Color.cyan]
                )
            }
            .buttonStyle(.plain)
            
            
        }
    }
    // MARK: - Rating Badge
    
    private var ratingBadge: some View {
        Text("\(displayRating)")
            .font(.system(size: 10, weight: .black, design: .rounded))
            .foregroundStyle(.white)
            .padding(.horizontal, 6)
            .padding(.vertical, 2)
            .background(ThemeUtils.ratingColor(displayRating), in: Capsule())
            .overlay(Capsule().stroke(Color.white, lineWidth: 1))
            .offset(x: 4, y: 4)
    }
    
    // MARK: - Avatar View
    
    private var avatarImage: some View {
        AvatarImageView(
            imageData: activeProfile?.avatarData,
            avatarURL: activeProfile?.avatarUrl,
            size: 60,
            placeholderSystemName: "person.circle.fill",
            placeholderTint: .blue.opacity(0.6),
            placeholderBackground: Color.blue.opacity(0.08)
        )
    }

    private var profileHeader: some View {
        Button {
            showingEditProfile = true
        } label: {
            HStack(spacing: 16) {
                // Avatar
                ZStack {
                    avatarImage
                    
                    // Rating badge
                    VStack {
                        Spacer()
                        HStack {
                            Spacer()
                            ratingBadge
                        }
                    }
                }
                .frame(width: 60, height: 60)
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(activeProfile?.name ?? String(localized: "home.profile.unbound"))
                            .font(.system(size: 18, weight: .bold))
                            .foregroundStyle(.primary)
                        
                        // Server badge
                        if let profile = activeProfile, let server = GameServer(rawValue: profile.server) {
                            Text(server.displayName)
                                .font(.system(size: 9, weight: .bold))
                                .foregroundStyle(.white)
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(serverColor(server), in: RoundedRectangle(cornerRadius: 4))
                        }
                    }
                    
                    if let plate = activeProfile?.plate, !plate.isEmpty {
                        Text(plate)
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    } else {
                        Text("home.profile.editHint")
                            .font(.system(size: 12))
                            .foregroundStyle(.secondary)
                    }
                }
                
                Spacer()
                
                Image(systemName: "chevron.right")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(.secondary.opacity(0.3))
            }
            .padding(16)
            .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 20))
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(Color.primary.opacity(0.05), lineWidth: 1)
            )
            .shadow(color: .black.opacity(0.02), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }
    
    private func serverColor(_ server: GameServer) -> Color {
        switch server {
        case .jp:   return .red
        case .intl: return .blue
        case .cn:   return .orange
        }
    }
    
    private func functionCard(icon: String, title: LocalizedStringKey, subtitle: LocalizedStringKey, gradient: [Color]) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Image(systemName: icon)
                .font(.system(size: 28))
                .foregroundStyle(
                    LinearGradient(colors: gradient, startPoint: .topLeading, endPoint: .bottomTrailing)
                )
                .frame(width: 32, height: 32, alignment: .leading)
            
            VStack(alignment: .leading, spacing: 4) {
                Text(title)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(.primary)
                
                Text(subtitle)
                    .font(.system(size: 12))
                    .foregroundStyle(.secondary)
                    .lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color.primary.opacity(0.05), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.02), radius: 8, x: 0, y: 4)
    }
}
