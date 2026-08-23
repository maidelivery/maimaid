package org.rhythmeta.maimaid.core.database

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ScoreDao {
    @Query("""
        SELECT scores.profileId AS profileId, scores.sheetKey AS sheetKey,
               scores.achievement AS achievement, scores.rank AS resultRank,
               scores.dxScore AS dxScore, scores.fc AS fc, scores.fs AS fs,
               songs.songIdentifier AS songIdentifier, songs.title AS title,
               songs.imageName AS imageName, songs.category AS category,
               songs.version AS songVersion, sheets.type AS type,
               sheets.difficulty AS difficulty, sheets.version AS sheetVersion,
               sheets.internalLevelValue AS internalLevelValue,
               sheets.providerSongId AS songId,
               COALESCE(sheets.total, 0) * 3 AS maxDxScore,
               sheets.regionJp AS regionJp, sheets.regionIntl AS regionIntl,
               sheets.regionCn AS regionCn
        FROM scores
        INNER JOIN sheets ON sheets.sheetKey = scores.sheetKey
        INNER JOIN songs ON songs.songIdentifier = sheets.songIdentifier
        WHERE scores.profileId = :profileId AND sheets.isRemoved = 0 AND songs.isRemoved = 0
    """)
    fun observeBest50Rows(profileId: String): Flow<List<Best50Row>>

    @Query("SELECT COUNT(*) FROM scores WHERE profileId = :profileId")
    fun observeScoreCount(profileId: String): Flow<Int>

    @Query("SELECT * FROM scores WHERE profileId = :profileId")
    fun observeScores(profileId: String): Flow<List<ScoreEntity>>

    @Query("SELECT * FROM scores WHERE profileId = :profileId")
    suspend fun scores(profileId: String): List<ScoreEntity>

    @Query("SELECT * FROM play_records WHERE profileId = :profileId ORDER BY playedAt DESC")
    suspend fun playRecords(profileId: String): List<PlayRecordEntity>

    @Query("SELECT * FROM scores WHERE profileId = :profileId AND sheetKey = :sheetKey LIMIT 1")
    suspend fun score(profileId: String, sheetKey: String): ScoreEntity?

    @Query("SELECT * FROM scores WHERE profileId = :profileId AND sheetKey IN (SELECT sheetKey FROM sheets WHERE songIdentifier = :songIdentifier)")
    fun observeScoresForSong(profileId: String, songIdentifier: String): Flow<List<ScoreEntity>>

    @Query("SELECT * FROM scores WHERE profileId = :profileId AND sheetKey IN (SELECT sheetKey FROM sheets WHERE songIdentifier = :songIdentifier)")
    suspend fun scoresForSong(profileId: String, songIdentifier: String): List<ScoreEntity>

    @Query("SELECT * FROM play_records WHERE profileId = :profileId AND sheetKey IN (SELECT sheetKey FROM sheets WHERE songIdentifier = :songIdentifier) ORDER BY playedAt DESC")
    fun observePlayRecordsForSong(profileId: String, songIdentifier: String): Flow<List<PlayRecordEntity>>

    @Query("SELECT * FROM play_records WHERE profileId = :profileId AND sheetKey = :sheetKey ORDER BY playedAt DESC")
    suspend fun playRecords(profileId: String, sheetKey: String): List<PlayRecordEntity>

    @Query("SELECT * FROM play_records WHERE id = :recordId LIMIT 1")
    suspend fun playRecord(recordId: String): PlayRecordEntity?

    @Upsert
    suspend fun upsertScore(score: ScoreEntity)

    @Upsert
    suspend fun upsertPlayRecord(record: PlayRecordEntity)

    @Delete
    suspend fun deletePlayRecord(record: PlayRecordEntity)

    @Delete
    suspend fun deletePlayRecords(records: List<PlayRecordEntity>)

    @Query("DELETE FROM scores WHERE profileId = :profileId AND sheetKey = :sheetKey")
    suspend fun deleteScore(profileId: String, sheetKey: String)

    @Query("DELETE FROM scores WHERE profileId = :profileId")
    suspend fun deleteScores(profileId: String)

    @Query("DELETE FROM play_records WHERE profileId = :profileId")
    suspend fun deletePlayRecords(profileId: String)
}
