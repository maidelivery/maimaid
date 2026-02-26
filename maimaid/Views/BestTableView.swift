import SwiftUI
import SwiftData

struct BestTableView: View {
    @Query private var songs: [Song]
    @Query private var configs: [SyncConfig]
    
    @State private var b50Result: (total: Int, b35: [RatingUtils.RatingEntry], b15: [RatingUtils.RatingEntry]) = (0, [], [])
    @State private var isLoading = true
    
    var body: some View {
        List {

            Section {
                HStack {
                    VStack(alignment: .leading) {
                        Text("DX Rating")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("\(b50Result.total)")
                            .font(.system(size: 34, weight: .black, design: .rounded))
                            .foregroundColor(.orange)
                            .opacity(isLoading ? 0.3 : 1.0)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text("Old: \(b50Result.b35.reduce(0) { $0 + $1.rating })")
                        Text("New: \(b50Result.b15.reduce(0) { $0 + $1.rating })")
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
            }

            Section("容量设置") {
                HStack(spacing: 20) {
                    capacityInput(title: "旧曲 (Old)", value: Binding(
                        get: { configs.first?.b35Count ?? 35 },
                        set: { configs.first?.b35Count = max(1, $0) }
                    ))
                    
                    VStack {
                        Image(systemName: "plus")
                            .font(.caption.bold())
                            .foregroundColor(.secondary)
                    }
                    
                    capacityInput(title: "新曲 (New)", value: Binding(
                        get: { configs.first?.b15Count ?? 15 },
                        set: { configs.first?.b15Count = max(1, $0) }
                    ))
                    
                    Divider()
                        .frame(height: 30)
                    
                    VStack(alignment: .center, spacing: 4) {
                        Text("当前总计").font(.caption2).foregroundColor(.secondary)
                        Text("\((configs.first?.b35Count ?? 35) + (configs.first?.b15Count ?? 15))")
                            .font(.system(.body, design: .rounded).bold())
                            .foregroundColor(.orange)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 4)
            }
            
            
            
            Section("新曲 B\(configs.first?.b15Count ?? 15)") {
                if isLoading {
                    ProgressView().padding()
                } else if b50Result.b15.isEmpty {
                    Text("暂无数据")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(b50Result.b15) { entry in
                        if let song = songs.first(where: { $0.songId == entry.songId }) {
                            NavigationLink(destination: SongDetailView(song: song)) {
                                ratingRow(entry: entry)
                            }
                        } else {
                            ratingRow(entry: entry)
                        }
                    }
                }
            }
            
            Section("旧曲 B\(configs.first?.b35Count ?? 35)") {
                if isLoading {
                    ProgressView().padding()
                } else if b50Result.b35.isEmpty {
                    Text("暂无数据")
                        .foregroundColor(.secondary)
                } else {
                    ForEach(b50Result.b35) { entry in
                        if let song = songs.first(where: { $0.songId == entry.songId }) {
                            NavigationLink(destination: SongDetailView(song: song)) {
                                ratingRow(entry: entry)
                            }
                        } else {
                            ratingRow(entry: entry)
                        }
                    }
                }
            }
        }
        .navigationTitle("Best Table")
        .navigationBarTitleDisplayMode(.inline)
        .task(id: songs) {
            await calculateRating()
        }
        .task(id: configs.first?.b35Count) { // Re-calculate when counts change
            await calculateRating()
        }
        .task(id: configs.first?.b15Count) {
            await calculateRating()
        }
    }
    
    private func capacityInput(title: String, value: Binding<Int>) -> some View {
        VStack(alignment: .center, spacing: 6) {
            Text(title).font(.caption2).foregroundColor(.secondary)
            
            // Bridge Int to String to allow clearing the field during editing
            TextField("", text: Binding(
                get: { String(value.wrappedValue) },
                set: { newValue in
                    if let intValue = Int(newValue.filter { $0.isNumber }) {
                        value.wrappedValue = intValue
                    } else if newValue.isEmpty {
                        // Allow empty during typing, but value.wrappedValue remains last valid int
                        // or we could set it to 0 and handle it as 1 in the model/calculation.
                        // For now, we update the wrappedValue to 1 (minimum) if they leave it empty.
                    }
                }
            ))
            .keyboardType(.numberPad)
            .multilineTextAlignment(.center)
            .font(.system(.body, design: .monospaced).bold())
            .frame(width: 60)
            .padding(.vertical, 8)
            .background(Color.secondary.opacity(0.1), in: RoundedRectangle(cornerRadius: 8))
            .onSubmit {
                if value.wrappedValue < 1 { value.wrappedValue = 1 }
            }
        }
        .frame(maxWidth: .infinity)
    }
    
    private func calculateRating() async {
        isLoading = true
        
        // Prepare sendable data on MainActor
        let input = songs.map { song in
            RatingUtils.RatingCalculationInput(
                songId: song.songId,
                title: song.title,
                version: song.version,
                releaseDate: song.releaseDate,
                imageUrl: song.imageUrl,
                imageName: song.imageName,
                sheets: song.sheets.compactMap { sheet in
                    guard let score = sheet.score else { return nil }
                    return RatingUtils.SheetCalculationInput(
                        difficulty: sheet.difficulty,
                        type: sheet.type,
                        internalLevel: sheet.internalLevelValue,
                        level: sheet.levelValue,
                        rate: score.rate,
                        fc: score.fc,
                        fs: score.fs,
                        dxScore: score.dxScore
                    )
                }
            )
        }
        
        let b35Limit = configs.first?.b35Count ?? 35
        let b15Limit = configs.first?.b15Count ?? 15
        
        // Background calculation with sendable input
        let result = await Task.detached(priority: .userInitiated) {
            await RatingUtils.calculateB50(input: input, b35Count: b35Limit, b15Count: b15Limit)
        }.value
        
        self.b50Result = result
        self.isLoading = false
    }
    
    private func ratingRow(entry: RatingUtils.RatingEntry) -> some View {
        HStack(spacing: 14) {
            // Song Jacket
            SongJacketView(
                imageName: entry.imageName ?? "",
                remoteUrl: entry.imageUrl ?? "",
                size: 56,
                cornerRadius: 10
            )
            
            VStack(alignment: .leading, spacing: 4) {
                // Line 1: Song Title
                MarqueeText(text: entry.songTitle, font: .system(size: 15, weight: .bold), fontWeight: .bold, color: .primary)
                    .frame(height: 20)
                // Line 2: Rank + Achievement + DX Score
                HStack(spacing: 6) {
                    Text(RatingUtils.calculateRank(achievement: entry.achievement))
                        .font(.system(size: 13, weight: .black, design: .rounded))
                        .foregroundColor(.orange)
                    
                    Text(String(format: "%.4f%%", entry.achievement))
                        .font(.system(size: 12, design: .monospaced))
                        .foregroundColor(.secondary)
                    
                    if entry.dxScore > 0 {
                        HStack(spacing: 2) {
                            Image(systemName: "star.fill")
                                .font(.system(size: 8))
                            Text("\(entry.dxScore)")
                                .font(.system(size: 10, weight: .bold, design: .monospaced))
                        }
                        .fixedSize()
                        .foregroundColor(.yellow)
                    }
                }
                
                // Line 3: Type + Diff + FC + FS (all badges)
                HStack(spacing: 4) {
                    badgeView(entry.type, bg: entry.type == "DX" ? .orange : .blue, fg: .white)
                    badgeView(diffShort(entry.diff), bg: difficultyColor(entry.diff), fg: .white)
                    
                    if let fc = entry.fc, !fc.isEmpty {
                        badgeView(fc.uppercased(), bg: fcColor(fc), fg: .white)
                    }
                    
                    if let fs = entry.fs, !fs.isEmpty {
                        badgeView(fs.uppercased(), bg: fsColor(fs), fg: .white)
                    }
                }
            }
            .frame(minHeight: 56, alignment: .leading) // Consistent row height
            
            Spacer()
            
            // Right side: Rating + Base Level
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(entry.rating)")
                    .font(.system(size: 18, weight: .black, design: .rounded))
                    .foregroundColor(.orange)
                Text("Base \(String(format: "%.1f", entry.level))")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            .fixedSize()
        }
        .padding(.vertical, 6)
    }

    
    /// Reusable pill badge — fixedSize prevents text wrapping
    private func badgeView(_ text: String, bg: Color, fg: Color) -> some View {
        Text(text)
            .font(.system(size: 9, weight: .heavy))
            .fixedSize()
            .padding(.horizontal, 5)
            .padding(.vertical, 2)
            .background(bg)
            .foregroundColor(fg)
            .cornerRadius(4)
    }
    
    // MARK: - Helpers
    
    /// Abbreviated difficulty names to save horizontal space
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
    
    private func fcColor(_ fc: String) -> Color {
        let low = fc.lowercased()
        if low.contains("ap") { return Color(red: 1.0, green: 0.6, blue: 0.0) }   // gold
        if low.contains("fc") { return Color(red: 0.2, green: 0.75, blue: 0.2) }  // green
        return .secondary
    }
    
    private func fsColor(_ fs: String) -> Color {
        let low = fs.lowercased()
        if low.contains("fsd") { return Color(red: 0.7, green: 0.3, blue: 1.0) } // purple
        if low.contains("fs")  { return Color(red: 0.3, green: 0.5, blue: 1.0) } // blue
        return .secondary
    }
    
    private func difficultyColor(_ diff: String) -> Color {
        switch diff.uppercased() {
        case "BASIC":    return Color(red: 0.35, green: 0.75, blue: 0.3)
        case "ADVANCED": return Color(red: 1.0,  green: 0.65, blue: 0.0)
        case "EXPERT":   return Color(red: 1.0,  green: 0.25, blue: 0.25)
        case "MASTER":   return Color(red: 0.65, green: 0.2,  blue: 0.9)
        case "REMASTER": return Color(red: 0.85, green: 0.55, blue: 1.0)
        default:         return .secondary
        }
    }
}

