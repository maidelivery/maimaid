import SwiftUI
@preconcurrency import AVFoundation
import Vision
import SwiftData
import PhotosUI
import os

// Add this extension near the top of the file, after imports

// MARK: - OCR Error Correction

extension ScannerView {
    /// Common OCR misrecognition patterns for Japanese game titles
    private static let ocrSubstitutions: [Character: [Character]] = [
        // Latin characters commonly confused
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
        
        // Japanese characters commonly confused
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
        "畫": ["画"],
    ]
    
    /// Generate OCR correction candidates for a string
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
                
                // Also check reverse - if OCR produced wrong char, original might be the correct one
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
    
    /// Check if two strings are similar enough considering common OCR errors
    private func isSimilarWithOCRErrors(_ s1: String, _ s2: String, threshold: Int? = nil) -> Bool {
        // First try exact match
        if s1.localizedCaseInsensitiveCompare(s2) == .orderedSame {
            return true
        }
        
        // Try with OCR variants
        let s1Variants = generateOCRVariants(s1)
        let s2Lower = s2.lowercased()
        
        for variant in s1Variants {
            let variantLower = variant.lowercased()
            if s2Lower.localizedCaseInsensitiveContains(variantLower) ||
               variantLower.localizedCaseInsensitiveContains(s2Lower) {
                return true
            }
        }
        
        // Try fuzzy match with adaptive threshold
        let dist = levenshteinDistance(s1, s2)
        let maxLen = max(s1.count, s2.count)
        let adaptiveThreshold = threshold ?? max(2, maxLen / 3)
        
        return dist <= adaptiveThreshold
    }
    
    /// Strip utage prefix from title (e.g., 【宴】Title -> Title, [祝]Title -> Title)
    private func stripUtagePrefix(_ title: String) -> String {
        var result = title
        
        // Pattern 1: 【xxx】prefix (full-width brackets with kanji)
        if let range = result.range(of: "^【[^】]+】", options: .regularExpression) {
            result.removeSubrange(range)
        }
        
        // Pattern 2: [xxx] prefix (half-width brackets with kanji)
        if let range = result.range(of: "^\\[[^\\]]+\\]", options: .regularExpression) {
            result.removeSubrange(range)
        }
        
        // Pattern 3: Full-width brackets with space
        if let range = result.range(of: "^【[^】]+】\\s*", options: .regularExpression) {
            result.removeSubrange(range)
        }
        
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    /// Match utage sheets for a song, using dxScore to distinguish if multiple exist
    private func matchUtageSheet(for song: Song, dxScore: Int?) -> Sheet? {
        let utageSheets = song.sheets.filter { $0.type.lowercased() == "utage" }
        
        // If only one utage sheet, return it
        if utageSheets.count == 1 {
            return utageSheets.first
        }
        
        // If multiple utage sheets and dxScore available, find matching one
        if let dx = dxScore, dx > 0 {
            // Find sheet where total * 3 is closest to dxScore
            let matchingSheet = utageSheets.min { sheet1, sheet2 in
                guard let total1 = sheet1.total, let total2 = sheet2.total else {
                    // Prefer sheets with total info
                    return sheet1.total != nil
                }
                let maxDx1 = total1 * 3
                let maxDx2 = total2 * 3
                return abs(maxDx1 - dx) < abs(maxDx2 - dx)
            }
            return matchingSheet
        }
        
        // Fallback: return first utage sheet
        return utageSheets.first
    }
}


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
    @State private var recognizedLevel: Double? = nil
    @State private var recognizedMaxCombo: Int? = nil
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
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 20)
                        .padding(.vertical, 12)
                        .background(.ultraThinMaterial, in: Capsule())
                        .padding(.bottom, 8)
                    }
                    
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
                    let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
                    let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { sheet in
                        !sheet.regionJp && !sheet.regionIntl && !sheet.regionCn
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
            
            if let targetCandidate = allCandidates.first {
                matchedSongs.sort { a, b in
                    let aIsExact = a.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    let bIsExact = b.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    if aIsExact != bIsExact { return aIsExact }
                    
                    let aAliasExact = a.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    let bAliasExact = b.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    if aAliasExact != bAliasExact { return aAliasExact }
                    
                    let aDist = levenshteinDistance(a.title, targetCandidate)
                    let bDist = levenshteinDistance(b.title, targetCandidate)
                    if aDist != bDist { return aDist < bDist }
                    
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
            
            let matchedSongs = matchSongsWithFilters(
                titleCandidates: recognition.titleCandidates,
                title: recognition.title,
                difficulty: recognition.difficulty,
                level: recognition.level,
                maxCombo: recognition.maxCombo,
                dxScore: recognition.dxScore,
                type: recognition.type
            )
            
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
                self.recognizedLevel = recognition.level
                self.recognizedMaxCombo = recognition.maxCombo
                self.debugBoxes = recognition.boxes
                self.isLocked = true
                self.lastSeenDate = Date()
            } else {
                showFeedback(String(localized: "scanner.error.title"))
            }
        }
    }
    
    // MARK: - Song Matching with Filters
    
    private func matchSongsWithFilters(
    titleCandidates: [String],
    title: String?,
    difficulty: String?,
    level: Double?,
    maxCombo: Int?,
    dxScore: Int?,
    type: String?
) -> [Song] {
    var allCandidates = titleCandidates
    if let exactTitle = title {
        allCandidates.insert(exactTitle, at: 0)
    }
    
    // Check if this is utage type
    let isUtage = type?.lowercased() == "utage"
    
    // For utage, strip prefix from title candidates
    if isUtage {
        allCandidates = allCandidates.map { stripUtagePrefix($0) }
    }
    
    // Check which validation conditions are available
    let hasDifficulty = difficulty != nil && !isUtage // Don't use difficulty for utage
    let hasLevel = level != nil && level! >= 1 && level! <= 15
    let hasMaxCombo = maxCombo != nil
    let hasDxScore = dxScore != nil && dxScore! > 0
    
    var filteredSongs = songs.filter { song in
        let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
        let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { sheet in
            !sheet.regionJp && !sheet.regionIntl && !sheet.regionCn
        }
        if isDeleted { return false }
        
        // For utage, check if song has utage sheets
        if isUtage {
            let utageSheets = song.sheets.filter { $0.type.lowercased() == "utage" }
            if utageSheets.isEmpty { return false }
            
            // If multiple utage sheets and we have dxScore, check which matches
            if utageSheets.count > 1 && hasDxScore {
                return utageSheets.contains { sheet in
                    guard let total = sheet.total else { return false }
                    let maxDx = total * 3
                    return maxDx >= dxScore!
                }
            }
            return true
        }
        
        return song.sheets.contains { sheet in
            if let diff = difficulty {
                if sheet.difficulty.lowercased() != diff.lowercased() {
                    return false
                }
            }
            
            if let lv = level, lv >= 1, lv <= 15 {
                let sheetLevel = sheet.internalLevelValue ?? sheet.levelValue ?? 0
                if sheetLevel > 0 {
                    if sheetLevel < lv {
                        return false
                    }
                } else {
                    let levelStr = sheet.level
                    let intLevel = Int(lv)
                    if Int(levelStr) != intLevel {
                        return false
                    }
                }
            }
            
            if let combo = maxCombo {
                if let total = sheet.total, total != combo {
                    return false
                }
            }
            
            if let dx = dxScore, dx > 0 {
                guard let total = sheet.total else {
                    return false
                }
                let maxDxScore = total * 3
                if maxDxScore < dx {
                    return false
                }
            }
            
            return true
        }
    }
    
    let hasAnyValidation = hasDifficulty || hasLevel || hasMaxCombo || hasDxScore
    
    if filteredSongs.isEmpty && !hasAnyValidation {
        filteredSongs = songs.filter { song in
            let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
            let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { sheet in
                !sheet.regionJp && !sheet.regionIntl && !sheet.regionCn
            }
            return !isDeleted
        }
    }
    
    // MARK: - Enhanced Search with OCR Error Correction
    
    var matchedSongs: [(song: Song, score: Int)] = []
    var seenIds = Set<String>()
    
    for candidate in allCandidates {
        let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty else { continue }
        
        // Generate OCR variants for the candidate
        let candidates = generateOCRVariants(cleaned, maxVariants: 6)
        
        for song in filteredSongs {
            guard !seenIds.contains(song.songIdentifier) else { continue }
            
            var matchScore = 0
            
            for searchCandidate in candidates {
                let searchLower = searchCandidate.lowercased()
                
                // Exact title match (highest priority)
                if song.title.localizedCaseInsensitiveCompare(searchCandidate) == .orderedSame {
                    matchScore = 100
                    break
                }
                
                // Exact alias match
                if song.aliases.contains(where: { $0.localizedCaseInsensitiveCompare(searchCandidate) == .orderedSame }) {
                    matchScore = max(matchScore, 95)
                    break
                }
                
                // Title contains candidate
                if song.title.localizedCaseInsensitiveContains(searchLower) {
                    matchScore = max(matchScore, 80)
                    continue
                }
                
                // Candidate contains title
                if searchLower.localizedCaseInsensitiveContains(song.title.lowercased()) {
                    matchScore = max(matchScore, 75)
                    continue
                }
                
                // Alias contains candidate
                if song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(searchLower) }) {
                    matchScore = max(matchScore, 70)
                    continue
                }
                
                // Search keywords match
                if let keywords = song.searchKeywords, keywords.localizedCaseInsensitiveContains(searchLower) {
                    matchScore = max(matchScore, 60)
                    continue
                }
                
                // Fuzzy match with adaptive threshold based on string length
                let dist = levenshteinDistance(cleaned, song.title)
                let maxLen = max(cleaned.count, song.title.count)
                let adaptiveThreshold = max(2, maxLen / 3)
                
                if dist <= adaptiveThreshold {
                    // Score based on similarity (higher score for lower distance)
                    matchScore = max(matchScore, 50 - dist)
                    continue
                }
                
                // Also check aliases with fuzzy match
                for alias in song.aliases {
                    let aliasDist = levenshteinDistance(cleaned, alias)
                    let aliasMaxLen = max(cleaned.count, alias.count)
                    let aliasThreshold = max(2, aliasMaxLen / 3)
                    
                    if aliasDist <= aliasThreshold {
                        matchScore = max(matchScore, 45 - aliasDist)
                        break
                    }
                }
            }
            
            if matchScore > 0 {
                matchedSongs.append((song: song, score: matchScore))
                seenIds.insert(song.songIdentifier)
            }
        }
    }
    
    // Sort by match score (descending), then by title length
    matchedSongs.sort { a, b in
        if a.score != b.score {
            return a.score > b.score
        }
        return a.song.title.count < b.song.title.count
    }
    
    return matchedSongs.map { $0.song }
}
    
    // MARK: - Fast Matching for Camera Frames
    
    private func matchSongsForCameraFrame(
        titleCandidates: [String],
        title: String?,
        difficulty: String?,
        level: Double?,
        maxCombo: Int?,
        dxScore: Int?,
        type: String?
    ) -> [String] {
        var allCandidates = titleCandidates
        if let exactTitle = title {
            allCandidates.insert(exactTitle, at: 0)
        }
        
        // Check if this is utage type
        let isUtage = type?.lowercased() == "utage"
        
        // For utage, strip prefix from title candidates
        if isUtage {
            allCandidates = allCandidates.map { stripUtagePrefix($0) }
        }
        
        // Check which validation conditions are available
        let hasDifficulty = difficulty != nil && !isUtage // Don't use difficulty for utage
        let hasLevel = level != nil && level! >= 1 && level! <= 15
        let hasMaxCombo = maxCombo != nil
        let hasDxScore = dxScore != nil && dxScore! > 0
        let hasAnyValidation = hasDifficulty || hasLevel || hasMaxCombo || hasDxScore
        
        var frameMatches: [String] = []
        
        for candidate in allCandidates {
            let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
            guard cleaned.count >= 2 else { continue }
            
            var foundFast = false
            for song in songs {
                let hasMatchingSheet = song.sheets.contains { sheet in
                    // For utage, only match utage sheets
                    if isUtage {
                        if sheet.type.lowercased() != "utage" { return false }
                        
                        // If multiple utage sheets and we have dxScore, check which matches
                        let utageSheets = song.sheets.filter { $0.type.lowercased() == "utage" }
                        if utageSheets.count > 1 && hasDxScore {
                            guard let total = sheet.total else { return false }
                            let maxDx = total * 3
                            return maxDx >= dxScore!
                        }
                        return true
                    }
                    
                    if let diff = difficulty {
                        if sheet.difficulty.lowercased() != diff.lowercased() {
                            return false
                        }
                    }
                    
                    // Level filter: internalLevelValue >= recognized level
                    if let lv = level, lv >= 1, lv <= 15 {
                        let sheetLevel = sheet.internalLevelValue ?? sheet.levelValue ?? 0
                        if sheetLevel > 0 {
                            if sheetLevel < lv {
                                return false
                            }
                        } else {
                            let levelStr = sheet.level
                            let intLevel = Int(lv)
                            if Int(levelStr) != intLevel {
                                return false
                            }
                        }
                    }
                    
                    // MaxCombo filter
                    if let combo = maxCombo {
                        if let total = sheet.total, total != combo {
                            return false
                        }
                    }
                    
                    // DX Score filter: sheet.total * 3 >= dxScore
                    if let dx = dxScore, dx > 0 {
                        guard let total = sheet.total else {
                            return false
                        }
                        let maxDxScore = total * 3
                        if maxDxScore < dx {
                            return false
                        }
                    }
                    
                    return true
                }
                
                if !hasMatchingSheet { continue }
                
                if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) {
                    frameMatches.append(song.songIdentifier)
                    foundFast = true
                    if frameMatches.count > 3 { break }
                }
            }
            
            if !foundFast && cleaned.count > 4 {
                for song in songs {
                    let hasMatchingSheet = song.sheets.contains { sheet in
                        // For utage, only match utage sheets
                        if isUtage {
                            if sheet.type.lowercased() != "utage" { return false }
                            
                            let utageSheets = song.sheets.filter { $0.type.lowercased() == "utage" }
                            if utageSheets.count > 1 && hasDxScore {
                                guard let total = sheet.total else { return false }
                                let maxDx = total * 3
                                return maxDx >= dxScore!
                            }
                            return true
                        }
                        
                        if let diff = difficulty {
                            if sheet.difficulty.lowercased() != diff.lowercased() { return false }
                        }
                        if let lv = level, lv >= 1, lv <= 15 {
                            let sheetLevel = sheet.internalLevelValue ?? sheet.levelValue ?? 0
                            if sheetLevel > 0 {
                                if sheetLevel < lv { return false }
                            } else {
                                let levelStr = sheet.level
                                let intLevel = Int(lv)
                                if Int(levelStr) != intLevel { return false }
                            }
                        }
                        if let combo = maxCombo {
                            if let total = sheet.total, total != combo { return false }
                        }
                        if let dx = dxScore, dx > 0 {
                            guard let total = sheet.total else { return false }
                            let maxDxScore = total * 3
                            if maxDxScore < dx { return false }
                        }
                        return true
                    }
                    if !hasMatchingSheet { continue }
                    
                    if fuzzyMatch(cleaned, song.title) {
                        frameMatches.append(song.songIdentifier)
                        if frameMatches.count > 3 { break }
                    }
                }
            }
            if !frameMatches.isEmpty { break }
        }
        
        // FIX: Only fallback if NO validation conditions are available
        if frameMatches.isEmpty && !hasAnyValidation {
            for candidate in allCandidates {
                let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                guard cleaned.count >= 2 else { continue }
                
                for song in songs {
                    let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
                    let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { sheet in
                        !sheet.regionJp && !sheet.regionIntl && !sheet.regionCn
                    }
                    if isDeleted { continue }
                    
                    if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) {
                        frameMatches.append(song.songIdentifier)
                        if frameMatches.count > 3 { break }
                    }
                }
                if !frameMatches.isEmpty { break }
            }
        }
        
        return frameMatches
    }
    
    private func levenshteinDistance(_ s1: String, _ s2: String) -> Int {
        let a = Array(s1.lowercased())
        let b = Array(s2.lowercased())
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
    
    // MARK: - Camera Frame Handling
    
    private func handleCameraFrame(_ image: UIImage) {
        guard !isShowingScoreEntry else { return }
        Task {
            let imageType = await MLDistinguishProcessor.shared.classify(image)
            
            if imageType == .unknown {
                updateUIWithResults(songIds: [], rate: nil, diff: nil, type: nil, dxScore: nil, fc: nil, fs: nil, boxes: [], imageClass: .unknown, level: nil, maxCombo: nil)
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
                
                updateUIWithResults(songIds: frameMatches, rate: nil, diff: nil, type: nil, dxScore: nil, fc: nil, fs: nil, boxes: recognition.boxes, imageClass: .choose, level: nil, maxCombo: nil)
            } else {
                let recognition = await MLScoreProcessor.shared.process(image)
                
                let matchedSongIds = matchSongsForCameraFrame(
                    titleCandidates: recognition.titleCandidates,
                    title: recognition.title,
                    difficulty: recognition.difficulty,
                    level: recognition.level,
                    maxCombo: recognition.maxCombo,
                    dxScore: recognition.dxScore,
                    type: recognition.type
                )
                
                updateUIWithResults(
                    songIds: matchedSongIds,
                    rate: recognition.rate as Double?,
                    diff: recognition.difficulty,
                    type: recognition.type,
                    dxScore: recognition.dxScore,
                    fc: recognition.comboStatus,
                    fs: recognition.syncStatus,
                    boxes: recognition.boxes,
                    imageClass: .score,
                    level: recognition.level,
                    maxCombo: recognition.maxCombo
                )
            }
        }
    }
    
    private func updateUIWithResults(
        songIds: [String],
        rate: Double?,
        diff: String?,
        type: String?,
        dxScore: Int?,
        fc: String?,
        fs: String?,
        boxes: [RecognizedBox],
        imageClass: MaimaiImageType,
        level: Double?,
        maxCombo: Int?
    ) {
        self.debugBoxes = boxes
        
        for id in recognitionBuffer.keys {
            recognitionBuffer[id, default: 0] -= 1
            if recognitionBuffer[id]! <= 0 { recognitionBuffer.removeValue(forKey: id) }
        }
        
        for id in songIds {
            recognitionBuffer[id, default: 0] += 6
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
                
                let counts = rateBuffer.reduce(into: [:]) { counts, value in counts[value, default: 0] += 1 }
                if let (mostFrequentRate, count) = counts.max(by: { $0.value < $1.value }), count >= stabilizationThreshold {
                    self.recognizedRate = mostFrequentRate
                } else if rateBuffer.count < stabilizationThreshold {
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
            if let lv = level { self.recognizedLevel = lv }
            if let mc = maxCombo { self.recognizedMaxCombo = mc }
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
        self.recognizedLevel = nil
        self.recognizedMaxCombo = nil
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
    
    private func matchedSheet(for song: Song, diff: String, type: String) -> Sheet? {
        // For utage, use special matching logic
        if type.lowercased() == "utage" {
            return matchUtageSheet(for: song, dxScore: recognizedDxScore)
        }
        
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

class CameraViewController: UIViewController, AVCaptureVideoDataOutputSampleBufferDelegate {
    var onImageCaptured: ((UIImage) -> Void)?
    private var captureSession: AVCaptureSession?
    private var previewLayer: AVCaptureVideoPreviewLayer?
    private var photoOutput: AVCapturePhotoOutput?
    private let processingQueue = DispatchQueue(label: "com.maimaid.camera.queue", qos: .userInteractive)
    
    private let frameCounter = OSAllocatedUnfairLock(initialState: 0)
    
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
    
    nonisolated func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let count = frameCounter.withLock { value -> Int in
            value += 1
            return value
        }
        
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
        
        let settings = AVCapturePhotoSettings()
        if let videoConnection = output.connection(with: .video) {
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
            NotificationCenter.default.post(name: Notification.Name("ScannerPhotoCaptured"), object: image)
        }
    }
}
