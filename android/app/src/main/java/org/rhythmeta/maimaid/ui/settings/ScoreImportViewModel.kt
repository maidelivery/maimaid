package org.rhythmeta.maimaid.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import java.time.Instant
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.BackendImportRunResponse
import org.rhythmeta.maimaid.core.data.ImportSyncConflictPreview
import org.rhythmeta.maimaid.core.data.ImportSyncResolution
import org.rhythmeta.maimaid.core.data.LxnsTokenExpiredException
import org.rhythmeta.maimaid.core.data.ProfileCredentials
import org.rhythmeta.maimaid.core.database.UserProfileEntity
import org.rhythmeta.maimaid.core.network.BackendApiException
import kotlin.time.Duration.Companion.milliseconds

enum class ScoreImportPhase {
    Idle,
    CheckingSession,
    Connecting,
    RefreshingToken,
    ExchangingToken,
    Fetching,
    CheckingConflicts,
    Applying,
}

enum class ScoreImportResult {
    Imported,
    NoChanges,
    LoginRequired,
    TokenExpired,
    Failed,
}

data class ScoreImportUiState(
    val profile: UserProfileEntity? = null,
    val divingFishConnected: Boolean = false,
    val divingFishCanWrite: Boolean = false,
    val divingFishUsername: String? = null,
    val lxnsRefreshToken: String = "",
    val lxnsAuthorizationCode: String = "",
    val phase: ScoreImportPhase = ScoreImportPhase.Idle,
    val result: ScoreImportResult? = null,
    val resultDetails: String? = null,
    val fetchedCount: Int = 0,
    val upsertedCount: Int = 0,
    val skippedCount: Int = 0,
    val conflictPreview: ImportSyncConflictPreview? = null,
    val isResolvingConflict: Boolean = false,
) {
    val isBusy: Boolean get() = phase != ScoreImportPhase.Idle || isResolvingConflict
    val hasDivingFishAccount: Boolean get() = divingFishConnected
    val hasLxnsAccount: Boolean get() = lxnsRefreshToken.isNotBlank()
}

class ScoreImportViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ScoreImportUiState())
    val state = mutableState.asStateFlow()
    private var lxnsCodeVerifier = ""

    init {
        viewModelScope.launch {
            container.profileRepository.activeProfile.collectLatest { profile ->
                val credentials = profile?.let { container.profileCredentialStore.credentials(it.id) }
                    ?: ProfileCredentials()
                mutableState.update {
                    it.copy(
                        profile = profile,
                        divingFishConnected = false,
                        divingFishCanWrite = false,
                        divingFishUsername = null,
                        lxnsRefreshToken = credentials.lxnsToken,
                        result = null,
                        resultDetails = null,
                        conflictPreview = null,
                    )
                }
                if (profile != null && container.backendSessionManager.state.value.isAuthenticated) {
                    runCatching { container.backendImportService.divingFishBindingStatus(profile.id) }
                        .onSuccess { binding ->
                            mutableState.update {
                                it.copy(
                                    divingFishConnected = binding.connected,
                                    divingFishCanWrite = binding.canWrite,
                                    divingFishUsername = binding.externalUsername,
                                )
                            }
                        }
                }
            }
        }
    }

    fun setLxnsAuthorizationCode(value: String) {
        mutableState.update { it.copy(lxnsAuthorizationCode = value) }
    }

    fun createLxnsAuthorizationUrl(): String {
        val authorization = container.backendImportService.createLxnsAuthorization()
        lxnsCodeVerifier = authorization.codeVerifier
        mutableState.update { it.copy(result = null, resultDetails = null) }
        return authorization.url
    }

    fun authorizeAndImportDivingFish(openAuthorization: (String) -> Boolean) {
        val state = mutableState.value
        val profile = state.profile ?: return
        if (state.isBusy) return
        viewModelScope.launch {
            startOperation()
            runCatching {
                requireAuthenticated()
                mutableState.update { it.copy(phase = ScoreImportPhase.Connecting) }
                container.backendSyncCoordinator.ensureProfileExists(profile)
                val authorization = container.backendImportService.startDivingFishAuthorization(profile.id)
                if (!openAuthorization(authorization.authorizationUrl)) throw ImportBrowserUnavailableException()
                val expiresAt = Instant.parse(authorization.expiresAt).toEpochMilli()
                while (System.currentTimeMillis() < expiresAt) {
                    delay(1_500.milliseconds)
                    val status = container.backendImportService.divingFishAuthorizationStatus(
                        authorization.authorizationId,
                    )
                    when (status.status) {
                        "success" -> {
                            val binding = runCatching {
                                container.backendImportService.divingFishBindingStatus(profile.id)
                            }.getOrNull()
                            mutableState.update {
                                it.copy(
                                    divingFishConnected = true,
                                    divingFishCanWrite = binding?.canWrite ?: false,
                                    divingFishUsername = binding?.externalUsername,
                                )
                            }
                            importDivingFish(profile)
                            return@runCatching
                        }
                        "failed", "expired" -> throw DivingFishAuthorizationException(status.errorCode)
                    }
                }
                throw DivingFishAuthorizationException("expired")
            }.onFailure(::finishFailure)
        }
    }

    fun quickImportDivingFish() {
        val state = mutableState.value
        val profile = state.profile ?: return
        if (!state.divingFishConnected || state.isBusy) return
        viewModelScope.launch {
            startOperation()
            runCatching {
                requireAuthenticated()
                container.backendSyncCoordinator.ensureProfileExists(profile)
                importDivingFish(profile)
            }.onFailure(::finishFailure)
        }
    }

    fun disconnectDivingFish() {
        val profile = mutableState.value.profile ?: return
        if (mutableState.value.isBusy) return
        viewModelScope.launch {
            startOperation()
            runCatching {
                requireAuthenticated()
                container.backendImportService.disconnectDivingFish(profile.id)
            }.onSuccess {
                mutableState.update {
                    it.copy(
                        phase = ScoreImportPhase.Idle,
                        divingFishConnected = false,
                        divingFishCanWrite = false,
                        divingFishUsername = null,
                    )
                }
            }.onFailure(::finishFailure)
        }
    }

    fun exchangeLxnsCodeAndImport() {
        val state = mutableState.value
        val profile = state.profile ?: return
        val code = state.lxnsAuthorizationCode.trim()
        if (code.isEmpty() || lxnsCodeVerifier.isEmpty() || state.isBusy) return
        viewModelScope.launch {
            startOperation(ScoreImportPhase.ExchangingToken)
            runCatching {
                requireAuthenticated()
                val token = container.backendImportService.exchangeLxnsCode(code, lxnsCodeVerifier)
                saveLxnsRefreshToken(profile.id, token.refreshToken)
                importLxns(profile, token.accessToken)
            }.onFailure(::finishFailure)
        }
    }

    fun quickImportLxns() {
        val state = mutableState.value
        val profile = state.profile ?: return
        val refreshToken = state.lxnsRefreshToken.trim()
        if (refreshToken.isEmpty() || state.isBusy) return
        viewModelScope.launch {
            startOperation(ScoreImportPhase.RefreshingToken)
            runCatching {
                requireAuthenticated()
                val token = container.backendImportService.refreshLxnsToken(refreshToken)
                saveLxnsRefreshToken(profile.id, token.refreshToken)
                importLxns(profile, token.accessToken)
            }.onFailure(::finishFailure)
        }
    }

    fun disconnectLxns() {
        val profileId = mutableState.value.profile?.id ?: return
        saveLxnsRefreshToken(profileId, "")
        mutableState.update {
            it.copy(
                lxnsRefreshToken = "",
                lxnsAuthorizationCode = "",
                result = null,
                resultDetails = null,
            )
        }
    }

    fun resolveConflict(resolution: ImportSyncResolution) {
        val preview = mutableState.value.conflictPreview ?: return
        if (mutableState.value.isResolvingConflict) return
        viewModelScope.launch {
            mutableState.update { it.copy(isResolvingConflict = true, resultDetails = null) }
            runCatching {
                container.backendSyncCoordinator.applyImportConflictResolution(resolution, preview)
                updateLocalImportDate(preview.profileId, isDivingFish = pendingImportIsDivingFish)
            }.onSuccess {
                mutableState.update {
                    it.copy(
                        conflictPreview = null,
                        isResolvingConflict = false,
                        result = ScoreImportResult.Imported,
                    )
                }
            }.onFailure { error ->
                mutableState.update {
                    it.copy(
                        conflictPreview = null,
                        isResolvingConflict = false,
                        result = ScoreImportResult.Failed,
                        resultDetails = error.message,
                    )
                }
            }
        }
    }

    fun dismissConflict() {
        if (!mutableState.value.isResolvingConflict) {
            mutableState.update { it.copy(conflictPreview = null) }
        }
    }

    private var pendingImportIsDivingFish = false

    private suspend fun importDivingFish(profile: UserProfileEntity) {
        mutableState.update { it.copy(phase = ScoreImportPhase.Fetching) }
        val response = container.backendImportService.importDivingFish(profile.id)
        finishRemoteImport(profile, response, isDivingFish = true)
    }

    private suspend fun importLxns(profile: UserProfileEntity, accessToken: String) {
        mutableState.update { it.copy(phase = ScoreImportPhase.Connecting) }
        container.backendSyncCoordinator.ensureProfileExists(profile)
        mutableState.update { it.copy(phase = ScoreImportPhase.Fetching) }
        val response = container.backendImportService.importLxns(profile.id, accessToken)
        finishRemoteImport(profile, response, isDivingFish = false)
    }

    private suspend fun finishRemoteImport(
        profile: UserProfileEntity,
        response: BackendImportRunResponse,
        isDivingFish: Boolean,
    ) {
        pendingImportIsDivingFish = isDivingFish
        mutableState.update {
            it.copy(
                phase = ScoreImportPhase.CheckingConflicts,
                fetchedCount = response.fetchedCount,
                upsertedCount = response.upsertedCount,
                skippedCount = response.skippedCount,
            )
        }
        val preview = container.backendSyncCoordinator.previewImportConflicts(profile.id)
        if (preview.conflictCount > 0) {
            mutableState.update {
                it.copy(phase = ScoreImportPhase.Idle, conflictPreview = preview)
            }
            return
        }
        mutableState.update { it.copy(phase = ScoreImportPhase.Applying) }
        container.backendSyncCoordinator.applyImportConflictResolution(ImportSyncResolution.MergeBest, preview)
        updateLocalImportDate(profile.id, isDivingFish)
        mutableState.update {
            it.copy(
                phase = ScoreImportPhase.Idle,
                result = if (response.upsertedCount == 0) ScoreImportResult.NoChanges else ScoreImportResult.Imported,
            )
        }
    }

    private suspend fun requireAuthenticated() {
        mutableState.update { it.copy(phase = ScoreImportPhase.CheckingSession) }
        container.backendSessionManager.checkSession()
        if (!container.backendSessionManager.state.value.isAuthenticated) {
            throw ImportLoginRequiredException()
        }
    }

    private fun saveLxnsRefreshToken(profileId: String, token: String) {
        val current = container.profileCredentialStore.credentials(profileId)
        container.profileCredentialStore.save(profileId, current.copy(lxnsToken = token.trim()))
        mutableState.update { it.copy(lxnsRefreshToken = token.trim()) }
    }

    private suspend fun updateLocalImportDate(profileId: String, isDivingFish: Boolean) {
        val profile = container.database.profileDao().profiles().firstOrNull { it.id == profileId } ?: return
        val now = System.currentTimeMillis()
        container.profileRepository.save(
            if (isDivingFish) profile.copy(lastImportDateDf = now)
            else profile.copy(lastImportDateLxns = now),
        )
    }

    private fun startOperation(phase: ScoreImportPhase = ScoreImportPhase.CheckingSession) {
        mutableState.update {
            it.copy(
                phase = phase,
                result = null,
                resultDetails = null,
                fetchedCount = 0,
                upsertedCount = 0,
                skippedCount = 0,
                conflictPreview = null,
            )
        }
    }

    private fun finishFailure(error: Throwable) {
        if (error is LxnsTokenExpiredException) {
            mutableState.value.profile?.id?.let { saveLxnsRefreshToken(it, "") }
        }
        mutableState.update {
            it.copy(
                phase = ScoreImportPhase.Idle,
                divingFishConnected = if (error is BackendApiException && error.code == "df_authorization_required") {
                    false
                } else {
                    it.divingFishConnected
                },
                divingFishUsername = if (error is BackendApiException && error.code == "df_authorization_required") {
                    null
                } else {
                    it.divingFishUsername
                },
                divingFishCanWrite = if (error is BackendApiException && error.code == "df_authorization_required") {
                    false
                } else {
                    it.divingFishCanWrite
                },
                result = when (error) {
                    is ImportLoginRequiredException -> ScoreImportResult.LoginRequired
                    is LxnsTokenExpiredException -> ScoreImportResult.TokenExpired
                    else -> ScoreImportResult.Failed
                },
                resultDetails = error.message,
            )
        }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScoreImportViewModel::class.java))
            return ScoreImportViewModel(container) as T
        }
    }
}

private class ImportLoginRequiredException : Exception("Authentication required.")
private class ImportBrowserUnavailableException : Exception("Unable to open the authorization page.")
private class DivingFishAuthorizationException(code: String?) :
    Exception("Diving Fish authorization failed${code?.let { ": $it" }.orEmpty()}.")
