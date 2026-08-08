package net.krtl.maimaid.core.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import net.krtl.maimaid.core.domain.repository.SettingsRepository
import net.krtl.maimaid.core.domain.repository.SettingsState
import net.krtl.maimaid.domain.model.ThemeMode
import net.krtl.maimaid.domain.repository.PreferencesRepository
import net.krtl.maimaid.domain.repository.StaticDataRepository

class SettingsRepositoryImpl(
    private val preferencesRepository: PreferencesRepository,
    private val staticDataRepository: StaticDataRepository
) : SettingsRepository {
    override val settingsState: Flow<SettingsState> = combine(
        preferencesRepository.preferences,
        staticDataRepository.observeSyncConfig()
    ) { preferences, config ->
        SettingsState(
            themeMode = preferences.themeMode,
            dynamicColorEnabled = preferences.dynamicColorEnabled,
            backgroundSyncInterval = config.backgroundSyncInterval,
            cloudBackupInterval = config.cloudBackupInterval
        )
    }

    override suspend fun updateThemeMode(themeMode: ThemeMode) {
        preferencesRepository.updateThemeMode(themeMode)
    }

    override suspend fun updateDynamicColorEnabled(enabled: Boolean) {
        preferencesRepository.updateDynamicColorEnabled(enabled)
    }

    override suspend fun updateBackgroundSyncInterval(hours: Int) {
        staticDataRepository.updateSyncConfig { current ->
            current.copy(backgroundSyncInterval = hours.coerceAtLeast(0))
        }
    }

    override suspend fun updateCloudBackupInterval(hours: Int) {
        staticDataRepository.updateSyncConfig { current ->
            current.copy(cloudBackupInterval = hours.coerceAtLeast(0))
        }
    }
}

