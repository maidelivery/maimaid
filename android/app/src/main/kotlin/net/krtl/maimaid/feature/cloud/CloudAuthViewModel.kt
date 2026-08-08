package net.krtl.maimaid.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SessionState
import net.krtl.maimaid.core.domain.repository.AuthRepository
import net.krtl.maimaid.core.domain.repository.AuthWebMode
import net.krtl.maimaid.core.domain.repository.SyncRepository
import net.krtl.maimaid.domain.repository.ProfileRepository
import net.krtl.maimaid.domain.repository.ScoreRepository
import net.krtl.maimaid.domain.repository.StaticDataRepository

data class CloudAuthUiState(
    val sessionState: SessionState = SessionState.Unknown,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val statusMessage: String? = null,
    val profileCount: Int = 0,
    val activeProfileName: String? = null,
    val activeScoreCount: Int = 0,
    val activeRecordCount: Int = 0,
    val lastSyncRevision: String = "0",
    val lastCloudBackupDate: Long? = null
)

sealed interface CloudAuthIntent {
    data object Refresh : CloudAuthIntent
    data object Login : CloudAuthIntent
    data object Register : CloudAuthIntent
    data object ForgotPassword : CloudAuthIntent
    data object PullFromCloud : CloudAuthIntent
    data object PushToCloud : CloudAuthIntent
    data object Logout : CloudAuthIntent
}

sealed interface CloudAuthEvent {
    data class OpenUrl(val url: String) : CloudAuthEvent
    data class Toast(val message: String) : CloudAuthEvent
}

class CloudAuthViewModel(
    private val authRepository: AuthRepository,
    private val syncRepository: SyncRepository,
    private val profileRepository: ProfileRepository,
    private val scoreRepository: ScoreRepository,
    private val staticDataRepository: StaticDataRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(CloudAuthUiState())
    val uiState: StateFlow<CloudAuthUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<CloudAuthEvent>(replay = 0)
    val events: SharedFlow<CloudAuthEvent> = _events.asSharedFlow()

    private var localSummaryJob: Job? = null

    init {
        viewModelScope.launch {
            authRepository.sessionState.collect { session ->
                _uiState.update { it.copy(sessionState = session, isLoading = false) }
            }
        }
        viewModelScope.launch {
            staticDataRepository.observeSyncConfig().collect { config ->
                _uiState.update {
                    it.copy(
                        lastSyncRevision = config.lastSyncRevision,
                        lastCloudBackupDate = config.lastCloudBackupDate
                    )
                }
            }
        }
        viewModelScope.launch {
            profileRepository.observeProfiles().collect { profiles ->
                _uiState.update { it.copy(profileCount = profiles.size) }
            }
        }
        viewModelScope.launch {
            profileRepository.observeActiveProfile().collect { profile ->
                _uiState.update { it.copy(activeProfileName = profile?.name) }
                localSummaryJob?.cancel()
                localSummaryJob = if (profile == null) {
                    _uiState.update { it.copy(activeScoreCount = 0, activeRecordCount = 0) }
                    null
                } else {
                    launch {
                        combine(
                            scoreRepository.observeScores(profile.id),
                            scoreRepository.observePlayRecords(profile.id)
                        ) { scores, records ->
                            scores.size to records.size
                        }.collect { (scoreCount, recordCount) ->
                            _uiState.update {
                                it.copy(
                                    activeScoreCount = scoreCount,
                                    activeRecordCount = recordCount
                                )
                            }
                        }
                    }
                }
            }
        }
        onIntent(CloudAuthIntent.Refresh)
    }

    fun onIntent(intent: CloudAuthIntent) {
        when (intent) {
            CloudAuthIntent.Refresh -> refresh()
            CloudAuthIntent.Login -> launchWebAuth(AuthWebMode.LOGIN)
            CloudAuthIntent.Register -> launchWebAuth(AuthWebMode.REGISTER)
            CloudAuthIntent.ForgotPassword -> launchWebAuth(AuthWebMode.FORGOT_PASSWORD)
            CloudAuthIntent.PullFromCloud -> pullFromCloud()
            CloudAuthIntent.PushToCloud -> pushToCloud()
            CloudAuthIntent.Logout -> logout()
        }
    }

    private fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            when (val result = authRepository.checkSession()) {
                is Result.Ok -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            sessionState = result.value,
                            statusMessage = null
                        )
                    }
                }
                is Result.Err -> {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            statusMessage = result.error.asReadableMessage()
                        )
                    }
                }
            }
        }
    }

    private fun launchWebAuth(mode: AuthWebMode) {
        viewModelScope.launch {
            val url = authRepository.buildWebAuthUrl(mode)
            if (url.isNullOrBlank()) {
                _events.emit(CloudAuthEvent.Toast("Backend auth URL is not configured"))
                return@launch
            }
            _events.emit(CloudAuthEvent.OpenUrl(url))
        }
    }

    private fun pullFromCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = null) }
            when (
                val result = syncRepository.pull(
                    sinceRevision = uiState.value.lastSyncRevision,
                    force = false
                )
            ) {
                is Result.Ok -> {
                    val summary = "Pulled ${result.value.profileCount} profiles, ${result.value.scoreCount} scores, ${result.value.recordCount} records"
                    _uiState.update { it.copy(isSyncing = false, statusMessage = summary) }
                    _events.emit(CloudAuthEvent.Toast(summary))
                }
                is Result.Err -> {
                    val message = "Pull failed: ${result.error.asReadableMessage()}"
                    _uiState.update { it.copy(isSyncing = false, statusMessage = message) }
                    _events.emit(CloudAuthEvent.Toast(message))
                }
            }
        }
    }

    private fun pushToCloud() {
        viewModelScope.launch {
            _uiState.update { it.copy(isSyncing = true, statusMessage = null) }
            when (val result = syncRepository.pushLocalSnapshot()) {
                is Result.Ok -> {
                    val summary = "Uploaded local data to cloud. Latest revision: ${result.value.latestRevision}"
                    _uiState.update { it.copy(isSyncing = false, statusMessage = summary) }
                    _events.emit(CloudAuthEvent.Toast(summary))
                }
                is Result.Err -> {
                    val message = "Push failed: ${result.error.asReadableMessage()}"
                    _uiState.update { it.copy(isSyncing = false, statusMessage = message) }
                    _events.emit(CloudAuthEvent.Toast(message))
                }
            }
        }
    }

    private fun logout() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = null) }
            val result = authRepository.logout()
            if (result is Result.Err) {
                _events.emit(CloudAuthEvent.Toast("Logout failed: ${result.error.asReadableMessage()}"))
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    private fun DomainError.asReadableMessage(): String = when (this) {
        is DomainError.Network -> message
        is DomainError.Unauthorized -> message
        is DomainError.Validation -> message
        is DomainError.Conflict -> message
        is DomainError.Server -> message
        is DomainError.Unknown -> message
    }
}
