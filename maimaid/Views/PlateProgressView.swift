import SwiftUI
import SwiftData

struct PlateProgressView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    
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
    @State private var recomputeTask: Task<Void, Never>?
    
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
            if newValue == .sho && selectedGroup?.hasSho == false {
                selectedPlate = .kiwami
            } else {
                recomputeAchievements()
            }
        }
        .onDisappear {
            recomputeTask?.cancel()
        }
    }
    
    // MARK: - Setup
    
    private func setupData() {
        let allGroups = PlateService.shared.getVersionGroups()
        self.groups = allGroups
        if selectedGroup == nil, let first = allGroups.first {
            selectedGroup = first
        }
    }
    
    // MARK: - Recomputation
    
    private static func sheetKey(_ sheet: Sheet) -> String {
        "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
    }
    
    /// Debounced recompute: yield to let UI settle before heavy work
    private func scheduleRecompute() {
        isComputing = true
        // Clear sections immediately so we show the spinner
        cachedSections = []
        
        // Cancel previous task and create new one with debounce
        recomputeTask?.cancel()
        recomputeTask = Task { @MainActor in
            // Yield one frame to allow SwiftUI to render the spinner
            await Task.yield()
            guard !Task.isCancelled else { return }
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
            if isUtageCategory(song.category) { continue }
            for sheet in song.sheets {
                if isUtageType(sheet.type) { continue }
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
        
        // Use scoreMap() for better performance
        scoreCache = ScoreService.shared.scoreMap(context: modelContext)
        
        var newAchievedCache: [String: Bool] = [:]
        newAchievedCache.reserveCapacity(allSheets.count)
        
        for sheet in allSheets {
            let key = Self.sheetKey(sheet)
            newAchievedCache[key] = selectedPlate.isAchieved(score: scoreCache[key])
        }
        achievedCache = newAchievedCache
    }
    
    // MARK: - Helpers
    
    private func isUtageCategory(_ category: String) -> Bool {
        category.lowercased().contains("utage") || category.contains("宴")
    }
    
    private func isUtageType(_ type: String) -> Bool {
        type.lowercased().contains("utage")
    }
    
    private func parseLevel(_ level: String) -> Double {
        Double(level.replacingOccurrences(of: "+", with: ".7")) ?? 0
    }
    
    private func applyFallbacks(newGroup: VersionPlateGroup?) {
        guard let group = newGroup else { return }
        
        // 只有舞代支持 REMASTER，其他版本自动退到 MASTER
        if group.name != "舞代" && selectedDifficulty == "remaster" {
            selectedDifficulty = "master"
        }
        
        // 如果选择的版本组不支持将牌，自动切换到极牌
        if !group.hasSho && selectedPlate == .sho {
            selectedPlate = .kiwami
        }
    }
    
    // MARK: - Header
    
    private var headerView: some View {
        HStack(spacing: 10) {
            menuButton(
                title: "plate.menu.version",
                selection: displayName(for: selectedGroup),
                options: groups.map { displayName(for: $0) }
            ) { displayName in
                if let found = groups.first(where: { self.displayName(for: $0) == displayName }) {
                    selectedGroup = found
                }
            }
            
            menuButton(
                title: "plate.menu.difficulty",
                selection: selectedDifficulty.uppercased(),
                options: difficulties.map { $0.uppercased() }
            ) { name in
                let diff = name.lowercased()
                // 只有舞代允许选择 REMASTER
                guard diff != "remaster" || selectedGroup?.name == "舞代" else { return }
                selectedDifficulty = diff
            }
            
            menuButton(
                title: "plate.menu.plate",
                selection: selectedPlate.rawValue,
                options: PlateType.allCases.map { $0.rawValue }
            ) { name in
                guard let plate = PlateType.allCases.first(where: { $0.rawValue == name }) else { return }
                guard plate != .sho || selectedGroup?.hasSho == true else { return }
                selectedPlate = plate
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 16)
    }
    
    private func displayName(for group: VersionPlateGroup?) -> String {
        guard let group = group else { return "..." }
        return (group.name == group.platePrefix || group.name == "舞代") ? group.name : group.platePrefix
    }
    
    private func menuButton(
        title: LocalizedStringKey,
        selection: String,
        options: [String],
        onSelect: @escaping (String) -> Void
    ) -> some View {
        Menu {
            ForEach(options, id: \.self) { option in
                Button(option) { onSelect(option) }
                    .disabled(isOptionDisabled(option))
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
    
    private func isOptionDisabled(_ option: String) -> Bool {
        (option == PlateType.sho.rawValue && selectedGroup?.hasSho == false) ||
        (option == "REMASTER" && selectedGroup?.name != "舞代")
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
        let key = Self.sheetKey(sheet)
        let isAchieved = achievedCache[key] ?? false
        let color = Color(hex: selectedPlate.color)
        
        return ZStack {
            SongJacketView(imageName: sheet.song?.imageName ?? "", size: 66, cornerRadius: 10)
                .grayscale(isAchieved ? 0 : 1.0)
                .brightness(isAchieved ? -0.3 : 0)
                .overlay {
                    achievementMarker(sheet: sheet)
                }
                .cornerRadius(10)
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(isAchieved ? color.opacity(0.6) : Color.primary.opacity(0.05), lineWidth: 1.5)
                }
        }
        .frame(width: 66, height: 66)
        .drawingGroup()
    }
    
    @ViewBuilder
    private func achievementMarker(sheet: Sheet) -> some View {
        let key = Self.sheetKey(sheet)
        let score = scoreCache[key]
        
        switch selectedPlate {
        case .kiwami:
            if let fc = score?.fc, !fc.isEmpty {
                badgeLabel(ThemeUtils.normalizeFC(fc), color: ThemeUtils.fcColor(fc))
            }
        case .sho:
            if let score {
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
    
    private func badgeLabel(_ text: String, color: Color) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .black, design: .rounded))
            .italic()
            .foregroundColor(.white)
            .shadow(color: .black.opacity(0.8), radius: 2)
    }
}
