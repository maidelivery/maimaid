package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity

class BackendSyncStateStoreTest {
    @Test
    fun profileFingerprintTracksSyncedMetadata() {
        val profile = profile()

        assertEquals(
            BackendSyncStateStore.profileFingerprint(profile),
            BackendSyncStateStore.profileFingerprint(profile.copy(avatarPath = "/local/cache/avatar.png")),
        )
        assertNotEquals(
            BackendSyncStateStore.profileFingerprint(profile),
            BackendSyncStateStore.profileFingerprint(profile.copy(name = "Second device edit")),
        )
    }

    @Test
    fun dataFingerprintIsStableAcrossCollectionOrder() {
        val scores = listOf(
            ScoreEntity("profile", "sheet-b", 99.0, "sss", 100, null, null, 2),
            ScoreEntity("profile", "sheet-a", 98.0, "ss", 90, "fc", null, 1),
        )
        val records = listOf(
            PlayRecordEntity("record-2", "profile", "sheet-b", 99.0, "sss", 100, null, null, 4),
            PlayRecordEntity("record-1", "profile", "sheet-a", 98.0, "ss", 90, "fc", null, 3),
        )

        assertEquals(
            BackendSyncStateStore.dataFingerprint(scores, records),
            BackendSyncStateStore.dataFingerprint(scores.reversed(), records.reversed()),
        )
        assertNotEquals(
            BackendSyncStateStore.dataFingerprint(scores, records),
            BackendSyncStateStore.dataFingerprint(scores, records.dropLast(1)),
        )
    }

    private fun profile() = UserProfileEntity(
        id = "00000000-0000-0000-0000-000000000001",
        name = "Player",
        server = "cn",
        isActive = true,
        createdAt = 1_700_000_000_000,
    )
}
