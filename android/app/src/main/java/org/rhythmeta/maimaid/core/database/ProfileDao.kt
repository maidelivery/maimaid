package org.rhythmeta.maimaid.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProfileDao {
    @Query("SELECT * FROM user_profiles WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    fun observeActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 ORDER BY createdAt LIMIT 1")
    suspend fun activeProfile(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles ORDER BY createdAt LIMIT 1")
    suspend fun firstProfile(): UserProfileEntity?

    @Upsert
    suspend fun upsert(profile: UserProfileEntity)
}
