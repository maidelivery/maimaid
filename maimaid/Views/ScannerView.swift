import SwiftUI
@preconcurrency import AVFoundation
import Vision
import SwiftData
import PhotosUI

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
            showFeedback("无法加载图片")
            return
        }
        
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
        
        matchedSongs.sort { a, b in
            let aIsExact = allCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(a.title) == .orderedSame })
            let bIsExact = allCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(b.title) == .orderedSame })
            if aIsExact != bIsExact { return aIsExact }
            return a.title.count > b.title.count
        }
        
        isProcessingPhoto = false
        
        if let firstMatch = matchedSongs.first {
            self.recognizedSong = firstMatch
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
            showFeedback("未能识别到歌曲标题")
        }
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
                let chartType = recognizedType ?? "dx"
                let diff = recognizedDifficulty ?? "master"
                let diffColor = ThemeUtils.colorForDifficulty(diff, chartType)
                
                // Find matching sheet if possible to show level
                let sheet = song.sheets.first(where: { $0.difficulty.lowercased() == diff.lowercased() && $0.type.lowercased() == chartType.lowercased() })
                
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
                                    Text(chartType.uppercased() == "STD" ? "标准" : chartType.uppercased())
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
            
            updateUIWithResults(songIds: frameMatches, rate: recognition.rate as Double?, diff: recognition.difficulty, type: recognition.type, dxScore: recognition.dxScore, fc: recognition.comboStatus, fs: recognition.syncStatus, boxes: recognition.boxes)
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

// Removed @MainActor - this class handles its own threading
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
    
    // Explicitly nonisolated since this is called on processingQueue (background thread)
    nonisolated func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        frameCounter += 1
        // Process 1 frame per ~10 calls to reduce frequency (approx 3 fps instead of 6 fps)
        // This helps stabilize the OCR readings and reduces immediate jitter
        guard frameCounter % 10 == 0 else { return }
        
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
