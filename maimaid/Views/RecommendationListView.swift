import SwiftUI
import SwiftData

struct RecommendationListView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]
    @State private var response: RecommendationResponse?
    @State private var isLoading = true
    
    // Cache invalidation: track a fingerprint of the user's scores
    @State private var lastScoreFingerprint: String = ""
    @State private var lastConfigFingerprint: String = ""
    @State private var hasLoadedOnce = false
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    /// Generates a lightweight fingerprint from scores to detect changes.
    /// Uses count + sum of rates, which changes whenever scores are added/updated.
    private func currentScoreFingerprint() -> String {
        let scores = ScoreService.shared.allScores(context: modelContext)
        let count = scores.count
        // Use a hash of count + total rate to detect any score change
        let totalRate = scores.reduce(0.0) { $0 + $1.rate }
        return "\(count)_\(String(format: "%.2f", totalRate))"
    }
    
    /// Generates a fingerprint from config values that affect recommendations
    private func currentConfigFingerprint() -> String {
        let b15Rec = activeProfile?.b15RecLimit ?? configs.first?.b15RecLimit ?? 10
        let b35Rec = activeProfile?.b35RecLimit ?? configs.first?.b35RecLimit ?? 10
        let b15Count = activeProfile?.b15Count ?? configs.first?.b15Count ?? 15
        let b35Count = activeProfile?.b35Count ?? configs.first?.b35Count ?? 35
        let server = activeProfile?.server ?? "jp"
        return "\(b15Rec)_\(b35Rec)_\(b15Count)_\(b35Count)_\(server)"
    }
    
    var body: some View {
        ZStack {
            List {
                if !isLoading {
                    // Capacity Settings Section
                    if let profile = activeProfile {
                        Section("rec.settings.capacity") {
                            rowStepper(title: "rec.settings.new", value: Binding(
                                get: { profile.b15RecLimit },
                                set: { profile.b15RecLimit = $0 }
                            ), range: 1...50)
                            
                            rowStepper(title: "rec.settings.old", value: Binding(
                                get: { profile.b35RecLimit },
                                set: { profile.b35RecLimit = $0 }
                            ), range: 1...50)
                        }
                    } else if let config = configs.first {
                        Section("rec.settings.capacity") {
                            rowStepper(title: "rec.settings.new", value: Binding(
                                get: { config.b15RecLimit },
                                set: { config.b15RecLimit = $0 }
                            ), range: 1...50)
                            
                            rowStepper(title: "rec.settings.old", value: Binding(
                                get: { config.b35RecLimit },
                                set: { config.b35RecLimit = $0 }
                            ), range: 1...50)
                        }
                    }
                    
                    // B15 Recommendations
                    if let b15 = response?.b15, !b15.isEmpty {
                        Section("rec.section.new") {
                            ForEach(b15) { result in
                                NavigationLink(destination: SongDetailView(song: result.song, preferredType: result.sheet.type)) {
                                    RecommendationRow(result: result)
                                }
                            }
                        }
                    }
                    
                    // B35 Recommendations
                    if let b35 = response?.b35, !b35.isEmpty {
                        Section("rec.section.old") {
                            ForEach(b35) { result in
                                NavigationLink(destination: SongDetailView(song: result.song, preferredType: result.sheet.type)) {
                                    RecommendationRow(result: result)
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            .refreshable {
                // Pull-to-refresh always forces a reload
                await loadRecommendations(force: true)
            }
            
            // Loading Overlay
            if isLoading {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("rec.loading")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
            } else if (response?.b15.isEmpty ?? true) && (response?.b35.isEmpty ?? true) {
                ContentUnavailableView(
                    "rec.empty.title",
                    systemImage: "sparkles",
                    description: Text("rec.empty.desc")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
            }
        }
        .navigationTitle("rec.title")
        .task {
            guard !songs.isEmpty else { return }
            await loadRecommendationsIfNeeded()
        }
        .onChange(of: activeProfile?.b15RecLimit) { _, _ in
            Task { await loadRecommendationsIfNeeded() }
        }
        .onChange(of: activeProfile?.b35RecLimit) { _, _ in
            Task { await loadRecommendationsIfNeeded() }
        }
    }
    
    private func rowStepper(title: LocalizedStringKey, value: Binding<Int>, range: ClosedRange<Int>) -> some View {
        HStack {
            Text(title)
                .font(.subheadline)
            Spacer()
            Stepper("\(value.wrappedValue)", value: value, in: range)
                .labelsHidden()
            Text("\(value.wrappedValue)")
                .font(.subheadline.monospacedDigit().bold())
                .frame(width: 30)
        }
    }
    
    /// Only reloads if scores or config have actually changed
    private func loadRecommendationsIfNeeded() async {
        let scoreFingerprint = currentScoreFingerprint()
        let configFingerprint = currentConfigFingerprint()
        
        // If we have cached results and nothing has changed, skip
        if hasLoadedOnce && scoreFingerprint == lastScoreFingerprint && configFingerprint == lastConfigFingerprint {
            return
        }
        
        await loadRecommendations(force: false)
    }
    
    private func loadRecommendations(force: Bool = false) async {
        // Update fingerprints before loading
        let scoreFingerprint = currentScoreFingerprint()
        let configFingerprint = currentConfigFingerprint()
        
        // Skip if nothing changed (unless forced)
        if !force && hasLoadedOnce && scoreFingerprint == lastScoreFingerprint && configFingerprint == lastConfigFingerprint {
            return
        }
        
        isLoading = true
        response = await RecommendationService.shared.getRecommendations(
            songs: songs,
            configs: configs,
            activeProfile: activeProfile,
            modelContext: modelContext
        )
        lastScoreFingerprint = scoreFingerprint
        lastConfigFingerprint = configFingerprint
        hasLoadedOnce = true
        isLoading = false
    }
}

struct RecommendationRow: View {
    let result: RecommendationResult
    
    var body: some View {
        HStack(spacing: 14) {
            // Song Jacket
            SongJacketView(
                imageName: result.song.imageName,
                size: 56,
                cornerRadius: 10
            )
            
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
                            .foregroundColor(RatingUtils.colorForRank(rank))
                        
                        Text(String(format: "%.2f%%", rate))
                            .font(.system(size: 10, design: .monospaced))
                            .foregroundColor(.secondary)
                    } else {
                        Text("rec.status.notPlayed")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                }
                
                // Line 3: Badges
                HStack(spacing: 4) {
                    BadgeView(text: result.sheet.type.uppercased(), background: result.sheet.type.lowercased() == "dx" ? .orange : .blue)
                    BadgeView(text: ThemeUtils.diffShort(result.sheet.difficulty), background: ThemeUtils.colorForDifficulty(result.sheet.difficulty, result.sheet.type))
                }
            }
            .frame(minHeight: 56, alignment: .leading)
            
            Spacer()
            
            // Right side: Target Rank & Gain
            VStack(alignment: .trailing, spacing: 2) {
                Text("+\(result.potentialGain)")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundColor(.orange)
                
                Text("rec.afterRank \(result.targetRank)")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(.secondary)
            }
            .fixedSize()
        }
        .padding(.vertical, 4)
    }
}
