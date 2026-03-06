import Foundation
import CoreML
import Vision
import UIKit

/// Processor that encapsulates the logic for parsing Maimai song choice screens using CoreML model.
class MLChooseProcessor: Sendable {
    static let shared = MLChooseProcessor()
    
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
            
            // Try specific version first
            if let modelURL = Bundle.main.url(forResource: "maimaidetectorv1.0", withExtension: "mlmodelc") {
                let mlModel = try MLModel(contentsOf: modelURL, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
                print("MLChooseProcessor: Successfully loaded maimaidetectorv1.0 model.")
            } else if let urls = Bundle.main.urls(forResourcesWithExtension: "mlmodelc", subdirectory: nil), let first = urls.first(where: { $0.lastPathComponent.contains("maimaidetector") }) {
                print("MLChooseProcessor: Successfully loaded fallback model \(first.lastPathComponent)")
                let mlModel = try MLModel(contentsOf: first, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
            } else {
                print("MLChooseProcessor: maimaidetector model not found in bundle. Check Target Membership.")
            }
        } catch {
            print("MLChooseProcessor: Error loading maimaidetector model - \(error)")
        }
    }
    
    // MARK: - Processing
    
    /// Processes an image using `maimaidetector` object detection and targeted OCR.
    func process(_ image: UIImage) async -> MLChooseResult {
        var result = MLChooseResult()
        
        let normalizedImage = image.normalized()
        guard let cgImage = normalizedImage.cgImage, let vnModel = visionModel else {
            return result
        }
        
        let request = VNCoreMLRequest(model: vnModel)
        request.imageCropAndScaleOption = .scaleFit
        let handler = VNImageRequestHandler(cgImage: cgImage, orientation: .up, options: [:])
        
        do {
            try handler.perform([request])
        } catch {
            print("MLChooseProcessor: VNCoreMLRequest failed: \(error)")
            return result
        }
        
        guard let results = request.results as? [VNRecognizedObjectObservation] else {
            return result
        }
        
        var titleBox: CGRect?
        
        for obs in results {
            guard let topLabel = obs.labels.first else { continue }
            let label = topLabel.identifier.lowercased()
            let box = obs.boundingBox // Vision coordinates (0,0 is bottom-left)
            
            // Add box for debugging
            result.boxes.append(RecognizedBox(label: label, rect: box))
            
            if label == "title" {
                titleBox = box
            }
        }
        
        // 2. Targeted OCR on Specific Regions 
        if let box = titleBox {
            let ocrText = await performOCR(on: cgImage, in: box)
            if let text = ocrText.first {
                result.title = text
            }
            result.titleCandidates = ocrText
        }
        
        return result
    }
    
    // MARK: - OCR Helpers
    
    /// Crops and performs OCR on a specific bounding box of the image
    private func performOCR(on cgImage: CGImage, in boundingBox: CGRect) async -> [String] {
        guard let cropped = cropImage(cgImage, to: boundingBox) else { return [] }
        
        let request = VNRecognizeTextRequest()
        request.recognitionLevel = .accurate
        request.usesLanguageCorrection = true // Title needs correction sometimes
        request.recognitionLanguages = ["ja-JP", "en-US", "zh-Hans"]
        
        let handler = VNImageRequestHandler(cgImage: cropped, options: [:])
        do {
            try handler.perform([request])
            let observations = request.results ?? []
            
            var extracted: [String] = []
            for obs in observations {
                guard let top = obs.topCandidates(1).first else { continue }
                extracted.append(top.string)
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
}

/// Data structure representing the output of the `MLChooseProcessor`.
struct MLChooseResult: Sendable {
    var title: String?
    var titleCandidates: [String] = []
    var boxes: [RecognizedBox] = []
}
