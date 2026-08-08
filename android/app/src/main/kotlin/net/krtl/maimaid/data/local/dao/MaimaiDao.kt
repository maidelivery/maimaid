@file:Suppress("SameReturnValue")

package net.krtl.maimaid.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow
import net.krtl.maimaid.data.local.entity.CommunityAliasCacheEntity
import net.krtl.maimaid.data.local.entity.MaimaiIconEntity
import net.krtl.maimaid.data.local.entity.PlayRecordEntity
import net.krtl.maimaid.data.local.entity.ScoreEntity
import net.krtl.maimaid.data.local.entity.SheetEntity
import net.krtl.maimaid.data.local.entity.SongEntity
import net.krtl.maimaid.data.local.entity.SyncConfigEntity
import net.krtl.maimaid.data.local.entity.UserProfileEntity
import net.krtl.maimaid.data.local.relation.SongWithSheets

@Dao
interface MaimaiDao {
    @Transaction
    @Query("SELECT * FROM songs ORDER BY sortOrder ASC, title ASC")
    fun observeSongsWithSheets(): Flow<List<SongWithSheets>>

    @Transaction
    @Query("SELECT * FROM songs WHERE songIdentifier = :songIdentifier")
    fun observeSongWithSheets(songIdentifier: String): Flow<SongWithSheets?>

    @Transaction
    @Query("SELECT * FROM songs ORDER BY sortOrder ASC, title ASC")
    suspend fun getSongsWithSheets(): List<SongWithSheets>

    @Query("SELECT COUNT(*) FROM songs")
    suspend fun getSongCount(): Int

    @Query("SELECT COUNT(*) FROM sheets")
    suspend fun getSheetCount(): Int

    @Query("SELECT COUNT(*) FROM maimai_icons")
    suspend fun getIconCount(): Int

    @Query("SELECT * FROM songs WHERE songIdentifier IN (:songIdentifiers)")
    suspend fun getSongsByIdentifiers(songIdentifiers: List<String>): List<SongEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSongs(songs: List<SongEntity>)

    @Query("UPDATE songs SET aliases = :aliases WHERE songIdentifier = :songIdentifier")
    suspend fun updateSongAliases(songIdentifier: String, aliases: List<String>)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE songIdentifier = :songIdentifier")
    suspend fun updateSongFavorite(songIdentifier: String, isFavorite: Boolean)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSheets(sheets: List<SheetEntity>)

    @Query("DELETE FROM songs")
    suspend fun clearSongs()

    @Query("DELETE FROM songs WHERE songIdentifier NOT IN (:songIdentifiers)")
    suspend fun deleteSongsNotIn(songIdentifiers: List<String>)

    @Query("DELETE FROM sheets WHERE songIdentifier = :songIdentifier AND sheetId NOT IN (:sheetIds)")
    suspend fun deleteSheetsNotIn(songIdentifier: String, sheetIds: List<String>)

    @Query("DELETE FROM sheets WHERE songIdentifier = :songIdentifier")
    suspend fun deleteSheetsForSong(songIdentifier: String)

    @Query("SELECT * FROM sheets WHERE sheetId = :sheetId LIMIT 1")
    suspend fun getSheet(sheetId: String): SheetEntity?

    @Query("SELECT * FROM sheets")
    suspend fun getSheets(): List<SheetEntity>

    @Query("SELECT * FROM scores WHERE userProfileId = :userProfileId")
    fun observeScores(userProfileId: String): Flow<List<ScoreEntity>>

    @Query("SELECT * FROM scores WHERE userProfileId = :userProfileId")
    suspend fun getScores(userProfileId: String): List<ScoreEntity>

    @Query("SELECT * FROM scores WHERE sheetId = :sheetId AND userProfileId = :userProfileId LIMIT 1")
    suspend fun getScore(sheetId: String, userProfileId: String): ScoreEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScore(score: ScoreEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertScores(scores: List<ScoreEntity>)

    @Query("DELETE FROM scores WHERE userProfileId = :userProfileId")
    suspend fun deleteScoresForUser(userProfileId: String)

    @Query("DELETE FROM scores WHERE sheetId NOT IN (:sheetIds)")
    suspend fun deleteScoresNotInSheets(sheetIds: List<String>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlayRecord(record: PlayRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPlayRecords(records: List<PlayRecordEntity>)

    @Query("SELECT * FROM play_records WHERE userProfileId = :userProfileId ORDER BY playDate DESC")
    fun observePlayRecords(userProfileId: String): Flow<List<PlayRecordEntity>>

    @Query("SELECT * FROM play_records WHERE userProfileId = :userProfileId ORDER BY playDate DESC")
    suspend fun getPlayRecords(userProfileId: String): List<PlayRecordEntity>

    @Query("DELETE FROM play_records WHERE userProfileId = :userProfileId")
    suspend fun deletePlayRecordsForUser(userProfileId: String)

    @Query("DELETE FROM play_records WHERE sheetId NOT IN (:sheetIds)")
    suspend fun deletePlayRecordsNotInSheets(sheetIds: List<String>)

    @Query("SELECT * FROM user_profiles ORDER BY createdAt ASC")
    fun observeProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY createdAt ASC")
    suspend fun getProfiles(): List<UserProfileEntity>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 LIMIT 1")
    fun observeActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles WHERE isActive = 1 LIMIT 1")
    suspend fun getActiveProfile(): UserProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfile(profile: UserProfileEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProfiles(profiles: List<UserProfileEntity>)

    @Query("UPDATE user_profiles SET isActive = 0")
    suspend fun clearActiveProfiles()

    @Query("UPDATE user_profiles SET isActive = 1 WHERE id = :profileId")
    suspend fun activateProfile(profileId: String)

    @Query("DELETE FROM user_profiles WHERE id = :profileId")
    suspend fun deleteProfile(profileId: String)

    @Query("SELECT * FROM sync_config WHERE id = 1 LIMIT 1")
    fun observeSyncConfig(): Flow<SyncConfigEntity?>

    @Query("SELECT * FROM sync_config WHERE id = 1 LIMIT 1")
    suspend fun getSyncConfig(): SyncConfigEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSyncConfig(config: SyncConfigEntity)

    @Query("SELECT * FROM maimai_icons ORDER BY id ASC")
    fun observeIcons(): Flow<List<MaimaiIconEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertIcons(icons: List<MaimaiIconEntity>)

    @Query(
        """
        SELECT * FROM community_alias_cache
        WHERE songIdentifier = :songIdentifier
        ORDER BY updatedAt DESC, aliasText COLLATE NOCASE ASC
        """
    )
    fun observeCommunityAliasCache(songIdentifier: String): Flow<List<CommunityAliasCacheEntity>>

    @Query("SELECT * FROM community_alias_cache")
    suspend fun getCommunityAliasCacheRows(): List<CommunityAliasCacheEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCommunityAliasCaches(rows: List<CommunityAliasCacheEntity>)

    @Query("DELETE FROM community_alias_cache")
    suspend fun clearCommunityAliasCaches()

    @Query("DELETE FROM community_alias_cache WHERE candidateId IN (:candidateIds)")
    suspend fun deleteCommunityAliasCachesByIds(candidateIds: List<String>)

    @Transaction
    suspend fun replaceProfileScoresAndRecords(
        profileId: String,
        scores: List<ScoreEntity>,
        records: List<PlayRecordEntity>
    ) {
        deleteScoresForUser(profileId)
        deletePlayRecordsForUser(profileId)
        if (scores.isNotEmpty()) {
            upsertScores(scores)
        }
        if (records.isNotEmpty()) {
            upsertPlayRecords(records)
        }
    }
}
