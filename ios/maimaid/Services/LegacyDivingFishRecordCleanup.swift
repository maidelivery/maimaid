import Foundation
import SwiftData

@MainActor
enum LegacyDivingFishRecordCleanup {
    private static let minimumBatchSize = 100

    static func removeRecords(context: ModelContext) throws -> Int {
        let records = try context.fetch(FetchDescriptor<PlayRecord>())
        let countByBatch = Dictionary(grouping: records, by: batchKey).mapValues(\.count)
        let legacyBatchKeys = Set(
            countByBatch.compactMap { key, count in
                count >= minimumBatchSize ? key : nil
            }
        )
        guard !legacyBatchKeys.isEmpty else { return 0 }

        let legacyRecords = records.filter { legacyBatchKeys.contains(batchKey(for: $0)) }
        for record in legacyRecords {
            context.delete(record)
        }
        try context.save()
        return legacyRecords.count
    }

    private static func batchKey(for record: PlayRecord) -> BatchKey {
        BatchKey(
            profileID: record.userProfileId,
            timestampMilliseconds: Int64((record.playDate.timeIntervalSince1970 * 1_000).rounded())
        )
    }

    private struct BatchKey: Hashable {
        let profileID: UUID?
        let timestampMilliseconds: Int64
    }
}
