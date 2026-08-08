package net.krtl.maimaid.feature.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.repository.ImportRepository
import net.krtl.maimaid.core.domain.repository.SyncRepository
import net.krtl.maimaid.domain.repository.ProfileRepository
import java.security.MessageDigest
import java.util.Base64
import java.util.UUID

data class DataImportUiState(
    val activeProfileId: String? = null,
    val username: String = "",
    val qq: String = "",
    val lxnsCode: String = "",
    val lxnsCodeVerifier: String = "",
    val isImportingDf: Boolean = false,
    val isImportingLxns: Boolean = false,
    val statusMessage: String? = null
)

sealed interface DataImportIntent {
    data class UpdateUsername(val value: String) : DataImportIntent
    data class UpdateQq(val value: String) : DataImportIntent
    data class UpdateLxnsCode(val value: String) : DataImportIntent
    data object OpenLxnsAuthPage : DataImportIntent
    data object RegenerateLxnsCodeVerifier : DataImportIntent
    data object ImportDivingFish : DataImportIntent
    data object ImportLxns : DataImportIntent
}

sealed interface DataImportEvent {
    data class Toast(val message: String) : DataImportEvent
    data class OpenUrl(val url: String) : DataImportEvent
}

class DataImportViewModel(
    private val profileRepository: ProfileRepository,
    private val importRepository: ImportRepository,
    private val syncRepository: SyncRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow(DataImportUiState())
    val uiState: StateFlow<DataImportUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<DataImportEvent>(replay = 0)
    val events: SharedFlow<DataImportEvent> = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            profileRepository.observeActiveProfile().collect { profile ->
                _uiState.update { state ->
                    state.copy(activeProfileId = profile?.id)
                }
            }
        }
        generateVerifier()
    }

    fun onIntent(intent: DataImportIntent) {
        when (intent) {
            is DataImportIntent.UpdateUsername -> _uiState.update { it.copy(username = intent.value) }
            is DataImportIntent.UpdateQq -> _uiState.update { it.copy(qq = intent.value) }
            is DataImportIntent.UpdateLxnsCode -> _uiState.update { it.copy(lxnsCode = intent.value) }
            DataImportIntent.OpenLxnsAuthPage -> openLxnsAuthPage()
            DataImportIntent.RegenerateLxnsCodeVerifier -> regenerateVerifier()
            DataImportIntent.ImportDivingFish -> importDivingFish()
            DataImportIntent.ImportLxns -> importLxns()
        }
    }

    private fun regenerateVerifier() {
        generateVerifier()
        openLxnsAuthPage()
    }

    private fun generateVerifier() {
        val verifier = UUID.randomUUID().toString() + UUID.randomUUID().toString()
        _uiState.update { it.copy(lxnsCodeVerifier = verifier) }
    }

    private fun openLxnsAuthPage() {
        val verifier = uiState.value.lxnsCodeVerifier.ifBlank {
            generateVerifier()
            uiState.value.lxnsCodeVerifier
        }
        val codeChallenge = Base64.getUrlEncoder()
            .withoutPadding()
            .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)))
        val authUrl = "https://maimai.lxns.net/oauth/authorize" +
            "?response_type=code" +
            "&client_id=cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338" +
            "&redirect_uri=urn:ietf:wg:oauth:2.0:oob" +
            "&scope=maimai_scores%2Cmaimai_profile" +
            "&code_challenge=$codeChallenge" +
            "&code_challenge_method=S256"
        viewModelScope.launch {
            _events.emit(DataImportEvent.OpenUrl(authUrl))
        }
    }

    private fun importDivingFish() {
        val profileId = uiState.value.activeProfileId
        if (profileId.isNullOrBlank()) {
            viewModelScope.launch { _events.emit(DataImportEvent.Toast("No active profile")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingDf = true, statusMessage = null) }
            val result = importRepository.importDivingFish(
                profileId = profileId,
                username = uiState.value.username,
                qq = uiState.value.qq
            )
            when (result) {
                is Result.Ok -> {
                    val pullResult = syncRepository.pull(
                        sinceRevision = "0",
                        profileId = profileId,
                        force = false
                    )
                    val syncMessage = when (pullResult) {
                        is Result.Ok -> ", synced ${pullResult.value.scoreCount} scores"
                        is Result.Err -> ", sync skipped: ${pullResult.error.asReadableMessage()}"
                    }
                    _uiState.update {
                        it.copy(
                            isImportingDf = false,
                            statusMessage = "DF import done: ${result.value.upsertedCount} upserted$syncMessage"
                        )
                    }
                }
                is Result.Err -> {
                    _uiState.update {
                        it.copy(
                            isImportingDf = false,
                            statusMessage = "DF import failed: ${result.error.asReadableMessage()}"
                        )
                    }
                }
            }
        }
    }

    private fun importLxns() {
        val profileId = uiState.value.activeProfileId
        if (profileId.isNullOrBlank()) {
            viewModelScope.launch { _events.emit(DataImportEvent.Toast("No active profile")) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isImportingLxns = true, statusMessage = null) }
            val result = importRepository.importLxnsByOAuthCode(
                profileId = profileId,
                code = uiState.value.lxnsCode,
                codeVerifier = uiState.value.lxnsCodeVerifier
            )
            when (result) {
                is Result.Ok -> {
                    val pullResult = syncRepository.pull(
                        sinceRevision = "0",
                        profileId = profileId,
                        force = false
                    )
                    val syncMessage = when (pullResult) {
                        is Result.Ok -> ", synced ${pullResult.value.scoreCount} scores"
                        is Result.Err -> ", sync skipped: ${pullResult.error.asReadableMessage()}"
                    }
                    _uiState.update {
                        it.copy(
                            isImportingLxns = false,
                            statusMessage = "LXNS import done: ${result.value.upsertedCount} upserted$syncMessage"
                        )
                    }
                }
                is Result.Err -> {
                    _uiState.update {
                        it.copy(
                            isImportingLxns = false,
                            statusMessage = "LXNS import failed: ${result.error.asReadableMessage()}"
                        )
                    }
                }
            }
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
