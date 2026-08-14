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

    private fun profile(id: String) = UserProfileEntity(
        id = id,
        name = "Player",
        server = "cn",
        isActive = false,
        createdAt = 1_700_000_000_000,
    )
}
