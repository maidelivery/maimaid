import Foundation
import Observation
import SwiftData

@MainActor
@Observable
final class CollectionImportCoordinator {
    private(set) var feedbackKey: String?
    private(set) var feedbackID = UUID()

    func importCollection(from value: String, context: ModelContext) async {
        do {
            let payload = try await CollectionSharingService.resolveImport(value)
            try SongCollectionImportService.importCollection(payload, context: context)
            feedbackKey = "collections_import_success"
        } catch is CancellationError {
            return
        } catch {
            feedbackKey = "collections_import_failed"
        }
        feedbackID = UUID()
    }
}
