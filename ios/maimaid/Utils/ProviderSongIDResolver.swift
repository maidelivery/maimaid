import Foundation

enum ProviderSongIDResolver {
    static func resolve(internalID: Int?, chartType: String) -> Int? {
        guard let internalID, internalID > 0 else { return nil }

        if chartType.lowercased() == "dx", internalID < 10_000 {
            return internalID + 10_000
        }
        return internalID
    }

    static func relatedIDs(to songID: Int) -> [Int] {
        guard songID > 0 else { return [] }

        var ids: [Int] = []
        func append(_ id: Int) {
            guard id > 0, !ids.contains(id) else { return }
            ids.append(id)
        }

        append(songID)
        if songID < 10_000 {
            append(songID + 10_000)
        } else if songID < 100_000 {
            append(songID % 10_000)
        } else {
            let baseID = songID % 100_000
            append(baseID)
            if baseID < 10_000 {
                append(baseID + 10_000)
            }
        }
        return ids
    }

    static func preferredID(from ids: [Int], chartTypes: some Sequence<String>) -> Int? {
        let uniqueIDs = ids.reduce(into: [Int]()) { result, id in
            guard id > 0, !result.contains(id) else { return }
            result.append(id)
        }
        let normalizedTypes = Set(chartTypes.map { $0.lowercased() })

        if normalizedTypes.contains("utage"), let id = uniqueIDs.first(where: { $0 >= 100_000 }) {
            return id
        }
        if normalizedTypes.contains("dx"), let id = uniqueIDs.first(where: { $0 >= 10_000 && $0 < 100_000 }) {
            return id
        }
        if normalizedTypes.contains("std") || normalizedTypes.contains("standard") {
            return uniqueIDs.first(where: { $0 < 10_000 })
        }
        return uniqueIDs.first
    }
}
