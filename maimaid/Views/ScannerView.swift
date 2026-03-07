import SwiftUI
@preconcurrency import AVFoundation
import Vision
import SwiftData
import PhotosUI
import os

@MainActor
struct ScannerView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query private var songs: [Song]
    
    @State private var isShowingDetail = false
    @State private var isShowingScoreEntry = false
    
    @State private var recognizedSong: Song? = nil
    @State private var recognizedRate: Double? = nil
    @State private var recognizedDifficulty: String? = nil
    @State private var recognizedType: String? = nil
    @State private var recognizedDxScore: Int? = nil
    @State private var recognizedFC: String? = nil
    @State private var recognizedFS: String? = nil
    @State private var debugBoxes: [RecognizedBox] = []
    @AppStorage("showScannerBoundingBox") private var showScannerBoundingBox: Bool = false
    @State private var recognizedClass: MaimaiImageType = .unknown
    
    // Photo Capture States
    @State private var showFlashOverlay = false
    @State private var isSavingPhoto = false
    
    // Stabilization & Persistence
    @State private var isLocked = false
    @State private var lastSeenDate = Date()
    @State private var recognitionBuffer: [String: Int] = [:]
    
    // Recognition Stabilization
    @State private var rateBuffer: [Double] = []
    @State private var dxScoreBuffer: [Int] = []
    private let stabilizationThreshold = 3
    
    // Photo Import
    @State private var selectedPhotoItem: PhotosPickerItem? = nil
    @State private var isProcessingPhoto = false
    @State private var photoImportFeedback: String? = nil
    
    var body: some View {
        NavigationStack {
            ZStack {
                CameraPreviewView { image in
                    handleCameraFrame(image)
                }
                .ignoresSafeArea()
                
                debugOverlayView()
                
                // Flash Overlay (Top Most layer within ZStack behind overlay items)
                if showFlashOverlay {
                    Color.white
                        .ignoresSafeArea()
                        .zIndex(10)
                        .transition(.opacity)
                }
                
                // Overlay
                VStack {
                    headerView()
                    Spacer()
                    
                    if isProcessingPhoto {
                        HStack(spacing: 10) {
                            ProgressView()
                                .tint(.white)
                            Text("scanner.processing")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 8)
                    }
                    
                    // Photo import feedback
                    if let feedback = photoImportFeedback {
                        Text(feedback)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.orange)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(.ultraThinMaterial, in: Capsule())
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                            .padding(.bottom, 8)
                    }
                }
                
                VStack {
                    Spacer()
                    // Camera Shutter Button (only show when a score is recognized to take photo of)
                    if recognizedClass == .score {
                        Button(action: {
                            triggerPhotoCapture()
                        }) {
                            ZStack {
                                Circle()
                                    .stroke(.white, lineWidth: 3)
                                    .frame(width: 64, height: 64)
                                
                                Circle()
                                    .fill(isSavingPhoto ? .gray : .white)
                                    .frame(width: 54, height: 54)
                                
                                if isSavingPhoto {
                                    ProgressView()
                                        .tint(.white)
                                }
                            }
                        }
                        .disabled(isSavingPhoto)
                        .transition(.scale.combined(with: .opacity))
                        .padding(.bottom, 20)
                    }
                    resultView()
                }
            }
            .sheet(isPresented: $isShowingScoreEntry, onDismiss: {
                resetScanner()
            }) {
                scoreEntrySheetContent
            }
            .onChange(of: selectedPhotoItem) { oldItem, newItem in
                if let item = newItem {
                    Task {
                        await processSelectedPhoto(item)
                    }
                }
            }
        }
    }
    
    @ViewBuilder
    private var scoreEntrySheetContent: some View {
        if let song = recognizedSong {
            if let sheet = matchedSheet(for: song, diff: recognizedDifficulty ?? "master", type: recognizedType ?? "dx") {
                ScoreEntryView(
                    sheet: sheet,
                    initialRate: recognizedRate,
                    initialRank: RatingUtils.calculateRank(achievement: recognizedRate ?? 0),
                    initialDxScore: recognizedDxScore,
                    initialFC: recognizedFC,
                    initialFS: recognizedFS
                )
            } else if let fallbackSheet = song.sheets.first {
                ScoreEntryView(
                    sheet: fallbackSheet,
                    initialRate: recognizedRate,
                    initialRank: RatingUtils.calculateRank(achievement: recognizedRate ?? 0),
                    initialDxScore: recognizedDxScore,
                    initialFC: recognizedFC,
                    initialFS: recognizedFS
                )
            }
        }
    }
    
    // MARK: - Photo Processing
    
    private func processSelectedPhoto(_ item: PhotosPickerItem) async {
        isProcessingPhoto = true
        photoImportFeedback = nil
        
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else {
            isProcessingPhoto = false
            showFeedback(String(localized: "scanner.error.load"))
            return
        }
        
        let imageType = await MLDistinguishProcessor.shared.classify(image)
        
        if imageType == .choose {
            let recognition = await MLChooseProcessor.shared.process(image)
            
            var matchedSongs: [Song] = []
            var seenIds = Set<String>()
            
            var allCandidates = recognition.titleCandidates
            if let exactTitle = recognition.title {
                allCandidates.insert(exactTitle, at: 0)
            }
            
            for candidate in allCandidates {
                let matches = songs.filter { song in
                    // Requirement 1: Filter out songs where all standard (dx/std) sheets are disabled (deleted)
                    // We ignore 'utage' sheets here because a song might be deleted from regular play but keep its utage chart
                    let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
                    
                    // If a song has no standard sheets at all, or if all its standard sheets are region disabled
                    let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { sheet in
                        !sheet.regionJp && !sheet.regionIntl && !sheet.regionUsa && !sheet.regionCn
                    }
                    if isDeleted { return false }
                    
                    return song.title.localizedCaseInsensitiveContains(candidate) ||
                    candidate.localizedCaseInsensitiveContains(song.title) ||
                    (song.searchKeywords?.localizedCaseInsensitiveContains(candidate) ?? false) ||
                    song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(candidate) })
                }
                
                for song in matches {
                    if !seenIds.contains(song.songIdentifier) {
                        matchedSongs.append(song)
                        seenIds.insert(song.songIdentifier)
                    }
                }
            }
            
            // Helper for Levenshtein distance directly in the closure
            func levenshtein(_ a: String, _ b: String) -> Int {
                let a = Array(a.lowercased())
                let b = Array(b.lowercased())
                if a.isEmpty { return b.count }
                if b.isEmpty { return a.count }
                var dist = [[Int]](repeating: [Int](repeating: 0, count: b.count + 1), count: a.count + 1)
                for i in 0...a.count { dist[i][0] = i }
                for j in 0...b.count { dist[0][j] = j }
                for i in 1...a.count {
                    for j in 1...b.count {
                        if a[i - 1] == b[j - 1] {
                            dist[i][j] = dist[i - 1][j - 1]
                        } else {
                            dist[i][j] = min(dist[i - 1][j] + 1, dist[i][j - 1] + 1, dist[i - 1][j - 1] + 1)
                        }
                    }
                }
                return dist[a.count][b.count]
            }
            
            // Requirement 2: Enhanced sorting logic
            if let targetCandidate = allCandidates.first {
                matchedSongs.sort { a, b in
                    // Exact Title Match takes supreme priority
                    let aIsExact = a.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    let bIsExact = b.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    if aIsExact != bIsExact { return aIsExact }
                    
                    // Exact Alias Match takes secondary priority
                    let aAliasExact = a.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    let bAliasExact = b.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    if aAliasExact != bAliasExact { return aAliasExact }
                    
                    // Levenshtein distance (smaller is better)
                    let aDist = levenshtein(a.title, targetCandidate)
                    let bDist = levenshtein(b.title, targetCandidate)
                    if aDist != bDist { return aDist < bDist }
                    
                    // Fallback to title length (shorter is more exact)
                    return a.title.count < b.title.count
                }
            } else {
                matchedSongs.sort { a, b in
                    return a.title.count < b.title.count
                }
            }
            
            isProcessingPhoto = false
            
            if let firstMatch = matchedSongs.first {
                self.recognizedSong = firstMatch
                self.recognizedClass = .choose
                self.debugBoxes = recognition.boxes
                self.isLocked = true
                self.lastSeenDate = Date()
            } else {
                showFeedback(String(localized: "scanner.error.title"))
            }

        } else {
            let recognition = await MLScoreProcessor.shared.process(image)
            
            // Match title candidates against song database
            var matchedSongs: [Song] = []
            var seenIds = Set<String>()
            
            var allCandidates = recognition.titleCandidates
            if let exactTitle = recognition.title {
                allCandidates.insert(exactTitle, at: 0)
            }
            
            let inputDifficulty = recognition.difficulty ?? "master"
            
            for candidate in allCandidates {
                let matches = songs.filter { song in
                    // Requirement 1: Filter out songs where all standard (dx/std) sheets are disabled (deleted)
                    let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
                    
                    let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { sheet in
                        !sheet.regionJp && !sheet.regionIntl && !sheet.regionUsa && !sheet.regionCn
                    }
                    if isDeleted { return false }
                    
                    let hasDifficulty = song.sheets.contains { $0.difficulty.lowercased() == inputDifficulty.lowercased() }
                    if recognition.difficulty != nil && !hasDifficulty { return false }
                    
                    return song.title.localizedCaseInsensitiveContains(candidate) ||
                    candidate.localizedCaseInsensitiveContains(song.title) ||
                    (song.searchKeywords?.localizedCaseInsensitiveContains(candidate) ?? false) ||
                    song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(candidate) })
                }
                
                for song in matches {
                    if !seenIds.contains(song.songIdentifier) {
                        matchedSongs.append(song)
                        seenIds.insert(song.songIdentifier)
                    }
                }
            }
            
            // Helper for Levenshtein distance directly in the closure
            func levenshtein(_ a: String, _ b: String) -> Int {
                let a = Array(a.lowercased())
                let b = Array(b.lowercased())
                if a.isEmpty { return b.count }
                if b.isEmpty { return a.count }
                var dist = [[Int]](repeating: [Int](repeating: 0, count: b.count + 1), count: a.count + 1)
                for i in 0...a.count { dist[i][0] = i }
                for j in 0...b.count { dist[0][j] = j }
                for i in 1...a.count {
                    for j in 1...b.count {
                        if a[i - 1] == b[j - 1] {
                            dist[i][j] = dist[i - 1][j - 1]
                        } else {
                            dist[i][j] = min(dist[i - 1][j] + 1, dist[i][j - 1] + 1, dist[i - 1][j - 1] + 1)
                        }
                    }
                }
                return dist[a.count][b.count]
            }
            
            // Requirement 2: Enhanced sorting logic
            if let targetCandidate = allCandidates.first {
                matchedSongs.sort { a, b in
                    // Exact Title Match takes supreme priority
                    let aIsExact = a.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    let bIsExact = b.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    if aIsExact != bIsExact { return aIsExact }
                    
                    // Exact Alias Match takes secondary priority
                    let aAliasExact = a.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    let bAliasExact = b.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    if aAliasExact != bAliasExact { return aAliasExact }
                    
                    // Levenshtein distance (smaller is better)
                    let aDist = levenshtein(a.title, targetCandidate)
                    let bDist = levenshtein(b.title, targetCandidate)
                    if aDist != bDist { return aDist < bDist }
                    
                    // Fallback to title length (shorter is more exact)
                    return a.title.count < b.title.count
                }
            } else {
                matchedSongs.sort { a, b in
                    return a.title.count < b.title.count
                }
            }
            
            isProcessingPhoto = false
            
            if let firstMatch = matchedSongs.first {
                self.recognizedSong = firstMatch
                self.recognizedClass = .score
                self.recognizedRate = recognition.rate
                self.recognizedDifficulty = recognition.difficulty
                self.recognizedType = recognition.type
                self.recognizedDxScore = recognition.dxScore
                self.recognizedFC = recognition.comboStatus
                self.recognizedFS = recognition.syncStatus
                self.debugBoxes = recognition.boxes
                self.isLocked = true
                self.lastSeenDate = Date()
            } else {
                showFeedback(String(localized: "scanner.error.title"))
            }
        }
    }
    
    
    private func showFeedback(_ message: String) {
        withAnimation { photoImportFeedback = message }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            withAnimation { photoImportFeedback = nil }
        }
    }
    
    // MARK: - Header
    
    @ViewBuilder
    private func headerView() -> some View {
        HStack(spacing: 16) {
            Spacer()
            
            // Photo picker button
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                Image(systemName: "photo.on.rectangle.angled")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundColor(.white)
                    .padding(10)
                    .background(.ultraThinMaterial, in: Circle())
            }
        }
        .padding()
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("ScannerPhotoCaptured"))) { notification in
            if let image = notification.object as? UIImage {
                handleCapturedScannerPhoto(image)
            }
        }
    }
    
    private func triggerPhotoCapture() {
        guard !isSavingPhoto else { return }
        let generator = UIImpactFeedbackGenerator(style: .rigid)
        generator.impactOccurred()
        
        // Flash effect
        withAnimation(.easeOut(duration: 0.1)) {
            showFlashOverlay = true
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) {
            withAnimation(.easeIn(duration: 0.2)) {
                self.showFlashOverlay = false
            }
        }
        
        isSavingPhoto = true
        NotificationCenter.default.post(name: NSNotification.Name("TakeScannerPhoto"), object: nil)
    }
    
    private func handleCapturedScannerPhoto(_ image: UIImage) {
        let title = recognizedSong?.title
        var tags: [String] = []
        
        if let t = title { tags.append(t) }
        
        if let song = recognizedSong, let diff = recognizedDifficulty {
            let type = recognizedType ?? "dx"
            if let sheet = matchedSheet(for: song, diff: diff, type: type) {
                tags.append("LV\(sheet.level)")
            }
            tags.append(diff.uppercased())
            tags.append(type.uppercased())
        }
        
        if let rate = recognizedRate {
            let rank = RatingUtils.calculateRank(achievement: rate)
            tags.append(rank)
        }
        
        Task {
            do {
                try await PhotoService.shared.saveImageWithMetadata(image, title: title, tags: tags)
                await MainActor.run {
                    self.isSavingPhoto = false
                    showFeedback(String(localized: "scanner.photo.saved"))
                }
            } catch {
                await MainActor.run {
                    self.isSavingPhoto = false
                    showFeedback(String(localized: "scanner.photo.error"))
                }
                print("Scanner photo save error: \(error)")
            }
        }
    }
    
    @ViewBuilder
    private func debugOverlayView() -> some View {
        ScannerDebugOverlayView(
            showScannerBoundingBox: showScannerBoundingBox,
            debugBoxes: debugBoxes
        )
    }
    
    @ViewBuilder
    private func resultView() -> some View {
        if let song = recognizedSong {
            ScannerResultCardView(
                song: song,
                recognizedClass: recognizedClass,
                recognizedType: recognizedType,
                recognizedDifficulty: recognizedDifficulty,
                recognizedRate: recognizedRate,
                onScoreEntryTap: { isShowingScoreEntry = true },
                onResetTap: { resetScanner() }
            )
            .equatable()
        }
    }
    
    // MARK: - Disambiguation Sheets
    

    private func handleCameraFrame(_ image: UIImage) {
        guard !isShowingScoreEntry else { return }
        Task {
            let imageType = await MLDistinguishProcessor.shared.classify(image)
            
            if imageType == .unknown {
                updateUIWithResults(songIds: [], rate: nil, diff: nil, type: nil, dxScore: nil, fc: nil, fs: nil, boxes: [], imageClass: .unknown)
                return
            }
            
            if imageType == .choose {
                let recognition = await MLChooseProcessor.shared.process(image)
                
                var frameMatches: [String] = []
                
                var allCandidates = recognition.titleCandidates
                if let exactTitle = recognition.title {
                    allCandidates.insert(exactTitle, at: 0)
                }
                
                for candidate in allCandidates {
                    let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard cleaned.count >= 2 else { continue }
                    
                    var foundFast = false
                    for song in songs {
                        if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) {
                            frameMatches.append(song.songIdentifier)
                            foundFast = true
                            if frameMatches.count > 3 { break }
                        }
                    }
                    
                    if !foundFast && cleaned.count > 4 {
                        for song in songs {
                            if fuzzyMatch(cleaned, song.title) {
                                frameMatches.append(song.songIdentifier)
                                if frameMatches.count > 3 { break }
                            }
                        }
                    }
                    if !frameMatches.isEmpty { break }
                }
                
                updateUIWithResults(songIds: frameMatches, rate: nil, diff: nil, type: nil, dxScore: nil, fc: nil, fs: nil, boxes: recognition.boxes, imageClass: .choose)
            } else {
                let recognition = await MLScoreProcessor.shared.process(image)
                
                var frameMatches: [String] = []
                
                var allCandidates = recognition.titleCandidates
                if let exactTitle = recognition.title {
                    allCandidates.insert(exactTitle, at: 0)
                }
                
                let inputDifficulty = recognition.difficulty ?? "master"
                
                for candidate in allCandidates {
                    let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard cleaned.count >= 2 else { continue }
                    
                    var foundFast = false
                    for song in songs {
                        let hasDifficulty = song.sheets.contains { $0.difficulty.lowercased() == inputDifficulty.lowercased() }
                        if recognition.difficulty != nil && !hasDifficulty { continue }
                        
                        if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) {
                            frameMatches.append(song.songIdentifier)
                            foundFast = true
                            if frameMatches.count > 3 { break }
                        }
                    }
                    
                    if !foundFast && cleaned.count > 4 {
                        for song in songs {
                            let hasDifficulty = song.sheets.contains { $0.difficulty.lowercased() == inputDifficulty.lowercased() }
                            if recognition.difficulty != nil && !hasDifficulty { continue }
                            
                            if fuzzyMatch(cleaned, song.title) {
                                frameMatches.append(song.songIdentifier)
                                if frameMatches.count > 3 { break }
                            }
                        }
                    }
                    if !frameMatches.isEmpty { break }
                }
                
                updateUIWithResults(songIds: frameMatches, rate: recognition.rate as Double?, diff: recognition.difficulty, type: recognition.type, dxScore: recognition.dxScore, fc: recognition.comboStatus, fs: recognition.syncStatus, boxes: recognition.boxes, imageClass: .score)
            }
        }
    }
    
    private func updateUIWithResults(songIds: [String], rate: Double?, diff: String?, type: String?, dxScore: Int?, fc: String?, fs: String?, boxes: [RecognizedBox], imageClass: MaimaiImageType) {
        self.debugBoxes = boxes
        
        for id in recognitionBuffer.keys {
            recognitionBuffer[id, default: 0] -= 1
            if recognitionBuffer[id]! <= 0 { recognitionBuffer.removeValue(forKey: id) }
        }
        
        for id in songIds {
            recognitionBuffer[id, default: 0] += 6
            // Cap the buffer value so it doesn't grow infinitely, allowing fast switching
            if recognitionBuffer[id]! > 18 {
                recognitionBuffer[id] = 18
            }
        }
        
        if let topCandidate = recognitionBuffer.max(by: { $0.value < $1.value }), topCandidate.value > 15 {
            if let song = songs.first(where: { $0.songIdentifier == topCandidate.key }) {
                let newClass = songIds.contains(song.songIdentifier) ? imageClass : recognizedClass
                
                if recognizedSong?.songIdentifier != song.songIdentifier || recognizedClass != newClass {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        self.recognizedSong = song
                        if newClass != .unknown {
                            self.recognizedClass = newClass
                        }
                        self.isLocked = true
                    }
                }
                self.lastSeenDate = Date()
            }
        }
        
        if isLocked {
            if let r = rate {
                rateBuffer.append(r)
                if rateBuffer.count > 5 { rateBuffer.removeFirst() }
                
                // Find most frequent rate in buffer
                let counts = rateBuffer.reduce(into: [:]) { counts, value in counts[value, default: 0] += 1 }
                if let (mostFrequentRate, count) = counts.max(by: { $0.value < $1.value }), count >= stabilizationThreshold {
                    self.recognizedRate = mostFrequentRate
                } else if rateBuffer.count < stabilizationThreshold {
                    // Always show the first few reads so the screen isn't completely blank
                    self.recognizedRate = rateBuffer.last
                }
            }
            if let d = diff { self.recognizedDifficulty = d }
            if let t = type { self.recognizedType = t }
            
            if let dx = dxScore {
                dxScoreBuffer.append(dx)
                if dxScoreBuffer.count > 5 { dxScoreBuffer.removeFirst() }
                
                let counts = dxScoreBuffer.reduce(into: [:]) { counts, value in counts[value, default: 0] += 1 }
                if let (mostFrequentScore, count) = counts.max(by: { $0.value < $1.value }), count >= stabilizationThreshold {
                    self.recognizedDxScore = mostFrequentScore
                } else if dxScoreBuffer.count < stabilizationThreshold {
                    self.recognizedDxScore = dxScoreBuffer.last
                }
            }
            
            if let f = fc { self.recognizedFC = f }
            if let s = fs { self.recognizedFS = s }
        }
        
        if isLocked && !isShowingScoreEntry {
            if Date().timeIntervalSince(lastSeenDate) > 4.0 {
                withAnimation {
                    resetScanner()
                }
            }
        }
    }
    
    private func resetScanner() {
        self.recognizedSong = nil
        self.recognizedRate = nil
        self.recognizedDifficulty = nil
        self.recognizedType = nil
        self.recognizedDxScore = nil
        self.recognizedFC = nil
        self.recognizedFS = nil
        self.recognizedClass = .unknown
        self.recognitionBuffer.removeAll()
        self.rateBuffer.removeAll()
        self.dxScoreBuffer.removeAll()
        self.debugBoxes.removeAll()
        self.isLocked = false
    }
    
    private func fuzzyMatch(_ s1: String, _ s2: String) -> Bool {
        let t1 = s1.lowercased().filter { !$0.isWhitespace }
        let t2 = s2.lowercased().filter { !$0.isWhitespace }
        if abs(t1.count - t2.count) > 2 { return false }
        let dist = levenshteinDistance(t1, t2)
        return dist <= max(1, t1.count / 4)
    }
    
    private func levenshteinDistance(_ s1: String, _ s2: String) -> Int {
        let empty = [Int](repeating: 0, count: s2.count + 1)
        var last = [Int](0...s2.count)
        for (i, char1) in s1.enumerated() {
            var cur = [i + 1] + empty.dropFirst()
            for (j, char2) in s2.enumerated() {
                cur[j + 1] = char1 == char2 ? last[j] : min(last[j], last[j + 1], cur[j]) + 1
            }
            last = cur
        }
        return last.last!
    }
    
    private func matchedSheet(for song: Song, diff: String, type: String) -> Sheet? {
        if let sheet = song.sheets.first(where: { $0.difficulty.lowercased() == diff.lowercased() && $0.type.lowercased() == type.lowercased() }) {
            return sheet
        }
        return song.sheets.first { $0.difficulty.lowercased() == diff.lowercased() }
    }
    
}

struct CameraPreviewView: UIViewControllerRepresentable {
    var onImageCaptured: (UIImage) -> Void
    func makeUIViewController(context: Context) -> CameraViewController {
        let controller = CameraViewController()
        controller.onImageCaptured = onImageCaptured
        return controller
    }
    func updateUIViewController(_ uiViewController: CameraViewController, context: Context) {}
}

// CameraViewController runs captureOutput on background queue, so we manage thread-safety explicitly
class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onImageCaptured: ((UIImage) -> Void)?
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var photoOutput: AVCapturePhotoOutput?
    private let processingQueue = DispatchQueue(label: "com.maimaid.camera.queue", qos: .userInteractive)
    
    // Thread-safe counter using OSAllocatedUnfairLock
    private let frameCounter = OSAllocatedUnfairLock(initialState: 0)
    
    // CIContext is thread-safe, mark as nonisolated to allow access from background queue
    nonisolated private static let rawContext = CIContext(options: [.useSoftwareRenderer: false])
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupCaptureSession()
        
        NotificationCenter.default.addObserver(self, selector: #selector(takePhoto), name: Notification.Name("TakeScannerPhoto"), object: nil)
    }
    
    deinit {
        NotificationCenter.default.removeObserver(self)
    }
    
    private func setupCaptureSession() {
        captureSession = AVCaptureSession()
        guard let captureSession = captureSession else { return }
        captureSession.sessionPreset = .hd1280x720
        guard let videoDevice = AVCaptureDevice.default(.builtInWideAngleCamera, for: .video, position: .back),
              let videoInput = try? AVCaptureDeviceInput(device: videoDevice) else { return }
        if captureSession.canAddInput(videoInput) { captureSession.addInput(videoInput) }
        let videoOutput = AVCaptureVideoDataOutput()
        videoOutput.alwaysDiscardsLateVideoFrames = true
        videoOutput.setSampleBufferDelegate(self, queue: processingQueue)
        if captureSession.canAddOutput(videoOutput) { captureSession.addOutput(videoOutput) }
        
        let photoOut = AVCapturePhotoOutput()
        if captureSession.canAddOutput(photoOut) {
            captureSession.addOutput(photoOut)
            if #available(iOS 16.0, *) {
                photoOut.maxPhotoDimensions = videoDevice.activeFormat.supportedMaxPhotoDimensions.last ?? CMVideoDimensions(width: 0, height: 0)
            } else {
                photoOut.isHighResolutionCaptureEnabled = true
            }
            self.photoOutput = photoOut
        }
        
        if let connection = videoOutput.connection(with: .video) {
            if #available(iOS 17.0, *) {
                if connection.isVideoRotationAngleSupported(90) {
                    connection.videoRotationAngle = 90
                }
            } else {
                if connection.isVideoOrientationSupported {
                    connection.videoOrientation = .portrait
                }
            }
        }
        
        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer?.frame = view.layer.bounds
        previewLayer?.videoGravity = .resizeAspectFill
        if let previewLayer = previewLayer { view.layer.addSublayer(previewLayer) }
        DispatchQueue.global(qos: .userInitiated).async { captureSession.startRunning() }
    }
    
    // Explicitly nonisolated since this is called on processingQueue (background thread)
    nonisolated func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        // Thread-safe increment
        let count = frameCounter.withLock { value -> Int in
            value += 1
            return value
        }
        
        // Process 1 frame per ~10 calls to reduce frequency (approx 3 fps instead of 6 fps)
        guard count % 10 == 0 else { return }
        
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = Self.rawContext.createCGImage(ciImage, from: ciImage.extent) else { return }
        let uiImage = UIImage(cgImage: cgImage)
        
        DispatchQueue.main.async {
            self.onImageCaptured?(uiImage)
        }
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }
    
    override func viewWillAppear(_ animated: Bool) {
        super.viewWillAppear(animated)
        if let captureSession, !captureSession.isRunning {
            DispatchQueue.global(qos: .userInitiated).async {
                captureSession.startRunning()
            }
        }
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        captureSession?.stopRunning()
    }
}

struct ScannerDebugOverlayView: View {
    let showScannerBoundingBox: Bool
    let debugBoxes: [RecognizedBox]
    
    var body: some View {
        if showScannerBoundingBox {
            GeometryReader { geo in
                ZStack(alignment: .topLeading) {
                    ForEach(debugBoxes.indices, id: \.self) { i in
                        let box = debugBoxes[i]
                        let rect = box.rect
                        
                        let x = rect.origin.x * geo.size.width
                        let y = (1 - rect.origin.y - rect.height) * geo.size.height
                        let w = rect.width * geo.size.width
                        let h = rect.height * geo.size.height
                        
                        Path { path in
                            path.addRect(CGRect(x: x, y: y, width: w, height: h))
                        }
                        .stroke(Color.green, lineWidth: 2)
                        
                        Text(box.label)
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 2)
                            .background(Color.green)
                            .position(x: x + w / 2, y: max(10, y - 8))
                    }
                }
            }
            .allowsHitTesting(false)
            .ignoresSafeArea()
        }
    }
}

struct ScannerResultCardView: View, Equatable {
    let song: Song
    let recognizedClass: MaimaiImageType
    let recognizedType: String?
    let recognizedDifficulty: String?
    let recognizedRate: Double?
    let onScoreEntryTap: () -> Void
    let onResetTap: () -> Void
    
    static func == (lhs: ScannerResultCardView, rhs: ScannerResultCardView) -> Bool {
        lhs.song.songIdentifier == rhs.song.songIdentifier &&
        lhs.recognizedClass == rhs.recognizedClass &&
        lhs.recognizedType == rhs.recognizedType &&
        lhs.recognizedDifficulty == rhs.recognizedDifficulty &&
        lhs.recognizedRate == rhs.recognizedRate
    }
    
    var body: some View {
        if recognizedClass == .choose {
            NavigationLink(destination: {
                SongDetailView(song: song)
                    .onDisappear { onResetTap() }
            }) {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        SongJacketView(imageName: song.imageName, size: 40, cornerRadius: 8)
                            .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                        
                        VStack(alignment: .leading, spacing: 3) {
                            Text(song.title)
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.primary)
                                .lineLimit(1)
                            
                            Text(song.artist)
                                .font(.system(size: 11, weight: .regular))
                                .foregroundColor(.secondary)
                                .lineLimit(1)
                        }
                        
                        Spacer()
                        
                        Image(systemName: "chevron.right")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(.secondary.opacity(0.4))
                    }
                    .padding(.vertical, 14)
                    .padding(.horizontal, 16)
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(Color.primary.opacity(0.1), lineWidth: 1)
                )
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .buttonStyle(.plain)
            .transition(.asymmetric(
                insertion: .move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.9)),
                removal: .opacity.combined(with: .scale(scale: 0.95))
            ))
        } else {
            Button {
                onScoreEntryTap()
            } label: {
                let chartType = recognizedType ?? "dx"
                let diff = recognizedDifficulty ?? "master"
                let diffColor = ThemeUtils.colorForDifficulty(diff, chartType)
                
                let sheet = song.sheets.first(where: { $0.difficulty.lowercased() == diff.lowercased() && $0.type.lowercased() == chartType.lowercased() })
                
                VStack(spacing: 0) {
                    HStack(spacing: 0) {
                        RoundedRectangle(cornerRadius: 2)
                            .fill(diffColor)
                            .frame(width: 4)
                            .padding(.vertical, 4)
                        
                        HStack(spacing: 12) {
                            SongJacketView(imageName: song.imageName, size: 40, cornerRadius: 8)
                                .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                            
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 4) {
                                    Text(chartType.uppercased() == "STD" ? String(localized: "scanner.chart.std") : chartType.uppercased())
                                        .font(.system(size: 8, weight: .black))
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(chartType.lowercased() == "dx" ? Color.orange : Color.blue)
                                        .foregroundColor(.white)
                                        .cornerRadius(3)
                                    
                                    Text(song.title)
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.primary)
                                        .lineLimit(1)
                                }
                                
                                if diff.lowercased() == "remaster" {
                                    Text("RE: MASTER")
                                        .font(.system(size: 13, weight: .bold, design: .rounded))
                                        .foregroundColor(diffColor)
                                } else {
                                    Text(diff.uppercased())
                                        .font(.system(size: 13, weight: .bold, design: .rounded))
                                        .foregroundColor(diffColor)
                                }
                            }
                            
                            Spacer()
                            
                            if let rate = recognizedRate {
                                VStack(alignment: .trailing, spacing: 1) {
                                    Text(String(format: "%.4f%%", rate))
                                        .font(.system(size: 12, weight: .bold, design: .monospaced))
                                        .foregroundColor(.primary)
                                    Text(RatingUtils.calculateRank(achievement: rate))
                                        .font(.system(size: 10, weight: .black, design: .rounded))
                                        .foregroundColor(diffColor)
                                }
                            }
                            
                            if let levelStr = sheet?.internalLevel ?? sheet?.level {
                                Text(levelStr)
                                    .font(.system(size: 28, weight: .black, design: .rounded))
                                    .foregroundColor(diffColor.opacity(0.85))
                                    .frame(minWidth: 44)
                            }
                            
                            Image(systemName: "chevron.right")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(.secondary.opacity(0.4))
                        }
                        .padding(.leading, 12)
                        .padding(.trailing, 16)
                    }
                    .padding(.vertical, 14)
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .strokeBorder(diffColor.opacity(0.2), lineWidth: 1)
                )
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .buttonStyle(.plain)
            .transition(.asymmetric(
                insertion: .move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.9)),
                removal: .opacity.combined(with: .scale(scale: 0.95))
            ))
        }
    }
}

extension CameraViewController: AVCapturePhotoCaptureDelegate {
    @objc private func takePhoto() {
        guard let output = photoOutput else { return }
        
        // Settings must be recreated for each capture
        let settings = AVCapturePhotoSettings()
        if let videoConnection = output.connection(with: .video) {
            // Apply current screen orientation to the photo output connection
            if #available(iOS 17.0, *) {
                if videoConnection.isVideoRotationAngleSupported(90) {
                    videoConnection.videoRotationAngle = 90
                }
            } else {
                if videoConnection.isVideoOrientationSupported {
                    videoConnection.videoOrientation = .portrait
                }
            }
        }
        
        if output.availablePhotoCodecTypes.contains(.jpeg) {
            if #available(iOS 16.0, *) {
                settings.maxPhotoDimensions = output.maxPhotoDimensions
            } else {
                settings.isHighResolutionPhotoEnabled = true
            }
        }
        
        output.capturePhoto(with: settings, delegate: self)
    }
    
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else { return }
        
        DispatchQueue.main.async {
            NotificationCenter.default.post(name: NSNotification.Name("ScannerPhotoCaptured"), object: image)
        }
    }
}
