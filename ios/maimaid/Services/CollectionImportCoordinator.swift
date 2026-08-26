import Foundation
import Observation
import SwiftData

@MainActor
@Observable
final class CollectionImportCoordinator {
    struct PendingImport: Identifiable {
        let id = UUID()
        let payload: SongCollectionExport
    }

    private(set) var feedbackKey: String?
    private(set) var feedbackID = UUID()
    var pendingImport: PendingImport?

    func prepareImport(from value: String) {
        pendingImport = nil
        Task { [weak self] in
            do {
                let payload = try await CollectionSharingService.resolveImport(value)
                guard !Task.isCancelled else { return }
                self?.pendingImport = PendingImport(payload: payload)
            } catch is CancellationError {
                return
            } catch {
                self?.feedbackKey = "collections_import_failed"
                self?.feedbackID = UUID()
            }
        }
    }

    func cancelPendingImport() {
        pendingImport = nil
    }

    func confirmPendingImport(_ payload: SongCollectionExport, context: ModelContext) async {
        pendingImport = nil
        do {
            try SongCollectionImportService.importCollection(payload, context: context)
            feedbackKey = "collections_import_success"
        } catch {
            feedbackKey = "collections_import_failed"
        }
        feedbackID = UUID()
    }

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
