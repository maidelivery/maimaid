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
                        ContentUnavailableView("plate.unavailable.title", systemImage: "music.note.list", description: Text("plate.unavailable.description"))
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
        .navigationTitle("plate.title")
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
            menuButton(title: "plate.menu.version", selection: selectedGroup?.name ?? "...", options: groups.map { $0.name }) { name in
                if let found = groups.first(where: { $0.name == name }) {
                    selectedGroup = found
                }
            }
            
            // Difficulty Menu
            menuButton(title: "plate.menu.difficulty", selection: selectedDifficulty.uppercased(), options: difficulties.map { $0.uppercased() }) { name in
                let diff = name.lowercased()
                if diff == "remaster" && selectedGroup?.name != "舞代" {
                    return 
                }
                selectedDifficulty = diff
            }
            
            // Plate Menu
            menuButton(title: "plate.menu.plate", selection: selectedPlate.rawValue, options: PlateType.allCases.map { $0.rawValue }) { name in
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
    
    private func menuButton(title: LocalizedStringKey, selection: String, options: [String], onSelect: @escaping (String) -> Void) -> some View {
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
            Text("plate.section.count \(section.level) \(section.sheets.count)")
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
                    badgeLabel(ThemeUtils.normalizeFC(fc), color: ThemeUtils.fcColor(fc))
                }
            case .sho:
                if let score = score {
                    let rank = RatingUtils.calculateRank(achievement: score.rate)
                    if rank.contains("SSS") {
                        badgeLabel(rank, color: RatingUtils.colorForRank(rank))
                    }
                }
            case .shin:
                if let fc = score?.fc, fc.lowercased().contains("ap") {
                    badgeLabel(ThemeUtils.normalizeFC(fc), color: ThemeUtils.fcColor(fc))
                }
            case .maimai:
                if let fs = score?.fs, fs.lowercased().contains("fsd") {
                    badgeLabel(ThemeUtils.normalizeFS(fs), color: ThemeUtils.fsColor(fs))
                }
            }
        }
    }
    
    private func badgeLabel(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .black, design: .rounded))
            .italic()
            .foregroundColor(.white)
            .shadow(color: .black.opacity(0.8), radius: 2)
    }
}

