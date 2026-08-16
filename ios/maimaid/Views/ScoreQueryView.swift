import SwiftUI
import SwiftData
import UIKit

struct ScoreQueryView: View {
    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Query private var songs: [Song]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    // MARK: - State
    
    @State private var songMap: [String: Song] = [:]
    @State private var allEntries: [ScoreEntry] = []
    @State private var filteredEntries: [ScoreEntry] = []
    @State private var isLoading = true
    @State private var searchText = ""
    @State private var searchTask: Task<Void, Never>?
    @State private var filterSettings = ScoreQueryFilterSettings()
    @State private var isShowingFilters = false
    
    // Display settings (persisted)
    @AppStorage(AppStorageKeys.scoreQueryDisplayMode) private var displayMode: DisplayMode = .grid
    @AppStorage(AppStorageKeys.scoreQueryGridColumns) private var committedColumns: Int = 5
    @AppStorage(AppStorageKeys.scoreQuerySortMode) private var sortMode: SortMode = .rating
    @AppStorage(AppStorageKeys.scoreQuerySortAscending) private var sortAscending: Bool = false
    
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
    
    // Stats
    @State private var stats = PlayerStats()

    private var activeServer: GameServer {
        activeProfiles.first.flatMap { GameServer(rawValue: $0.server) } ?? .jp
    }
    
    // MARK: - Types
    
    enum DisplayMode: String, CaseIterable {
        case grid, list
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
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(50))
                        withAnimation(.easeOut(duration: 0.2)) {
                            self.zoomAnchorEntryID = anchorID
                        }
                    }
                }

                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(300))
                    navigationDisabled = false
                }
            }
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
                        
                        // Content
                        Group {
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
                        .animation(.easeInOut(duration: 0.2), value: filteredEntries.count)
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
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) {
                        displayMode = displayMode == .grid ? .list : .grid
                    }
                } label: {
                    Label(
                        displayMode == .grid ? "scoreQuery.display.list" : "scoreQuery.display.grid",
                        systemImage: displayMode == .grid ? "list.bullet" : "square.grid.2x2"
                    )
                    .contentTransition(.symbolEffect(.replace))
                }
            }

            ToolbarItem(placement: .topBarTrailing) {
                Menu("sort.title", systemImage: "arrow.up.arrow.down") {
                    Picker("sort.title", selection: $sortMode) {
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
                }
            }

            ToolbarItem(placement: .topBarTrailing) {
                Button("filter.title", systemImage: "line.3.horizontal.decrease.circle") {
                    isShowingFilters = true
                }
                .tint(filterSettings.isEmpty ? .primary : .blue)
            }
        }
        .searchable(text: $searchText, prompt: "search.placeholder")
        .sheet(isPresented: $isShowingFilters) {
            ScoreQueryFilterView(settings: $filterSettings)
        }
        .onAppear {
            liveColumnCount = CGFloat(committedColumns)
        }
        .onChange(of: searchText) { _, _ in
            debounceFilter()
        }
        .onChange(of: sortMode) { _, _ in applyFiltersAndSort() }
        .onChange(of: sortAscending) { _, _ in applyFiltersAndSort() }
        .onChange(of: filterSettings) { _, _ in applyFiltersAndSort() }
        .onReceive(NotificationCenter.default.publisher(for: .maimaiScoresDidChange)) { notification in
            if let changedProfileID = notification.object as? UUID,
               changedProfileID != activeProfiles.first?.id {
                return
            }
            Task { await loadData() }
        }
        .task(id: activeProfiles.first?.server) {
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
                .foregroundStyle(.primary)
                .contentTransition(.numericText())
            Text(label)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity)
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
            .overlay {
                RoundedRectangle(cornerRadius: cornerRadius)
                    .stroke(
                        ThemeUtils.colorForDifficulty(entry.difficulty, entry.type, colorScheme),
                        lineWidth: intCols > 5 ? 1.5 : 2
                    )
            }

            scoreBadges(entry: entry, intCols: intCols)
                .padding(2)
        }
    }
    
    @ViewBuilder
    private func scoreBadges(entry: ScoreEntry, intCols: Int) -> some View {
        VStack(alignment: .trailing, spacing: 2) {
            gridBadge(
                text: entry.rank,
                color: RatingUtils.colorForRank(entry.rank),
                intCols: intCols
            )

            if let fc = entry.fc, !fc.isEmpty {
                gridBadge(
                    text: ThemeUtils.normalizeFC(fc),
                    color: ThemeUtils.fcColor(fc),
                    intCols: intCols
                )
            }

            if let fs = entry.fs, !fs.isEmpty {
                gridBadge(
                    text: ThemeUtils.normalizeFS(fs),
                    color: ThemeUtils.fsColor(fs),
                    intCols: intCols
                )
            }
        }
    }

    private func gridBadge(text: String, color: Color, intCols: Int) -> some View {
        Text(text)
            .font(.system(size: intCols > 5 ? 7 : 9, weight: .black, design: .rounded))
            .foregroundStyle(.white)
            .padding(.horizontal, 3)
            .padding(.vertical, 1)
            .background(color, in: RoundedRectangle(cornerRadius: 3))
            .lineLimit(1)
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
                .fill(ThemeUtils.colorForDifficulty(entry.difficulty, entry.type, colorScheme))
                .frame(width: 3)
                .padding(.vertical, 6)
            
            // Jacket
            SongJacketView(imageName: entry.imageName, size: 42, cornerRadius: 8)
            
            // Info
            VStack(alignment: .leading, spacing: 2) {
                Text(entry.songTitle)
                    .font(.system(size: 14, weight: .semibold))
                    .lineLimit(1)
                    .foregroundStyle(.primary)
                
                HStack(spacing: 4) {
                    Text("\(entry.achievement, format: .number.precision(.fractionLength(4)))%")
                        .font(.system(size: 11, weight: .medium, design: .monospaced))
                        .foregroundStyle(.secondary)
                    
                    if let fc = entry.fc, !fc.isEmpty {
                        Text(ThemeUtils.normalizeFC(fc))
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(ThemeUtils.fcColor(fc))
                    }
                    
                    if let fs = entry.fs, !fs.isEmpty {
                        Text(ThemeUtils.normalizeFS(fs))
                            .font(.system(size: 9, weight: .bold))
                            .foregroundStyle(ThemeUtils.fsColor(fs))
                    }
                }
            }
            
            Spacer()
            
            // Rating + Rank
            VStack(alignment: .trailing, spacing: 2) {
                Text(entry.rank)
                    .font(.system(size: 13, weight: .black, design: .rounded))
                    .foregroundStyle(RatingUtils.colorForRank(entry.rank))
                
                Text("\(entry.rating)")
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .foregroundStyle(.secondary)
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
            SongDetailView(song: song, preferredType: entry.type)
        } else {
            Text("scoreQuery.songNotFound")
        }
    }
    
    // MARK: - Data Loading
    
    private func loadData() async {
        let map = ScoreService.shared.scoreMap(context: modelContext)
        
        var sMap: [String: Song] = [:]
        var rootEntries: [ScoreEntry] = []
        
        for (index, song) in songs.enumerated() {
            if index.isMultiple(of: 32) {
                await Task.yield()
            }
            sMap[song.songIdentifier] = song
            
            if song.category.lowercased().contains("utage") || song.category.contains("宴") { continue }
            
            for sheet in song.sheets {
                if sheet.type.lowercased().contains("utage") { continue }

                guard ServerChartPolicy.isPlayable(sheet, on: activeServer) else { continue }
                let metadata = ServerChartPolicy.metadata(for: sheet, on: activeServer)
                guard let level = metadata.ratingLevel else { continue }
                
                let score = scoreForSheet(sheet, in: map)
                
                guard let score, score.rate > 0 else { continue }
                
                let rank = RatingUtils.calculateRank(achievement: score.rate)
                let rating = RatingUtils.calculateRating(internalLevel: level, achievement: score.rate)
                
                rootEntries.append(ScoreEntry(
                    id: "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)",
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
            try? await Task.sleep(for: .milliseconds(300))
            if !Task.isCancelled {
                applyFiltersAndSort()
            }
        }
    }
    
    private func applyFiltersAndSort() {
//        let searchLower = searchText.lowercased()
        let hasSearch = !searchText.isEmpty
        let normalizedSearch = SearchTextNormalizer.normalized(searchText)
        let compactSearch = SearchTextNormalizer.compact(searchText)
        let diffFilter = filterSettings.selectedDifficulties
        let rankFilter = filterSettings.selectedRanks
        let fcFilter = filterSettings.selectedFC
        let fsFilter = filterSettings.selectedFS
        let currentSortMode = sortMode
        let ascending = sortAscending
        
        var entries: [ScoreEntry] = []
        
        for entry in allEntries {
            // Search filter
            if hasSearch {
                let matches = SearchTextNormalizer.matches(
                    entry.songTitle,
                    normalizedQuery: normalizedSearch,
                    compactQuery: compactSearch,
                ) || entry.aliases.contains(where: {
                    SearchTextNormalizer.matches(
                        $0,
                        normalizedQuery: normalizedSearch,
                        compactQuery: compactSearch,
                    )
                }) || (entry.searchKeywords.map {
                    SearchTextNormalizer.matches(
                        $0,
                        normalizedQuery: normalizedSearch,
                        compactQuery: compactSearch,
                    )
                } ?? false)
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
        
        filteredEntries = entries
    }
    
    private func scoreForSheet(_ sheet: Sheet, in map: [String: Score]) -> Score? {
        for candidate in candidateSheetIds(for: sheet) {
            if let score = map[candidate] {
                return score
            }
        }
        return nil
    }
    
    private func candidateSheetIds(for sheet: Sheet) -> [String] {
        let rawIdentifiers: [String?] = [
            sheet.songIdentifier,
            sheet.song.map { String($0.songId) },
            sheet.song?.songIdentifier,
            sheet.songId == 0 ? nil : String(sheet.songId)
        ]
        
        var candidates: [String] = []
        var seen = Set<String>()
        
        for raw in rawIdentifiers {
            guard let raw, !raw.isEmpty, raw != "0" else { continue }
            
            for separator in ["_", "-"] {
                let sheetId = "\(raw)\(separator)\(sheet.type)\(separator)\(sheet.difficulty)"
                if seen.insert(sheetId).inserted {
                    candidates.append(sheetId)
                }
            }
        }
        
        return candidates
    }
}
