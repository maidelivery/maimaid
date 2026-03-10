import SwiftUI
import SwiftData

struct PlateProgressView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]
    
    // Filters
    @State private var selectedGroup: VersionPlateGroup?
    @State private var selectedDifficulty: String = "master"
    @State private var selectedPlate: PlateType = .sho
    
    // Data
    @State private var groups: [VersionPlateGroup] = []
    
    // Cached computation results
    @State private var cachedSections: [(level: String, sheets: [Sheet])] = []
    @State private var achievedCache: [String: Bool] = [:]
    @State private var scoreCache: [String: Score] = [:]
    @State private var isComputing = false
    @State private var hasAppeared = false
    
    private let difficulties = ["basic", "advanced", "expert", "master", "remaster"]
    
    var body: some View {
        ScrollView {
            VStack(spacing: 0) {
                // Filters Header
                headerView
                
                if selectedGroup == nil || isComputing {
                    ProgressView()
                        .padding(.top, 40)
                } else if cachedSections.isEmpty {
                    VStack {
                        ContentUnavailableView("plate.unavailable.title", systemImage: "music.note.list", description: Text("plate.unavailable.description"))
                    }
                    .padding(.top, 60)
                } else {
                    LazyVStack(spacing: 24) {
                        ForEach(cachedSections, id: \.level) { section in
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
        .task {
            guard !hasAppeared else { return }
            hasAppeared = true
            setupData()
        }
        .onChange(of: selectedGroup) { _, newValue in
            applyFallbacks(newGroup: newValue)
            scheduleRecompute()
        }
        .onChange(of: selectedDifficulty) { _, _ in
            scheduleRecompute()
        }
        .onChange(of: selectedPlate) { _, newValue in
            if newValue == .sho && selectedGroup?.platePrefix == "真" {
                selectedPlate = .kiwami
            } else {
                recomputeAchievements()
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
    
    // MARK: - Recomputation
    
    private func sheetKey(_ sheet: Sheet) -> String {
        "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
    }
    
    /// Debounced recompute: yield to let UI settle before heavy work
    private func scheduleRecompute() {
        isComputing = true
        // Clear sections immediately so we show the spinner
        // but don't do heavy work until next runloop
        Task { @MainActor in
            // Yield one frame to allow SwiftUI to render the spinner
            await Task.yield()
            recomputeSections()
        }
    }
    
    private func recomputeSections() {
        guard let group = selectedGroup else {
            cachedSections = []
            isComputing = false
            return
        }
        
        let versionSet = Set(group.versions)
        let targetDifficulty = selectedDifficulty.lowercased()
        
        var sheets: [Sheet] = []
        for song in songs {
            guard let version = song.version, versionSet.contains(version) else { continue }
            if song.category.lowercased().contains("utage") || song.category.contains("宴") { continue }
            for sheet in song.sheets {
                if sheet.type.lowercased().contains("utage") { continue }
                if !sheet.regionJp { continue }
                if sheet.difficulty.lowercased() == targetDifficulty {
                    sheets.append(sheet)
                }
            }
        }
        
        let grouped = Dictionary(grouping: sheets) { sheet in
            if let val = sheet.internalLevelValue {
                return sheet.internalLevel ?? String(format: "%.1f", val)
            }
            return sheet.level
        }
        
        cachedSections = grouped.map { (level: $0.key, sheets: $0.value) }
            .sorted { parseLevel($0.level) > parseLevel($1.level) }
        
        recomputeAchievements()
        
        isComputing = false
    }
    
    private func recomputeAchievements() {
        let allSheets = cachedSections.flatMap { $0.sheets }
        
        let allScores = ScoreService.shared.allScores(context: modelContext)
        var newScoreCache: [String: Score] = [:]
        for score in allScores {
            newScoreCache[score.sheetId] = score
        }
        scoreCache = newScoreCache
        
        var newAchievedCache: [String: Bool] = [:]
        for sheet in allSheets {
            let key = sheetKey(sheet)
            let score = newScoreCache[key]
            newAchievedCache[key] = isAchievedFromScore(plateType: selectedPlate, score: score)
        }
        achievedCache = newAchievedCache
    }
    
    private func isAchievedFromScore(plateType: PlateType, score: Score?) -> Bool {
        guard let score = score else { return false }
        switch plateType {
        case .kiwami:
            if let fc = score.fc?.lowercased(), ["fc", "fcp", "ap", "app"].contains(fc) { return true }
        case .sho:
            if score.rate >= 100.0 { return true }
        case .shin:
            if let fc = score.fc?.lowercased(), ["ap", "app"].contains(fc) { return true }
        case .maimai:
            if let fs = score.fs?.lowercased(), ["fsd", "fsdp"].contains(fs) { return true }
        }
        return false
    }
    
    private func parseLevel(_ level: String) -> Double {
        if let val = Double(level.replacingOccurrences(of: "+", with: ".7")) {
            return val
        }
        return 0
    }
    
    private func applyFallbacks(newGroup: VersionPlateGroup?) {
        guard let group = newGroup else { return }
        
        if group.name != "舞代" && selectedDifficulty == "remaster" {
            selectedDifficulty = "master"
        }
        
        if group.platePrefix == "真" && selectedPlate == .sho {
            selectedPlate = .kiwami
        }
    }
    
    // MARK: - Header
    
    private var headerView: some View {
        HStack(spacing: 10) {
            menuButton(title: "plate.menu.version", selection: displayName(for: selectedGroup), options: groups.map { displayName(for: $0) }) { displayName in
                if let found = groups.first(where: { self.displayName(for: $0) == displayName }) {
                    selectedGroup = found
                }
            }
            
            menuButton(title: "plate.menu.difficulty", selection: selectedDifficulty.uppercased(), options: difficulties.map { $0.uppercased() }) { name in
                let diff = name.lowercased()
                if diff == "remaster" && selectedGroup?.name != "舞代" {
                    return 
                }
                selectedDifficulty = diff
            }
            
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
    
    private func displayName(for group: VersionPlateGroup?) -> String {
        guard let group = group else { return "..." }
        if group.name == group.platePrefix || group.name == "舞代" {
            return group.name
        }
        return group.platePrefix
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
        let key = sheetKey(sheet)
        let isAchieved = achievedCache[key] ?? false
        let color = Color(hex: selectedPlate.color)
        
        return ZStack {
            SongJacketView(imageName: sheet.song?.imageName ?? "", size: 66, cornerRadius: 10)
                .grayscale(isAchieved ? 0 : 1.0)
                .brightness(isAchieved ? -0.3 : 0)
                .overlay {
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
        .drawingGroup() // Flatten compositing to reduce GPU overdraw during scrolling/transitions
    }
    
    @ViewBuilder
    private func achievementMarker(sheet: Sheet) -> some View {
        let key = sheetKey(sheet)
        let score = scoreCache[key]
        
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
