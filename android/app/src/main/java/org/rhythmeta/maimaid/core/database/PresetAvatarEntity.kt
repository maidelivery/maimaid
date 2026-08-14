package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "preset_avatars")
data class PresetAvatarEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val genre: String,
)
