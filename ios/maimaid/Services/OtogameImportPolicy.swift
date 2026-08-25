import CryptoKit
import Foundation

enum OtogameImportPolicy {
    static let playlogPageLimit = 4

    static func isEligibleServer(_ server: String?) -> Bool {
        server?.trimmingCharacters(in: .whitespacesAndNewlines).localizedCaseInsensitiveCompare("jp") == .orderedSame
    }

    static func achievement(_ rawValue: Int64) -> Double {
        Double(rawValue) / 10_000
    }

    static func difficultyCode(for playlog: OtogamePlaylog) -> Int {
        playlog.difficulty >= 0 ? playlog.difficulty : playlog.levelInfo.difficulty
    }

    static func difficulty(for code: Int) -> String? {
        switch code {
        case 0: "basic"
        case 1: "advanced"
        case 2: "expert"
        case 3: "master"
        case 4: "remaster"
        case 10: "utage"
        default: nil
        }
    }

    static func chartType(for playlog: OtogamePlaylog) -> String {
        if difficultyCode(for: playlog) == 10 {
            return "utage"
        }
        return playlog.music.isDeluxe ? "dx" : "standard"
    }

    static func rank(for code: Int, achievement: Double) -> String {
        switch code {
        case 0: "D"
        case 1: "C"
        case 2: "B"
        case 3: "BB"
        case 4: "BBB"
        case 5: "A"
        case 6: "AA"
        case 7: "AAA"
        case 8: "S"
        case 9: "S+"
        case 10: "SS"
        case 11: "SS+"
        case 12: "SSS"
        case 13: "SSS+"
        default: calculatedRank(for: achievement)
        }
    }

    static func calculatedRank(for achievement: Double) -> String {
        switch achievement {
        case 100.5...: "SSS+"
        case 100...: "SSS"
        case 99.5...: "SS+"
        case 99...: "SS"
        case 98...: "S+"
        case 97...: "S"
        case 94...: "AAA"
        case 90...: "AA"
        case 80...: "A"
        case 75...: "BBB"
        case 70...: "BB"
        case 60...: "B"
        case 50...: "C"
        default: "D"
        }
    }

    static func fullCombo(for code: Int) -> String? {
        switch code {
        case 1: "fc"
        case 2: "fcp"
        case 3: "ap"
        case 4: "app"
        default: nil
        }
    }

    static func fullSync(for code: Int) -> String? {
        switch code {
        case 1: "fs"
        case 2: "fsp"
        case 3: "fsd"
        case 4: "fsdp"
        case 5: "sync"
        default: nil
        }
    }

    static func stableRecordID(profileID: UUID, playlog: OtogamePlaylog) -> UUID {
        let identity = [
            profileID.uuidString.lowercased(),
            "otogame",
            playlog.music.musicID,
            String(difficultyCode(for: playlog)),
            String(playlog.playDate),
            String(playlog.trackNumber),
            String(playlog.achievement),
            String(playlog.deluxeScore),
        ].joined(separator: "|")
        var bytes = Array(SHA256.hash(data: Data(identity.utf8)).prefix(16))
        bytes[6] = (bytes[6] & 0x0f) | 0x50
        bytes[8] = (bytes[8] & 0x3f) | 0x80
        return UUID(uuid: (
            bytes[0], bytes[1], bytes[2], bytes[3],
            bytes[4], bytes[5], bytes[6], bytes[7],
            bytes[8], bytes[9], bytes[10], bytes[11],
            bytes[12], bytes[13], bytes[14], bytes[15]
        ))
    }
}
