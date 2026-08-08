package net.krtl.maimaid.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.runTest
import net.krtl.maimaid.data.local.dao.MaimaiDao
import net.krtl.maimaid.data.local.entity.CommunityAliasCacheEntity
import net.krtl.maimaid.data.local.entity.MaimaiIconEntity
import net.krtl.maimaid.data.local.entity.PlayRecordEntity
import net.krtl.maimaid.data.local.entity.ScoreEntity
import net.krtl.maimaid.data.local.entity.SheetEntity
import net.krtl.maimaid.data.local.entity.SongEntity
import net.krtl.maimaid.data.local.entity.SyncConfigEntity
import net.krtl.maimaid.data.local.entity.UserProfileEntity
import net.krtl.maimaid.data.local.relation.SongWithSheets
import org.junit.Test

class StaticDataCleanupTest {
    @Test
    fun cleanupRemoteSheetsAndRelatedData_removesObsoleteSheetsScoresAndPlayRecords() = runTest {
        val dao = CleanupDao().apply {
            deletedSheetsBySong += "song-1" to mutableListOf()
            scores += ScoreEntity("profile::sheet-keep", "sheet-keep", "profile", 100.0, "SSS", 1L, 0, null, null)
            scores += ScoreEntity("profile::sheet-drop", "sheet-drop", "profile", 99.0, "SS+", 2L, 0, null, null)
            playRecords += PlayRecordEntity("keep-play", "sheet-keep", "profile", 100.0, "SSS", 1L, 0, null, null)
            playRecords += PlayRecordEntity("drop-play", "sheet-drop", "profile", 99.0, "SS+", 2L, 0, null, null)
        }

        cleanupRemoteSheetsAndRelatedData(
            dao = dao,
            songs = listOf(
                SongEntity(
                    songIdentifier = "song-1",
                    category = "POPS",
                    title = "Song 1",
                    artist = "Artist",
                    imageName = "song-1.png",
                    version = "PRiSM",
                    releaseDate = null,
                    sortOrder = 0,
                    bpm = null,
                    isNew = false,
                    isLocked = false,
                    comment = null,
                    searchKeywords = null,
                    aliases = emptyList(),
                    songId = 1,
                    isFavorite = false
                )
            ),
            sheets = listOf(
                SheetEntity(
                    sheetId = "sheet-keep",
                    songIdentifier = "song-1",
                    type = "dx",
                    difficulty = "master",
                    level = "14+",
                    levelValue = 14.7,
                    internalLevel = "14.7",
                    internalLevelValue = 14.7,
                    noteDesigner = null,
                    tap = null,
                    hold = null,
                    slide = null,
                    touch = null,
                    breakCount = null,
                    total = null,
                    regionJp = true,
                    regionIntl = true,
                    regionUsa = false,
                    regionCn = false,
                    songId = 1
                )
            )
        )

        assertThat(dao.deletedSheetsBySong["song-1"]).containsExactly("sheet-keep")
        assertThat(dao.scores.map { it.sheetId }).containsExactly("sheet-keep")
        assertThat(dao.playRecords.map { it.sheetId }).containsExactly("sheet-keep")
    }
}

private class CleanupDao : MaimaiDao {
    val deletedSheetsBySong = mutableMapOf<String, MutableList<String>>()
    val scores = mutableListOf<ScoreEntity>()
    val playRecords = mutableListOf<PlayRecordEntity>()

    override fun observeSongsWithSheets(): Flow<List<SongWithSheets>> = emptyFlow()

    override fun observeSongWithSheets(songIdentifier: String): Flow<SongWithSheets?> = emptyFlow()

    override suspend fun getSongsWithSheets(): List<SongWithSheets> = emptyList()

    override suspend fun getSongCount(): Int = 0

    override suspend fun getSheetCount(): Int = 0

    override suspend fun getIconCount(): Int = 0

    override suspend fun getSongsByIdentifiers(songIdentifiers: List<String>): List<SongEntity> = emptyList()

    override suspend fun upsertSongs(songs: List<SongEntity>) = Unit

    override suspend fun updateSongAliases(songIdentifier: String, aliases: List<String>) = Unit

    override suspend fun updateSongFavorite(songIdentifier: String, isFavorite: Boolean) = Unit

    override suspend fun upsertSheets(sheets: List<SheetEntity>) = Unit

    override suspend fun clearSongs() = Unit

    override suspend fun deleteSongsNotIn(songIdentifiers: List<String>) = Unit

    override suspend fun deleteSheetsNotIn(songIdentifier: String, sheetIds: List<String>) {
        deletedSheetsBySong.getOrPut(songIdentifier) { mutableListOf() }.apply {
            clear()
            addAll(sheetIds)
        }
    }

    override suspend fun deleteSheetsForSong(songIdentifier: String) {
        deletedSheetsBySong[songIdentifier] = mutableListOf()
    }

    override suspend fun getSheet(sheetId: String): SheetEntity? = null

    override suspend fun getSheets(): List<SheetEntity> = emptyList()

    override fun observeScores(userProfileId: String): Flow<List<ScoreEntity>> = emptyFlow()

    override suspend fun getScores(userProfileId: String): List<ScoreEntity> = scores.filter { it.userProfileId == userProfileId }

    override suspend fun getScore(sheetId: String, userProfileId: String): ScoreEntity? = null

    override suspend fun upsertScore(score: ScoreEntity) = Unit

    override suspend fun upsertScores(scores: List<ScoreEntity>) = Unit

    override suspend fun deleteScoresForUser(userProfileId: String) = Unit

    override suspend fun deleteScoresNotInSheets(sheetIds: List<String>) {
        scores.removeAll { it.sheetId !in sheetIds }
    }

    override suspend fun insertPlayRecord(record: PlayRecordEntity) = Unit

    override suspend fun upsertPlayRecords(records: List<PlayRecordEntity>) = Unit

    override fun observePlayRecords(userProfileId: String): Flow<List<PlayRecordEntity>> = emptyFlow()

    override suspend fun getPlayRecords(userProfileId: String): List<PlayRecordEntity> =
        playRecords.filter { it.userProfileId == userProfileId }

    override suspend fun deletePlayRecordsForUser(userProfileId: String) = Unit

    override suspend fun deletePlayRecordsNotInSheets(sheetIds: List<String>) {
        playRecords.removeAll { it.sheetId !in sheetIds }
    }

    override fun observeProfiles(): Flow<List<UserProfileEntity>> = emptyFlow()

    override suspend fun getProfiles(): List<UserProfileEntity> = emptyList()

    override fun observeActiveProfile(): Flow<UserProfileEntity?> = emptyFlow()

    override suspend fun getActiveProfile(): UserProfileEntity? = null

    override suspend fun upsertProfile(profile: UserProfileEntity) = Unit

    override suspend fun upsertProfiles(profiles: List<UserProfileEntity>) = Unit

    override suspend fun clearActiveProfiles() = Unit

    override suspend fun activateProfile(profileId: String) = Unit

    override suspend fun deleteProfile(profileId: String) = Unit

    override fun observeSyncConfig(): Flow<SyncConfigEntity?> = emptyFlow()

    override suspend fun getSyncConfig(): SyncConfigEntity? = null

    override suspend fun upsertSyncConfig(config: SyncConfigEntity) = Unit

    override fun observeIcons(): Flow<List<MaimaiIconEntity>> = emptyFlow()

    override suspend fun upsertIcons(icons: List<MaimaiIconEntity>) = Unit

    override fun observeCommunityAliasCache(songIdentifier: String): Flow<List<CommunityAliasCacheEntity>> = emptyFlow()

    override suspend fun getCommunityAliasCacheRows(): List<CommunityAliasCacheEntity> = emptyList()

    override suspend fun upsertCommunityAliasCaches(rows: List<CommunityAliasCacheEntity>) = Unit

    override suspend fun clearCommunityAliasCaches() = Unit

    override suspend fun deleteCommunityAliasCachesByIds(candidateIds: List<String>) = Unit
}
