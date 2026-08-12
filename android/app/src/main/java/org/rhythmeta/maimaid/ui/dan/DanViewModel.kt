package org.rhythmeta.maimaid.ui.dan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.DanCategoryDetail
import org.rhythmeta.maimaid.core.data.DanCategoryGroup

data class DanListUiState(
    val isLoading: Boolean = true,
    val groups: List<DanCategoryGroup> = emptyList(),
)

class DanListViewModel(
    container: AppContainer,
    unknownLabel: String,
) : ViewModel() {
    val state = container.danRepository.observeGroups(unknownLabel)
        .map { DanListUiState(isLoading = false, groups = it) }
        .onStart { emit(DanListUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DanListUiState(),
        )

    class Factory(
        private val container: AppContainer,
        private val unknownLabel: String,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DanListViewModel::class.java))
            return DanListViewModel(container, unknownLabel) as T
        }
    }
}

data class DanDetailUiState(
    val isLoading: Boolean = true,
    val detail: DanCategoryDetail? = null,
)

class DanDetailViewModel(
    categoryId: String,
    container: AppContainer,
) : ViewModel() {
    val state = container.danRepository.observeCategory(categoryId)
        .map { DanDetailUiState(isLoading = false, detail = it) }
        .onStart { emit(DanDetailUiState()) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = DanDetailUiState(),
        )

    class Factory(
        private val categoryId: String,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(DanDetailViewModel::class.java))
            return DanDetailViewModel(categoryId, container) as T
        }
    }
}
