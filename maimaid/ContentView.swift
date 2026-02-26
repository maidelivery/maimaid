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
    
    @State private var selectedCategory: String? = nil
    @State private var filterSettings = FilterSettings()
    @State private var showFilterSheet = false
    @State private var isFetching = false
    @State private var sortOption: SortOption = .defaultOrder
    @State private var sortAscending: Bool = true
    
    var categories: [String] {
        Array(Set(songs.map { $0.category })).sorted()
    }
    
    var allVersions: [String] {
        Array(Set(songs.compactMap { $0.version })).sorted()
    }
    
    var sortedAndFilteredSongs: [Song] {
        let filtered = songs.filter { song in
            let matchesSearch = searchText.isEmpty || 
                               song.title.localizedCaseInsensitiveContains(searchText) || 
                               song.artist.localizedCaseInsensitiveContains(searchText) ||
                               song.sheets.contains(where: { $0.noteDesigner?.localizedCaseInsensitiveContains(searchText) ?? false }) ||
                               (song.searchKeywords?.localizedCaseInsensitiveContains(searchText) ?? false) ||
                               song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(searchText) })
            
            let matchesCategory = selectedCategory == nil || song.category == selectedCategory
            
            let matchesVersion = filterSettings.selectedVersions.isEmpty || 
                                (song.version != nil && filterSettings.selectedVersions.contains(song.version!))
            
            let matchesDifficulty = filterSettings.selectedDifficulties.isEmpty || 
                                   song.sheets.contains(where: { filterSettings.selectedDifficulties.contains($0.difficulty.lowercased()) })
            
            let matchesType = filterSettings.selectedTypes.isEmpty || 
                             song.sheets.contains(where: { filterSettings.selectedTypes.contains($0.type.lowercased()) })
            
            
            return matchesSearch && matchesCategory && matchesVersion && matchesDifficulty && matchesType
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
                        // Category pills
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                categoryPill(title: "全部", isSelected: selectedCategory == nil) {
                                    selectedCategory = nil
                                }
                                
                                ForEach(categories, id: \.self) { category in
                                    categoryPill(title: category, isSelected: selectedCategory == category) {
                                        selectedCategory = category
                                    }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                        .padding(.top, 8)
                        
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
                
                
                ToolbarItem(placement: .topBarLeading) {
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
                
                ToolbarItem(placement: .topBarLeading) {
                    Button {
                        showFilterSheet = true
                    } label: {
                        Image(systemName: "line.3.horizontal.decrease.circle")
                            .foregroundColor(filterSettings.selectedVersions.isEmpty && 
                                       filterSettings.selectedDifficulties.isEmpty && 
                                       filterSettings.selectedTypes.isEmpty ? Color.primary : Color.blue)
                    }
                }
            }
        }
        .sheet(isPresented: $showFilterSheet) {
            FilterView(settings: $filterSettings, allVersions: allVersions)
        }
    }
    
    // MARK: - Category Pill
    
    private func categoryPill(title: String, isSelected: Bool, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .padding(.horizontal, 14)
                .padding(.vertical, 7)
                .foregroundColor(isSelected ? .white : .primary)
                .background(
                    isSelected ? Color.blue : Color.primary.opacity(0.06),
                    in: Capsule()
                )
                .overlay(
                    Capsule()
                        .strokeBorder(isSelected ? Color.blue.opacity(0.3) : Color.primary.opacity(0.08), lineWidth: 1)
                )
        }
        .buttonStyle(.plain)
    }
}



#Preview {
    ContentView()
}
