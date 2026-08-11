package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val songIdentifier: String,
    val category: String,
    val title: String,
    val artist: String,
    val imageName: String,
    val version: String?,
    val releaseDate: String?,
    val sortOrder: Int,
    val bpm: Double?,
    val isNew: Boolean,
    val isLocked: Boolean,
    val comment: String?,
    val isFavorite: Boolean = false,
    val isRemoved: Boolean = false,
)
