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
            
            if let modelURL = Bundle.main.url(forResource: "maimaid v1.31n", withExtension: "mlmodelc") {
                let mlModel = try MLModel(contentsOf: modelURL, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
                print("MLScoreProcessor: Successfully loaded maimaid v1.31n model from mlmodelc.")
            } else if let urls = Bundle.main.urls(forResourcesWithExtension: "mlmodelc", subdirectory: nil), let first = urls.first {
                print("MLScoreProcessor: Loaded fallback model \(first.lastPathComponent)")
                let mlModel = try MLModel(contentsOf: first, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
            } else {
                print("MLScoreProcessor: maimaidv1.31.mlmodelc not found in bundle. Check Target Membership.")
            }
        } catch {
            print("MLScoreProcessor: Error loading maimaidv1.31 model - \(error)")
        }
    }
    
    // MARK: - Processing
    
    /// Processes an image using `maimaidv1.31` object detection and targeted OCR.
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
        
        // MLScoreResult holds our parsed data
        var scoreResult = MLScoreResult()
        
        // Class labels expected from maimaidv1.31:
        // achievement, title, dx, std, utage, difficulty, fc, fcp, ap, app, sync, fs, fsp, fdx, fdxp, dxscore
        
        var achievementBox: CGRect?
        var titleBox: CGRect?
        var difficultyBox: CGRect?
        var dxscoreBox: CGRect?
        
        // 1. Parse distinct classifications
        for obs in results {
            guard let topLabel = obs.labels.first else { continue }
            let label = topLabel.identifier.lowercased()
            let box = obs.boundingBox // Vision coordinates (0,0 is bottom-left)
            
            // Add box for debugging
            scoreResult.boxes.append(RecognizedBox(label: label, rect: box))
            
            switch label {
            case "dx": scoreResult.type = "dx"
            case "std": scoreResult.type = "std"
            case "utage": scoreResult.type = "utage"
            case "achievement": achievementBox = box
            case "title": titleBox = box
            case "difficulty": difficultyBox = box
            case "dxscore": dxscoreBox = box
                
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
        // We crop the original image using the bounding box from Vision and run VNRecognizeTextRequest.
        
        if let box = achievementBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: "(\\d{2,3}[.,]\\d{4})")
            if let text = ocrText.first {
                let cleaned = text.replacingOccurrences(of: ",", with: ".")
                                  .replacingOccurrences(of: "%", with: "")
                if let val = Double(cleaned), val <= 101.0 {
                    scoreResult.rate = val
                }
            }
        }
        
        if let box = titleBox {
            // Title can be mostly anything, don't use pattern
            let ocrText = await performOCR(on: cgImage, in: box, pattern: nil)
            if let text = ocrText.first {
                scoreResult.title = text
            }
            scoreResult.titleCandidates = ocrText
        }
        
        if let box = dxscoreBox {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: "(\\d+)")
            if let text = ocrText.first, let val = Int(text) {
                scoreResult.dxScore = val
            }
        }
        
        // 3. Difficulty Parsing (OCR for "re", fallback to Color)
        if let box = difficultyBox, let cropped = cropImage(cgImage, to: box) {
            let ocrText = await performOCR(on: cgImage, in: box, pattern: nil) // Check full region for text
            let joinedText = ocrText.joined(separator: " ").lowercased()
            
            if joinedText.contains("re") {
                scoreResult.difficulty = "remaster"
            } else {
                // Fallback to average color classification
                scoreResult.difficulty = classifyDifficultyColor(from: cropped)
            }
        }
        
        return scoreResult
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
            request.usesLanguageCorrection = true // Title needs correction
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
        
        // Convert normalized bounding box to pixel coordinates
        // Vision Y axis is inverted from CoreGraphics Y axis
        let rect = CGRect(
            x: boundingBox.origin.x * width,
            y: (1 - boundingBox.origin.y - boundingBox.height) * height,
            width: boundingBox.width * width,
            height: boundingBox.height * height
        )
        
        return cgImage.cropping(to: rect)
    }
    
    // MARK: - Color Classification
    
    /// Determines the difficulty based on the average Hue of a cropped CoreGraphics image region.
    private func classifyDifficultyColor(from cgImage: CGImage) -> String {
        guard let avgColor = cgImage.averageColor() else { return "master" }
        var hue: CGFloat = 0
        var sat: CGFloat = 0
        var bri: CGFloat = 0
        avgColor.getHue(&hue, saturation: &sat, brightness: &bri, alpha: nil)
        
        // Hue values (0.0 - 1.0 = 0 - 360 deg)
        // Red (Expert) is ~0.0 or ~1.0
        // Orange (Advanced) is ~0.1
        // Green (Basic) is ~0.33
        // Purple (Master) is ~0.75
        let h = hue * 360
        
        if h > 320 || h < 20 {
            return "expert" // Red roughly 340-20
        } else if h >= 20 && h < 60 {
            return "advanced" // Orange roughly 20-60
        } else if h >= 60 && h < 160 {
            return "basic" // Green roughly 80-140
        } else {
            return "master" // Default to Master for Purple/Blue (~260-310)
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
    var comboStatus: String?
    var syncStatus: String?
    
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
