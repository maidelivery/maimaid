package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "game_versions")
data class GameVersionEntity(
    @PrimaryKey val name: String,
    val abbreviation: String,
    val releaseDate: String?,
    val sortOrder: Int,
)
