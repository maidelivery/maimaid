import SwiftUI
import SwiftData

struct PlateProgressView: View {
    @Query private var songs: [Song]
    
    // Filters
    @State private var selectedGroup: VersionPlateGroup?
    @State private var selectedDifficulty: String = "master"
    @State private var selectedPlate: PlateType = .sho
    
    // Data
    @State private var groups: [VersionPlateGroup] = []
    
    private let difficulties = ["basic", "advanced", "expert", "master", "remaster"]
    
    var filteredSections: [(level: String, sheets: [Sheet])] {
        guard let group = selectedGroup else { return [] }
        
        let filteredSongs = songs.filter { song in
            group.versions.contains(song.version ?? "")
        }
        
        var sheets: [Sheet] = []
        for song in filteredSongs {
            if song.category.lowercased().contains("utage") || song.category.contains("宴") { continue }
            for sheet in song.sheets {
                if sheet.type.lowercased().contains("utage") { continue }
                // Exclude deleted songs (those not in Japan region)
                if !sheet.regionJp { continue }
                if sheet.difficulty.lowercased() == selectedDifficulty.lowercased() {
                    sheets.append(sheet)
                }
            }
        }
        
        // Group by internalLevel
        let grouped = Dictionary(grouping: sheets) { sheet in
            if let val = sheet.internalLevelValue {
                return sheet.internalLevel ?? String(format: "%.1f", val)
            }
            return sheet.level
        }
        
        return grouped.map { (level: $0.key, sheets: $0.value) }
            .sorted { a, b in
                let aVal = parseLevel(a.level)
                let bVal = parseLevel(b.level)
                return aVal > bVal
            }
    }
    
    private func parseLevel(_ level: String) -> Double {
        if let val = Double(level.replacingOccurrences(of: "+", with: ".7")) {
            return val
        }
        return 0
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // Filters Header - Using old card-like style
                headerView
                
                if selectedGroup == nil {
                    ProgressView()
                        .padding(.top, 40)
                } else if filteredSections.isEmpty {
                    VStack {
                        ContentUnavailableView("无更多谱面", systemImage: "music.note.list", description: Text("选择的版本和难度下没有找到谱面"))
                    }
                    .padding(.top, 60)
                } else {
                    LazyVStack(spacing: 24) {
                        ForEach(filteredSections, id: \.level) { section in
                            levelSection(section)
                        }
                    }
                    .padding(.top, 8)
                    .padding(.bottom, 40)
                }
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("牌子进度")
        .navigationBarTitleDisplayMode(.large)
        .onAppear {
            setupData()
        }
        .onChange(of: selectedGroup) { _, newValue in
            applyFallbacks(newGroup: newValue)
        }
        .onChange(of: selectedPlate) { _, newValue in
             if newValue == .sho && selectedGroup?.platePrefix == "真" {
                 selectedPlate = .kiwami
             }
        }
    }
    
    private func setupData() {
        let allGroups = PlateService.shared.getVersionGroups()
        self.groups = allGroups
        if selectedGroup == nil, let first = allGroups.first {
            selectedGroup = first
        }
    }
    
    private func applyFallbacks(newGroup: VersionPlateGroup?) {
        guard let group = newGroup else { return }
        
        // 1. Remaster only for 舞代
        if group.name != "舞代" && selectedDifficulty == "remaster" {
            selectedDifficulty = "master"
        }
        
        // 2. Sho (将) not for maimai (真)
        if group.platePrefix == "真" && selectedPlate == .sho {
            selectedPlate = .kiwami
        }
    }
    
    // MARK: - Header
    
    private var headerView: some View {
        HStack(spacing: 10) {
            // Version Menu
            menuButton(title: "版本", selection: selectedGroup?.name ?? "...", options: groups.map { $0.name }) { name in
                if let found = groups.first(where: { $0.name == name }) {
                    selectedGroup = found
                }
            }
            
            // Difficulty Menu
            menuButton(title: "难度", selection: selectedDifficulty.uppercased(), options: difficulties.map { $0.uppercased() }) { name in
                let diff = name.lowercased()
                if diff == "remaster" && selectedGroup?.name != "舞代" {
                    return 
                }
                selectedDifficulty = diff
            }
            
            // Plate Menu
            menuButton(title: "牌子", selection: selectedPlate.rawValue, options: PlateType.allCases.map { $0.rawValue }) { name in
                if let plate = PlateType.allCases.first(where: { $0.rawValue == name }) {
                    if plate == .sho && selectedGroup?.platePrefix == "真" {
                        return
                    }
                    selectedPlate = plate
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
    }
    
    private func menuButton(title: String, selection: String, options: [String], onSelect: @escaping (String) -> Void) -> some View {
        Menu {
            ForEach(options, id: \.self) { option in
                Button(option) {
                    onSelect(option)
                }
            }
        } label: {
            VStack(spacing: 2) {
                Text(title)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(.secondary)
                Text(selection)
                    .font(.system(size: 13, weight: .black))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(Color.blue.opacity(0.1))
            .foregroundColor(.blue)
            .cornerRadius(12)
        }
    }
    
    // MARK: - Level Section
    
    private func levelSection(_ section: (level: String, sheets: [Sheet])) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Lv.\(section.level) (\(section.sheets.count)首)")
                .font(.system(size: 13, weight: .black))
                .foregroundColor(.secondary)
                .padding(.horizontal, 24)
            
            let columns = [
                GridItem(.adaptive(minimum: 60, maximum: 70), spacing: 10)
            ]
            
            LazyVGrid(columns: columns, spacing: 10) {
                ForEach(section.sheets) { sheet in
                    if let song = sheet.song {
                        NavigationLink(destination: SongDetailView(song: song)) {
                            jacketItem(sheet)
                        }
                        .buttonStyle(.plain)
                    } else {
                        jacketItem(sheet)
                    }
                }
            }
            .padding(.horizontal, 24)
        }
    }
    
    private func jacketItem(_ sheet: Sheet) -> some View {
        let isAchieved = PlateService.shared.isAchieved(plateType: selectedPlate, sheet: sheet)
        let color = Color(hex: selectedPlate.color)
        
        return ZStack {
            SongJacketView(imageName: sheet.song?.imageName ?? "", size: 66, cornerRadius: 10)
                .grayscale(isAchieved ? 0 : 1.0)
                .brightness(isAchieved ? -0.3 : 0) // Dim if completed for text visibility
                .overlay {
                    // Achievement status overlay
                    ZStack {
                        achievementMarker(sheet: sheet)
                    }
                }
                .cornerRadius(10)
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isAchieved ? color.opacity(0.6) : Color.primary.opacity(0.05), lineWidth: 1.5)
                }
        }
        .frame(width: 66, height: 66)
    }
    
    @ViewBuilder
    private func achievementMarker(sheet: Sheet) -> some View {
        let score = sheet.score
        
        Group {
            switch selectedPlate {
            case .kiwami:
                if let fc = score?.fc, !fc.isEmpty {
                    badgeView(normalizeFC(fc), color: fcColor(fc))
                }
            case .sho:
                if let score = score {
                    let rank = RatingUtils.calculateRank(achievement: score.rate)
                    if rank.contains("SSS") {
                        badgeView(rank, color: RatingUtils.colorForRank(rank))
                    }
                }
            case .shin:
                if let fc = score?.fc, fc.lowercased().contains("ap") {
                    badgeView(normalizeFC(fc), color: fcColor(fc))
                }
            case .maimai:
                if let fs = score?.fs, fs.lowercased().contains("fsd") {
                    badgeView(normalizeFS(fs), color: fsColor(fs))
                }
            }
        }
    }
    
    private func badgeView(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .black, design: .rounded))
            .italic()
            .foregroundColor(.white)
            .shadow(color: .black.opacity(0.8), radius: 2)
    }
    
    // Text normalization helpers
    private func normalizeFC(_ fc: String) -> String {
        let low = fc.lowercased()
        if low == "app" { return "AP+" }
        if low == "ap" { return "AP" }
        if low == "fcp" { return "FC+" }
        if low == "fc" { return "FC" }
        return fc.uppercased()
    }
    
    private func normalizeFS(_ fs: String) -> String {
        let low = fs.lowercased()
        if low == "fsdp" { return "FDX+" }
        if low == "fsd" { return "FDX" }
        if low == "fsp" { return "FS+" }
        if low == "fs" { return "FS" }
        return fs.uppercased()
    }
    
    // Status color helpers from BestTableView style
    private func fcColor(_ fc: String) -> Color {
        let low = fc.lowercased()
        if low.contains("ap") { return Color(red: 1.0, green: 0.6, blue: 0.0) }   // gold
        if low.contains("fc") { return Color(red: 0.2, green: 0.75, blue: 0.2) }  // green
        return .secondary
    }
    
    private func fsColor(_ fs: String) -> Color {
        let low = fs.lowercased()
        if low.contains("fsd") { return Color(red: 0.7, green: 0.3, blue: 1.0) } // purple
        if low.contains("fs") || low.contains("sync") { return Color(red: 0.3, green: 0.5, blue: 1.0) } // blue
        return .secondary
    }
}
