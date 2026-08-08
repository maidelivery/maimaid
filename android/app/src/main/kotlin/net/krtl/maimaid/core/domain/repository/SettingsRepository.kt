package net.krtl.maimaid.core.domain.repository

import kotlinx.coroutines.flow.Flow
import net.krtl.maimaid.domain.model.ThemeMode

data class SettingsState(
    val themeMode: ThemeMode,
    val dynamicColorEnabled: Boolean,
    val backgroundSyncInterval: Int,
    val cloudBackupInterval: Int
)

interface SettingsRepository {
    val settingsState: Flow<SettingsState>
    suspend fun updateThemeMode(themeMode: ThemeMode)
    suspend fun updateDynamicColorEnabled(enabled: Boolean)
    suspend fun updateBackgroundSyncInterval(hours: Int)
    suspend fun updateCloudBackupInterval(hours: Int)
}

