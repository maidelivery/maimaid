import UIKit
import Vision
import CoreImage
import CoreImage.CIFilterBuiltins

/// Recognition result from a maimai score screenshot
struct ScoreRecognitionResult {
    var achievementRate: Double?
    /// All distinct rates found in the image, sorted descending
    var rateCandidates: [Double] = []
    var difficulty: String?
    var type: String? // "dx", "std", "utage"
    /// All viable title candidates for matching against song DB
    var titleCandidates: [String] = []
    var debugInfo: String = ""
}

/// Processes maimai score screenshots using full-image OCR with preprocessing.
///
/// Instead of cropping fixed regions (which fails with different photo framings),
/// this processor runs OCR on the full preprocessed image and uses text content
/// analysis to identify fields (rate, difficulty, type, and title candidates).
class ScoreImageProcessor {
    
    static let shared = ScoreImageProcessor()
    
    private let ciContext = CIContext(options: [.useSoftwareRenderer: false])
    
    // MARK: - Main Processing
    
    func process(_ image: UIImage) async -> ScoreRecognitionResult {
        guard let cgImage = image.cgImage else {
            return ScoreRecognitionResult(debugInfo: "Failed to get CGImage")
        }
        
        var result = ScoreRecognitionResult()
        var debugLines: [String] = []
        
        // --- Step 1: Preprocess and OCR the full image ---
        let allTexts = ocrFullImage(cgImage)
        debugLines.append("Raw OCR (\(allTexts.count) items): \(allTexts.map(\.text).joined(separator: " | "))")
        
        // --- Step 2: Extract achievement rates ---
        result.rateCandidates = extractAllRates(from: allTexts)
        result.achievementRate = result.rateCandidates.first // highest rate
        
        // --- Step 3: Extract difficulty ---
        result.difficulty = extractDifficulty(from: allTexts)
        
        // --- Step 4: Extract type (DX/STD) ---
        result.type = extractType(from: allTexts)
        
        // --- Step 5: Collect title candidates ---
        result.titleCandidates = extractTitleCandidates(from: allTexts)
        
        debugLines.append("Rate: \(result.achievementRate.map { String(format: "%.4f", $0) } ?? "nil")")
        debugLines.append("Diff: \(result.difficulty ?? "nil")")
        debugLines.append("Type: \(result.type ?? "nil")")
        debugLines.append("Title candidates: \(result.titleCandidates.joined(separator: " | "))")
        
        result.debugInfo = debugLines.joined(separator: "\n")
        return result
    }
    
    // MARK: - Full Image OCR
    
    private struct OCRItem {
        let text: String
        let box: CGRect
        let confidence: Float
        /// Center Y in vision coordinates (0=bottom, 1=top)
        var centerY: CGFloat { box.origin.y + box.height / 2 }
        var centerX: CGFloat { box.origin.x + box.width / 2 }
    }
    
    private func ocrFullImage(_ cgImage: CGImage) -> [OCRItem] {
        // Preprocess: boost contrast, moderate desaturation, sharpen
        guard let processed = preprocessFullImage(cgImage) else { return [] }
        
        let handler = VNImageRequestHandler(cgImage: processed, options: [:])
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true
        request.recognitionLanguages = ["ja-JP", "zh-Hans", "zh-Hant", "en-US"]
        
        do {
            try handler.perform([request])
        } catch {
            return []
        }
        
        return (request.results ?? []).compactMap { obs in
            guard let top = obs.topCandidates(1).first else { return nil }
            return OCRItem(
                text: top.string,
                box: obs.boundingBox,
                confidence: top.confidence
            )
        }
    }
    
    private func preprocessFullImage(_ cgImage: CGImage) -> CGImage? {
        var ciImage = CIImage(cgImage: cgImage)
        
        // Boost contrast and reduce saturation to make text pop against backgrounds
        let controls = CIFilter.colorControls()
        controls.inputImage = ciImage
        controls.contrast = 1.6
        controls.saturation = 0.3
        controls.brightness = 0.05
        if let out = controls.outputImage { ciImage = out }
        
        // Sharpen text edges
        let sharpen = CIFilter.unsharpMask()
        sharpen.inputImage = ciImage
        sharpen.radius = 1.5
        sharpen.intensity = 0.6
        if let out = sharpen.outputImage { ciImage = out }
        
        return ciContext.createCGImage(ciImage, from: ciImage.extent)
    }
    
    // MARK: - Field Extraction
    
    private func extractAllRates(from items: [OCRItem]) -> [Double] {
        let pattern = "(\\d{2,3}[.,]\\d{3,4})"
        let regex = try? NSRegularExpression(pattern: pattern)
        
        var rates = Set<Double>()
        
        for item in items {
            let cleaned = item.text
                .replacingOccurrences(of: " ", with: "")
                .replacingOccurrences(of: "O", with: "0")
                .replacingOccurrences(of: ",", with: ".")
                .replacingOccurrences(of: "%", with: "")
            
            let range = NSRange(location: 0, length: cleaned.utf16.count)
            let matches = regex?.matches(in: cleaned, range: range) ?? []
            
            for match in matches {
                if let matchRange = Range(match.range(at: 1), in: cleaned) {
                    let numStr = String(cleaned[matchRange]).replacingOccurrences(of: ",", with: ".")
                    if let value = Double(numStr), value >= 50.0 && value <= 101.0 {
                        // Round to 4 decimal places to deduplicate
                        let rounded = (value * 10000).rounded() / 10000
                        rates.insert(rounded)
                    }
                }
            }
        }
        
        return rates.sorted(by: >)
    }
    
    private func extractDifficulty(from items: [OCRItem]) -> String? {
        // Search order matters: check "remaster" before "master" to avoid false match
        let keywords: [(pattern: String, normalized: String)] = [
            ("remaster", "remaster"),
            ("re:master", "remaster"),
            ("re: master", "remaster"),
            ("master", "master"),
            ("expert", "expert"),
            ("advanced", "advanced"),
            ("basic", "basic"),
        ]
        
        for item in items {
            let low = item.text.lowercased()
            for kw in keywords {
                if low.contains(kw.pattern) {
                    return kw.normalized
                }
            }
        }
        return nil
    }
    
    private func extractType(from items: [OCRItem]) -> String? {
        for item in items {
            let text = item.text
            let low = text.lowercased()
            
            // Japanese indicators (primary — these appear on the maimai screen)
            // でらっくす / でらっくスコア = DX
            // スタンダード = STD
            if text.contains("でらっくす") || text.contains("デラックス") ||
               text.contains("でらっく") || // partial match
               text.contains("でらつくす") { // common OCR misread
                return "dx"
            }
            if text.contains("スタンダード") || text.contains("すたんだーど") {
                return "std"
            }
            if text.contains("宴") {
                return "utage"
            }
            
            // English fallbacks (isolated, to avoid false positives)
            if low == "dx" || low.contains("deluxe") { return "dx" }
            if low.contains("standard") && !low.contains("スコア") { return "std" }
        }
        return nil
    }
    
    /// Returns all text fragments that could potentially be a song title,
    /// filtered to exclude known UI labels. The caller matches these against the song DB.
    private func extractTitleCandidates(from items: [OCRItem]) -> [String] {
        // Known non-title patterns
        let skipPatterns: Set<String> = [
            "track", "clear", "clear!", "lv", "achievement", "rating",
            "master", "basic", "advanced", "expert", "remaster",
            "critical", "perfect", "great", "good", "miss",
            "combo", "sync", "play", "next", "new", "record",
            "tap", "hold", "slide", "touch", "break",
            "fast", "late", "score", "my best", "newrecord",
            "max", "sub-monitor"
        ]
        
        let skipJapanese: [String] = [
            "つぎへ", "つぎ", "でらっくす", "スタンダード", "デラックス",
            "でらっくスコア", "コア", "スコア"
        ]
        
        var candidates: [String] = []
        
        for item in items {
            let cleaned = item.text.trimmingCharacters(in: .whitespacesAndNewlines)
            let low = cleaned.lowercased()
            
            // Skip very short text
            if cleaned.count < 3 { continue }
            
            // Skip known UI elements
            if skipPatterns.contains(where: { low.contains($0) }) { continue }
            if skipJapanese.contains(where: { cleaned.contains($0) }) { continue }
            
            // Skip purely numeric text (scores, combos, levels)
            let alphaOnly = cleaned.filter { $0.isLetter }
            if alphaOnly.isEmpty { continue }
            
            // Skip if it looks like "LvXX" or just a number with suffix
            if low.hasPrefix("lv") { continue }
            
            // Skip very common OCR artifacts
            if cleaned.count <= 3 && cleaned.allSatisfy({ $0.isUppercase || $0.isNumber }) { continue }
            
            candidates.append(cleaned)
        }
        
        // Sort by text length descending (longer = more likely to be a title)
        return candidates.sorted { $0.count > $1.count }
    }
}
