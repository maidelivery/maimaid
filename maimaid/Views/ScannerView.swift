import SwiftUI
@preconcurrency import AVFoundation
import Vision
import SwiftData
import PhotosUI
import os

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
        "畫": ["画"],
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
        
        return result.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    private func matchUtageSheet(for song: Song, kanji: String?, maxDxScore: Int?, dxScore: Int?) -> Sheet? {
        let utageSheets = song.sheets.filter { $0.type.lowercased() == "utage" }
        
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
    @State private var recognizedMaxDxScore: Int? = nil
    @State private var recognizedFC: String? = nil
    @State private var recognizedFS: String? = nil
    @State private var recognizedLevel: Double? = nil
    @State private var recognizedMaxCombo: Int? = nil
    @State private var recognizedKanji: String? = nil
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
                    Task { await processSelectedPhoto(item) }
                }
            }
        }
    }
    
    @ViewBuilder
    private var scoreEntrySheetContent: some View {
        if let song = recognizedSong {
            if let sheet = matchedSheet(for: song, diff: recognizedDifficulty ?? "master", type: recognizedType ?? "dx") {
                ScoreEntryView(sheet: sheet, initialRate: recognizedRate, initialRank: RatingUtils.calculateRank(achievement: recognizedRate ?? 0), initialDxScore: recognizedDxScore, initialFC: recognizedFC, initialFS: recognizedFS)
            } else if let fallbackSheet = song.sheets.first {
                ScoreEntryView(sheet: fallbackSheet, initialRate: recognizedRate, initialRank: RatingUtils.calculateRank(achievement: recognizedRate ?? 0), initialDxScore: recognizedDxScore, initialFC: recognizedFC, initialFS: recognizedFS)
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
                matchedSongs.sort { a, b in
                    let aExact = a.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    let bExact = b.title.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame
                    if aExact != bExact { return aExact }
                    let aAlias = a.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    let bAlias = b.aliases.contains { $0.localizedCaseInsensitiveCompare(targetCandidate) == .orderedSame }
                    if aAlias != bAlias { return aAlias }
                    let aDist = levenshteinDistance(a.title, targetCandidate)
                    let bDist = levenshteinDistance(b.title, targetCandidate)
                    if aDist != bDist { return aDist < bDist }
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
            let matchedSongs = matchSongsWithFilters(titleCandidates: recognition.titleCandidates, title: recognition.title, difficulty: recognition.difficulty, level: recognition.level, maxCombo: recognition.maxCombo, dxScore: recognition.dxScore, maxDxScore: recognition.maxDxScore, type: recognition.type, kanji: recognition.kanji)
            
            isProcessingPhoto = false
            if let firstMatch = matchedSongs.first {
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
    
    private func matchSongsWithFilters(titleCandidates: [String], title: String?, difficulty: String?, level: Double?, maxCombo: Int?, dxScore: Int?, maxDxScore: Int?, type: String?, kanji: String?) -> [Song] {
        var allCandidates = titleCandidates
        if let exactTitle = title { allCandidates.insert(exactTitle, at: 0) }
        let isUtage = type?.lowercased() == "utage"
        if isUtage { allCandidates = allCandidates.map { stripUtagePrefix($0) } }
        
        let derivedTotalNotes: Int? = {
            if let maxDx = maxDxScore, maxDx > 0 { return maxDx / 3 }
            return maxCombo
        }()
        let hasDifficulty = difficulty != nil && !isUtage
        let hasLevel = level != nil && level! >= 1 && level! <= 15
        let hasTotalNotes = derivedTotalNotes != nil && derivedTotalNotes! > 0
        let hasDxScore = dxScore != nil && dxScore! > 0
        let hasMaxDxScore = maxDxScore != nil && maxDxScore! > 0
        let hasKanji = kanji != nil && !(kanji!.isEmpty)
        
        var filteredSongs = songs.filter { song in
            let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
            let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { !$0.regionJp && !$0.regionIntl && !$0.regionCn }
            if isDeleted { return false }
            if isUtage {
                let utageSheets = song.sheets.filter { $0.type.lowercased() == "utage" }
                if utageSheets.isEmpty { return false }
                if hasKanji { if !utageSheets.contains(where: { $0.difficulty.contains(kanji!) }) { return false } }
                if hasTotalNotes {
                    let totalMatch = utageSheets.contains { sheet in guard let total = sheet.total else { return true }; return total == derivedTotalNotes! }
                    if !totalMatch {
                        if hasDxScore { if !utageSheets.contains(where: { sheet in guard let total = sheet.total else { return true }; return total * 3 >= dxScore! }) { return false } } else { return false }
                    }
                } else if hasDxScore {
                    if !utageSheets.contains(where: { sheet in guard let total = sheet.total else { return true }; return total * 3 >= dxScore! }) { return false }
                }
                return true
            }
            return song.sheets.contains { sheet in
                if sheet.type.lowercased() == "utage" { return false }
                if let t = type, t.lowercased() != "utage" { if sheet.type.lowercased() != t.lowercased() { return false } }
                if let diff = difficulty { if sheet.difficulty.lowercased() != diff.lowercased() { return false } }
                if let lv = level, lv >= 1, lv <= 15 {
                    let sheetLevel = sheet.internalLevelValue ?? sheet.levelValue ?? 0
                    if sheetLevel > 0 { if Int(sheetLevel) != Int(lv) { return false } } else { if Int(sheet.level) != Int(lv) { return false } }
                }
                if let total = derivedTotalNotes, total > 0 { if let sheetTotal = sheet.total { if sheetTotal != total { return false } } }
                if let dx = dxScore, dx > 0 { if let total = sheet.total { if total * 3 < dx { return false } } }
                if let maxDx = maxDxScore, maxDx > 0 { if let total = sheet.total { if total * 3 != maxDx { return false } } }
                return true
            }
        }
        
        let hasAnyValidation = hasDifficulty || hasLevel || hasTotalNotes || hasDxScore || hasMaxDxScore || hasKanji
        if filteredSongs.isEmpty && !hasAnyValidation {
            filteredSongs = songs.filter { song in
                let standardSheets = song.sheets.filter { $0.type.lowercased() != "utage" }
                let isDeleted = standardSheets.isEmpty || standardSheets.allSatisfy { !$0.regionJp && !$0.regionIntl && !$0.regionCn }
                return !isDeleted
            }
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
                if hasMaxDxScore { if song.sheets.contains(where: { guard let total = $0.total else { return false }; return total * 3 == maxDxScore! }) { constraintBonus += 20 } }
                for searchCandidate in variants {
                    let searchLower = searchCandidate.lowercased()
                    if song.title.localizedCaseInsensitiveCompare(searchCandidate) == .orderedSame { matchScore = 100; break }
                    if song.aliases.contains(where: { $0.localizedCaseInsensitiveCompare(searchCandidate) == .orderedSame }) { matchScore = max(matchScore, 95); break }
                    if song.title.localizedCaseInsensitiveContains(searchLower) { matchScore = max(matchScore, 80); continue }
                    if searchLower.localizedCaseInsensitiveContains(song.title.lowercased()) { matchScore = max(matchScore, 75); continue }
                    if song.aliases.contains(where: { $0.localizedCaseInsensitiveContains(searchLower) }) { matchScore = max(matchScore, 70); continue }
                    if let keywords = song.searchKeywords, keywords.localizedCaseInsensitiveContains(searchLower) { matchScore = max(matchScore, 60); continue }
                    let dist = levenshteinDistance(cleaned, song.title)
                    let maxLen = max(cleaned.count, song.title.count)
                    if dist <= max(2, maxLen / 3) { matchScore = max(matchScore, 50 - dist); continue }
                    for alias in song.aliases {
                        let aliasDist = levenshteinDistance(cleaned, alias)
                        if aliasDist <= max(2, max(cleaned.count, alias.count) / 3) { matchScore = max(matchScore, 45 - aliasDist); break }
                    }
                }
                if matchScore == 0 && song.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    if hasMaxDxScore || (hasTotalNotes && hasDifficulty) { matchScore = 30 }
                }
                let totalScore = matchScore + constraintBonus
                if totalScore > 0 { matchedSongs.append((song: song, score: totalScore)); seenIds.insert(song.songIdentifier) }
            }
        }
        if matchedSongs.isEmpty && allCandidates.allSatisfy({ $0.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }) && hasAnyValidation {
            for song in filteredSongs where !seenIds.contains(song.songIdentifier) {
                let score = hasMaxDxScore ? (song.sheets.contains(where: { guard let total = $0.total else { return false }; return total * 3 == maxDxScore! }) ? 50 : 10) : 10
                matchedSongs.append((song: song, score: score))
            }
        }
        matchedSongs.sort { a, b in if a.score != b.score { return a.score > b.score }; return a.song.title.count < b.song.title.count }
        return matchedSongs.map { $0.song }
    }
    
    // MARK: - Fast Camera Frame Matching
    
    private func matchSongsForCameraFrame(titleCandidates: [String], title: String?, difficulty: String?, level: Double?, maxCombo: Int?, dxScore: Int?, maxDxScore: Int?, type: String?, kanji: String?) -> [String] {
        var allCandidates = titleCandidates
        if let exactTitle = title { allCandidates.insert(exactTitle, at: 0) }
        let isUtage = type?.lowercased() == "utage"
        if isUtage { allCandidates = allCandidates.map { stripUtagePrefix($0) } }
        let derivedTotalNotes: Int? = { if let maxDx = maxDxScore, maxDx > 0 { return maxDx / 3 }; return maxCombo }()
        let hasMaxDxScore = maxDxScore != nil && maxDxScore! > 0
        let hasAnyValidation = (difficulty != nil && !isUtage) || (level != nil && level! >= 1 && level! <= 15) || (derivedTotalNotes != nil && derivedTotalNotes! > 0) || (dxScore != nil && dxScore! > 0) || hasMaxDxScore || (kanji != nil && !(kanji!.isEmpty))
        
        func sheetOK(_ sheet: Sheet) -> Bool {
            if isUtage {
                if sheet.type.lowercased() != "utage" { return false }
                if let k = kanji, !k.isEmpty, !sheet.difficulty.contains(k) { return false }
                if let tn = derivedTotalNotes, tn > 0 { if let total = sheet.total, total != tn { return false } }
                if let dx = dxScore, dx > 0 { if let total = sheet.total, total * 3 < dx { return false } }
                return true
            }
            if sheet.type.lowercased() == "utage" { return false }
            if let t = type, t.lowercased() != "utage" { if sheet.type.lowercased() != t.lowercased() { return false } }
            if let diff = difficulty { if sheet.difficulty.lowercased() != diff.lowercased() { return false } }
            if let lv = level, lv >= 1, lv <= 15 {
                let sl = sheet.internalLevelValue ?? sheet.levelValue ?? 0
                if sl > 0 { if Int(sl) != Int(lv) { return false } } else { if Int(sheet.level) != Int(lv) { return false } }
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
            for song in songs {
                if !song.sheets.contains(where: { sheetOK($0) }) { continue }
                if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) {
                    frameMatches.append(song.songIdentifier); foundFast = true
                    if frameMatches.count > 3 { break }
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
                    if std.isEmpty || std.allSatisfy({ !$0.regionJp && !$0.regionIntl && !$0.regionCn }) { continue }
                    if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) { frameMatches.append(song.songIdentifier); if frameMatches.count > 3 { break } }
                }
                if !frameMatches.isEmpty { break }
            }
        }
        return frameMatches
    }
    
    // MARK: - Utilities
    
    private func levenshteinDistance(_ s1: String, _ s2: String) -> Int {
        let a = Array(s1.lowercased()), b = Array(s2.lowercased())
        if a.isEmpty { return b.count }; if b.isEmpty { return a.count }
        var dist = [[Int]](repeating: [Int](repeating: 0, count: b.count + 1), count: a.count + 1)
        for i in 0...a.count { dist[i][0] = i }; for j in 0...b.count { dist[0][j] = j }
        for i in 1...a.count { for j in 1...b.count {
            dist[i][j] = a[i-1] == b[j-1] ? dist[i-1][j-1] : min(dist[i-1][j]+1, dist[i][j-1]+1, dist[i-1][j-1]+1)
        } }
        return dist[a.count][b.count]
    }
    
    private func showFeedback(_ message: String) {
        withAnimation { photoImportFeedback = message }
        Task { @MainActor in try? await Task.sleep(for: .seconds(2.5)); withAnimation { photoImportFeedback = nil } }
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
                    .foregroundColor(.white)
                    .padding(10)
                    .background(.ultraThinMaterial, in: Circle())
            }
        }
        .padding()
        .onReceive(NotificationCenter.default.publisher(for: NSNotification.Name("ScannerPhotoCaptured"))) { notification in
            if let image = notification.object as? UIImage { handleCapturedScannerPhoto(image) }
        }
    }
    
    private func triggerPhotoCapture() {
        guard !isSavingPhoto else { return }
        UIImpactFeedbackGenerator(style: .rigid).impactOccurred()
        withAnimation(.easeOut(duration: 0.1)) { showFlashOverlay = true }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.1) { withAnimation(.easeIn(duration: 0.2)) { self.showFlashOverlay = false } }
        isSavingPhoto = true
        NotificationCenter.default.post(name: NSNotification.Name("TakeScannerPhoto"), object: nil)
    }
    
    private func handleCapturedScannerPhoto(_ image: UIImage) {
        let title = recognizedSong?.title
        var tags: [String] = []
        if let t = title { tags.append(t) }
        if let song = recognizedSong, let diff = recognizedDifficulty {
            let type = recognizedType ?? "dx"
            if let sheet = matchedSheet(for: song, diff: diff, type: type) { tags.append("LV\(sheet.level)") }
            tags.append(diff.uppercased()); tags.append(type.uppercased())
        }
        if let rate = recognizedRate { tags.append(RatingUtils.calculateRank(achievement: rate)) }
        Task {
            do {
                try await PhotoService.shared.saveImageWithMetadata(image, title: title, tags: tags)
                await MainActor.run { self.isSavingPhoto = false; showFeedback(String(localized: "scanner.photo.saved")) }
            } catch {
                await MainActor.run { self.isSavingPhoto = false; showFeedback(String(localized: "scanner.photo.error")) }
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
            ScannerResultCardView(song: song, recognizedClass: recognizedClass, recognizedType: recognizedType, recognizedDifficulty: recognizedDifficulty, recognizedRate: recognizedRate, onScoreEntryTap: { isShowingScoreEntry = true }, onResetTap: { resetScanner() })
                .equatable()
        }
    }
    
    // MARK: - Camera Frame Handling
    
    private func handleCameraFrame(_ image: UIImage) {
        guard !isShowingScoreEntry else { return }
        Task {
            let imageType = await MLDistinguishProcessor.shared.classify(image)
            if imageType == .unknown {
                updateUIWithResults(songIds: [], rate: nil, diff: nil, type: nil, dxScore: nil, maxDxScore: nil, fc: nil, fs: nil, boxes: [], imageClass: .unknown, level: nil, maxCombo: nil, kanji: nil)
                return
            }
            if imageType == .choose {
                let recognition = await MLChooseProcessor.shared.process(image)
                var frameMatches: [String] = []
                var allCandidates = recognition.titleCandidates
                if let exactTitle = recognition.title { allCandidates.insert(exactTitle, at: 0) }
                for candidate in allCandidates {
                    let cleaned = candidate.trimmingCharacters(in: .whitespacesAndNewlines)
                    guard cleaned.count >= 2 else { continue }
                    var foundFast = false
                    for song in songs {
                        if song.title.localizedCaseInsensitiveContains(cleaned) || cleaned.localizedCaseInsensitiveContains(song.title) {
                            frameMatches.append(song.songIdentifier); foundFast = true; if frameMatches.count > 3 { break }
                        }
                    }
                    if !foundFast && cleaned.count > 4 {
                        for song in songs { if fuzzyMatch(cleaned, song.title) { frameMatches.append(song.songIdentifier); if frameMatches.count > 3 { break } } }
                    }
                    if !frameMatches.isEmpty { break }
                }
                updateUIWithResults(songIds: frameMatches, rate: nil, diff: nil, type: nil, dxScore: nil, maxDxScore: nil, fc: nil, fs: nil, boxes: recognition.boxes, imageClass: .choose, level: nil, maxCombo: nil, kanji: nil)
            } else {
                let recognition = await MLScoreProcessor.shared.process(image)
                let matchedSongIds = matchSongsForCameraFrame(titleCandidates: recognition.titleCandidates, title: recognition.title, difficulty: recognition.difficulty, level: recognition.level, maxCombo: recognition.maxCombo, dxScore: recognition.dxScore, maxDxScore: recognition.maxDxScore, type: recognition.type, kanji: recognition.kanji)
                updateUIWithResults(songIds: matchedSongIds, rate: recognition.rate, diff: recognition.difficulty, type: recognition.type, dxScore: recognition.dxScore, maxDxScore: recognition.maxDxScore, fc: recognition.comboStatus, fs: recognition.syncStatus, boxes: recognition.boxes, imageClass: .score, level: recognition.level, maxCombo: recognition.maxCombo, kanji: recognition.kanji)
            }
        }
    }
    
    private func updateUIWithResults(songIds: [String], rate: Double?, diff: String?, type: String?, dxScore: Int?, maxDxScore: Int?, fc: String?, fs: String?, boxes: [RecognizedBox], imageClass: MaimaiImageType, level: Double?, maxCombo: Int?, kanji: String?) {
        self.debugBoxes = boxes
        for id in recognitionBuffer.keys { recognitionBuffer[id, default: 0] -= 1; if recognitionBuffer[id]! <= 0 { recognitionBuffer.removeValue(forKey: id) } }
        for id in songIds { recognitionBuffer[id, default: 0] += 6; if recognitionBuffer[id]! > 18 { recognitionBuffer[id] = 18 } }
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
            if let r = rate {
                rateBuffer.append(r); if rateBuffer.count > 5 { rateBuffer.removeFirst() }
                let counts = rateBuffer.reduce(into: [:]) { $0[$1, default: 0] += 1 }
                if let (best, count) = counts.max(by: { $0.value < $1.value }), count >= stabilizationThreshold { self.recognizedRate = best } else if rateBuffer.count < stabilizationThreshold { self.recognizedRate = rateBuffer.last }
            }
            if let d = diff { self.recognizedDifficulty = d }
            if let t = type { self.recognizedType = t }
            if let dx = dxScore {
                dxScoreBuffer.append(dx); if dxScoreBuffer.count > 5 { dxScoreBuffer.removeFirst() }
                let counts = dxScoreBuffer.reduce(into: [:]) { $0[$1, default: 0] += 1 }
                if let (best, count) = counts.max(by: { $0.value < $1.value }), count >= stabilizationThreshold { self.recognizedDxScore = best } else if dxScoreBuffer.count < stabilizationThreshold { self.recognizedDxScore = dxScoreBuffer.last }
            }
            if let maxDx = maxDxScore {
                maxDxScoreBuffer.append(maxDx); if maxDxScoreBuffer.count > 5 { maxDxScoreBuffer.removeFirst() }
                let counts = maxDxScoreBuffer.reduce(into: [:]) { $0[$1, default: 0] += 1 }
                if let (best, count) = counts.max(by: { $0.value < $1.value }), count >= stabilizationThreshold { self.recognizedMaxDxScore = best } else if maxDxScoreBuffer.count < stabilizationThreshold { self.recognizedMaxDxScore = maxDxScoreBuffer.last }
            }
            if let f = fc { self.recognizedFC = f }; if let s = fs { self.recognizedFS = s }
            if let lv = level { self.recognizedLevel = lv }; if let mc = maxCombo { self.recognizedMaxCombo = mc }
            if let k = kanji { self.recognizedKanji = k }
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
    
    private func matchedSheet(for song: Song, diff: String, type: String) -> Sheet? {
        if type.lowercased() == "utage" { return matchUtageSheet(for: song, kanji: recognizedKanji, maxDxScore: recognizedMaxDxScore, dxScore: recognizedDxScore) }
        let candidates = song.sheets.filter { $0.difficulty.lowercased() == diff.lowercased() && $0.type.lowercased() == type.lowercased() }
        if candidates.count == 1 { return candidates.first }
        if candidates.count > 1, let maxDx = recognizedMaxDxScore, maxDx > 0 {
            if let exact = candidates.first(where: { $0.total == maxDx / 3 }) { return exact }
        }
        return candidates.first ?? song.sheets.first { $0.difficulty.lowercased() == diff.lowercased() }
    }
}

// MARK: - Camera

struct CameraPreviewView: UIViewControllerRepresentable {
    var onImageCaptured: (UIImage) -> Void
    func makeUIViewController(context: Context) -> CameraViewController { let c = CameraViewController(); c.onImageCaptured = onImageCaptured; return c }
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
        NotificationCenter.default.addObserver(self, selector: #selector(handleTakePhoto), name: Notification.Name("TakeScannerPhoto"), object: nil)
    }
    
    deinit { NotificationCenter.default.removeObserver(self) }
    
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
            if #available(iOS 16.0, *) { photoOut.maxPhotoDimensions = videoDevice.activeFormat.supportedMaxPhotoDimensions.last ?? CMVideoDimensions(width: 0, height: 0) } else { photoOut.isHighResolutionCaptureEnabled = true }
            self.photoOutput = photoOut
        }
        if let connection = videoOutput.connection(with: .video) {
            if #available(iOS 17.0, *) { if connection.isVideoRotationAngleSupported(90) { connection.videoRotationAngle = 90 } } else { if connection.isVideoOrientationSupported { connection.videoOrientation = .portrait } }
        }
        previewLayer = AVCaptureVideoPreviewLayer(session: captureSession)
        previewLayer?.frame = view.layer.bounds
        previewLayer?.videoGravity = .resizeAspectFill
        if let previewLayer = previewLayer { view.layer.addSublayer(previewLayer) }
        DispatchQueue.global(qos: .userInitiated).async { captureSession.startRunning() }
    }
    
    nonisolated func captureOutput(_ output: AVCaptureOutput, didOutput sampleBuffer: CMSampleBuffer, from connection: AVCaptureConnection) {
        let count = frameCounter.withLock { value -> Int in value += 1; return value }
        guard count % 10 == 0 else { return }
        guard let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else { return }
        let ciImage = CIImage(cvPixelBuffer: pixelBuffer)
        guard let cgImage = Self.rawContext.createCGImage(ciImage, from: ciImage.extent) else { return }
        let uiImage = UIImage(cgImage: cgImage)
        DispatchQueue.main.async { self.onImageCaptured?(uiImage) }
    }
    
    override func viewDidLayoutSubviews() { super.viewDidLayoutSubviews(); previewLayer?.frame = view.layer.bounds }
    override func viewWillAppear(_ animated: Bool) { super.viewWillAppear(animated); if let cs = captureSession, !cs.isRunning { DispatchQueue.global(qos: .userInitiated).async { cs.startRunning() } } }
    override func viewWillDisappear(_ animated: Bool) { super.viewWillDisappear(animated); captureSession?.stopRunning() }
    
    @objc private func handleTakePhoto() {
        guard let output = photoOutput else { return }
        let settings = AVCapturePhotoSettings()
        if let vc = output.connection(with: .video) {
            if #available(iOS 17.0, *) { if vc.isVideoRotationAngleSupported(90) { vc.videoRotationAngle = 90 } } else { if vc.isVideoOrientationSupported { vc.videoOrientation = .portrait } }
        }
        if output.availablePhotoCodecTypes.contains(.jpeg) {
            if #available(iOS 16.0, *) { settings.maxPhotoDimensions = output.maxPhotoDimensions } else { settings.isHighResolutionPhotoEnabled = true }
        }
        output.capturePhoto(with: settings, delegate: self)
    }
}

extension CameraViewController: AVCapturePhotoCaptureDelegate {
    func photoOutput(_ output: AVCapturePhotoOutput, didFinishProcessingPhoto photo: AVCapturePhoto, error: Error?) {
        guard let data = photo.fileDataRepresentation(), let image = UIImage(data: data) else { return }
        DispatchQueue.main.async { NotificationCenter.default.post(name: Notification.Name("ScannerPhotoCaptured"), object: image) }
    }
}

// MARK: - Debug Overlay

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
                        Path { path in path.addRect(CGRect(x: x, y: y, width: w, height: h)) }.stroke(Color.green, lineWidth: 2)
                        Text(box.label).font(.system(size: 10, weight: .bold)).foregroundColor(.white).padding(.horizontal, 2).background(Color.green).position(x: x + w / 2, y: max(10, y - 8))
                    }
                }
            }
            .allowsHitTesting(false)
            .ignoresSafeArea()
        }
    }
}

// MARK: - Result Card

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
                SongDetailView(song: song).onDisappear { onResetTap() }
            }) {
                VStack(spacing: 0) {
                    HStack(spacing: 12) {
                        SongJacketView(imageName: song.imageName, size: 40, cornerRadius: 8)
                            .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                        VStack(alignment: .leading, spacing: 3) {
                            Text(song.title).font(.system(size: 14, weight: .bold)).foregroundColor(.primary).lineLimit(1)
                            Text(song.artist).font(.system(size: 11, weight: .regular)).foregroundColor(.secondary).lineLimit(1)
                        }
                        Spacer()
                        Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundColor(.secondary.opacity(0.4))
                    }
                    .padding(.vertical, 14)
                    .padding(.horizontal, 16)
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(Color.primary.opacity(0.1), lineWidth: 1))
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .buttonStyle(.plain)
            .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.9)), removal: .opacity.combined(with: .scale(scale: 0.95))))
        } else {
            Button { onScoreEntryTap() } label: {
                let chartType = recognizedType ?? "dx"
                let diff = recognizedDifficulty ?? "master"
                let diffColor = ThemeUtils.colorForDifficulty(diff, chartType)
                let sheet = song.sheets.first(where: { $0.difficulty.lowercased() == diff.lowercased() && $0.type.lowercased() == chartType.lowercased() })
                VStack(spacing: 0) {
                    HStack(spacing: 0) {
                        RoundedRectangle(cornerRadius: 2).fill(diffColor).frame(width: 4).padding(.vertical, 4)
                        HStack(spacing: 12) {
                            SongJacketView(imageName: song.imageName, size: 40, cornerRadius: 8)
                                .shadow(color: .black.opacity(0.2), radius: 4, x: 0, y: 2)
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 4) {
                                    Text(chartType.uppercased() == "STD" ? String(localized: "scanner.chart.std") : chartType.uppercased())
                                        .font(.system(size: 8, weight: .black))
                                        .padding(.horizontal, 4).padding(.vertical, 1)
                                        .background(chartType.lowercased() == "dx" ? Color.orange : Color.blue)
                                        .foregroundColor(.white).cornerRadius(3)
                                    Text(song.title).font(.system(size: 12, weight: .bold)).foregroundColor(.primary).lineLimit(1)
                                }
                                if diff.lowercased() == "remaster" {
                                    Text("RE: MASTER").font(.system(size: 13, weight: .bold, design: .rounded)).foregroundColor(diffColor)
                                } else {
                                    Text(diff.uppercased()).font(.system(size: 13, weight: .bold, design: .rounded)).foregroundColor(diffColor)
                                }
                            }
                            Spacer()
                            if let rate = recognizedRate {
                                VStack(alignment: .trailing, spacing: 1) {
                                    Text(String(format: "%.4f%%", rate)).font(.system(size: 12, weight: .bold, design: .monospaced)).foregroundColor(.primary)
                                    Text(RatingUtils.calculateRank(achievement: rate)).font(.system(size: 10, weight: .black, design: .rounded)).foregroundColor(diffColor)
                                }
                            }
                            if let levelStr = sheet?.internalLevel ?? sheet?.level {
                                Text(levelStr).font(.system(size: 28, weight: .black, design: .rounded)).foregroundColor(diffColor.opacity(0.85)).frame(minWidth: 44)
                            }
                            Image(systemName: "chevron.right").font(.system(size: 12, weight: .bold)).foregroundColor(.secondary.opacity(0.4))
                        }
                        .padding(.leading, 12).padding(.trailing, 16)
                    }
                    .padding(.vertical, 14)
                }
                .fixedSize(horizontal: false, vertical: true)
                .background(.ultraThinMaterial, in: RoundedRectangle(cornerRadius: 16))
                .overlay(RoundedRectangle(cornerRadius: 16).strokeBorder(diffColor.opacity(0.2), lineWidth: 1))
                .padding(.horizontal, 20)
                .padding(.bottom, 40)
            }
            .buttonStyle(.plain)
            .transition(.asymmetric(insertion: .move(edge: .bottom).combined(with: .opacity).combined(with: .scale(scale: 0.9)), removal: .opacity.combined(with: .scale(scale: 0.95))))
        }
    }
}
