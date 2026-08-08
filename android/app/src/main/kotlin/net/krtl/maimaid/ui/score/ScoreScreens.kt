package net.krtl.maimaid.ui.score

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import net.krtl.maimaid.R
import net.krtl.maimaid.domain.model.AppPreferencesState
import net.krtl.maimaid.domain.model.B50Result
import net.krtl.maimaid.domain.model.PlateProgressItem
import net.krtl.maimaid.domain.model.PlayRecord
import net.krtl.maimaid.domain.model.RatingEntry
import net.krtl.maimaid.domain.model.RecommendationItem
import net.krtl.maimaid.domain.model.RecommendationResult
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SecondaryScreenScaffold
import net.krtl.maimaid.ui.common.SectionCard
import net.krtl.maimaid.ui.common.SongGridCard
import net.krtl.maimaid.ui.common.SongInfoBadge
import net.krtl.maimaid.ui.common.SongListCard
import net.krtl.maimaid.ui.common.StatChip
import net.krtl.maimaid.ui.common.difficultyAccentColor
import net.krtl.maimaid.ui.song.SongSharedTransitionState
import net.krtl.maimaid.util.difficultyOrder
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private enum class ScoreSortMode(val labelRes: Int) {
    RATING(R.string.score_sort_rating),
    ACHIEVEMENT(R.string.score_sort_achievement),
    RECENT(R.string.score_sort_recent)
}

private enum class ScoreDisplayMode {
    GRID,
    LIST
}

private enum class ScoreScopeFilter(val labelRes: Int) {
    ALL(R.string.score_scope_all),
    IN_B50(R.string.score_scope_in_b50),
    ELIGIBLE(R.string.score_scope_eligible),
    EXCLUDED(R.string.score_scope_excluded)
}

private enum class ScoreB50State {
    IN_B15,
    IN_B35,
    ELIGIBLE_NOT_IN_B50,
    EXCLUDED_UTAGE,
    EXCLUDED_SERVER,
    EXCLUDED_NO_LEVEL
}

private data class ScoreListItemUi(
    val song: Song,
    val sheet: net.krtl.maimaid.domain.model.Sheet,
    val score: net.krtl.maimaid.domain.model.Score,
    val b50State: ScoreB50State,
    val potentialRating: Int
)

@OptIn(ExperimentalSharedTransitionApi::class, ExperimentalMaterial3Api::class)
@Composable
fun B50Screen(
    container: AppContainer,
    innerPadding: PaddingValues,
    activeSharedTransitionSongId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    openSong: (String, SongSharedTransitionState) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val profile by container.profileRepository.observeActiveProfile().collectAsStateWithLifecycle(initialValue = null)
    val scoreFlow = remember(profile?.id) { profile?.id?.let(container.scoreRepository::observeScores) ?: emptyFlow() }
    val scores by scoreFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferencesState()
    )
    var result by remember { mutableStateOf(B50Result(0, emptyList(), emptyList())) }
    var isExporting by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var pendingExportFile by remember { mutableStateOf<File?>(null) }
    var previewExportFile by remember { mutableStateOf<File?>(null) }
    val saveAsSuccessMessage = stringResource(R.string.b50_export_save_as_success)
    val saveAlbumSuccessMessage = stringResource(R.string.b50_export_save_album_success)
    val exportFailedGenericMessage = stringResource(R.string.b50_export_failed_generic)
    val saveDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("image/png")
    ) { uri ->
        val file = pendingExportFile ?: previewExportFile
        pendingExportFile = null
        if (uri == null || file == null) return@rememberLauncherForActivityResult
        scope.launch {
            val writeResult = B50ExportRenderer.writeBitmapToUri(context, file, uri)
            writeResult.onSuccess {
                Toast.makeText(
                    context,
                    saveAsSuccessMessage,
                    Toast.LENGTH_SHORT
                ).show()
            }.onFailure { error ->
                exportError = error.message ?: exportFailedGenericMessage
            }
        }
    }
    LaunchedEffect(profile?.id, songs, scores, preferences.versionSequence, preferences.chartStatsJson, preferences.useFitDiff) {
        result = container.calculateB50UseCase(profile)
    }
    val b15Rating = remember(result) { result.b15.sumOf { it.rating } }
    val b35Rating = remember(result) { result.b35.sumOf { it.rating } }
    val currentVersion = remember(profile?.server, songs, preferences.versionSequence) {
        profile?.server?.let { server ->
            net.krtl.maimaid.domain.usecase.RatingEngine.latestVersionFor(
                server = server,
                songs = songs,
                versionSequence = preferences.versionSequence
            )
        }
    }
    val canExport = remember(result) { result.b15.isNotEmpty() || result.b35.isNotEmpty() }
    val exportPayload = remember(
        result,
        profile?.name,
        currentVersion,
        preferences.useFitDiff,
        preferences.versionsJson
    ) {
        B50ExportPayload(
            b35 = result.b35,
            b15 = result.b15,
            totalRating = result.total,
            userName = profile?.name,
            currentVersion = currentVersion,
            useFitDiff = preferences.useFitDiff,
            versionsJson = preferences.versionsJson
        )
    }

    fun generatePreview() {
        exportError = null
        isExporting = true
        scope.launch {
            val exportResult = B50ExportRenderer.exportToCache(context, exportPayload)
            exportResult.onSuccess { file ->
                previewExportFile = file
            }.onFailure { error ->
                exportError = error.message ?: exportFailedGenericMessage
            }
            isExporting = false
        }
    }

    SecondaryScreenScaffold(title = "Best 50", innerPadding = innerPadding, onBack = onBack) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Total rating", "Current B35/B15 total") {
                    Text("${result.total}", style = MaterialTheme.typography.headlineMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatChip("B15", "$b15Rating", MaterialTheme.colorScheme.primary)
                        StatChip("B35", "$b35Rating", MaterialTheme.colorScheme.secondary)
                        StatChip("Charts", "${result.b15.size + result.b35.size}", MaterialTheme.colorScheme.tertiary)
                    }
                    Button(
                        onClick = {
                            generatePreview()
                        },
                        enabled = canExport && !isExporting,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isExporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(
                            if (isExporting) {
                                stringResource(R.string.b50_export_generating)
                            } else {
                                stringResource(R.string.b50_export_generate_action)
                            }
                        )
                    }
                    exportError?.let { message ->
                        Text(
                            text = stringResource(R.string.b50_export_failed, message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
            item { SectionCard("B15", "Current version best charts") }
            if (result.b15.isEmpty()) {
                item { EmptySectionMessage("No B15 charts yet") }
            }
            items(result.b15, key = { "${it.songIdentifier}-${it.type}-${it.diff}" }) { entry ->
                RatingEntryRow(
                    entry = entry,
                    isTransitioning = activeSharedTransitionSongId == entry.songIdentifier,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = {
                        openSong(
                            entry.songIdentifier,
                            SongSharedTransitionState(
                                songIdentifier = entry.songIdentifier,
                                displayMode = "LIST",
                                anchorIndex = 0,
                                anchorOffset = 0,
                                sourceRoute = "b50"
                            )
                        )
                    }
                )
            }
            item { SectionCard("B35", "Previous version best charts") }
            if (result.b35.isEmpty()) {
                item { EmptySectionMessage("No B35 charts yet") }
            }
            items(result.b35, key = { "${it.songIdentifier}-${it.type}-${it.diff}" }) { entry ->
                RatingEntryRow(
                    entry = entry,
                    isTransitioning = activeSharedTransitionSongId == entry.songIdentifier,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = {
                        openSong(
                            entry.songIdentifier,
                            SongSharedTransitionState(
                                songIdentifier = entry.songIdentifier,
                                displayMode = "LIST",
                                anchorIndex = 0,
                                anchorOffset = 0,
                                sourceRoute = "b50"
                            )
                        )
                    }
                )
            }
        }
    }

    previewExportFile?.let { previewFile ->
        ModalBottomSheet(
            onDismissRequest = { previewExportFile = null }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = stringResource(R.string.b50_export_preview_title),
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = stringResource(R.string.b50_export_preview_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    AsyncImage(
                        model = previewFile,
                        contentDescription = stringResource(R.string.b50_export_preview_title),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 240.dp, max = 640.dp),
                        contentScale = ContentScale.Fit
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { B50ExportRenderer.shareBitmapFile(context, previewFile) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.b50_export_share_action))
                    }
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                val saveResult = B50ExportRenderer.saveBitmapToGallery(context, previewFile)
                                saveResult.onSuccess {
                                    Toast.makeText(
                                        context,
                                        saveAlbumSuccessMessage,
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }.onFailure { error ->
                                    exportError = error.message
                                        ?: exportFailedGenericMessage
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(R.string.b50_export_save_album_action))
                    }
                }
                OutlinedButton(
                    onClick = {
                        pendingExportFile = previewFile
                        saveDocumentLauncher.launch(B50ExportRenderer.createExportFileName())
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.b50_export_save_as_action))
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun RecommendationsScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    activeSharedTransitionSongId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    openSong: (String, SongSharedTransitionState) -> Unit,
    onBack: () -> Unit
) {
    val profile by container.profileRepository.observeActiveProfile().collectAsStateWithLifecycle(initialValue = null)
    val scoreFlow = remember(profile?.id) { profile?.id?.let(container.scoreRepository::observeScores) ?: emptyFlow() }
    val scores by scoreFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferencesState()
    )
    var result by remember { mutableStateOf(RecommendationResult(emptyList(), emptyList())) }
    LaunchedEffect(profile?.id, songs, scores, preferences.versionSequence, preferences.chartStatsJson, preferences.useFitDiff) {
        result = container.getRecommendationsUseCase(profile)
    }
    val totalGain = remember(result) { (result.b15 + result.b35).sumOf { it.potentialGain } }

    SecondaryScreenScaffold(title = "Recommendations", innerPadding = innerPadding, onBack = onBack) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard("Overview", "Potential rating gain from unoptimized charts") {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatChip("B15", "${result.b15.size}", MaterialTheme.colorScheme.primary)
                        StatChip("B35", "${result.b35.size}", MaterialTheme.colorScheme.secondary)
                        StatChip("Gain", "+$totalGain", MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            item { SectionCard("B15", "Current-version suggestions") }
            if (result.b15.isEmpty()) {
                item { EmptySectionMessage("No B15 recommendations right now") }
            }
            items(result.b15, key = { "${it.song.songIdentifier}-${it.sheet.sheetId}" }) { item ->
                RecommendationRow(
                    item = item,
                    isTransitioning = activeSharedTransitionSongId == item.song.songIdentifier,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = {
                        openSong(
                            item.song.songIdentifier,
                            SongSharedTransitionState(
                                songIdentifier = item.song.songIdentifier,
                                displayMode = "LIST",
                                anchorIndex = 0,
                                anchorOffset = 0,
                                sourceRoute = "recommendations"
                            )
                        )
                    }
                )
            }
            item { SectionCard("B35", "Older-chart suggestions") }
            if (result.b35.isEmpty()) {
                item { EmptySectionMessage("No B35 recommendations right now") }
            }
            items(result.b35, key = { "${it.song.songIdentifier}-${it.sheet.sheetId}" }) { item ->
                RecommendationRow(
                    item = item,
                    isTransitioning = activeSharedTransitionSongId == item.song.songIdentifier,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = animatedVisibilityScope,
                    onClick = {
                        openSong(
                            item.song.songIdentifier,
                            SongSharedTransitionState(
                                songIdentifier = item.song.songIdentifier,
                                displayMode = "LIST",
                                anchorIndex = 0,
                                anchorOffset = 0,
                                sourceRoute = "recommendations"
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
fun PlateProgressScreen(container: AppContainer, innerPadding: PaddingValues, onBack: () -> Unit) {
    val profile by container.profileRepository.observeActiveProfile().collectAsStateWithLifecycle(initialValue = null)
    val scoreFlow = remember(profile?.id) { profile?.id?.let(container.scoreRepository::observeScores) ?: emptyFlow() }
    val scores by scoreFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    var itemsState by remember { mutableStateOf<List<PlateProgressItem>>(emptyList()) }
    LaunchedEffect(profile?.id, songs, scores) { itemsState = container.getPlateProgressUseCase(profile) }
    val totalSheets = remember(itemsState) { itemsState.sumOf { it.totalSheets } }
    val completedSheets = remember(itemsState) { itemsState.sumOf { it.completedSheets } }
    val progress = remember(totalSheets, completedSheets) {
        if (totalSheets == 0) 0f else completedSheets.toFloat() / totalSheets.toFloat()
    }

    SecondaryScreenScaffold(title = "Plate progress", innerPadding = innerPadding, onBack = onBack) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SectionCard("Overview", "Cross-version plate completion") {
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatChip("Done", "$completedSheets", MaterialTheme.colorScheme.primary)
                        StatChip("Left", "${(totalSheets - completedSheets).coerceAtLeast(0)}", MaterialTheme.colorScheme.secondary)
                        StatChip("All", "$totalSheets", MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            if (itemsState.isEmpty()) {
                item { EmptySectionMessage("No plate progress data available") }
            }
            items(itemsState, key = { "${it.version}-${it.plateType}" }) { item ->
                SectionCard(
                    title = "${item.version} · ${item.plateType.displayName}",
                    subtitle = "${item.completedSheets} / ${item.totalSheets}"
                ) {
                    val itemProgress = if (item.totalSheets == 0) 0f else item.completedSheets.toFloat() / item.totalSheets.toFloat()
                    LinearProgressIndicator(progress = { itemProgress }, modifier = Modifier.fillMaxWidth())
                    Text(
                        text = "${(itemProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ScoreListScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    activeSharedTransitionSongId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    openSong: (String, SongSharedTransitionState) -> Unit,
    openSearch: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val profile by container.profileRepository.observeActiveProfile().collectAsStateWithLifecycle(initialValue = null)
    val scoreFlow = remember(profile?.id) { profile?.id?.let(container.scoreRepository::observeScores) ?: emptyFlow() }
    val playRecordFlow = remember(profile?.id) { profile?.id?.let(container.scoreRepository::observePlayRecords) ?: emptyFlow() }
    val scores by scoreFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val playRecords by playRecordFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = AppPreferencesState()
    )
    val songBySheet = remember(songs) { buildSongBySheetMap(songs) }
    val sheetById = remember(songs) { songs.flatMap { song -> song.sheets.map { it.sheetId to it } }.toMap() }
    var b50Result by remember { mutableStateOf(B50Result(0, emptyList(), emptyList())) }
    var query by remember { mutableStateOf("") }
    var selectedDifficulty by remember { mutableStateOf<String?>(null) }
    var selectedRank by remember { mutableStateOf<String?>(null) }
    var sortMode by remember { mutableStateOf(ScoreSortMode.RECENT) }
    var displayMode by remember { mutableStateOf(ScoreDisplayMode.GRID) }
    var scopeFilter by remember { mutableStateOf(ScoreScopeFilter.ALL) }
    var scopeMenuExpanded by remember { mutableStateOf(false) }
    var sortMenuExpanded by remember { mutableStateOf(false) }
    var difficultyMenuExpanded by remember { mutableStateOf(false) }
    var rankMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(profile?.id, songs, scores, preferences.versionSequence, preferences.chartStatsJson, preferences.useFitDiff) {
        b50Result = container.calculateB50UseCase(profile)
    }
    val latestVersion = remember(profile?.server, songs, preferences.versionSequence) {
        profile?.server?.let { server ->
            net.krtl.maimaid.domain.usecase.RatingEngine.latestVersionFor(
                server = server,
                songs = songs,
                versionSequence = preferences.versionSequence
            )
        }
    }
    val chartStats = remember(preferences.chartStatsJson) {
        net.krtl.maimaid.domain.usecase.RatingEngine.parseChartStats(preferences.chartStatsJson)
    }
    val b50StateBySheet = remember(b50Result) {
        buildMap {
            b50Result.b15.forEach { put(it.sheetId, ScoreB50State.IN_B15) }
            b50Result.b35.forEach { put(it.sheetId, ScoreB50State.IN_B35) }
        }
    }
    val difficultyOptions = remember(sheetById) {
        sheetById.values.map { it.difficulty.lowercase() }.distinct().sortedBy { difficultyOrder(it) }
    }
    val scoreItems = remember(
        scores,
        songBySheet,
        sheetById,
        profile,
        preferences.versionSequence,
        preferences.useFitDiff,
        chartStats,
        latestVersion,
        b50StateBySheet
    ) {
        scores.mapNotNull { score ->
            val song = songBySheet[score.sheetId] ?: return@mapNotNull null
            val sheet = sheetById[score.sheetId] ?: return@mapNotNull null
            val explicitB50State = b50StateBySheet[score.sheetId]
            val classification = if (profile != null) {
                net.krtl.maimaid.domain.usecase.RatingEngine.classifySheetForB50(
                    profile = profile!!,
                    song = song,
                    sheet = sheet,
                    versionSequence = preferences.versionSequence,
                    chartStats = chartStats,
                    useFitDiff = preferences.useFitDiff,
                    latestVersion = latestVersion
                )
            } else {
                null
            }
            val derivedState = if (classification != null) {
                when (classification.status) {
                    net.krtl.maimaid.domain.usecase.RatingEngine.B50SheetStatus.B15,
                    net.krtl.maimaid.domain.usecase.RatingEngine.B50SheetStatus.B35 -> ScoreB50State.ELIGIBLE_NOT_IN_B50
                    net.krtl.maimaid.domain.usecase.RatingEngine.B50SheetStatus.EXCLUDED_UTAGE -> ScoreB50State.EXCLUDED_UTAGE
                    net.krtl.maimaid.domain.usecase.RatingEngine.B50SheetStatus.EXCLUDED_SERVER -> ScoreB50State.EXCLUDED_SERVER
                    net.krtl.maimaid.domain.usecase.RatingEngine.B50SheetStatus.EXCLUDED_NO_LEVEL -> ScoreB50State.EXCLUDED_NO_LEVEL
                }
            } else {
                ScoreB50State.EXCLUDED_SERVER
            }
            val finalState = explicitB50State ?: derivedState
            val potentialRating = if (
                finalState == ScoreB50State.IN_B15 ||
                finalState == ScoreB50State.IN_B35 ||
                finalState == ScoreB50State.ELIGIBLE_NOT_IN_B50
            ) {
                net.krtl.maimaid.domain.usecase.RatingEngine.calculateRating(
                    internalLevel = classification?.internalLevel ?: sheet.internalLevelValue ?: sheet.levelValue ?: 0.0,
                    achievement = score.rate,
                    fc = score.fc,
                    afterCircle = net.krtl.maimaid.domain.usecase.RatingEngine.isAfterCircle(
                        latestVersion,
                        preferences.versionSequence
                    )
                )
            } else {
                0
            }
            ScoreListItemUi(
                song = song,
                sheet = sheet,
                score = score,
                b50State = finalState,
                potentialRating = potentialRating
            )
        }
    }
    val visibleScores = remember(
        scoreItems,
        query,
        selectedDifficulty,
        selectedRank,
        sortMode,
        scopeFilter
    ) {
        scoreItems
            .filter { item ->
                val queryOk = if (query.isBlank()) {
                    true
                } else {
                    val q = query.trim().lowercase()
                    val title = item.song.title.lowercase()
                    val artist = item.song.artist.lowercase()
                    val status = item.b50State.name.lowercase().replace('_', ' ')
                    title.contains(q) || artist.contains(q) || item.score.rank.lowercase().contains(q) || status.contains(q)
                }
                val difficultyOk = selectedDifficulty == null || item.sheet.difficulty.equals(selectedDifficulty, true)
                val rankOk = selectedRank == null || item.score.rank == selectedRank
                val scopeOk = when (scopeFilter) {
                    ScoreScopeFilter.ALL -> true
                    ScoreScopeFilter.IN_B50 -> item.b50State == ScoreB50State.IN_B15 || item.b50State == ScoreB50State.IN_B35
                    ScoreScopeFilter.ELIGIBLE -> item.b50State == ScoreB50State.ELIGIBLE_NOT_IN_B50
                    ScoreScopeFilter.EXCLUDED -> item.b50State !in setOf(
                        ScoreB50State.IN_B15,
                        ScoreB50State.IN_B35,
                        ScoreB50State.ELIGIBLE_NOT_IN_B50
                    )
                }
                queryOk && difficultyOk && rankOk && scopeOk
            }
            .let { list ->
                when (sortMode) {
                    ScoreSortMode.RECENT -> list.sortedByDescending { it.score.achievementDate }
                    ScoreSortMode.ACHIEVEMENT -> list.sortedByDescending { it.score.rate }
                    ScoreSortMode.RATING -> list.sortedWith(
                        compareByDescending<ScoreListItemUi> { it.potentialRating }
                            .thenByDescending { it.score.rate }
                    )
                }
            }
    }
    val recentPlayItems = remember(playRecords, songBySheet, sheetById) {
        playRecords.mapNotNull { record ->
            val song = songBySheet[record.sheetId] ?: return@mapNotNull null
            val sheet = sheetById[record.sheetId] ?: return@mapNotNull null
            RecentPlaySummary(record = record, song = song, sheet = sheet)
        }
    }
    val statsSssPlus = remember(scores) { scores.count { it.rank == "SSS+" } }
    val statsSss = remember(scores) { scores.count { it.rank == "SSS" } }
    val statsFc = remember(scores) { scores.count { !it.fc.isNullOrBlank() } }
    val statsAp = remember(scores) { scores.count { it.fc?.contains("ap", true) == true } }
    val statsFs = remember(scores) { scores.count { !it.fs.isNullOrBlank() } }
    val inB50Count = remember(scoreItems) {
        scoreItems.count { it.b50State == ScoreB50State.IN_B15 || it.b50State == ScoreB50State.IN_B35 }
    }
    val eligibleNotSelectedCount = remember(scoreItems) { scoreItems.count { it.b50State == ScoreB50State.ELIGIBLE_NOT_IN_B50 } }
    val excludedCount = remember(scoreItems) {
        scoreItems.count { it.b50State !in setOf(ScoreB50State.IN_B15, ScoreB50State.IN_B35, ScoreB50State.ELIGIBLE_NOT_IN_B50) }
    }
    val scoreSubtitle = profile?.let {
        stringResource(R.string.score_profile_summary, it.server.displayName, scores.size)
    } ?: stringResource(R.string.score_profile_summary_count_only, scores.size)

    SecondaryScreenScaffold(
        title = stringResource(R.string.score_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(contentPadding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                SectionCard(
                    title = profile?.name ?: stringResource(R.string.score_title),
                    subtitle = scoreSubtitle
                ) {
                    if (scores.isEmpty()) {
                        Text(
                            stringResource(R.string.score_summary_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatChip(
                                stringResource(R.string.score_stat_scores),
                                "${scores.size}",
                                MaterialTheme.colorScheme.primary
                            )
                            StatChip(
                                stringResource(R.string.score_scope_in_b50),
                                "$inB50Count",
                                MaterialTheme.colorScheme.secondary
                            )
                            StatChip(
                                stringResource(R.string.score_scope_eligible),
                                "$eligibleNotSelectedCount",
                                MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            StatChip("SSS+", "$statsSssPlus", MaterialTheme.colorScheme.primary)
                            StatChip("SSS", "$statsSss", MaterialTheme.colorScheme.secondary)
                            StatChip(
                                stringResource(R.string.score_scope_excluded),
                                "$excludedCount",
                                MaterialTheme.colorScheme.tertiary
                            )
                        }
                        Text(
                            text = stringResource(R.string.score_b50_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.score_browse_title),
                    subtitle = stringResource(R.string.score_browse_subtitle, visibleScores.size, scores.size)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatChip(stringResource(R.string.score_stat_fc), "$statsFc", MaterialTheme.colorScheme.primary)
                        StatChip(stringResource(R.string.score_stat_ap), "$statsAp", MaterialTheme.colorScheme.secondary)
                        StatChip(stringResource(R.string.score_stat_fs), "$statsFs", MaterialTheme.colorScheme.tertiary)
                    }
                    ScoreSearchField(
                        value = query,
                        onValueChange = { query = it },
                        onClear = { query = "" }
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = {
                                query = ""
                                selectedDifficulty = null
                                selectedRank = null
                                sortMode = ScoreSortMode.RECENT
                                scopeFilter = ScoreScopeFilter.ALL
                            }
                        ) {
                            Text(stringResource(R.string.score_reset_filters))
                        }
                        TextButton(onClick = openSearch) {
                            Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.size(6.dp))
                            Text(stringResource(R.string.score_open_song_search))
                        }
                    }
                    ScoreFilterToolbar(
                        scopeValue = stringResource(scopeFilter.labelRes),
                        sortValue = stringResource(sortMode.labelRes),
                        difficultyValue = selectedDifficulty?.let { displayScoreDifficultyLabel(it) }
                            ?: stringResource(R.string.score_filter_all),
                        rankValue = selectedRank ?: stringResource(R.string.score_filter_all),
                        displayMode = displayMode,
                        scopeExpanded = scopeMenuExpanded,
                        onScopeExpandedChange = { scopeMenuExpanded = it },
                        sortExpanded = sortMenuExpanded,
                        onSortExpandedChange = { sortMenuExpanded = it },
                        difficultyExpanded = difficultyMenuExpanded,
                        onDifficultyExpandedChange = { difficultyMenuExpanded = it },
                        rankExpanded = rankMenuExpanded,
                        onRankExpandedChange = { rankMenuExpanded = it },
                        onToggleDisplayMode = {
                            displayMode = if (displayMode == ScoreDisplayMode.GRID) {
                                ScoreDisplayMode.LIST
                            } else {
                                ScoreDisplayMode.GRID
                            }
                        },
                        scopeMenu = {
                            ScoreScopeFilter.entries.forEach { filter ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(filter.labelRes)) },
                                    onClick = {
                                        scopeFilter = filter
                                        scopeMenuExpanded = false
                                    }
                                )
                            }
                        },
                        sortMenu = {
                            ScoreSortMode.entries.forEach { mode ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(mode.labelRes)) },
                                    onClick = {
                                        sortMode = mode
                                        sortMenuExpanded = false
                                    }
                                )
                            }
                        },
                        difficultyMenu = {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.score_filter_all)) },
                                onClick = {
                                    selectedDifficulty = null
                                    difficultyMenuExpanded = false
                                }
                            )
                            difficultyOptions.forEach { difficulty ->
                                DropdownMenuItem(
                                    text = { Text(displayScoreDifficultyLabel(difficulty)) },
                                    onClick = {
                                        selectedDifficulty = difficulty
                                        difficultyMenuExpanded = false
                                    }
                                )
                            }
                        },
                        rankMenu = {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.score_filter_all)) },
                                onClick = {
                                    selectedRank = null
                                    rankMenuExpanded = false
                                }
                            )
                            listOf("SSS+", "SSS", "SS+", "SS").forEach { rank ->
                                DropdownMenuItem(
                                    text = { Text(rank) },
                                    onClick = {
                                        selectedRank = rank
                                        rankMenuExpanded = false
                                    }
                                )
                            }
                        }
                    )
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(
                            R.string.score_b50_status_summary,
                            inB50Count,
                            eligibleNotSelectedCount,
                            excludedCount
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                    )
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.score_results_title),
                    subtitle = stringResource(R.string.score_results_subtitle, visibleScores.size)
                )
            }
            if (visibleScores.isEmpty()) {
                item {
                    EmptySectionMessage(
                        title = stringResource(R.string.score_empty_title),
                        body = stringResource(R.string.score_empty_body)
                    )
                }
            }
            when (displayMode) {
                ScoreDisplayMode.GRID -> {
                    items(visibleScores.chunked(3), key = { row -> row.joinToString("-") { it.score.scoreKey } }) { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            row.forEach { item ->
                                ScoreGridCell(
                                    item = item,
                                    isTransitioning = activeSharedTransitionSongId == item.song.songIdentifier,
                                    sharedTransitionScope = sharedTransitionScope,
                                    animatedVisibilityScope = animatedVisibilityScope,
                                    modifier = Modifier.weight(1f),
                                    onClick = {
                                        openSong(
                                            item.song.songIdentifier,
                                            SongSharedTransitionState(
                                                songIdentifier = item.song.songIdentifier,
                                                displayMode = "GRID",
                                                anchorIndex = 0,
                                                anchorOffset = 0,
                                                sourceRoute = "scores"
                                            )
                                        )
                                    }
                                )
                            }
                            repeat(3 - row.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
                ScoreDisplayMode.LIST -> {
                    items(visibleScores, key = { it.score.scoreKey }) { item ->
                        ScoreCompactRow(
                            item = item,
                            isTransitioning = activeSharedTransitionSongId == item.song.songIdentifier,
                            sharedTransitionScope = sharedTransitionScope,
                            animatedVisibilityScope = animatedVisibilityScope,
                            onClick = {
                                openSong(
                                    item.song.songIdentifier,
                                    SongSharedTransitionState(
                                        songIdentifier = item.song.songIdentifier,
                                        displayMode = "LIST",
                                        anchorIndex = 0,
                                        anchorOffset = 0,
                                        sourceRoute = "scores"
                                    )
                                )
                            }
                        )
                    }
                }
            }
            item {
                SectionCard(
                    title = stringResource(R.string.score_recent_records_title),
                    subtitle = if (playRecords.isEmpty()) {
                        stringResource(R.string.score_recent_records_empty_subtitle)
                    } else {
                        stringResource(R.string.score_recent_records_subtitle, playRecords.size)
                    }
                ) {
                    if (recentPlayItems.isEmpty()) {
                        Text(
                            text = stringResource(R.string.score_recent_records_empty_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        recentPlayItems.take(8).forEach { item ->
                            RecentPlayRow(
                                item = item,
                                onClick = {
                                    openSong(
                                        item.song.songIdentifier,
                                        SongSharedTransitionState(
                                            songIdentifier = item.song.songIdentifier,
                                            displayMode = "LIST",
                                            anchorIndex = 0,
                                            anchorOffset = 0,
                                            sourceRoute = "scores"
                                        )
                                    )
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RatingEntryRow(
    entry: RatingEntry,
    isTransitioning: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit
) {
    val song = Song(
        songIdentifier = entry.songIdentifier,
        category = "",
        title = entry.songTitle,
        artist = "",
        imageName = entry.imageName.orEmpty(),
        version = null,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
        searchKeywords = null,
        aliases = emptyList(),
        songId = entry.songId,
        isFavorite = false,
        sheets = emptyList()
    )
    SongListCard(
        song = song,
        subtitle = "${entry.type.uppercase()} ${entry.diff} · ${entry.achievement}%",
        supporting = {
            Text(
                text = listOfNotNull("Lv ${entry.level}", entry.fc, entry.fs).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = "${entry.rating}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        isTransitioning = isTransitioning,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onClick = onClick
    )
}

@Composable
private fun EmptySectionMessage(
    title: String,
    body: String = stringResource(R.string.score_empty_default_body)
) {
    SectionCard(title = title) {
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScoreSearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            placeholder = { Text(stringResource(R.string.score_search_label)) },
            leadingIcon = {
                Icon(Icons.Default.Search, contentDescription = null)
            },
            trailingIcon = {
                if (value.isNotBlank()) {
                    IconButton(onClick = onClear) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.search_clear)
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ScoreFilterToolbar(
    scopeValue: String,
    sortValue: String,
    difficultyValue: String,
    rankValue: String,
    displayMode: ScoreDisplayMode,
    scopeExpanded: Boolean,
    onScopeExpandedChange: (Boolean) -> Unit,
    sortExpanded: Boolean,
    onSortExpandedChange: (Boolean) -> Unit,
    difficultyExpanded: Boolean,
    onDifficultyExpandedChange: (Boolean) -> Unit,
    rankExpanded: Boolean,
    onRankExpandedChange: (Boolean) -> Unit,
    onToggleDisplayMode: () -> Unit,
    scopeMenu: @Composable () -> Unit,
    sortMenu: @Composable () -> Unit,
    difficultyMenu: @Composable () -> Unit,
    rankMenu: @Composable () -> Unit
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ScoreMenuSelector(
            label = stringResource(R.string.score_scope_label),
            value = scopeValue,
            expanded = scopeExpanded,
            onExpandedChange = onScopeExpandedChange,
            menuContent = scopeMenu
        )
        ScoreMenuSelector(
            label = stringResource(R.string.score_sort_label),
            value = sortValue,
            expanded = sortExpanded,
            onExpandedChange = onSortExpandedChange,
            menuContent = sortMenu
        )
        ScoreMenuSelector(
            label = stringResource(R.string.score_filter_difficulty),
            value = difficultyValue,
            expanded = difficultyExpanded,
            onExpandedChange = onDifficultyExpandedChange,
            menuContent = difficultyMenu
        )
        ScoreMenuSelector(
            label = stringResource(R.string.score_filter_rank),
            value = rankValue,
            expanded = rankExpanded,
            onExpandedChange = onRankExpandedChange,
            menuContent = rankMenu
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.clickable(onClick = onToggleDisplayMode)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (displayMode == ScoreDisplayMode.GRID) {
                        Icons.Default.GridView
                    } else {
                        Icons.AutoMirrored.Filled.ViewList
                    },
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(
                        if (displayMode == ScoreDisplayMode.GRID) {
                            R.string.score_view_grid
                        } else {
                            R.string.score_view_list
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun ScoreMenuSelector(
    label: String,
    value: String,
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    menuContent: @Composable () -> Unit
) {
    Box {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.clickable { onExpandedChange(true) }
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandedChange(false) }
        ) {
            menuContent()
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun RecommendationRow(
    item: RecommendationItem,
    isTransitioning: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit
) {
    SongListCard(
        song = item.song,
        subtitle = "${item.sheet.type.uppercase()} ${item.sheet.difficulty} · target ${item.targetRank} ${item.targetAchievement}%",
        supporting = {
            Text(
                text = listOfNotNull(
                    item.currentRate?.let { "Current ${it}%" },
                    item.fitDiff?.let { "Fit ${"%.1f".format(it)}" }
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = "+${item.potentialGain}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
        },
        isTransitioning = isTransitioning,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onClick = onClick
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ScoreGridCell(
    item: ScoreListItemUi,
    isTransitioning: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val diffColor = difficultyColorForScore(item.sheet.difficulty)
    val b50Tint = scoreB50StateColor(item.b50State)
    SongGridCard(
        song = item.song,
        modifier = modifier,
        accentColor = diffColor,
        badgeText = difficultyShort(item.sheet.difficulty),
        badgeTint = diffColor,
        isTransitioning = isTransitioning,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        bottomOverlay = {
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f),
                shape = RoundedCornerShape(topStart = 10.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp)) {
                    Text(
                        text = item.score.rank,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = stringResource(R.string.score_achievement_short_value, item.score.rate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Text(
                        text = stringResource(scoreB50StateLabelRes(item.b50State)),
                        style = MaterialTheme.typography.labelSmall,
                        color = b50Tint
                    )
                }
            }
        },
        onClick = onClick
    )
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun ScoreCompactRow(
    item: ScoreListItemUi,
    isTransitioning: Boolean,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    onClick: () -> Unit
) {
    val diffColor = difficultyColorForScore(item.sheet.difficulty)
    val achievement = stringResource(R.string.score_achievement_value, item.score.rate)
    SongListCard(
        song = item.song,
        subtitle = stringResource(
            R.string.score_row_subtitle,
            item.sheet.type.uppercase(),
            difficultyShort(item.sheet.difficulty),
            item.score.rank
        ),
        accentColor = diffColor,
        supporting = {
            val extra = listOfNotNull(
                achievement,
                stringResource(scoreB50StateLabelRes(item.b50State)),
                item.score.fc,
                item.score.fs,
                item.score.dxScore.takeIf { it > 0 }?.let { stringResource(R.string.score_dx_value, it) }
            ).joinToString(" · ")
            if (extra.isNotBlank()) {
                Text(
                    text = extra,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        trailingContent = {
            Text(
                text = item.score.rank,
                style = MaterialTheme.typography.titleSmall,
                color = diffColor
            )
            if (item.potentialRating > 0) {
                Text(
                    text = stringResource(R.string.score_rating_value, item.potentialRating),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            SongInfoBadge(
                text = stringResource(scoreB50BadgeLabelRes(item.b50State)),
                tint = scoreB50StateColor(item.b50State)
            )
        },
        isTransitioning = isTransitioning,
        sharedTransitionScope = sharedTransitionScope,
        animatedVisibilityScope = animatedVisibilityScope,
        onClick = onClick
    )
}

private fun buildSongBySheetMap(songs: List<Song>): Map<String, Song> =
    songs.flatMap { song -> song.sheets.map { it.sheetId to song } }.toMap()

private fun difficultyShort(difficulty: String): String = when (difficulty.lowercase()) {
    "basic" -> "BSC"
    "advanced" -> "ADV"
    "expert" -> "EXP"
    "master" -> "MST"
    "remaster" -> "ReM"
    else -> difficulty.take(3)
}

@Composable
private fun displayScoreDifficultyLabel(difficulty: String): String = when (difficulty.lowercase()) {
    "remaster" -> stringResource(R.string.search_diff_remaster)
    else -> difficulty.replaceFirstChar(Char::uppercase)
}

private fun difficultyColorForScore(difficulty: String): Color =
    difficultyAccentColor(difficulty = difficulty, type = null)

private fun scoreB50StateLabelRes(state: ScoreB50State): Int = when (state) {
    ScoreB50State.IN_B15 -> R.string.score_b50_state_in_b15
    ScoreB50State.IN_B35 -> R.string.score_b50_state_in_b35
    ScoreB50State.ELIGIBLE_NOT_IN_B50 -> R.string.score_b50_state_eligible
    ScoreB50State.EXCLUDED_UTAGE -> R.string.score_b50_state_excluded_utage
    ScoreB50State.EXCLUDED_SERVER -> R.string.score_b50_state_excluded_server
    ScoreB50State.EXCLUDED_NO_LEVEL -> R.string.score_b50_state_excluded_no_level
}

private fun scoreB50BadgeLabelRes(state: ScoreB50State): Int = when (state) {
    ScoreB50State.IN_B15 -> R.string.score_b50_badge_b15
    ScoreB50State.IN_B35 -> R.string.score_b50_badge_b35
    ScoreB50State.ELIGIBLE_NOT_IN_B50 -> R.string.score_b50_badge_ready
    ScoreB50State.EXCLUDED_UTAGE,
    ScoreB50State.EXCLUDED_SERVER,
    ScoreB50State.EXCLUDED_NO_LEVEL -> R.string.score_b50_badge_out
}

@Composable
private fun scoreB50StateColor(state: ScoreB50State) = when (state) {
    ScoreB50State.IN_B15 -> MaterialTheme.colorScheme.primary
    ScoreB50State.IN_B35 -> MaterialTheme.colorScheme.tertiary
    ScoreB50State.ELIGIBLE_NOT_IN_B50 -> MaterialTheme.colorScheme.secondary
    ScoreB50State.EXCLUDED_UTAGE,
    ScoreB50State.EXCLUDED_SERVER,
    ScoreB50State.EXCLUDED_NO_LEVEL -> MaterialTheme.colorScheme.error
}

private data class RecentPlaySummary(
    val record: PlayRecord,
    val song: Song,
    val sheet: net.krtl.maimaid.domain.model.Sheet
)

@Composable
private fun RecentPlayRow(
    item: RecentPlaySummary,
    onClick: () -> Unit
) {
    val diffColor = difficultyColorForScore(item.sheet.difficulty)
    SongListCard(
        song = item.song,
        subtitle = stringResource(
            R.string.score_recent_record_subtitle,
            item.sheet.type.uppercase(),
            difficultyShort(item.sheet.difficulty),
            item.record.rank
        ),
        accentColor = diffColor,
        supporting = {
            Text(
                text = listOfNotNull(
                    formatScoreRecordDate(item.record.playDate),
                    stringResource(R.string.score_achievement_value, item.record.rate),
                    item.record.fc,
                    item.record.fs
                ).joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingContent = {
            Text(
                text = item.record.rank,
                style = MaterialTheme.typography.titleSmall,
                color = diffColor
            )
        },
        onClick = onClick
    )
}

private fun formatScoreRecordDate(timestampMillis: Long): String =
    Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("MM-dd HH:mm"))

@Composable
private fun <T> ChipRows(
    items: List<T>,
    itemsPerRow: Int,
    content: @Composable (T) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        items.chunked(itemsPerRow).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                rowItems.forEach { item ->
                    content(item)
                }
            }
        }
    }
}
