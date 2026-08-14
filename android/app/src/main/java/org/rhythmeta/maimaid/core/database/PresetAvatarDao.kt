package org.rhythmeta.maimaid.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetAvatarDao {
    @Query("SELECT * FROM preset_avatars ORDER BY id")
    suspend fun avatars(): List<PresetAvatarEntity>

    @Query("SELECT * FROM preset_avatars ORDER BY id")
    fun observeAvatars(): Flow<List<PresetAvatarEntity>>

    @Query("DELETE FROM preset_avatars")
    suspend fun deleteAll()

    @Upsert
    suspend fun upsertAll(avatars: List<PresetAvatarEntity>)

}
