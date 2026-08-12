package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
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

    private fun profile() = UserProfileEntity(
        id = "00000000-0000-0000-0000-000000000001",
        name = "Player",
        server = "cn",
        isActive = true,
        createdAt = 1_700_000_000_000,
    )
}
