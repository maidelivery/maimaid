package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity

class ThirdPartyScoreSyncPolicyTest {
    @Test
    fun `sync is limited to active China profile scores on available charts`() {
        val profile = profile()
        val score = ScoreEntity(
            profileId = profile.id,
            sheetKey = "song|dx|master",
            achievement = 100.0,
            rank = "SSS",
            dxScore = 0,
            fc = null,
            fs = null,
            achievedAt = 0,
        )

        assertTrue(ThirdPartyScoreSyncPolicy.isEligible(profile, score, sheet()))
        assertFalse(ThirdPartyScoreSyncPolicy.isEligible(profile.copy(server = "jp"), score, sheet()))
        assertFalse(ThirdPartyScoreSyncPolicy.isEligible(profile.copy(isActive = false), score, sheet()))
        assertFalse(ThirdPartyScoreSyncPolicy.isEligible(profile, score.copy(profileId = "other"), sheet()))
        assertFalse(ThirdPartyScoreSyncPolicy.isEligible(profile, score, sheet(regionCn = false)))
    }

    @Test
    fun `difficulty indexes match provider contracts`() {
        assertEquals(0, ThirdPartyScoreSyncPolicy.difficultyIndex("basic"))
        assertEquals(1, ThirdPartyScoreSyncPolicy.difficultyIndex("advanced"))
        assertEquals(2, ThirdPartyScoreSyncPolicy.difficultyIndex("expert"))
        assertEquals(3, ThirdPartyScoreSyncPolicy.difficultyIndex("master"))
        assertEquals(4, ThirdPartyScoreSyncPolicy.difficultyIndex("remaster"))
        assertEquals(3, ThirdPartyScoreSyncPolicy.difficultyIndex("utage"))
    }

    @Test
    fun `LXNS receives its base song id for DX charts`() {
        assertEquals(1_422, ThirdPartyScoreSyncPolicy.lxnsSongId(11_422, "dx"))
        assertEquals(1_422, ThirdPartyScoreSyncPolicy.lxnsSongId(1_422, "standard"))
        assertEquals(100_123, ThirdPartyScoreSyncPolicy.lxnsSongId(100_123, "utage"))
    }

    private fun profile() = UserProfileEntity(
        id = "profile",
        name = "Player",
        server = "cn",
        isActive = true,
        createdAt = 0,
    )

    private fun sheet(regionCn: Boolean = true) = SheetEntity(
        sheetKey = "song|dx|master",
        songIdentifier = "song",
        type = "dx",
        difficulty = "master",
        version = null,
        level = "14",
        levelValue = 14.0,
        internalLevel = null,
        internalLevelValue = null,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = true,
        regionIntl = true,
        regionUsa = true,
        regionCn = regionCn,
    )
}
