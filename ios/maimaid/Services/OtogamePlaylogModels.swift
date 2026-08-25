import Foundation

struct OtogamePlaylogResponse: Decodable, Sendable {
    let code: String
    let message: String
    let data: OtogamePlaylogPage
}

struct OtogameRatingResponse: Decodable, Sendable {
    let data: OtogameRatingData
}

struct OtogameRatingData: Decodable, Sendable {
    let ratingList: [OtogameRatingEntry]
    let newRatingList: [OtogameRatingEntry]

    private enum CodingKeys: String, CodingKey {
        case ratingList = "rating_list"
        case newRatingList = "new_rating_list"
    }
}

struct OtogameRatingEntry: Decodable, Sendable {
    let music: OtogameMusic
    let levelInfo: OtogameLevelInfo
    let achievement: Int64
    let comboStatus: Int

    private enum CodingKeys: String, CodingKey {
        case music
        case levelInfo = "level_info"
        case achievement
        case comboStatus = "combo_status"
    }
}

struct OtogamePlaylogPage: Decodable, Sendable {
    let data: [OtogamePlaylog]
    let pagination: OtogamePagination
}

struct OtogamePagination: Decodable, Sendable {
    let page: Int
    let perPage: Int
    let totalPage: Int

    private enum CodingKeys: String, CodingKey {
        case page
        case perPage
        case perPageSnake = "per_page"
        case totalPage
        case totalPageSnake = "total_page"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        page = try container.decodeIfPresent(Int.self, forKey: .page) ?? 1
        perPage = try container.decodeIfPresent(Int.self, forKey: .perPageSnake)
            ?? container.decodeIfPresent(Int.self, forKey: .perPage)
            ?? 0
        totalPage = try container.decodeIfPresent(Int.self, forKey: .totalPageSnake)
            ?? container.decodeIfPresent(Int.self, forKey: .totalPage)
            ?? 1
    }
}

struct OtogamePlaylog: Decodable, Sendable {
    let music: OtogameMusic
    let difficulty: Int
    let levelInfo: OtogameLevelInfo
    let trackNumber: Int
    let playDate: Int64
    let achievement: Int64
    let scoreRank: Int
    let deluxeScore: Int
    let comboStatus: Int
    let syncStatus: Int

    private enum CodingKeys: String, CodingKey {
        case music
        case difficulty
        case levelInfo = "level_info"
        case trackNumber = "track_no"
        case playDate = "play_date"
        case achievement
        case scoreRank = "score_rank"
        case deluxeScore = "deluxe_score"
        case comboStatus = "combo_status"
        case syncStatus = "sync_status"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        music = try container.decode(OtogameMusic.self, forKey: .music)
        difficulty = try container.decodeIfPresent(Int.self, forKey: .difficulty) ?? -1
        levelInfo = try container.decodeIfPresent(OtogameLevelInfo.self, forKey: .levelInfo) ?? .empty
        trackNumber = try container.decodeIfPresent(Int.self, forKey: .trackNumber) ?? 0
        playDate = try container.decodeIfPresent(Int64.self, forKey: .playDate) ?? 0
        achievement = try container.decodeIfPresent(Int64.self, forKey: .achievement) ?? 0
        scoreRank = try container.decodeIfPresent(Int.self, forKey: .scoreRank) ?? 0
        deluxeScore = try container.decodeIfPresent(Int.self, forKey: .deluxeScore) ?? 0
        comboStatus = try container.decodeIfPresent(Int.self, forKey: .comboStatus) ?? 0
        syncStatus = try container.decodeIfPresent(Int.self, forKey: .syncStatus) ?? 0
    }
}

struct OtogameMusic: Decodable, Sendable {
    let musicID: String
    let name: String
    let isDeluxe: Bool
    let utageKanjiName: String?

    private enum CodingKeys: String, CodingKey {
        case musicID = "music_id"
        case name
        case isDeluxe = "is_deluxe"
        case utageKanjiName = "utage_kanji_name"
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        if let stringID = try? container.decode(String.self, forKey: .musicID) {
            musicID = stringID
        } else if let integerID = try? container.decode(Int64.self, forKey: .musicID) {
            musicID = String(integerID)
        } else {
            musicID = ""
        }
        name = try container.decodeIfPresent(String.self, forKey: .name) ?? ""
        isDeluxe = try container.decodeIfPresent(Bool.self, forKey: .isDeluxe) ?? false
        utageKanjiName = try container.decodeIfPresent(String.self, forKey: .utageKanjiName)
    }
}

struct OtogameLevelInfo: Decodable, Sendable {
    let difficulty: Int
    let level: Int

    static let empty = OtogameLevelInfo(difficulty: -1, level: 0)
}
