package org.rhythmeta.maimaid.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.data.Best50State
import org.rhythmeta.maimaid.core.database.UserProfileEntity
import org.rhythmeta.maimaid.core.data.RatingUtils

class WidgetSnapshotBuilderTest {
    @Test
    fun buildsProfileScopedSummaryAndAllBestScores() {
        val profile = UserProfileEntity(
            id = "profile-1",
            name = "Player",
            server = "jp",
            isActive = true,
            createdAt = 1L,
            playerRating = 1_200,
        )
        val old = score("Old", 700)
        val recent = score("Recent", 900, difficulty = "MASTER")
        val highest = score("Highest", 1_500)
        val fourth = score("Fourth", 100)
        val state = Best50State(
            b35 = listOf(old, highest, fourth),
            b15 = listOf(recent),
            total = 3_200,
            isEmpty = false,
        )

        val snapshot = WidgetSnapshotBuilder.build(profile, state, updatedAt = 42L)

        assertEquals("profile-1", snapshot.profileId)
        assertEquals(3_200, snapshot.displayRating)
        assertEquals(2_300, snapshot.b35Rating)
        assertEquals(900, snapshot.b15Rating)
        assertEquals(4, snapshot.best50Count)
        assertEquals(listOf("Highest", "Recent", "Old", "Fourth"), snapshot.topScores.map { it.title })
        assertEquals(42L, snapshot.updatedAt)
        assertEquals(WidgetSnapshot.Status.Ready, snapshot.status)
    }

    @Test
    fun supportsEmptyProfileAndScores() {
        val noProfile = WidgetSnapshotBuilder.build(null, Best50State(), 10L)
        assertEquals(WidgetSnapshot.Status.NoProfile, noProfile.status)
        assertTrue(noProfile.profileId == null)

        val profile = UserProfileEntity("id", "Player", "cn", isActive = true, createdAt = 1L)
        val noScores = WidgetSnapshotBuilder.build(profile, Best50State(), 10L)
        assertEquals(WidgetSnapshot.Status.NoScores, noScores.status)
        assertEquals(0, noScores.best50Count)
    }

    private fun score(
        title: String,
        rating: Int,
        difficulty: String = "EXPERT",
    ) = RatingUtils.Entry(
        sheetKey = title,
        songIdentifier = title,
        songId = 1,
        title = title,
        imageName = "",
        achievement = 100.0,
        rating = rating,
        level = 13.0,
        difficulty = difficulty,
        type = "standard",
        dxScore = 0,
        maxDxScore = 0,
        fc = null,
        fs = null,
        isNew = false,
    )
}
