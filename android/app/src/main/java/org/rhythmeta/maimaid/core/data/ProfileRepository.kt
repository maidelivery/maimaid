package org.rhythmeta.maimaid.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.rhythmeta.maimaid.core.database.ProfileDao
import org.rhythmeta.maimaid.core.database.UserProfileEntity
import java.util.UUID

class ProfileRepository(
    private val profileDao: ProfileDao,
    private val defaultProfileName: String,
) {
    private val activeProfileMutex = Mutex()

    val activeProfile: Flow<UserProfileEntity?> = profileDao.observeActiveProfile()

    suspend fun ensureDefaultProfile() {
        ensureActiveProfile()
    }

    suspend fun ensureActiveProfile(): UserProfileEntity = activeProfileMutex.withLock {
        profileDao.activeProfile()?.let { return@withLock it }

        profileDao.firstProfile()?.let { firstProfile ->
            val activated = firstProfile.copy(isActive = true)
            profileDao.upsert(activated)
            return@withLock activated
        }

        val defaultProfile = UserProfileEntity(
            id = UUID.randomUUID().toString(),
            name = defaultProfileName,
            server = "jp",
            isActive = true,
            createdAt = System.currentTimeMillis(),
        )
        profileDao.upsert(defaultProfile)
        defaultProfile
    }
}
