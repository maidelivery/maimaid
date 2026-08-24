package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "song_collections", indices = [Index(value = ["sortIndex"]), Index(value = ["updatedAt"]), Index(value = ["clientUpdatedAt"])])
data class SongCollectionEntity(
    @androidx.room.PrimaryKey val id: String,
    val name: String,
    val sortIndex: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val clientUpdatedAt: Long? = null,
)

@Entity(
    tableName = "song_collection_items",
    primaryKeys = ["id"],
    indices = [Index(value = ["collectionId", "songId", "chartType", "difficulty"], unique = true), Index(value = ["collectionId", "position"]), Index(value = ["clientUpdatedAt"])],
)
data class SongCollectionItemEntity(
    val id: String,
    val collectionId: String,
    val songId: String,
    val chartType: String,
    val difficulty: String,
    val position: Int = 0,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val clientUpdatedAt: Long? = null,
)
