import Foundation
import UIKit
import Photos

class PhotoService {
    static let shared = PhotoService()
    private let albumName = "maimai"
    
    private init() {}
    
    // MARK: - Album Management
    
    private func fetchMaimaiAlbum() -> PHAssetCollection? {
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "title = %@", albumName)
        let collection = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: fetchOptions)
        return collection.firstObject
    }
    
    private func createMaimaiAlbum() async throws -> PHAssetCollection {
        if let existingAlbum = fetchMaimaiAlbum() {
            return existingAlbum
        }
        
        var albumPlaceholder: PHObjectPlaceholder?
        try await PHPhotoLibrary.shared().performChanges {
            let createAlbumRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: self.albumName)
            albumPlaceholder = createAlbumRequest.placeholderForCreatedAssetCollection
        }
        
        guard let placeholder = albumPlaceholder else {
            throw NSError(domain: "PhotoService", code: 1, userInfo: [NSLocalizedDescriptionKey: "Failed to allocate album placeholder"])
        }
        
        let fetchResult = PHAssetCollection.fetchAssetCollections(withLocalIdentifiers: [placeholder.localIdentifier], options: nil)
        guard let album = fetchResult.firstObject else {
            throw NSError(domain: "PhotoService", code: 2, userInfo: [NSLocalizedDescriptionKey: "Failed to fetch newly created album"])
        }
        
        return album
    }
    
    // MARK: - Image Saving
    
    /// Requests necessary photo library access.
    public func checkAuthorizations() async -> (canAdd: Bool, canReadWrite: Bool) {
        let rwStatus = await PHPhotoLibrary.requestAuthorization(for: .readWrite)
        let addStatus = await PHPhotoLibrary.requestAuthorization(for: .addOnly)
        
        let canReadWrite = rwStatus == .authorized || rwStatus == .limited
        let canAdd = addStatus == .authorized || addStatus == .limited || canReadWrite
        
        return (canAdd, canReadWrite)
    }
    
    /// Stuffs the title and tags internally inside the JPEG metadata block, specifically the Title/Description fields so it falls into iOS Spotlight scope.
    private func jpegDataWithMetadata(_ uiImage: UIImage, title: String?, tags: [String]?) -> Data? {
        guard let originalData = uiImage.jpegData(compressionQuality: 0.95) as CFData?,
              let source = CGImageSourceCreateWithData(originalData, nil),
              let uti = CGImageSourceGetType(source) else {
            return nil
        }
        
        let mutableData = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(mutableData, uti, 1, nil) else {
            return nil
        }
        
        // Extract existing metadata if presents (orientation, etc)
        var metadata = CGImageSourceCopyPropertiesAtIndex(source, 0, nil) as? [String: Any] ?? [String: Any]()
        
        if let title = title, !title.isEmpty {
            // Write IPTC
            var iptc = metadata[kCGImagePropertyIPTCDictionary as String] as? [String: Any] ?? [String: Any]()
            // IPTC Caption/Abstract
            iptc[kCGImagePropertyIPTCCaptionAbstract as String] = title
            // IPTC Object Name
            iptc[kCGImagePropertyIPTCObjectName as String] = title
            metadata[kCGImagePropertyIPTCDictionary as String] = iptc
            
            // Write TIFF Description
            var tiff = metadata[kCGImagePropertyTIFFDictionary as String] as? [String: Any] ?? [String: Any]()
            tiff[kCGImagePropertyTIFFImageDescription as String] = title
            metadata[kCGImagePropertyTIFFDictionary as String] = tiff
        }
        
        if let tags = tags, !tags.isEmpty {
            var iptc = metadata[kCGImagePropertyIPTCDictionary as String] as? [String: Any] ?? [String: Any]()
            iptc[kCGImagePropertyIPTCKeywords as String] = tags
            metadata[kCGImagePropertyIPTCDictionary as String] = iptc
        }
        
        CGImageDestinationAddImageFromSource(destination, source, 0, metadata as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            return nil
        }
        
        return mutableData as Data
    }
    
    public func saveImageWithMetadata(_ image: UIImage, title: String?, tags: [String]? = nil) async throws {
        let auths = await checkAuthorizations()
        guard auths.canAdd else {
            throw NSError(domain: "PhotoService", code: 3, userInfo: [NSLocalizedDescriptionKey: "Photo library access denied"])
        }
        
        // Try appending EXIF data if title is provided
        guard let metadataData = jpegDataWithMetadata(image, title: title, tags: tags) else {
            throw NSError(domain: "PhotoService", code: 4, userInfo: [NSLocalizedDescriptionKey: "Failed to weave metadata into JPEG"])
        }
        
        var targetAlbum: PHAssetCollection? = nil
        if auths.canReadWrite {
            targetAlbum = try? await createMaimaiAlbum()
        }
        
        try await PHPhotoLibrary.shared().performChanges {
            let creationRequest = PHAssetCreationRequest.forAsset()
            creationRequest.addResource(with: .photo, data: metadataData, options: nil)
            
            // Add to our album if we have read/write access and the album exists
            if let album = targetAlbum, let placeholder = creationRequest.placeholderForCreatedAsset {
                let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                albumChangeRequest?.addAssets([placeholder] as NSArray)
            }
        }
    }
}
