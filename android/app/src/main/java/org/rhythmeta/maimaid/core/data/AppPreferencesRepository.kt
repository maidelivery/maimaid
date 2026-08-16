package org.rhythmeta.maimaid.core.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.rhythmeta.maimaid.ui.theme.AppThemeColorSource
import org.rhythmeta.maimaid.ui.theme.AppThemeMode
import org.rhythmeta.maimaid.ui.theme.DefaultThemeCustomColorArgb

private val Context.appPreferencesDataStore by preferencesDataStore(name = "app_preferences")

class AppPreferencesRepository(
    private val context: Context,
) {
    val themeMode: Flow<AppThemeMode> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ThemeModeKey]
            ?.let { stored -> AppThemeMode.entries.firstOrNull { it.name == stored } }
            ?: AppThemeMode.System
    }

    val themeColorSource: Flow<AppThemeColorSource> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ThemeColorSourceKey]
            ?.let { stored -> AppThemeColorSource.entries.firstOrNull { it.name == stored } }
            ?: AppThemeColorSource.Wallpaper
    }

    val themeCustomColorArgb: Flow<Int> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ThemeCustomColorArgbKey] ?: DefaultThemeCustomColorArgb
    }

    val showScannerBoundingBoxes: Flow<Boolean> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ShowScannerBoundingBoxesKey] ?: false
    }

    val thirdPartyScoreSyncEnabled: Flow<Boolean> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ThirdPartyScoreSyncEnabledKey] ?: false
    }

    val catalogSortOption: Flow<CatalogSortOption> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[CatalogSortOptionKey]
            ?.let { stored -> CatalogSortOption.entries.firstOrNull { it.name == stored } }
            ?: CatalogSortOption.DefaultOrder
    }

    val catalogSortAscending: Flow<Boolean> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[CatalogSortAscendingKey] ?: true
    }

    val catalogGridColumns: Flow<Int> = context.appPreferencesDataStore.data.map { preferences ->
        (preferences[CatalogGridColumnsKey] ?: 4).coerceIn(3, 9)
    }

    val catalogHideUnavailableSongs: Flow<Boolean> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[CatalogHideUnavailableSongsKey] ?: false
    }

    val catalogShowPlayableSongsOnly: Flow<Boolean> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[CatalogShowPlayableSongsOnlyKey] ?: false
    }

    val scoreQueryDisplayMode: Flow<ScoreQueryDisplayMode> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ScoreQueryDisplayModeKey]
            ?.let { stored -> ScoreQueryDisplayMode.entries.firstOrNull { it.name == stored } }
            ?: ScoreQueryDisplayMode.Grid
    }

    val scoreQueryGridColumns: Flow<Int> = context.appPreferencesDataStore.data.map { preferences ->
        (preferences[ScoreQueryGridColumnsKey] ?: 5).coerceIn(3, 9)
    }

    val scoreQuerySortMode: Flow<ScoreQuerySortMode> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ScoreQuerySortModeKey]
            ?.let { stored -> ScoreQuerySortMode.entries.firstOrNull { it.name == stored } }
            ?: ScoreQuerySortMode.Rating
    }

    val scoreQuerySortAscending: Flow<Boolean> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[ScoreQuerySortAscendingKey] ?: false
    }

    val best50ConstantMode: Flow<Best50ConstantMode> = context.appPreferencesDataStore.data.map { preferences ->
        preferences[Best50ConstantModeKey]
            ?.let { stored -> Best50ConstantMode.entries.firstOrNull { it.name == stored } }
            ?: Best50ConstantMode.Server
    }

    suspend fun setThemeMode(themeMode: AppThemeMode) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ThemeModeKey] = themeMode.name
        }
    }

    suspend fun setThemeColorSource(source: AppThemeColorSource) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ThemeColorSourceKey] = source.name
        }
    }

    suspend fun setThemeCustomColorArgb(colorArgb: Int) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ThemeCustomColorArgbKey] = colorArgb
        }
    }

    suspend fun setShowScannerBoundingBoxes(show: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ShowScannerBoundingBoxesKey] = show
        }
    }

    suspend fun setThirdPartyScoreSyncEnabled(enabled: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ThirdPartyScoreSyncEnabledKey] = enabled
        }
    }

    suspend fun setCatalogSortOption(sortOption: CatalogSortOption) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[CatalogSortOptionKey] = sortOption.name
        }
    }

    suspend fun setCatalogSortAscending(ascending: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[CatalogSortAscendingKey] = ascending
        }
    }

    suspend fun setCatalogGridColumns(columns: Int) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[CatalogGridColumnsKey] = columns.coerceIn(3, 9)
        }
    }

    suspend fun setCatalogHideUnavailableSongs(hide: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[CatalogHideUnavailableSongsKey] = hide
        }
    }

    suspend fun setCatalogShowPlayableSongsOnly(show: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[CatalogShowPlayableSongsOnlyKey] = show
        }
    }

    suspend fun setScoreQueryDisplayMode(displayMode: ScoreQueryDisplayMode) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ScoreQueryDisplayModeKey] = displayMode.name
        }
    }

    suspend fun setScoreQueryGridColumns(columns: Int) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ScoreQueryGridColumnsKey] = columns.coerceIn(3, 9)
        }
    }

    suspend fun setScoreQuerySortMode(sortMode: ScoreQuerySortMode) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ScoreQuerySortModeKey] = sortMode.name
        }
    }

    suspend fun setScoreQuerySortAscending(ascending: Boolean) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[ScoreQuerySortAscendingKey] = ascending
        }
    }

    suspend fun setBest50ConstantMode(mode: Best50ConstantMode) {
        context.appPreferencesDataStore.edit { preferences ->
            preferences[Best50ConstantModeKey] = mode.name
        }
    }

    private companion object {
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val ThemeColorSourceKey = stringPreferencesKey("theme_color_source")
        val ThemeCustomColorArgbKey = intPreferencesKey("theme_custom_color_argb")
        val ShowScannerBoundingBoxesKey = booleanPreferencesKey("show_scanner_bounding_boxes")
        val ThirdPartyScoreSyncEnabledKey = booleanPreferencesKey("third_party_score_sync_enabled")
        val CatalogSortOptionKey = stringPreferencesKey("catalog_sort_option")
        val CatalogSortAscendingKey = booleanPreferencesKey("catalog_sort_ascending")
        val CatalogGridColumnsKey = intPreferencesKey("catalog_grid_columns")
        val CatalogHideUnavailableSongsKey = booleanPreferencesKey("catalog_hide_unavailable_songs")
        val CatalogShowPlayableSongsOnlyKey = booleanPreferencesKey("catalog_show_playable_songs_only")
        val ScoreQueryDisplayModeKey = stringPreferencesKey("score_query_display_mode")
        val ScoreQueryGridColumnsKey = intPreferencesKey("score_query_grid_columns")
        val ScoreQuerySortModeKey = stringPreferencesKey("score_query_sort_mode")
        val ScoreQuerySortAscendingKey = booleanPreferencesKey("score_query_sort_ascending")
        val Best50ConstantModeKey = stringPreferencesKey("best50_constant_mode")
    }
}
