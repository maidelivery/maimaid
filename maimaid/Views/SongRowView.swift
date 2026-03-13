import SwiftUI
import SwiftData

struct SongRowView: View {
    let song: Song
    var scoreCache: [String: Score] = [:]
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]
    
    private var highestSheet: Sheet? {
        let dxSheets = song.sheets.filter { $0.type.lowercased() == "dx" }
        let pool = dxSheets.isEmpty ? song.sheets.filter { $0.type.lowercased() == "std" } : dxSheets
        return pool.max(by: { ThemeUtils.difficultyOrder($0.difficulty) < ThemeUtils.difficultyOrder($1.difficulty) })
    }
    
    private var accentColor: Color {
        guard let sheet = highestSheet else { return .blue }
        return ThemeUtils.colorForDifficulty(sheet.difficulty, sheet.type)
    }
    
    var body: some View {
        HStack(spacing: 0) {
            // Difficulty accent bar
            RoundedRectangle(cornerRadius: 2)
                .fill(accentColor)
                .frame(width: 4)
                .padding(.vertical, 8)
            
            HStack(spacing: 14) {
                // Jacket
                SongJacketView(imageName: song.imageName, size: 52, cornerRadius: 12)
                
                // Info
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 4) {
                        MarqueeText(text: song.title, font: .system(size: 15, weight: .semibold), fontWeight: .semibold, color: .primary)
                            .frame(height: 20)
                    }
                    
                    MarqueeText(text: song.artist, font: .system(size: 12), color: .secondary, speed: 30)
                        .frame(height: 16)
                }
                
                Spacer()
                
                // Version + Type badge
                VStack(alignment: .trailing, spacing: 4) {
                    if let version = song.version {
                        Text(ThemeUtils.versionAbbreviation(version))
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(
                                song.sheets.contains(where: { $0.type.lowercased() == "dx" }) ? Color.orange : Color.blue,
                                in: RoundedRectangle(cornerRadius: 4)
                            )
                    }
                    
                    // Difficulty dots - use cached scores for better performance
                    HStack(spacing: 3) {
                        let prioritizedSheets: [Sheet] = {
                            let dxSheets = song.sheets.filter { $0.type.lowercased() == "dx" }
                            if !dxSheets.isEmpty {
                                return dxSheets.sorted(by: { ThemeUtils.difficultyOrder($0.difficulty) > ThemeUtils.difficultyOrder($1.difficulty) })
                            }
                            return song.sheets
                                .filter { $0.type.lowercased() == "std" }
                                .sorted(by: { ThemeUtils.difficultyOrder($0.difficulty) > ThemeUtils.difficultyOrder($1.difficulty) })
                        }()
                        
                        ForEach(prioritizedSheets) { sheet in
                            if scoreCache.isEmpty {
                                // Fallback to direct lookup if cache not provided
                                ScoreProgressDot(sheet: sheet, context: modelContext)
                            } else {
                                ScoreProgressDotOptimized(sheet: sheet, scoreCache: scoreCache)
                            }
                        }
                    }
                }
            }
            .padding(.leading, 10)
            .padding(.trailing, 14)
        }
        .padding(.vertical, 12)
        .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 14))
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .strokeBorder(accentColor.opacity(0.12), lineWidth: 1)
        )
        .padding(.horizontal, 16)
    }
    
    // MARK: - Progress Dot (original - with context)
    
    struct ScoreProgressDot: View {
        let sheet: Sheet
        let context: ModelContext
        
        private var color: Color {
            ThemeUtils.colorForDifficulty(sheet.difficulty, sheet.type)
        }
        
        var body: some View {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.3), lineWidth: 1)
                    .frame(width: 8, height: 8)
                
                if let score = ScoreService.shared.score(for: sheet, context: context), score.rate > 0 {
                    let progress = min(1.0, score.rate - 100)
                    Circle()
                        .trim(from: 0, to: progress)
                        .stroke(color, style: StrokeStyle(lineWidth: 4, lineCap: .butt))
                        .frame(width: 4, height: 4)
                        .rotationEffect(.degrees(-90))
                }
            }
        }
    }
}

// MARK: - Optimized Progress Dot (with cache)

struct ScoreProgressDotOptimized: View {
    let sheet: Sheet
    let scoreCache: [String: Score]
    
    private var color: Color {
        ThemeUtils.colorForDifficulty(sheet.difficulty, sheet.type)
    }
    
    private var sheetId: String {
        "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
    }
    
    var body: some View {
        ZStack {
            Circle()
                .stroke(color.opacity(0.3), lineWidth: 1.2)
                .frame(width: 8, height: 8)
            
            if let score = scoreCache[sheetId], score.rate > 0 {
                let progress = min(1.0, score.rate - 100)
                Circle()
                    .trim(from: 0, to: progress)
                    .stroke(color, style: StrokeStyle(lineWidth: 4, lineCap: .butt))
                    .frame(width: 4, height: 4)
                    .rotationEffect(.degrees(-90))
            }
        }
    }
}
