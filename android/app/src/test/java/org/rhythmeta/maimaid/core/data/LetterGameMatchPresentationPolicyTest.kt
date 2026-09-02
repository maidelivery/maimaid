package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LetterGameMatchPresentationPolicyTest {
    @Test
    fun `fresh clients ignore historical results but accept active matches`() {
        assertFalse(LetterGameMatchPresentationPolicy.shouldAccept("finished", "match-1", null))
        assertTrue(LetterGameMatchPresentationPolicy.shouldAccept("active", "match-2", null))
    }

    @Test
    fun `tracked matches can transition to results`() {
        assertTrue(LetterGameMatchPresentationPolicy.shouldAccept("finished", "match-1", "match-1"))
        assertFalse(LetterGameMatchPresentationPolicy.shouldAccept("abandoned", "match-1", "match-2"))
    }

    @Test
    fun `gameplay players follow accepted room membership`() {
        val players = listOf(
            player("a", 0),
            player("b", 1),
            player("c", 2),
        )
        val members = listOf(
            member("b", "accepted", 1),
            member("c", "pending", 2),
        )

        val visible = LetterGameMatchPresentationPolicy.visiblePlayers(players, members)

        assertEquals(listOf("b"), visible.map(LetterGameMatchPlayer::userId))
    }

    private fun player(userId: String, turnOrder: Int) = LetterGameMatchPlayer(
        userId = userId,
        score = turnOrder,
        turnOrder = turnOrder,
        status = "active",
    )

    private fun member(userId: String, status: String, seatOrder: Int) = LetterGameRoomMember(
        userId = userId,
        status = status,
        seatOrder = seatOrder,
    )
}
