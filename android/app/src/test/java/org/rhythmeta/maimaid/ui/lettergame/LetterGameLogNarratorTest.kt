package org.rhythmeta.maimaid.ui.lettergame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.data.LetterGameLogEntry

class LetterGameLogNarratorTest {
    @Test
    fun `replays progress and streak context`() {
        val logs = listOf(
            log("open-1", actionType = "open_character", character = "A"),
            log("open-2", actionType = "open_character", character = "Z"),
            log("open-3", actionType = "open_character", character = "Q"),
            log("open-4", actionType = "open_character", character = "A", newlyRevealedCount = 2, points = 2),
        )

        val narration = LetterGameLogNarrator.narrate(logs)

        assertTrue(narration[0].template in setOf(LetterGameLogTemplate.OPEN_NO_PROGRESS_1, LetterGameLogTemplate.OPEN_NO_PROGRESS_2))
        assertTrue(narration[2].template in setOf(LetterGameLogTemplate.OPEN_NO_PROGRESS_STREAK_1, LetterGameLogTemplate.OPEN_NO_PROGRESS_STREAK_2))
        assertTrue(narration[3].template in setOf(LetterGameLogTemplate.PROGRESS_RECOVERED_1, LetterGameLogTemplate.PROGRESS_RECOVERED_2))
        assertEquals(2, narration[3].points)
    }

    @Test
    fun `classifies guesses and hint visibility`() {
        val logs = listOf(
            log("blind", actionType = "guess_song", correct = true, blind = true, points = 15),
            log("wrong-1", actionType = "guess_song", correct = false),
            log("wrong-2", actionType = "guess_song", correct = false),
            log("score", actionType = "open_character", character = "B", newlyRevealedCount = 5, points = 5),
            log("private-hint", actionType = "buy_hint", hintType = "version", hintVisibility = "private", hintCost = 2, points = 2, balance = 22),
            log("low-hint", actionType = "buy_hint", hintType = "constant", hintVisibility = "public", hintCost = 10, points = 10),
        )

        val narration = LetterGameLogNarrator.narrate(logs)

        assertTrue(narration[0].template in setOf(LetterGameLogTemplate.GUESS_BLIND_1, LetterGameLogTemplate.GUESS_BLIND_2))
        assertTrue(narration[2].template in setOf(LetterGameLogTemplate.GUESS_INCORRECT_STREAK_1, LetterGameLogTemplate.GUESS_INCORRECT_STREAK_2))
        assertTrue(narration[4].template in setOf(LetterGameLogTemplate.HINT_PRIVATE_1, LetterGameLogTemplate.HINT_PRIVATE_2))
        assertTrue(narration[5].template in setOf(LetterGameLogTemplate.HINT_LOW_SCORE_1, LetterGameLogTemplate.HINT_LOW_SCORE_2))
        assertEquals(1, narration[4].hintCount)
        assertEquals(2, narration[5].hintCount)
        assertEquals(22, narration[4].balance)
    }

    @Test
    fun `uses stable variants and safe fallback`() {
        val first = log("stable", actionType = "open_character", character = "X", newlyRevealedCount = 1, points = 1)
        val firstNarration = LetterGameLogNarrator.narrate(listOf(first)).single()
        val repeatedNarration = LetterGameLogNarrator.narrate(listOf(first)).single()
        val variants = LetterGameLogNarrator.narrate((1..12).map { first.copy(id = "variant-$it") }).map { it.template }.toSet()
        val fallback = LetterGameLogNarrator.narrate(listOf(log("unknown", actionType = "future_action", message = "server message"))).single()

        assertEquals(firstNarration, repeatedNarration)
        assertTrue(variants.size > 1)
        assertEquals(LetterGameLogTemplate.FALLBACK, fallback.template)
        assertEquals("server message", fallback.fallbackMessage)
    }

    @Test
    fun `falls back safely when player identity is missing`() {
        val narration = LetterGameLogNarrator.narrate(
            listOf(
                log(
                    id = "missing-player",
                    actionType = "open_character",
                    character = "A",
                    actorUserId = null,
                    actorName = null,
                ),
            ),
        ).single()

        assertEquals("Player", narration.actor)
        assertTrue(narration.template in setOf(LetterGameLogTemplate.OPEN_NO_PROGRESS_1, LetterGameLogTemplate.OPEN_NO_PROGRESS_2))
    }

    private fun log(
        id: String,
        actionType: String,
        message: String = "",
        character: String? = null,
        newlyRevealedCount: Int? = null,
        points: Int? = null,
        correct: Boolean? = null,
        blind: Boolean? = null,
        hintType: String? = null,
        hintVisibility: String? = null,
        hintCost: Int? = null,
        balance: Int? = null,
        actorUserId: String? = "user-1",
        actorName: String? = "Mia",
    ) = LetterGameLogEntry(
        id = id,
        message = message,
        actorUserId = actorUserId,
        actorName = actorName,
        actionType = actionType,
        character = character,
        newlyRevealedCount = newlyRevealedCount,
        points = points,
        correct = correct,
        blind = blind,
        hintType = hintType,
        hintVisibility = hintVisibility,
        hintCost = hintCost,
        balance = balance,
    )
}
