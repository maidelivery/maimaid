import Foundation

@MainActor
struct OtogameSheetMatcher {
    private let songsByIdentifier: [String: Song]
    private let japaneseSheets: [Sheet]

    init(songs: [Song], sheets: [Sheet]) {
        songsByIdentifier = Dictionary(songs.map { ($0.songIdentifier, $0) }, uniquingKeysWith: { first, _ in first })
        japaneseSheets = sheets.filter(\.regionJp)
    }

    func match(_ playlog: OtogamePlaylog) -> Sheet? {
        guard let difficulty = OtogameImportPolicy.difficulty(
            for: OtogameImportPolicy.difficultyCode(for: playlog)
        ) else {
            return nil
        }

        let expectedType = OtogameImportPolicy.chartType(for: playlog)
        let expectedTitle = normalizeTitle(playlog.music.name)
        guard !expectedTitle.isEmpty else {
            return nil
        }

        let matches = japaneseSheets.filter { sheet in
            guard chartType(sheet.type) == expectedType,
                  difficultyMatches(sheet, expected: difficulty, utageKanji: playlog.music.utageKanjiName) else {
                return false
            }
            let title = sheet.song?.title ?? songsByIdentifier[sheet.songIdentifier]?.title ?? ""
            return normalizeTitle(title) == expectedTitle
        }
        return matches.count == 1 ? matches[0] : nil
    }

    func match(_ entry: OtogameRatingEntry) -> Sheet? {
        match(
            music: entry.music,
            difficultyCode: entry.levelInfo.difficulty
        )
    }

    private func match(music: OtogameMusic, difficultyCode: Int) -> Sheet? {
        guard let difficulty = OtogameImportPolicy.difficulty(for: difficultyCode) else {
            return nil
        }
        let expectedType = music.isDeluxe ? "dx" : "standard"
        let expectedTitle = normalizeTitle(music.name)
        guard !expectedTitle.isEmpty else {
            return nil
        }
        let matches = japaneseSheets.filter { sheet in
            guard chartType(sheet.type) == expectedType,
                  difficultyMatches(sheet, expected: difficulty, utageKanji: music.utageKanjiName) else {
                return false
            }
            let title = sheet.song?.title ?? songsByIdentifier[sheet.songIdentifier]?.title ?? ""
            return normalizeTitle(title) == expectedTitle
        }
        return matches.count == 1 ? matches[0] : nil
    }

    private func chartType(_ value: String) -> String {
        switch value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() {
        case "std", "standard": "standard"
        case "utage": "utage"
        default: value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        }
    }

    private func difficultyMatches(_ sheet: Sheet, expected: String, utageKanji: String?) -> Bool {
        if expected != "utage" {
            return sheet.difficulty.localizedCaseInsensitiveCompare(expected) == .orderedSame
        }
        let expectedKanji = normalizeUtageKanji(utageKanji ?? "")
        return expectedKanji.isEmpty || normalizeUtageKanji(sheet.difficulty) == expectedKanji
    }

    private func normalizeTitle(_ value: String) -> String {
        let compatible = value.precomposedStringWithCompatibilityMapping
            .trimmingCharacters(in: .whitespacesAndNewlines)
        let withoutPrefix = removingUtagePrefix(from: compatible)
        return withoutPrefix.lowercased().unicodeScalars.reduce(into: "") { result, scalar in
            if scalar.properties.isAlphabetic || scalar.properties.numericType != nil {
                result.unicodeScalars.append(scalar)
            }
        }
    }

    private func normalizeUtageKanji(_ value: String) -> String {
        value.precomposedStringWithCompatibilityMapping.unicodeScalars.reduce(into: "") { result, scalar in
            if scalar.properties.isAlphabetic || scalar.properties.numericType != nil {
                result.unicodeScalars.append(scalar)
            }
        }
    }

    private func removingUtagePrefix(from value: String) -> String {
        guard let first = value.first else {
            return value
        }
        let closingCharacter: Character? = switch first {
        case "[": "]"
        case "【": "】"
        case "［": "］"
        default: nil
        }
        guard let closingCharacter,
              let closingIndex = value.firstIndex(of: closingCharacter) else {
            return value
        }
        return String(value[value.index(after: closingIndex)...])
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }
}
