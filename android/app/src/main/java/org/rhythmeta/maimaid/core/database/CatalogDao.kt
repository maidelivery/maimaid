package org.rhythmeta.maimaid.core.database

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface CatalogDao {
    @Query("SELECT COUNT(*) FROM songs WHERE isRemoved = 0")
    fun observeSongCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM sheets WHERE isRemoved = 0")
    fun observeSheetCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM songs WHERE isRemoved = 0")
    suspend fun songCount(): Int

    @Query("SELECT * FROM songs WHERE isRemoved = 0 ORDER BY sortOrder LIMIT :limit")
    fun observeFeaturedSongs(limit: Int): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isRemoved = 0 ORDER BY sortOrder")
    fun observeSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM sheets WHERE isRemoved = 0")
    fun observeSheets(): Flow<List<SheetEntity>>

    @Query("SELECT * FROM sheets WHERE songIdentifier = :songIdentifier AND isRemoved = 0")
    fun observeSheetsForSong(songIdentifier: String): Flow<List<SheetEntity>>

    @Query("SELECT alias FROM song_aliases WHERE songIdentifier = :songIdentifier ORDER BY alias COLLATE NOCASE")
    fun observeAliasesForSong(songIdentifier: String): Flow<List<String>>

    @Query("SELECT * FROM song_aliases ORDER BY alias COLLATE NOCASE")
    fun observeAliases(): Flow<List<SongAliasEntity>>

    @Query("SELECT * FROM sheets WHERE sheetKey = :sheetKey LIMIT 1")
    suspend fun sheet(sheetKey: String): SheetEntity?

    @Query("SELECT * FROM game_versions ORDER BY sortOrder")
    fun observeVersions(): Flow<List<GameVersionEntity>>

    @Query("SELECT * FROM song_categories ORDER BY sortOrder")
    fun observeCategories(): Flow<List<SongCategoryEntity>>

    @Query("SELECT songIdentifier FROM songs WHERE isFavorite = 1")
    suspend fun favoriteSongIds(): List<String>

    @Query("SELECT imageName FROM songs WHERE isRemoved = 0 AND imageName != ''")
    suspend fun imageNames(): List<String>

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE songIdentifier = :songIdentifier")
    suspend fun setFavorite(songIdentifier: String, isFavorite: Boolean)

    @Query("UPDATE songs SET isRemoved = 1")
    suspend fun markAllSongsRemoved()

    @Query("UPDATE sheets SET isRemoved = 1")
    suspend fun markAllSheetsRemoved()

    @Query("DELETE FROM song_aliases")
    suspend fun deleteAliases()

    @Upsert
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Upsert
    suspend fun upsertSheets(sheets: List<SheetEntity>)

    @Upsert
    suspend fun upsertCategories(categories: List<SongCategoryEntity>)

    @Upsert
    suspend fun upsertVersions(versions: List<GameVersionEntity>)

    @Upsert
    suspend fun upsertAliases(aliases: List<SongAliasEntity>)
}
