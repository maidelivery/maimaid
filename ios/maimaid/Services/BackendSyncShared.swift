import Foundation

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
