struct UtageChartStatsItem: Decodable, Sendable {
    let id: Int
    let title: String
    let notes: Int
    let noteTypes: UtageNoteTypes?
}
