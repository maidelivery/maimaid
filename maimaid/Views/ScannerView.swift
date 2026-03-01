import SwiftUI
import AVFoundation
import Vision
import SwiftData
import PhotosUI

struct ScannerView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query private var songs: [Song]
    
    @State private var recognizedSong: Song? = nil
    @State private var recognizedRate: Double? = nil
    @State private var recognizedDifficulty: String? = nil
    @State private var recognizedType: String? = nil
    
    @State private var recognizedDxScore: Int? = nil
    
    @State private var recognizedFC: String? = nil
    @State private var recognizedFS: String? = nil
    
    @State private var debugBoxes: [RecognizedBox] = []
    
    // Disambiguation
    @State private var songCandidates: [Song] = []
    @State private var rateCandidates: [Double] = []
    @State private var showSongSelection = false
    @State private var showRateSelection = false
    
    @State private var isShowingDetail = false
    @State private var showScoreImportConfirmation = false
    @State private var isShowingScoreEntry = false
    
    // Stabilization & Persistence
    @State private var isLocked = false
    @State private var lastSeenDate = Date()
    
    // Background Processing
    private let processingQueue = DispatchQueue(label: "com.maimaid.scanner.processing", qos: .userInitiated)
    @State private var recognitionBuffer: [String: Int] = [:]
    
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
                
                // Overlay
                VStack {
                    headerView()
                    Spacer()
                    
                    // Photo processing indicator
                    if isProcessingPhoto {
                        HStack(spacing: 10) {
                            ProgressView()
                                .tint(.white)
                            Text("正在识别照片...")
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
                    
                    resultView()
                }
            }
            .navigationDestination(isPresented: $isShowingDetail) {
                if let song = recognizedSong {
                    SongDetailView(song: song)
                }
            }
            .alert("导入成绩", isPresented: $showScoreImportConfirmation) {
                Button("导入", role: .none) { executeImport() }
                Button("取消", role: .cancel) { isLocked = false }
            } message: {
                if let song = recognizedSong, let rate = recognizedRate {
                    Text("将 \(String(format: "%.4f", rate))% 导入到「\(song.title)」？")
                }
            }
            .sheet(isPresented: $isShowingScoreEntry, onDismiss: {
                resetScanner()
            }) {
                if let song = recognizedSong, let sheet = matchedSheet(for: song, diff: recognizedDifficulty ?? "master", type: recognizedType ?? "dx") {
                    ScoreEntryView(
                        sheet: sheet,
                        initialRate: recognizedRate,
                        initialRank: RatingUtils.calculateRank(achievement: recognizedRate ?? 0),
                        initialDxScore: recognizedDxScore,
                        initialFC: recognizedFC,
                        initialFS: recognizedFS
                    )
                } else if let song = recognizedSong, let fallbackSheet = song.sheets.first {
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
            .onChange(of: selectedPhotoItem) { _, newValue in
                guard let newValue else { return }
                Task {
                    await processSelectedPhoto(newValue)
                }
            }
            .sheet(isPresented: $showSongSelection) {
                songSelectionSheet
            }
            .sheet(isPresented: $showRateSelection) {
                rateSelectionSheet
            }
        }
    }
    
    // MARK: - Photo Processing
    
    private func processSelectedPhoto(_ item: PhotosPickerItem) async {
        await MainActor.run {
            isProcessingPhoto = true
            photoImportFeedback = nil
            songCandidates = []
            rateCandidates = []
        }
        
        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else {
            await MainActor.run {
                isProcessingPhoto = false
                showFeedback("无法加载图片")
            }
            return
        }
        
        // Use MLScoreProcessor for object detection and OCR
        let recognition = await MLScoreProcessor.shared.process(image)
        
        // Match title candidates against song database
        var matchedSongs: [Song] = []
        var seenIds = Set<String>()
        
        // Try all candidates collected by the processor
        var allCandidates = recognition.titleCandidates
        if let exactTitle = recognition.title {
            allCandidates.insert(exactTitle, at: 0)
        }
        
        let inputDifficulty = recognition.difficulty ?? "master"
        
        for candidate in allCandidates {
            let matches = songs.filter { song in
                let hasDifficulty = song.sheets.contains { $0.difficulty.lowercased() == inputDifficulty.lowercased() }
                if recognition.difficulty != nil && !hasDifficulty { return false }
                
                return song.title.localizedCaseInsensitiveContains(candidate) ||
                candidate.localizedCaseInsensitiveContains(song.title) ||
                (song.searchKeywords?.localizedCaseInsensitiveContains(candidate) ?? false)
            }
            
            for song in matches {
                if !seenIds.contains(song.songId) {
                    matchedSongs.append(song)
                    seenIds.insert(song.songId)
                }
            }
        }
        
        // Sort matches: Exact matches first, then by title similarity or length
        matchedSongs.sort { a, b in
            let aIsExact = allCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(a.title) == .orderedSame })
            let bIsExact = allCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(b.title) == .orderedSame })
            if aIsExact != bIsExact { return aIsExact }
            return a.title.count > b.title.count // Favor longer (more specific) titles
        }
        
        await MainActor.run {
            isProcessingPhoto = false
            
            // Set results
            self.rateCandidates = recognition.achievementRate != nil ? [recognition.achievementRate!] : []
            if let dxScore = recognition.dxScore { self.recognizedDxScore = dxScore }
            self.songCandidates = matchedSongs
            self.recognizedDifficulty = recognition.difficulty
            self.recognizedType = recognition.type
            self.recognizedRate = recognition.achievementRate
            self.recognizedFC = recognition.comboStatus
            self.recognizedFS = recognition.syncStatus
            self.debugBoxes = recognition.boxes
            
            // Logic for auto-selecting or prompting
            if matchedSongs.isEmpty {
                showFeedback("未能识别到歌曲标题")
            } else if matchedSongs.count > 1 {
                // If we have an "exact" match and others are just substrings, auto-select exact
                let exactMatched = matchedSongs.first { song in
                    allCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(song.title) == .orderedSame })
                }
                
                if let exact = exactMatched {
                    selectSong(exact)
                } else {
                    showSongSelection = true
                }
            } else {
                selectSong(matchedSongs[0])
            }
            
            self.isLocked = true
            self.lastSeenDate = Date()
        }
    }
    
    private func selectSong(_ song: Song) {
        recognizedSong = song
        isLocked = true
        lastSeenDate = Date()
    }
    
    private func showFeedback(_ message: String) {
        withAnimation { photoImportFeedback = message }
        DispatchQueue.main.asyncAfter(deadline: .now() + 2.5) {
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
    }
    
    @ViewBuilder
    private func debugOverlayView() -> some View {
        GeometryReader { geo in
            ZStack(alignment: .topLeading) {
                ForEach(debugBoxes.indices, id: \.self) { i in
                    let box = debugBoxes[i]
                    let rect = box.rect
                    
                    // CoreML / Vision returns normalized coordinates where (0,0) is bottom-left
                    // CameraFeed is AspectFill, which means the sides might be cropped.
                    // To simply draw them, we convert from Vision (bottom-left) to UIKit (top-left)
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
    
    @ViewBuilder
    private func resultView() -> some View {
        if let song = recognizedSong {
            Button {
                isShowingScoreEntry = true
            } label: {
                let diff = recognizedDifficulty ?? "master"
                let diffColor = colorForDifficulty(diff)
                let type = recognizedType ?? "dx"
                
                // Find matching sheet if possible to show level
                let sheet = song.sheets.first(where: { $0.difficulty.lowercased() == diff.lowercased() && $0.type.lowercased() == type.lowercased() })
                
                VStack(spacing: 0) {
                    HStack(spacing: 0) {
                        // Difficulty accent bar
                        RoundedRectangle(cornerRadius: 2)
                            .fill(diffColor)
                            .frame(width: 4)
                            .padding(.vertical, 4)
                        
                        HStack(spacing: 12) {
                            // Jacket
                            SongJacketView(imageName: song.imageName, size: 40, cornerRadius: 8)
                                .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                            
                            // Info
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 4) {
                                    Text(type.uppercased() == "STD" ? "标准" : type.uppercased())
                                        .font(.system(size: 8, weight: .black))
                                        .padding(.horizontal, 4)
                                        .padding(.vertical, 1)
                                        .background(type.lowercased() == "dx" ? Color.orange : Color.blue)
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
                            
                            // Score info
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
                            
                            // Level
                            if let levelStr = sheet?.internalLevel ?? sheet?.level {
                                Text(levelStr)
                                    .font(.system(size: 28, weight: .black, design: .rounded))
                                    .foregroundColor(diffColor.opacity(0.85))
                                    .frame(minWidth: 44)
                            }
                            
                            // Edit chevron
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
    
    // MARK: - Disambiguation Sheets
    
    private var songSelectionSheet: some View {
        NavigationStack {
            List(songCandidates) { song in
                Button {
                    selectSong(song)
                    showSongSelection = false
                } label: {
                    HStack {
                        SongJacketView(imageName: song.imageName, size: 40)
                        VStack(alignment: .leading) {
                            Text(song.title).font(.headline)
                            Text(song.artist).font(.caption).foregroundColor(.secondary)
                        }
                    }
                }
            }
            .navigationTitle("选择正确歌曲")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("取消") { showSongSelection = false }
                }
            }
        }
        .presentationDetents([.medium, .large])
    }
    
    private var rateSelectionSheet: some View {
        NavigationStack {
            List(rateCandidates, id: \.self) { rate in
                Button {
                    recognizedRate = rate
                    showRateSelection = false
                } label: {
                    HStack {
                        Text("\(String(format: "%.4f", rate))%")
                            .font(.system(.body, design: .monospaced))
                            .fontWeight(.bold)
                        Spacer()
                        if recognizedRate == rate {
                            Image(systemName: "checkmark").foregroundColor(.blue)
                        }
                    }
                }
            }
            .navigationTitle("选择正确达成率")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("完成") { showRateSelection = false }
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func handleCameraFrame(_ image: UIImage) {
        guard !isShowingScoreEntry else { return }
        Task {
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
                        frameMatches.append(song.songId)
                        foundFast = true
                        if frameMatches.count > 3 { break }
                    }
                }
                
                if !foundFast && cleaned.count > 4 {
                    for song in songs {
                        let hasDifficulty = song.sheets.contains { $0.difficulty.lowercased() == inputDifficulty.lowercased() }
                        if recognition.difficulty != nil && !hasDifficulty { continue }
                        
                        if fuzzyMatch(cleaned, song.title) {
                            frameMatches.append(song.songId)
                            if frameMatches.count > 3 { break }
                        }
                    }
                }
                if !frameMatches.isEmpty { break }
            }
            
            await MainActor.run {
                self.updateUIWithResults(songIds: frameMatches, rate: recognition.achievementRate, diff: recognition.difficulty, type: recognition.type, dxScore: recognition.dxScore, fc: recognition.comboStatus, fs: recognition.syncStatus, boxes: recognition.boxes)
            }
        }
    }
    
    private func updateUIWithResults(songIds: [String], rate: Double?, diff: String?, type: String?, dxScore: Int?, fc: String?, fs: String?, boxes: [RecognizedBox]) {
        self.debugBoxes = boxes
        
        for id in recognitionBuffer.keys {
            recognitionBuffer[id, default: 0] -= 1
            if recognitionBuffer[id]! <= 0 { recognitionBuffer.removeValue(forKey: id) }
        }
        
        for id in songIds {
            recognitionBuffer[id, default: 0] += 5
        }
        
        if let topCandidate = recognitionBuffer.max(by: { $0.value < $1.value }), topCandidate.value > 15 {
            if let song = songs.first(where: { $0.songId == topCandidate.key }) {
                if recognizedSong?.songId != song.songId {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        self.recognizedSong = song
                        self.isLocked = true
                    }
                }
                self.lastSeenDate = Date()
            }
        }
        
        if isLocked {
            if let r = rate { self.recognizedRate = r }
            if let d = diff { self.recognizedDifficulty = d }
            if let t = type { self.recognizedType = t }
            if let dx = dxScore { self.recognizedDxScore = dx }
            if let f = fc { self.recognizedFC = f }
            if let s = fs { self.recognizedFS = s }
        }
        
        if isLocked && !showScoreImportConfirmation && !isShowingDetail && !isShowingScoreEntry {
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
        self.recognitionBuffer.removeAll()
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
    
    private func executeImport() {
        guard let (song, rate, diff) = pendingImport else { return }
        let targetDiff = diff ?? recognizedDifficulty ?? "master"
        let targetType = recognizedType ?? "dx"
        
        let sheet = matchedSheet(for: song, diff: targetDiff, type: targetType)
        
        if let sheet = sheet {
            let existingScore = sheet.score
            if existingScore == nil || rate > existingScore!.rate {
                if let existing = existingScore { modelContext.delete(existing) }
                let newScore = Score(sheetId: "\(sheet.songId)-\(sheet.type)-\(sheet.difficulty)", rate: rate, rank: RatingUtils.calculateRank(achievement: rate), dxScore: recognizedDxScore ?? 0)
                modelContext.insert(newScore)
                sheet.score = newScore
                try? modelContext.save()
                
                // Trigger Auto-Upload
                if let config = configs.first {
                    Task {
                        await SyncManager.shared.uploadScoreIfNeeded(sheet: sheet, score: newScore, config: config)
                    }
                }
            }
        }
        isLocked = false
    }
    
    
    private func colorForDifficulty(_ diff: String) -> Color {
        ThemeUtils.colorForDifficulty(diff, nil)
    }
    
    private func importScore(to song: Song, rate: Double, diff: String?) {
        isLocked = true
        pendingImport = (song, rate, diff)
        showScoreImportConfirmation = true
    }
    
    @State private var pendingImport: (Song, Double, String?)? = nil
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

class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onImageCaptured: ((UIImage) -> Void)?
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let processingQueue = DispatchQueue(label: "com.maimaid.camera.queue", qos: .userInteractive)
    private var frameCounter = 0
    private static let rawContext = CIContext(options: [.useSoftwareRenderer: false])
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupCaptureSession()
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
    
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        frameCounter += 1
        // Process 1 frame per ~5 calls to avoid blocking UI with expensive ML requests
        guard frameCounter % 5 == 0 else { return }
        
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = Self.rawContext.createCGImage(ciImage, from: ciImage.extent) else { return }
        let uiImage = UIImage(cgImage: cgImage) // Implicitly .up and portrait
        
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
