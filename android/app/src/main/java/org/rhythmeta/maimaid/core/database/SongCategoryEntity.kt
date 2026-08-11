package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "song_categories")
data class SongCategoryEntity(
    @PrimaryKey val name: String,
    val sortOrder: Int,
)
