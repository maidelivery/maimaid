package org.rhythmeta.maimaid.ui.recommendation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.RecommendationResponse

data class RecommendationUiState(
    val isLoading: Boolean = true,
    val response: RecommendationResponse = RecommendationResponse(),
    val refreshGeneration: Int = 0,
)

class RecommendationViewModel(
    container: AppContainer,
) : ViewModel() {
    private val refreshToken = MutableStateFlow(0)

    val state = container.recommendationRepository
        .observeRecommendations(refreshToken)
        .map {
            RecommendationUiState(
                isLoading = false,
                response = it,
                refreshGeneration = refreshToken.value,
            )
        }
        .onStart { emit(RecommendationUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RecommendationUiState(),
        )

    fun refresh() {
        refreshToken.update { it + 1 }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(RecommendationViewModel::class.java))
            return RecommendationViewModel(container) as T
        }
    }
}
