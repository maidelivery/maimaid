package org.rhythmeta.maimaid.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY createdAt")
    fun observeProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    fun observeActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    suspend fun activeProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles ORDER BY createdAt LIMIT 1")
    suspend fun firstProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles ORDER BY createdAt")
    suspend fun profiles(): List<UserProfileEntity>

    @Query("UPDATE user_profiles SET isActive = 0")
    suspend fun clearActiveProfile()

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)

    @Delete
    suspend fun delete(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAll()

    @Transaction
    suspend fun activate(profile: UserProfileEntity) {
        clearActiveProfile()
        upsert(profile.copy(isActive = true))
    }
}
