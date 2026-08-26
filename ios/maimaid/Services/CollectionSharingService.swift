import Foundation

enum CollectionSharingService {
    nonisolated static func isCollectionLink(_ url: URL) -> Bool {
        extractSegment(from: url.absoluteString) != nil
    }

    static func fetchCloudCollection(_ collectionID: UUID) async throws -> SongCollectionExport? {
        do {
            let response: Response = try await BackendAPIClient.request(
                path: "v1/public/collections/\(collectionID.uuidString.lowercased())",
                authentication: .none
            )
            return SongCollectionExport(
                name: response.collection.name,
                entries: response.collection.entries.map {
                    SongCollectionExportEntry(songId: $0.songId, chartType: $0.chartType, difficulty: $0.difficulty)
                }
            )
        } catch let error as BackendAPIError where error.statusCode == 404 {
            return nil
        }
    }

    static func resolveImport(_ value: String) async throws -> SongCollectionExport {
        let normalized = value.filter { !$0.isWhitespace }
        if let snapshot = extractSnapshot(from: normalized) {
            return try SongCollectionCodec.decode(snapshot)
        }
        guard let collectionID = extractCollectionID(from: normalized),
              let collection = try await fetchCloudCollection(collectionID) else {
            throw SongCollectionCodecError.invalid
        }
        return collection
    }

    private static func extractSnapshot(from value: String) -> String? {
        if value.hasPrefix(SongCollectionCodec.prefix) {
            return value
        }
        guard let segment = extractSegment(from: value), segment.hasPrefix(SongCollectionCodec.prefix) else {
            return nil
        }
        return segment
    }

    private static func extractCollectionID(from value: String) -> UUID? {
        if let direct = UUID(uuidString: value) {
            return direct
        }
        return extractSegment(from: value).flatMap(UUID.init(uuidString:))
    }

    nonisolated private static func extractSegment(from value: String) -> String? {
        guard let components = URLComponents(string: value) else {
            return nil
        }
        if components.scheme == "https", components.host == "maimaid.rhythmeta.org" {
            let parts = components.path.split(separator: "/", omittingEmptySubsequences: true)
            guard parts.count == 2, parts.first == "collection" else { return nil }
            return String(parts[1])
        }
        if components.scheme == "maimaid", components.host == "collection" {
            return components.path.split(separator: "/", omittingEmptySubsequences: true).first.map(String.init)
        }
        return nil
    }
}

private extension CollectionSharingService {
    struct Response: Decodable {
        let collection: Collection
    }

    struct Collection: Decodable {
        let name: String
        let entries: [Entry]
    }

    struct Entry: Decodable {
        let songId: String
        let chartType: String
        let difficulty: String
    }
}
