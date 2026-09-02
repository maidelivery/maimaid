import Testing
@testable import maimaid

struct LetterGameInputActionTests {
    @Test(
        "Input selects the matching server action",
        arguments: [
            (" A ", LetterGameInputAction.openCharacter("A")),
            ("Garakuta Doll Play", LetterGameInputAction.guessSong("Garakuta Doll Play")),
            ("  ", nil as LetterGameInputAction?)
        ]
    )
    func classifiesInput(input: String, expected: LetterGameInputAction?) {
        #expect(LetterGameInputAction(input: input) == expected)
    }

    @Test("Open-character payload applies to all songs")
    func buildsGlobalOpenPayload() throws {
        let action = try #require(LetterGameInputAction(input: "Q"))

        #expect(action.payload == [
            "kind": .string("open_character"),
            "character": .string("Q")
        ])
        #expect(action.payload["slotId"] == nil)
    }

    @Test("Guess payload lets the server resolve titles and aliases")
    func buildsGuessPayload() throws {
        let action = try #require(LetterGameInputAction(input: "World's end loneliness"))

        #expect(action.payload == [
            "kind": .string("guess_song"),
            "guess": .string("World's end loneliness")
        ])
        #expect(action.payload["slotId"] == nil)
    }
}
