import SwiftUI
import ImageIO

// MARK: - Thumbnail Cache

/// In-memory cache for downsampled song jacket thumbnails.
/// Uses ImageIO to decode at target size directly, avoiding full-resolution decode.
final class ThumbnailCache: @unchecked Sendable {
    static let shared = ThumbnailCache()
    
    /// Target pixel size for thumbnails. 200px covers up to ~9 columns on most devices
    /// at 3x scale (200 / 3 ≈ 66pt). Sharp enough for grid cells.
    private let maxPixelSize: Int = 200
    
    private let cache = NSCache<NSString, UIImage>()
    
    private init() {
        cache.countLimit = 600
        cache.totalCostLimit = 100 * 1024 * 1024 // ~100MB
    }
    
    /// Returns a cached or freshly-downsampled thumbnail for the given image name.
    /// Returns nil if the image hasn't been downloaded yet.
    func thumbnail(for imageName: String) -> UIImage? {
        let key = imageName as NSString
        
        if let cached = cache.object(forKey: key) {
            return cached
        }
        
        guard let fileURL = localFileURL(for: imageName) else {
            return nil
        }
        
        guard let thumbnail = downsample(fileURL: fileURL, maxPixelSize: maxPixelSize) else {
            guard let fallback = UIImage(contentsOfFile: fileURL.path) else { return nil }
            cache.setObject(fallback, forKey: key, cost: estimatedCost(fallback))
            return fallback
        }
        
        cache.setObject(thumbnail, forKey: key, cost: estimatedCost(thumbnail))
        return thumbnail
    }
    
    /// Evict all cached thumbnails (e.g. on memory warning).
    func evictAll() {
        cache.removeAllObjects()
    }
    
    // MARK: - Private
    
    private func localFileURL(for imageName: String) -> URL? {
        let clean = imageName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !clean.isEmpty else { return nil }
        
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        let coverURL = paths[0].appendingPathComponent("Covers").appendingPathComponent(clean)
        
        if FileManager.default.fileExists(atPath: coverURL.path) {
            return coverURL
        }
        
        return nil
    }
    
    /// Use ImageIO to decode the image directly at the target size.
    /// Much faster and uses much less memory than decoding the full image then scaling.
    private func downsample(fileURL: URL, maxPixelSize: Int) -> UIImage? {
        let sourceOptions: [CFString: Any] = [
            kCGImageSourceShouldCache: false
        ]
        
        guard let source = CGImageSourceCreateWithURL(fileURL as CFURL, sourceOptions as CFDictionary) else {
            return nil
        }
        
        let downsampleOptions: [CFString: Any] = [
            kCGImageSourceCreateThumbnailFromImageAlways: true,
            kCGImageSourceShouldCacheImmediately: true,
            kCGImageSourceCreateThumbnailWithTransform: true,
            kCGImageSourceThumbnailMaxPixelSize: maxPixelSize
        ]
        
        guard let cgImage = CGImageSourceCreateThumbnailAtIndex(source, 0, downsampleOptions as CFDictionary) else {
            return nil
        }
        
        return UIImage(cgImage: cgImage)
    }
    
    private func estimatedCost(_ image: UIImage) -> Int {
        guard let cgImage = image.cgImage else { return 0 }
        return cgImage.bytesPerRow * cgImage.height
    }
}

// MARK: - SongJacketView

struct SongJacketView: View {
    let imageName: String
    var size: CGFloat = 60
    var cornerRadius: CGFloat = 12
    /// When true, uses the downsampled thumbnail cache for better grid scrolling performance.
    var useThumbnail: Bool = false
    
    var body: some View {
        Group {
            if let uiImage = loadImage() {
                Image(uiImage: uiImage)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
            } else if let url = URL(string: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/\(imageName)") {
                AsyncImage(url: url) { image in
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                } placeholder: {
                    ZStack {
                        Color.primary.opacity(0.05)
                        if !useThumbnail {
                            ProgressView()
                                .scaleEffect(0.5)
                        }
                    }
                }
            } else {
                ZStack {
                    Color.primary.opacity(0.05)
                    if !useThumbnail {
                        Image(systemName: "music.note")
                            .foregroundStyle(.secondary.opacity(0.3))
                    }
                }
            }
        }
        .frame(width: size, height: size)
        .clipShape(RoundedRectangle(cornerRadius: cornerRadius))
        .overlay(
            RoundedRectangle(cornerRadius: cornerRadius)
                .stroke(Color.primary.opacity(0.1), lineWidth: useThumbnail ? 0.5 : 1)
        )
    }
    
    private func loadImage() -> UIImage? {
        if useThumbnail {
            // Fast path: use downsampled cached thumbnail
            return ThumbnailCache.shared.thumbnail(for: imageName)
        }
        
        // Original path: full-resolution image
        if let downloaded = ImageDownloader.shared.loadImage(imageName: imageName) {
            return downloaded
        }
        
        if let assetImage = UIImage(named: imageName) {
            return assetImage
        }
        
        return nil
    }
}
