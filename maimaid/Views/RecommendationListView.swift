import SwiftUI
import SwiftData

struct RecommendationListView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    @State private var response: RecommendationResponse?
    @State private var isLoading = true
    
    var body: some View {
        ZStack {
            List {
                if !isLoading {
                    // Capacity Settings Section
                    if let config = configs.first {
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
                                NavigationLink(destination: SongDetailView(song: result.song)) {
                                    RecommendationRow(result: result)
                                }
                            }
                        }
                    }
                    
                    // B35 Recommendations
                    if let b35 = response?.b35, !b35.isEmpty {
                        Section("rec.section.old") {
                            ForEach(b35) { result in
                                NavigationLink(destination: SongDetailView(song: result.song)) {
                                    RecommendationRow(result: result)
                                }
                            }
                        }
                    }
                }
            }
            .listStyle(.insetGrouped)
            
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
                // Empty State centered
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
        .task(id: songs) {
            if !songs.isEmpty {
                await loadRecommendations()
            }
        }
        .task(id: configs) {
            // Re-load if limits change
            if !songs.isEmpty {
                await loadRecommendations()
            }
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
    
    private func loadRecommendations() async {
        isLoading = true
        response = await RecommendationService.shared.getRecommendations(songs: songs, configs: configs)
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
