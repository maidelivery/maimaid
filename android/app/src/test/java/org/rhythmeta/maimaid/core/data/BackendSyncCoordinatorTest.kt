package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test
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
    fun importedSyncBatchesStayWithinTheConfiguredSize() {
        val scores = List(2_500) { index -> score(index) }
        val records = List(4_501) { index -> record(index) }

        val batches = importedSyncBatches(scores, records, batchSize = 2_000)

        assertEquals(listOf(2_000, 500, 0), batches.map { it.scores.size })
        assertEquals(listOf(2_000, 2_000, 501), batches.map { it.records.size })
    }

    private fun profile(id: String) = UserProfileEntity(
        id = id,
        name = "Player",
        server = "cn",
        isActive = false,
        createdAt = 1_700_000_000_000,
    )

    private fun score(index: Int) = BackendScoreEntry(
        songIdentifier = "song-$index",
        type = "dx",
        difficulty = "master",
        achievements = 100.0,
        rank = "sss",
        dxScore = 1_000,
        achievedAt = "2026-08-20T10:00:00Z",
    )

    private fun record(index: Int) = BackendPlayRecordEntry(
        songIdentifier = "song-$index",
        type = "dx",
        difficulty = "master",
        achievements = 100.0,
        rank = "sss",
        dxScore = 1_000,
        playTime = "2026-08-20T10:00:00Z",
    )
}
