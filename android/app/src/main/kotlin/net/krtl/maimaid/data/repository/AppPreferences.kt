package net.krtl.maimaid.data.repository

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.krtl.maimaid.domain.model.AppPreferencesState
import net.krtl.maimaid.domain.model.StaticSyncOptions
import net.krtl.maimaid.domain.model.ThemeMode
import net.krtl.maimaid.domain.repository.PreferencesRepository

class AppPreferences(context: Context) : PreferencesRepository {
    private val json = Json { ignoreUnknownKeys = true }
    private val dataStore = PreferenceDataStoreFactory.create(
        produceFile = { context.preferencesDataStoreFile("maimaid.preferences_pb") }
    )

    override val preferences: Flow<AppPreferencesState> = dataStore.data.map { prefs ->
        AppPreferencesState(
            themeMode = ThemeMode.fromRawValue((prefs[THEME_MODE] ?: "0").toIntOrNull() ?: 0),
            dynamicColorEnabled = prefs[DYNAMIC_COLOR_ENABLED] ?: true,
            useFitDiff = prefs[USE_FIT_DIFF] ?: false,
            showScannerBoundingBox = prefs[SHOW_SCANNER_BOUNDING_BOX] ?: false,
            syncOptions = StaticSyncOptions(
                updateRemoteData = prefs[SYNC_UPDATE_REMOTE_DATA] ?: true,
                updateAliases = prefs[SYNC_UPDATE_ALIASES] ?: true,
                updateCovers = prefs[SYNC_UPDATE_COVERS] ?: true,
                updateIcons = prefs[SYNC_UPDATE_ICONS] ?: true,
                updateDanData = prefs[SYNC_UPDATE_DAN_DATA] ?: true,
                updateChartStats = prefs[SYNC_UPDATE_CHART_STATS] ?: true
            ),
            hideDeletedSongs = prefs[HIDE_DELETED_SONGS] ?: true,
            versionSequence = prefs[VERSION_SEQUENCE]?.let { json.decodeFromString(it) } ?: emptyList(),
            categorySequence = prefs[CATEGORY_SEQUENCE]?.let { json.decodeFromString(it) } ?: emptyList(),
            versionsJson = prefs[VERSIONS_JSON],
            chartStatsJson = prefs[CHART_STATS_JSON],
            didPerformInitialSync = prefs[DID_PERFORM_INITIAL_SYNC] ?: false
        )
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = themeMode.rawValue.toString() }
    }

    override suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        dataStore.edit { it[DYNAMIC_COLOR_ENABLED] = enabled }
    }

    override suspend fun updateUseFitDiff(enabled: Boolean) {
        dataStore.edit { it[USE_FIT_DIFF] = enabled }
    }

    override suspend fun updateShowScannerBoundingBox(enabled: Boolean) {
        dataStore.edit { it[SHOW_SCANNER_BOUNDING_BOX] = enabled }
    }

    override suspend fun updateSyncOptions(options: StaticSyncOptions) {
        dataStore.edit {
            it[SYNC_UPDATE_REMOTE_DATA] = options.updateRemoteData
            it[SYNC_UPDATE_ALIASES] = options.updateAliases
            it[SYNC_UPDATE_COVERS] = options.updateCovers
            it[SYNC_UPDATE_ICONS] = options.updateIcons
            it[SYNC_UPDATE_DAN_DATA] = options.updateDanData
            it[SYNC_UPDATE_CHART_STATS] = options.updateChartStats
        }
    }

    override suspend fun updateHideDeletedSongs(enabled: Boolean) {
        dataStore.edit { it[HIDE_DELETED_SONGS] = enabled }
    }

    override suspend fun setVersionMetadata(versionsJson: String, versionSequence: List<String>, categorySequence: List<String>) {
        dataStore.edit {
            it[VERSIONS_JSON] = versionsJson
            it[VERSION_SEQUENCE] = json.encodeToString(versionSequence)
            it[CATEGORY_SEQUENCE] = json.encodeToString(categorySequence)
        }
    }

    override suspend fun setChartStatsJson(chartStatsJson: String) {
        dataStore.edit { it[CHART_STATS_JSON] = chartStatsJson }
    }

    override suspend fun markInitialSyncComplete() {
        dataStore.edit { it[DID_PERFORM_INITIAL_SYNC] = true }
    }

    override suspend fun getLastVersionCheckSuccessAt(): Long? = dataStore.data.first()[LAST_VERSION_CHECK_SUCCESS_AT]

    override suspend fun setLastVersionCheckSuccessAt(timestampMillis: Long) {
        dataStore.edit { it[LAST_VERSION_CHECK_SUCCESS_AT] = timestampMillis }
    }

    companion object {
        private val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamicColorEnabled")
        private val USE_FIT_DIFF = booleanPreferencesKey("useFitDiff")
        private val SHOW_SCANNER_BOUNDING_BOX = booleanPreferencesKey("showScannerBoundingBox")
        private val SYNC_UPDATE_REMOTE_DATA = booleanPreferencesKey("syncUpdateRemoteData")
        private val SYNC_UPDATE_ALIASES = booleanPreferencesKey("syncUpdateAliases")
        private val SYNC_UPDATE_COVERS = booleanPreferencesKey("syncUpdateCovers")
        private val SYNC_UPDATE_ICONS = booleanPreferencesKey("syncUpdateIcons")
        private val SYNC_UPDATE_DAN_DATA = booleanPreferencesKey("syncUpdateDanData")
        private val SYNC_UPDATE_CHART_STATS = booleanPreferencesKey("syncUpdateChartStats")
        private val HIDE_DELETED_SONGS = booleanPreferencesKey("filter.hideDeletedSongs")
        private val VERSION_SEQUENCE = stringPreferencesKey("MaimaiVersionSequence")
        private val CATEGORY_SEQUENCE = stringPreferencesKey("MaimaiCategorySequence")
        private val VERSIONS_JSON = stringPreferencesKey("MaimaiVersionsData")
        private val CHART_STATS_JSON = stringPreferencesKey("MaimaiChartStatsData")
        private val DID_PERFORM_INITIAL_SYNC = booleanPreferencesKey("didPerformInitialSync")
        private val LAST_VERSION_CHECK_SUCCESS_AT = longPreferencesKey("lastVersionCheckSuccessAt")
        private val THEME_MODE = stringPreferencesKey("themeMode")
    }
}
