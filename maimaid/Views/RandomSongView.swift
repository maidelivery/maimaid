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
                Picker("数量", selection: $songCount) {
                    Text("一次 3 首").tag(3)
                    Text("一次 4 首").tag(4)
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
                .frame(height: currentSlotHeight)
                .padding(16)
                .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 24))
                .padding(.horizontal, 16)
                
                // Spin Button
                Button(action: spin) {
                    HStack {
                        Image(systemName: isSpinning ? "arrow.clockwise" : "dice.fill")
                        Text(isSpinning ? "正在抽取" : "立刻随机抽取")
                            .font(.headline.bold())
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 8)
                }
                .buttonStyle(.borderedProminent)
                .disabled(isSpinning || allSongs.isEmpty)
                .padding(.horizontal, 40)
            }
            .padding(.bottom, 24)
            .background(Color(.systemGroupedBackground))
            
            // Results Section
            if !isSpinning && !results.isEmpty {
                ScrollView {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("抽选结果")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.secondary)
                            .padding(.horizontal, 24)
                            .padding(.bottom, 8)
                        
                        ForEach(Array(results.enumerated()), id: \.element.id) { index, song in
                            NavigationLink(destination: SongDetailView(song: song)) {
                                HStack(spacing: 16) {
                                    SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: 52, cornerRadius: 10)
                                    
                                    VStack(alignment: .leading, spacing: 3) {
                                        Text(song.title)
                                            .font(.system(size: 16, weight: .semibold))
                                            .foregroundColor(.primary)
                                        Text(song.artist)
                                            .font(.system(size: 12))
                                            .foregroundColor(.secondary)
                                            .lineLimit(1)
                                    }
                                    Spacer()
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.secondary.opacity(0.3))
                                }
                                .padding(.vertical, 8)
                                .padding(.horizontal, 24)
                                .background(Color(.systemBackground).opacity(0.001))
                            }
                            .buttonStyle(.plain)
                            .transition(.asymmetric(
                                insertion: .move(edge: .bottom).combined(with: .opacity),
                                removal: .opacity
                            ))
                            
                            if song.id != results.last?.id {
                                Divider()
                                    .padding(.leading, 92)
                            }
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
        .navigationTitle("随机歌曲")
        .background(Color(.systemGroupedBackground))
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    showFilterSheet = true
                } label: {
                    Image(systemName: "line.3.horizontal.decrease.circle")
                        .foregroundColor(filterSettings == FilterSettings() ? Color.primary : Color.blue)
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
        
        // Haptic Feedback Generator
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
            // Mechanical feel: decelerates and stops sharply without oscillation
            withAnimation(.timingCurve(0.4, 0, 0.2, 1, duration: columnDuration)) {
                spinOffsets[i] = Double(-CGFloat(displayedSongs[i].count - 1) * currentSlotHeight)
            }
            
            // Trigger haptic exactly when the column stops
            DispatchQueue.main.asyncAfter(deadline: .now() + columnDuration) {
                feedbackGenerator.impactOccurred()
            }
        }
        
        // Final completion logic
        let totalDuration = 2.0 + Double(songCount - 1) * 0.4
        DispatchQueue.main.asyncAfter(deadline: .now() + totalDuration + 0.1) {
            withAnimation(.spring(response: 0.4, dampingFraction: 0.8)) {
                results = newResults
                isSpinning = false
            }
        }
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
                            .foregroundColor(.gray.opacity(0.3))
                        Text("Ready?")
                            .font(.caption.bold())
                            .foregroundColor(.gray.opacity(0.4))
                    }
                    .frame(height: slotHeight)
                    .frame(maxWidth: .infinity)
                } else {
                    ForEach(0..<songs.count, id: \.self) { index in
                        ZStack {
                            SongJacketView(
                                imageName: songs[index].imageName,
                                remoteUrl: songs[index].imageUrl,
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
