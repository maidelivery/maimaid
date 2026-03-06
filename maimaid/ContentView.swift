//
//  ContentView.swift
//  maimaid
//
//  Created by 西 宮缄 on 2/23/26.
//

import SwiftUI
import SwiftData

enum SortOption: String, CaseIterable, Identifiable {
    case defaultOrder = "sort.default"
    case versionAndDate = "sort.versionDate"
    case difficulty = "sort.difficulty"
    
    var id: String { self.rawValue }
}

struct ContentView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \Song.sortOrder, order: .forward) private var songs: [Song]
    
    var searchText: String = ""
    
    @State private var filterSettings = FilterSettings()
    @State private var showFilterSheet = false
    @State private var isFetching = false
    @State private var sortOption: SortOption = .defaultOrder
    @State private var sortAscending: Bool = true
    @State private var isGridView: Bool = false
    
    var allCategories: [String] {
        Array(Set(songs.map { $0.category })).sorted { ThemeUtils.categorySortOrder($0) < ThemeUtils.categorySortOrder($1) }
    }
    
    var allVersions: [String] {
        Array(Set(songs.compactMap { $0.version })).sorted { ThemeUtils.versionSortOrder($0) < ThemeUtils.versionSortOrder($1) }
    }
    
    var sortedAndFilteredSongs: [Song] {
        let filtered = FilterUtils.filterSongs(songs, settings: filterSettings, searchText: searchText)
        
        return filtered.sorted { a, b in
            switch sortOption {
            case .defaultOrder:
                return sortAscending ? (a.sortOrder < b.sortOrder) : (a.sortOrder > b.sortOrder)
            case .versionAndDate:
                let vA = a.version ?? ""
                let vB = b.version ?? ""
                let orderA = ThemeUtils.versionSortOrder(vA)
                let orderB = ThemeUtils.versionSortOrder(vB)
                
                if orderA != orderB {
                    return sortAscending ? (orderA < orderB) : (orderA > orderB)
                }
                
                // Same version, sort by date
                let dA = a.releaseDate ?? "0000-00-00"
                let dB = b.releaseDate ?? "0000-00-00"
                if dA != dB {
                    // Dates are different
                    return sortAscending ? (dA < dB) : (dA > dB)
                }
                
                // Same date or missing, fallback to sortOrder
                return a.sortOrder < b.sortOrder
            case .difficulty:
                let maxDiffA = a.sheets.compactMap { $0.internalLevelValue ?? $0.levelValue }.max() ?? 0.0
                let maxDiffB = b.sheets.compactMap { $0.internalLevelValue ?? $0.levelValue }.max() ?? 0.0
                if maxDiffA == maxDiffB {
                    return a.sortOrder < b.sortOrder
                }
                return sortAscending ? (maxDiffA < maxDiffB) : (maxDiffA > maxDiffB)
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
                    ScrollView {
                        if isGridView {
                            gridContent
                        } else {
                            listContent
                        }
                    }
                    
                    if !searchText.isEmpty && sortedAndFilteredSongs.isEmpty {
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
                        Picker("sort.title", selection: $sortOption) {
                            ForEach(SortOption.allCases) { option in
                                Text(LocalizedStringKey(option.rawValue)).tag(option)
                            }
                        }
                        
                        Toggle(isOn: $sortAscending) {
                            Label(sortAscending ? "sort.ascending" : "sort.descending", systemImage: sortAscending ? "arrow.up" : "arrow.down")
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
    }
    
    // MARK: - List Layout
    
    private var listContent: some View {
        LazyVStack(spacing: 8) {
            ForEach(sortedAndFilteredSongs) { song in
                NavigationLink {
                    SongDetailView(song: song)
                } label: {
                    SongRowView(song: song)
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
            ForEach(sortedAndFilteredSongs) { song in
                NavigationLink {
                    SongDetailView(song: song)
                } label: {
                    SongGridCell(song: song)
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
                
                // Difficulty dots
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
                        SongRowView.ScoreProgressDot(sheet: sheet)
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



#Preview {
    ContentView()
}
