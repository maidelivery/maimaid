package net.krtl.maimaid.ui.scanner

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.krtl.maimaid.R
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.domain.model.UserProfile
import net.krtl.maimaid.domain.repository.PreferencesRepository
import net.krtl.maimaid.domain.repository.ProfileRepository
import net.krtl.maimaid.domain.repository.ScoreRepository
import net.krtl.maimaid.domain.repository.StaticDataRepository
import net.krtl.maimaid.scanner.analysis.ScannerAnalyzer
import net.krtl.maimaid.scanner.analysis.ScannerResultStabilizer
import net.krtl.maimaid.scanner.matching.ScannerSongMatcher
import net.krtl.maimaid.scanner.model.ScannerImageType
import net.krtl.maimaid.scanner.model.ScannerMatch
import net.krtl.maimaid.scanner.model.ScannerRecognition

data class ScannerUiState(
    val isAnalyzerReady: Boolean = false,
    val isAnalyzing: Boolean = false,
    val songsLoaded: Boolean = false,
    val activeProfile: UserProfile? = null,
    val stableMatch: ScannerMatch? = null,
    val reviewDraft: ScannerReviewDraft? = null,
    val isReviewOpen: Boolean = false,
    val lastRecognition: ScannerRecognition? = null,
    val statusMessageRes: Int = R.string.scanner_status_point_camera,
    val statusDetail: String? = null,
    val debugText: String = "",
    val saveInProgress: Boolean = false,
    val showModelRegions: Boolean = false
)

data class ScannerReviewDraft(
    val recognition: ScannerRecognition,
    val candidates: List<ScannerReviewCandidate>,
    val selectedSheetId: String?,
    val openedFromPhoto: Boolean = false
)

data class ScannerReviewCandidate(
    val song: Song,
    val sheet: Sheet
)

sealed interface ScannerEvent {
    data class Toast(val messageRes: Int, val detail: String? = null) : ScannerEvent
    data class OpenSong(val songIdentifier: String) : ScannerEvent
}

class ScannerViewModel(
    context: Context,
    private val staticDataRepository: StaticDataRepository,
    private val profileRepository: ProfileRepository,
    private val scoreRepository: ScoreRepository,
    private val preferencesRepository: PreferencesRepository,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) : ViewModel() {
    private val analyzer = ScannerAnalyzer(context.applicationContext, dispatcher)
    private val _uiState = MutableStateFlow(ScannerUiState(isAnalyzerReady = true))
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<ScannerEvent>(replay = 0)
    val events: SharedFlow<ScannerEvent> = _events.asSharedFlow()

    private var matcher: ScannerSongMatcher = ScannerSongMatcher(emptyList())
    private var pendingBitmap: Bitmap? = null
    private var analysisJob: Job? = null
    private val stabilizer = ScannerResultStabilizer()

    private companion object {
        const val TAG = "MaimaiScanner"
    }

    init {
        viewModelScope.launch {
            staticDataRepository.observeSongs().collect { songs ->
                matcher = ScannerSongMatcher(songs)
                _uiState.update { it.copy(songsLoaded = songs.isNotEmpty()) }
            }
        }
        viewModelScope.launch {
            profileRepository.observeActiveProfile().collect { profile ->
                _uiState.update { it.copy(activeProfile = profile) }
            }
        }
        viewModelScope.launch {
            preferencesRepository.preferences.collect { preferences ->
                _uiState.update { it.copy(showModelRegions = preferences.showScannerBoundingBox) }
            }
        }
    }

    fun onCameraBitmap(bitmap: Bitmap) {
        viewModelScope.launch {
            pendingBitmap = bitmap
            if (analysisJob?.isActive == true) return@launch
            analysisJob = launch { consumePendingFrames() }
        }
    }

    fun analyzePhoto(bitmap: Bitmap) {
        viewModelScope.launch {
            pendingBitmap = null
            analysisJob?.cancel()
            _uiState.update {
                it.copy(
                    isAnalyzing = true,
                    stableMatch = null,
                    reviewDraft = null,
                    isReviewOpen = false,
                    statusMessageRes = R.string.scanner_status_processing_photo,
                    statusDetail = null
                )
            }
            try {
                analyzeBitmap(bitmap, forceStable = true)
            } finally {
                _uiState.update { it.copy(isAnalyzing = false) }
                analysisJob = null
            }
        }
    }

    fun onResultTapped(match: ScannerMatch) {
        if (match.recognition.imageType == ScannerImageType.CHOOSE) {
            viewModelScope.launch {
                _events.emit(ScannerEvent.OpenSong(match.song.songIdentifier))
            }
        } else if (match.sheet != null) {
            openReview(match.recognition)
        }
    }

    fun openReview(recognition: ScannerRecognition? = _uiState.value.lastRecognition) {
        recognition ?: return
        val draft = buildReviewDraft(recognition, openedFromPhoto = false) ?: return
        _uiState.update { it.copy(reviewDraft = draft, isReviewOpen = true) }
    }

    fun closeReview() {
        _uiState.update { it.copy(isReviewOpen = false) }
    }

    fun selectReviewSheet(sheetId: String) {
        _uiState.update { state ->
            val draft = state.reviewDraft ?: return@update state
            state.copy(reviewDraft = draft.copy(selectedSheetId = sheetId))
        }
    }

    fun reset() {
        pendingBitmap = null
        stabilizer.reset()
        _uiState.update {
            it.copy(
                stableMatch = null,
                reviewDraft = null,
                isReviewOpen = false,
                lastRecognition = null,
                statusMessageRes = R.string.scanner_status_point_camera,
                statusDetail = null,
                debugText = ""
            )
        }
    }

    fun saveScore(match: ScannerMatch, rate: Double, dxScore: Int, fc: String?, fs: String?) {
        val sheet = match.sheet ?: return
        saveScore(sheet, rate, dxScore, fc, fs)
    }

    fun saveReviewScore(rate: Double, dxScore: Int, fc: String?, fs: String?) {
        val draft = _uiState.value.reviewDraft ?: return
        val sheet = draft.candidates.firstOrNull { it.sheet.sheetId == draft.selectedSheetId }?.sheet ?: return
        saveScore(sheet, rate, dxScore, fc, fs)
    }

    private fun saveScore(sheet: Sheet, rate: Double, dxScore: Int, fc: String?, fs: String?) {
        val profile = _uiState.value.activeProfile
        if (profile == null) {
            viewModelScope.launch { _events.emit(ScannerEvent.Toast(R.string.scanner_error_no_active_profile)) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(saveInProgress = true) }
            try {
                scoreRepository.saveScore(
                    sheetId = sheet.sheetId,
                    userProfileId = profile.id,
                    rate = rate,
                    dxScore = dxScore,
                    fc = fc,
                    fs = fs
                )
                _events.emit(ScannerEvent.Toast(R.string.scanner_score_saved))
                reset()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.emit(ScannerEvent.Toast(R.string.scanner_error_save_failed))
            } finally {
                _uiState.update { it.copy(saveInProgress = false) }
            }
        }
    }

    private suspend fun consumePendingFrames() {
        _uiState.update { it.copy(isAnalyzing = true) }
        try {
            while (true) {
                val bitmap = pendingBitmap ?: break
                pendingBitmap = null
                analyzeBitmap(bitmap, forceStable = false)
            }
        } finally {
            _uiState.update { it.copy(isAnalyzing = false) }
            analysisJob = null
        }
    }

    private suspend fun analyzeBitmap(bitmap: Bitmap, forceStable: Boolean) {
        val recognition = try {
            analyzer.analyze(bitmap)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "analyzeBitmap failed", e)
            _uiState.update {
                it.copy(
                    statusMessageRes = R.string.scanner_status_analyzer_failed,
                    statusDetail = e.message,
                    debugText = e.stackTraceToString()
                )
            }
            return
        }

        val match = matcher.match(recognition)
        updateStabilizedResult(recognition, match, forceStable)
    }

    private fun updateStabilizedResult(recognition: ScannerRecognition, match: ScannerMatch?, forceStable: Boolean) {
        val now = System.currentTimeMillis()
        val stabilized = stabilizer.update(
            recognition = recognition,
            match = match,
            nowMillis = now,
            forceStable = forceStable
        )

        _uiState.update {
            val draft = if (recognition.imageType == ScannerImageType.SCORE) {
                buildReviewDraft(recognition, openedFromPhoto = forceStable)
            } else {
                null
            }
            val shouldReplaceDraft = forceStable || !it.isReviewOpen
            it.copy(
                stableMatch = stabilized.stableMatch,
                reviewDraft = if (shouldReplaceDraft) draft ?: it.reviewDraft else it.reviewDraft,
                isReviewOpen = if (forceStable && draft != null) true else it.isReviewOpen,
                lastRecognition = recognition,
                statusMessageRes = recognitionStatus(recognition, stabilized.stableMatch),
                statusDetail = recognitionDetail(recognition, match, stabilized.topCandidateId?.let { id ->
                    id to stabilized.topCandidateScore
                }),
                debugText = buildDebugText(recognition, match, stabilized.topCandidateId, stabilized.topCandidateScore)
            )
        }
        Log.d(TAG, _uiState.value.debugText)
    }

    private fun buildReviewDraft(recognition: ScannerRecognition, openedFromPhoto: Boolean): ScannerReviewDraft? {
        if (recognition.imageType != ScannerImageType.SCORE) return null
        val candidates = matcher.matchScoreCandidates(recognition)
            .flatMap { match ->
                if (match.sheet != null) {
                    listOf(ScannerReviewCandidate(match.song, match.sheet))
                } else {
                    match.song.sheets
                        .filterNot { it.type.equals("utage", ignoreCase = true) }
                        .filter { sheet ->
                            recognition.difficulty?.let { sheet.difficulty.equals(it, ignoreCase = true) } ?: true
                        }
                        .filter { sheet ->
                            recognition.type?.let { sheet.type.equals(it, ignoreCase = true) } ?: true
                        }
                        .map { sheet -> ScannerReviewCandidate(match.song, sheet) }
                }
            }
            .distinctBy { it.sheet.sheetId }
            .take(8)

        if (candidates.isEmpty()) return null
        return ScannerReviewDraft(
            recognition = recognition,
            candidates = candidates,
            selectedSheetId = candidates.first().sheet.sheetId,
            openedFromPhoto = openedFromPhoto
        )
    }

    private fun recognitionStatus(recognition: ScannerRecognition, match: ScannerMatch?): Int {
        if (match != null) {
            return when (recognition.imageType) {
                ScannerImageType.SCORE -> R.string.scanner_status_score_recognized
                ScannerImageType.CHOOSE -> R.string.scanner_status_choose_recognized
                ScannerImageType.UNKNOWN -> R.string.scanner_status_point_camera
            }
        }
        return when (recognition.imageType) {
            ScannerImageType.SCORE -> R.string.scanner_status_matching_score
            ScannerImageType.CHOOSE -> R.string.scanner_status_matching_choose
            ScannerImageType.UNKNOWN -> R.string.scanner_status_point_camera
        }
    }

    private fun recognitionDetail(
        recognition: ScannerRecognition,
        match: ScannerMatch?,
        topCandidate: Pair<String, Int>?
    ): String? {
        if (match != null) {
            return "${match.song.title} (${topCandidate?.second ?: 0}/18)"
        }
        return recognition.titleCandidates
            .take(3)
            .joinToString(" / ")
            .takeIf { it.isNotBlank() }
    }

    private fun buildDebugText(
        recognition: ScannerRecognition,
        match: ScannerMatch?,
        topCandidateId: String?,
        topCandidateScore: Int
    ): String = buildString {
        appendLine("imageType=${recognition.imageType}")
        appendLine("match=${match?.song?.songIdentifier ?: "null"} ${match?.song?.title.orEmpty()}")
        appendLine("sheet=${match?.sheet?.sheetId ?: "null"}")
        appendLine("topBuffer=${topCandidateId ?: "null"}:$topCandidateScore")
        appendLine("title=${recognition.title ?: "null"}")
        appendLine("candidates=${recognition.titleCandidates.joinToString(" | ")}")
        appendLine("rate=${recognition.rate} diff=${recognition.difficulty} type=${recognition.type} level=${recognition.level}")
        appendLine("dx=${recognition.dxScore}/${recognition.maxDxScore} fc=${recognition.fc} fs=${recognition.fs} kanji=${recognition.kanji}")
        appendLine("detections=${recognition.detections.joinToString { "${it.label}@${"%.2f".format(it.score)}[${"%.2f".format(it.left)},${"%.2f".format(it.top)},${"%.2f".format(it.right)},${"%.2f".format(it.bottom)}]" }}")
        if (recognition.debugText.isNotBlank()) {
            appendLine("--- analyzer ---")
            append(recognition.debugText)
        }
    }.trim()

    override fun onCleared() {
        analyzer.close()
        super.onCleared()
    }
}

class ScannerViewModelFactory(
    private val context: Context,
    private val staticDataRepository: StaticDataRepository,
    private val profileRepository: ProfileRepository,
    private val scoreRepository: ScoreRepository,
    private val preferencesRepository: PreferencesRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return ScannerViewModel(
            context = context,
            staticDataRepository = staticDataRepository,
            profileRepository = profileRepository,
            scoreRepository = scoreRepository,
            preferencesRepository = preferencesRepository
        ) as T
    }
}
