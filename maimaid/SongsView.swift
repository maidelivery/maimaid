//
//  ContentView.swift
//  maimaid
//
//  Created by 西 宫缄 on 2/23/26.
//

import SwiftUI
import SwiftData

enum SortOption: String, CaseIterable, Identifiable {
    case defaultOrder = "sort.default"
    case versionAndDate = "sort.versionDate"
    case difficulty = "sort.difficulty"
    
    var id: String { self.rawValue }
}

struct SongsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \Song.sortOrder, order: .forward) private var songs: [Song]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    
    var searchText: String = ""
    
    @State private var filterSettings = FilterSettings()
    @State private var showFilterSheet = false
    @State private var isFetching = false
    @AppStorage(AppStorageKeys.songsSortOption) private var sortOption: SortOption = .defaultOrder
    @AppStorage(AppStorageKeys.songsSortAscending) private var sortAscending: Bool = true
    @State private var isGridView: Bool = false
    @State private var displayedSongs: [Song] = []
    @State private var isSorting: Bool = false
    @State private var searchTask: Task<Void, Never>?
    
    // Performance: Cache score map to avoid repeated lookups
    @State private var scoreCache: [String: Score] = [:]
    @State private var cachedProfileId: UUID? = nil
    
    // Grid zoom state
    @AppStorage(AppStorageKeys.songsGridColumns) private var committedColumns: Int = 4
    @State private var isZooming: Bool = false
    @State private var liveColumnCount: CGFloat = 4.0
    @State private var pinchStartColumns: CGFloat = 4.0
    @State private var zoomAnchorSongID: String? = nil
    @State private var viewportHeight: CGFloat = 0
    /// Brief cooldown after pinch ends to prevent accidental tap activation
    @State private var navigationDisabled: Bool = false
    
    /// Namespace for matched geometry zoom transition (iOS 18+)
    @Namespace private var songTransitionNamespace
    
    /// The song selected for fullScreenCover detail presentation
    @State private var selectedSong: Song? = nil
    
    /// Task for background score cache refresh
    @State private var scoreCacheTask: Task<Void, Never>?
    
    private let minColumns: CGFloat = 3
    private let maxColumns: CGFloat = 9
    
    private struct SongFilterSnapshot: Sendable {
        struct SheetSnapshot: Sendable {
            let type: String
            let difficulty: String
            let noteDesigner: String?
            let internalLevelValue: Double?
            let levelValue: Double?
            let regionJp: Bool
            let regionIntl: Bool
            let regionCn: Bool
        }
        
        let songIdentifier: String
        let songId: Int
        let category: String
        let title: String
        let artist: String
        let version: String?
        let releaseDate: String?
        let sortOrder: Int
        let isFavorite: Bool
        let searchKeywords: String?
        let aliases: [String]
        let sheets: [SheetSnapshot]
        let maxDifficulty: Double
    }
    
    private func refreshScoreCache() {
        scoreCacheTask?.cancel()
        scoreCacheTask = Task { @MainActor in
            // Small delay to debounce rapid calls
            try? await Task.sleep(for: .milliseconds(50))
            guard !Task.isCancelled else { return }
            let profileId = activeProfiles.first?.id
            scoreCache = ScoreService.shared.scoreMap(context: modelContext)
            cachedProfileId = profileId
        }
    }
    
    var allCategories: [String] {
        Array(Set(songs.map { $0.category })).sorted { ThemeUtils.categorySortOrder($0) < ThemeUtils.categorySortOrder($1) }
    }
    
    var allVersions: [String] {
        Array(Set(songs.compactMap { $0.version })).sorted { ThemeUtils.versionSortOrder($0) < ThemeUtils.versionSortOrder($1) }
    }
    
    private func makeSongSnapshot(_ song: Song) -> SongFilterSnapshot {
        let sheetSnapshots = song.sheets.map { sheet in
            SongFilterSnapshot.SheetSnapshot(
                type: sheet.type.lowercased(),
                difficulty: sheet.difficulty.lowercased(),
                noteDesigner: sheet.noteDesigner,
                internalLevelValue: sheet.internalLevelValue,
                levelValue: sheet.levelValue,
                regionJp: sheet.regionJp,
                regionIntl: sheet.regionIntl,
                regionCn: sheet.regionCn
            )
        }
        
        let maxDifficulty = sheetSnapshots.compactMap { $0.internalLevelValue ?? $0.levelValue }.max() ?? 0.0
        
        return SongFilterSnapshot(
            songIdentifier: song.songIdentifier,
            songId: song.songId,
            category: song.category,
            title: song.title,
            artist: song.artist,
            version: song.version,
            releaseDate: song.releaseDate,
            sortOrder: song.sortOrder,
            isFavorite: song.isFavorite,
            searchKeywords: song.searchKeywords,
            aliases: song.aliases,
            sheets: sheetSnapshots,
            maxDifficulty: maxDifficulty
        )
    }
    
    private static func filterAndSortSongIdentifiers(
        from snapshots: [SongFilterSnapshot],
        settings: FilterSettings,
        searchText: String,
        sortOption: SortOption,
        sortAscending: Bool
    ) -> [String] {
        let normalizedSearch = searchText.replacing(" ", with: "")
        let hasSearch = !searchText.isEmpty
        let hasCategories = !settings.selectedCategories.isEmpty
        let hasVersions = !settings.selectedVersions.isEmpty
        let hasTypes = !settings.selectedTypes.isEmpty
        let hasDifficulties = !settings.selectedDifficulties.isEmpty
        
        let filtered = snapshots.filter { song in
            if hasSearch {
                let titleMatch = song.title.localizedStandardContains(searchText)
                    || song.title.replacing(" ", with: "").localizedStandardContains(normalizedSearch)
                let artistMatch = song.artist.localizedStandardContains(searchText)
                    || song.artist.replacing(" ", with: "").localizedStandardContains(normalizedSearch)
                let keywordMatch = (song.searchKeywords?.localizedStandardContains(searchText) ?? false)
                    || (song.searchKeywords?.replacing(" ", with: "").localizedStandardContains(normalizedSearch) ?? false)
                let aliasMatch = song.aliases.contains {
                    $0.localizedStandardContains(searchText)
                        || $0.replacing(" ", with: "").localizedStandardContains(normalizedSearch)
                }
                let designerMatch = song.sheets.contains {
                    ($0.noteDesigner?.localizedStandardContains(searchText) ?? false)
                        || ($0.noteDesigner?.replacing(" ", with: "").localizedStandardContains(normalizedSearch) ?? false)
                }
                let idMatch = String(song.songId) == searchText
                
                if !titleMatch && !artistMatch && !keywordMatch && !aliasMatch && !designerMatch && !idMatch {
                    return false
                }
            }
            
            if settings.showFavoritesOnly && !song.isFavorite {
                return false
            }
            
            if hasCategories && !settings.selectedCategories.contains(song.category) {
                return false
            }
            
            if hasVersions {
                guard let version = song.version, settings.selectedVersions.contains(version) else {
                    return false
                }
            }
            
            if hasTypes || hasDifficulties || settings.hideDeletedSongs {
                var hasMatchingType = !hasTypes
                var hasMatchingDifficulty = !hasDifficulties
                var isPlayable = !settings.hideDeletedSongs
                
                for sheet in song.sheets {
                    if hasTypes && settings.selectedTypes.contains(sheet.type) {
                        hasMatchingType = true
                    }
                    
                    if hasDifficulties && settings.selectedDifficulties.contains(sheet.difficulty) {
                        let level = sheet.internalLevelValue ?? sheet.levelValue ?? 0.0
                        if level >= settings.minLevel && level <= settings.maxLevel {
                            hasMatchingDifficulty = true
                        }
                    }
                    
                    if settings.hideDeletedSongs && (sheet.regionJp || sheet.regionIntl || sheet.regionCn) {
                        isPlayable = true
                    }
                }
                
                if !hasMatchingType || !hasMatchingDifficulty || !isPlayable {
                    return false
                }
            }
            
            return true
        }
        
        return filtered.sorted { a, b in
            switch sortOption {
            case .defaultOrder:
                return sortAscending ? (a.sortOrder < b.sortOrder) : (a.sortOrder > b.sortOrder)
            case .versionAndDate:
                let orderA = ThemeUtils.versionSortOrder(a.version ?? "")
                let orderB = ThemeUtils.versionSortOrder(b.version ?? "")
                
                if orderA != orderB {
                    return sortAscending ? (orderA < orderB) : (orderA > orderB)
                }
                
                let releaseA = a.releaseDate ?? "0000-00-00"
                let releaseB = b.releaseDate ?? "0000-00-00"
                if releaseA != releaseB {
                    return sortAscending ? (releaseA < releaseB) : (releaseA > releaseB)
                }
                return a.sortOrder < b.sortOrder
            case .difficulty:
                if a.maxDifficulty == b.maxDifficulty {
                    return a.sortOrder < b.sortOrder
                }
                return sortAscending ? (a.maxDifficulty < b.maxDifficulty) : (a.maxDifficulty > b.maxDifficulty)
            }
        }
        .map(\.songIdentifier)
    }
    
    private func updateDisplayedSongs(debounce: Duration? = nil) {
        searchTask?.cancel()
        isSorting = true
        
        searchTask = Task { @MainActor in
            defer { isSorting = false }
            if let debounce {
                try? await Task.sleep(for: debounce)
            }
            guard !Task.isCancelled else { return }
            
            let currentSongs = songs
            let songsByIdentifier = Dictionary(uniqueKeysWithValues: currentSongs.map { ($0.songIdentifier, $0) })
            let snapshots = currentSongs.map(makeSongSnapshot)
            
            let currentFilter = filterSettings
            let currentSearch = searchText
            let currentSort = sortOption
            let currentAscending = sortAscending
            
            let identifiers = await Task.detached(priority: .userInitiated) {
                Self.filterAndSortSongIdentifiers(
                    from: snapshots,
                    settings: currentFilter,
                    searchText: currentSearch,
                    sortOption: currentSort,
                    sortAscending: currentAscending
                )
            }.value
            
            guard !Task.isCancelled else { return }
            displayedSongs = identifiers.compactMap { songsByIdentifier[$0] }
        }
    }
    
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
    
    /// Whether to show score dots at this column count
    private func showDots(for columns: Int) -> Bool {
        columns <= 5
    }
    
    /// Estimate which song index is near the center of the pinch gesture.
    private func estimateCenterSongIndex(pinchY: CGFloat, gridWidth: CGFloat, columns: Int) -> Int {
        let metrics = gridMetrics(intColumns: columns, in: gridWidth)
        let cellSize = metrics.cellSize
        let spacing = metrics.spacing
        let rowHeight = cellSize + spacing
        guard rowHeight > 0 else { return 0 }
        
        let estimatedRow = Int(max(0, pinchY - 12) / rowHeight)
        let centerIndexInRow = columns / 2
        let index = estimatedRow * columns + centerIndexInRow
        return min(max(0, index), max(0, displayedSongs.count - 1))
    }
    
    private func makePinchGesture(width: CGFloat) -> some Gesture {
        MagnifyGesture(minimumScaleDelta: 0.02)
            .onChanged { value in
                if !isZooming {
                    isZooming = true
                    navigationDisabled = true
                    pinchStartColumns = CGFloat(committedColumns)
                    
                    let centerIdx = estimateCenterSongIndex(
                        pinchY: value.startLocation.y,
                        gridWidth: width,
                        columns: committedColumns
                    )
                    if centerIdx >= 0 && centerIdx < displayedSongs.count {
                        zoomAnchorSongID = displayedSongs[centerIdx].songIdentifier
                    }
                }
                liveColumnCount = continuousColumns(for: value.magnification)
            }
            .onEnded { value in
                let finalCols = continuousColumns(for: value.magnification)
                let targetCols = max(Int(minColumns), min(Int(maxColumns), Int(finalCols.rounded())))
                let changed = targetCols != committedColumns
                let anchorID = zoomAnchorSongID
                
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
                            self.zoomAnchorSongID = anchorID
                        }
                    }
                }
                
                Task { @MainActor in
                    try? await Task.sleep(for: .milliseconds(300))
                    navigationDisabled = false
                }
            }
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                if songs.isEmpty && !isFetching {
                    ContentUnavailableView {
                        Label("songs.unavailable.title", systemImage: "music.note.list")
                    } description: {
                        Text("songs.unavailable.description")
                    }
                } else {
                    GeometryReader { geo in
                        let width = geo.size.width
                        
                        ScrollViewReader { scrollProxy in
                            ScrollView {
                                if isGridView {
                                    gridBody(in: width)
                                } else {
                                    listContent
                                }
                            }
                            .scrollDisabled(isZooming)
                            .if(isGridView) { view in
                                view.simultaneousGesture(makePinchGesture(width: width))
                            }
                            .onChange(of: zoomAnchorSongID) { _, newID in
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
                    .overlay {
                        if isSorting {
                            ProgressView()
                                .padding()
                                .background(.ultraThinMaterial)
                                .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    }
                    
                    if !searchText.isEmpty && displayedSongs.isEmpty && !isSorting {
                        ContentUnavailableView.search(text: searchText)
                            .padding(.top, 40)
                    }
                }
            }
            .navigationTitle("songs.title")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        withAnimation(.easeInOut(duration: 0.25)) {
                            isGridView.toggle()
                        }
                    } label: {
                        Image(systemName: isGridView ? "list.bullet" : "square.grid.2x2")
                            .foregroundColor(.blue)
                            .contentTransition(.symbolEffect(.replace))
                    }
                }
                
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker("sort.title", selection: $sortOption.animation(.easeInOut)) {
                            ForEach(SortOption.allCases) { option in
                                Text(LocalizedStringKey(option.rawValue)).tag(option)
                            }
                        }
                        
                        Divider()
                        
                        Button {
                            withAnimation(.easeInOut) {
                                sortAscending.toggle()
                            }
                        } label: {
                            Label(
                                sortAscending ? "sort.ascending" : "sort.descending",
                                systemImage: sortAscending ? "arrow.up" : "arrow.down"
                            )
                        }
                    } label: {
                        Image(systemName: "arrow.up.arrow.down")
                            .foregroundColor(.blue)
                    }
                }
                
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        showFilterSheet = true
                    } label: {
                        Image(systemName: "line.3.horizontal.decrease.circle")
                            .foregroundColor(filterSettings == FilterSettings() ? Color.primary : Color.blue)
                    }
                }
            }
            // fullScreenCover for song detail — used for zoom transition
            .fullScreenCover(item: $selectedSong) { song in
                NavigationStack {
                    SongDetailView(song: song)
                        .toolbar {
                            ToolbarItem(placement: .topBarLeading) {
                                Button {
                                    selectedSong = nil
                                } label: {
                                    Image(systemName: "chevron.left")
                                        .font(.system(size: 18))
                                        .symbolRenderingMode(.hierarchical)
                                }
                            }
                        }
                }
                .applyZoomTransition(id: song.songIdentifier, ns: songTransitionNamespace)
            }
        }
        .sheet(isPresented: $showFilterSheet) {
            FilterView(settings: $filterSettings, allCategories: allCategories, allVersions: allVersions)
        }
        .onAppear {
            // Only refresh cache if empty or profile changed
            let currentProfileId = activeProfiles.first?.id
            if scoreCache.isEmpty || cachedProfileId != currentProfileId {
                refreshScoreCache()
            }
            if displayedSongs.isEmpty && !songs.isEmpty {
                updateDisplayedSongs()
            }
            liveColumnCount = CGFloat(committedColumns)
        }
        // Use single onChange for profile changes
        .onChange(of: activeProfiles.first?.id) { _, _ in
            refreshScoreCache()
        }
        .onChange(of: songs) { _, _ in updateDisplayedSongs() }
        .onChange(of: searchText) { _, _ in updateDisplayedSongs(debounce: .milliseconds(200)) }
        .onChange(of: filterSettings) { _, _ in updateDisplayedSongs() }
        .onChange(of: sortOption) { _, _ in updateDisplayedSongs() }
        .onChange(of: sortAscending) { _, _ in updateDisplayedSongs() }
        .onDisappear {
            searchTask?.cancel()
            scoreCacheTask?.cancel()
        }
    }
    
    // MARK: - List Layout
    
    private var listContent: some View {
        LazyVStack(spacing: 8) {
            ForEach(displayedSongs) { song in
                Button {
                    guard !navigationDisabled && !isZooming else { return }
                    selectedSong = song
                } label: {
                    SongRowView(song: song, scoreCache: scoreCache)
                }
                .buttonStyle(.plain)
                .applyMatchedTransitionSource(id: song.songIdentifier, ns: songTransitionNamespace)
            }
        }
        .padding(.vertical, 12)
    }
    
    // MARK: - Grid Layout
    
    private func gridBody(in width: CGFloat) -> some View {
        let cols = isZooming ? liveColumnCount : CGFloat(committedColumns)
        let intCols = max(Int(minColumns), min(Int(maxColumns), Int(cols.rounded())))
        let metrics = gridMetrics(intColumns: intCols, in: width)
        let cellSize = metrics.cellSize
        let spacing = metrics.spacing
        let horizontalPadding = spacing + 2
        let cr = cornerRadius(for: intCols)
        let dots = showDots(for: intCols)
        
        return LazyVGrid(
            columns: Array(repeating: GridItem(.fixed(cellSize), spacing: spacing), count: intCols),
            spacing: spacing
        ) {
            ForEach(displayedSongs) { song in
                gridCellView(song: song, intCols: intCols, cellSize: cellSize, cornerRadius: cr, showDots: dots)
                    .frame(width: cellSize, height: cellSize)
                    .id(song.songIdentifier)
            }
        }
        .padding(.horizontal, horizontalPadding)
        .padding(.vertical, 12)
    }
    
    @ViewBuilder
    private func gridCellView(song: Song, intCols: Int, cellSize: CGFloat, cornerRadius: CGFloat, showDots: Bool) -> some View {
        Button {
            guard !navigationDisabled && !isZooming else { return }
            selectedSong = song
        } label: {
            SongGridCell(
                song: song,
                scoreCache: scoreCache,
                columnCount: intCols,
                cellSize: cellSize,
                cornerRadius: cornerRadius,
                showDots: showDots
            )
        }
        .buttonStyle(.plain)
        .applyMatchedTransitionSource(id: song.songIdentifier, ns: songTransitionNamespace)
    }
}

// MARK: - Conditional modifier helper

extension View {
    @ViewBuilder
    func `if`<Content: View>(_ condition: Bool, transform: (Self) -> Content) -> some View {
        if condition {
            transform(self)
        } else {
            self
        }
    }
}

// MARK: - iOS 18 Zoom Transition Helpers

extension View {
    @ViewBuilder
    func applyMatchedTransitionSource(id: String, ns: Namespace.ID) -> some View {
        if #available(iOS 18.0, *) {
            self.matchedTransitionSource(id: id, in: ns)
        } else {
            self
        }
    }
    
    @ViewBuilder
    func applyZoomTransition(id: String, ns: Namespace.ID) -> some View {
        if #available(iOS 18.0, *) {
            self.navigationTransition(.zoom(sourceID: id, in: ns))
        } else {
            self
        }
    }
}

// MARK: - Grid Cell

private struct SongGridCell: View {
    let song: Song
    var scoreCache: [String: Score] = [:]
    var columnCount: Int = 4
    var cellSize: CGFloat = 60
    var cornerRadius: CGFloat = 6
    var showDots: Bool = true
    @Environment(\.modelContext) private var modelContext
    
    private var utageFontSize: CGFloat {
        switch columnCount {
        case ...3: return 14
        case 4: return 8
        case 5: return 7
        default: return 6
        }
    }
    
    var body: some View {
        ZStack(alignment: .bottomTrailing) {
            SongJacketView(
                imageName: song.imageName,
                size: cellSize,
                cornerRadius: cornerRadius,
                useThumbnail: true
            )
            
            if showDots {
                let isUtage = song.songId > 100000
                
                HStack(spacing: 2) {
                    if isUtage {
                        Text("宴")
                            .font(.system(size: utageFontSize, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 2)
                    } else {
                        let prioritizedSheets: [Sheet] = {
                            let dxSheets = song.sheets.filter { $0.type.lowercased() == "dx" }
                            if !dxSheets.isEmpty {
                                return dxSheets.sorted(by: { ThemeUtils.difficultyOrder($0.difficulty) > ThemeUtils.difficultyOrder($1.difficulty) })
                            }
                            return song.sheets
                                .filter { $0.type.lowercased() == "std" }
                                .sorted(by: { ThemeUtils.difficultyOrder($0.difficulty) > ThemeUtils.difficultyOrder($1.difficulty) })
                        }()
                        
                        ForEach(prioritizedSheets) { sheet in
                            if scoreCache.isEmpty {
                                SongRowView.ScoreProgressDot(sheet: sheet, context: modelContext)
                            } else {
                                ScoreProgressDotOptimized(sheet: sheet, scoreCache: scoreCache)
                            }
                        }
                    }
                }
                .padding(.horizontal, columnCount <= 3 ? 6 : 4)
                .padding(.vertical, columnCount <= 3 ? 3 : 2)
                .background(song.songId > 100000 ? Color.pink.opacity(0.5) : Color.clear)
                .background(.ultraThickMaterial)
                .environment(\.colorScheme, .light)
                .clipShape(Capsule())
                .padding(columnCount <= 3 ? 6 : 4)
            }
        }
    }
}
