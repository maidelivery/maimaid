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

    private companion object {
        val ThemeModeKey = stringPreferencesKey("theme_mode")
        val ThemeColorSourceKey = stringPreferencesKey("theme_color_source")
        val ThemeCustomColorArgbKey = intPreferencesKey("theme_custom_color_argb")
        val ShowScannerBoundingBoxesKey = booleanPreferencesKey("show_scanner_bounding_boxes")
        val CatalogSortOptionKey = stringPreferencesKey("catalog_sort_option")
        val CatalogSortAscendingKey = booleanPreferencesKey("catalog_sort_ascending")
        val CatalogGridColumnsKey = intPreferencesKey("catalog_grid_columns")
        val CatalogHideUnavailableSongsKey = booleanPreferencesKey("catalog_hide_unavailable_songs")
    }
}
