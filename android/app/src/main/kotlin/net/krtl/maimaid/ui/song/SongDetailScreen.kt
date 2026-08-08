@file:OptIn(ExperimentalLayoutApi::class)

package net.krtl.maimaid.ui.song

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HowToVote
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.krtl.maimaid.R
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SessionState
import net.krtl.maimaid.domain.model.CommunityAliasApprovedAlias
import net.krtl.maimaid.domain.model.CommunityAliasDuplicateReason
import net.krtl.maimaid.domain.model.CommunityAliasMyCandidate
import net.krtl.maimaid.domain.model.CommunityAliasSubmitStatus
import net.krtl.maimaid.data.remote.dto.ChartStatDto
import net.krtl.maimaid.data.remote.dto.ChartStatsResponse
import net.krtl.maimaid.domain.model.AppPreferencesState
import net.krtl.maimaid.domain.model.GameServer
import net.krtl.maimaid.domain.model.PlayRecord
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.domain.usecase.RatingEngine
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SectionCard
import net.krtl.maimaid.ui.common.SongCover
import net.krtl.maimaid.util.difficultyOrder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

private val chartStatsJson = Json { ignoreUnknownKeys = true }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SongDetailScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    songIdentifier: String,
    onBack: () -> Unit,
    openCommunityBoard: () -> Unit,
    openCloudAuth: () -> Unit,
    searchTransitionState: SearchSharedTransitionState? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null
) {
    val song by container.staticDataRepository.observeSong(songIdentifier).collectAsStateWithLifecycle(initialValue = null)
    val profile by container.profileRepository.observeActiveProfile().collectAsStateWithLifecycle(initialValue = null)
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(initialValue = AppPreferencesState())
    val sessionState by container.authRepository.sessionState.collectAsStateWithLifecycle()
    val approvedAliases by container.communityAliasRepository.observeApprovedAliases(songIdentifier)
        .collectAsStateWithLifecycle(initialValue = emptyList())
    val scoreFlow = remember(profile?.id) { profile?.id?.let(container.scoreRepository::observeScores) ?: emptyFlow() }
    val playRecordFlow = remember(profile?.id) {
        profile?.id?.let(container.scoreRepository::observePlayRecords) ?: emptyFlow()
    }
    val scores by scoreFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val playRecords by playRecordFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val scoreBySheet = remember(scores) { scores.associateBy { it.sheetId } }
    val recordsBySheet = remember(playRecords) { playRecords.groupBy { it.sheetId } }
    val chartStats = remember(preferences.chartStatsJson) { parseChartStats(preferences.chartStatsJson) }
    val isSharedTransitionEnabled =
        searchTransitionState?.songIdentifier == songIdentifier &&
            sharedTransitionScope != null &&
            animatedVisibilityScope != null
    val isArtistSharedTransitionEnabled =
        isSharedTransitionEnabled && searchTransitionState.displayMode == SONG_SEARCH_DISPLAY_MODE_LIST

    var selectedType by remember(songIdentifier) { mutableStateOf("") }
    var editingSheet by remember { mutableStateOf<Sheet?>(null) }
    var chromeVisible by remember(songIdentifier, isSharedTransitionEnabled) { mutableStateOf(!isSharedTransitionEnabled) }
    var detailContentVisible by remember(songIdentifier, isSharedTransitionEnabled) { mutableStateOf(!isSharedTransitionEnabled) }
    var communityAliasDraft by rememberSaveable(songIdentifier) { mutableStateOf("") }
    var myCommunityCandidates by remember(songIdentifier) { mutableStateOf(emptyList<CommunityAliasMyCandidate>()) }
    var communityDailyCount by remember(songIdentifier) { mutableIntStateOf(0) }
    var isSubmittingCommunityAlias by remember(songIdentifier) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val uriHandler = LocalUriHandler.current
    val context = LocalContext.current
    val isCommunityConfigured = remember(container) { container.backendSessionManager.isConfigured() }

    LaunchedEffect(song?.songIdentifier) {
        song?.let { selectedType = defaultType(it.sheets) }
    }

    LaunchedEffect(songIdentifier, isSharedTransitionEnabled) {
        chromeVisible = !isSharedTransitionEnabled
        detailContentVisible = !isSharedTransitionEnabled
        if (isSharedTransitionEnabled) {
            delay(SongMotionTokens.CONTENT_FADE_DELAY_MILLIS.toLong())
            chromeVisible = true
            detailContentVisible = true
        }
    }

    LaunchedEffect(songIdentifier, sessionState, isCommunityConfigured) {
        if (!isCommunityConfigured) {
            myCommunityCandidates = emptyList()
            communityDailyCount = 0
            return@LaunchedEffect
        }
        if (sessionState is SessionState.Unknown) {
            container.authRepository.checkSession()
            return@LaunchedEffect
        }
        container.communityAliasRepository.syncApprovedAliasesIfNeeded()
        if (sessionState is SessionState.LoggedIn) {
            myCommunityCandidates = when (val result = container.communityAliasRepository.fetchMySongCandidates(songIdentifier)) {
                is Result.Ok -> result.value
                is Result.Err -> emptyList()
            }
            communityDailyCount = when (val result = container.communityAliasRepository.fetchMyDailySubmissionCount()) {
                is Result.Ok -> result.value
                is Result.Err -> 0
            }
        } else {
            myCommunityCandidates = emptyList()
            communityDailyCount = 0
        }
    }

    val detailContentTopPadding = maxOf(
        88.dp,
        innerPadding.calculateTopPadding() + 72.dp
    )
    val detailContainerModifier = if (song != null && isSharedTransitionEnabled) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(songCardContainerKey(songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    val title = song?.songId?.takeIf { it > 0 }?.let { "#$it" } ?: stringResource(R.string.song_detail_title)
    val currentSong = song

    Box(modifier = Modifier.fillMaxSize()) {
        if (currentSong == null) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp)
                    .padding(top = detailContentTopPadding, bottom = innerPadding.calculateBottomPadding())
            ) {
                SectionCard(
                    title = stringResource(R.string.song_detail_not_found_title),
                    subtitle = stringResource(R.string.song_detail_not_found_description)
                )
            }
        } else {
            val availableTypes = currentSong.sheets.map { it.type.lowercase() }.distinct().sorted().reversed()
            val effectiveType = selectedType.ifBlank { defaultType(currentSong.sheets) }
            val filteredSheets = remember(currentSong, effectiveType) {
                currentSong.sheets
                    .filter { it.type.equals(effectiveType, ignoreCase = true) }
                    .sortedByDescending { difficultyOrder(it.difficulty) }
            }
            val matchedStats = remember(chartStats, currentSong.songId, currentSong.songIdentifier) {
                chartStats[currentSong.songId.toString()] ?: chartStats[currentSong.songIdentifier].orEmpty()
            }
            val aliasItems = remember(currentSong.aliases, approvedAliases) {
                buildSongAliasItems(currentSong.aliases, approvedAliases)
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .then(detailContainerModifier),
                color = MaterialTheme.colorScheme.surface
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 20.dp,
                        end = 20.dp,
                        top = detailContentTopPadding,
                        bottom = innerPadding.calculateBottomPadding() + 28.dp
                    ),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    item {
                        SongHeroSection(
                            song = currentSong,
                            sharedTransitionScope = if (isSharedTransitionEnabled) sharedTransitionScope else null,
                            animatedVisibilityScope = if (isSharedTransitionEnabled) animatedVisibilityScope else null,
                            enableArtistSharedTransition = isArtistSharedTransitionEnabled
                        )
                    }

                    item {
                        AnimatedVisibility(
                            visible = detailContentVisible,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = SongMotionTokens.CONTENT_FADE_DURATION_MILLIS
                                )
                            ),
                            exit = fadeOut()
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                                ExternalLinksSection(song = currentSong, onOpen = uriHandler::openUri)
                                CommunityAliasCard(
                                    aliasItems = aliasItems,
                                    approvedAliases = approvedAliases,
                                    myCandidates = myCommunityCandidates,
                                    draft = communityAliasDraft,
                                    dailyCount = communityDailyCount,
                                    isConfigured = isCommunityConfigured,
                                    isLoggedIn = sessionState is SessionState.LoggedIn,
                                    isSubmitting = isSubmittingCommunityAlias,
                                    onDraftChange = { communityAliasDraft = it },
                                    onSubmit = {
                                        val draft = communityAliasDraft.trim()
                                        if (draft.isEmpty()) {
                                            return@CommunityAliasCard
                                        }
                                        if (sessionState !is SessionState.LoggedIn) {
                                            openCloudAuth()
                                            return@CommunityAliasCard
                                        }
                                        scope.launch {
                                            isSubmittingCommunityAlias = true
                                            val result = container.communityAliasRepository.submitAlias(
                                                songIdentifier = currentSong.songIdentifier,
                                                aliasText = draft
                                            )
                                            isSubmittingCommunityAlias = false
                                            when (result.status) {
                                                CommunityAliasSubmitStatus.CREATED -> {
                                                    communityAliasDraft = ""
                                                    communityDailyCount = (5 - (result.quotaRemaining ?: 0)).coerceIn(0, 5)
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.community_alias_submit_success),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                CommunityAliasSubmitStatus.REJECTED_DUPLICATE -> {
                                                    val messageRes = when (result.duplicateReason) {
                                                        CommunityAliasDuplicateReason.LXNS_EXISTING ->
                                                            R.string.community_alias_submit_duplicate_lxns
                                                        CommunityAliasDuplicateReason.COMMUNITY_EXISTING ->
                                                            R.string.community_alias_submit_duplicate_community
                                                        CommunityAliasDuplicateReason.ADMIN_REJECTED_LOCKED ->
                                                            R.string.community_alias_submit_duplicate_locked
                                                        null -> null
                                                    }
                                                    Toast.makeText(
                                                        context,
                                                        messageRes?.let(context::getString) ?: result.message,
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                CommunityAliasSubmitStatus.QUOTA_EXCEEDED -> {
                                                    communityDailyCount = 5
                                                    Toast.makeText(
                                                        context,
                                                        context.getString(R.string.community_alias_submit_quota_exceeded),
                                                        Toast.LENGTH_SHORT
                                                    ).show()
                                                }
                                                CommunityAliasSubmitStatus.UNAUTHENTICATED -> openCloudAuth()
                                                CommunityAliasSubmitStatus.INVALID_REQUEST,
                                                CommunityAliasSubmitStatus.ERROR -> {
                                                    Toast.makeText(context, result.message, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                            myCommunityCandidates =
                                                when (val refresh = container.communityAliasRepository.fetchMySongCandidates(currentSong.songIdentifier)) {
                                                    is Result.Ok -> refresh.value
                                                    is Result.Err -> myCommunityCandidates
                                                }
                                            container.communityAliasRepository.syncApprovedAliasesIfNeeded()
                                        }
                                    },
                                    onOpenBoard = openCommunityBoard,
                                    onLogin = openCloudAuth
                                )
                                SongAvailabilityCard(song = currentSong, activeServer = profile?.server)

                                if (availableTypes.size > 1) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        availableTypes.forEach { type ->
                                            FilterChip(
                                                selected = effectiveType == type,
                                                onClick = { selectedType = type },
                                                label = {
                                                    Text(
                                                        if (type == "std") {
                                                            stringResource(R.string.song_detail_type_std)
                                                        } else {
                                                            type.uppercase()
                                                        }
                                                    )
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    items(filteredSheets, key = { it.sheetId }) { sheet ->
                        AnimatedVisibility(
                            visible = detailContentVisible,
                            enter = fadeIn(
                                animationSpec = tween(
                                    durationMillis = SongMotionTokens.CONTENT_FADE_DURATION_MILLIS,
                                    delayMillis = 40
                                )
                            ),
                            exit = fadeOut()
                        ) {
                            SheetCard(
                                sheet = sheet,
                                score = scoreBySheet[sheet.sheetId],
                                stat = findMatchingStat(sheet, matchedStats),
                                recentRecords = recordsBySheet[sheet.sheetId].orEmpty(),
                                onRecordClick = { editingSheet = sheet }
                            )
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = chromeVisible,
            enter = fadeIn(animationSpec = tween(SongMotionTokens.CONTENT_FADE_DURATION_MILLIS)),
            exit = fadeOut()
        ) {
            SongDetailTopBar(
                title = title,
                isFavorite = currentSong?.isFavorite == true,
                onToggleFavorite = currentSong?.let { song ->
                    {
                        scope.launch {
                            container.staticDataRepository.setSongFavorite(
                                songIdentifier = song.songIdentifier,
                                isFavorite = !song.isFavorite
                            )
                        }
                    }
                },
                onBack = onBack
            )
        }
    }

    editingSheet?.let { sheet ->
        ScoreEditorDialog(
            sheet = sheet,
            existingScore = scores.firstOrNull { it.sheetId == sheet.sheetId },
            onDismiss = { editingSheet = null },
            onSave = { rate, dxScore, fc, fs ->
                val activeProfile = profile ?: return@ScoreEditorDialog
                scope.launch {
                    container.scoreRepository.saveScore(sheet.sheetId, activeProfile.id, rate, dxScore, fc, fs)
                }
                editingSheet = null
            }
        )
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SongHeroSection(
    song: Song,
    sharedTransitionScope: SharedTransitionScope?,
    animatedVisibilityScope: AnimatedVisibilityScope?,
    enableArtistSharedTransition: Boolean
) {
    val coverSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedElement(
                sharedContentState = rememberSharedContentState(songCoverKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    val titleSharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(songTitleKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    val artistSharedModifier = if (enableArtistSharedTransition && sharedTransitionScope != null && animatedVisibilityScope != null) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(songArtistKey(song.songIdentifier)),
                animatedVisibilityScope = animatedVisibilityScope
            )
        }
    } else {
        Modifier
    }
    Box(modifier = Modifier.padding(0.dp, 20.dp)) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SongCover(
                imageName = song.imageName,
                title = song.title,
                modifier = Modifier
                    .fillMaxWidth(0.56f)
                    .aspectRatio(1f)
                    .then(coverSharedModifier),
                cornerRadius = SongMotionTokens.SHARED_COVER_CORNER_RADIUS_DP,
                highQuality = true
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = titleSharedModifier
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = artistSharedModifier
                )
            }

            SongMetadataSection(song = song)
        }
    }
}

@Composable
private fun SongDetailTopBar(
    title: String,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    onBack: () -> Unit
) {
    Surface(
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
        tonalElevation = 3.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.common_back)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            IconButton(
                onClick = { onToggleFavorite?.invoke() },
                enabled = onToggleFavorite != null
            ) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                    contentDescription = stringResource(
                        if (isFavorite) {
                            R.string.song_detail_remove_favorite
                        } else {
                            R.string.song_detail_add_favorite
                        }
                    ),
                    tint = if (isFavorite) {
                        Color(0xFFF4B400)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SongMetadataSection(song: Song) {
    val metadataItems = buildList {
        song.bpm?.let {
            add(
                SongMetadataItem(
                    labelRes = R.string.song_detail_metadata_bpm,
                    value = it.toInt().toString(),
                    tint = MaterialTheme.colorScheme.secondary
                )
            )
        }
        add(
            SongMetadataItem(
                labelRes = R.string.song_detail_metadata_category,
                value = song.category,
                tint = MaterialTheme.colorScheme.tertiary
            )
        )
        song.version?.let {
            add(
                SongMetadataItem(
                    labelRes = R.string.song_detail_metadata_version,
                    value = compactVersionName(it),
                    tint = MaterialTheme.colorScheme.primary
                )
            )
        }
        song.releaseDate?.let {
            add(
                SongMetadataItem(
                    labelRes = R.string.song_detail_metadata_release_date,
                    value = formatCompactDate(it),
                    tint = MaterialTheme.colorScheme.outline
                )
            )
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            metadataItems.forEach { item ->
                SongMetadataCell(
                    item = item,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun SongAvailabilityCard(song: Song, activeServer: GameServer?) {
    val servers = listOf(
        ServerAvailabilityUi("JP", R.string.song_detail_server_jp, song.sheets.any { it.regionJp }, activeServer == GameServer.JP),
        ServerAvailabilityUi("INTL", R.string.song_detail_server_intl, song.sheets.any { it.regionIntl }, activeServer == GameServer.INTL),
        ServerAvailabilityUi("USA", R.string.song_detail_server_usa, song.sheets.any { it.regionUsa }, false),
        ServerAvailabilityUi("CN", R.string.song_detail_server_cn, song.sheets.any { it.regionCn }, activeServer == GameServer.CN)
    )

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.song_detail_availability_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                servers.forEach { server ->
                    ServerAvailabilityChip(server)
                }
                LockStatusBadge(isLocked = song.isLocked)
            }
        }
    }
}

@Composable
private fun LockStatusBadge(isLocked: Boolean) {
    val tint = if (isLocked) Color(0xFFF39C12) else Color(0xFF2DBB73)
    Surface(
        color = tint.copy(alpha = 0.14f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (isLocked) Icons.Default.Lock else Icons.Default.LockOpen,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(14.dp)
            )
            Text(
                text = stringResource(if (isLocked) R.string.song_detail_unlock_required else R.string.song_detail_unlock_not_required),
                style = MaterialTheme.typography.labelLarge,
                color = tint,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun ServerAvailabilityChip(server: ServerAvailabilityUi) {
    val tint = when {
        server.highlighted && server.available -> MaterialTheme.colorScheme.primary
        server.available -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.outline
    }
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (server.available) tint.copy(alpha = 0.14f) else MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, tint.copy(alpha = if (server.available) 0.25f else 0.15f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(if (server.available) tint else tint.copy(alpha = 0.28f))
            )
            Text(
                text = server.shortCode,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
            if (server.highlighted) {
                Text(
                    text = stringResource(R.string.song_detail_server_current),
                    style = MaterialTheme.typography.labelSmall,
                    color = tint
                )
            }
        }
    }
}

@Composable
private fun ExternalLinksSection(song: Song, onOpen: (String) -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.song_detail_external_links),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AssistChip(
                    onClick = { onOpen("https://www.youtube.com/results?search_query=maimai+${encodeQuery(song.title)}") },
                    label = { Text(stringResource(R.string.song_detail_link_youtube)) },
                    trailingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                AssistChip(
                    onClick = { onOpen("https://search.bilibili.com/all?keyword=maimai+${encodeQuery(song.title)}") },
                    label = { Text(stringResource(R.string.song_detail_link_bilibili)) },
                    trailingIcon = {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
            }
        }
    }
}

@Composable
private fun SheetCard(
    sheet: Sheet,
    score: Score?,
    stat: ChartStatDto?,
    recentRecords: List<PlayRecord>,
    onRecordClick: () -> Unit
) {
    val diffColor = difficultyColor(sheet.difficulty, sheet.type)
    var expanded by rememberSaveable(sheet.sheetId) { mutableStateOf(false) }
    var historySortByDate by rememberSaveable(sheet.sheetId) { mutableStateOf(true) }
    var historyPage by rememberSaveable(sheet.sheetId) { mutableIntStateOf(1) }
    val levelValue = sheet.internalLevelValue ?: sheet.levelValue

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 16.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(
                    modifier = Modifier
                        .width(4.dp)
                        .height(76.dp)
                        .background(diffColor, RoundedCornerShape(999.dp))
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = displayDifficulty(sheet.difficulty),
                        style = MaterialTheme.typography.titleSmall,
                        color = diffColor,
                        fontWeight = FontWeight.Bold
                    )

                    sheet.noteDesigner?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (score == null) {
                        Text(
                            text = stringResource(R.string.song_detail_no_score),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatAchievement(score.rate),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            DetailBadge(text = score.rank, tint = diffColor)
                            score.fc?.takeIf { it.isNotBlank() }?.let {
                                DetailBadge(text = normalizeFc(it), tint = fcColor(it))
                            }
                            score.fs?.takeIf { it.isNotBlank() }?.let {
                                DetailBadge(text = normalizeFs(it), tint = fsColor(it))
                            }
                        }
                    }

                    if (score != null && score.dxScore > 0) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            DetailBadge(
                                text = stringResource(R.string.song_detail_dx_score_value, score.dxScore),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = sheet.internalLevel ?: sheet.level,
                        style = MaterialTheme.typography.headlineMedium,
                        color = diffColor,
                        fontWeight = FontWeight.Black
                    )
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = diffColor.copy(alpha = 0.18f)
                    )

                    stat?.let {
                        SheetDetailSection(
                            sectionKey = "${sheet.sheetId}/stats",
                            title = stringResource(R.string.song_detail_section_stats),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                StatTile(
                                    label = stringResource(R.string.song_detail_stats_fit_diff),
                                    value = stat.fitDiff?.let { "%.1f".format(Locale.US, it) } ?: "—"
                                )
                                StatTile(
                                    label = stringResource(R.string.song_detail_stats_avg_rate),
                                    value = stat.avg?.let { "%.4f%%".format(Locale.US, it) } ?: "—"
                                )
                                StatTile(
                                    label = stringResource(R.string.song_detail_stats_samples),
                                    value = stat.cnt?.toInt()?.toString() ?: "—"
                                )
                            }
                        }
                    }

                    SheetDetailSection(
                        sectionKey = "${sheet.sheetId}/notes",
                        title = stringResource(R.string.song_detail_section_notes),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        NoteBreakdownSection(sheet = sheet)
                    }

                    levelValue?.takeIf { it > 0 }?.let {
                        SheetDetailSection(
                            sectionKey = "${sheet.sheetId}/rating",
                            title = stringResource(R.string.song_detail_section_rating),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            RatingTable(level = it)
                        }
                    }

                    if (sheet.total != null) {
                        SheetDetailSection(
                            sectionKey = "${sheet.sheetId}/calculator",
                            title = stringResource(R.string.song_detail_section_calculator),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            FaultToleranceSection(sheet = sheet, diffColor = diffColor)
                        }
                    }

                    if (recentRecords.isNotEmpty()) {
                        SheetDetailSection(
                            sectionKey = "${sheet.sheetId}/history",
                            title = stringResource(R.string.song_detail_section_history),
                            modifier = Modifier.padding(horizontal = 16.dp)
                        ) {
                            RecentHistoryList(
                                records = recentRecords,
                                diffColor = diffColor,
                                historySortByDate = historySortByDate,
                                onSortChange = {
                                    historySortByDate = it
                                    historyPage = 1
                                },
                                historyPage = historyPage,
                                onHistoryPageChange = { historyPage = it }
                            )
                        }
                    }

                    Button(
                        onClick = onRecordClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.song_detail_action_record, displayDifficulty(sheet.difficulty)),
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun StatTile(label: String, value: String) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .width(110.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun RatingTable(level: Double) {
    val rows = remember(level) { ratingRows(level) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.song_detail_table_achievement),
                    modifier = Modifier.weight(1.2f),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.song_detail_table_rating),
                    modifier = Modifier.weight(0.7f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = stringResource(R.string.song_detail_table_delta),
                    modifier = Modifier.weight(0.6f),
                    textAlign = TextAlign.End,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${row.rank} · ${"%.4f".format(Locale.US, row.achievement)}%",
                        modifier = Modifier.weight(1.2f),
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = row.rating.toString(),
                        modifier = Modifier.weight(0.7f),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        text = row.delta?.let { "+$it" } ?: "—",
                        modifier = Modifier.weight(0.6f),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NoteBreakdownSection(sheet: Sheet) {
    val rows = remember(sheet) { noteBreakdownRows(sheet) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, row ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = row.label,
                        modifier = Modifier.width(46.dp),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(row.ratio.coerceIn(0f, 1f))
                                .height(8.dp)
                                .clip(RoundedCornerShape(999.dp))
                                .background(row.color.copy(alpha = 0.75f))
                        )
                    }

                    Text(
                        text = row.count.toString(),
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    Text(
                        text = "${(row.ratio * 100).toInt()}%",
                        modifier = Modifier.width(40.dp),
                        textAlign = TextAlign.End,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun FaultToleranceSection(sheet: Sheet, diffColor: Color) {
    val targetRanks = remember { ratingMilestones }
    var targetAchievement by rememberSaveable(sheet.sheetId) { mutableDoubleStateOf(100.5) }
    val results = remember(sheet, targetAchievement) { calculateFaultTolerance(sheet, targetAchievement) }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.song_detail_calculator_hint),
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(12.dp))
                DetailBadge(
                    text = "${"%.4f".format(Locale.US, targetAchievement)}%",
                    tint = diffColor
                )
            }

            FlowRow(
                modifier = Modifier.padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                targetRanks.forEach { milestone ->
                    FilterChip(
                        selected = targetAchievement == milestone.achievement,
                        onClick = { targetAchievement = milestone.achievement },
                        label = { Text(milestone.rank) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ToleranceInfoBox(
                    title = stringResource(R.string.song_detail_tolerance_great),
                    value = results.great,
                    color = Color(0xFFE26AA5)
                )
                ToleranceInfoBox(
                    title = stringResource(R.string.song_detail_tolerance_good),
                    value = results.good,
                    color = Color(0xFF56B870)
                )
                ToleranceInfoBox(
                    title = stringResource(R.string.song_detail_tolerance_miss),
                    value = results.miss,
                    color = Color(0xFF8B8B8B)
                )
            }
        }
    }
}

@Composable
private fun RowScope.ToleranceInfoBox(title: String, value: Int, color: Color) {
    Surface(
        modifier = Modifier.weight(1f),
        shape = RoundedCornerShape(16.dp),
        color = color.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = color,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.song_detail_calculator_cap),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RecentHistoryList(
    records: List<PlayRecord>,
    diffColor: Color,
    historySortByDate: Boolean,
    onSortChange: (Boolean) -> Unit,
    historyPage: Int,
    onHistoryPageChange: (Int) -> Unit
) {
    val sortedRecords = remember(records, historySortByDate) {
        if (historySortByDate) records.sortedByDescending { it.playDate } else records.sortedByDescending { it.rate }
    }
    val bestRecordId = remember(records) { records.maxByOrNull { it.rate }?.id }
    val itemsPerPage = 5
    val totalPages = maxOf(1, (sortedRecords.size + itemsPerPage - 1) / itemsPerPage)
    val validPage = historyPage.coerceIn(1, totalPages)
    val startIndex = (validPage - 1) * itemsPerPage
    val pageRecords = sortedRecords.drop(startIndex).take(itemsPerPage)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = historySortByDate,
                    onClick = { onSortChange(true) },
                    label = { Text(stringResource(R.string.song_detail_sort_time)) }
                )
                FilterChip(
                    selected = !historySortByDate,
                    onClick = { onSortChange(false) },
                    label = { Text(stringResource(R.string.song_detail_sort_rate)) }
                )
                Spacer(modifier = Modifier.weight(1f))
                if (totalPages > 1) {
                    Text(
                        text = "$validPage / $totalPages",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            pageRecords.forEachIndexed { index, record ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.width(72.dp)) {
                        Text(
                            text = formatHistoryDate(record.playDate),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = formatHistoryTime(record.playDate),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatAchievement(record.rate),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Bold
                            )
                            DetailBadge(text = record.rank, tint = diffColor)
                            if (record.id == bestRecordId) {
                                DetailBadge(text = stringResource(R.string.song_detail_history_best), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (record.dxScore > 0) {
                                DetailBadge(
                                    text = stringResource(R.string.song_detail_dx_score_value, record.dxScore),
                                    tint = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            record.fc?.takeIf { it.isNotBlank() }?.let {
                                DetailBadge(text = normalizeFc(it), tint = fcColor(it))
                            }
                            record.fs?.takeIf { it.isNotBlank() }?.let {
                                DetailBadge(text = normalizeFs(it), tint = fsColor(it))
                            }
                        }
                    }
                }
            }

            if (totalPages > 1) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = { onHistoryPageChange((validPage - 1).coerceAtLeast(1)) },
                        enabled = validPage > 1
                    ) {
                        Text(stringResource(R.string.song_detail_prev_page))
                    }
                    Text(
                        text = stringResource(R.string.song_detail_page_label, validPage, totalPages),
                        style = MaterialTheme.typography.labelLarge
                    )
                    TextButton(
                        onClick = { onHistoryPageChange((validPage + 1).coerceAtMost(totalPages)) },
                        enabled = validPage < totalPages
                    ) {
                        Text(stringResource(R.string.song_detail_next_page))
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailBadge(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun CommunityAliasCard(
    aliasItems: List<SongAliasItem>,
    approvedAliases: List<CommunityAliasApprovedAlias>,
    myCandidates: List<CommunityAliasMyCandidate>,
    draft: String,
    dailyCount: Int,
    isConfigured: Boolean,
    isLoggedIn: Boolean,
    isSubmitting: Boolean,
    onDraftChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onOpenBoard: () -> Unit,
    onLogin: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.community_alias_section_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(
                            R.string.community_alias_section_summary,
                            approvedAliases.size,
                            myCandidates.size
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedButton(onClick = onOpenBoard) {
                    Icon(Icons.Default.HowToVote, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.community_alias_action_open_board))
                }
            }

            if (aliasItems.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    aliasItems.forEach { item ->
                        AliasListRow(item = item)
                    }
                }
            }

            when {
                !isConfigured -> {
                    Text(
                        text = stringResource(R.string.community_alias_not_configured),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                !isLoggedIn -> {
                    Text(
                        text = stringResource(R.string.community_alias_login_required_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.AutoMirrored.Filled.Login, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.community_alias_action_login))
                    }
                }

                else -> {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(
                                R.string.community_alias_daily_quota,
                                dailyCount.coerceIn(0, 5),
                                5
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        LinearProgressIndicator(
                            progress = { dailyCount.coerceIn(0, 5) / 5f },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draft,
                            onValueChange = onDraftChange,
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.community_alias_submit_label)) },
                            placeholder = { Text(stringResource(R.string.community_alias_submit_placeholder)) },
                            enabled = !isSubmitting && dailyCount < 5
                        )
                        Button(
                            onClick = onSubmit,
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isSubmitting && draft.isNotBlank() && dailyCount < 5
                        ) {
                            Text(
                                text = if (isSubmitting) {
                                    stringResource(R.string.community_alias_submit_loading)
                                } else {
                                    stringResource(R.string.community_alias_submit_action)
                                }
                            )
                        }
                    }
                }
            }

            if (myCandidates.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = stringResource(R.string.community_alias_my_candidates_title),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    myCandidates.take(6).forEach { candidate ->
                        CommunityAliasCandidateRow(candidate = candidate)
                    }
                }
            }
        }
    }
}

@Composable
private fun AliasListRow(item: SongAliasItem) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AliasBadge(
                text = stringResource(
                    if (item.isCommunity) {
                        R.string.community_alias_source_community
                    } else {
                        R.string.community_alias_source_official
                    }
                )
            )
            Text(
                text = item.text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CommunityAliasCandidateRow(candidate: CommunityAliasMyCandidate) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = candidate.aliasText,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(12.dp))
                AliasBadge(text = stringResource(statusLabelRes(candidate.status)))
            }
            Text(
                text = stringResource(
                    R.string.community_alias_candidate_meta,
                    candidate.supportCount,
                    candidate.opposeCount,
                    formatCommunityAliasDate(candidate.updatedAt)
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AliasBadge(text: String) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}

@Composable
private fun SongMetadataCell(
    item: SongMetadataItem,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = item.tint.copy(alpha = 0.08f),
        shape = RoundedCornerShape(999.dp),
        border = BorderStroke(1.dp, item.tint.copy(alpha = 0.14f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = stringResource(item.labelRes),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = item.value,
                style = MaterialTheme.typography.titleSmall,
                color = item.tint,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SheetDetailSection(
    sectionKey: String,
    title: String,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(sectionKey) { mutableStateOf(initiallyExpanded) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            DetailSectionTitle(title)
            Spacer(modifier = Modifier.weight(1f))
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            content()
        }
    }
}

@Composable
private fun ScoreEditorDialog(
    sheet: Sheet,
    existingScore: Score?,
    onDismiss: () -> Unit,
    onSave: (Double, Int, String?, String?) -> Unit
) {
    val fcOptions = listOf("", "FC", "FC+", "AP", "AP+")
    val fsOptions = listOf("", "FS", "FS+", "FDX", "FDX+")

    var rate by remember(existingScore?.scoreKey) { mutableStateOf(existingScore?.rate?.toString().orEmpty()) }
    var dxScore by remember(existingScore?.scoreKey) { mutableStateOf(existingScore?.dxScore?.toString().orEmpty()) }
    var fc by remember(existingScore?.scoreKey) { mutableStateOf(existingScore?.fc.orEmpty()) }
    var fs by remember(existingScore?.scoreKey) { mutableStateOf(existingScore?.fs.orEmpty()) }

    val parsedRate = rate.toDoubleOrNull()
    val parsedDxScore = dxScore.toIntOrNull()
    val rankPreview = parsedRate?.let { RatingEngine.calculateRank(it) } ?: "—"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.song_detail_edit_title, displayDifficulty(sheet.difficulty))) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = rate,
                    onValueChange = { rate = it },
                    label = { Text(stringResource(R.string.song_detail_achievement)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = dxScore,
                    onValueChange = { dxScore = it },
                    label = { Text(stringResource(R.string.song_detail_dx_score)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = stringResource(R.string.song_detail_rank_preview, rankPreview),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(stringResource(R.string.song_detail_fc), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fcOptions.forEach { option ->
                        FilterChip(
                            selected = fc == option,
                            onClick = { fc = option },
                            label = { Text(option.ifBlank { stringResource(R.string.song_detail_none) }) }
                        )
                    }
                }
                Text(stringResource(R.string.song_detail_fs), style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    fsOptions.forEach { option ->
                        FilterChip(
                            selected = fs == option,
                            onClick = { fs = option },
                            label = { Text(option.ifBlank { stringResource(R.string.song_detail_none) }) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        parsedRate ?: 0.0,
                        parsedDxScore ?: 0,
                        fc.ifBlank { null },
                        fs.ifBlank { null }
                    )
                },
                enabled = parsedRate != null && parsedDxScore != null
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        }
    )
}

private data class ServerAvailabilityUi(
    val shortCode: String,
    val labelRes: Int,
    val available: Boolean,
    val highlighted: Boolean
)

private data class SongAliasItem(
    val text: String,
    val isCommunity: Boolean
)

private data class SongMetadataItem(
    val labelRes: Int,
    val value: String,
    val tint: Color
)

private data class NoteBreakdownRow(
    val label: String,
    val count: Int,
    val ratio: Float,
    val color: Color
)

private data class RankMilestone(
    val rank: String,
    val achievement: Double
)

private data class RatingRow(
    val rank: String,
    val achievement: Double,
    val rating: Int,
    val delta: Int?
)

private fun buildSongAliasItems(
    allAliases: List<String>,
    approvedAliases: List<CommunityAliasApprovedAlias>
): List<SongAliasItem> {
    val approvedSet = approvedAliases.map { it.aliasText.trim().lowercase() }.toSet()
    val seen = linkedSetOf<String>()
    val items = mutableListOf<SongAliasItem>()
    allAliases.forEach { alias ->
        val normalized = alias.trim()
        if (normalized.isEmpty()) {
            return@forEach
        }
        val key = normalized.lowercase()
        if (seen.add(key)) {
            items += SongAliasItem(
                text = normalized,
                isCommunity = approvedSet.contains(key)
            )
        }
    }
    approvedAliases.forEach { alias ->
        val normalized = alias.aliasText.trim()
        if (normalized.isEmpty()) {
            return@forEach
        }
        val key = normalized.lowercase()
        if (seen.add(key)) {
            items += SongAliasItem(text = normalized, isCommunity = true)
        }
    }
    return items
}

private fun statusLabelRes(status: String): Int = when (status.lowercase()) {
    "approved" -> R.string.community_alias_status_approved
    "rejected" -> R.string.community_alias_status_rejected
    else -> R.string.community_alias_status_voting
}

private fun formatCommunityAliasDate(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private fun difficultyColor(difficulty: String, type: String?): Color = when {
    difficulty.equals("basic", true) -> Color(0xFF36BF63)
    difficulty.equals("advanced", true) -> Color(0xFFFCA13B)
    difficulty.equals("expert", true) -> Color(0xFFF7536A)
    difficulty.equals("master", true) -> Color(0xFFA34EE4)
    difficulty.equals("remaster", true) -> Color(0xFFE3BDFC)
    type?.contains("utage", true) == true -> Color(0xFFEC48E9)
    else -> Color(0xFFE35D6A)
}

private fun displayDifficulty(difficulty: String): String = when (difficulty.lowercase()) {
    "remaster" -> "RE: MASTER"
    else -> difficulty.uppercase()
}

private fun formatAchievement(rate: Double): String = String.format(Locale.US, "%.4f%%", rate)

private fun compactVersionName(version: String): String {
    val trimmed = version
        .replace("maimai でらっくす", "", ignoreCase = true)
        .replace("maimai deluxe", "", ignoreCase = true)
        .replace("maimai dx", "", ignoreCase = true)
        .replace("maimai", "", ignoreCase = true)
        .trim()
        .replace(" PLUS", "+")

    return trimmed.ifBlank { version }
}

private fun encodeQuery(text: String): String = URLEncoder.encode(text, StandardCharsets.UTF_8.toString())

private fun formatCompactDate(date: String): String {
    val parts = date.split("-")
    return if (parts.size == 3) {
        "${parts[0].takeLast(2)}/${parts[1]}/${parts[2]}"
    } else {
        date
    }
}

private fun defaultType(sheets: List<Sheet>): String {
    val types = sheets.map { it.type.lowercase() }.distinct()
    return when {
        types.contains("dx") -> "dx"
        types.contains("std") -> "std"
        else -> types.firstOrNull().orEmpty()
    }
}

private fun parseChartStats(chartStatsJsonString: String?): Map<String, List<ChartStatDto>> {
    if (chartStatsJsonString.isNullOrBlank()) return emptyMap()
    return runCatching {
        chartStatsJson.decodeFromString<ChartStatsResponse>(chartStatsJsonString).charts
    }.getOrDefault(emptyMap())
}

private fun findMatchingStat(sheet: Sheet, stats: List<ChartStatDto>): ChartStatDto? {
    val index = when (sheet.difficulty.lowercase()) {
        "basic" -> 0
        "advanced" -> 1
        "expert" -> 2
        "master" -> 3
        "remaster" -> 4
        else -> return null
    }
    return stats.firstOrNull { it.diff == index.toString() }
        ?: stats.minByOrNull { abs((it.fitDiff ?: 0.0) - (sheet.internalLevelValue ?: sheet.levelValue ?: 0.0)) }
}

private fun noteBreakdownRows(sheet: Sheet): List<NoteBreakdownRow> {
    val totalWeight = listOf(
        (sheet.tap ?: 0) * 1.0,
        (sheet.hold ?: 0) * 2.0,
        (sheet.slide ?: 0) * 3.0,
        (sheet.touch ?: 0) * 1.0,
        (sheet.breakCount ?: 0) * 5.0
    ).sum()
    if (totalWeight <= 0.0) return emptyList()

    return buildList {
        addNoteBreakdownRow("TAP", sheet.tap, 1.0, totalWeight, Color(0xFFE76AA3))
        addNoteBreakdownRow("HOLD", sheet.hold, 2.0, totalWeight, Color(0xFFD780FF))
        addNoteBreakdownRow("SLIDE", sheet.slide, 3.0, totalWeight, Color(0xFF5B9CFF))
        addNoteBreakdownRow("TOUCH", sheet.touch, 1.0, totalWeight, Color(0xFF53C6D8))
        addNoteBreakdownRow("BREAK", sheet.breakCount, 5.0, totalWeight, Color(0xFFFFA24D))
    }
}

private fun MutableList<NoteBreakdownRow>.addNoteBreakdownRow(
    label: String,
    count: Int?,
    weight: Double,
    totalWeight: Double,
    color: Color
) {
    val actualCount = count ?: return
    if (actualCount <= 0) return
    val ratio = (((actualCount * weight) / totalWeight).toFloat()).coerceIn(0f, 1f)
    add(NoteBreakdownRow(label, actualCount, ratio, color))
}

private data class FaultToleranceResult(
    val great: Int,
    val good: Int,
    val miss: Int
)

private val ratingMilestones = listOf(
    RankMilestone("SSS+", 100.5),
    RankMilestone("SSS", 100.0),
    RankMilestone("SS+", 99.5),
    RankMilestone("SS", 99.0),
    RankMilestone("S+", 98.0),
    RankMilestone("S", 97.0),
    RankMilestone("AAA", 94.0),
    RankMilestone("AA", 90.0),
    RankMilestone("A", 80.0),
    RankMilestone("BBB", 75.0),
    RankMilestone("BB", 70.0),
    RankMilestone("B", 60.0),
    RankMilestone("C", 50.0),
    RankMilestone("D", 0.0)
)

private fun calculateFaultTolerance(sheet: Sheet, targetAchievement: Double): FaultToleranceResult {
    val totalBaseWeight = (sheet.tap ?: 0) * 1.0 +
        (sheet.hold ?: 0) * 2.0 +
        (sheet.slide ?: 0) * 3.0 +
        (sheet.touch ?: 0) * 1.0 +
        (sheet.breakCount ?: 0) * 5.0

    if (totalBaseWeight <= 0.0) return FaultToleranceResult(0, 0, 0)

    val maxAllowedLoss = 101.0 - targetAchievement
    if (maxAllowedLoss <= 0.0) return FaultToleranceResult(0, 0, 0)

    val tapGreatLoss = (0.2 / totalBaseWeight) * 100.0
    val tapGoodLoss = (0.5 / totalBaseWeight) * 100.0
    val tapMissLoss = (1.0 / totalBaseWeight) * 100.0
    val tapCount = sheet.tap ?: 0

    return FaultToleranceResult(
        great = minOf((maxAllowedLoss / tapGreatLoss).toInt(), tapCount),
        good = minOf((maxAllowedLoss / tapGoodLoss).toInt(), tapCount),
        miss = minOf((maxAllowedLoss / tapMissLoss).toInt(), tapCount)
    )
}

private fun ratingRows(level: Double): List<RatingRow> {
    val milestones = ratingMilestones
    return milestones.mapIndexed { index, milestone ->
        val rating = RatingEngine.calculateRating(level, milestone.achievement)
        val nextRating = milestones
            .getOrNull(index + 1)
            ?.achievement
            ?.let { RatingEngine.calculateRating(level, it) }
        RatingRow(milestone.rank, milestone.achievement, rating, nextRating?.let { rating - it })
    }
}

private fun normalizeFc(fc: String): String = when (fc.lowercase()) {
    "app" -> "AP+"
    "ap" -> "AP"
    "fcp" -> "FC+"
    "fc" -> "FC"
    else -> fc.uppercase()
}

private fun normalizeFs(fs: String): String = when (fs.lowercase()) {
    "fsdp" -> "FDX+"
    "fsd" -> "FDX"
    "fsp" -> "FS+"
    "fs" -> "FS"
    else -> fs.uppercase()
}

private fun fcColor(fc: String): Color = when {
    fc.contains("ap", ignoreCase = true) -> Color(0xFFFF9800)
    fc.contains("fc", ignoreCase = true) -> Color(0xFF37B24D)
    else -> Color(0xFF7A7A7A)
}

private fun fsColor(fs: String): Color = when {
    fs.contains("fsd", ignoreCase = true) -> Color(0xFF9C36FF)
    fs.contains("fs", ignoreCase = true) || fs.contains("sync", ignoreCase = true) -> Color(0xFF3D7CFF)
    else -> Color(0xFF7A7A7A)
}

private fun formatHistoryDate(timeMillis: Long): String =
    DateTimeFormatter.ofPattern("yy/MM/dd").format(Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()))

private fun formatHistoryTime(timeMillis: Long): String =
    DateTimeFormatter.ofPattern("HH:mm").format(Instant.ofEpochMilli(timeMillis).atZone(ZoneId.systemDefault()))
