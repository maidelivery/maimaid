package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity

class BackendSyncCoordinatorTest {
    @Test
    fun localProfilesAbsentFromCloudReturnsOnlyProfilesMissingFromTheSnapshot() {
        val cloudProfile = profile(id = "00000000-0000-0000-0000-000000000001")
        val localOnlyProfile = profile(id = "00000000-0000-0000-0000-000000000002")

        val localOnlyProfileIds = localProfilesAbsentFromCloud(
            localProfiles = listOf(cloudProfile, localOnlyProfile),
            cloudProfileIds = setOf(cloudProfile.id),
        ).map(UserProfileEntity::id)

        assertEquals(listOf(localOnlyProfile.id), localOnlyProfileIds)
    }

    @Test
    fun legacyDivingFishPlayRecordsReturnsOnlyLargeSameMillisecondBatches() {
        val legacy = List(100) { index -> record(id = "legacy-$index", playedAt = 1_700_000_000_000) }
        val realRecords = List(4) { index -> record(id = "real-$index", playedAt = 1_700_000_001_000) }

        assertEquals(legacy, legacyDivingFishPlayRecords(legacy + realRecords))
    }

    private fun profile(id: String) = UserProfileEntity(
        id = id,
        name = "Player",
        server = "cn",
        isActive = false,
        createdAt = 1_700_000_000_000,
    )

    private fun record(id: String, playedAt: Long) = PlayRecordEntity(
        id = id,
        profileId = "profile",
        sheetKey = "song|std|master",
        achievement = 100.0,
        rank = "ss",
        dxScore = 1_000,
        fc = null,
        fs = null,
        playedAt = playedAt,
    )
}
