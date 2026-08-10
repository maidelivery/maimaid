import SwiftUI
import SwiftData

struct RecommendationListView: View {
    private enum RecommendationPage: Hashable {
        case new
        case old
    }

    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    @Query private var allScores: [Score]
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]
    @State private var response: RecommendationResponse?
    @State private var isLoading = true
    @State private var selectedPage: RecommendationPage = .new
    @State private var visibleB15Count = 10
    @State private var visibleB35Count = 10
    
    // Cache invalidation: track a fingerprint of the user's scores
    @State private var lastScoreFingerprint: String = ""
    @State private var lastConfigFingerprint: String = ""
    @State private var hasLoadedOnce = false

    private let pageSize = 10
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    /// Generates a lightweight fingerprint from scores to detect changes.
    /// Uses count + sum of rates, which changes whenever scores are added/updated.
    private func currentScoreFingerprint() async -> String {
        let profileID = activeProfile?.id
        var count = 0
        var totalRate = 0.0
        var latestUpdate: TimeInterval = 0
        for (index, score) in allScores.enumerated() where score.userProfileId == profileID {
            count += 1
            totalRate += score.rate
            latestUpdate = max(latestUpdate, score.achievementDate.timeIntervalSince1970)
            if index.isMultiple(of: 200) {
                await Task.yield()
            }
        }
        let normalizedRate = Int((totalRate * 100).rounded())
        return "\(count)_\(normalizedRate)_\(Int(latestUpdate))"
    }
    
    /// Generates a fingerprint from config values that affect recommendations
    private func currentConfigFingerprint() -> String {
        let b15Count = activeProfile?.b15Count ?? configs.first?.b15Count ?? 15
        let b35Count = activeProfile?.b35Count ?? configs.first?.b35Count ?? 35
        let server = activeProfile?.server ?? "jp"
        return "\(b15Count)_\(b35Count)_\(server)"
    }

    private var b15Recommendations: [RecommendationResult] {
        response?.b15 ?? []
    }

    private var b35Recommendations: [RecommendationResult] {
        response?.b35 ?? []
    }

    private var selectedPageTitle: LocalizedStringKey {
        switch selectedPage {
        case .new:
            "rec.section.new"
        case .old:
            "rec.section.old"
        }
    }
    
    var body: some View {
        ZStack {
            switch selectedPage {
            case .new:
                RecommendationPageView(
                    results: b15Recommendations,
                    visibleCount: visibleB15Count,
                    onLoadMore: loadMoreB15
                ) {
                    await loadRecommendations(force: true)
                }
            case .old:
                RecommendationPageView(
                    results: b35Recommendations,
                    visibleCount: visibleB35Count,
                    onLoadMore: loadMoreB35
                ) {
                    await loadRecommendations(force: true)
                }
            }

            if isLoading {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("rec.loading")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
            }
        }
        .navigationTitle("rec.title")
        .background(Color(.systemGroupedBackground))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("rec.title", selection: $selectedPage) {
                        Text("rec.section.new").tag(RecommendationPage.new)
                        Text("rec.section.old").tag(RecommendationPage.old)
                    }
                } label: {
                    Label(selectedPageTitle, systemImage: "rectangle.2.swap")
                        .labelStyle(.titleAndIcon)
                }
            }
        }
        .task {
            guard !songs.isEmpty else { return }
            await loadRecommendationsIfNeeded()
        }
        .task(id: currentConfigFingerprint()) {
            guard hasLoadedOnce else { return }
            do {
                try await Task.sleep(for: .milliseconds(500))
            } catch {
                return
            }
            await loadRecommendationsIfNeeded()
        }
        .onReceive(NotificationCenter.default.publisher(for: .maimaiScoresDidChange)) { notification in
            if let changedProfileID = notification.object as? UUID,
               changedProfileID != activeProfile?.id {
                return
            }
            Task { await loadRecommendations(force: true) }
        }
    }
    
    private func loadMoreB15() {
        visibleB15Count = min(visibleB15Count + pageSize, b15Recommendations.count)
    }

    private func loadMoreB35() {
        visibleB35Count = min(visibleB35Count + pageSize, b35Recommendations.count)
    }
    
    /// Only reloads if scores or config have actually changed
    private func loadRecommendationsIfNeeded() async {
        let scoreFingerprint = await currentScoreFingerprint()
        let configFingerprint = currentConfigFingerprint()
        
        // If we have cached results and nothing has changed, skip
        if hasLoadedOnce && scoreFingerprint == lastScoreFingerprint && configFingerprint == lastConfigFingerprint {
            return
        }
        
        await loadRecommendations(force: false)
    }
    
    private func loadRecommendations(force: Bool = false) async {
        // Update fingerprints before loading
        let scoreFingerprint = await currentScoreFingerprint()
        let configFingerprint = currentConfigFingerprint()
        
        // Skip if nothing changed (unless forced)
        if !force && hasLoadedOnce && scoreFingerprint == lastScoreFingerprint && configFingerprint == lastConfigFingerprint {
            return
        }
        
        isLoading = true
        let newResponse = await RecommendationService.shared.getRecommendations(
            songs: songs,
            configs: configs,
            activeProfile: activeProfile,
            modelContext: modelContext
        )
        guard !Task.isCancelled else {
            isLoading = false
            return
        }
        response = newResponse
        visibleB15Count = min(pageSize, newResponse.b15.count)
        visibleB35Count = min(pageSize, newResponse.b35.count)
        lastScoreFingerprint = scoreFingerprint
        lastConfigFingerprint = configFingerprint
        hasLoadedOnce = true
        isLoading = false
    }
}

struct RecommendationRow: View {
    let result: RecommendationResult
    @Environment(\.colorScheme) private var colorScheme
    
    var body: some View {
        HStack(spacing: 14) {
            HStack(spacing: 10) {
                RoundedRectangle(cornerRadius: 2)
                    .fill(ThemeUtils.colorForDifficulty(result.sheet.difficulty, result.sheet.type, colorScheme))
                    .frame(width: 4)
                    .padding(.vertical, 8)

                SongJacketView(
                    imageName: result.song.imageName,
                    size: 56,
                    cornerRadius: 10
                )
            }
            
            VStack(alignment: .leading, spacing: 4) {
                // Line 1: Song Title
                MarqueeText(text: result.song.title, font: .system(size: 15, weight: .bold), fontWeight: .bold, color: .primary)
                    .frame(height: 18)
                
                // Line 2: Current Status
                HStack(spacing: 6) {
                    if let rate = result.currentRate {
                        let rank = RatingUtils.calculateRank(achievement: rate)
                        Text(rank)
                            .font(.system(size: 11, weight: .black, design: .rounded))
                            .foregroundStyle(RatingUtils.colorForRank(rank))
                        
                        Text("\(rate, format: .number.precision(.fractionLength(4)))%")
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundStyle(.secondary)
                    } else {
                        Text("rec.status.notPlayed")
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }
                
                // Line 3: Badges
                HStack(spacing: 4) {
                    BadgeView(text: result.sheet.type.uppercased(), background: result.sheet.type.lowercased() == "dx" ? .orange : .blue)
                }
            }
            .frame(minHeight: 56, alignment: .leading)
            
            Spacer()
            
            // Right side: Target Rank & Gain
            VStack(alignment: .trailing, spacing: 2) {
                Text("+\(result.potentialGain)")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundStyle(.orange)
                
                Text("rec.afterRank \(result.targetRank)")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundStyle(.secondary)
            }
            .fixedSize()
        }
        .padding(.vertical, 4)
    }
}
