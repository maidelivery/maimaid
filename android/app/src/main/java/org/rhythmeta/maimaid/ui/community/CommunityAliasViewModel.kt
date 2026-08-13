package org.rhythmeta.maimaid.ui.community

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.CommunityAliasVotingBoardItem

data class CommunityAliasBoardUiState(
    val isConfigured: Boolean = true,
    val isAuthenticated: Boolean = false,
    val isLoading: Boolean = true,
    val items: List<CommunityAliasVotingBoardItem> = emptyList(),
    val inFlightCandidateId: String? = null,
    val message: String? = null,
)

class CommunityAliasBoardViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val mutableState = MutableStateFlow(
        CommunityAliasBoardUiState(
            isConfigured = container.communityAliasService.isConfigured,
            isAuthenticated = container.communityAliasService.isAuthenticated,
        ),
    )
    val state = mutableState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val service = container.communityAliasService
            val configured = service.isConfigured
            val authenticated = service.isAuthenticated
            mutableState.update {
                it.copy(
                    isConfigured = configured,
                    isAuthenticated = authenticated,
                    isLoading = configured && authenticated,
                    message = null,
                )
            }
            if (!configured || !authenticated) {
                mutableState.update { it.copy(isLoading = false, items = emptyList()) }
                return@launch
            }
            runCatching { service.fetchVotingBoard() }
                .onSuccess { items ->
                    mutableState.update { it.copy(isLoading = false, items = items) }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(isLoading = false, message = error.message.orEmpty())
                    }
                }
        }
    }

    fun checkSession() {
        viewModelScope.launch {
            container.backendSessionManager.checkSession()
            refresh()
        }
    }

    fun vote(candidateId: String, support: Boolean) {
        if (mutableState.value.inFlightCandidateId != null) return
        viewModelScope.launch {
            mutableState.update { it.copy(inFlightCandidateId = candidateId, message = null) }
            runCatching { container.communityAliasService.vote(candidateId, support) }
                .onSuccess { result ->
                    mutableState.update { current ->
                        current.copy(
                            inFlightCandidateId = null,
                            message = "",
                            items = current.items.map { item ->
                                if (item.candidateId == result.candidateId) {
                                    item.copy(
                                        supportCount = result.supportCount,
                                        opposeCount = result.opposeCount,
                                        myVote = result.myVote,
                                    )
                                } else {
                                    item
                                }
                            },
                        )
                    }
                }
                .onFailure { error ->
                    mutableState.update {
                        it.copy(inFlightCandidateId = null, message = error.message.orEmpty())
                    }
                }
        }
    }

    fun consumeMessage() {
        mutableState.update { it.copy(message = null) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(CommunityAliasBoardViewModel::class.java))
            return CommunityAliasBoardViewModel(container) as T
        }
    }
}
