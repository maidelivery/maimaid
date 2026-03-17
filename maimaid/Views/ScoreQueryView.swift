import SwiftUI
import SwiftData

struct ScoreQueryView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var songs: [Song]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    // MARK: - State
    
    @State private var scoreMap: [String: Score] = [:]
    @State private var songMap: [String: Song] = [:]
    @State private var allEntries: [ScoreEntry] = []
    @State private var filteredEntries: [ScoreEntry] = []
    @State private var isLoading = true
    @State private var searchText = ""
    @State private var searchTask: Task<Void, Never>?
    
    // Display settings (persisted)
    @AppStorage("scoreQuery.displayMode") private var displayMode: DisplayMode = .grid
    @AppStorage("scoreQuery.gridColumns") private var committedColumns: Int = 5
    @AppStorage("scoreQuery.badgeMode") private var badgeMode: BadgeMode = .rank
    @AppStorage("scoreQuery.sortMode") private var sortMode: SortMode = .rating
    @AppStorage("scoreQuery.sortAscending") private var sortAscending: Bool = false
    
    // Grid zoom state
    @State private var isZooming: Bool = false
    @State private var liveColumnCount: CGFloat = 5.0
    @State private var pinchStartColumns: CGFloat = 5.0
    @State private var zoomAnchorEntryID: String? = nil
    @State private var viewportHeight: CGFloat = 0
    /// Brief cooldown after pinch ends to prevent accidental tap activation
    @State private var navigationDisabled: Bool = false
    
    private let minColumns: CGFloat = 3
    private let maxColumns: CGFloat = 9
    
    // Filters
    @State private var selectedDifficulties: Set<String> = []
    @State private var selectedRanks: Set<String> = []
    @State private var selectedFC: Set<String> = []
    @State private var selectedFS: Set<String> = []
    
    // Stats
    @State private var stats = PlayerStats()
    
    // MARK: - Types
    
    enum DisplayMode: String, CaseIterable {
        case grid, list
    }
    
    enum BadgeMode: String, CaseIterable {
        case rank, fc, fs
    }
    
    enum SortMode: String, CaseIterable {
        case rating, achievement, level
    }
    
    struct PlayerStats {
        var totalPlayed: Int = 0
        var sssPlus: Int = 0
        var sss: Int = 0
        var fcCount: Int = 0
        var apCount: Int = 0
        var fsCount: Int = 0
        var fsdCount: Int = 0
    }
    
    struct ScoreEntry: Identifiable, Sendable {
        let id: String // sheetId
        let songId: Int
        let songIdentifier: String
        let songTitle: String
        let aliases: [String]
        let searchKeywords: String?
        let imageName: String
        let difficulty: String
        let type: String
        let level: Double
        let achievement: Double
        let rank: String
        let rating: Int
        let fc: String?
        let fs: String?
        let dxScore: Int
    }
    
    // MARK: - Computed
    
    private var activeProfile: UserProfile? { activeProfiles.first }
    
    // MARK: - Grid Zoom Helpers
    
    /// Given the pinch magnification, compute the effective continuous column count.
    private func continuousColumns(for magnification: CGFloat) -> CGFloat {
        let raw = pinchStartColumns / magnification
        return min(maxColumns, max(minColumns, raw))
    }
    
    /// Compute cell size and spacing from a column count and available width.
    private func gridMetrics(intColumns: Int, in width: CGFloat) -> (cellSize: CGFloat, spacing: CGFloat) {
        let intCols = max(1, intColumns)
        let spacing: CGFloat
        switch intCols {
        case ...3: spacing = 5
        case 4: spacing = 4
        case 5: spacing = 3
        case 6: spacing = 2
        default: spacing = 1
        }
        let totalSpacing = spacing * CGFloat(intCols - 1)
        let horizontalPadding: CGFloat = spacing + 2
        let cellSize = (width - totalSpacing - horizontalPadding * 2) / CGFloat(intCols)
        return (max(1, cellSize), spacing)
    }
    
    /// The corner radius for a given column count
    private func cornerRadius(for columns: Int) -> CGFloat {
        switch columns {
        case ...3: return 10
        case 4...5: return 6
        default: return 3
        }
    }
    
    /// Estimate which entry index is near the center of the pinch gesture.
    private func estimateCenterEntryIndex(pinchY: CGFloat, gridWidth: CGFloat, columns: Int) -> Int {
        let metrics = gridMetrics(intColumns: columns, in: gridWidth)
        let cellSize = metrics.cellSize
        let spacing = metrics.spacing
        let rowHeight = cellSize + spacing
        guard rowHeight > 0 else { return 0 }
        
        let estimatedRow = Int(max(0, pinchY - 12) / rowHeight)
        let centerIndexInRow = columns / 2
        let index = estimatedRow * columns + centerIndexInRow
        return min(max(0, index), max(0, filteredEntries.count - 1))
    }
    
    private func makePinchGesture(width: CGFloat) -> some Gesture {
        MagnifyGesture(minimumScaleDelta: 0.02)
            .onChanged { value in
                if !isZooming {
                    isZooming = true
                    navigationDisabled = true
                    pinchStartColumns = CGFloat(committedColumns)
                    
                    let centerIdx = estimateCenterEntryIndex(
                        pinchY: value.startLocation.y,
                        gridWidth: width,
                        columns: committedColumns
                    )
                    if centerIdx >= 0 && centerIdx < filteredEntries.count {
                        zoomAnchorEntryID = filteredEntries[centerIdx].id
                    }
                }
                liveColumnCount = continuousColumns(for: value.magnification)
            }
            .onEnded { value in
                let finalCols = continuousColumns(for: value.magnification)
                let targetCols = max(Int(minColumns), min(Int(maxColumns), Int(finalCols.rounded())))
                let changed = targetCols != committedColumns
                let anchorID = zoomAnchorEntryID
                
                isZooming = false
                
                withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) {
                    committedColumns = targetCols
                    liveColumnCount = CGFloat(targetCols)
                }
                
                if changed {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
                
                if let anchorID = anchorID {
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) {
                        withAnimation(.easeOut(duration: 0.2)) {
                            self.zoomAnchorEntryID = anchorID
                        }
                    }
                }
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    navigationDisabled = false
                }
            }
    }
    
    private var allDifficulties: [String] {
        ["basic", "advanced", "expert", "master", "remaster"]
    }
    
    private var difficultyDisplayNames: [String: String] {
        ["basic": "Basic", "advanced": "Advanced", "expert": "Expert", "master": "Master", "remaster": "Re:Master"]
    }
    
    private var rankOptions: [String] {
        ["SSS+", "SSS", "SS+", "SS", "S+", "S", "AAA", "AA", "A", "BBB", "BB", "B", "C", "D"]
    }
    
    private var fcOptions: [String] {
        ["AP+", "AP", "FC+", "FC"]
    }
    
    private var fsOptions: [String] {
        ["FDX+", "FDX", "FS+", "FS"]
    }
    
    // MARK: - Body
    
    var body: some View {
        GeometryReader { geo in
            let width = geo.size.width
            
            ScrollViewReader { scrollProxy in
                ScrollView {
                    LazyVStack(spacing: 0) {
                        // Stats Header
                        statsHeader
                            .padding(.horizontal, 16)
                            .padding(.top, 8)
                        
                        // Controls
                        controlsSection
                            .padding(.horizontal, 16)
                            .padding(.top, 12)
                        
                        // Filter Chips
                        filterSection
                            .padding(.top, 12)
                        
                        // Content
                        if isLoading {
                            ProgressView()
                                .padding(.top, 60)
                        } else if filteredEntries.isEmpty {
                            ContentUnavailableView(
                                "scoreQuery.empty",
                                systemImage: "music.note.list",
                                description: Text("")
                            )
                            .padding(.top, 40)
                        } else {
                            contentView(in: width)
                                .padding(.top, 8)
                        }
                    }
                }
                .scrollDisabled(isZooming)
                .if(displayMode == .grid) { view in
                    view.simultaneousGesture(makePinchGesture(width: width))
                }
                .onChange(of: zoomAnchorEntryID) { _, newID in
                    if let id = newID, !isZooming {
                        scrollProxy.scrollTo(id, anchor: .center)
                    }
                }
            }
            .onAppear {
                viewportHeight = geo.size.height
            }
            .onChange(of: geo.size.height) { _, h in
                viewportHeight = h
            }
        }
        .background(Color(.systemGroupedBackground))
        .navigationTitle("scoreQuery.title")
        .navigationBarTitleDisplayMode(.large)
        .searchable(text: $searchText, prompt: "search.placeholder")
        .onAppear {
            liveColumnCount = CGFloat(committedColumns)
        }
        .onChange(of: searchText) { _, _ in
            debounceFilter()
        }
        .onChange(of: sortMode) { _, _ in applyFiltersAndSort() }
        .onChange(of: sortAscending) { _, _ in applyFiltersAndSort() }
        .onChange(of: selectedDifficulties) { _, _ in applyFiltersAndSort() }
        .onChange(of: selectedRanks) { _, _ in applyFiltersAndSort() }
        .onChange(of: selectedFC) { _, _ in applyFiltersAndSort() }
        .onChange(of: selectedFS) { _, _ in applyFiltersAndSort() }
        .task {
            await loadData()
        }
    }
    
    // MARK: - Stats Header
    
    private var statsHeader: some View {
        VStack(spacing: 12) {
            // Primary stats row
            HStack(spacing: 0) {
                statItem(value: stats.totalPlayed, label: "scoreQuery.stats.played")
                Divider().frame(height: 30)
                statItem(value: stats.sssPlus, label: "SSS+")
                Divider().frame(height: 30)
                statItem(value: stats.sss, label: "SSS")
            }
            
            Divider()
            
            // Secondary stats row
            HStack(spacing: 0) {
                statItem(value: stats.fcCount, label: "scoreQuery.stats.fc")
                Divider().frame(height: 30)
                statItem(value: stats.apCount, label: "scoreQuery.stats.ap")
                Divider().frame(height: 30)
                statItem(value: stats.fsCount, label: "scoreQuery.stats.fs")
                Divider().frame(height: 30)
                statItem(value: stats.fsdCount, label: "scoreQuery.stats.fsd")
            }
        }
        .padding(16)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 16))
    }
    
    private func statItem(value: Int, label: LocalizedStringKey) -> some View {
        VStack(spacing: 4) {
            Text("\(value)")
                .font(.system(size: 22, weight: .bold, design: .rounded))
                .foregroundColor(.primary)
                .contentTransition(.numericText())
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
    }
    
    // MARK: - Controls Section
    
    private var controlsSection: some View {
        VStack(spacing: 12) {
            // Sort + Display Toggle
            HStack(spacing: 12) {
                // Sort picker
                Menu {
                    Picker("", selection: $sortMode) {
                        Label("scoreQuery.sort.rating", systemImage: "star.fill").tag(SortMode.rating)
                        Label("scoreQuery.sort.achievement", systemImage: "percent").tag(SortMode.achievement)
                        Label("scoreQuery.sort.level", systemImage: "chart.bar.fill").tag(SortMode.level)
                    }
                    
                    Divider()
                    
                    Button {
                        sortAscending.toggle()
                    } label: {
                        Label(
                            sortAscending ? String(localized: "sort.ascending") : String(localized: "sort.descending"),
                            systemImage: sortAscending ? "arrow.up" : "arrow.down"
                        )
                    }
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.up.arrow.down")
                        Text(sortModeLabel)
                            .font(.system(size: 13, weight: .medium))
                        Image(systemName: sortAscending ? "arrow.up" : "arrow.down")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Color(.tertiarySystemGroupedBackground), in: Capsule())
                }
                
                Spacer()
                
                // Display mode toggle
                Picker("", selection: $displayMode) {
                    Image(systemName: "square.grid.3x3.fill").tag(DisplayMode.grid)
                    Image(systemName: "list.bullet").tag(DisplayMode.list)
                }
                .pickerStyle(.segmented)
                .frame(width: 100)
            }
            
            // Grid-specific controls
            if displayMode == .grid {
                HStack(spacing: 12) {
                    // Badge mode
                    Picker("", selection: $badgeMode) {
                        Text("scoreQuery.badge.rank").tag(BadgeMode.rank)
                        Text("scoreQuery.badge.fc").tag(BadgeMode.fc)
                        Text("scoreQuery.badge.fs").tag(BadgeMode.fs)
                    }
                    .pickerStyle(.segmented)
                    .frame(width: 180)
                    
                    Spacer()
                }
            }
        }
    }
    
    private var sortModeLabel: LocalizedStringKey {
        switch sortMode {
        case .rating: return "scoreQuery.sort.rating"
        case .achievement: return "scoreQuery.sort.achievement"
        case .level: return "scoreQuery.sort.level"
        }
    }
    
    // MARK: - Filter Section
    
    private var filterSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            // Difficulty chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(allDifficulties, id: \.self) { diff in
                        let displayName = difficultyDisplayNames[diff] ?? diff.capitalized
                        FilterChip(
                            title: displayName,
                            isSelected: selectedDifficulties.contains(diff),
                            color: ThemeUtils.colorForDifficulty(diff, nil)
                        ) {
                            if selectedDifficulties.contains(diff) {
                                selectedDifficulties.remove(diff)
                            } else {
                                selectedDifficulties.insert(diff)
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            
            // Rank filter chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    ForEach(rankOptions.prefix(6), id: \.self) { rank in
                        FilterChip(
                            title: rank,
                            isSelected: selectedRanks.contains(rank),
                            color: RatingUtils.colorForRank(rank)
                        ) {
                            if selectedRanks.contains(rank) {
                                selectedRanks.remove(rank)
                            } else {
                                selectedRanks.insert(rank)
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            
            // FC/FS chips
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 6) {
                    // FC chips
                    ForEach(fcOptions, id: \.self) { fc in
                        FilterChip(
                            title: fc,
                            isSelected: selectedFC.contains(fc),
                            color: ThemeUtils.fcColor(fc)
                        ) {
                            if selectedFC.contains(fc) {
                                selectedFC.remove(fc)
                            } else {
                                selectedFC.insert(fc)
                            }
                        }
                    }
                    
                    Divider().frame(height: 20)
                    
                    // FS chips
                    ForEach(fsOptions, id: \.self) { fs in
                        FilterChip(
                            title: fs,
                            isSelected: selectedFS.contains(fs),
                            color: ThemeUtils.fsColor(fs)
                        ) {
                            if selectedFS.contains(fs) {
                                selectedFS.remove(fs)
                            } else {
                                selectedFS.insert(fs)
                            }
                        }
                    }
                }
                .padding(.horizontal, 16)
            }
            
            // Active filter count
            let activeCount = selectedDifficulties.count + selectedRanks.count + selectedFC.count + selectedFS.count
            if activeCount > 0 {
                HStack {
                    Text("^[\(filteredEntries.count) \("scoreQuery.results")](inflect: true)")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                    
                    Spacer()
                    
                    Button {
                        selectedDifficulties.removeAll()
                        selectedRanks.removeAll()
                        selectedFC.removeAll()
                        selectedFS.removeAll()
                    } label: {
                        Text("filter.reset")
                            .font(.system(size: 11, weight: .medium))
                    }
                }
                .padding(.horizontal, 16)
            }
        }
    }
    
    // MARK: - Content View
    
    @ViewBuilder
    private func contentView(in width: CGFloat) -> some View {
        switch displayMode {
        case .grid:
            gridBody(in: width)
        case .list:
            listView
        }
    }
    
    private func gridBody(in width: CGFloat) -> some View {
        let cols = isZooming ? liveColumnCount : CGFloat(committedColumns)
        let intCols = max(Int(minColumns), min(Int(maxColumns), Int(cols.rounded())))
        let metrics = gridMetrics(intColumns: intCols, in: width)
        let cellSize = metrics.cellSize
        let spacing = metrics.spacing
        let horizontalPadding = spacing + 2
        let cr = cornerRadius(for: intCols)
        
        return LazyVGrid(
            columns: Array(repeating: GridItem(.fixed(cellSize), spacing: spacing), count: intCols),
            spacing: spacing
        ) {
            ForEach(filteredEntries) { entry in
                NavigationLink(destination: songDetailDestination(entry: entry)) {
                    gridCell(entry: entry, cellSize: cellSize, cornerRadius: cr, intCols: intCols)
                }
                .disabled(navigationDisabled || isZooming)
                .buttonStyle(.plain)
                .frame(width: cellSize, height: cellSize)
                .id(entry.id)
            }
        }
        .padding(.horizontal, horizontalPadding)
        .padding(.bottom, 20)
    }
    
    private func gridCell(entry: ScoreEntry, cellSize: CGFloat, cornerRadius: CGFloat, intCols: Int) -> some View {
        ZStack(alignment: .bottomTrailing) {
            SongJacketView(
                imageName: entry.imageName,
                size: cellSize,
                cornerRadius: cornerRadius
            )
            
            // Difficulty accent (top-left small bar)
            VStack {
                HStack {
                    RoundedRectangle(cornerRadius: 1)
                        .fill(ThemeUtils.colorForDifficulty(entry.difficulty, entry.type))
                        .frame(width: 3, height: 14)
                        .padding(.leading, 2)
                        .padding(.top, 2)
                    Spacer()
                }
                Spacer()
            }
            
            // Badge overlay
            badgeOverlay(entry: entry, intCols: intCols)
                .padding(2)
        }
    }
    
    @ViewBuilder
    private func badgeOverlay(entry: ScoreEntry, intCols: Int) -> some View {
        switch badgeMode {
        case .rank:
            Text(entry.rank)
                .font(.system(size: intCols > 5 ? 7 : 9, weight: .black, design: .rounded))
                .foregroundColor(.white)
                .padding(.horizontal, 3)
                .padding(.vertical, 1)
                .background(RatingUtils.colorForRank(entry.rank), in: RoundedRectangle(cornerRadius: 3))
        case .fc:
            if let fc = entry.fc, !fc.isEmpty {
                Text(ThemeUtils.normalizeFC(fc))
                    .font(.system(size: intCols > 5 ? 7 : 9, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 3)
                    .padding(.vertical, 1)
                    .background(ThemeUtils.fcColor(fc), in: RoundedRectangle(cornerRadius: 3))
            }
        case .fs:
            if let fs = entry.fs, !fs.isEmpty {
                Text(ThemeUtils.normalizeFS(fs))
                    .font(.system(size: intCols > 5 ? 7 : 9, weight: .black, design: .rounded))
                    .foregroundColor(.white)
                    .padding(.horizontal, 3)
                    .padding(.vertical, 1)
                    .background(ThemeUtils.fsColor(fs), in: RoundedRectangle(cornerRadius: 3))
            }
        }
    }
    
    private var listView: some View {
        LazyVStack(spacing: 2) {
            ForEach(filteredEntries) { entry in
                NavigationLink(destination: songDetailDestination(entry: entry)) {
                    listRow(entry: entry)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 12)
        .padding(.bottom, 20)
    }
    
    private func listRow(entry: ScoreEntry) -> some View {
        HStack(spacing: 10) {
            // Difficulty accent
            RoundedRectangle(cornerRadius: 2)
                .fill(ThemeUtils.colorForDifficulty(entry.difficulty, entry.type))
                .frame(width: 3)
                .padding(.vertical, 6)
            
            // Jacket
            SongJacketView(imageName: entry.imageName, size: 42, cornerRadius: 8)
            
            // Info
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.songTitle)
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(1)
                    .foregroundColor(.primary)
                
                HStack(spacing: 4) {
                    Text(String(format: "%.4f%%", entry.achievement))
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundColor(.secondary)
                    
                    if let fc = entry.fc, !fc.isEmpty {
                        Text(ThemeUtils.normalizeFC(fc))
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(ThemeUtils.fcColor(fc))
                    }
                    
                    if let fs = entry.fs, !fs.isEmpty {
                        Text(ThemeUtils.normalizeFS(fs))
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(ThemeUtils.fsColor(fs))
                    }
                }
            }
            
            Spacer()
            
            // Rating + Rank
            VStack(alignment: .trailing, spacing: 2) {
                Text(entry.rank)
                    .font(.system(size: 13, weight: .black, design: .rounded))
                    .foregroundColor(RatingUtils.colorForRank(entry.rank))
                
                Text("\(entry.rating)")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundColor(.secondary)
            }
        }
        .padding(.vertical, 8)
        .padding(.horizontal, 10)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 10))
    }
    
    // MARK: - Navigation
    
    @ViewBuilder
    private func songDetailDestination(entry: ScoreEntry) -> some View {
        if let song = songMap[entry.songIdentifier] {
            SongDetailView(song: song)
        } else {
            Text("scoreQuery.songNotFound")
        }
    }
    
    // MARK: - Data Loading
    
    private func loadData() async {
        let map = ScoreService.shared.scoreMap(context: modelContext)
        self.scoreMap = map
        
        var sMap: [String: Song] = [:]
        var rootEntries: [ScoreEntry] = []
        
        for song in songs {
            sMap[song.songIdentifier] = song
            
            if song.category.lowercased().contains("utage") || song.category.contains("宴") { continue }
            
            for sheet in song.sheets {
                if sheet.type.lowercased().contains("utage") { continue }
                
                let sheetId = "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
                guard let score = map[sheetId], score.rate > 0 else { continue }
                
                let rank = RatingUtils.calculateRank(achievement: score.rate)
                let level = sheet.internalLevelValue ?? sheet.levelValue ?? 0
                let rating = RatingUtils.calculateRating(internalLevel: level, achievement: score.rate)
                
                rootEntries.append(ScoreEntry(
                    id: sheetId,
                    songId: song.songId,
                    songIdentifier: song.songIdentifier,
                    songTitle: song.title,
                    aliases: song.aliases,
                    searchKeywords: song.searchKeywords,
                    imageName: song.imageName,
                    difficulty: sheet.difficulty,
                    type: sheet.type,
                    level: level,
                    achievement: score.rate,
                    rank: rank,
                    rating: rating,
                    fc: score.fc,
                    fs: score.fs,
                    dxScore: score.dxScore
                ))
            }
        }
        
        self.songMap = sMap
        self.allEntries = rootEntries
        
        computeStats(from: map)
        applyFiltersAndSort()
        isLoading = false
    }
    
    private func computeStats(from map: [String: Score]) {
        var s = PlayerStats()
        
        // Count unique songs with any score
        var songsWithScores = Set<String>()
        
        for (_, score) in map {
            // Extract songIdentifier from sheetId (format: songId_type_difficulty)
            let parts = score.sheetId.components(separatedBy: "_")
            if parts.count >= 1 {
                songsWithScores.insert(parts[0])
            }
            
            // Achievement-based stats (count per sheet)
            if score.rate >= 100.5 { s.sssPlus += 1 }
            else if score.rate >= 100.0 { s.sss += 1 }
            
            // FC stats
            if let fc = score.fc?.lowercased(), !fc.isEmpty {
                if fc.contains("app") || fc.contains("ap") {
                    s.apCount += 1
                } else if fc.contains("fc") {
                    s.fcCount += 1
                }
            }
            
            // FS stats
            if let fs = score.fs?.lowercased(), !fs.isEmpty {
                if fs.contains("fsd") {
                    s.fsdCount += 1
                } else if fs.contains("fs") {
                    s.fsCount += 1
                }
            }
        }
        
        s.totalPlayed = songsWithScores.count
        self.stats = s
    }
    
    // MARK: - Filtering & Sorting
    
    private func debounceFilter() {
        searchTask?.cancel()
        searchTask = Task {
            try? await Task.sleep(nanoseconds: 300_000_000) // 300ms debounce
            if !Task.isCancelled {
                applyFiltersAndSort()
            }
        }
    }
    
    private func applyFiltersAndSort() {
//        let searchLower = searchText.lowercased()
        let hasSearch = !searchText.isEmpty
        let diffFilter = selectedDifficulties
        let rankFilter = selectedRanks
        let fcFilter = selectedFC
        let fsFilter = selectedFS
        let currentSortMode = sortMode
        let ascending = sortAscending
        
        var entries: [ScoreEntry] = []
        
        for entry in allEntries {
            // Search filter
            if hasSearch {
                let matches = entry.songTitle.localizedCaseInsensitiveContains(searchText) ||
                              entry.aliases.contains(where: { $0.localizedCaseInsensitiveContains(searchText) }) ||
                              (entry.searchKeywords?.localizedCaseInsensitiveContains(searchText) ?? false)
                if !matches { continue }
            }
            
            // Difficulty filter
            if !diffFilter.isEmpty && !diffFilter.contains(entry.difficulty.lowercased()) { continue }
            
            // Rank filter
            if !rankFilter.isEmpty && !rankFilter.contains(entry.rank) { continue }
            
            // FC filter
            if !fcFilter.isEmpty {
                let normalizedFC = entry.fc.map { ThemeUtils.normalizeFC($0) } ?? ""
                if !fcFilter.contains(normalizedFC) { continue }
            }
            
            // FS filter
            if !fsFilter.isEmpty {
                let normalizedFS = entry.fs.map { ThemeUtils.normalizeFS($0) } ?? ""
                if !fsFilter.contains(normalizedFS) { continue }
            }
            
            entries.append(entry)
        }
        
        // Sort
        entries.sort { a, b in
            let result: Bool
            switch currentSortMode {
            case .rating:
                result = a.rating > b.rating
            case .achievement:
                result = a.achievement > b.achievement
            case .level:
                result = a.level > b.level
            }
            return ascending ? !result : result
        }
        
        withAnimation(.easeInOut(duration: 0.2)) {
            filteredEntries = entries
        }
    }
}
