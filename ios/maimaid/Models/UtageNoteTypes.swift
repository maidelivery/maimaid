struct UtageNoteTypes: Decodable, Sendable {
    let tap: Int
    let hold: Int
    let slide: Int
    let touch: Int
    let breakCount: Int

    enum CodingKeys: String, CodingKey {
        case tap, hold, slide, touch
        case breakCount = "break"
    }
}
