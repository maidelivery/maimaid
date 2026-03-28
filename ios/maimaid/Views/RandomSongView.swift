import SwiftUI
import SwiftData

struct RandomSongView: View {
    @Query private var allSongs: [Song]
    @State private var songCount: Int = 3
    @State private var isSpinning = false
    @State private var results: [Song] = []
    @State private var filterSettings = FilterSettings()
    @State private var showFilterSheet = false
    @State private var spinOffsets: [Double] = [0, 0, 0, 0]
    @State private var displayedSongs: [[Song]] = [[], [], [], []]
    @State private var pendingResults: [Song] = []
    @State private var currentSpinTask: Task<Void, Never>? = nil
    
    // Computed properties for filter options
    private var allCategories: [String] {
        Array(Set(allSongs.map { $0.category })).sorted { ThemeUtils.categorySortOrder($0) < ThemeUtils.categorySortOrder($1) }
    }
    
    private var allVersions: [String] {
        Array(Set(allSongs.compactMap { $0.version })).sorted { ThemeUtils.versionSortOrder($0) < ThemeUtils.versionSortOrder($1) }
    }
    
    private var currentSlotHeight: CGFloat {
        songCount == 4 ? 100 : 120
    }
    private let visibleItems: Int = 1
    private let spinDuration: Double = 3.0
    
    var body: some View {
        VStack(spacing: 0) {
            // Top Section
            VStack(spacing: 24) {
                Picker("random.count", selection: $songCount) {
                    Text("random.count.3").tag(3)
                    Text("random.count.4").tag(4)
                }
                .pickerStyle(.segmented)
                .padding(.horizontal, 40)
                .padding(.top, 16)
                
                // Slot Machine Area
                HStack(spacing: 8) {
                    ForEach(0..<songCount, id: \.self) { index in
                        SlotColumn(
                            songs: displayedSongs[index],
                            offset: spinOffsets[index],
                            slotHeight: currentSlotHeight,
                            jacketSize: songCount == 4 ? 70 : 85
                        )
                    }
                }
                .allowsHitTesting(false)
                .frame(height: currentSlotHeight)
                .padding(16)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))
                .padding(.horizontal, 16)
                
                // Spin Button
                Button(action: {
                    if isSpinning {
                        skipSpin()
                    } else {
                        spin()
                    }
                }) {
                    HStack {
                        Image(systemName: isSpinning ? "forward.end.fill" : "dice.fill")
                        Text(isSpinning ? "random.action.skip" : "random.action.spin")
                            .font(.headline.bold())
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .disabled(allSongs.isEmpty)
                .padding(.horizontal, 40)
            }
            .padding(.bottom, 24)
            .background(Color(.systemGroupedBackground))
            
            // Results Section
            if !isSpinning && !results.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 12) {
                        Text("random.results")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.secondary)
                            .padding(.horizontal, 24)
                            .padding(.bottom, 8)
                        
                        ForEach(results) { song in
                            NavigationLink(destination: SongDetailView(song: song)) {
                                SongRowView(song: song)
                            }
                            .buttonStyle(.plain)
                            .transition(.asymmetric(
                                insertion: .move(edge: .bottom).combined(with: .opacity),
                                removal: .opacity
                            ))
                        }
                    }
                    .padding(.top, 8)
                    .padding(.bottom, 40)
                }
                .transition(.opacity.animation(.easeInOut(duration: 0.5)))
            } else {
                Spacer()
            }
        }
        .navigationTitle("random.title")
        .background(Color(.systemGroupedBackground))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showFilterSheet = true
                } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                        .foregroundStyle(filterSettings == FilterSettings() ? Color.primary : Color.blue)
                }
            }
        }
        .sheet(isPresented: $showFilterSheet) {
            FilterView(settings: $filterSettings, allCategories: allCategories, allVersions: allVersions)
        }
        .onChange(of: songCount) {
            resetSlots()
        }
    }
    
    private func resetSlots() {
        withAnimation(.easeOut(duration: 0.3)) {
            results = []
            displayedSongs = [[], [], [], []]
            spinOffsets = [0, 0, 0, 0]
        }
    }
    
    private func spin() {
        // 1. Apply Filters
        let filteredSongs = FilterUtils.filterSongs(allSongs, settings: filterSettings)
        
        guard !filteredSongs.isEmpty else { return }
        
        // Prepare haptics
        let feedbackGenerator = UIImpactFeedbackGenerator(style: .medium)
        feedbackGenerator.prepare()
        
        withAnimation(.easeOut(duration: 0.2)) {
            isSpinning = true
            results = []
        }
        
        // Pick from filtered pool
        var newResults: [Song] = []
        for _ in 0..<songCount {
            if let randomSong = filteredSongs.randomElement() {
                newResults.append(randomSong)
            }
        }
        pendingResults = newResults
        
        // Build the scrolling list for each slot
        for i in 0..<songCount {
            var columnSongs: [Song] = []
            for _ in 0..<20 {
                // Fillers can be from allSongs for variety, but the target should be from filteredSongs
                if let filler = allSongs.randomElement() {
                    columnSongs.append(filler)
                }
            }
            columnSongs.append(newResults[i])
            displayedSongs[i] = columnSongs
            spinOffsets[i] = 0
            
            // Animation for each column
            let columnDuration = 2.0 + Double(i) * 0.4
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: columnDuration)) {
                spinOffsets[i] = Double(-CGFloat(displayedSongs[i].count - 1) * currentSlotHeight)
            }
        }
        
        // Final completion logic
        let totalDuration = 2.0 + Double(songCount - 1) * 0.4
        
        currentSpinTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: UInt64(totalDuration * 1_000_000_000))
            
            if !Task.isCancelled {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                    results = pendingResults
                    isSpinning = false
                    feedbackGenerator.impactOccurred()
                }
            }
        }
    }
    
    private func skipSpin() {
        currentSpinTask?.cancel()
        currentSpinTask = nil
        
        // Snap everything to end
        withAnimation(.none) {
            for i in 0..<songCount {
                if i < displayedSongs.count && i < spinOffsets.count {
                    spinOffsets[i] = Double(-CGFloat(displayedSongs[i].count - 1) * currentSlotHeight)
                }
            }
        }
        
        withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
            results = pendingResults
            isSpinning = false
        }
        
        UIImpactFeedbackGenerator(style: .medium).impactOccurred()
    }
}

struct SlotColumn: View {
    let songs: [Song]
    let offset: Double
    let slotHeight: CGFloat
    let jacketSize: CGFloat // Now dynamic
    
    var body: some View {
        GeometryReader { _ in
            VStack(spacing: 0) {
                if songs.isEmpty {
                    VStack {
                        Image(systemName: "questionmark.circle.fill")
                            .font(.system(size: 40))
                            .foregroundStyle(.gray.opacity(0.3))
                        Text("random.ready")
                            .font(.caption.bold())
                            .foregroundStyle(.gray.opacity(0.4))
                    }
                    .frame(height: slotHeight)
                    .frame(maxWidth: .infinity)
                } else {
                    ForEach(0..<songs.count, id: \.self) { index in
                        ZStack {
                            SongJacketView(
                                imageName: songs[index].imageName,
                                size: jacketSize, // Applied dynamic size
                                cornerRadius: 12
                            )
                            .shadow(color: .black.opacity(0.15), radius: 4)
                        }
                        .frame(height: slotHeight)
                        .frame(maxWidth: .infinity)
                        .background(
                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.primary.opacity(0.02))
                                .padding(2)
                        )
                    }
                }
            }
            .offset(y: offset)
        }
        .clipped()
        .background(Color.primary.opacity(0.03))
        .cornerRadius(20)
    }
}
