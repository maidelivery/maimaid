import Foundation
import UIKit

class ImageDownloader {
    static let shared = ImageDownloader()
    
    // The directory where covers will be stored
    var coversDirectory: URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let docDir = paths[0]
        let dir = docDir.appendingPathComponent("Covers", isDirectory: true)
        
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true, attributes: nil)
        }
        return dir
    }
    
    func getCoverUrl(for imageName: String) -> URL {
        return coversDirectory.appendingPathComponent(imageName)
    }
    
    func imageExists(imageName: String) -> Bool {
        let url = getCoverUrl(for: imageName)
        return FileManager.default.fileExists(atPath: url.path)
    }
    
    func downloadImage(from urlString: String, as imageName: String) async throws -> URL {
        let destinationUrl = getCoverUrl(for: imageName)
        if FileManager.default.fileExists(atPath: destinationUrl.path) {
            return destinationUrl
        }
        
        guard let url = URL(string: urlString) else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.setValue(AppKeys.userAgent, forHTTPHeaderField: "User-Agent")
        
        let (data, _) = try await URLSession.shared.data(for: request)
        
        // Save to disk
        try data.write(to: destinationUrl)
        
        return destinationUrl
    }
    
    // Utility to get image synchronously if it exists, useful for SwiftUI or UIImage(contentsOfFile:)
    func loadImage(imageName: String) -> UIImage? {
        let url = getCoverUrl(for: imageName)
        return UIImage(contentsOfFile: url.path)
    }
}
