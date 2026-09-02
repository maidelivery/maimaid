import Foundation

nonisolated struct LetterGameRoomSettings: Codable, Equatable, Sendable {
    let turnDurationSeconds: Int
    let stalledRoundLimit: Int
    let songCountOverride: Int?
    let publicHintCost: Int
    let privateHintCost: Int
    let selectionMode: String
    let selectionConfig: [String: LetterGameJSONValue]
    let selectedCollections: [LetterGameCollectionSummary]

    init(
        turnDurationSeconds: Int = 30,
        stalledRoundLimit: Int = 3,
        songCountOverride: Int? = nil,
        publicHintCost: Int = 5,
        privateHintCost: Int = 10,
        selectionMode: String = "filtered_random",
        selectionConfig: [String: LetterGameJSONValue] = [:],
        selectedCollections: [LetterGameCollectionSummary] = []
    ) {
        self.turnDurationSeconds = turnDurationSeconds
        self.stalledRoundLimit = stalledRoundLimit
        self.songCountOverride = songCountOverride
        self.publicHintCost = publicHintCost
        self.privateHintCost = privateHintCost
        self.selectionMode = selectionMode
        self.selectionConfig = selectionConfig
        self.selectedCollections = selectedCollections
    }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.init(
            turnDurationSeconds: try container.decodeIfPresent(Int.self, forKey: .turnDurationSeconds) ?? 30,
            stalledRoundLimit: try container.decodeIfPresent(Int.self, forKey: .stalledRoundLimit) ?? 3,
            songCountOverride: try container.decodeIfPresent(Int.self, forKey: .songCountOverride),
            publicHintCost: try container.decodeIfPresent(Int.self, forKey: .publicHintCost) ?? 5,
            privateHintCost: try container.decodeIfPresent(Int.self, forKey: .privateHintCost) ?? 10,
            selectionMode: try container.decodeIfPresent(String.self, forKey: .selectionMode) ?? "filtered_random",
            selectionConfig: try container.decodeIfPresent(
                [String: LetterGameJSONValue].self,
                forKey: .selectionConfig
            ) ?? [:],
            selectedCollections: try container.decodeIfPresent(
                [LetterGameCollectionSummary].self,
                forKey: .selectedCollections
            ) ?? []
        )
    }
}
