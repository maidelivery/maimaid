package org.rhythmeta.maimaid.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface SongCollectionDao {
    @Query("SELECT * FROM song_collections WHERE deletedAt IS NULL ORDER BY sortIndex, createdAt")
    fun observeCollections(): Flow<List<SongCollectionEntity>>

    @Query("SELECT * FROM song_collection_items WHERE deletedAt IS NULL ORDER BY collectionId, position, createdAt")
    fun observeItems(): Flow<List<SongCollectionItemEntity>>

    @Query("SELECT * FROM song_collection_items WHERE collectionId = :collectionId AND deletedAt IS NULL ORDER BY position, createdAt")
    fun observeItems(collectionId: String): Flow<List<SongCollectionItemEntity>>

    @Query("SELECT * FROM song_collections")
    suspend fun collectionsIncludingDeleted(): List<SongCollectionEntity>

    @Query("SELECT * FROM song_collection_items")
    suspend fun itemsIncludingDeleted(): List<SongCollectionItemEntity>

    @Query("SELECT * FROM song_collection_items WHERE id = :id LIMIT 1")
    suspend fun item(id: String): SongCollectionItemEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM song_collections)")
    suspend fun hasAnyCollections(): Boolean

    @Query("SELECT EXISTS(SELECT 1 FROM song_collection_items)")
    suspend fun hasAnyItems(): Boolean

    @Query("DELETE FROM song_collection_items")
    suspend fun deleteAllItems()

    @Query("DELETE FROM song_collections")
    suspend fun deleteAllCollections()

    @Upsert suspend fun upsertCollection(value: SongCollectionEntity)
    @Upsert suspend fun upsertItem(value: SongCollectionItemEntity)
}
