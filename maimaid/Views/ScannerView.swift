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
    
    // Disambiguation
    @State private var songCandidates: [Song] = []
    @State private var rateCandidates: [Double] = []
    @State private var showSongSelection = false
    @State private var showRateSelection = false
    
    @State private var isShowingDetail = false
    @State private var showScoreImportConfirmation = false
    
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
                CameraPreviewView { observations in
                    handleObservations(observations)
                }
                .ignoresSafeArea()
                
                // Overlay
                VStack {
                    headerView()
                    Spacer()
                    scanningGuideView()
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
        
        // Use ScoreImageProcessor for full-image OCR + field extraction
        let recognition = await ScoreImageProcessor.shared.process(image)
        
        // Match title candidates against song database
        var matchedSongs: [Song] = []
        var seenIds = Set<String>()
        
        // Try all candidates collected by the processor
        for candidate in recognition.titleCandidates {
            let matches = songs.filter { song in
                song.title.localizedCaseInsensitiveContains(candidate) ||
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
            let aIsExact = recognition.titleCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(a.title) == .orderedSame })
            let bIsExact = recognition.titleCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(b.title) == .orderedSame })
            if aIsExact != bIsExact { return aIsExact }
            return a.title.count > b.title.count // Favor longer (more specific) titles
        }
        
        await MainActor.run {
            isProcessingPhoto = false
            
            // Set results
            self.rateCandidates = recognition.rateCandidates
            self.songCandidates = matchedSongs
            self.recognizedDifficulty = recognition.difficulty
            self.recognizedType = recognition.type
            
            // Logic for auto-selecting or prompting
            if matchedSongs.isEmpty {
                showFeedback("未能识别到歌曲标题")
            } else if matchedSongs.count > 1 {
                // If we have an "exact" match and others are just substrings, auto-select exact
                let exactMatched = matchedSongs.first { song in
                    recognition.titleCandidates.contains(where: { $0.localizedCaseInsensitiveCompare(song.title) == .orderedSame })
                }
                
                if let exact = exactMatched {
                    selectSong(exact)
                } else {
                    showSongSelection = true
                }
            } else {
                selectSong(matchedSongs[0])
            }
            
            // If we have multiple rates, we'll let the user change it from the result card
            // but we default to the highest one.
            self.recognizedRate = recognition.achievementRate
            
            if recognition.rateCandidates.count > 1 {
                // We'll show an indicator in the result card that other scores were found
            }
            
            self.isLocked = true
            self.lastSeenDate = Date()
        }
    }
    
    private func selectSong(_ song: Song) {
        recognizedSong = song
        // If we have a rate, we can prompt for import, but let's wait until disambiguation is done
        if recognizedRate != nil {
            showScoreImportConfirmation = true
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
    private func scanningGuideView() -> some View {
        ZStack {
            // Circular Lens UI
            Circle()
                .strokeBorder(
                    LinearGradient(colors: [isLocked ? .green : .blue, .clear], startPoint: .top, endPoint: .bottom),
                    lineWidth: 4
                )
                .frame(width: 320, height: 320)
                .overlay(
                    Circle()
                        .stroke(Color.white.opacity(0.1), lineWidth: 1)
                        .padding(-12)
                )
            
            ScanningLineView(isLocked: isLocked)
                .frame(width: 320, height: 320)
                .clipShape(Circle())
            
            VStack(spacing: 6) {
                Text(isLocked ? "TARGET LOCKED" : "ALIGN CENTER")
                    .font(.system(size: 14, weight: .black, design: .rounded))
                    .foregroundColor(isLocked ? .green : .blue)
                
                Text(isLocked ? "DATA SYNCED" : "ALIGN RESULT RING")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(.white.opacity(0.6))
            }
            .offset(y: 190)
        }
        .offset(y: -50)
    }
    
    @ViewBuilder
    private func resultView() -> some View {
        if let song = recognizedSong {
            Button {
                isShowingDetail = true
            } label: {
                HStack(spacing: 16) {
                    SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: 60, cornerRadius: 16)
                        .shadow(color: .black.opacity(0.3), radius: 6, x: 0, y: 3)
                    
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 6) {
                            if let type = recognizedType {
                                Text(type.uppercased() == "STD" ? "标准" : type.uppercased())
                                    .font(.system(size: 8, weight: .black))
                                    .padding(.horizontal, 4)
                                    .padding(.vertical, 1)
                                    .background(type.lowercased() == "dx" ? Color.orange : Color.blue)
                                    .foregroundColor(.white)
                                    .cornerRadius(3)
                            }
                            Text(song.title)
                                .font(.system(.subheadline, weight: .bold))
                                .foregroundColor(.white)
                                .lineLimit(1)
                        }
                        
                        Text(song.artist)
                            .font(.system(size: 10))
                            .foregroundColor(.white.opacity(0.6))
                            .lineLimit(1)
                    }
                    
                    Spacer()
                    
                    if let rate = recognizedRate {
                        VStack(alignment: .trailing, spacing: 6) {
                            Button {
                                if rateCandidates.count > 1 {
                                    showRateSelection = true
                                }
                            } label: {
                                HStack(spacing: 4) {
                                    Text("\(String(format: "%.4f", rate))%")
                                        .font(.system(size: 16, weight: .black, design: .monospaced))
                                        .foregroundColor(.yellow)
                                    
                                    if rateCandidates.count > 1 {
                                        Image(systemName: "chevron.up.chevron.down")
                                            .font(.system(size: 10, weight: .bold))
                                            .foregroundColor(.yellow.opacity(0.8))
                                    }
                                }
                                .shadow(color: .yellow.opacity(0.3), radius: 4)
                            }
                            .buttonStyle(.plain)
                            .disabled(rateCandidates.count <= 1)
                            
                            HStack(spacing: 4) {
                                if let diff = recognizedDifficulty {
                                    Text(diff.uppercased())
                                        .font(.system(size: 8, weight: .black))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(colorForDifficulty(diff))
                                        .foregroundColor(.white)
                                        .cornerRadius(4)
                                }
                                
                                Button {
                                    importScore(to: song, rate: rate, diff: recognizedDifficulty)
                                } label: {
                                    Text("导入")
                                        .font(.system(size: 8, weight: .black))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(Color.green)
                                        .foregroundColor(.white)
                                        .cornerRadius(6)
                                        .shadow(color: .green.opacity(0.4), radius: 4)
                                }
                            }
                        }
                    }
                    
                    Image(systemName: "chevron.right")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white.opacity(0.4))
                }
                .padding(18)
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
                        SongJacketView(imageName: song.imageName, remoteUrl: song.imageUrl, size: 40)
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

    struct ScanningLineView: View {
        let isLocked: Bool
        @State private var phase: CGFloat = 0
        
        var body: some View {
            Rectangle()
                .fill(LinearGradient(colors: [.clear, (isLocked ? Color.green : Color.blue).opacity(0.6), .clear], startPoint: .top, endPoint: .bottom))
                .frame(height: 60)
                .offset(y: -160 + (320 * phase))
                .onAppear {
                    withAnimation(.linear(duration: 2.5).repeatForever(autoreverses: false)) {
                        phase = 1.0
                    }
                }
        }
    }

    private func handleObservations(_ observations: [VNRecognizedTextObservation]) {
        processingQueue.async {
            self.processInQueue(observations)
        }
    }

    private func processInQueue(_ observations: [VNRecognizedTextObservation]) {
        let candidates = observations.compactMap { obs -> (String, CGRect) in
            let text = obs.topCandidates(1).first?.string ?? ""
            return (text, obs.boundingBox)
        }
        
        let topCandidates = candidates.filter { $0.1.origin.y > 0.6 }
        let midCandidates = candidates.filter { $0.1.origin.y > 0.3 && $0.1.origin.y <= 0.6 }
        let botCandidates = candidates.filter { $0.1.origin.y <= 0.3 }
        
        let ratePattern = "(\\d{1,3}\\.\\d{4})"
        let rateRegex = try? NSRegularExpression(pattern: ratePattern)
        var rates: [Double] = []
        for (text, _) in midCandidates {
            let matches = rateRegex?.matches(in: text, options: [], range: NSRange(location: 0, length: text.utf16.count)) ?? []
            for match in matches {
                if let range = Range(match.range(at: 1), in: text), let value = Double(text[range]), value <= 101.0 {
                    rates.append(value)
                }
            }
        }
        let capturedRate = rates.max()
        
        let diffKeywords = ["basic", "advanced", "expert", "master", "remaster"]
        var capturedDiff: String? = nil
        var capturedType: String? = nil
        
        for (text, _) in (topCandidates + midCandidates + botCandidates) {
            let low = text.lowercased()
            if capturedDiff == nil {
                if let matched = diffKeywords.first(where: { low.contains($0) }) {
                    capturedDiff = matched
                }
            }
            if capturedType == nil {
                if low.contains("dx") || low.contains("d×") || low.contains("d x") { capturedType = "dx" }
                else if low.contains("std") || low.contains("standard") || low.contains("stand") { capturedType = "std" }
                else if low.contains("utage") || low.contains("宴") { capturedType = "utage" }
            }
        }
        
        var frameMatches: [String] = []
        for (text, _) in (topCandidates + midCandidates) {
            let cleaned = text.trimmingCharacters(in: .whitespacesAndNewlines)
            guard cleaned.count >= 2 else { continue }
            
            var foundFast = false
            for song in songs {
                if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) {
                    frameMatches.append(song.songId)
                    foundFast = true
                    if frameMatches.count > 3 { break }
                }
            }
            
            if !foundFast && cleaned.count > 4 {
                for song in songs {
                    if fuzzyMatch(cleaned, song.title) {
                        frameMatches.append(song.songId)
                        if frameMatches.count > 3 { break }
                    }
                }
            }
            if !frameMatches.isEmpty { break }
        }
        
        DispatchQueue.main.async {
            self.updateUIWithResults(songIds: frameMatches, rate: capturedRate, diff: capturedDiff, type: capturedType)
        }
    }
    
    private func updateUIWithResults(songIds: [String], rate: Double?, diff: String?, type: String?) {
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
        }
        
        if isLocked && !showScoreImportConfirmation && !isShowingDetail {
            if Date().timeIntervalSince(lastSeenDate) > 4.0 {
                withAnimation {
                    self.recognizedSong = nil
                    self.recognizedRate = nil
                    self.recognizedDifficulty = nil
                    self.recognizedType = nil
                    self.recognitionBuffer.removeAll()
                    self.isLocked = false
                }
            }
        }
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
    
    private func executeImport() {
        guard let (song, rate, diff) = pendingImport else { return }
        let targetDiff = diff ?? recognizedDifficulty ?? "master"
        let targetType = recognizedType ?? "dx"
        
        var matchedSheet = song.sheets.first { sheet in
            let diffMatch = sheet.difficulty.lowercased() == targetDiff.lowercased()
            let typeMatch = sheet.type.lowercased() == targetType.lowercased()
            return diffMatch && typeMatch
        }
        
        if matchedSheet == nil {
            matchedSheet = song.sheets.first { $0.difficulty.lowercased() == targetDiff.lowercased() }
        }
        
        if let sheet = matchedSheet {
            let existingScore = sheet.score
            if existingScore == nil || rate > existingScore!.rate {
                if let existing = existingScore { modelContext.delete(existing) }
                let newScore = Score(sheetId: "\(sheet.songId)-\(sheet.type)-\(sheet.difficulty)", rate: rate, rank: calculateRank(rate))
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
    
    private func calculateRank(_ rate: Double) -> String {
        if rate >= 100.5 { return "SSS+" }
        if rate >= 100.0 { return "SSS" }
        if rate >= 99.5 { return "SS+" }
        if rate >= 99.0 { return "SS" }
        if rate >= 98.0 { return "S+" }
        if rate >= 97.0 { return "S" }
        if rate >= 94.0 { return "AAA" }
        if rate >= 90.0 { return "AA" }
        if rate >= 80.0 { return "A" }
        return "B"
    }
    
    private func colorForDifficulty(_ diff: String) -> Color {
        switch diff.lowercased() {
        case "basic": return .green
        case "advanced": return .orange
        case "expert": return .red
        case "master": return .purple
        case "remaster": return Color(red: 0.85, green: 0.65, blue: 1.0)
        default: return .blue
        }
    }
    
    private func importScore(to song: Song, rate: Double, diff: String?) {
        isLocked = true
        pendingImport = (song, rate, diff)
        showScoreImportConfirmation = true
    }
    
    @State private var pendingImport: (Song, Double, String?)? = nil
}

struct CameraPreviewView: UIViewControllerRepresentable {
    var onObservationsRecognized: ([VNRecognizedTextObservation]) -> Void
    func makeUIViewController(context: Context) -> CameraViewController {
        let controller = CameraViewController()
        controller.onObservationsRecognized = onObservationsRecognized
        return controller
    }
    func updateUIViewController(_ uiViewController: CameraViewController, context: Context) {}
}

class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onObservationsRecognized: (([VNRecognizedTextObservation]) -> Void)?
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private let textRecognitionRequest = VNRecognizeTextRequest()
    private let processingQueue = DispatchQueue(label: "com.maimaid.camera.queue", qos: .userInteractive)
    private var frameCounter = 0
    
    override func viewDidLoad() {
        super.viewDidLoad()
        setupCaptureSession()
        setupVision()
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
        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer?.frame = view.layer.bounds
        previewLayer?.videoGravity = .resizeAspectFill
        if let previewLayer = previewLayer { view.layer.addSublayer(previewLayer) }
        DispatchQueue.global(qos: .userInitiated).async { captureSession.startRunning() }
    }
    
    private func setupVision() {
        textRecognitionRequest.recognitionLevel = .accurate
        textRecognitionRequest.usesLanguageCorrection = true
        textRecognitionRequest.recognitionLanguages = ["ja-JP", "zh-Hans", "en-US"]
        textRecognitionRequest.regionOfInterest = CGRect(x: 0.1, y: 0.28, width: 0.8, height: 0.6)
    }
    
    func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        frameCounter += 1
        guard frameCounter % 4 == 0 else { return }
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let requestHandler = VNImageRequestHandler(cvPixelBuffer: pixelBuffer, orientation: .right, options: [:])
        do {
            try requestHandler.perform([textRecognitionRequest])
            if let results = textRecognitionRequest.results {
                self.onObservationsRecognized?(results)
            }
        } catch {
            print("Vision failed: \(error)")
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
