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
            case .title:
                return sortAscending ? (a.title < b.title) : (a.title > b.title)
            case .version:
                let vA = a.version ?? ""
                let vB = b.version ?? ""
                if vA == vB {
                    return a.sortOrder < b.sortOrder
                }
                let orderA = ThemeUtils.versionSortOrder(vA)
                let orderB = ThemeUtils.versionSortOrder(vB)
                return sortAscending ? (orderA < orderB) : (orderA > orderB)
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
                        Text("进入设置页面下载数据")
                    }
                } else {
                    ScrollView {
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
                    
                    if !searchText.isEmpty && sortedAndFilteredSongs.isEmpty {
                        ContentUnavailableView.search(text: searchText)
                            .padding(.top, 40)
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
