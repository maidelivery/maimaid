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
                    Section {
                        VStack(alignment: .leading, spacing: 12) {
                            
                            if let config = configs.first {
                                rowStepper(title: "新曲建议数量", value: Binding(
                                    get: { config.b15RecLimit },
                                    set: { config.b15RecLimit = $0 }
                                ), range: 1...50)
                                
                                Divider()
                                
                                rowStepper(title: "旧曲建议数量", value: Binding(
                                    get: { config.b35RecLimit },
                                    set: { config.b35RecLimit = $0 }
                                ), range: 1...50)
                            }
                        }
                        .padding(.vertical, 8)
                    }
                    .navigationTitle("容量设置")
                    
                    // B15 Recommendations
                    if let b15 = response?.b15, !b15.isEmpty {
                        Section("新曲推荐") {
                            ForEach(b15) { result in
                                NavigationLink(destination: SongDetailView(song: result.song)) {
                                    RecommendationRow(result: result)
                                }
                            }
                        }
                    }
                    
                    // B35 Recommendations
                    if let b35 = response?.b35, !b35.isEmpty {
                        Section("旧曲推荐") {
                            ForEach(b35) { result in
                                NavigationLink(destination: SongDetailView(song: result.song)) {
                                    RecommendationRow(result: result)
                                }
                            }
                        }
                        Text("拟合定数通过大量玩家成绩分析得出，仅供参考。由于个人差，推荐结果可能并不完全适合你。对于新曲，将结合你的 B15 平均能力与潜力点数进行综合推荐。")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .listRowBackground(Color.clear)
                    }
                }
            }
            .listStyle(.insetGrouped)
            
            // Loading Overlay
            if isLoading {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("正在分析拟合数据...")
                        .font(.subheadline)
                        .foregroundColor(.secondary)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
            } else if (response?.b15.isEmpty ?? true) && (response?.b35.isEmpty ?? true) {
                // Empty State centered
                ContentUnavailableView(
                    "暂无推荐",
                    systemImage: "sparkles",
                    description: Text("你的 B50 数据不足，或当前没有明显的可吃分歌曲。")
                )
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Color(.systemGroupedBackground))
            }
        }
        .navigationTitle("吃分推荐")
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
    
    private func rowStepper(title: String, value: Binding<Int>, range: ClosedRange<Int>) -> some View {
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
                        Text("未游玩")
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                }
                
                // Line 3: Badges
                HStack(spacing: 4) {
                    badgeView(result.sheet.type.uppercased(), bg: result.sheet.type.lowercased() == "dx" ? .orange : .blue, fg: .white)
                    badgeView(diffShort(result.sheet.difficulty), bg: ThemeUtils.colorForDifficulty(result.sheet.difficulty, result.sheet.type), fg: .white)
                }
            }
            .frame(minHeight: 56, alignment: .leading)
            
            Spacer()
            
            // Right side: Potential Gain
            VStack(alignment: .trailing, spacing: 2) {
                Text("+\(result.potentialGain)")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundColor(.orange)
                
                Text("\(result.potentialRating)")
                    .font(.system(size: 10, weight: .bold, design: .monospaced))
                    .foregroundColor(.secondary)
            }
            .fixedSize()
        }
        .padding(.vertical, 4)
    }
    
    private func badgeView(_ text: String, bg: Color, fg: Color) -> some View {
        Text(text.uppercased())
            .font(.system(size: 9, weight: .heavy))
            .fixedSize()
            .padding(.horizontal, 4)
            .padding(.vertical, 2)
            .background(bg)
            .foregroundColor(fg)
            .cornerRadius(4)
    }
    
    private func diffShort(_ diff: String) -> String {
        switch diff.uppercased() {
        case "BASIC":    return "BAS"
        case "ADVANCED": return "ADV"
        case "EXPERT":   return "EXP"
        case "MASTER":   return "MAS"
        case "REMASTER": return "ReM"
        default:         return diff
        }
    }
}
