import Foundation

nonisolated enum LetterGameInputAction: Equatable, Sendable {
    case openCharacter(String)
    case guessSong(String)

    init?(input: String) {
        let normalized = input.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalized.isEmpty else { return nil }
        if normalized.count == 1 {
            self = .openCharacter(normalized)
        } else {
            self = .guessSong(normalized)
        }
    }

    var payload: [String: LetterGameJSONValue] {
        switch self {
        case .openCharacter(let character):
            ["kind": .string("open_character"), "character": .string(character)]
        case .guessSong(let guess):
            ["kind": .string("guess_song"), "guess": .string(guess)]
        }
    }
}
