import Foundation
import CoreML
import Vision
import UIKit

/// Processor that encapsulates the logic for parsing Maimai scores using CoreML model.
class MLScoreProcessor: Sendable {
    static let shared = MLScoreProcessor()
    
    // The compiled CoreML model
    private var visionModel: VNCoreMLModel?
    
    init() {
        setupModel()
    }
    
    // MARK: - Setup
    
    private func setupModel() {
        do {
            let config = MLModelConfiguration()
            config.computeUnits = .all
            
            if let modelURL = Bundle.main.url(forResource: "maimaid v1.41n", withExtension: "mlmodelc") {
                let mlModel = try MLModel(contentsOf: modelURL, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
                print("MLScoreProcessor: Successfully loaded maimaid v1.41n model from mlmodelc.")
            } else if let modelURL = Bundle.main.url(forResource: "maimaid v1.4n", withExtension: "mlmodelc") {
                let mlModel = try MLModel(contentsOf: modelURL, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
                print("MLScoreProcessor: Loaded fallback maimaid v1.4n model.")
            } else if let urls = Bundle.main.urls(forResourcesWithExtension: "mlmodelc", subdirectory: nil),
                      let first = urls.first(where: { $0.lastPathComponent.contains("maimaid") && !$0.lastPathComponent.contains("distinguish") && !$0.lastPathComponent.contains("choose") }) {
                print("MLScoreProcessor: Loaded fallback model \(first.lastPathComponent)")
                let mlModel = try MLModel(contentsOf: first, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
            } else {
                print("MLScoreProcessor: No maimaid score model found in bundle. Check Target Membership.")
            }
        } catch {
            print("MLScoreProcessor: Error loading model - \(error)")
        }
    }
    
    // MARK: - Processing
    
    /// Processes an image using object detection and targeted OCR.
    func process(_ image: UIImage) async -> MLScoreResult {
        let normalizedImage = image.normalized()
        guard let cgImage = normalizedImage.cgImage, let vnModel = visionModel else {
            return MLScoreResult(debugInfo: "Model not loaded or invalid image.")
        }
        
        let request = VNCoreMLRequest(model: vnModel)
        request.imageCropAndScaleOption = .scaleFit
        let handler = VNImageRequestHandler(cgImage: cgImage, orientation: .up, options: [:])
        
        do {
            try handler.perform([request])
        } catch {
            return MLScoreResult(debugInfo: "VNCoreMLRequest failed: \(error)")
        }
        
        guard let results = request.results as? [VNRecognizedObjectObservation] else {
            return MLScoreResult(debugInfo: "No recognized objects.")
        }
        
        var scoreResult = MLScoreResult()
        
        // Bounding boxes for OCR regions
        var achievementBox: CGRect?
        var titleBox: CGRect?
        var difficultyBox: CGRect?
        var dxscoreBox: CGRect?
        var maxdxscoreBox: CGRect?
        var lvBox: CGRect?
        var kanjiBox: CGRect?
        
        // 1. Parse distinct classifications
        print("════════════════════════════════════════")
        print("MLScoreProcessor: Detected \(results.count) objects")
        
        for obs in results {
            guard let topLabel = obs.labels.first else { continue }
            let label = topLabel.identifier.lowercased()
            let confidence = topLabel.confidence
            let box = obs.boundingBox
            
            scoreResult.boxes.append(RecognizedBox(label: label, rect: box))
            
            print("  [\(label)] confidence: \(String(format: "%.2f", confidence)), box: [x:\(String(format: "%.3f", box.origin.x)), y:\(String(format: "%.3f", box.origin.y)), w:\(String(format: "%.3f", box.width)), h:\(String(format: "%.3f", box.height))]")
            
            switch label {
            case "dx": scoreResult.type = "dx"
            case "std": scoreResult.type = "std"
            case "utage": scoreResult.type = "utage"
            case "achievement": achievementBox = box
            case "title": titleBox = box
            case "difficulty": difficultyBox = box
            case "dxscore": dxscoreBox = box
            case "maxdxscore": maxdxscoreBox = box
            case "lv": lvBox = box
            case "kanji": kanjiBox = box
                
            // Status Badges
            case "fc": scoreResult.comboStatus = "fc"
            case "fcp": scoreResult.comboStatus = "fc+"
            case "ap": scoreResult.comboStatus = "ap"
            case "app": scoreResult.comboStatus = "ap+"
            case "sync": scoreResult.syncStatus = "sync"
            case "fs": scoreResult.syncStatus = "fs"
            case "fsp": scoreResult.syncStatus = "fs+"
            case "fdx": scoreResult.syncStatus = "fsd"
            case "fdxp": scoreResult.syncStatus = "fsdp"
            default: break
            }
        }
        
        // 2. Targeted OCR on Specific Regions
        print("────────────────────────────────────────")
        print("MLScoreProcessor: Starting OCR on detected regions...")
        
        // Achievement
        if let box = achievementBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: "(\\d{2,3}[.,]\\d{4})")
            print("  [achievement OCR] raw results: \(ocrText)")
            if let text = ocrText.first {
                let cleaned = text.replacingOccurrences(of: ",", with: ".")
                                  .replacingOccurrences(of: "%", with: "")
                if let val = Double(cleaned), val <= 101.0 {
                    scoreResult.rate = val
                    print("  [achievement] ✓ parsed: \(val)%")
                } else {
                    print("  [achievement] ✗ failed to parse: '\(cleaned)'")
                }
            }
        } else {
            print("  [achievement] ✗ no bounding box detected")
        }
        
        // Title
        if let box = titleBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: nil)
            print("  [title OCR] raw results: \(ocrText)")
            if let text = ocrText.first {
                scoreResult.title = text
                print("  [title] ✓ parsed: '\(text)'")
            }
            scoreResult.titleCandidates = ocrText
        } else {
            print("  [title] ✗ no bounding box detected")
        }
        
        // DX Score
        if let box = dxscoreBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: "(\\d+)")
            print("  [dxscore OCR] raw results: \(ocrText)")
            if let text = ocrText.first, let val = Int(text) {
                scoreResult.dxScore = val
                print("  [dxscore] ✓ parsed: \(val)")
            }
        } else {
            print("  [dxscore] ✗ no bounding box detected")
        }
        
        // Max DX Score (NEW in v1.41n)
        if let box = maxdxscoreBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: "(\\d+)")
            print("  [maxdxscore OCR] raw results: \(ocrText)")
            if let text = ocrText.first, let val = Int(text), val > 0 {
                scoreResult.maxDxScore = val
                scoreResult.maxCombo = val / 3
                print("  [maxdxscore] ✓ parsed: \(val) (total notes: \(val / 3))")
            }
        } else {
            print("  [maxdxscore] ✗ no bounding box detected")
        }
        
        // Level (integer only)
        if let box = lvBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: "(\\d+)")
            print("  [lv OCR] raw results: \(ocrText)")
            if let text = ocrText.first, let val = Int(text), val >= 1, val <= 15 {
                scoreResult.level = Double(val)
                print("  [lv] ✓ parsed: \(val)")
            } else {
                print("  [lv] ✗ failed to parse or out of range")
            }
        } else {
            print("  [lv] ✗ no bounding box detected")
        }
        
        // Kanji (NEW in v1.41n) - for utage sheet identification
        if let box = kanjiBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: nil)
            print("  [kanji OCR] raw results: \(ocrText)")
            if let text = ocrText.first?.trimmingCharacters(in: .whitespacesAndNewlines), !text.isEmpty {
                scoreResult.kanji = text
                print("  [kanji] ✓ parsed: '\(text)'")
            }
        } else {
            print("  [kanji] ✗ no bounding box detected")
        }
        
        // Difficulty (OCR-based in v1.41n)
        // OCR result takes absolute priority over color classification,
        // because color can be affected by ambient lighting conditions.
        if let box = difficultyBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: nil)
            let joinedText = ocrText.joined(separator: " ")
            print("  [difficulty OCR] raw results: \(ocrText)")
            
            // Try OCR text first — this is the authoritative source
            if let parsed = parseDifficultyFromOCR(joinedText) {
                scoreResult.difficulty = parsed
                print("  [difficulty] ✓ parsed: \(parsed) (from OCR text, authoritative)")
            } else if let cropped = cropImage(cgImage, to: box) {
                // Color fallback only when OCR cannot determine difficulty at all
                let colorResult = classifyDifficultyColor(from: cropped)
                scoreResult.difficulty = colorResult
                print("  [difficulty] ⚠ parsed: \(colorResult) (color fallback, OCR inconclusive)")
            }
        } else {
            print("  [difficulty] ✗ no bounding box detected")
        }
        
        // If utage type detected but no difficulty set, mark as utage
        if scoreResult.type == "utage" && scoreResult.difficulty == nil {
            scoreResult.difficulty = "utage"
        }
        
        // MARK: - Extract kanji from title as fallback
        if scoreResult.kanji == nil || scoreResult.kanji!.isEmpty {
            let extractedKanji = extractKanjiFromTitleCandidates(scoreResult.titleCandidates, fallback: scoreResult.title)
            if let extracted = extractedKanji {
                scoreResult.kanji = extracted
                print("  [kanji] ✓ extracted from title: '\(extracted)'")
            }
        }
        
        // Final summary
        print("────────────────────────────────────────")
        print("MLScoreProcessor: Final Result Summary")
        print("  type: \(scoreResult.type ?? "nil")")
        print("  title: \(scoreResult.title ?? "nil")")
        print("  titleCandidates: \(scoreResult.titleCandidates)")
        print("  rate: \(scoreResult.rate != nil ? String(format: "%.4f%%", scoreResult.rate!) : "nil")")
        print("  difficulty: \(scoreResult.difficulty ?? "nil")")
        print("  dxScore: \(scoreResult.dxScore != nil ? "\(scoreResult.dxScore!)" : "nil")")
        print("  maxDxScore: \(scoreResult.maxDxScore != nil ? "\(scoreResult.maxDxScore!)" : "nil")")
        print("  maxCombo (total): \(scoreResult.maxCombo != nil ? "\(scoreResult.maxCombo!)" : "nil")")
        print("  level: \(scoreResult.level != nil ? String(format: "%.0f", scoreResult.level!) : "nil")")
        print("  kanji: \(scoreResult.kanji ?? "nil")")
        print("  comboStatus: \(scoreResult.comboStatus ?? "nil")")
        print("  syncStatus: \(scoreResult.syncStatus ?? "nil")")
        print("════════════════════════════════════════")
        
        return scoreResult
    }
    
    // MARK: - Kanji Extraction from Title
    
    /// Attempts to extract a utage kanji identifier from title candidates.
    /// Matches patterns like 【宴】, 【狂】, [覚], [協] etc. at the start of the title.
    private func extractKanjiFromTitleCandidates(_ candidates: [String], fallback: String?) -> String? {
        var allTexts = candidates
        if let fb = fallback { allTexts.insert(fb, at: 0) }
        
        for text in allTexts {
            let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
            
            // Pattern 1: Full-width brackets 【X】
            if let match = trimmed.range(of: "^【([^】]+)】", options: .regularExpression) {
                let inner = trimmed[match]
                    .dropFirst(1)  // drop 【
                    .dropLast(1)   // drop 】
                let kanji = String(inner).trimmingCharacters(in: .whitespacesAndNewlines)
                if !kanji.isEmpty { return kanji }
            }
            
            // Pattern 2: Half-width brackets [X]
            if let match = trimmed.range(of: "^\\[([^\\]]+)\\]", options: .regularExpression) {
                let inner = trimmed[match]
                    .dropFirst(1)  // drop [
                    .dropLast(1)   // drop ]
                let kanji = String(inner).trimmingCharacters(in: .whitespacesAndNewlines)
                if !kanji.isEmpty { return kanji }
            }
        }
        
        return nil
    }
    
    // MARK: - Difficulty Parsing
    
    /// Parses difficulty from OCR text by matching known keywords.
    /// This is the authoritative source — color classification is only a fallback
    /// because ambient lighting can distort perceived colors.
    private func parseDifficultyFromOCR(_ text: String) -> String? {
        let lower = text.lowercased()
            .replacingOccurrences(of: " ", with: "")
            .replacingOccurrences(of: ":", with: "")
        
        // Check for utage early — even a single "u" in the difficulty region
        // strongly indicates utage, since no other difficulty starts with "u".
        // The score screen shows "UTAGE" but OCR may only capture partial text.
        if lower.contains("utage") || lower.contains("宴") {
            return "utage"
        }
        
        // Order matters: "remaster" before "master" to avoid substring match
        if lower.contains("remaster") || lower.contains("re:master") || lower.contains("reマスター") {
            return "remaster"
        }
        if lower.contains("master") || lower.contains("マスター") {
            return "master"
        }
        if lower.contains("expert") || lower.contains("エキスパート") {
            return "expert"
        }
        if lower.contains("advanced") || lower.contains("アドバンス") {
            return "advanced"
        }
        if lower.contains("basic") || lower.contains("ベーシック") {
            return "basic"
        }
        
        // Abbreviated matches — must come after full-word checks
        // to avoid false positives (e.g. "mas" in some title fragment)
        if lower.contains("re") && (lower.contains("ma") || lower.contains("ster")) {
            return "remaster"
        }
        if lower.contains("mas") {
            return "master"
        }
        if lower.contains("exp") {
            return "expert"
        }
        if lower.contains("adv") {
            return "advanced"
        }
        if lower.contains("bas") {
            return "basic"
        }
        
        // Single character "u" check — utage is the only difficulty starting with U.
        // This catches cases where OCR only reads a fragment like "U", "UT", "UTA" etc.
        // We check this last to avoid false positives with other words containing "u".
        // Only match if the cleaned text is very short (likely just the difficulty label)
        // or starts with "u" as the first alphabetic character.
        let alphaOnly = lower.filter { $0.isLetter }
        if !alphaOnly.isEmpty && alphaOnly.hasPrefix("u") {
            // Verify it's not part of a known non-utage word
            let nonUtageUWords = ["under", "ultra", "up", "union", "unit", "universe"]
            let isNonUtage = nonUtageUWords.contains { alphaOnly.hasPrefix($0) }
            if !isNonUtage {
                return "utage"
            }
        }
        
        return nil
    }
    
    // MARK: - OCR Helpers
    
    /// Crops and performs OCR on a specific bounding box of the image
    private func performOCR(on cgImage: CGImage, in boundingBox: CGRect, pattern: String?) async -> [String] {
        guard let cropped = cropImage(cgImage, to: boundingBox) else { return [] }
        
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = false
        if pattern == nil {
            request.recognitionLanguages = ["ja-JP", "en-US", "zh-Hans"]
            request.usesLanguageCorrection = true
        }
        
        let handler = VNImageRequestHandler(cgImage: cropped, options: [:])
        do {
            try handler.perform([request])
            let observations = request.results ?? []
            
            var extracted: [String] = []
            for obs in observations {
                guard let top = obs.topCandidates(1).first else { continue }
                if let p = pattern, let regex = try? NSRegularExpression(pattern: p) {
                    let text = top.string.replacingOccurrences(of: " ", with: "")
                    let range = NSRange(location: 0, length: text.utf16.count)
                    if let match = regex.firstMatch(in: text, range: range),
                       let matchRange = Range(match.range(at: 1), in: text) {
                        extracted.append(String(text[matchRange]))
                    }
                } else {
                    extracted.append(top.string)
                }
            }
            return extracted
        } catch {
            return []
        }
    }
    
    /// Vision coordinates (0,0 is bottom-left) to Image Coordinates (0,0 is top-left)
    private func cropImage(_ cgImage: CGImage, to boundingBox: CGRect) -> CGImage? {
        let width = CGFloat(cgImage.width)
        let height = CGFloat(cgImage.height)
        
        let rect = CGRect(
            x: boundingBox.origin.x * width,
            y: (1 - boundingBox.origin.y - boundingBox.height) * height,
            width: boundingBox.width * width,
            height: boundingBox.height * height
        )
        
        return cgImage.cropping(to: rect)
    }
    
    // MARK: - Color Classification (Fallback)
    
    /// Determines the difficulty based on the average Hue of a cropped CoreGraphics image region.
    /// NOTE: This is only used as a fallback when OCR fails to determine difficulty.
    /// Color-based detection can be unreliable due to ambient lighting conditions.
    private func classifyDifficultyColor(from cgImage: CGImage) -> String {
        guard let avgColor = cgImage.averageColor() else { return "master" }
        var hue: CGFloat = 0
        var sat: CGFloat = 0
        var bri: CGFloat = 0
        avgColor.getHue(&hue, saturation: &sat, brightness: &bri, alpha: nil)
        
        let h = hue * 360
        
        if h > 320 || h < 20 {
            return "expert"
        } else if h >= 20 && h < 60 {
            return "advanced"
        } else if h >= 60 && h < 160 {
            return "basic"
        } else if h >= 260 && h <= 320 {
            if bri > 0.75 && sat < 0.5 {
                return "remaster"
            }
            return "master"
        } else {
            return "master"
        }
    }
}

/// Bounding box debug info
struct RecognizedBox: Sendable {
    let label: String
    let rect: CGRect
}

/// Data structure representing the output of the `MLScoreProcessor`.
struct MLScoreResult: Sendable {
    var rate: Double?
    var difficulty: String?
    var type: String? // dx, std, utage
    var title: String?
    var titleCandidates: [String] = []
    
    var dxScore: Int?
    var maxDxScore: Int? // NEW: max possible DX score from OCR
    var comboStatus: String?
    var syncStatus: String?
    
    var level: Double? // integer level from OCR (e.g. 14)
    var maxCombo: Int? // derived from maxDxScore / 3
    var kanji: String? // NEW: utage kanji identifier (e.g. "宴", "狂", "覚")
    
    var debugInfo: String = ""
    var boxes: [RecognizedBox] = []
}

// MARK: - CoreGraphics Extensions
extension CGImage {
    /// Calculates the average color of the CGImage by drawing it into a 1x1 pixel context.
    func averageColor() -> UIColor? {
        let context = CGContext(
            data: nil,
            width: 1,
            height: 1,
            bitsPerComponent: 8,
            bytesPerRow: 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        )
        
        guard let ctx = context else { return nil }
        ctx.draw(self, in: CGRect(x: 0, y: 0, width: 1, height: 1))
        
        guard let data = ctx.data else { return nil }
        let buffer = data.bindMemory(to: UInt8.self, capacity: 4)
        
        let r = CGFloat(buffer[0]) / 255.0
        let g = CGFloat(buffer[1]) / 255.0
        let b = CGFloat(buffer[2]) / 255.0
        let a = CGFloat(buffer[3]) / 255.0
        
        return UIColor(red: r, green: g, blue: b, alpha: a)
    }
}

// MARK: - UIImage Extensions
extension UIImage {
    func normalized() -> UIImage {
        if self.imageOrientation == .up { return self }
        UIGraphicsBeginImageContextWithOptions(self.size, false, self.scale)
        self.draw(in: CGRect(origin: .zero, size: self.size))
        let normalizedImage = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()
        return normalizedImage ?? self
    }
}
