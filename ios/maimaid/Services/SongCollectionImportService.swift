import Foundation
import SwiftData

enum SongCollectionImportService {
    @MainActor
    static func importCollection(_ payload: SongCollectionExport, context: ModelContext) throws {
        let descriptor = FetchDescriptor<SongCollection>()
        let collections = try context.fetch(descriptor).filter { $0.deletedAt == nil }
        let existingNames = Set(collections.map(\.name))
        let baseName = String(payload.name.prefix(40))
        let collection = SongCollection(
            name: uniqueName(from: baseName, existingNames: existingNames),
            sortIndex: collections.count,
            clientUpdatedAt: .now
        )
        context.insert(collection)

        for (position, entry) in payload.entries.enumerated() {
            context.insert(
                SongCollectionItem(
                    collectionId: collection.id,
                    songId: entry.songId,
                    chartType: entry.chartType,
                    difficulty: entry.difficulty,
                    position: position,
                    clientUpdatedAt: .now
                )
            )
        }
        try context.save()
    }

    private static func uniqueName(from rawName: String, existingNames: Set<String>) -> String {
        let baseName = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
        let resolvedBaseName = baseName.isEmpty ? String(localized: "collections_import_default_name") : baseName
        guard existingNames.contains(resolvedBaseName) else { return resolvedBaseName }
        var suffix = 2
        while existingNames.contains("\(resolvedBaseName) (\(suffix))") {
            suffix += 1
        }
        return "\(resolvedBaseName) (\(suffix))"
    }
}
