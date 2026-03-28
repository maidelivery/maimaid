import Foundation
@preconcurrency import CoreML
@preconcurrency import Vision
@preconcurrency import UIKit

/// Processor that encapsulates the logic for parsing Maimai song choice screens using CoreML model.
nonisolated final class MLChooseProcessor {
    nonisolated(unsafe) static let shared = MLChooseProcessor()
    
    // The compiled CoreML model
    private let visionModel: VNCoreMLModel?
    
    private init() {
        self.visionModel = Self.loadModel()
    }
    
    // MARK: - Setup
    
    private static func loadModel() -> VNCoreMLModel? {
        do {
            let config = MLModelConfiguration()
            config.computeUnits = .all
            
            // Try specific version first
            if let modelURL = Bundle.main.url(forResource: "maimaidetector v1.2n", withExtension: "mlmodelc") {
                let mlModel = try MLModel(contentsOf: modelURL, configuration: config)
                let visionModel = try VNCoreMLModel(for: mlModel)
                print("MLChooseProcessor: Successfully loaded maimaidetector v1.2n model.")
                return visionModel
            } else if let urls = Bundle.main.urls(forResourcesWithExtension: "mlmodelc", subdirectory: nil), let first = urls.first(where: { $0.lastPathComponent.contains("maimaidetector") }) {
                print("MLChooseProcessor: Successfully loaded fallback model \(first.lastPathComponent)")
                let mlModel = try MLModel(contentsOf: first, configuration: config)
                return try VNCoreMLModel(for: mlModel)
            } else {
                print("MLChooseProcessor: maimaidetector model not found in bundle. Check Target Membership.")
                return nil
            }
        } catch {
            print("MLChooseProcessor: Error loading maimaidetector model - \(error)")
            return nil
        }
    }
    
    // MARK: - Processing
    
    /// Processes an image using `maimaidetector` object detection and targeted OCR.
    func process(_ image: UIImage) async -> MLChooseResult {
        let normalizedImage = await MainActor.run { image.normalized() }
        return Self.processSynchronously(normalizedImage, visionModel: visionModel)
    }
    
    private static func processSynchronously(_ image: UIImage, visionModel: VNCoreMLModel?) -> MLChooseResult {
        var result = MLChooseResult()
        
        guard let cgImage = image.cgImage, let vnModel = visionModel else {
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
            let ocrText = performOCR(on: cgImage, in: box)
            if let text = ocrText.first {
                result.title = text
            }
            result.titleCandidates = ocrText
        }
        
        return result
    }
    
    // MARK: - OCR Helpers
    
    /// Crops and performs OCR on a specific bounding box of the image
    private static func performOCR(on cgImage: CGImage, in boundingBox: CGRect) -> [String] {
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
    private static func cropImage(_ cgImage: CGImage, to boundingBox: CGRect) -> CGImage? {
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
nonisolated struct MLChooseResult: Sendable {
    var title: String?
    var titleCandidates: [String] = []
    var boxes: [RecognizedBox] = []
}
