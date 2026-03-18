import SwiftUI
import SwiftData

struct PlateProgressView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.accessibilityDifferentiateWithoutColor) private var differentiateWithoutColor
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
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
    
    private var totalSheetCount: Int {
        cachedSections.reduce(0) { $0 + $1.sheets.count }
    }
    
    private var achievedSheetCount: Int {
        cachedSections.reduce(0) { partialResult, section in
            partialResult + section.sheets.filter { achievedCache[Self.sheetKey($0)] == true }.count
        }
    }
    
    private var progressValue: Double {
        guard totalSheetCount > 0 else { return 0 }
        return Double(achievedSheetCount) / Double(totalSheetCount)
    }
    
    private var remainingSheetCount: Int {
        max(totalSheetCount - achievedSheetCount, 0)
    }
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                headerView
                
                if selectedGroup == nil {
                    ProgressView()
                        .controlSize(.large)
                        .frame(maxWidth: .infinity, minHeight: 240)
                } else if cachedSections.isEmpty {
                    ContentUnavailableView("plate.unavailable.title", systemImage: "music.note.list", description: Text("plate.unavailable.description"))
                        .frame(maxWidth: .infinity, minHeight: 280)
                } else {
                    LazyVStack(spacing: 24) {
                        ForEach(cachedSections, id: \.level) { section in
                            levelSection(section)
                        }
                    }
                }
            }
            .padding(.horizontal, 16)
            .padding(.top, 16)
            .padding(.bottom, 40)
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("plate.title")
        .navigationBarTitleDisplayMode(.large)
        .overlay {
            if isComputing, hasAppeared, !cachedSections.isEmpty {
                loadingOverlay
                    .transition(.opacity)
            }
        }
        .animation(reduceMotion ? .easeOut(duration: 0.15) : .snappy(duration: 0.22), value: isComputing)
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
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(alignment: .top, spacing: 12) {
                    VStack(alignment: .leading, spacing: 6) {
                        Text(progressTitle)
                            .font(.title3.bold())
                        Text(summarySubtitle)
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    
                    Spacer(minLength: 12)
                    
                    Text(progressText)
                        .font(.headline.monospacedDigit().bold())
                        .foregroundStyle(Color(hex: selectedPlate.color))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(hex: selectedPlate.color).opacity(0.12), in: Capsule())
                }
                
                ProgressView(value: progressValue)
                    .tint(Color(hex: selectedPlate.color))
                
                HStack(spacing: 12) {
                    summaryMetric(title: "已完成", value: achievedSheetCount, tint: Color(hex: selectedPlate.color))
                    summaryMetric(title: "剩余", value: remainingSheetCount, tint: .secondary)
                    summaryMetric(title: "总谱面", value: totalSheetCount, tint: .secondary)
                }
            }
            .padding(16)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 20))
            
            HStack(spacing: 10) {
                menuButton(
                    title: "plate.menu.version",
                    selection: displayName(for: selectedGroup),
                    systemImage: "square.stack.3d.up.fill",
                    options: groups.map { displayName(for: $0) }
                ) { displayName in
                    if let found = groups.first(where: { self.displayName(for: $0) == displayName }) {
                        selectedGroup = found
                    }
                }
                
                menuButton(
                    title: "plate.menu.difficulty",
                    selection: selectedDifficulty.uppercased(),
                    systemImage: "dial.medium.fill",
                    options: difficulties.map { $0.uppercased() }
                ) { name in
                    let diff = name.lowercased()
                    guard diff != "remaster" || selectedGroup?.name == "舞代" else { return }
                    selectedDifficulty = diff
                }
                
                menuButton(
                    title: "plate.menu.plate",
                    selection: selectedPlate.rawValue,
                    systemImage: "sparkles.rectangle.stack.fill",
                    options: PlateType.allCases.map { $0.rawValue }
                ) { name in
                    guard let plate = PlateType.allCases.first(where: { $0.rawValue == name }) else { return }
                    guard plate != .sho || selectedGroup?.hasSho == true else { return }
                    selectedPlate = plate
                }
            }
        }
    }
    
    private func displayName(for group: VersionPlateGroup?) -> String {
        guard let group = group else { return "..." }
        return (group.name == group.platePrefix || group.name == "舞代") ? group.name : group.platePrefix
    }
    
    private func menuButton(
        title: LocalizedStringKey,
        selection: String,
        systemImage: String,
        options: [String],
        onSelect: @escaping (String) -> Void
    ) -> some View {
        Menu {
            ForEach(options, id: \.self) { option in
                Button(option) { onSelect(option) }
                    .disabled(isOptionDisabled(option))
            }
        } label: {
            VStack(alignment: .leading, spacing: 8) {
                Label(title, systemImage: systemImage)
                    .font(.caption.bold())
                    .foregroundStyle(.secondary)
                    .labelStyle(.titleAndIcon)
                
                HStack(spacing: 6) {
                    Text(selection)
                        .font(.subheadline.bold())
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.down")
                        .font(.caption.bold())
                        .foregroundStyle(.tertiary)
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 12)
            .frame(maxWidth: .infinity, minHeight: 54, alignment: .leading)
            .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
        }
        .accessibilityLabel(Text(selection))
    }
    
    private func isOptionDisabled(_ option: String) -> Bool {
        (option == PlateType.sho.rawValue && selectedGroup?.hasSho == false) ||
        (option == "REMASTER" && selectedGroup?.name != "舞代")
    }
    
    // MARK: - Level Section
    
    private func levelSection(_ section: (level: String, sheets: [Sheet])) -> some View {
        let completedCount = section.sheets.filter { achievedCache[Self.sheetKey($0)] == true }.count
        
        let columns = Array(repeating: GridItem(.flexible(), spacing: 12), count: 5)
        
        return VStack(alignment: .leading, spacing: 10) {
            Text("Lv. \(section.level)")
                .font(.headline.bold())
            
            HStack(spacing: 10) {
                Text("\(completedCount) / \(section.sheets.count) 已完成")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
                
                ProgressView(value: Double(completedCount), total: Double(max(section.sheets.count, 1)))
                    .tint(Color(hex: selectedPlate.color))
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            
            LazyVGrid(columns: columns, alignment: .leading, spacing: 12) {
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
        }
    }
    
    private func jacketItem(_ sheet: Sheet) -> some View {
        let key = Self.sheetKey(sheet)
        let isAchieved = achievedCache[key] ?? false
        let color = Color(hex: selectedPlate.color)
        
        return GeometryReader { geometry in
            SongJacketView(
                imageName: sheet.song?.imageName ?? "",
                size: geometry.size.width,
                cornerRadius: 12
            )
            .saturation(isAchieved ? 1 : 0.08)
            .brightness(isAchieved ? -0.08 : 0)
            .overlay(alignment: .bottom) {
                if isAchieved {
                    LinearGradient(
                        colors: [Color.clear, color.opacity(0.75)],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                    .frame(height: 22)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                }
            }
            .overlay(alignment: .center) {
                if !isAchieved && differentiateWithoutColor {
                    Image(systemName: "circle.dashed")
                        .font(.title3)
                        .foregroundStyle(.white.opacity(0.85))
                        .shadow(radius: 4)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                achievementMarker(sheet: sheet)
                    .padding(4)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 12)
                    .strokeBorder(isAchieved ? color.opacity(0.7) : Color.primary.opacity(0.08), lineWidth: isAchieved ? 2 : 1)
            }
        }
        .aspectRatio(1, contentMode: .fit)
        .contentShape(RoundedRectangle(cornerRadius: 12))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(jacketAccessibilityLabel(for: sheet, achieved: isAchieved))
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
            .font(.system(size: 8, weight: .bold, design: .rounded))
            .foregroundStyle(.white)
            .padding(.horizontal, 4)
            .padding(.vertical, 2.5)
            .background(color.opacity(0.95), in: Capsule())
            .shadow(color: .black.opacity(0.25), radius: 2, y: 1)
    }
    
    private var loadingOverlay: some View {
        ZStack {
            Color.black.opacity(0.04)
                .ignoresSafeArea()
            
            ProgressView("更新中…")
                .padding(.horizontal, 18)
                .padding(.vertical, 14)
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
        }
        .allowsHitTesting(false)
    }
    
    private var progressText: String {
        guard totalSheetCount > 0 else { return "0%" }
        return "\(Int(progressValue * 100))%"
    }
    
    private var progressTitle: String {
        guard let group = selectedGroup else { return String(localized: "plate.title") }
        
        if group.name == "舞代" || group.name != group.platePrefix {
            return "\(group.platePrefix)\(selectedPlate.shortName)进度"
        } else {
            return "\(group.name) \(selectedPlate.rawValue)进度"
        }
    }
    
    private var summarySubtitle: String {
        let groupName = displayName(for: selectedGroup)
        let difficultyName = selectedDifficulty.uppercased()
        return "\(groupName) · \(difficultyName) · \(selectedPlate.rawValue)"
    }
    
    private func summaryMetric(title: String, value: Int, tint: Color) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(value.formatted())
                .font(.headline.monospacedDigit().bold())
                .foregroundStyle(tint)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
    
    private func jacketAccessibilityLabel(for sheet: Sheet, achieved: Bool) -> String {
        let title = sheet.song?.title ?? "Unknown Song"
        let state = achieved ? "已完成" : "未完成"
        return "\(title)，\(sheet.difficulty.uppercased())，\(state)"
    }
}
