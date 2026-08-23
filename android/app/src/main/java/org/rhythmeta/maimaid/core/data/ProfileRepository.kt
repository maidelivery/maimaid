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
    private val onProfileChanged: () -> Unit = {},
) {
    private val activeProfileMutex = Mutex()

    val activeProfile: Flow<UserProfileEntity?> = profileDao.observeActiveProfile()
    val profiles: Flow<List<UserProfileEntity>> = profileDao.observeProfiles()

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

    suspend fun save(profile: UserProfileEntity) {
        profileDao.upsert(
            profile.copy(
                name = profile.name.trim(),
                plate = profile.plate?.trim()?.takeIf(String::isNotEmpty),
                dfUsername = profile.dfUsername.trim(),
                b35Count = profile.b35Count.coerceAtLeast(1),
                b15Count = profile.b15Count.coerceAtLeast(1),
            ),
        )
        onProfileChanged()
    }

    suspend fun create(
        name: String,
        server: String,
        avatarPath: String?,
        dfUsername: String,
        plate: String,
    ): UserProfileEntity {
        val isFirstProfile = profileDao.firstProfile() == null
        val profile = UserProfileEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            server = server,
            avatarPath = avatarPath,
            isActive = isFirstProfile,
            createdAt = System.currentTimeMillis(),
            dfUsername = dfUsername.trim(),
            plate = plate.trim().takeIf(String::isNotEmpty),
        )
        profileDao.upsert(profile)
        onProfileChanged()
        return profile
    }

    suspend fun activate(profile: UserProfileEntity) {
        activeProfileMutex.withLock {
            profileDao.activate(profile)
        }
        onProfileChanged()
    }

    suspend fun delete(profile: UserProfileEntity): Boolean {
        if (profile.isActive) return false
        profileDao.delete(profile)
        return true
    }

    suspend fun updateBestCapacity(b35Count: Int, b15Count: Int) {
        val profile = profileDao.activeProfile() ?: return
        profileDao.upsert(
            profile.copy(
                b35Count = b35Count.coerceAtLeast(1),
                b15Count = b15Count.coerceAtLeast(1),
            ),
        )
        onProfileChanged()
    }
}
