package org.rhythmeta.maimaid.ui.scanner

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
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
import org.rhythmeta.maimaid.core.ml.ModelAvailability
import org.rhythmeta.maimaid.core.ml.ScannerCatalog
import org.rhythmeta.maimaid.core.ml.ScannerMatch
import org.rhythmeta.maimaid.core.ml.ScannerRecognitionEngine
import org.rhythmeta.maimaid.core.ml.ScannerSongMatcher
import org.rhythmeta.maimaid.core.ml.ScannerStabilizer
import org.rhythmeta.maimaid.ui.song.ScoreSaveStatus

data class ScannerUiState(
    val modelState: ScannerModelState = ScannerModelState.Checking(),
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
    OfflineModels,
}

class ScannerViewModel(
    private val container: AppContainer,
) : ViewModel() {
    private var engine = ScannerRecognitionEngine(container.onnxSessionFactory)
    private val stabilizer = ScannerStabilizer()
    private val analysisMutex = Mutex()
    private var modelTask: Job? = null
    private var photoResultDismissJob: Job? = null
    @Volatile
    private var liveRecognitionEnabled = true
    private var catalog = ScannerCatalog(emptyList(), emptyList(), emptyMap())
    private val mutableState = MutableStateFlow(ScannerUiState())
    val state = mutableState.asStateFlow()

    init {
        checkModels()
    }

    fun checkModels() {
        modelTask?.cancel()
        modelTask = viewModelScope.launch {
            val cachedModelsAvailable = canRecognize(mutableState.value.modelState) ||
                container.remoteModelStore.hasUsableCachedModels()
            if (!cachedModelsAvailable) liveRecognitionEnabled = false
            mutableState.update {
                it.copy(modelState = ScannerModelState.Checking(cachedModelsAvailable))
            }
            when (val availability = container.remoteModelStore.inspect()) {
                is ModelAvailability.Ready -> {
                    liveRecognitionEnabled = true
                    mutableState.update {
                        it.copy(
                            modelState = ScannerModelState.Ready(availability.offline),
                            isLoadingModels = false,
                            message = ScannerMessage.OfflineModels.takeIf { availability.offline },
                        )
                    }
                }
                is ModelAvailability.DownloadRequired -> {
                    liveRecognitionEnabled = false
                    mutableState.update {
                        it.copy(
                            modelState = ScannerModelState.DownloadRequired(availability.totalBytes),
                            isLoadingModels = false,
                        )
                    }
                }
                is ModelAvailability.UpdateAvailable -> {
                    liveRecognitionEnabled = true
                    mutableState.update {
                        it.copy(
                            modelState = ScannerModelState.UpdateAvailable(availability.totalBytes),
                            isLoadingModels = false,
                        )
                    }
                }
                is ModelAvailability.Failed -> {
                    liveRecognitionEnabled = cachedModelsAvailable
                    mutableState.update {
                        it.copy(
                            modelState = ScannerModelState.Failed(
                                availability.message,
                                cachedModelsAvailable,
                            ),
                            isLoadingModels = false,
                        )
                    }
                }
            }
        }
    }

    fun downloadModels() {
        val previous = mutableState.value.modelState
        val isUpdate = previous is ScannerModelState.UpdateAvailable ||
            (previous is ScannerModelState.Failed && previous.cachedModelsAvailable)
        modelTask?.cancel()
        liveRecognitionEnabled = isUpdate
        modelTask = viewModelScope.launch {
            try {
                container.remoteModelStore.downloadPending { progress ->
                    mutableState.update {
                        it.copy(modelState = ScannerModelState.Downloading(progress, isUpdate))
                    }
                }
                analysisMutex.withLock {
                    engine.close()
                    engine = ScannerRecognitionEngine(container.onnxSessionFactory)
                    stabilizer.reset()
                }
                mutableState.update {
                    it.copy(
                        modelState = ScannerModelState.Ready(),
                        isLoadingModels = false,
                        match = null,
                        regions = emptyList(),
                    )
                }
                liveRecognitionEnabled = true
            } catch (_: CancellationException) {
                mutableState.update { it.copy(modelState = previous) }
                liveRecognitionEnabled = canRecognize(previous)
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        modelState = ScannerModelState.Failed(
                            message = error.message ?: error::class.java.simpleName,
                            cachedModelsAvailable = isUpdate,
                        ),
                        isLoadingModels = false,
                    )
                }
                liveRecognitionEnabled = isUpdate
            }
        }
    }

    fun cancelModelDownload() {
        modelTask?.cancel()
    }

    fun updateCatalog(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        aliases: List<SongAliasEntity>,
        server: String,
    ) {
        catalog = ScannerCatalog(
            songs = songs,
            sheets = sheets,
            aliasesBySong = aliases.groupBy(SongAliasEntity::songIdentifier)
                .mapValues { (_, values) -> values.map(SongAliasEntity::alias) },
            server = server,
        )
    }

    fun analyzeLiveFrame(bitmap: Bitmap) {
        if (!liveRecognitionEnabled || !canRecognize(mutableState.value.modelState) ||
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
            } catch (error: Exception) {
                handleRuntimeModelFailure(error)
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
        if (!canRecognize(mutableState.value.modelState)) {
            bitmap.recycle()
            return
        }
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
        } catch (error: Exception) {
            handleRuntimeModelFailure(error)
            mutableState.update {
                it.copy(
                    isLoadingModels = false,
                    isProcessingPhoto = false,
                    message = ScannerMessage.RecognitionFailed,
                )
            }
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun handleRuntimeModelFailure(error: Exception) {
        val currentModelState = mutableState.value.modelState
        if (currentModelState is ScannerModelState.Downloading && currentModelState.isUpdate) {
            liveRecognitionEnabled = false
            mutableState.update { it.copy(message = ScannerMessage.LoadFailed) }
            return
        }
        liveRecognitionEnabled = false
        container.remoteModelStore.invalidateActiveModels()
        engine.close()
        engine = ScannerRecognitionEngine(container.onnxSessionFactory)
        stabilizer.reset()
        mutableState.update {
            it.copy(
                modelState = ScannerModelState.Failed(
                    message = error.message ?: error::class.java.simpleName,
                    cachedModelsAvailable = false,
                ),
                isLoadingModels = false,
                message = ScannerMessage.LoadFailed,
            )
        }
    }

    fun reset() {
        photoResultDismissJob?.cancel()
        photoResultDismissJob = null
        clearResultAndResumeLiveRecognition()
    }

    private fun clearResultAndResumeLiveRecognition() {
        stabilizer.reset()
        mutableState.update {
            ScannerUiState(
                modelState = it.modelState,
                isLoadingModels = false,
            )
        }
        liveRecognitionEnabled = canRecognize(mutableState.value.modelState)
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

    fun saveScore(input: ScoreInput, maxDxScoreOverride: Int? = null) {
        val sheetKey = mutableState.value.match?.sheet?.sheetKey ?: return
        if (mutableState.value.scoreSaveStatus == ScoreSaveStatus.Saving) return
        viewModelScope.launch {
            mutableState.update { it.copy(scoreSaveStatus = ScoreSaveStatus.Saving) }
            val result = runCatching {
                container.scoreRepository.saveScore(sheetKey, input, maxDxScoreOverride)
            }
            result.onSuccess { score ->
                launch { container.scoreSyncService.syncAfterScoreSave(sheetKey, score) }
            }
            val status = if (result.isSuccess) ScoreSaveStatus.Saved else ScoreSaveStatus.Failed
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
        modelTask?.cancel()
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

        fun canRecognize(state: ScannerModelState): Boolean = when (state) {
            is ScannerModelState.Ready,
            is ScannerModelState.UpdateAvailable,
            -> true
            is ScannerModelState.Checking -> state.cachedModelsAvailable
            is ScannerModelState.Downloading -> state.isUpdate
            is ScannerModelState.Failed -> state.cachedModelsAvailable
            is ScannerModelState.DownloadRequired,
            -> false
        }
    }
}
