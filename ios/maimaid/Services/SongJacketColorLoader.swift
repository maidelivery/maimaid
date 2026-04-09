import Foundation
import ImageIO
import UIKit

enum SongJacketColorLoader {
    private static let cache = NSCache<NSString, UIColor>()

    private struct ColorComponents: Sendable {
        let red: Double
        let green: Double
        let blue: Double
        let alpha: Double

        var color: UIColor {
            UIColor(
                red: CGFloat(red),
                green: CGFloat(green),
                blue: CGFloat(blue),
                alpha: CGFloat(alpha)
            )
        }
    }

    static func dominantColor(for imageName: String) -> UIColor? {
        let normalizedImageName = imageName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedImageName.isEmpty else { return nil }

        let cacheKey = normalizedImageName as NSString
        if let cachedColor = cache.object(forKey: cacheKey) {
            return cachedColor
        }

        guard let components = loadDominantColorComponents(for: normalizedImageName) else {
            return nil
        }

        let color = components.color
        cache.setObject(color, forKey: cacheKey)
        return color
    }

    nonisolated private static func loadDominantColorComponents(for imageName: String) -> ColorComponents? {
        guard let imageSource = makeImageSource(for: imageName) else { return nil }

        let downsampleOptions: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: 96
        ]

        let cgImage = CGImageSourceCreateThumbnailAtIndex(
            imageSource,
            0,
            downsampleOptions as CFDictionary
        ) ?? CGImageSourceCreateImageAtIndex(imageSource, 0, nil)

        guard
            let cgImage,
            let averageColor = cgImage.averageColor()
        else {
            return nil
        }

        var red: CGFloat = 0
        var green: CGFloat = 0
        var blue: CGFloat = 0
        var alpha: CGFloat = 0

        guard averageColor.getRed(&red, green: &green, blue: &blue, alpha: &alpha) else {
            return nil
        }

        return ColorComponents(
            red: Double(red),
            green: Double(green),
            blue: Double(blue),
            alpha: Double(alpha)
        )
    }

    nonisolated private static func makeImageSource(for imageName: String) -> CGImageSource? {
        let sourceOptions: [CFString: Any] = [
            kCGImageSourceShouldCache: false
        ]

        if let fileURL = localFileURL(for: imageName) {
            return CGImageSourceCreateWithURL(fileURL as CFURL, sourceOptions as CFDictionary)
        }

        if let assetURL = Bundle.main.url(forResource: imageName, withExtension: nil) {
            return CGImageSourceCreateWithURL(assetURL as CFURL, sourceOptions as CFDictionary)
        }

        return nil
    }

    nonisolated private static func localFileURL(for imageName: String) -> URL? {
        let fileURL = URL.documentsDirectory
            .appending(path: "Covers", directoryHint: .isDirectory)
            .appending(path: imageName)

        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }
        return fileURL
    }
}
