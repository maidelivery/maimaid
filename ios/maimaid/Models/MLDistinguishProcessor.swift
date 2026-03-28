import Foundation
@preconcurrency import CoreML
@preconcurrency import Vision
@preconcurrency import UIKit

/// Processor that encapsulates the logic for classifying Maimai image types using CoreML model.
nonisolated final class MLDistinguishProcessor {
    nonisolated(unsafe) static let shared = MLDistinguishProcessor()
    
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
            if let modelURL = Bundle.main.url(forResource: "maimaidistinguish v1.2", withExtension: "mlmodelc") {
                let mlModel = try MLModel(contentsOf: modelURL, configuration: config)
                let visionModel = try VNCoreMLModel(for: mlModel)
                print("MLDistinguishProcessor: Successfully loaded maimaidistinguish v1.2 model.")
                return visionModel
            } else if let urls = Bundle.main.urls(forResourcesWithExtension: "mlmodelc", subdirectory: nil), let first = urls.first(where: { $0.lastPathComponent.contains("maimaidistinguish") }) {
                print("MLDistinguishProcessor: Loaded fallback model \(first.lastPathComponent)")
                let mlModel = try MLModel(contentsOf: first, configuration: config)
                return try VNCoreMLModel(for: mlModel)
            } else {
                print("MLDistinguishProcessor: maimaidistinguish model not found in bundle. Check Target Membership.")
                return nil
            }
        } catch {
            print("MLDistinguishProcessor: Error loading maimaidistinguish model - \(error)")
            return nil
        }
    }
    
    // MARK: - Processing
    
    /// Classifies an image into .score, .choose, or .unknown
    func classify(_ image: UIImage) async -> MaimaiImageType {
        let normalizedImage = await MainActor.run { image.normalized() }
        return Self.classifySynchronously(normalizedImage, visionModel: visionModel)
    }
    
    private static func classifySynchronously(_ image: UIImage, visionModel: VNCoreMLModel?) -> MaimaiImageType {
        guard let cgImage = image.cgImage, let vnModel = visionModel else {
            return .unknown
        }
        
        let request = VNCoreMLRequest(model: vnModel)
        request.imageCropAndScaleOption = .scaleFit
        let handler = VNImageRequestHandler(cgImage: cgImage, orientation: .up, options: [:])
        
        do {
            try handler.perform([request])
            
            if let results = request.results as? [VNClassificationObservation], let topResult = results.first {
                let label = topResult.identifier.lowercased()
                if label == "score" {
                    return .score
                } else if label == "choose" {
                    return .choose
                }
            }
        } catch {
            print("MLDistinguishProcessor classification failed: \(error)")
        }
        
        return .unknown
    }
}

/// Enum representing the recognized image type
enum MaimaiImageType: String, Sendable {
    case score = "score"
    case choose = "choose"
    case unknown = "unknown"
}
