import Testing
@testable import maimaid

struct LetterGameLogNarratorTests {
    @Test("Narration tracks droughts, recovery, and per-player streaks")
    func tracksReplayState() {
        let logs = [
            log(id: "1", action: "guess_song", correct: false),
            log(id: "2", action: "guess_song", correct: false),
            log(id: "3", action: "open_character", character: "Q", count: 0),
            log(id: "4", action: "guess_song", points: 12, correct: true),
            log(id: "5", action: "guess_song", points: 10, correct: true),
            log(id: "6", action: "open_character", character: "A", count: 1, points: 1),
            log(id: "7", action: "open_character", character: "B", count: 2, points: 2)
        ]

        let narrations = LetterGameLogNarrator.narrate(logs)

        #expect(isIncorrect(narrations[0].template))
        #expect(isIncorrectStreak(narrations[1].template))
        #expect(isNoProgressStreak(narrations[2].template))
        #expect(isRecovered(narrations[3].template))
        #expect(isCorrect(narrations[4].template))
        #expect(isSuccessStreak(narrations[5].template))
        #expect(narrations[5].streak == 3)
        #expect(isSuccessStreak(narrations[6].template))
        #expect(narrations[6].streak == 4)
    }

    @Test("Narration tracks repeated letters and hint balance")
    func tracksRepeatedLettersAndHints() {
        let logs = [
            log(id: "1", action: "open_character", character: "A", count: 2, points: 2, balance: 12),
            log(id: "2", action: "open_character", character: "A", count: 1, points: 1, balance: 13),
            log(
                id: "3",
                action: "buy_hint",
                balance: 8,
                hintType: "version",
                hintVisibility: "public",
                hintCost: 5
            ),
            log(
                id: "4",
                action: "buy_hint",
                balance: 3,
                hintType: "white_chart",
                hintVisibility: "private",
                hintCost: 5,
                hintResult: true
            )
        ]

        let narrations = LetterGameLogNarrator.narrate(logs)

        #expect(isRepeated(narrations[1].template))
        #expect(isLowBalanceHint(narrations[2].template))
        #expect(narrations[2].hintCount == 1)
        #expect(narrations[2].balance == 8)
        #expect(isLowBalanceHint(narrations[3].template))
        #expect(narrations[3].hintCount == 2)
        #expect(narrations[3].hintResult == true)
    }

    @Test("Template selection is deterministic")
    func selectsTemplateDeterministically() {
        let logs = [log(id: "stable", action: "open_character", character: "Z", count: 5, points: 5)]

        #expect(LetterGameLogNarrator.narrate(logs) == LetterGameLogNarrator.narrate(logs))
        #expect(LetterGameLogNarrator.narrate(logs)[0].template == .openMany1)
    }

    private func log(
        id: String,
        action: String,
        character: String? = nil,
        count: Int? = nil,
        points: Int? = nil,
        correct: Bool? = nil,
        balance: Int? = nil,
        hintType: String? = nil,
        hintVisibility: String? = nil,
        hintCost: Int? = nil,
        hintResult: Bool? = nil
    ) -> LetterGameLogEntry {
        LetterGameLogEntry(
            id: id,
            message: "fallback",
            actorUserId: "user-1",
            actorName: "Mia",
            actionType: action,
            character: character,
            newlyRevealedCount: count,
            points: points,
            correct: correct,
            blind: false,
            balance: balance,
            hintType: hintType,
            hintVisibility: hintVisibility,
            hintCost: hintCost,
            hintResult: hintResult,
            songNumber: nil
        )
    }

    private func isIncorrect(_ template: LetterGameLogTemplate) -> Bool {
        [.guessIncorrect1, .guessIncorrect2].contains(template)
    }

    private func isIncorrectStreak(_ template: LetterGameLogTemplate) -> Bool {
        [.guessIncorrectStreak1, .guessIncorrectStreak2].contains(template)
    }

    private func isNoProgressStreak(_ template: LetterGameLogTemplate) -> Bool {
        [.openNoProgressStreak1, .openNoProgressStreak2].contains(template)
    }

    private func isRecovered(_ template: LetterGameLogTemplate) -> Bool {
        [.progressRecovered1, .progressRecovered2].contains(template)
    }

    private func isCorrect(_ template: LetterGameLogTemplate) -> Bool {
        [.guessCorrect1, .guessCorrect2].contains(template)
    }

    private func isSuccessStreak(_ template: LetterGameLogTemplate) -> Bool {
        [.openSuccessStreak1, .openSuccessStreak2].contains(template)
    }

    private func isRepeated(_ template: LetterGameLogTemplate) -> Bool {
        [.openRepeated1, .openRepeated2].contains(template)
    }

    private func isLowBalanceHint(_ template: LetterGameLogTemplate) -> Bool {
        [.hintLowScore1, .hintLowScore2].contains(template)
    }

}
