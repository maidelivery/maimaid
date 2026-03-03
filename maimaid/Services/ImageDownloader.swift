import Foundation
import UIKit

@MainActor
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
    
    // The directory where icons will be stored
    var iconsDirectory: URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let docDir = paths[0]
        let dir = docDir.appendingPathComponent("Icons", isDirectory: true)
        
        if !FileManager.default.fileExists(atPath: dir.path) {
            try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true, attributes: nil)
        }
        return dir
    }
    
    func getCoverUrl(for imageName: String) -> URL {
        let cleanName = imageName.trimmingCharacters(in: .whitespacesAndNewlines)
        return coversDirectory.appendingPathComponent(cleanName)
    }
    
    func getIconUrl(for iconId: Int) -> URL {
        return iconsDirectory.appendingPathComponent("\(iconId).png")
    }
    
    private func isValidImageName(_ name: String) -> Bool {
        let clean = name.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty || clean.lowercased() == "n/a" || clean.lowercased() == "n/a " {
            return false
        }
        return true
    }
    
    func imageExists(imageName: String) -> Bool {
        guard isValidImageName(imageName) else { return false }
        let url = getCoverUrl(for: imageName)
        return FileManager.default.fileExists(atPath: url.path)
    }
    
    func iconExists(iconId: Int) -> Bool {
        let url = getIconUrl(for: iconId)
        return FileManager.default.fileExists(atPath: url.path)
    }
    
    func downloadImage(from urlString: String, as imageName: String) async throws -> URL {
        guard isValidImageName(imageName) else {
            throw URLError(.badURL)
        }
        
        let destinationUrl = getCoverUrl(for: imageName)
        return try await downloadAndSave(from: urlString, to: destinationUrl)
    }
    
    func downloadIcon(from urlString: String, id: Int) async throws -> URL {
        let destinationUrl = getIconUrl(for: id)
        return try await downloadAndSave(from: urlString, to: destinationUrl)
    }
    
    private func downloadAndSave(from urlString: String, to destinationUrl: URL) async throws -> URL {
        if FileManager.default.fileExists(atPath: destinationUrl.path) {
            return destinationUrl
        }
        
        guard let url = URL(string: urlString) else {
            throw URLError(.badURL)
        }
        
        var request = URLRequest(url: url)
        request.setValue(AppKeys.userAgent, forHTTPHeaderField: "User-Agent")
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        // Basic validation: ensure we didn't get an HTML error page
        if let httpResponse = response as? HTTPURLResponse, httpResponse.statusCode != 200 {
            throw URLError(.badServerResponse)
        }
        
        // Check if data is actually an image (rough check)
        // Wrap in autoreleasepool to release the UIImage instance immediately
        let isValidImage = autoreleasepool {
            return UIImage(data: data) != nil
        }
        
        guard isValidImage else {
            throw URLError(.cannotDecodeContentData)
        }
        
        // Save to disk
        try data.write(to: destinationUrl)
        
        return destinationUrl
    }
    
    // Utility to get image synchronously if it exists, useful for SwiftUI or UIImage(contentsOfFile:)
    func loadImage(imageName: String) -> UIImage? {
        guard isValidImageName(imageName) else { return nil }
        let url = getCoverUrl(for: imageName)
        return UIImage(contentsOfFile: url.path)
    }
    
    func loadImage(iconId: Int) -> UIImage? {
        let url = getIconUrl(for: iconId)
        return UIImage(contentsOfFile: url.path)
    }
}
