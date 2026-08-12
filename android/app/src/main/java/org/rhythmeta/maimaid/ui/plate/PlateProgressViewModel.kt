package org.rhythmeta.maimaid.ui.plate

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.PlateProgressResponse
import org.rhythmeta.maimaid.core.data.PlateProgressSelection
import org.rhythmeta.maimaid.core.data.PlateType

data class PlateProgressUiState(
    val isLoading: Boolean = true,
    val response: PlateProgressResponse = PlateProgressResponse(),
)

class PlateProgressViewModel(
    container: AppContainer,
) : ViewModel() {
    private val selection = MutableStateFlow(PlateProgressSelection())

    val state = container.plateProgressRepository.observePlateProgress(selection)
        .onEach { response ->
            selection.update { current ->
                current.copy(
                    groupId = response.selectedGroup?.id ?: current.groupId,
                    difficulty = response.difficulty,
                    plateType = response.plateType,
                )
            }
        }
        .map { PlateProgressUiState(isLoading = false, response = it) }
        .onStart { emit(PlateProgressUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PlateProgressUiState(),
        )

    fun selectGroup(groupId: String) {
        selection.update { it.copy(groupId = groupId) }
    }

    fun selectDifficulty(difficulty: String) {
        selection.update { it.copy(difficulty = difficulty.lowercase()) }
    }

    fun selectPlateType(plateType: PlateType) {
        selection.update { it.copy(plateType = plateType) }
    }

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(PlateProgressViewModel::class.java))
            return PlateProgressViewModel(container) as T
        }
    }
}
