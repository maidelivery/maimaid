package org.rhythmeta.maimaid.ui.constanttable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ConstantTableResponse
import org.rhythmeta.maimaid.core.data.ConstantTableSection

data class ConstantTableUiState(
    val isLoading: Boolean = true,
    val response: ConstantTableResponse = ConstantTableResponse(),
    val selectedBaseLevel: Int? = null,
    val includeScores: Boolean = false,
    val sections: List<ConstantTableSection> = emptyList(),
) {
    val chartCount: Int get() = sections.sumOf { it.entries.size }
}

class ConstantTableViewModel(
    container: AppContainer,
) : ViewModel() {
    private val selectedBaseLevel = MutableStateFlow<Int?>(null)
    private val includeScores = MutableStateFlow(false)

    val state = combine(
        container.constantTableRepository.observeConstantTable()
            .map<ConstantTableResponse, ConstantTableResponse?> { it }
            .onStart { emit(null) },
        selectedBaseLevel,
        includeScores,
    ) { response, requestedBaseLevel, scoresIncluded ->
        if (response == null) {
            ConstantTableUiState(includeScores = scoresIncluded)
        } else {
            val levels = response.availableBaseLevels
            val resolvedBaseLevel = requestedBaseLevel
                ?.takeIf(levels::contains)
                ?: 14.takeIf(levels::contains)
                ?: levels.firstOrNull()
            ConstantTableUiState(
                isLoading = false,
                response = response,
                selectedBaseLevel = resolvedBaseLevel,
                includeScores = scoresIncluded,
                sections = resolvedBaseLevel?.let(response::sections).orEmpty(),
            )
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ConstantTableUiState(),
    )

    fun selectBaseLevel(baseLevel: Int) {
        selectedBaseLevel.value = baseLevel
    }

    fun setIncludeScores(include: Boolean) {
        includeScores.value = include
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ConstantTableViewModel::class.java))
            return ConstantTableViewModel(container) as T
        }
    }
}
