import Foundation

nonisolated struct LetterGameMatchSong: Codable, Equatable, Identifiable, Sendable {
    let slotId: String
    let title: String
    let remainingCharacterCount: Int
    let status: String
    let completionReason: String?
    let completedByUserId: String?
    let facts: [LetterGameFact]
    let imageName: String?
    let artist: String?
    let version: String?
    let chartTypes: [String]
    // These names mirror the maimai chart terminology used by the backend protocol.
    // swiftlint:disable inclusive_language
    let hasRemaster: Bool
    let masterConstant: String?
    let remasterConstant: String?
    // swiftlint:enable inclusive_language
    let maxConstant: String?

    var id: String { slotId }

    init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        slotId = try container.decode(String.self, forKey: .slotId)
        title = try container.decode(String.self, forKey: .title)
        remainingCharacterCount = try container.decodeIfPresent(Int.self, forKey: .remainingCharacterCount) ?? 0
        status = try container.decodeIfPresent(String.self, forKey: .status) ?? "active"
        completionReason = try container.decodeIfPresent(String.self, forKey: .completionReason)
        completedByUserId = try container.decodeIfPresent(String.self, forKey: .completedByUserId)
        facts = try container.decodeIfPresent([LetterGameFact].self, forKey: .facts) ?? []
        imageName = try container.decodeIfPresent(String.self, forKey: .imageName)
        artist = try container.decodeIfPresent(String.self, forKey: .artist)
        version = try container.decodeIfPresent(String.self, forKey: .version)
        chartTypes = try container.decodeIfPresent([String].self, forKey: .chartTypes) ?? []
        hasRemaster = try container.decodeIfPresent(Bool.self, forKey: .hasRemaster) ?? false
        masterConstant = try container.decodeIfPresent(String.self, forKey: .masterConstant)
        remasterConstant = try container.decodeIfPresent(String.self, forKey: .remasterConstant)
        maxConstant = try container.decodeIfPresent(String.self, forKey: .maxConstant)
    }
}
