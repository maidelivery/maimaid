import Foundation
@preconcurrency import CoreML
@preconcurrency import Vision
@preconcurrency import UIKit

/// Processor that encapsulates the logic for classifying Maimai image types using CoreML model.
nonisolated final class MLDistinguishProcessor {
    nonisolated(unsafe) static let shared = MLDistinguishProcessor()
    private init() {}
    
    // MARK: - Processing
    
    /// Classifies an image into .score, .choose, or .unknown
    func classify(_ image: UIImage) async throws -> MaimaiImageType {
        let normalizedImage = await MainActor.run { image.normalized() }
        let visionModel = try await MLModelStore.shared.model(for: .distinguish)
        return Self.classifySynchronously(normalizedImage, visionModel: visionModel)
    }
    
    private static func classifySynchronously(_ image: UIImage, visionModel: VNCoreMLModel?) -> MaimaiImageType {
        guard let cgImage = image.cgImage, let visionModel else {
            return .unknown
        }
        
        let request = VNCoreMLRequest(model: visionModel)
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
