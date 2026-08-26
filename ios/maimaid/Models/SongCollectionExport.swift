import Foundation

struct SongCollectionExport: Sendable, Equatable {
    let name: String
    let entries: [SongCollectionExportEntry]
}
