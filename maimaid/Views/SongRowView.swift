import SwiftUI

struct SongRowView: View {
    let song: Song
    
    private var highestSheet: Sheet? {
        let dxSheets = song.sheets.filter { $0.type.lowercased() == "dx" }
        let pool = dxSheets.isEmpty ? song.sheets.filter { $0.type.lowercased() == "std" } : dxSheets
        return pool.max(by: { difficultyOrder($0.difficulty) < difficultyOrder($1.difficulty) })
    }
    
    private var accentColor: Color {
        guard let sheet = highestSheet else { return .blue }
        return ThemeUtils.colorForDifficulty(sheet.difficulty)
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
                SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: 52, cornerRadius: 12)
                
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
                    
                    // Difficulty dots
                    HStack(spacing: 3) {
                        let prioritizedSheets: [Sheet] = {
                            let dxSheets = song.sheets.filter { $0.type.lowercased() == "dx" }
                            if !dxSheets.isEmpty {
                                return dxSheets.sorted(by: { difficultyOrder($0.difficulty) > difficultyOrder($1.difficulty) })
                            }
                            return song.sheets
                                .filter { $0.type.lowercased() == "std" }
                                .sorted(by: { difficultyOrder($0.difficulty) > difficultyOrder($1.difficulty) })
                        }()
                        
                        ForEach(prioritizedSheets) { sheet in
                            ScoreProgressDot(sheet: sheet)
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
    
    // MARK: - Progress Dot
    
    struct ScoreProgressDot: View {
        let sheet: Sheet
        
        private var color: Color {
            ThemeUtils.colorForDifficulty(sheet.difficulty)
        }
        
        var body: some View {
            ZStack {
                Circle()
                    .stroke(color.opacity(0.3), lineWidth: 1)
                    .frame(width: 8, height: 8)
                
                if let score = sheet.score, score.rate > 0 {
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
    
    private func difficultyOrder(_ difficulty: String) -> Int {
        switch difficulty.lowercased() {
        case "basic": return 0
        case "advanced": return 1
        case "expert": return 2
        case "master": return 3
        case "remaster": return 4
        default: return -1
        }
    }

