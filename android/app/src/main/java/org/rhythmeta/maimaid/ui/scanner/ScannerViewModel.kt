package org.rhythmeta.maimaid.ui.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ScoreInput
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongAliasEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.ml.RecognizedRegion
import org.rhythmeta.maimaid.core.ml.ScannerCatalog
import org.rhythmeta.maimaid.core.ml.ScannerMatch
import org.rhythmeta.maimaid.core.ml.ScannerRecognitionEngine
import org.rhythmeta.maimaid.core.ml.ScannerSongMatcher
import org.rhythmeta.maimaid.core.ml.ScannerStabilizer
import org.rhythmeta.maimaid.ui.song.ScoreSaveStatus

data class ScannerUiState(
    val isLoadingModels: Boolean = true,
    val isProcessingPhoto: Boolean = false,
    val match: ScannerMatch? = null,
    val regions: List<RecognizedRegion> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
    val scoreEntryVisible: Boolean = false,
    val scoreSaveStatus: ScoreSaveStatus = ScoreSaveStatus.Idle,
    val message: ScannerMessage? = null,
)

enum class ScannerMessage {
    LoadFailed,
    RecognitionFailed,
    PhotoSaved,
    PhotoSaveFailed,
}

class ScannerViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private val engine = ScannerRecognitionEngine(container.applicationContext, container.onnxSessionFactory)
    private val stabilizer = ScannerStabilizer()
    private val analysisMutex = Mutex()
    private var photoResultDismissJob: Job? = null
    @Volatile
    private var liveRecognitionEnabled = true
    private var catalog = ScannerCatalog(emptyList(), emptyList(), emptyMap())
    private val mutableState = MutableStateFlow(ScannerUiState())
    val state = mutableState.asStateFlow()

    fun updateCatalog(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        aliases: List<SongAliasEntity>,
    ) {
        catalog = ScannerCatalog(
            songs = songs,
            sheets = sheets,
            aliasesBySong = aliases.groupBy(SongAliasEntity::songIdentifier)
                .mapValues { (_, values) -> values.map(SongAliasEntity::alias) },
        )
    }

    fun analyzeLiveFrame(bitmap: Bitmap) {
        if (!liveRecognitionEnabled ||
            mutableState.value.isProcessingPhoto ||
            mutableState.value.scoreEntryVisible
        ) {
            bitmap.recycle()
            return
        }
        if (!analysisMutex.tryLock()) {
            bitmap.recycle()
            return
        }
        viewModelScope.launch {
            try {
                val raw = withContext(Dispatchers.Default) { engine.recognize(bitmap) }
                val matches = withContext(Dispatchers.Default) { ScannerSongMatcher.matchFast(raw, catalog) }
                val stabilized = stabilizer.update(matches, raw)
                if (!liveRecognitionEnabled ||
                    mutableState.value.isProcessingPhoto ||
                    mutableState.value.scoreEntryVisible
                ) {
                    return@launch
                }
                mutableState.update {
                    it.copy(
                        isLoadingModels = false,
                        match = stabilized.match,
                        regions = stabilized.regions,
                        imageWidth = raw.imageWidth,
                        imageHeight = raw.imageHeight,
                    )
                }
            } catch (_: Exception) {
                mutableState.update { it.copy(isLoadingModels = false, message = ScannerMessage.LoadFailed) }
            } finally {
                bitmap.recycle()
                analysisMutex.unlock()
            }
        }
    }

    fun analyzePhoto(bitmap: Bitmap) {
        analyzePhotoWhenReady(bitmap)
    }

    fun analyzePhotoWhenReady(bitmap: Bitmap) {
        photoResultDismissJob?.cancel()
        liveRecognitionEnabled = false
        mutableState.update { it.copy(isProcessingPhoto = true, message = null) }
        viewModelScope.launch {
            analysisMutex.withLock {
                analyzePhotoLocked(bitmap)
            }
        }
    }

    private suspend fun analyzePhotoLocked(bitmap: Bitmap) {
        try {
            val raw = withContext(Dispatchers.Default) { engine.recognize(bitmap) }
            val match = withContext(Dispatchers.Default) {
                ScannerSongMatcher.match(raw, catalog).firstOrNull { candidate ->
                    raw.screenType != org.rhythmeta.maimaid.core.ml.MaimaiScreenType.Score ||
                        raw.difficulty.isNullOrBlank() || candidate.sheet != null
                }
            }
            if (match != null) {
                stabilizer.seed(match)
            } else {
                stabilizer.reset()
            }
            mutableState.update {
                it.copy(
                    isLoadingModels = false,
                    isProcessingPhoto = false,
                    match = match,
                    regions = raw.regions,
                    imageWidth = raw.imageWidth,
                    imageHeight = raw.imageHeight,
                    message = ScannerMessage.RecognitionFailed.takeIf { match == null },
                )
            }
            if (match == null) {
                liveRecognitionEnabled = true
            } else {
                schedulePhotoResultDismissal()
            }
        } catch (_: Exception) {
            mutableState.update {
                it.copy(
                    isLoadingModels = false,
                    isProcessingPhoto = false,
                    message = ScannerMessage.RecognitionFailed,
                )
            }
            liveRecognitionEnabled = true
        } finally {
            bitmap.recycle()
        }
    }

    fun reset() {
        photoResultDismissJob?.cancel()
        photoResultDismissJob = null
        clearResultAndResumeLiveRecognition()
    }

    private fun clearResultAndResumeLiveRecognition() {
        stabilizer.reset()
        mutableState.update { ScannerUiState(isLoadingModels = false) }
        liveRecognitionEnabled = true
    }

    private fun schedulePhotoResultDismissal() {
        photoResultDismissJob = viewModelScope.launch {
            delay(PhotoResultDurationMillis)
            photoResultDismissJob = null
            clearResultAndResumeLiveRecognition()
        }
    }

    fun openScoreEntry() {
        if (mutableState.value.match?.sheet == null) return
        photoResultDismissJob?.cancel()
        photoResultDismissJob = null
        mutableState.update {
            it.copy(scoreEntryVisible = true, scoreSaveStatus = ScoreSaveStatus.Idle)
        }
    }

    fun dismissScoreEntry() {
        mutableState.update {
            it.copy(scoreEntryVisible = false, scoreSaveStatus = ScoreSaveStatus.Idle)
        }
        reset()
    }

    fun markScoreEntryChanged() {
        if (mutableState.value.scoreSaveStatus in setOf(ScoreSaveStatus.Saved, ScoreSaveStatus.Failed)) {
            mutableState.update { it.copy(scoreSaveStatus = ScoreSaveStatus.Idle) }
        }
    }

    fun saveScore(input: ScoreInput) {
        val sheetKey = mutableState.value.match?.sheet?.sheetKey ?: return
        if (mutableState.value.scoreSaveStatus == ScoreSaveStatus.Saving) return
        viewModelScope.launch {
            mutableState.update { it.copy(scoreSaveStatus = ScoreSaveStatus.Saving) }
            val status = runCatching { container.scoreRepository.saveScore(sheetKey, input) }
                .fold(
                    onSuccess = { ScoreSaveStatus.Saved },
                    onFailure = { ScoreSaveStatus.Failed },
                )
            mutableState.update { it.copy(scoreSaveStatus = status) }
        }
    }

    fun showMessage(message: ScannerMessage) {
        mutableState.update { it.copy(message = message) }
    }

    fun consumeMessage() {
        mutableState.update { it.copy(message = null) }
    }

    override fun onCleared() {
        photoResultDismissJob?.cancel()
        engine.close()
    }

    class Factory(
        private val container: AppContainer,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            require(modelClass.isAssignableFrom(ScannerViewModel::class.java))
            return ScannerViewModel(container) as T
        }
    }

    private companion object {
        const val PhotoResultDurationMillis = 5_000L
    }
}
