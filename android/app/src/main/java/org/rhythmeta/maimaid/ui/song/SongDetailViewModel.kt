package org.rhythmeta.maimaid.ui.song

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ScoreInput
import org.rhythmeta.maimaid.core.data.ServerChartPolicy
import org.rhythmeta.maimaid.core.data.StaticBundleResponse
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.ui.util.SongVisualUtils

class SongDetailViewModel(
    private val songIdentifier: String,
    private val container: AppContainer,
) : ViewModel() {
    private val entrySheetKey = MutableStateFlow<String?>(null)
    private val saveStatus = MutableStateFlow(ScoreSaveStatus.Idle)

    private val sheetsWithServer = combine(
        container.catalogRepository.observeSheetsForSong(songIdentifier),
        container.profileRepository.activeProfile,
    ) { sheets, profile ->
        sheets to (profile?.server ?: "jp")
    }

    val uiState = combine(
        sheetsWithServer,
        container.scoreRepository.observeSongScoreData(songIdentifier),
        container.catalogRepository.chartFit,
        entrySheetKey,
        saveStatus,
    ) { (sheets, server), scoreData, chartFit, selectedSheetKey, status ->
        val scoresBySheet = scoreData.scores.associateBy { it.sheetKey }
        val historyBySheet = scoreData.playRecords.groupBy { it.sheetKey }
        SongDetailUiState(
            profileId = scoreData.profileId,
            charts = sheets
                .sortedWith(
                    compareByDescending<org.rhythmeta.maimaid.core.database.SheetEntity> {
                        chartTypeOrder(it.type)
                    }.thenByDescending { SongVisualUtils.difficultyOrder(it.difficulty) },
                )
                .map { sheet ->
                    SheetScoreUiState(
                        sheet = sheet,
                        score = scoresBySheet[sheet.sheetKey],
                        history = historyBySheet[sheet.sheetKey].orEmpty(),
                        chartFit = chartFit.findFor(sheet),
                        resolvedMetadata = ServerChartPolicy.metadata(sheet, server),
                    )
                },
            entrySheetKey = selectedSheetKey,
            saveStatus = status,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SongDetailUiState(),
    )

    fun openScoreEntry(sheetKey: String) {
        entrySheetKey.value = sheetKey
        saveStatus.value = ScoreSaveStatus.Idle
    }

    fun dismissScoreEntry() {
        entrySheetKey.value = null
        saveStatus.value = ScoreSaveStatus.Idle
    }

    fun markEntryChanged() {
        if (saveStatus.value == ScoreSaveStatus.Saved || saveStatus.value == ScoreSaveStatus.Failed) {
            saveStatus.value = ScoreSaveStatus.Idle
        }
    }

    fun saveScore(input: ScoreInput) {
        val sheetKey = entrySheetKey.value ?: return
        if (saveStatus.value == ScoreSaveStatus.Saving) return
        viewModelScope.launch {
            saveStatus.value = ScoreSaveStatus.Saving
            val result = runCatching {
                container.scoreRepository.saveScore(sheetKey, input)
            }
            result.onSuccess { score ->
                launch { container.scoreSyncService.syncAfterScoreSave(sheetKey, score) }
            }
            saveStatus.value = if (result.isSuccess) ScoreSaveStatus.Saved else ScoreSaveStatus.Failed
        }
    }

    fun deletePlayRecord(recordId: String) {
        viewModelScope.launch {
            container.scoreRepository.deletePlayRecord(recordId)
        }
    }

    class Factory(
        private val songIdentifier: String,
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(SongDetailViewModel::class.java))
            return SongDetailViewModel(songIdentifier, container) as T
        }
    }

    private companion object {
        fun StaticBundleResponse.ChartFitPayload.findFor(
            sheet: SheetEntity,
        ): StaticBundleResponse.ChartFitStat? {
            val providerSongId = sheet.providerSongId.takeIf { it > 0 } ?: return null
            val candidateIds = buildList {
                if (sheet.type.equals("dx", ignoreCase = true) && providerSongId < 10_000) {
                    add(providerSongId + 10_000)
                }
                add(providerSongId)
                if (sheet.type.equals("dx", ignoreCase = true) && providerSongId >= 10_000) {
                    add(providerSongId - 10_000)
                }
            }.distinct()
            return candidateIds.firstNotNullOfOrNull { id ->
                charts[id.toString()]?.firstOrNull { it.diff == sheet.level }
            }
        }

        fun chartTypeOrder(type: String): Int = when (type.lowercase()) {
            "dx" -> 3
            "std", "standard" -> 2
            "utage" -> 1
            else -> 0
        }
    }
}
