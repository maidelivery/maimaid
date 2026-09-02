import Foundation

nonisolated struct LetterGameCreateRequest: Codable, Equatable, Sendable {
    let visibility: String
    let hostMode: String
    let turnDurationSeconds: Int
    let stalledRoundLimit: Int
    let songCount: Int?
    let publicHintCost: Int
    let privateHintCost: Int
    let selectionMode: String
    let selectionConfig: [String: LetterGameJSONValue]

    init(
        visibility: String,
        hostMode: String = "fixed",
        turnDurationSeconds: Int = 30,
        stalledRoundLimit: Int = 3,
        songCount: Int? = nil,
        publicHintCost: Int = 5,
        privateHintCost: Int = 10,
        selectionMode: String = "filtered_random",
        selectionConfig: [String: LetterGameJSONValue] = [:]
    ) {
        self.visibility = visibility
        self.hostMode = hostMode
        self.turnDurationSeconds = turnDurationSeconds
        self.stalledRoundLimit = stalledRoundLimit
        self.songCount = songCount
        self.publicHintCost = publicHintCost
        self.privateHintCost = privateHintCost
        self.selectionMode = selectionMode
        self.selectionConfig = selectionConfig
    }
}
