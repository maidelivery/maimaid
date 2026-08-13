package org.rhythmeta.maimaid.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.CommunityAliasDailyQuota
import org.rhythmeta.maimaid.core.data.CommunityAliasDuplicateReason
import org.rhythmeta.maimaid.core.data.CommunityAliasMyCandidate
import org.rhythmeta.maimaid.core.data.CommunityAliasSubmitStatus

enum class CommunityAliasMessage {
    SubmitSuccess,
    DuplicateLxns,
    DuplicateCommunity,
    AdminRejected,
    Duplicate,
    QuotaExceeded,
    LoginRequired,
    InvalidRequest,
    SubmitFailed,
}

data class SongCommunityAliasUiState(
    val isConfigured: Boolean = true,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = true,
    val draft: String = "",
    val isSubmitting: Boolean = false,
    val dailyUsedCount: Int = 0,
    val candidates: List<CommunityAliasMyCandidate> = emptyList(),
    val approvedAliases: List<String> = emptyList(),
    val message: CommunityAliasMessage? = null,
    val errorMessage: String? = null,
)

class SongCommunityAliasViewModel(
    private val songIdentifier: String,
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        SongCommunityAliasUiState(
            isConfigured = container.communityAliasService.isConfigured,
            isAuthenticated = container.communityAliasService.isAuthenticated,
        ),
    )
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun setDraft(value: String) {
        if (value.length <= 64) mutableState.update { it.copy(draft = value) }
    }

    fun refresh() {
        viewModelScope.launch {
            val service = container.communityAliasService
            service.syncApprovedAliasesIfNeeded()
            val configured = service.isConfigured
            val authenticated = service.isAuthenticated
            val approvedAliases = service.approvedAliases.value[songIdentifier].orEmpty()
            mutableState.update {
                it.copy(
                    isConfigured = configured,
                    isAuthenticated = authenticated,
                    isLoading = configured && authenticated,
                    approvedAliases = approvedAliases,
                )
            }
            if (!configured || !authenticated) {
                mutableState.update {
                    it.copy(isLoading = false, dailyUsedCount = 0, candidates = emptyList())
                }
                return@launch
            }
            val candidates = runCatching { service.fetchMySongCandidates(songIdentifier) }
                .getOrDefault(emptyList())
            val count = runCatching { service.fetchDailySubmissionCount() }.getOrDefault(0)
            mutableState.update {
                it.copy(
                    isLoading = false,
                    dailyUsedCount = count.coerceIn(0, CommunityAliasDailyQuota),
                    candidates = candidates,
                    approvedAliases = approvedAliases,
                )
            }
        }
    }

    fun submit() {
        val current = mutableState.value
        val draft = current.draft.trim()
        if (draft.isEmpty() || current.isSubmitting) return
        if (current.dailyUsedCount >= CommunityAliasDailyQuota) {
            mutableState.update { it.copy(message = CommunityAliasMessage.QuotaExceeded) }
            return
        }
        viewModelScope.launch {
            mutableState.update { it.copy(isSubmitting = true, message = null, errorMessage = null) }
            val result = container.communityAliasService.submitAlias(songIdentifier, draft)
            if (result.status == CommunityAliasSubmitStatus.Created) {
                container.communityAliasService.rememberSubmittedAlias(songIdentifier, draft)
            }
            val message = when (result.status) {
                CommunityAliasSubmitStatus.Created -> CommunityAliasMessage.SubmitSuccess
                CommunityAliasSubmitStatus.RejectedDuplicate -> when (result.duplicateReason) {
                    CommunityAliasDuplicateReason.LxnsExisting -> CommunityAliasMessage.DuplicateLxns
                    CommunityAliasDuplicateReason.CommunityExisting -> CommunityAliasMessage.DuplicateCommunity
                    CommunityAliasDuplicateReason.AdminRejectedLocked -> CommunityAliasMessage.AdminRejected
                    null -> CommunityAliasMessage.Duplicate
                }
                CommunityAliasSubmitStatus.QuotaExceeded -> CommunityAliasMessage.QuotaExceeded
                CommunityAliasSubmitStatus.Unauthenticated -> CommunityAliasMessage.LoginRequired
                CommunityAliasSubmitStatus.InvalidRequest -> CommunityAliasMessage.InvalidRequest
                CommunityAliasSubmitStatus.Error -> CommunityAliasMessage.SubmitFailed
            }
            mutableState.update { state ->
                state.copy(
                    draft = if (result.status == CommunityAliasSubmitStatus.Created) "" else state.draft,
                    isSubmitting = false,
                    dailyUsedCount = result.quotaRemaining?.let {
                        CommunityAliasDailyQuota - it
                    }?.coerceIn(0, CommunityAliasDailyQuota) ?: state.dailyUsedCount,
                    message = message,
                    errorMessage = result.message.takeIf(String::isNotBlank),
                )
            }
            refresh()
        }
    }

    fun consumeMessage() {
        mutableState.update { it.copy(message = null, errorMessage = null) }
    }

    class Factory(
        private val songIdentifier: String,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SongCommunityAliasViewModel::class.java))
            return SongCommunityAliasViewModel(songIdentifier, container) as T
        }
    }
}
