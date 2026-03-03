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
                        Text("bestTable.rating")
                            .font(.subheadline)
                            .foregroundColor(.secondary)
                        Text("\(b50Result.total)")
                            .font(.system(size: 34, weight: .black, design: .rounded))
                            .foregroundColor(.orange)
                            .opacity(isLoading ? 0.3 : 1.0)
                    }
                    Spacer()
                    VStack(alignment: .trailing) {
                        Text(String(localized: "bestTable.old.count \(b50Result.b35.reduce(0) { $0 + $1.rating })"))
                        Text(String(localized: "bestTable.new.count \(b50Result.b15.reduce(0) { $0 + $1.rating })"))
                    }
                    .font(.caption)
                    .foregroundColor(.secondary)
                }
                .padding(.vertical, 8)
            }

            Section("bestTable.settings.capacity") {
                HStack(spacing: 20) {
                    capacityInput(title: "bestTable.settings.old", value: Binding(
                        get: { configs.first?.b35Count ?? 35 },
                        set: { configs.first?.b35Count = max(1, $0) }
                    ))
                    
                    VStack {
                        Image(systemName: "plus")
                            .font(.caption.bold())
                            .foregroundColor(.secondary)
                    }
                    
                    capacityInput(title: "bestTable.settings.new", value: Binding(
                        get: { configs.first?.b15Count ?? 15 },
                        set: { configs.first?.b15Count = max(1, $0) }
                    ))
                    
                    Divider()
                        .frame(height: 30)
                    
                    VStack(alignment: .center, spacing: 4) {
                        Text("bestTable.settings.total").font(.caption2).foregroundColor(.secondary)
                        Text("\((configs.first?.b35Count ?? 35) + (configs.first?.b15Count ?? 15))")
                            .font(.system(.body, design: .rounded).bold())
                            .foregroundColor(.orange)
                    }
                    .frame(maxWidth: .infinity)
                }
                .padding(.vertical, 4)
            }
            
            
            
            Section(String(localized: "bestTable.section.new \(configs.first?.b15Count ?? 15)")) {
                if isLoading {
                    ProgressView().padding()
                } else if b50Result.b15.isEmpty {
                    Text("bestTable.empty")
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
            
            Section(String(localized: "bestTable.section.old \(configs.first?.b35Count ?? 35)")) {
                if isLoading {
                    ProgressView().padding()
                } else if b50Result.b35.isEmpty {
                    Text("bestTable.empty")
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
        .navigationTitle("bestTable.title")
//        .navigationBarTitleDisplayMode(.inline)
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
    
    private func capacityInput(title: LocalizedStringKey, value: Binding<Int>) -> some View {
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
        
        let input = songs.toCalculationInput()
        let b35Limit = configs.first?.b35Count ?? 35
        let b15Limit = configs.first?.b15Count ?? 15
        
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
                size: 56,
                cornerRadius: 10
            )
            
            VStack(alignment: .leading, spacing: 4) {
                // Line 1: Song Title
                MarqueeText(text: entry.songTitle, font: .system(size: 15, weight: .bold), fontWeight: .bold, color: .primary)
                    .frame(height: 20)
                // Line 2: Rank + Achievement + DX Score
                HStack(spacing: 6) {
                    let rank = RatingUtils.calculateRank(achievement: entry.achievement)
                    Text(rank)
                        .font(.system(size: 13, weight: .black, design: .rounded))
                        .foregroundColor(RatingUtils.colorForRank(rank))
                    
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
                    BadgeView(text: entry.type, background: entry.type == "DX" ? .orange : .blue)
                    BadgeView(text: ThemeUtils.diffShort(entry.diff), background: ThemeUtils.colorForDifficulty(entry.diff, entry.type))
                    
                    if let fc = entry.fc, !fc.isEmpty {
                        BadgeView(text: fc.uppercased(), background: ThemeUtils.fcColor(fc))
                    }
                    
                    if let fs = entry.fs, !fs.isEmpty {
                        BadgeView(text: fs.uppercased(), background: ThemeUtils.fsColor(fs))
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
                Text("bestTable.base \(entry.level, specifier: "%.1f")")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            .fixedSize()
        }
        .padding(.vertical, 6)
    }

}

