package net.krtl.maimaid.domain.repository

import kotlinx.coroutines.flow.Flow
import net.krtl.maimaid.domain.model.AppPreferencesState
import net.krtl.maimaid.domain.model.B50Result
import net.krtl.maimaid.domain.model.HomeSummary
import net.krtl.maimaid.domain.model.MaimaiIcon
import net.krtl.maimaid.domain.model.PlayRecord
import net.krtl.maimaid.domain.model.PlateProgressItem
import net.krtl.maimaid.domain.model.RecommendationResult
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.domain.model.SongStatistics
import net.krtl.maimaid.domain.model.StaticSyncOptions
import net.krtl.maimaid.domain.model.StaticSyncStatus
import net.krtl.maimaid.domain.model.SyncConfig
import net.krtl.maimaid.domain.model.ThemeMode
import net.krtl.maimaid.domain.model.UserProfile

interface StaticDataRepository {
    val syncStatus: Flow<StaticSyncStatus>
    fun observeSongs(): Flow<List<Song>>
    fun observeSong(songIdentifier: String): Flow<Song?>
    fun observeSyncConfig(): Flow<SyncConfig>
    fun observeIcons(): Flow<List<MaimaiIcon>>
    suspend fun syncStaticData(options: StaticSyncOptions)
    suspend fun setSongFavorite(songIdentifier: String, isFavorite: Boolean)
    suspend fun updateSyncConfig(transform: (SyncConfig) -> SyncConfig)
    suspend fun getSongStatistics(): SongStatistics
}

interface ProfileRepository {
    fun observeProfiles(): Flow<List<UserProfile>>
    fun observeActiveProfile(): Flow<UserProfile?>
    suspend fun ensureActiveProfile(): UserProfile
    suspend fun setActiveProfile(profileId: String)
    suspend fun saveProfile(profile: UserProfile)
    suspend fun deleteProfile(profileId: String)
}

interface ScoreRepository {
    fun observeScores(userProfileId: String): Flow<List<Score>>
    fun observePlayRecords(userProfileId: String): Flow<List<PlayRecord>>
    suspend fun getScores(userProfileId: String): List<Score>
    suspend fun getPlayRecords(userProfileId: String): List<PlayRecord>
    suspend fun getScore(sheetId: String, userProfileId: String): Score?
    suspend fun saveScore(
        sheetId: String,
        userProfileId: String,
        rate: Double,
        dxScore: Int = 0,
        fc: String? = null,
        fs: String? = null
    ): Score
}

interface RecommendationRepository {
    suspend fun getB50(profile: UserProfile, songs: List<Song>, scores: List<Score>, preferences: AppPreferencesState): B50Result
    suspend fun getRecommendations(profile: UserProfile, songs: List<Song>, scores: List<Score>, preferences: AppPreferencesState): RecommendationResult
    suspend fun getPlateProgress(songs: List<Song>, scores: List<Score>): List<PlateProgressItem>
    suspend fun getHomeSummary(): HomeSummary
}

interface PreferencesRepository {
    val preferences: Flow<AppPreferencesState>
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateDynamicColorEnabled(enabled: Boolean)
    suspend fun updateUseFitDiff(enabled: Boolean)
    suspend fun updateShowScannerBoundingBox(enabled: Boolean)
    suspend fun updateSyncOptions(options: StaticSyncOptions)
    suspend fun updateHideDeletedSongs(enabled: Boolean)
    suspend fun setVersionMetadata(versionsJson: String, versionSequence: List<String>, categorySequence: List<String>)
    suspend fun setChartStatsJson(chartStatsJson: String)
    suspend fun markInitialSyncComplete()
    suspend fun getLastVersionCheckSuccessAt(): Long?
    suspend fun setLastVersionCheckSuccessAt(timestampMillis: Long = System.currentTimeMillis())
}
