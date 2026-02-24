import SwiftUI
import Vision
import Combine

class VisionService: ObservableObject {
    @Published var recognizedRate: Double? = nil
    @Published var isProcessing = false
    
    func recognizeScore(from image: UIImage) {
        isProcessing = true
        
        guard let cgImage = image.cgImage else {
            isProcessing = false
            return
        }
        
        let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
        let request = VNRecognizeTextRequest { [weak self] request, error in
            guard let observations = request.results as? [VNRecognizedTextObservation], error == nil else {
                DispatchQueue.main.async {
                    self?.isProcessing = false
                }
                return
            }
            
            var highestRate: Double? = nil
            
            // Look for patterns like 100.5000%, 99.1234, etc.
            let pattern = "(\\d{2,3}\\.\\d{4})"
            let regex = try? NSRegularExpression(pattern: pattern)
            
            for observation in observations {
                let candidates = observation.topCandidates(1)
                if let topCandidate = candidates.first {
                    let text = topCandidate.string
                    
                    let range = NSRange(location: 0, length: text.utf16.count)
                    if let match = regex?.firstMatch(in: text, options: [], range: range) {
                        if let matchRange = Range(match.range(at: 1), in: text),
                           let rateValue = Double(text[matchRange]) {
                            // Maimai rates are usually between 0 and 101
                            if rateValue <= 101.0 {
                                if highestRate == nil || rateValue > highestRate! {
                                    highestRate = rateValue
                                }
                            }
                        }
                    }
                }
            }
            
            DispatchQueue.main.async {
                self?.recognizedRate = highestRate
                self?.isProcessing = false
            }
        }
        
        request.recognitionLevel = .accurate
        
        do {
            try handler.perform([request])
        } catch {
            print("Vision request failed: \(error)")
            isProcessing = false
        }
    }
}
