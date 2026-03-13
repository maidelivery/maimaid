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
    
    var searchText: String = ""
    
    @State private var filterSettings = FilterSettings()
    @State private var showFilterSheet = false
    @State private var isFetching = false
    @AppStorage("songs.sortOption") private var sortOption: SortOption = .defaultOrder
    @AppStorage("songs.sortAscending") private var sortAscending: Bool = true
    @State private var isGridView: Bool = false
    @State private var displayedSongs: [Song] = []
    @State private var isSorting: Bool = false
    
    // Performance: Cache score map to avoid repeated lookups
    @State private var scoreCache: [String: Score] = [:]
    @State private var lastCacheInvalidation: Int = 0
    
    var allCategories: [String] {
        Array(Set(songs.map { $0.category })).sorted { ThemeUtils.categorySortOrder($0) < ThemeUtils.categorySortOrder($1) }
    }
    
    var allVersions: [String] {
        Array(Set(songs.compactMap { $0.version })).sorted { ThemeUtils.versionSortOrder($0) < ThemeUtils.versionSortOrder($1) }
    }
    
    private func updateDisplayedSongs() {
        isSorting = true
        
        // Capture current values synchronously on MainActor
        let currentSongs = songs
        let currentFilter = filterSettings
        let currentSearch = searchText
        let currentSort = sortOption
        let currentAscending = sortAscending
        
        // Refresh score cache if needed
        if lastCacheInvalidation != currentSongs.count {
            scoreCache = ScoreService.shared.scoreMap(context: modelContext)
            lastCacheInvalidation = currentSongs.count
        }
        
        // Perform filtering and sorting on MainActor
        Task { @MainActor in
            // Small delay to let UI animations finish
//            try? await Task.sleep(nanoseconds: 50_000_000)w
            
            // Use optimized single-pass filter
            let filtered = FilterUtils.filterSongsOptimized(currentSongs, settings: currentFilter, searchText: currentSearch)
            
            // Sort songs
            let result = filtered.sorted { a, b in
                switch currentSort {
                case .defaultOrder:
                    return currentAscending ? (a.sortOrder < b.sortOrder) : (a.sortOrder > b.sortOrder)
                case .versionAndDate:
                    let vA = a.version ?? ""
                    let vB = b.version ?? ""
                    let orderA = ThemeUtils.versionSortOrder(vA)
                    let orderB = ThemeUtils.versionSortOrder(vB)
                    
                    if orderA != orderB {
                        return currentAscending ? (orderA < orderB) : (orderA > orderB)
                    }
                    
                    let dA = a.releaseDate ?? "0000-00-00"
                    let dB = b.releaseDate ?? "0000-00-00"
                    if dA != dB {
                        return currentAscending ? (dA < dB) : (dA > dB)
                    }
                    return a.sortOrder < b.sortOrder
                case .difficulty:
                    let maxDiffA = a.sheets.compactMap { $0.internalLevelValue ?? $0.levelValue }.max() ?? 0.0
                    let maxDiffB = b.sheets.compactMap { $0.internalLevelValue ?? $0.levelValue }.max() ?? 0.0
                    if maxDiffA == maxDiffB {
                        return a.sortOrder < b.sortOrder
                    }
                    return currentAscending ? (maxDiffA < maxDiffB) : (maxDiffA > maxDiffB)
                }
            }
            
            self.displayedSongs = result
            self.isSorting = false
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
                    ScrollView {
                        if isGridView {
                            gridContent
                        } else {
                            listContent
                        }
                    }
                    .overlay {
                        if isSorting && !displayedSongs.isEmpty {
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
        }
        .sheet(isPresented: $showFilterSheet) {
            FilterView(settings: $filterSettings, allCategories: allCategories, allVersions: allVersions)
        }
        .onAppear {
            if displayedSongs.isEmpty && !songs.isEmpty {
                // Pre-cache scores on first load
                scoreCache = ScoreService.shared.scoreMap(context: modelContext)
                lastCacheInvalidation = songs.count
                updateDisplayedSongs()
            }
        }
        .onChange(of: songs) { _, _ in updateDisplayedSongs() }
        .onChange(of: searchText) { _, _ in updateDisplayedSongs() }
        .onChange(of: filterSettings) { _, _ in updateDisplayedSongs() }
        .onChange(of: sortOption) { _, _ in updateDisplayedSongs() }
        .onChange(of: sortAscending) { _, _ in updateDisplayedSongs() }
    }
    
    // MARK: - List Layout
    
    private var listContent: some View {
        LazyVStack(spacing: 8) {
            ForEach(displayedSongs) { song in
                NavigationLink {
                    SongDetailView(song: song)
                } label: {
                    SongRowView(song: song, scoreCache: scoreCache)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.vertical, 12)
    }
    
    // MARK: - Grid Layout
    
    private let gridColumns = [
        GridItem(.flexible(), spacing: 0),
        GridItem(.flexible(), spacing: 0),
        GridItem(.flexible(), spacing: 0)
    ]
    
    private var gridContent: some View {
        LazyVGrid(columns: gridColumns, spacing: 0) {
            ForEach(displayedSongs) { song in
                NavigationLink {
                    SongDetailView(song: song)
                } label: {
                    SongGridCell(song: song, scoreCache: scoreCache)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(.horizontal, 1) // Offset for borders
    }
}

// MARK: - Grid Cell

private struct SongGridCell: View {
    let song: Song
    var scoreCache: [String: Score] = [:]
    @Environment(\.modelContext) private var modelContext
    
    var body: some View {
        VStack(spacing: 8) {
            // Jacket
            SongJacketView(imageName: song.imageName, size: 80, cornerRadius: 4)
            
            VStack(spacing: 4) {
                // Title
                Text(song.title)
                    .font(.system(size: 10, weight: .medium))
                    .foregroundColor(.primary)
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .frame(height: 28)
                
                // Difficulty dots - use cached scores
                HStack(spacing: 2) {
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
                            // Fallback to direct lookup if cache not provided
                            SongRowView.ScoreProgressDot(sheet: sheet, context: modelContext)
                        } else {
                            ScoreProgressDotOptimized(sheet: sheet, scoreCache: scoreCache)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 16)
        .padding(.horizontal, 4)
        .border(Color.primary.opacity(0.1), width: 0.5)
    }
}
