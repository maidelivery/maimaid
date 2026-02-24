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
    
    @State private var searchText = ""
    @State private var selectedCategory: String? = nil
    @State private var filterSettings = FilterSettings()
    @State private var showFilterSheet = false
    @State private var showScanner = false
    @State private var isFetching = false
    @State private var sortOption: SortOption = .defaultOrder
    @State private var sortAscending: Bool = true
    
    // First Launch Sync
    @State private var requiresInitialSync = !UserDefaults.standard.bool(forKey: "didPerformInitialSync")
    @State private var syncProgress = ""
    
    var categories: [String] {
        Array(Set(songs.map { $0.category })).sorted()
    }
    
    var allVersions: [String] {
        Array(Set(songs.compactMap { $0.version })).sorted()
    }
    
    var sortedAndFilteredSongs: [Song] {
        let filtered = songs.filter { song in
            // Basic Text Search (Title, Artist, Note Designer)
            let matchesSearch = searchText.isEmpty || 
                               song.title.localizedCaseInsensitiveContains(searchText) || 
                               song.artist.localizedCaseInsensitiveContains(searchText) ||
                               song.sheets.contains(where: { $0.noteDesigner?.localizedCaseInsensitiveContains(searchText) ?? false }) ||
                               (song.searchKeywords?.localizedCaseInsensitiveContains(searchText) ?? false)
            
            // Category Filter
            let matchesCategory = selectedCategory == nil || song.category == selectedCategory
            
            // Advanced Filters
            let matchesVersion = filterSettings.selectedVersions.isEmpty || 
                                (song.version != nil && filterSettings.selectedVersions.contains(song.version!))
            
            let matchesDifficulty = filterSettings.selectedDifficulties.isEmpty || 
                                   song.sheets.contains(where: { filterSettings.selectedDifficulties.contains($0.difficulty.lowercased()) })
            
            let matchesType = filterSettings.selectedTypes.isEmpty || 
                             song.sheets.contains(where: { filterSettings.selectedTypes.contains($0.type.lowercased()) })
            
            let matchesBpm = !filterSettings.isBpmFilterActive || 
                            (song.bpm != nil && filterSettings.bpmRange.contains(song.bpm!))
            
            return matchesSearch && matchesCategory && matchesVersion && matchesDifficulty && matchesType && matchesBpm
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
                            Label("No Songs", systemImage: "music.note.list")
                        } description: {
                            Text("Tap the download button to fetch maimai data.")
                        }
                        .foregroundColor(.white)
                    } else {
                        ScrollView {
                            // Category Filter
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 10) {
                                    Button {
                                        selectedCategory = nil
                                    } label: {
                                        Text("All")
                                            .font(.subheadline)
                                            .padding(.horizontal, 16)
                                            .padding(.vertical, 8)
                                            .background(selectedCategory == nil ? Color.blue : Color.white.opacity(0.1))
                                            .cornerRadius(20)
                                            .foregroundColor(.white)
                                    }
                                    
                                    ForEach(categories, id: \.self) { category in
                                        Button {
                                            selectedCategory = category
                                        } label: {
                                            Text(category)
                                                .font(.subheadline)
                                                .padding(.horizontal, 16)
                                                .padding(.vertical, 8)
                                                .background(selectedCategory == category ? Color.blue : Color.white.opacity(0.1))
                                                .cornerRadius(20)
                                                .foregroundColor(.white)
                                        }
                                    }
                                }
                                .padding(.horizontal)
                            }
                            .padding(.top)
                            
                            LazyVStack(spacing: 12) {
                                ForEach(sortedAndFilteredSongs) { song in
                                    NavigationLink {
                                        SongDetailView(song: song)
                                    } label: {
                                        SongRowView(song: song)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                            .padding(.vertical)
                        }
                    }
                }
                .navigationTitle("maimaid")
                .searchable(text: $searchText, prompt: "Search songs, artists, aliases...")
                .toolbar {
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            showScanner = true
                        } label: {
                            Image(systemName: "camera.viewfinder")
                                .foregroundColor(.white)
                        }
                    }
                    
                    ToolbarItem(placement: .topBarTrailing) {
                        Button {
                            Task {
                                isFetching = true
                                try? await MaimaiDataFetcher.shared.fetchSongs(modelContext: modelContext)
                                isFetching = false
                            }
                        } label: {
                            Image(systemName: isFetching ? "arrow.triangle.2.circlepath" : "arrow.down.circle")
                                .foregroundColor(.white)
                                .rotationEffect(Angle(degrees: isFetching ? 360 : 0))
                                .animation(isFetching ? .linear(duration: 1).repeatForever(autoreverses: false) : .default, value: isFetching)
                        }
                    }
                    
                    ToolbarItem(placement: .topBarLeading) {
                        Menu {
                            Picker("Sort By", selection: $sortOption) {
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
                                           filterSettings.selectedTypes.isEmpty && 
                                           !filterSettings.isBpmFilterActive ? .white : .blue)
                        }
                    }
                }
                
                
                // Initial Sync Overlay
                if requiresInitialSync {
                    ZStack {
                        Color.black.opacity(0.8)
                            .ignoresSafeArea()
                        
                        VStack(spacing: 24) {
                            Image(systemName: "arrow.down.circle.fill")
                                .font(.system(size: 64))
                                .foregroundColor(.blue)
                                .symbolEffect(.pulse)
                            
                            VStack(spacing: 8) {
                                Text("Initial Sync")
                                    .font(.title2)
                                    .fontWeight(.bold)
                                
                                Text("Fetching rich song data and metadata...")
                                    .font(.subheadline)
                                    .foregroundColor(.white.opacity(0.6))
                                    .multilineTextAlignment(.center)
                            }
                            
                            ProgressView()
                                .tint(.white)
                            
                            Text(syncProgress)
                                .font(.caption2)
                                .foregroundColor(.white.opacity(0.4))
                        }
                        .padding(40)
//                        .glass(cornerRadius: 32)
                        .padding(40)
                    }
                    .transition(.opacity)
                }
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showFilterSheet) {
                FilterView(settings: $filterSettings, allVersions: allVersions)
            }
            .sheet(isPresented: $showScanner) {
                ScannerView()
            }
            .task {
                if requiresInitialSync {
                    do {
                        try await MaimaiDataFetcher.shared.fetchSongs(modelContext: modelContext)
                        withAnimation {
                            requiresInitialSync = false
                        }
                    } catch {
                        syncProgress = "Sync failed: \(error.localizedDescription)"
                    }
                }
            }
        }
    }



#Preview {
    ContentView()
}
