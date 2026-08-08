package net.krtl.maimaid.data.repository

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
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

class ProfileRepositoryImplTest {
    @Test
    fun ensureActiveProfile_createsSingleDefaultProfileWhenCalledConcurrently() = runTest {
        val dao = ConcurrentProfileDao()
        val repository = ProfileRepositoryImpl(dao)

        val profiles = List(8) {
            async { repository.ensureActiveProfile() }
        }.awaitAll()

        assertThat(dao.upsertCount).isEqualTo(1)
        assertThat(dao.profiles).hasSize(1)
        assertThat(profiles.map { it.id }.distinct()).hasSize(1)
        assertThat(profiles.first().name).isEqualTo("Player")
        assertThat(profiles.first().isActive).isTrue()
    }

    @Test
    fun deleteProfile_removesAssociatedUserDataAndPromotesNextProfile() = runTest {
        val dao = ConcurrentProfileDao().apply {
            profiles += UserProfileEntity(
                id = "active",
                name = "Active",
                server = "jp",
                avatarUrl = null,
                isActive = true,
                createdAt = 1L,
                playerRating = 0,
                plate = null,
                b35Count = 35,
                b15Count = 15,
                b35RecLimit = 10,
                b15RecLimit = 10
            )
            profiles += UserProfileEntity(
                id = "backup",
                name = "Backup",
                server = "jp",
                avatarUrl = null,
                isActive = false,
                createdAt = 2L,
                playerRating = 0,
                plate = null,
                b35Count = 35,
                b15Count = 15,
                b35RecLimit = 10,
                b15RecLimit = 10
            )
            scores += ScoreEntity("active::sheet-1", "sheet-1", "active", 100.0, "SSS", 1L, 0, null, null)
            scores += ScoreEntity("backup::sheet-2", "sheet-2", "backup", 99.0, "SS+", 2L, 0, null, null)
            playRecords += PlayRecordEntity("play-1", "sheet-1", "active", 100.0, "SSS", 1L, 0, null, null)
            playRecords += PlayRecordEntity("play-2", "sheet-2", "backup", 99.0, "SS+", 2L, 0, null, null)
        }
        val repository = ProfileRepositoryImpl(dao)

        repository.deleteProfile("active")

        assertThat(dao.profiles.map { it.id }).containsExactly("backup")
        assertThat(dao.profiles.single().isActive).isTrue()
        assertThat(dao.scores.map { it.userProfileId }).containsExactly("backup")
        assertThat(dao.playRecords.map { it.userProfileId }).containsExactly("backup")
    }
}

private class ConcurrentProfileDao : MaimaiDao {
    val profiles = mutableListOf<UserProfileEntity>()
    val scores = mutableListOf<ScoreEntity>()
    val playRecords = mutableListOf<PlayRecordEntity>()
    var upsertCount = 0

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

    override suspend fun deleteSheetsNotIn(songIdentifier: String, sheetIds: List<String>) = Unit

    override suspend fun deleteSheetsForSong(songIdentifier: String) = Unit

    override suspend fun getSheet(sheetId: String): SheetEntity? = null

    override suspend fun getSheets(): List<SheetEntity> = emptyList()

    override fun observeScores(userProfileId: String): Flow<List<ScoreEntity>> = flowOf(scores.filter { it.userProfileId == userProfileId })

    override suspend fun getScores(userProfileId: String): List<ScoreEntity> = scores.filter { it.userProfileId == userProfileId }

    override suspend fun getScore(sheetId: String, userProfileId: String): ScoreEntity? =
        scores.firstOrNull { it.sheetId == sheetId && it.userProfileId == userProfileId }

    override suspend fun upsertScore(score: ScoreEntity) {
        synchronized(this) {
            scores.removeAll { it.scoreKey == score.scoreKey }
            scores += score
        }
    }

    override suspend fun upsertScores(scores: List<ScoreEntity>) = Unit

    override suspend fun deleteScoresForUser(userProfileId: String) {
        synchronized(this) {
            scores.removeAll { it.userProfileId == userProfileId }
        }
    }

    override suspend fun deleteScoresNotInSheets(sheetIds: List<String>) {
        synchronized(this) {
            scores.removeAll { it.sheetId !in sheetIds }
        }
    }

    override suspend fun insertPlayRecord(record: PlayRecordEntity) {
        synchronized(this) {
            playRecords.removeAll { it.id == record.id }
            playRecords += record
        }
    }

    override suspend fun upsertPlayRecords(records: List<PlayRecordEntity>) = Unit

    override fun observePlayRecords(userProfileId: String): Flow<List<PlayRecordEntity>> =
        flowOf(playRecords.filter { it.userProfileId == userProfileId })

    override suspend fun getPlayRecords(userProfileId: String): List<PlayRecordEntity> =
        playRecords.filter { it.userProfileId == userProfileId }

    override suspend fun deletePlayRecordsForUser(userProfileId: String) {
        synchronized(this) {
            playRecords.removeAll { it.userProfileId == userProfileId }
        }
    }

    override suspend fun deletePlayRecordsNotInSheets(sheetIds: List<String>) {
        synchronized(this) {
            playRecords.removeAll { it.sheetId !in sheetIds }
        }
    }

    override fun observeProfiles(): Flow<List<UserProfileEntity>> = flowOf(profiles.toList())

    override suspend fun getProfiles(): List<UserProfileEntity> {
        delay(10)
        return synchronized(this) { profiles.sortedBy { it.createdAt }.toList() }
    }

    override fun observeActiveProfile(): Flow<UserProfileEntity?> = flowOf(profiles.firstOrNull { it.isActive })

    override suspend fun getActiveProfile(): UserProfileEntity? {
        delay(10)
        return synchronized(this) { profiles.firstOrNull { it.isActive } }
    }

    override suspend fun upsertProfile(profile: UserProfileEntity) {
        synchronized(this) {
            upsertCount += 1
            profiles.removeAll { it.id == profile.id }
            profiles += profile
        }
    }

    override suspend fun upsertProfiles(profiles: List<UserProfileEntity>) = Unit

    override suspend fun clearActiveProfiles() {
        synchronized(this) {
            val cleared = profiles.map { it.copy(isActive = false) }
            profiles.clear()
            profiles += cleared
        }
    }

    override suspend fun activateProfile(profileId: String) {
        synchronized(this) {
            val updated = profiles.map { profile ->
                profile.copy(isActive = profile.id == profileId)
            }
            profiles.clear()
            profiles += updated
        }
    }

    override suspend fun deleteProfile(profileId: String) {
        synchronized(this) {
            profiles.removeAll { it.id == profileId }
        }
    }

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
