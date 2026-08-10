import Foundation
import UIKit
import Photos

nonisolated final class PhotoService: Sendable {
    static let shared = PhotoService()
    private static let albumName = "maimai"
    
    private init() {}
    
    // MARK: - Album Management
    
    private static func fetchMaimaiAlbum() -> PHAssetCollection? {
        let fetchOptions = PHFetchOptions()
        fetchOptions.predicate = NSPredicate(format: "title = %@", albumName)
        let collection = PHAssetCollection.fetchAssetCollections(with: .album, subtype: .any, options: fetchOptions)
        return collection.firstObject
    }
    
    private static func createMaimaiAlbum() async throws -> PHAssetCollection {
        if let existingAlbum = fetchMaimaiAlbum() {
            return existingAlbum
        }
        
        var albumPlaceholder: PHObjectPlaceholder?
        try await PHPhotoLibrary.shared().performChanges {
            let createAlbumRequest = PHAssetCollectionChangeRequest.creationRequestForAssetCollection(withTitle: albumName)
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
    
    private static func requestAuthorization() async -> PHAuthorizationStatus {
        let currentStatus = PHPhotoLibrary.authorizationStatus(for: .readWrite)
        guard currentStatus == .notDetermined else { return currentStatus }
        return await PHPhotoLibrary.requestAuthorization(for: .readWrite)
    }
    
    /// Adds searchable title and tag metadata while preserving the camera's original image format.
    private static func photoDataWithMetadata(_ originalData: Data, title: String?, tags: [String]?) -> Data? {
        guard let source = CGImageSourceCreateWithData(originalData as CFData, nil),
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
    
    public func savePhotoDataWithMetadata(_ data: Data, title: String?, tags: [String]? = nil) async throws {
        let authorizationStatus = await Self.requestAuthorization()
        guard authorizationStatus == .authorized || authorizationStatus == .limited else {
            throw NSError(domain: "PhotoService", code: 3, userInfo: [NSLocalizedDescriptionKey: "Photo library access denied"])
        }

        let metadataData = await Task.detached(priority: .userInitiated) {
            Self.photoDataWithMetadata(data, title: title, tags: tags) ?? data
        }.value

        let targetAlbum = try? await Self.createMaimaiAlbum()
        
        try await PHPhotoLibrary.shared().performChanges {
            let creationRequest = PHAssetCreationRequest.forAsset()
            creationRequest.addResource(with: .photo, data: metadataData, options: nil)
            
            if let album = targetAlbum, let placeholder = creationRequest.placeholderForCreatedAsset {
                let albumChangeRequest = PHAssetCollectionChangeRequest(for: album)
                albumChangeRequest?.addAssets([placeholder] as NSArray)
            }
        }
    }
}
