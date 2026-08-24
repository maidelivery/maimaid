import Foundation
import Compression

struct BackendSyncFlexibleDouble: Decodable {
    let value: Double

    init(from decoder: Decoder) throws {
        let container = try decoder.singleValueContainer()
        if let number = try? container.decode(Double.self) {
            value = number
            return
        }
        if let intValue = try? container.decode(Int.self) {
            value = Double(intValue)
            return
        }
        if let string = try? container.decode(String.self), let parsed = Double(string) {
            value = parsed
            return
        }
        throw DecodingError.typeMismatch(
            Double.self,
            .init(codingPath: decoder.codingPath, debugDescription: "Expected numeric value.")
        )
    }
}

enum BackendSyncShared {
    nonisolated static func canonicalScoreSheetId(for sheet: Sheet) -> String {
        "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
    }

    nonisolated static func canonicalRecordSheetId(for sheet: Sheet) -> String {
        "\(sheet.songIdentifier)-\(sheet.type)-\(sheet.difficulty)"
    }

    nonisolated static func buildSheetMap(for sheets: [Sheet], separators: [String]) -> [String: Sheet] {
        var map: [String: Sheet] = [:]
        for sheet in sheets {
            let chartType = canonicalChartType(sheet.type)
            let difficulty = canonicalDifficulty(sheet.difficulty)
            guard !chartType.isEmpty, !difficulty.isEmpty else {
                continue
            }
            for identifier in songIdentifiers(for: sheet) {
                for separator in separators {
                    map[sheetKey(identifier: identifier, separator: separator, chartType: chartType, difficulty: difficulty)] = sheet
                }
            }
        }
        return map
    }

    nonisolated static func resolveSheet(
        songIdentifier: String,
        songId: Int,
        chartType: String,
        difficulty: String,
        sheetMap: [String: Sheet]
    ) -> Sheet? {
        let identifierCandidates = [songIdentifier, String(songId)]
            .compactMap(normalizeIdentifier(_:))
            .filter { !$0.isEmpty && $0 != "0" }
        let canonicalType = canonicalChartType(chartType)
        let canonicalDifficulty = canonicalDifficulty(difficulty)
        guard !canonicalType.isEmpty, !canonicalDifficulty.isEmpty else {
            return nil
        }

        for identifier in identifierCandidates {
            for separator in ["_", "-"] {
                let key = sheetKey(
                    identifier: identifier,
                    separator: separator,
                    chartType: canonicalType,
                    difficulty: canonicalDifficulty
                )
                if let sheet = sheetMap[key] {
                    return sheet
                }
            }
        }
        return nil
    }

    nonisolated static func resolveSheet(for existingSheetId: String, sheetMap: [String: Sheet]) -> Sheet? {
        let key = existingSheetId.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if let sheet = sheetMap[key] {
            return sheet
        }
        let swapped = key.contains("_")
            ? key.replacing("_", with: "-")
            : key.replacing("-", with: "_")
        return sheetMap[swapped]
    }

    nonisolated static func downloadAvatarData(from avatarURLString: String?) async -> Data? {
        guard let avatarURLString, let avatarURL = URL(string: avatarURLString) else {
            return nil
        }

        var request = URLRequest(url: avatarURL)
        request.httpMethod = "GET"
        request.timeoutInterval = 30

        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                return nil
            }
            guard (200...299).contains(httpResponse.statusCode), !data.isEmpty else {
                return nil
            }
            return data
        } catch {
            return nil
        }
    }

    nonisolated private static func songIdentifiers(for sheet: Sheet) -> Set<String> {
        var ids: Set<String> = []
        if let normalized = normalizeIdentifier(sheet.songIdentifier), !normalized.isEmpty {
            ids.insert(normalized)
        }
        if sheet.songId > 0 {
            ids.insert(String(sheet.songId))
        }
        if let song = sheet.song {
            if let normalized = normalizeIdentifier(song.songIdentifier), !normalized.isEmpty {
                ids.insert(normalized)
            }
            if song.songId > 0 {
                ids.insert(String(song.songId))
            }
        }
        return ids
    }

    nonisolated private static func normalizeIdentifier(_ value: String) -> String? {
        let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            return nil
        }
        return trimmed.lowercased()
    }

    nonisolated private static func canonicalChartType(_ value: String) -> String {
        let normalized = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if normalized == "standard" || normalized == "std" || normalized == "sd" {
            return "std"
        }
        if normalized == "dx" {
            return "dx"
        }
        if normalized == "utage" {
            return "utage"
        }
        return normalized
    }

    nonisolated private static func canonicalDifficulty(_ value: String) -> String {
        let lowered = value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let normalized = lowered
            .replacing(" ", with: "")
            .replacing("_", with: "")
            .replacing(":", with: "")
        if normalized == "remaster" {
            return "remaster"
        }
        return normalized
    }

    nonisolated private static func sheetKey(
        identifier: String,
        separator: String,
        chartType: String,
        difficulty: String
    ) -> String {
        "\(identifier)\(separator)\(chartType)\(separator)\(difficulty)".lowercased()
    }
}

struct SongCollectionExport: Codable, Sendable {
    let version: Int
    let kind: String
    let collections: [SongCollectionExportCollection]

    enum CodingKeys: String, CodingKey { case version = "v", kind = "k", collections = "c" }
}

struct SongCollectionExportCollection: Codable, Sendable {
    let id: String
    let name: String
    let position: Int
    let entries: [SongCollectionExportEntry]

    enum CodingKeys: String, CodingKey { case id = "i", name = "n", position = "p", entries = "e" }
}

struct SongCollectionExportEntry: Codable, Sendable {
    let songId: String
    let chartType: String
    let difficulty: String
    let position: Int

    enum CodingKeys: String, CodingKey { case songId = "s", chartType = "t", difficulty = "d", position = "p" }
}

enum SongCollectionCodec {
    nonisolated static let prefix = "MMD1."
    nonisolated static let kind = "MMD_COLLECTIONS"

    static func encode(collections: [SongCollection], items: [SongCollectionItem]) throws -> String {
        let active = collections.filter { $0.deletedAt == nil }.sorted { $0.sortIndex == $1.sortIndex ? $0.id.uuidString < $1.id.uuidString : $0.sortIndex < $1.sortIndex }
        let itemMap = Dictionary(grouping: items.filter { $0.deletedAt == nil }, by: \.collectionId)
        let payload = SongCollectionExport(version: 1, kind: kind, collections: active.map { collection in
            SongCollectionExportCollection(
                id: collection.id.uuidString.lowercased(), name: collection.name, position: collection.sortIndex,
                entries: (itemMap[collection.id] ?? []).sorted { $0.position == $1.position ? $0.id.uuidString < $1.id.uuidString : $0.position < $1.position }.map {
                    SongCollectionExportEntry(songId: $0.songId, chartType: $0.chartType.lowercased(), difficulty: $0.difficulty.lowercased(), position: $0.position)
                }
            )
        })
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.sortedKeys]
        let raw = try encoder.encode(payload)
        // NSData's .zlib stream is raw DEFLATE on Apple platforms. Android mirrors this with nowrap=true.
        let compressed = try (raw as NSData).compressed(using: .zlib) as Data
        return prefix + compressed.base64EncodedString()
            .replacingOccurrences(of: "+", with: "-")
            .replacingOccurrences(of: "/", with: "_")
            .replacingOccurrences(of: "=", with: "")
    }

    static func decode(_ value: String) throws -> SongCollectionExport {
        let normalizedValue = value.filter { !$0.isWhitespace }
        guard normalizedValue.hasPrefix(prefix), normalizedValue.count <= 2_000_000 else { throw CocoaError(.fileReadCorruptFile) }
        var encoded = String(normalizedValue.dropFirst(prefix.count)).replacingOccurrences(of: "-", with: "+").replacingOccurrences(of: "_", with: "/")
        encoded += String(repeating: "=", count: (4 - encoded.count % 4) % 4)
        guard let compressed = Data(base64Encoded: encoded), compressed.count <= 1_000_000,
              let raw = try? (compressed as NSData).decompressed(using: .zlib) as Data,
              raw.count <= 1_000_000 else { throw CocoaError(.fileReadCorruptFile) }
        let payload = try JSONDecoder().decode(SongCollectionExport.self, from: raw)
        guard payload.version == 1, payload.kind == kind, payload.collections.count <= 100 else { throw CocoaError(.fileReadCorruptFile) }
        return payload
    }
}
