//
//  ContentView.swift
//  maimaid
//
//  Created by 西 宮缄 on 2/23/26.
//

import SwiftUI
import SwiftData

enum SortOption: String, CaseIterable, Identifiable {
    case defaultOrder = "默认"
    case title = "标题"
    case version = "版本"
    case difficulty = "最大难度"
    
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
    
    var allCategories: [String] {
        Array(Set(songs.map { $0.category })).sorted()
    }
    
    var allVersions: [String] {
        Array(Set(songs.compactMap { $0.version })).sorted()
    }
    
    var sortedAndFilteredSongs: [Song] {
        let filtered = songs.filter { song in
            // 1. Search Text
            let matchesSearch = searchText.isEmpty || 
                               song.title.localizedCaseInsensitiveContains(searchText) || 
                               song.artist.localizedCaseInsensitiveContains(searchText) ||
                               song.sheets.contains(where: { $0.noteDesigner?.localizedCaseInsensitiveContains(searchText) ?? false }) ||
                               (song.searchKeywords?.localizedCaseInsensitiveContains(searchText) ?? false) ||
                               song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(searchText) })
            if !matchesSearch { return false }
            
            // 2. Multi-Categories
            if !filterSettings.selectedCategories.isEmpty && !filterSettings.selectedCategories.contains(song.category) {
                return false
            }
            
            // 3. Versions
            if !filterSettings.selectedVersions.isEmpty {
                guard let version = song.version, filterSettings.selectedVersions.contains(version) else {
                    return false
                }
            }
            
            // 4. Types
            if !filterSettings.selectedTypes.isEmpty {
                let hasMatchingType = song.sheets.contains { sheet in
                    filterSettings.selectedTypes.contains(sheet.type.lowercased())
                }
                if !hasMatchingType { return false }
            }
            
            // 5. Difficulty Range + Reference Levels
            // User MUST select at least one difficulty as reference for the range to apply
            if !filterSettings.selectedDifficulties.isEmpty {
                let hasMatchingDifficultyInRange = song.sheets.contains { sheet in
                    let difficultyMatches = filterSettings.selectedDifficulties.contains(sheet.difficulty.lowercased())
                    if !difficultyMatches { return false }
                    
                    let level = sheet.internalLevelValue ?? sheet.levelValue ?? 0.0
                    return level >= filterSettings.minLevel && level <= filterSettings.maxLevel
                }
                if !hasMatchingDifficultyInRange { return false }
            }
            
            return true
        }
        
        return filtered.sorted { a, b in
            switch sortOption {
            case .defaultOrder:
                return sortAscending ? (a.sortOrder < b.sortOrder) : (a.sortOrder > b.sortOrder)
            case .title:
                return sortAscending ? (a.title < b.title) : (a.title > b.title)
            case .version:
                let vA = a.version ?? ""
                let vB = b.version ?? ""
                if vA == vB {
                    return a.sortOrder < b.sortOrder
                }
                return sortAscending ? (vA < vB) : (vA > vB)
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
                        Label("暂无歌曲", systemImage: "music.note.list")
                    } description: {
                        Text("点击右上角下载按钮获取 maimai 数据")
                    }
                } else {
                    ScrollView {
                        // Song list
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
                        
                        if !searchText.isEmpty && sortedAndFilteredSongs.isEmpty {
                            ContentUnavailableView.search(text: searchText)
                                .padding(.top, 40)
                        }
                    }
                }
            }
            .navigationTitle("Songs")
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Menu {
                        Picker("排序方式", selection: $sortOption) {
                            ForEach(SortOption.allCases) { option in
                                Text(option.rawValue).tag(option)
                            }
                        }
                        
                        Toggle(isOn: $sortAscending) {
                            Label("升序", systemImage: sortAscending ? "arrow.up" : "arrow.down")
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
}



#Preview {
    ContentView()
}
