import Foundation
import CoreML
import Vision
import UIKit

/// Processor that encapsulates the logic for classifying Maimai image types using CoreML model.
class MLDistinguishProcessor: Sendable {
    static let shared = MLDistinguishProcessor()
    
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
            if let modelURL = Bundle.main.url(forResource: "maimaidistinguish v1.2", withExtension: "mlmodelc") {
                let mlModel = try MLModel(contentsOf: modelURL, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
                print("MLDistinguishProcessor: Successfully loaded maimaidistinguish v1.2 model.")
            } else if let urls = Bundle.main.urls(forResourcesWithExtension: "mlmodelc", subdirectory: nil), let first = urls.first(where: { $0.lastPathComponent.contains("maimaidistinguish") }) {
                print("MLDistinguishProcessor: Loaded fallback model \(first.lastPathComponent)")
                let mlModel = try MLModel(contentsOf: first, configuration: config)
                self.visionModel = try VNCoreMLModel(for: mlModel)
            } else {
                print("MLDistinguishProcessor: maimaidistinguish model not found in bundle. Check Target Membership.")
            }
        } catch {
            print("MLDistinguishProcessor: Error loading maimaidistinguish model - \(error)")
        }
    }
    
    // MARK: - Processing
    
    /// Classifies an image into .score, .choose, or .unknown
    func classify(_ image: UIImage) async -> MaimaiImageType {
        let normalizedImage = image.normalized()
        guard let cgImage = normalizedImage.cgImage, let vnModel = visionModel else {
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
