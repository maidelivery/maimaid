import SwiftUI
import SwiftData
import PhotosUI

// Camera recognition, matching, stabilization, and result presentation share frame state.
// swiftlint:disable file_length

// MARK: - OCR Error Correction

extension ScannerView {
    /// Common OCR misrecognition patterns for Japanese game titles
    private static let ocrSubstitutions: [Character: [Character]] = [
        "O": ["0", "D", "Q"],
        "0": ["O", "D"],
        "D": ["O", "0"],
        "I": ["1", "l", "L"],
        "1": ["I", "l"],
        "l": ["I", "1"],
        "L": ["I", "1"],
        "S": ["5", "Z"],
        "5": ["S"],
        "Z": ["S", "2"],
        "2": ["Z"],
        "B": ["8"],
        "8": ["B"],
        "G": ["6", "C"],
        "6": ["G"],
        "C": ["G"],
        "Q": ["O", "0"],
        "職": ["蔵", "藏", "概"],
        "蔵": ["職", "藏"],
        "藏": ["職", "蔵"],
        "黒": ["黑"],
        "黑": ["黒"],
        "響": ["郷"],
        "郷": ["響"],
        "桜": ["櫻"],
        "櫻": ["桜"],
        "竜": ["龍"],
        "龍": ["竜"],
        "斬": ["斷"],
        "國": ["国"],
        "国": ["國"],
        "円": ["圓"],
        "圓": ["円"],
        "劇": ["劇"],
        "鍵": ["鍵"],
        "変": ["變"],
        "變": ["変"],
        "戦": ["戰"],
        "戰": ["戦"],
        "関": ["關"],
        "關": ["関"],
        "広": ["廣"],
        "廣": ["広"],
        "駅": ["驛"],
        "驛": ["駅"],
        "帯": ["帶"],
        "帶": ["帯"],
        "極": ["极"],
        "极": ["極"],
        "転": ["轉"],
        "轉": ["転"],
        "検": ["檢"],
        "檢": ["検"],
        "権": ["權"],
        "權": ["権"],
        "譲": ["讓"],
        "讓": ["譲"],
        "説": ["說"],
        "說": ["説"],
        "読": ["讀"],
        "讀": ["読"],
        "弾": ["彈"],
        "彈": ["弾"],
        "個": ["箇"],
        "箇": ["個"],
        "号": ["號"],
        "號": ["号"],
        "声": ["聲"],
        "聲": ["声"],
        "栄": ["榮"],
        "榮": ["栄"],
        "営": ["營"],
        "營": ["営"],
        "様": ["樣"],
        "樣": ["様"],
        "測": ["測"],
        "画": ["畫"],
        "畫": ["画"]
    ]

    private func generateOCRVariants(_ text: String, maxVariants: Int = 8) -> [String] {
        var variants = [text]
        var queue = [text]
        var seen = Set<String>([text])

        while !queue.isEmpty && variants.count < maxVariants {
            let current = queue.removeFirst()

            for (original, replacements) in Self.ocrSubstitutions {
                if current.contains(original) {
                    for replacement in replacements {
                        let variant = current.replacingOccurrences(of: String(original), with: String(replacement))
                        if !seen.contains(variant) {
                            seen.insert(variant)
                            variants.append(variant)
                            queue.append(variant)
                        }
                    }
                }

                for replacement in replacements {
                    if current.contains(replacement) {
                        let variant = current.replacingOccurrences(of: String(replacement), with: String(original))
                        if !seen.contains(variant) {
                            seen.insert(variant)
                            variants.append(variant)
                            queue.append(variant)
                        }
                    }
                }
            }
        }

        return variants
    }

    private func isSimilarWithOCRErrors(_ s1: String, _ s2: String, threshold: Int? = nil) -> Bool {
        if s1.localizedCaseInsensitiveCompare(s2) == .orderedSame {
            return true
        }

        let s1Variants = generateOCRVariants(s1)
        let s2Lower = s2.lowercased()

        for variant in s1Variants {
            let variantLower = variant.lowercased()
            if s2Lower.localizedCaseInsensitiveContains(variantLower) ||
               variantLower.localizedCaseInsensitiveContains(s2Lower) {
                return true
            }
        }

        let dist = levenshteinDistance(s1, s2)
        let maxLen = max(s1.count, s2.count)
        let adaptiveThreshold = threshold ?? max(2, maxLen / 3)

        return dist <= adaptiveThreshold
    }

    private func stripUtagePrefix(_ title: String) -> String {
        var result = title

        if let range = result.range(of: "^【[^】]+】\\s*", options: .regularExpression) {
            result.removeSubrange(range)
        }

        if let range = result.range(of: "^\\[[^\\]]+\\]\\s*", options: .regularExpression) {
            result.removeSubrange(range)
        }

        if let range = result.range(of: "^［[^］]+］\\s*", options: .regularExpression) {
            result.removeSubrange(range)
        }

        return result
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.widthInsensitive], locale: Locale(identifier: "en_US_POSIX"))
    }

    private func extractUtagePrefixKanji(from text: String?) -> String? {
        guard let text else { return nil }
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)

        if let range = trimmed.range(of: #"^【([^】]+)】"#, options: .regularExpression) {
            return String(trimmed[range]).dropFirst().dropLast().trimmingCharacters(in: .whitespacesAndNewlines)
        }

        if let range = trimmed.range(of: #"^\[([^\]]+)\]"#, options: .regularExpression) {
            return String(trimmed[range]).dropFirst().dropLast().trimmingCharacters(in: .whitespacesAndNewlines)
        }

        if let range = trimmed.range(of: #"^［([^］]+)］"#, options: .regularExpression) {
            return String(trimmed[range]).dropFirst().dropLast().trimmingCharacters(in: .whitespacesAndNewlines)
        }

        return nil
    }

    private func songHasExplicitUtagePrefix(_ song: Song, kanji: String?) -> Bool {
        for text in [song.title, song.songIdentifier] {
            if let detected = extractUtagePrefixKanji(from: text) {
                if let kanji, !kanji.isEmpty {
                    if detected == kanji { return true }
                } else {
                    return true
                }
            }
        }

        return false
    }

    private func normalizedSongMatchTitle(_ title: String) -> String {
        stripUtagePrefix(title)
            .folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .replacingOccurrences(of: "\u{3000}", with: " ")
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .lowercased()
    }

    private func matchUtageSheet(for song: Song, kanji: String?, maxDxScore: Int?, dxScore: Int?) -> Sheet? {
        let utageSheets = song.sheets.filter {
            $0.type.lowercased() == "utage" &&
                ServerChartPolicy.isPlayable($0, on: activeServer) &&
                ScannerNoteCountValidator.isCompatible(maxDxScore: maxDxScore, sheetTotal: $0.total)
        }

        guard !utageSheets.isEmpty else { return nil }

        if let kanji = kanji, !kanji.isEmpty {
            let kanjiMatches = utageSheets.filter { sheet in
                let diff = sheet.difficulty
                return diff.contains(kanji)
            }

            if kanjiMatches.count == 1 {
                return kanjiMatches.first
            }

            if kanjiMatches.count > 1 {
                if let maxDx = maxDxScore, maxDx > 0 {
                    let totalNotes = maxDx / 3
                    let exact = kanjiMatches.first { $0.total == totalNotes }
                    if let exact = exact { return exact }
                }
                if let dx = dxScore, dx > 0 {
                    let best = kanjiMatches.min { s1, s2 in
                        guard let t1 = s1.total, let t2 = s2.total else { return s1.total != nil }
                        return abs(t1 * 3 - dx) < abs(t2 * 3 - dx)
                    }
                    if let best = best { return best }
                }
                return kanjiMatches.first
            }
        }

        if let maxDx = maxDxScore, maxDx > 0 {
            let totalNotes = maxDx / 3
            let exact = utageSheets.first { $0.total == totalNotes }
            if let exact = exact { return exact }
        }

        if let dx = dxScore, dx > 0 {
            let matching = utageSheets.filter { sheet in
                guard let total = sheet.total else { return true }
                return total * 3 >= dx
            }
            if matching.count == 1 { return matching.first }

            let best = matching.min { s1, s2 in
                guard let t1 = s1.total, let t2 = s2.total else { return s1.total != nil }
                return abs(t1 * 3 - dx) < abs(t2 * 3 - dx)
            }
            if let best = best { return best }
        }

        if utageSheets.count == 1 {
            return utageSheets.first
        }

        return utageSheets.first
    }
}

@MainActor
// The scanner owns the camera frame lifecycle and its coordinated recognition buffers.
// swiftlint:disable:next type_body_length
struct ScannerView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var configs: [SyncConfig]
    @Query private var songs: [Song]
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]

    @State private var isShowingDetail = false
    @State private var isShowingScoreEntry = false

    @State private var recognizedSong: Song?
    @State private var recognizedRate: Double?
    @State private var recognizedDifficulty: String?
    @State private var recognizedType: String?
    @State private var recognizedDxScore: Int?
    @State private var recognizedMaxDxScore: Int?
    @State private var recognizedFC: String?
    @State private var recognizedFS: String?
    @State private var recognizedLevel: Double?
    @State private var recognizedMaxCombo: Int?
    @State private var recognizedKanji: String?
    @State private var debugBoxes: [RecognizedBox] = []
    @AppStorage(AppStorageKeys.showScannerBoundingBox) private var showScannerBoundingBox: Bool = false
    @State private var recognizedClass: MaimaiImageType = .unknown

    @State private var showFlashOverlay = false
    @State private var isSavingPhoto = false

    @State private var isLocked = false
    @State private var lastSeenDate = Date()
    @State private var recognitionBuffer: [String: Int] = [:]

    @State private var rateBuffer: [Double] = []
    @State private var dxScoreBuffer: [Int] = []
    @State private var maxDxScoreBuffer: [Int] = []
    private let stabilizationThreshold = 3

    private var activeServer: GameServer {
        activeProfiles.first.flatMap { GameServer(rawValue: $0.server) } ?? .jp
    }

    @State private var selectedPhotoItem: PhotosPickerItem?
    @State private var isProcessingPhoto = false
    @State private var photoImportFeedback: String?
    @State private var feedbackDismissTask: Task<Void, Never>?
    @State private var frameAnalysisTask: Task<Void, Never>?
    @State private var pendingFrameImage: UIImage?
    @State private var modelController = ScannerModelDownloadController()

    private var resolvedCurrentScoreSheet: Sheet? {
        guard let song = recognizedSong else { return nil }
        return resolvedScoreSheet(
            for: song,
            difficulty: recognizedDifficulty,
            type: recognizedType,
            kanji: recognizedKanji,
            maxDxScore: recognizedMaxDxScore,
            dxScore: recognizedDxScore
        )
    }

    private var canPresentCurrentScoreResult: Bool {
        guard recognizedClass == .score, recognizedSong != nil else { return false }

        guard let difficulty = recognizedDifficulty?.trimmingCharacters(in: .whitespacesAndNewlines),
              !difficulty.isEmpty else {
            return true
        }

        return resolvedCurrentScoreSheet != nil
    }

    var body: some View {
        @Bindable var downloadController = modelController

        return NavigationStack {
            ZStack {
                CameraPreviewView(
                    onImageCaptured: handleCameraFrame,
                    onPhotoCaptured: handleCapturedScannerPhoto,
                    onQRCodeDetected: handleCollectionQRCode
                )
                .ignoresSafeArea()

                debugOverlayView()

                if showFlashOverlay {
                    Color.white
                        .ignoresSafeArea()
                        .zIndex(10)
                        .transition(.opacity)
                }

                VStack {
                    headerView()
                    Spacer()

                    if isProcessingPhoto {
                        HStack(spacing: 10) {
                            ProgressView()
                                .tint(.white)
                            Text("scanner.processing")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(.white)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 8)
                    }

                    if let feedback = photoImportFeedback {
                        Text(feedback)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(.orange)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(.ultraThinMaterial, in: Capsule())
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                            .padding(.bottom, 8)
                    }
                }

                VStack {
                    Spacer()
                    if canPresentCurrentScoreResult {
                        Button(action: triggerPhotoCapture) {
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
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(Text(isSavingPhoto ? "scanner.capture.saving" : "scanner.capture.button"))
                        .accessibilityHint(Text("scanner.capture.hint"))
                        .transition(.scale.combined(with: .opacity))
                        .padding(.bottom, 20)
                    }
                    resultView()
                }

                ScannerModelDownloadView(
                    state: modelController.state,
                    download: modelController.download,
                    retry: modelController.download,
                    cancel: modelController.cancelDownload
                )
            }
            .sheet(isPresented: $isShowingScoreEntry, onDismiss: {
                resetScanner()
            }) {
                scoreEntrySheetContent
            }
            .onChange(of: selectedPhotoItem) { _, newItem in
                if let item = newItem {
                    Task { await processSelectedPhoto(item) }
                }
            }
            .onDisappear {
                feedbackDismissTask?.cancel()
                feedbackDismissTask = nil
                frameAnalysisTask?.cancel()
                frameAnalysisTask = nil
                pendingFrameImage = nil
                modelController.cancelDownload()
            }
        }
        .task {
            modelController.check()
        }
        .onChange(of: modelController.state) { _, state in
            if case .ready(offline: true) = state {
                showFeedback(String(localized: "scanner.models.offline.cache"))
            }
        }
        .confirmationDialog(
            modelPromptTitle,
            isPresented: $downloadController.isShowingPrompt,
            titleVisibility: .visible
        ) {
            Button("scanner.models.download.action", systemImage: "arrow.down.circle") {
                modelController.download()
            }
            Button("scanner.models.cancel", role: .cancel) {}
        } message: {
            Text(modelPromptMessage)
        }
    }

    private var modelPromptTitle: LocalizedStringKey {
        if case .updateAvailable = modelController.state {
            return "scanner.models.update.title"
        }
        return "scanner.models.download.title"
    }

    private var modelPromptMessage: String {
        let totalBytes: Int64 = switch modelController.state {
        case let .downloadRequired(totalBytes), let .updateAvailable(totalBytes): totalBytes
        case .checking, .downloading, .ready, .failed: 0
        }
        let size = totalBytes.formatted(.byteCount(style: .file))
        if case .updateAvailable = modelController.state {
            return String(localized: "scanner.models.update.summary \(size)")
        }
        return String(localized: "scanner.models.download.summary \(size)")
    }

    private func handleCollectionQRCode(_ value: String) {
        guard !isProcessingPhoto else { return }
        do {
            let payload = try SongCollectionCodec.decode(value)
            try SongCollectionImportService.importCollection(payload, context: modelContext)
            showFeedback("Imported collections")
        } catch {
            showFeedback("Invalid collection QR code")
        }
    }

    @ViewBuilder
    private var scoreEntrySheetContent: some View {
        if recognizedClass == .score, let sheet = resolvedCurrentScoreSheet {
                ScoreEntryView(
                    sheet: sheet,
                    initialRate: recognizedRate,
                    initialRank: RatingUtils.calculateRank(achievement: recognizedRate ?? 0),
                    initialDxScore: recognizedDxScore,
                    initialFC: recognizedFC,
                    initialFS: recognizedFS
                )
        }
    }

    // MARK: - Photo Processing

    // Photo recognition mirrors the live camera pipeline while reporting user-facing failures.
    // swiftlint:disable:next function_body_length
    private func processSelectedPhoto(_ item: PhotosPickerItem) async {
        guard modelController.canRecognize else { return }
        isProcessingPhoto = true
        photoImportFeedback = nil

        guard let data = try? await item.loadTransferable(type: Data.self),
              let image = UIImage(data: data) else {
            isProcessingPhoto = false
            showFeedback(String(localized: "scanner.error.load"))
            return
        }

        let imageType: MaimaiImageType
        do {
            imageType = try await MLDistinguishProcessor.shared.classify(image)
        } catch {
            isProcessingPhoto = false
            await modelController.reportRuntimeFailure(error)
            showFeedback(error.localizedDescription)
            return
        }

        if imageType == .choose {
            let recognition: MLChooseResult
            do {
                recognition = try await MLChooseProcessor.shared.process(image)
            } catch {
                isProcessingPhoto = false
                await modelController.reportRuntimeFailure(error)
                showFeedback(error.localizedDescription)
                return
            }
            var matchedSongs: [Song] = []
            var seenIds = Set<String>()
            var allCandidates = recognition.titleCandidates
            if let exactTitle = recognition.title { allCandidates.insert(exactTitle, at: 0) }

            for candidate in allCandidates {
                let matches = songs.filter { song in
                    let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
                    let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { !$0.regionJp && !$0.regionIntl && !$0.regionCn }
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

            if let targetCandidate = allCandidates.first {
                matchedSongs.sort { firstSong, secondSong in
                    let aExact = firstSong.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    let bExact = secondSong.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    if aExact != bExact { return aExact }
                    let aAlias = firstSong.aliases.contains {
                        $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    }
                    let bAlias = secondSong.aliases.contains {
                        $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    }
                    if aAlias != bAlias { return aAlias }
                    let aDist = levenshteinDistance(firstSong.title, targetCandidate)
                    let bDist = levenshteinDistance(secondSong.title, targetCandidate)
                    if aDist != bDist { return aDist < bDist }
                    return firstSong.title.count < secondSong.title.count
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
            let recognition: MLScoreResult
            do {
                recognition = try await MLScoreProcessor.shared.process(image)
            } catch {
                isProcessingPhoto = false
                await modelController.reportRuntimeFailure(error)
                showFeedback(error.localizedDescription)
                return
            }
            let matchedSongs = matchSongsWithFilters(recognition)

            isProcessingPhoto = false
            if let firstMatch = matchedSongs.first(where: {
                canPresentScoreResult(
                    for: $0,
                    difficulty: recognition.difficulty,
                    type: recognition.type,
                    kanji: recognition.kanji,
                    maxDxScore: recognition.maxDxScore,
                    dxScore: recognition.dxScore
                )
            }) {
                self.recognizedSong = firstMatch
                self.recognizedClass = .score
                self.recognizedRate = recognition.rate
                self.recognizedDifficulty = recognition.difficulty
                self.recognizedType = recognition.type
                self.recognizedDxScore = recognition.dxScore
                self.recognizedMaxDxScore = recognition.maxDxScore
                self.recognizedFC = recognition.comboStatus
                self.recognizedFS = recognition.syncStatus
                self.recognizedLevel = recognition.level
                self.recognizedMaxCombo = recognition.maxCombo
                self.recognizedKanji = recognition.kanji
                self.debugBoxes = recognition.boxes
                self.isLocked = true
                self.lastSeenDate = Date()
            } else {
                showFeedback(String(localized: "scanner.error.title"))
            }
        }
    }

    // MARK: - Song Matching

    // OCR candidates require coordinated title, chart, level, note-count, and server validation.
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    private func matchSongsWithFilters(_ recognition: MLScoreResult) -> [Song] {
        let titleCandidates = recognition.titleCandidates
        let title = recognition.title
        let difficulty = recognition.difficulty
        let level = recognition.level
        let maxCombo = recognition.maxCombo
        let dxScore = recognition.dxScore
        let maxDxScore = recognition.maxDxScore
        let type = recognition.type
        let kanji = recognition.kanji
        let rawCandidates = ([title] + titleCandidates.map { Optional($0) }).compactMap { $0 }
        var allCandidates = titleCandidates
        if let exactTitle = title {
            allCandidates.insert(exactTitle, at: 0)
        }

        let isUtage = type?.lowercased() == "utage"
        if isUtage {
            allCandidates = allCandidates.map(stripUtagePrefix)
        }

        let explicitTitleKanji = rawCandidates.compactMap { extractUtagePrefixKanji(from: $0) }.first
        let derivedTotalNotes: Int? = {
            if let maxDxScore, maxDxScore > 0 {
                return maxDxScore / 3
            }
            return maxCombo
        }()

        let validatedLevel: Double? = {
            guard let level, level >= 1, level <= 15 else { return nil }
            return level
        }()
        let validatedDxScore: Int? = {
            guard let dxScore, dxScore > 0 else { return nil }
            return dxScore
        }()
        let validatedMaxDxScore: Int? = {
            guard let maxDxScore, maxDxScore > 0 else { return nil }
            return maxDxScore
        }()
        let validatedKanji: String? = {
            guard let kanji, !kanji.isEmpty else { return nil }
            return kanji
        }()

        let hasDifficulty = difficulty != nil && !isUtage
        let hasLevel = validatedLevel != nil
        let hasTotalNotes = (derivedTotalNotes ?? 0) > 0
        let hasDxScore = validatedDxScore != nil
        let hasMaxDxScore = validatedMaxDxScore != nil
        let hasKanji = validatedKanji != nil
        let hasExplicitUtagePrefix = explicitTitleKanji != nil

        func hasAvailableStandardSheets(_ song: Song) -> Bool {
            let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
            return standardSheets.contains { ServerChartPolicy.isPlayable($0, on: activeServer) }
        }

        func matchesUtage(_ song: Song) -> Bool {
            if isUtage {
                let utageSheets = song.sheets.filter {
                    $0.type.lowercased() == "utage" &&
                        ServerChartPolicy.isPlayable($0, on: activeServer) &&
                        ScannerNoteCountValidator.isCompatible(maxDxScore: maxDxScore, sheetTotal: $0.total)
                }
                if utageSheets.isEmpty { return false }
                if hasExplicitUtagePrefix && !songHasExplicitUtagePrefix(song, kanji: explicitTitleKanji) { return false }
                if let validatedKanji,
                   !utageSheets.contains(where: { $0.difficulty.contains(validatedKanji) }) {
                    return false
                }
                if hasTotalNotes {
                    let totalMatch = utageSheets.contains { sheet in
                        guard let total = sheet.total, let derivedTotalNotes else { return true }
                        return total == derivedTotalNotes
                    }
                    if !totalMatch {
                        if let validatedDxScore {
                            if !utageSheets.contains(where: { sheet in
                                guard let total = sheet.total else { return true }
                                return total * 3 >= validatedDxScore
                            }) {
                                return false
                            }
                        } else {
                            return false
                        }
                    }
                } else if let validatedDxScore {
                    if !utageSheets.contains(where: { sheet in
                        guard let total = sheet.total else { return true }
                        return total * 3 >= validatedDxScore
                    }) {
                        return false
                    }
                }
                return true
            }
            return false
        }

        func matchesStandard(_ song: Song) -> Bool {
            song.sheets.contains { sheet in
                if !ServerChartPolicy.isPlayable(sheet, on: activeServer) {
                    return false
                }
                if !ScannerNoteCountValidator.isCompatible(maxDxScore: maxDxScore, sheetTotal: sheet.total) {
                    return false
                }
                if sheet.type.lowercased() == "utage" {
                    return false
                }
                if let type, type.lowercased() != "utage", sheet.type.lowercased() != type.lowercased() {
                    return false
                }
                if let difficulty, sheet.difficulty.lowercased() != difficulty.lowercased() {
                    return false
                }
                if let validatedLevel {
                    let metadata = ServerChartPolicy.metadata(for: sheet, on: activeServer)
                    let sheetLevel = metadata.ratingLevel ?? 0
                    if sheetLevel > 0 {
                        if Int(sheetLevel) != Int(validatedLevel) { return false }
                    } else if Int(metadata.level) != Int(validatedLevel) {
                        return false
                    }
                }
                if let derivedTotalNotes, derivedTotalNotes > 0,
                   let sheetTotal = sheet.total, sheetTotal != derivedTotalNotes {
                    return false
                }
                if let validatedDxScore,
                   let total = sheet.total, total * 3 < validatedDxScore {
                    return false
                }
                if let validatedMaxDxScore,
                   let total = sheet.total, total * 3 != validatedMaxDxScore {
                    return false
                }
                return true
            }
        }

        var filteredSongs = songs.filter { song in
            guard hasAvailableStandardSheets(song) else { return false }
            return isUtage ? matchesUtage(song) : matchesStandard(song)
        }

        let hasAnyValidation = hasDifficulty || hasLevel || hasTotalNotes || hasDxScore || hasMaxDxScore || hasKanji
        if filteredSongs.isEmpty && !hasAnyValidation {
            filteredSongs = songs.filter(hasAvailableStandardSheets)
        }
        if filteredSongs.count == 1 && hasMaxDxScore { return filteredSongs }

        var matchedSongs: [(song: Song, score: Int)] = []
        var seenIds = Set<String>()

        for candidate in allCandidates {
            let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !cleaned.isEmpty else { continue }

            let variants = generateOCRVariants(cleaned, maxVariants: 6)
            for song in filteredSongs {
                guard !seenIds.contains(song.songIdentifier) else { continue }

                var matchScore = 0
                var constraintBonus = 0
                let normalizedSongTitle = normalizedSongMatchTitle(song.title)
                if let validatedMaxDxScore,
                   song.sheets.contains(where: {
                       guard let total = $0.total else { return false }
                       return total * 3 == validatedMaxDxScore
                   }) {
                    constraintBonus += 20
                }

                for searchCandidate in variants {
                    let normalizedSearchTitle = normalizedSongMatchTitle(searchCandidate)
                    if normalizedSongTitle == normalizedSearchTitle { matchScore = 110; break }
                    if song.title.localizedCaseInsensitiveCompare(searchCandidate) == .orderedSame { matchScore = 100; break }
                    if song.aliases.contains(where: { $0.localizedCaseInsensitiveCompare(searchCandidate) == .orderedSame }) { matchScore = max(matchScore, 95); break }
                    if normalizedSongTitle.hasPrefix(normalizedSearchTitle) && normalizedSongTitle != normalizedSearchTitle {
                        matchScore = max(matchScore, isUtage ? 45 : 80)
                        continue
                    }
                    if normalizedSearchTitle.hasPrefix(normalizedSongTitle) && normalizedSongTitle != normalizedSearchTitle {
                        matchScore = max(matchScore, isUtage ? 40 : 75)
                        continue
                    }
                    if song.title.localizedStandardContains(searchCandidate) { matchScore = max(matchScore, isUtage ? 35 : 80); continue }
                    if searchCandidate.localizedStandardContains(song.title) { matchScore = max(matchScore, isUtage ? 30 : 75); continue }
                    if song.aliases.contains(where: { $0.localizedStandardContains(searchCandidate) }) { matchScore = max(matchScore, 70); continue }
                    if let keywords = song.searchKeywords, keywords.localizedStandardContains(searchCandidate) { matchScore = max(matchScore, 60); continue }

                    let dist = levenshteinDistance(cleaned, song.title)
                    let maxLen = max(cleaned.count, song.title.count)
                    if dist <= max(2, maxLen / 3) { matchScore = max(matchScore, 50 - dist); continue }

                    for alias in song.aliases {
                        let aliasDist = levenshteinDistance(cleaned, alias)
                        if aliasDist <= max(2, max(cleaned.count, alias.count) / 3) {
                            matchScore = max(matchScore, 45 - aliasDist)
                            break
                        }
                    }
                }

                if matchScore == 0 && song.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    if hasMaxDxScore || (hasTotalNotes && hasDifficulty) { matchScore = 30 }
                }

                let totalScore = matchScore + constraintBonus
                if totalScore > 0 {
                    matchedSongs.append((song: song, score: totalScore))
                    seenIds.insert(song.songIdentifier)
                }
            }
        }

        if matchedSongs.isEmpty && allCandidates.allSatisfy({ $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) && hasAnyValidation {
            for song in filteredSongs where !seenIds.contains(song.songIdentifier) {
                let score: Int
                if let validatedMaxDxScore {
                    let exactMaxDxMatch = song.sheets.contains(where: {
                        guard let total = $0.total else { return false }
                        return total * 3 == validatedMaxDxScore
                    })
                    score = exactMaxDxMatch ? 50 : 10
                } else {
                    score = 10
                }
                matchedSongs.append((song: song, score: score))
            }
        }

        matchedSongs.sort { lhs, rhs in
            if lhs.score != rhs.score {
                return lhs.score > rhs.score
            }
            return lhs.song.title.count < rhs.song.title.count
        }
        return matchedSongs.map { $0.song }
    }

    // MARK: - Fast Camera Frame Matching

    // The fast path repeats the same validation without materializing score-entry state.
    // swiftlint:disable:next cyclomatic_complexity function_body_length
    private func matchSongsForCameraFrame(_ recognition: MLScoreResult) -> [String] {
        let titleCandidates = recognition.titleCandidates
        let title = recognition.title
        let difficulty = recognition.difficulty
        let level = recognition.level
        let maxCombo = recognition.maxCombo
        let dxScore = recognition.dxScore
        let maxDxScore = recognition.maxDxScore
        let type = recognition.type
        let kanji = recognition.kanji
        let rawCandidates = ([title] + titleCandidates.map { Optional($0) }).compactMap { $0 }
        var allCandidates = titleCandidates
        if let exactTitle = title { allCandidates.insert(exactTitle, at: 0) }
        let isUtage = type?.lowercased() == "utage"
        if isUtage { allCandidates = allCandidates.map { stripUtagePrefix($0) } }
        let explicitTitleKanji = rawCandidates.compactMap { extractUtagePrefixKanji(from: $0) }.first
        let hasExplicitUtagePrefix = explicitTitleKanji != nil
        let derivedTotalNotes: Int? = { if let maxDx = maxDxScore, maxDx > 0 { return maxDx / 3 }; return maxCombo }()
        let hasMaxDxScore = (maxDxScore ?? 0) > 0
        let hasAnyValidation = (difficulty != nil && !isUtage) ||
            level.map { $0 >= 1 && $0 <= 15 } == true ||
            (derivedTotalNotes ?? 0) > 0 ||
            (dxScore ?? 0) > 0 ||
            hasMaxDxScore ||
            kanji?.isEmpty == false

        // Every available OCR signal is optional and narrows compatible chart candidates.
        // swiftlint:disable:next cyclomatic_complexity
        func sheetOK(_ sheet: Sheet) -> Bool {
            if !ServerChartPolicy.isPlayable(sheet, on: activeServer) { return false }
            if !ScannerNoteCountValidator.isCompatible(maxDxScore: maxDxScore, sheetTotal: sheet.total) {
                return false
            }
            if isUtage {
                if sheet.type.lowercased() != "utage" { return false }
                if let kanji, !kanji.isEmpty, !sheet.difficulty.contains(kanji) { return false }
                if hasExplicitUtagePrefix, let song = sheet.song, !songHasExplicitUtagePrefix(song, kanji: explicitTitleKanji) { return false }
                if let tn = derivedTotalNotes, tn > 0 { if let total = sheet.total, total != tn { return false } }
                if let dx = dxScore, dx > 0 { if let total = sheet.total, total * 3 < dx { return false } }
                return true
            }
            if sheet.type.lowercased() == "utage" { return false }
            if let type, type.lowercased() != "utage" {
                if sheet.type.lowercased() != type.lowercased() { return false }
            }
            if let diff = difficulty { if sheet.difficulty.lowercased() != diff.lowercased() { return false } }
            if let lv = level, lv >= 1, lv <= 15 {
                let metadata = ServerChartPolicy.metadata(for: sheet, on: activeServer)
                let sl = metadata.ratingLevel ?? 0
                if sl > 0 { if Int(sl) != Int(lv) { return false } } else { if Int(metadata.level) != Int(lv) { return false } }
            }
            if let tn = derivedTotalNotes, tn > 0 { if let st = sheet.total, st != tn { return false } }
            if let dx = dxScore, dx > 0 { if let total = sheet.total, total * 3 < dx { return false } }
            if let maxDx = maxDxScore, maxDx > 0 { if let total = sheet.total, total * 3 != maxDx { return false } }
            return true
        }

        var frameMatches: [String] = []
        for candidate in allCandidates {
            let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            guard cleaned.count >= 2 else { continue }
            var foundFast = false
            let normalizedCleaned = normalizedSongMatchTitle(cleaned)
            var exactTitleMatches: [String] = []
            for song in songs {
                if !song.sheets.contains(where: { sheetOK($0) }) { continue }
                let normalizedSongTitle = normalizedSongMatchTitle(song.title)
                if normalizedSongTitle == normalizedCleaned {
                    exactTitleMatches.append(song.songIdentifier)
                    foundFast = true
                    if exactTitleMatches.count > 3 { break }
                }
            }
            if !exactTitleMatches.isEmpty {
                frameMatches.append(contentsOf: exactTitleMatches)
            } else {
                for song in songs {
                    if !song.sheets.contains(where: { sheetOK($0) }) { continue }
                    let normalizedSongTitle = normalizedSongMatchTitle(song.title)
                    if (!isUtage && (song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title))) ||
                        (isUtage && normalizedSongTitle.hasPrefix(normalizedCleaned)) {
                        frameMatches.append(song.songIdentifier); foundFast = true
                        if frameMatches.count > 3 { break }
                    }
                }
            }
            if !foundFast && cleaned.count > 4 {
                for song in songs {
                    if !song.sheets.contains(where: { sheetOK($0) }) { continue }
                    if fuzzyMatch(cleaned, song.title) { frameMatches.append(song.songIdentifier); if frameMatches.count > 3 { break } }
                }
            }
            if !frameMatches.isEmpty { break }
        }
        if frameMatches.isEmpty && hasMaxDxScore {
            for song in songs { if song.sheets.contains(where: { sheetOK($0) }) { frameMatches.append(song.songIdentifier); if frameMatches.count > 3 { break } } }
        }
        if frameMatches.isEmpty && !hasAnyValidation {
            for candidate in allCandidates {
                let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                guard cleaned.count >= 2 else { continue }
                for song in songs {
                    let std = song.sheets.filter { $0.type.lowercased() != "utage" }
                    if std.isEmpty || std.allSatisfy({ !$0.regionJp && !$0.regionIntl && !$0.regionCn }) {
                        continue
                    }
                    if song.title.localizedCaseInsensitiveContains(cleaned)
                        || cleaned.localizedCaseInsensitiveContains(song.title) {
                        frameMatches.append(song.songIdentifier)
                        if frameMatches.count > 3 { break }
                    }
                }
                if !frameMatches.isEmpty { break }
            }
        }
        return frameMatches
    }

    // MARK: - Utilities

    private func levenshteinDistance(_ s1: String, _ s2: String) -> Int {
        let firstCharacters = Array(s1.lowercased())
        let secondCharacters = Array(s2.lowercased())
        if firstCharacters.isEmpty { return secondCharacters.count }
        if secondCharacters.isEmpty { return firstCharacters.count }

        var distances = [[Int]](
            repeating: [Int](repeating: 0, count: secondCharacters.count + 1),
            count: firstCharacters.count + 1
        )
        for rowIndex in 0...firstCharacters.count {
            distances[rowIndex][0] = rowIndex
        }
        for columnIndex in 0...secondCharacters.count {
            distances[0][columnIndex] = columnIndex
        }
        for rowIndex in 1...firstCharacters.count {
            for columnIndex in 1...secondCharacters.count {
                distances[rowIndex][columnIndex] = firstCharacters[rowIndex - 1] == secondCharacters[columnIndex - 1]
                    ? distances[rowIndex - 1][columnIndex - 1]
                    : min(
                        distances[rowIndex - 1][columnIndex] + 1,
                        distances[rowIndex][columnIndex - 1] + 1,
                        distances[rowIndex - 1][columnIndex - 1] + 1
                    )
            }
        }
        return distances[firstCharacters.count][secondCharacters.count]
    }

    private func showFeedback(_ message: String) {
        feedbackDismissTask?.cancel()
        withAnimation { photoImportFeedback = message }
        feedbackDismissTask = Task { @MainActor in
            do {
                try await Task.sleep(for: .seconds(2.5))
            } catch is CancellationError {
                return
            } catch {
                return
            }

            guard photoImportFeedback == message else { return }
            withAnimation { photoImportFeedback = nil }
            feedbackDismissTask = nil
        }
    }

    private func fuzzyMatch(_ s1: String, _ s2: String) -> Bool {
        let t1 = s1.lowercased().filter { !$0.isWhitespace }, t2 = s2.lowercased().filter { !$0.isWhitespace }
        if abs(t1.count - t2.count) > 2 { return false }
        return levenshteinDistance(t1, t2) <= max(1, t1.count / 4)
    }

    // MARK: - Header

    @ViewBuilder
    private func headerView() -> some View {
        HStack(spacing: 16) {
            Spacer()
            PhotosPicker(selection: $selectedPhotoItem, matching: .images) {
                Image(systemName: "photo.on.rectangle.angled")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(.white)
                    .padding(10)
                    .background(.ultraThinMaterial, in: Circle())
            }
            .disabled(!modelController.canRecognize)
            .opacity(modelController.canRecognize ? 1 : 0.5)
            .accessibilityLabel(Text("scanner.library.button"))
            .accessibilityHint(Text("scanner.library.hint"))
        }
        .padding()
    }

    private func triggerPhotoCapture() {
        guard !isSavingPhoto else { return }
        UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        withAnimation(.easeOut(duration: 0.1)) { showFlashOverlay = true }
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(100))
            withAnimation(.easeIn(duration: 0.2)) { self.showFlashOverlay = false }
        }
        isSavingPhoto = true
        NotificationCenter.default.post(name: NSNotification.Name("TakeScannerPhoto"), object: nil)
    }

    private func handleCapturedScannerPhoto(_ result: Result<Data, Error>) {
        guard case .success(let data) = result else {
            isSavingPhoto = false
            showFeedback(String(localized: "scanner.photo.error"))
            return
        }

        let title = recognizedSong?.title
        var tags: [String] = []
        if let title { tags.append(title) }
        if let song = recognizedSong, let diff = recognizedDifficulty {
            let type = recognizedType ?? "dx"
            if let sheet = matchedSheet(for: song, diff: diff, type: type) {
                tags.append("LV\(ServerChartPolicy.metadata(for: sheet, on: activeServer).displayLevel)")
            }
            tags.append(diff.uppercased()); tags.append(type.uppercased())
        }
        if let rate = recognizedRate { tags.append(RatingUtils.calculateRank(achievement: rate)) }
        Task { @MainActor in
            defer { isSavingPhoto = false }
            do {
                try await PhotoService.shared.savePhotoDataWithMetadata(data, title: title, tags: tags)
                showFeedback(String(localized: "scanner.photo.saved"))
            } catch {
                showFeedback(String(localized: "scanner.photo.error"))
            }
        }
    }

    @ViewBuilder
    private func debugOverlayView() -> some View {
        ScannerDebugOverlayView(showScannerBoundingBox: showScannerBoundingBox, debugBoxes: debugBoxes)
    }

    @ViewBuilder
    private func resultView() -> some View {
        if let song = recognizedSong {
            if recognizedClass != .score || canPresentCurrentScoreResult {
                ScannerResultCardView(
                    song: song,
                    recognizedClass: recognizedClass,
                    recognizedType: recognizedType,
                    recognizedDifficulty: recognizedDifficulty,
                    recognizedRate: recognizedRate,
                    resolvedSheet: recognizedClass == .score ? resolvedCurrentScoreSheet : nil,
                    server: activeServer,
                    onScoreEntryTap: { isShowingScoreEntry = true },
                    onResetTap: resetScanner
                )
                    .equatable()
            }
        }
    }

    // MARK: - Camera Frame Handling

    private func handleCameraFrame(_ image: UIImage) {
        guard !isShowingScoreEntry, modelController.canRecognize else { return }
        pendingFrameImage = image

        guard frameAnalysisTask == nil else { return }

        frameAnalysisTask = Task { @MainActor in
            defer {
                frameAnalysisTask = nil
                pendingFrameImage = nil
            }

            while !Task.isCancelled {
                guard let nextFrame = pendingFrameImage else { break }
                pendingFrameImage = nil
                await analyzeCameraFrame(nextFrame)
            }
        }
    }

    // Choose-screen and score-screen models intentionally share one serialized frame loop.
    // swiftlint:disable:next cyclomatic_complexity
    private func analyzeCameraFrame(_ image: UIImage) async {
        guard !isShowingScoreEntry else { return }

        guard modelController.canRecognize else { return }
        let imageType: MaimaiImageType
        do {
            imageType = try await MLDistinguishProcessor.shared.classify(image)
        } catch {
            await modelController.reportRuntimeFailure(error)
            return
        }
        guard !Task.isCancelled, !isShowingScoreEntry else { return }

        if imageType == .unknown {
            updateUIWithResults(
                songIds: [],
                recognition: MLScoreResult(),
                imageClass: .unknown
            )
            return
        }

        if imageType == .choose {
            let recognition: MLChooseResult
            do {
                recognition = try await MLChooseProcessor.shared.process(image)
            } catch {
                await modelController.reportRuntimeFailure(error)
                return
            }
            guard !Task.isCancelled, !isShowingScoreEntry else { return }

            var frameMatches: [String] = []
            var allCandidates = recognition.titleCandidates
            if let exactTitle = recognition.title { allCandidates.insert(exactTitle, at: 0) }
            for candidate in allCandidates {
                let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                guard cleaned.count >= 2 else { continue }
                var foundFast = false
                for song in songs {
                    if song.title.localizedCaseInsensitiveContains(cleaned)
                        || cleaned.localizedCaseInsensitiveContains(song.title) {
                        frameMatches.append(song.songIdentifier)
                        foundFast = true
                        if frameMatches.count > 3 { break }
                    }
                }
                if !foundFast && cleaned.count > 4 {
                    for song in songs where fuzzyMatch(cleaned, song.title) {
                        frameMatches.append(song.songIdentifier)
                        if frameMatches.count > 3 { break }
                    }
                }
                if !frameMatches.isEmpty { break }
            }
            var chooseResult = MLScoreResult()
            chooseResult.boxes = recognition.boxes
            updateUIWithResults(
                songIds: frameMatches,
                recognition: chooseResult,
                imageClass: .choose
            )
        } else {
            let recognition: MLScoreResult
            do {
                recognition = try await MLScoreProcessor.shared.process(image)
            } catch {
                await modelController.reportRuntimeFailure(error)
                return
            }
            guard !Task.isCancelled, !isShowingScoreEntry else { return }

            let matchedSongIds = matchSongsForCameraFrame(recognition)
                .filter { songId in
                    guard let song = songs.first(where: { $0.songIdentifier == songId }) else { return false }
                    return canPresentScoreResult(
                        for: song,
                        difficulty: recognition.difficulty,
                        type: recognition.type,
                        kanji: recognition.kanji,
                        maxDxScore: recognition.maxDxScore,
                        dxScore: recognition.dxScore
                    )
                }
            updateUIWithResults(
                songIds: matchedSongIds,
                recognition: recognition,
                imageClass: .score
            )
        }
    }

    // Stabilization combines independent OCR signals before publishing a coherent result.
    // swiftlint:disable:next cyclomatic_complexity
    private func updateUIWithResults(
        songIds: [String],
        recognition: MLScoreResult,
        imageClass: MaimaiImageType
    ) {
        debugBoxes = recognition.boxes
        for songId in recognitionBuffer.keys {
            recognitionBuffer[songId, default: 0] -= 1
            if recognitionBuffer[songId, default: 0] <= 0 {
                recognitionBuffer.removeValue(forKey: songId)
            }
        }
        for songId in songIds {
            recognitionBuffer[songId, default: 0] += 6
            if recognitionBuffer[songId, default: 0] > 18 {
                recognitionBuffer[songId] = 18
            }
        }
        if let topCandidate = recognitionBuffer.max(by: { $0.value < $1.value }), topCandidate.value > 15 {
            if let song = songs.first(where: { $0.songIdentifier == topCandidate.key }) {
                let newClass = songIds.contains(song.songIdentifier) ? imageClass : recognizedClass
                if recognizedSong?.songIdentifier != song.songIdentifier || recognizedClass != newClass {
                    withAnimation(.spring(response: 0.3, dampingFraction: 0.7)) {
                        self.recognizedSong = song; if newClass != .unknown { self.recognizedClass = newClass }; self.isLocked = true
                    }
                }
                self.lastSeenDate = Date()
            }
        }
        if isLocked {
            if let rate = recognition.rate {
                rateBuffer.append(rate)
                if rateBuffer.count > 5 { rateBuffer.removeFirst() }
                let counts = rateBuffer.reduce(into: [:]) { $0[$1, default: 0] += 1 }
                if let (best, count) = counts.max(by: { $0.value < $1.value }),
                   count >= stabilizationThreshold {
                    recognizedRate = best
                } else if rateBuffer.count < stabilizationThreshold {
                    recognizedRate = rateBuffer.last
                }
            }
            if let difficulty = recognition.difficulty { recognizedDifficulty = difficulty }
            if let type = recognition.type { recognizedType = type }
            if let dxScore = recognition.dxScore {
                dxScoreBuffer.append(dxScore)
                if dxScoreBuffer.count > 5 { dxScoreBuffer.removeFirst() }
                let counts = dxScoreBuffer.reduce(into: [:]) { $0[$1, default: 0] += 1 }
                if let (best, count) = counts.max(by: { $0.value < $1.value }),
                   count >= stabilizationThreshold {
                    recognizedDxScore = best
                } else if dxScoreBuffer.count < stabilizationThreshold {
                    recognizedDxScore = dxScoreBuffer.last
                }
            }
            if let maxDxScore = recognition.maxDxScore {
                maxDxScoreBuffer.append(maxDxScore)
                if maxDxScoreBuffer.count > 5 { maxDxScoreBuffer.removeFirst() }
                let counts = maxDxScoreBuffer.reduce(into: [:]) { $0[$1, default: 0] += 1 }
                if let (best, count) = counts.max(by: { $0.value < $1.value }),
                   count >= stabilizationThreshold {
                    recognizedMaxDxScore = best
                } else if maxDxScoreBuffer.count < stabilizationThreshold {
                    recognizedMaxDxScore = maxDxScoreBuffer.last
                }
            }
            if let comboStatus = recognition.comboStatus { recognizedFC = comboStatus }
            if let syncStatus = recognition.syncStatus { recognizedFS = syncStatus }
            if let level = recognition.level { recognizedLevel = level }
            if let maxCombo = recognition.maxCombo { recognizedMaxCombo = maxCombo }
            if let kanji = recognition.kanji { recognizedKanji = kanji }
        }
        if isLocked && !isShowingScoreEntry { if Date().timeIntervalSince(lastSeenDate) > 4.0 { withAnimation { resetScanner() } } }
    }

    private func resetScanner() {
        recognizedSong = nil; recognizedRate = nil; recognizedDifficulty = nil; recognizedType = nil
        recognizedDxScore = nil; recognizedMaxDxScore = nil; recognizedFC = nil; recognizedFS = nil
        recognizedLevel = nil; recognizedMaxCombo = nil; recognizedKanji = nil; recognizedClass = .unknown
        recognitionBuffer.removeAll(); rateBuffer.removeAll(); dxScoreBuffer.removeAll()
        maxDxScoreBuffer.removeAll(); debugBoxes.removeAll(); isLocked = false
    }

    private func canPresentScoreResult(for song: Song, difficulty: String?, type: String?, kanji: String?, maxDxScore: Int?, dxScore: Int?) -> Bool {
        let normalizedDifficulty = difficulty?.trimmingCharacters(in: .whitespacesAndNewlines)
        let hasDifficulty = normalizedDifficulty?.isEmpty == false
        let hasMaxDxScore = (maxDxScore ?? 0) > 0
        guard hasDifficulty || hasMaxDxScore else {
            return true
        }

        return resolvedScoreSheet(
            for: song,
            difficulty: normalizedDifficulty,
            type: type,
            kanji: kanji,
            maxDxScore: maxDxScore,
            dxScore: dxScore
        ) != nil
    }

    private func resolvedScoreSheet(for song: Song, difficulty: String?, type: String?, kanji: String?, maxDxScore: Int?, dxScore: Int?) -> Sheet? {
        let normalizedDifficulty = difficulty?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let normalizedType = type?.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()

        if normalizedType == "utage" {
            return matchUtageSheet(for: song, kanji: kanji, maxDxScore: maxDxScore, dxScore: dxScore)
        }

        let filteredCandidates = song.sheets.filter { sheet in
            if !ServerChartPolicy.isPlayable(sheet, on: activeServer) { return false }
            if !ScannerNoteCountValidator.isCompatible(maxDxScore: maxDxScore, sheetTotal: sheet.total) {
                return false
            }
            let sheetType = sheet.type.lowercased()
            if sheetType == "utage" { return false }
            if let normalizedType, !normalizedType.isEmpty, sheetType != normalizedType { return false }
            if let normalizedDifficulty, !normalizedDifficulty.isEmpty, sheet.difficulty.lowercased() != normalizedDifficulty { return false }
            return true
        }

        guard !filteredCandidates.isEmpty else { return nil }
        if filteredCandidates.count == 1 { return filteredCandidates.first }

        if let maxDxScore, maxDxScore > 0 {
            let targetTotal = maxDxScore / 3
            if let exact = filteredCandidates.first(where: { $0.total == targetTotal }) {
                return exact
            }
        }

        if let dxScore, dxScore > 0 {
            let dxCandidates = filteredCandidates.filter { sheet in
                guard let total = sheet.total else { return true }
                return total * 3 >= dxScore
            }

            if dxCandidates.count == 1 { return dxCandidates.first }
            if let best = dxCandidates.min(by: { lhs, rhs in
                guard let lhsTotal = lhs.total, let rhsTotal = rhs.total else {
                    return lhs.total != nil
                }
                return abs(lhsTotal * 3 - dxScore) < abs(rhsTotal * 3 - dxScore)
            }) {
                return best
            }
        }

        return filteredCandidates.first
    }

    private func matchedSheet(for song: Song, diff: String, type: String) -> Sheet? {
        resolvedScoreSheet(
            for: song,
            difficulty: diff,
            type: type,
            kanji: recognizedKanji,
            maxDxScore: recognizedMaxDxScore,
            dxScore: recognizedDxScore
        )
    }
}
