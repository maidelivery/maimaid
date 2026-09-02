package org.rhythmeta.maimaid.ui.lettergame

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.WebSocket
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.LetterGameCreateRequest
import org.rhythmeta.maimaid.core.data.LetterGameEvent
import org.rhythmeta.maimaid.core.data.LetterGameFact
import org.rhythmeta.maimaid.core.data.LetterGameLogEntry
import org.rhythmeta.maimaid.core.data.LetterGameMatchPlayer
import org.rhythmeta.maimaid.core.data.LetterGameMatchSnapshot
import org.rhythmeta.maimaid.core.data.LetterGameMatchSong
import org.rhythmeta.maimaid.core.data.LetterGameRepository
import org.rhythmeta.maimaid.core.data.LetterGameRoom
import org.rhythmeta.maimaid.core.data.LetterGameRoomMember
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.ui.catalog.ChartTypeVersionBadge
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.appTextFieldColors
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.PullToRefresh
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import top.yukonga.miuix.kmp.window.WindowListPopup
import java.io.File
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

private enum class RoomVisibility { Public, Private }

@SuppressLint("SuspiciousIndentation")
@Composable
fun LetterGameScreen(
    container: AppContainer,
    contentTopPadding: Dp,
    onOpenLogin: () -> Unit = {},
    joinRequestToken: Int = 0,
    exitRequestToken: Int = 0,
    copyRequestToken: Int = 0,
    settingsRequestToken: Int = 0,
    onRoomPresenceChanged: (Boolean) -> Unit = {},
    onRoomCodeChanged: (String?) -> Unit = {},
    onMatchActiveChanged: (Boolean) -> Unit = {},
    onJoinActionAvailabilityChanged: (Boolean) -> Unit = {},
) {
    val session by container.backendSessionManager.state.collectAsStateWithLifecycle()
    val repository = container.letterGameRepository
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf(emptyList<LetterGameRoom>()) }
    var selectedRoom by remember { mutableStateOf<LetterGameRoom?>(null) }
    var savedRoomCode by rememberSaveable { mutableStateOf<String?>(null) }
    var match by remember { mutableStateOf<LetterGameMatchSnapshot?>(null) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var createVisibility by remember { mutableStateOf(RoomVisibility.Public) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var leavingRoom by remember { mutableStateOf(false) }
    var isLobbyRefreshing by remember { mutableStateOf(false) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var hiddenFinishedMatchId by rememberSaveable { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showRoomSettings by remember { mutableStateOf(false) }
    var handledJoinRequestToken by remember { mutableIntStateOf(joinRequestToken) }
    var handledExitRequestToken by remember { mutableIntStateOf(exitRequestToken) }
    var handledCopyRequestToken by remember { mutableIntStateOf(copyRequestToken) }
    var handledSettingsRequestToken by remember { mutableIntStateOf(settingsRequestToken) }
    val snackbarHostState = remember { SnackbarHostState() }
    val roomRefreshMutex = remember { Mutex() }
    val roomCodeCopiedMessage = stringResource(R.string.letter_game_room_code_copied)
    val hintAlreadyKnownMessage = stringResource(R.string.letter_game_hint_already_known)
    val ambiguousGuessMessage = stringResource(R.string.letter_game_ambiguous_guess)
    val removedFromRoomMessage = stringResource(R.string.letter_game_removed_from_room)
    val roomDissolvedMessage = stringResource(R.string.letter_game_room_dissolved)
    val gameVersions by container.catalogRepository.versions.collectAsStateWithLifecycle(emptyList())
    val songCategories by container.catalogRepository.categories.collectAsStateWithLifecycle(emptyList())
    val collections by container.songCollectionRepository.collections.collectAsStateWithLifecycle(emptyList())
    val collectionItems by container.songCollectionRepository.items.collectAsStateWithLifecycle(emptyList())
    val activeProfile by container.profileRepository.activeProfile.collectAsStateWithLifecycle(null)
    val localAvatarModel = remember(activeProfile?.avatarPath, activeProfile?.avatarUrl) {
        activeProfile?.avatarPath
            ?.takeIf(String::isNotBlank)
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: container.presetAvatarRepository.imageFileFor(activeProfile?.avatarUrl)
            ?: activeProfile?.avatarUrl?.takeIf(String::isNotBlank)
    }

    LaunchedEffect(Unit) { container.backendSessionManager.checkSession() }
    LaunchedEffect(joinRequestToken) {
        if (joinRequestToken != handledJoinRequestToken) {
            handledJoinRequestToken = joinRequestToken
            if (selectedRoom == null && session.isAuthenticated) showJoinDialog = true
        }
    }
    LaunchedEffect(exitRequestToken) {
        if (exitRequestToken != handledExitRequestToken) {
            handledExitRequestToken = exitRequestToken
            if (selectedRoom != null) showExitConfirmation = true
        }
    }
    LaunchedEffect(copyRequestToken) {
        if (copyRequestToken != handledCopyRequestToken) {
            handledCopyRequestToken = copyRequestToken
            selectedRoom?.let { room ->
                copyRoomCode(container.applicationContext, room.code)
                snackbarHostState.showSnackbar(roomCodeCopiedMessage, duration = SnackbarDuration.Short)
            }
        }
    }
    LaunchedEffect(settingsRequestToken) {
        if (settingsRequestToken != handledSettingsRequestToken) {
            handledSettingsRequestToken = settingsRequestToken
            if (selectedRoom != null) showRoomSettings = true
        }
    }
    LaunchedEffect(selectedRoom?.code, savedRoomCode, session.isAuthenticated) {
        val inRoom = selectedRoom != null || savedRoomCode != null
        onRoomPresenceChanged(inRoom)
        onRoomCodeChanged(selectedRoom?.code ?: savedRoomCode)
        onJoinActionAvailabilityChanged(!inRoom && session.isAuthenticated)
    }
    suspend fun refreshPublicRooms(showIndicator: Boolean) {
        roomRefreshMutex.withLock {
            if (showIndicator) isLobbyRefreshing = true
            try {
                runCatching { repository.listPublicRooms() }
                    .onSuccess {
                        rooms = it
                        if (showIndicator) errorMessage = null
                    }
                    .onFailure { errorMessage = it.message }
            } finally {
                if (showIndicator) isLobbyRefreshing = false
            }
        }
    }

    LaunchedEffect(session.user?.id) {
        if (session.user == null) {
            rooms = emptyList()
            selectedRoom = null
            savedRoomCode = null
            match = null
            return@LaunchedEffect
        }
        refreshPublicRooms(showIndicator = false)
    }
    LaunchedEffect(session.user?.id, selectedRoom?.id, savedRoomCode) {
        if (!session.isAuthenticated || selectedRoom != null || savedRoomCode != null) return@LaunchedEffect
        while (true) {
            delay(5_000.milliseconds)
            refreshPublicRooms(showIndicator = false)
        }
    }
    LaunchedEffect(session.user?.id, savedRoomCode) {
        val code = savedRoomCode ?: return@LaunchedEffect
        if (!session.isAuthenticated) return@LaunchedEffect
        runCatching { repository.getRoom(code) }
            .onSuccess { room ->
                if (savedRoomCode != code || leavingRoom) return@onSuccess
                val membership = room.members.firstOrNull { it.userId == session.user?.id }
                if (!leavingRoom && membership != null && membership.status !in setOf("accepted", "pending")) {
                    savedRoomCode = null
                    selectedRoom = null
                    match = null
                    errorMessage = null
                    scope.launch { snackbarHostState.showSnackbar(removedFromRoomMessage, duration = SnackbarDuration.Short) }
                } else {
                    selectedRoom = room
                }
            }
            .onFailure {
                savedRoomCode = null
                selectedRoom = null
                match = null
            }
    }
    LaunchedEffect(Unit) {
        repository.events.collect { event ->
            when (event) {
                is LetterGameEvent.Room -> {
                    rooms = rooms.map { if (it.id == event.room.id) event.room else it }
                    if (selectedRoom?.id == event.room.id) {
                        val membership = event.room.members.firstOrNull { it.userId == session.user?.id }
                        if (!leavingRoom && membership != null && membership.status !in setOf("accepted", "pending")) {
                            socket?.close(1000, "member_removed")
                            socket = null
                            selectedRoom = null
                            savedRoomCode = null
                            match = null
                            errorMessage = null
                            showRoomSettings = false
                            showExitConfirmation = false
                            hiddenFinishedMatchId = null
                            scope.launch { snackbarHostState.showSnackbar(removedFromRoomMessage, duration = SnackbarDuration.Short) }
                        } else {
                            selectedRoom = event.room
                        }
                    }
                }
                is LetterGameEvent.MemberRemoved -> {
                    if (!leavingRoom && (selectedRoom?.id == event.roomId || selectedRoom?.code == event.roomId)) {
                        socket?.close(1000, "member_removed")
                        socket = null
                        selectedRoom = null
                        savedRoomCode = null
                        match = null
                        errorMessage = null
                        showRoomSettings = false
                        showExitConfirmation = false
                        hiddenFinishedMatchId = null
                        scope.launch { snackbarHostState.showSnackbar(removedFromRoomMessage, duration = SnackbarDuration.Short) }
                    }
                }
                is LetterGameEvent.RoomDissolved -> {
                    if (!leavingRoom && (selectedRoom?.id == event.roomId || selectedRoom?.code == event.roomId)) {
                        socket?.close(1000, "room_dissolved")
                        socket = null
                        selectedRoom = null
                        savedRoomCode = null
                        match = null
                        errorMessage = null
                        showRoomSettings = false
                        showExitConfirmation = false
                        hiddenFinishedMatchId = null
                        scope.launch { snackbarHostState.showSnackbar(roomDissolvedMessage, duration = SnackbarDuration.Short) }
                    }
                }
                is LetterGameEvent.Match -> {
                    val isHiddenFinishedMatch = event.match.matchId == hiddenFinishedMatchId && event.match.status != "active"
                    if (!isHiddenFinishedMatch && selectedRoom != null && (match?.matchId == null || match?.matchId == event.match.matchId)) {
                        match = event.match
                    }
                }
                is LetterGameEvent.Error -> {
                    errorMessage = when (event.code) {
                        "hint_already_known" -> hintAlreadyKnownMessage
                        "ambiguous_song_guess" -> ambiguousGuessMessage
                        else -> event.message ?: event.code
                    }
                    if (event.code == "connection_failed" && selectedRoom != null) reconnectAttempt += 1
                    if (event.code == "stale_revision") {
                        match?.let { staleMatch ->
                            if (staleMatch.matchId != hiddenFinishedMatchId) {
                                scope.launch {
                                    runCatching { repository.getMatch(staleMatch.matchId) }
                                        .onSuccess { refreshed ->
                                            if (refreshed.matchId != hiddenFinishedMatchId || refreshed.status == "active") match = refreshed
                                        }
                                }
                            }
                        }
                    }
                }
                is LetterGameEvent.ActionAccepted -> Unit
            }
        }
    }
    LaunchedEffect(selectedRoom?.id, selectedRoom?.latestMatch?.id) {
        val room = selectedRoom ?: return@LaunchedEffect
        val latest = room.latestMatch ?: return@LaunchedEffect
        if (latest.id == hiddenFinishedMatchId && latest.status != "active") return@LaunchedEffect
        runCatching { repository.getMatch(latest.id) }
            .onSuccess { refreshed ->
                if (hiddenFinishedMatchId != latest.id || refreshed.status == "active") match = refreshed
            }
            .onFailure { errorMessage = it.message }
    }
    LaunchedEffect(match?.status) {
        if (match?.status == "active") hiddenFinishedMatchId = null
    }
    LaunchedEffect(selectedRoom?.id) {
        if (selectedRoom != null) leavingRoom = false
        val roomId = selectedRoom?.id ?: return@LaunchedEffect
        while (true) {
            delay(5_000.milliseconds)
            runCatching { repository.getRoom(roomId) }
                .onSuccess { refreshed ->
                    if (selectedRoom?.id != roomId) return@onSuccess
                    val membership = refreshed.members.firstOrNull { it.userId == session.user?.id }
                    if (!leavingRoom && membership != null && membership.status !in setOf("accepted", "pending")) {
                        socket?.close(1000, "member_removed")
                        socket = null
                        selectedRoom = null
                        savedRoomCode = null
                        match = null
                        errorMessage = null
                        showRoomSettings = false
                        showExitConfirmation = false
                        hiddenFinishedMatchId = null
                        scope.launch { snackbarHostState.showSnackbar(removedFromRoomMessage, duration = SnackbarDuration.Short) }
                    } else {
                        selectedRoom = refreshed
                    }
                }
                .onFailure { error ->
                    if ((error as? org.rhythmeta.maimaid.core.network.BackendApiException)?.code == "room_access_denied") {
                        socket?.close(1000, "member_removed")
                        socket = null
                        selectedRoom = null
                        savedRoomCode = null
                        match = null
                        errorMessage = null
                        showRoomSettings = false
                        showExitConfirmation = false
                        hiddenFinishedMatchId = null
                        scope.launch { snackbarHostState.showSnackbar(removedFromRoomMessage, duration = SnackbarDuration.Short) }
                    } else {
                        errorMessage = error.message
                    }
                }
        }
    }
    LaunchedEffect(selectedRoom?.code, reconnectAttempt) {
        if (reconnectAttempt == 0 || selectedRoom == null) return@LaunchedEffect
        delay((reconnectAttempt.coerceAtMost(5) * 1_000L).milliseconds)
        val room = selectedRoom ?: return@LaunchedEffect
        val membership = room.members.firstOrNull { it.userId == session.user?.id }
        if (membership?.status != "accepted") return@LaunchedEffect
        val reconnected = repository.connect(room.code)
        socket?.close(1000, "reconnecting")
        socket = reconnected
        val currentMatch = match
        if (reconnected != null && currentMatch != null) repository.resume(reconnected, currentMatch.matchId, currentMatch.revision)
    }
    DisposableEffect(
        selectedRoom?.code,
        selectedRoom?.members?.firstOrNull { it.userId == session.user?.id }?.status,
    ) {
        socket?.close(1000, "room changed")
        val opened = selectedRoom
            ?.takeIf { room -> room.members.firstOrNull { it.userId == session.user?.id }?.status == "accepted" }
            ?.let { repository.connect(it.code) }
        socket = opened
        val previousMatch = match
        if (opened != null && previousMatch != null) {
            repository.resume(opened, previousMatch.matchId, previousMatch.revision)
        }
        onDispose {
            opened?.close(1000, "screen closed")
            if (socket === opened) socket = null
        }
    }

    val room = selectedRoom
    val visibleMatch = match
    LaunchedEffect(visibleMatch?.status) {
        onMatchActiveChanged(visibleMatch?.status == "active")
    }
    if (!session.isAuthenticated) {
        LoginRequired(contentTopPadding, onOpenLogin)
        return
    }
    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
        targetState = room to visibleMatch,
        contentKey = { (targetRoom, _) -> targetRoom?.id ?: "lobby" },
        modifier = Modifier.fillMaxSize(),
        transitionSpec = {
            val slideAnimationSpec = tween<IntOffset>(durationMillis = 280)
            val fadeAnimationSpec = tween<Float>(durationMillis = 220)
            if (targetState.first == null) {
                (slideInHorizontally(slideAnimationSpec) { width -> -width / 3 } + fadeIn(fadeAnimationSpec)) togetherWith
                    (slideOutHorizontally(slideAnimationSpec) { width -> width / 3 } + fadeOut(fadeAnimationSpec))
            } else {
                (slideInHorizontally(slideAnimationSpec) { width -> width / 3 } + fadeIn(fadeAnimationSpec)) togetherWith
                    (slideOutHorizontally(slideAnimationSpec) { width -> -width / 3 } + fadeOut(fadeAnimationSpec))
            }
        },
        label = "letter-game-room-transition",
        ) { (animatedRoom, animatedMatch) ->
        if (animatedRoom == null) {
            if (savedRoomCode != null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(stringResource(R.string.letter_game_restoring_room), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            } else LobbyPage(
                rooms = rooms,
                contentTopPadding = contentTopPadding,
                createVisibility = createVisibility,
                loading = loading,
                isRefreshing = isLobbyRefreshing,
                errorMessage = errorMessage,
                onVisibilityChange = { createVisibility = it },
                onRefresh = { scope.launch { refreshPublicRooms(showIndicator = true) } },
                onCreate = {
                    scope.launch {
                        loading = true
                        errorMessage = null
                        runCatching {
                            repository.createRoom(
                                LetterGameCreateRequest(visibility = createVisibility.name.lowercase()),
                            )
                        }.onSuccess {
                            savedRoomCode = it.code
                            selectedRoom = it
                        }
                            .onFailure { errorMessage = it.message }
                        loading = false
                    }
                },
                onJoinPublic = { publicRoom ->
                    scope.launch {
                        loading = true
                        errorMessage = null
                        runCatching { repository.joinRoom(publicRoom.code) }
                            .onSuccess {
                                savedRoomCode = it.code
                                selectedRoom = it
                            }
                            .onFailure { errorMessage = it.message }
                        loading = false
                    }
                },
            )
        } else {
            val currentUserId = session.user?.id
                    when (animatedMatch?.status) {
                        "active" -> PlayingPage(
                                match = animatedMatch,
                                    currentUserId = currentUserId,
                                    localAvatarModel = localAvatarModel,
                                    englishOnly = animatedRoom.settings.selectionConfig["englishOnly"]?.jsonPrimitive?.booleanOrNull == true,
                                publicHintCost = animatedRoom.settings.publicHintCost,
                                privateHintCost = animatedRoom.settings.privateHintCost,
                                socket = socket,
                                repository = repository,
                                coverImageStore = container.coverImageStore,
                                versions = gameVersions,
                                contentTopPadding = contentTopPadding,
                                errorMessage = errorMessage,
                                onShowSnackbar = { message ->
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            message = message,
                                            withDismissAction = true,
                                            duration = SnackbarDuration.Custom(5_000),
                                        )
                                    }
                                },
                                onMatchRefresh = { refreshed -> match = refreshed },
                            )
                                "finished", "abandoned" -> ResultsPage(
                                    match = animatedMatch,
                                    currentUserId = currentUserId,
                                    localAvatarModel = localAvatarModel,
                                    coverImageStore = container.coverImageStore,
                                    versions = gameVersions,
                                    contentTopPadding = contentTopPadding,
                                    onReopen = {
                                        hiddenFinishedMatchId = animatedMatch.matchId
                                        scope.launch {
                                        runCatching { repository.reopenRoom(animatedRoom.id) }
                                                .onSuccess {
                                                        savedRoomCode = it.code
                                                        selectedRoom = it
                                                        hiddenFinishedMatchId = animatedMatch.matchId
                                                        match = null
                                                    }
                                                    .onFailure {
                                                        hiddenFinishedMatchId = null
                                                    errorMessage = it.message
                                                }
                                    }
                                },
                                onExit = { showExitConfirmation = true },
                            )
                            else -> WaitingPage(
                                    room = animatedRoom,
                                    currentUserId = currentUserId,
                                    localAvatarModel = localAvatarModel,
                                    contentTopPadding = contentTopPadding,
                                repository = repository,
                                loading = loading,
                                errorMessage = errorMessage,
                                onUpdateRoom = { updated ->
                                    savedRoomCode = updated.code
                                    selectedRoom = updated
                                },
                                onError = { errorMessage = it },
                                onStart = {
                                    scope.launch {
                                        loading = true
                                        runCatching { repository.startMatch(animatedRoom.id) }
                                                .onSuccess {
                                                    hiddenFinishedMatchId = null
                                                    match = it
                                                }
                                            .onFailure { errorMessage = it.message }
                                        loading = false
                                    }
                                },
                                onLeave = { showExitConfirmation = true },
                            )
                    }
        }
        }
        SnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .imePadding()
                .navigationBarsPadding()
                .padding(
                    start = 16.dp,
                    end = 16.dp,
                    top = 16.dp,
                    bottom = if (visibleMatch?.status == "active") 112.dp else 16.dp,
                ),
        )
    }

    if (room == null && showJoinDialog) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.letter_game_join_game),
            onDismissRequest = { showJoinDialog = false },
        ) {
            TextField(
                value = joinCode,
                onValueChange = { joinCode = it.filter(Char::isLetterOrDigit).take(6).uppercase() },
                label = stringResource(R.string.letter_game_room_code),
                singleLine = true,
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    scope.launch {
                        loading = true
                        errorMessage = null
                        runCatching { repository.joinRoom(joinCode) }
                            .onSuccess {
                                savedRoomCode = it.code
                                selectedRoom = it
                                showJoinDialog = false
                                joinCode = ""
                            }
                            .onFailure { errorMessage = it.message }
                        loading = false
                    }
                },
                enabled = joinCode.length == 6 && !loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(stringResource(R.string.letter_game_join)) }
        }
    }

    if (room != null && showExitConfirmation) {
        WindowDialog(
            show = true,
            title = stringResource(R.string.letter_game_leave_room_confirm_title),
            onDismissRequest = { showExitConfirmation = false },
        ) {
            Text(
                stringResource(R.string.letter_game_leave_room_confirm_summary),
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.height(14.dp))
            TextButton(
                text = stringResource(R.string.letter_game_cancel),
                onClick = { showExitConfirmation = false },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Button(
                onClick = {
                    scope.launch {
                        leavingRoom = true
                        loading = true
                        runCatching { repository.leaveRoom(room.id) }
                            .onSuccess {
                                showExitConfirmation = false
                                savedRoomCode = null
                                selectedRoom = null
                                match = null
                                hiddenFinishedMatchId = null
                            }
                            .onFailure {
                                leavingRoom = false
                                errorMessage = it.message
                            }
                        loading = false
                    }
                },
                enabled = !loading,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.letter_game_leave_room))
            }
        }
    }

    if (room != null) {
        LetterGameRoomSettingsSheet(
            visible = showRoomSettings,
            room = room,
            currentUserId = session.user?.id,
            matchInProgress = visibleMatch?.status == "active",
            gameVersions = gameVersions,
            songCategories = songCategories,
            collections = collections,
            collectionItems = collectionItems,
            onDismiss = { showRoomSettings = false },
            onUpdate = { request ->
                repository.updateRoom(room.id, request).also {
                    savedRoomCode = it.code
                    selectedRoom = it
                }
            },
            onError = { errorMessage = it },
        )
    }

}

@Composable
private fun LoginRequired(contentTopPadding: Dp, onOpenLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = contentTopPadding + 24.dp, start = 20.dp, end = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(Icons.Rounded.Lock, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(32.dp))
        Text(stringResource(R.string.letter_game_login_required), style = MiuixTheme.textStyles.title2)
        Text(stringResource(R.string.letter_game_login_required_summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Button(onClick = onOpenLogin, colors = ButtonDefaults.buttonColorsPrimary()) {
            Icon(Icons.Rounded.Lock, contentDescription = null)
            Spacer(Modifier.width(6.dp))
            Text(stringResource(R.string.cloud_login))
        }
    }
}

@Composable
private fun LobbyPage(
    rooms: List<LetterGameRoom>,
    contentTopPadding: Dp,
    createVisibility: RoomVisibility,
    loading: Boolean,
    isRefreshing: Boolean,
    errorMessage: String?,
    onVisibilityChange: (RoomVisibility) -> Unit,
    onRefresh: () -> Unit,
    onCreate: () -> Unit,
    onJoinPublic: (LetterGameRoom) -> Unit,
) {
    PullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = contentTopPadding),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 8.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                LobbySectionTitle(
                    text = stringResource(R.string.letter_game_join_title),
                    icon = Icons.Rounded.AddHome,
                )
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 14.dp,
                    insideMargin = PaddingValues(14.dp),
                    colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        VisibilityButton(RoomVisibility.Public, createVisibility == RoomVisibility.Public, onVisibilityChange, Modifier.weight(1f))
                        VisibilityButton(RoomVisibility.Private, createVisibility == RoomVisibility.Private, onVisibilityChange, Modifier.weight(1f))
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onCreate,
                        enabled = !loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(if (createVisibility == RoomVisibility.Public) Icons.Rounded.Public else Icons.Rounded.Lock, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(
                                if (createVisibility == RoomVisibility.Public) {
                                    R.string.letter_game_create_public
                                } else {
                                    R.string.letter_game_create_private
                                },
                            ),
                        )
                    }
                }
            }
            item {
                LobbySectionTitle(
                    text = stringResource(R.string.letter_game_public_rooms),
                    icon = Icons.Rounded.Public,
                )
            }
            if (rooms.isEmpty()) {
                item { Text(stringResource(R.string.letter_game_no_public_rooms), color = MiuixTheme.colorScheme.onSurfaceVariantSummary) }
            } else {
                items(rooms, key = LetterGameRoom::id) { room ->
                    PublicRoomRow(room = room, onJoin = { onJoinPublic(room) })
                }
            }
            if (errorMessage != null) item { ErrorBanner(errorMessage) }
        }
    }
}

@Composable
private fun LobbySectionTitle(text: String, icon: ImageVector) {
    Row(
        modifier = Modifier.padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MiuixTheme.colorScheme.onBackgroundVariant,
        )
        Text(
            text = text,
            style = MiuixTheme.textStyles.subtitle,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun VisibilityButton(
    visibility: RoomVisibility,
    selected: Boolean,
    onSelected: (RoomVisibility) -> Unit,
    modifier: Modifier = Modifier,
) {
    Button(
        onClick = { onSelected(visibility) },
        modifier = modifier,
        colors = if (selected) ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors(),
    ) {
        Icon(if (visibility == RoomVisibility.Public) Icons.Rounded.Public else Icons.Rounded.Lock, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(
            stringResource(
                if (visibility == RoomVisibility.Public) R.string.letter_game_public else R.string.letter_game_private,
            ),
        )
    }
}

@Composable
private fun PublicRoomRow(room: LetterGameRoom, onJoin: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Rounded.Public, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(room.code, style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
                Text(stringResource(R.string.letter_game_member_count, room.memberCount), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            Button(onClick = onJoin, colors = ButtonDefaults.buttonColorsPrimary()) { Text(stringResource(R.string.letter_game_join)) }
        }
    }
}

@Composable
private fun WaitingPage(
    room: LetterGameRoom,
    currentUserId: String?,
    localAvatarModel: Any?,
    contentTopPadding: Dp,
    repository: LetterGameRepository,
    loading: Boolean,
    errorMessage: String?,
    onUpdateRoom: (LetterGameRoom) -> Unit,
    onError: (String) -> Unit,
    onStart: () -> Unit,
    onLeave: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val isHost = room.hostUserId == currentUserId
    val matchInProgress = room.latestMatch?.status == "active"

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            RoomMembers(
                members = room.members,
                hostUserId = room.hostUserId,
                currentUserId = currentUserId,
                localAvatarModel = localAvatarModel,
                isHost = isHost,
                onApprove = { member ->
                    member.id?.let { memberId ->
                        scope.launch {
                            runCatching { repository.approveMember(room.id, memberId) }
                                .onSuccess(onUpdateRoom)
                                .onFailure { onError(it.message ?: "Request failed") }
                        }
                    }
                },
                onKick = { member ->
                    member.id?.let { memberId ->
                        scope.launch {
                            runCatching { repository.kickMember(room.id, memberId) }
                                .onSuccess(onUpdateRoom)
                                .onFailure { onError(it.message ?: "Request failed") }
                        }
                    }
                },
                onReject = { member ->
                    member.id?.let { memberId ->
                        scope.launch {
                            runCatching { repository.rejectMember(room.id, memberId) }
                                .onSuccess(onUpdateRoom)
                                .onFailure { onError(it.message ?: "Request failed") }
                        }
                    }
                },
            )
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onStart, enabled = isHost && !loading && !matchInProgress, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                    Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.letter_game_start))
                }
                Button(onClick = onLeave, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.letter_game_leave_room))
                }
            }
        }
        item { RoomSettingsSummary(room) }
        if (errorMessage != null) item { ErrorBanner(errorMessage) }
    }
}

@Composable
private fun RoomSettingsSummary(room: LetterGameRoom) {
    val settings = room.settings
    val config = settings.selectionConfig
    val listSeparator = stringResource(R.string.letter_game_summary_list_separator)
    val hostMode = stringResource(
        if (room.hostMode == "rotate") {
            R.string.letter_game_summary_host_rotate
        } else {
            R.string.letter_game_summary_host_fixed
        },
    )
    val source = if (settings.selectionMode == "collection") {
        val collectionNames = settings.selectedCollections.joinToString(listSeparator) { it.name }
        if (collectionNames.isBlank()) {
            stringResource(R.string.letter_game_source_collection)
        } else {
            stringResource(R.string.letter_game_summary_source_collection, collectionNames)
        }
    } else {
        stringResource(R.string.letter_game_summary_source_random)
    }
    val filters = mutableListOf<String>()
    if (config["excludeDeleted"]?.jsonPrimitive?.booleanOrNull != false) {
        filters += stringResource(R.string.letter_game_exclude_deleted)
    }
    if (config["englishOnly"]?.jsonPrimitive?.booleanOrNull != false) {
        filters += stringResource(R.string.letter_game_english_only)
    }
    val minVersion = config.summaryString("minVersion")
    val maxVersion = config.summaryString("maxVersion")
    if (minVersion != null || maxVersion != null) {
        filters += stringResource(
            R.string.letter_game_summary_version_range,
            minVersion ?: stringResource(R.string.letter_game_summary_earliest_version),
            maxVersion ?: stringResource(R.string.letter_game_summary_latest_version),
        )
    }
    val categories = config.summaryStrings("categories")
    if (categories.isNotEmpty()) {
        filters += stringResource(R.string.letter_game_summary_categories, categories.joinToString(listSeparator))
    }
    val chartTypes = config.summaryStrings("chartTypes")
        .mapNotNull { type ->
            when (type.lowercase()) {
                "standard", "std", "sd" -> "STD"
                "dx" -> "DX"
                else -> null
            }
        }
        .distinct()
    if (chartTypes.isNotEmpty()) {
        filters += stringResource(R.string.letter_game_summary_chart_types, chartTypes.joinToString(listSeparator))
    }
    val filterSummary = if (filters.isEmpty()) {
        stringResource(R.string.letter_game_summary_no_filters)
    } else {
        stringResource(R.string.letter_game_summary_filters, filters.joinToString(listSeparator))
    }
    val acceptedPlayerCount = room.members.count { it.status == "accepted" }.coerceAtLeast(1)
    val songCount = settings.songCountOverride ?: (acceptedPlayerCount * 3)
    val overview = stringResource(
        R.string.letter_game_room_settings_overview,
        hostMode,
        settings.turnDurationSeconds,
        settings.stalledRoundLimit,
        source,
        songCount,
        filterSummary,
        settings.publicHintCost,
        settings.privateHintCost,
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Icon(
                imageVector = Icons.Rounded.Tune,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            SmallTitle(
                text = stringResource(R.string.letter_game_room_settings),
                insideMargin = PaddingValues(0.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = overview,
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}

private fun Map<String, kotlinx.serialization.json.JsonElement>.summaryString(key: String): String? =
    runCatching { get(key)?.jsonPrimitive?.contentOrNull?.takeIf(String::isNotBlank) }.getOrNull()

private fun Map<String, kotlinx.serialization.json.JsonElement>.summaryStrings(key: String): List<String> =
    runCatching {
        get(key)?.jsonArray?.mapNotNull { value -> value.jsonPrimitive.contentOrNull?.takeIf(String::isNotBlank) }.orEmpty()
    }.getOrDefault(emptyList())

@Composable
private fun RoomMembers(
    members: List<LetterGameRoomMember>,
    hostUserId: String,
    currentUserId: String?,
    localAvatarModel: Any?,
    isHost: Boolean,
    onApprove: (LetterGameRoomMember) -> Unit,
    onKick: (LetterGameRoomMember) -> Unit,
    onReject: (LetterGameRoomMember) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            SmallTitle(
                text = stringResource(R.string.letter_game_players),
                insideMargin = PaddingValues(0.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        members.filter { it.status == "accepted" || it.status == "pending" }.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                PlayerAvatar(
                    url = member.avatarUrl,
                    name = member.displayName ?: member.userId,
                    size = 36.dp,
                    localModel = localAvatarModel.takeIf { member.userId == currentUserId },
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(member.displayName ?: member.userId, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when {
                            member.userId == hostUserId -> stringResource(R.string.letter_game_host)
                            member.status == "pending" -> stringResource(R.string.letter_game_waiting_approval)
                            member.userId == currentUserId -> stringResource(R.string.letter_game_you)
                            else -> stringResource(R.string.letter_game_ready)
                        },
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        style = MiuixTheme.textStyles.footnote1,
                    )
                }
                if (isHost && member.status == "pending") {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Button(
                            onClick = { onApprove(member) },
                            modifier = Modifier.heightIn(min = 36.dp),
                            colors = ButtonDefaults.buttonColors(),
                        ) {
                            Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.letter_game_approve))
                        }
                        Button(
                            onClick = { onReject(member) },
                            modifier = Modifier.heightIn(min = 36.dp),
                            colors = ButtonDefaults.buttonColors(),
                        ) {
                            Icon(Icons.Rounded.Cancel, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.letter_game_reject))
                        }
                    }
                }
                if (isHost && member.userId != hostUserId && member.status == "accepted") {
                    Button(
                        onClick = { onKick(member) },
                        modifier = Modifier.heightIn(min = 36.dp),
                        colors = ButtonDefaults.buttonColors(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.letter_game_remove))
                    }
                }
            }
        }
    }
}

@Composable
private fun PlayingPage(
    match: LetterGameMatchSnapshot,
    currentUserId: String?,
    localAvatarModel: Any?,
    englishOnly: Boolean,
    publicHintCost: Int,
    privateHintCost: Int,
    socket: WebSocket?,
    repository: LetterGameRepository,
    coverImageStore: org.rhythmeta.maimaid.core.data.CoverImageStore,
    versions: List<GameVersionEntity>,
    contentTopPadding: Dp,
    errorMessage: String?,
    onShowSnackbar: (String) -> Unit,
    onMatchRefresh: (LetterGameMatchSnapshot) -> Unit,
) {
    var inputText by remember { mutableStateOf("") }
    var hintTarget by remember { mutableStateOf<LetterGameMatchSong?>(null) }
    var hintType by remember { mutableStateOf("version") }
    var hintVisibility by remember { mutableStateOf("public") }
    var secondsLeft by remember { mutableIntStateOf(0) }
    var showInputTip by rememberSaveable(match.matchId) { mutableStateOf(true) }
    val isTurn = match.turnUserId == currentUserId
    val canAct = isTurn && socket != null
    val currentPlayer = match.players.firstOrNull { it.userId == currentUserId }
    val canBuyHint = canAct && currentPlayer?.scoringEligible == true
    val currentScore = currentPlayer?.score ?: 0
    val narratedLogs = remember(match.logs) { LetterGameLogNarrator.narrate(match.logs) }
    val snackbarMessages = narratedLogs.associate { narration ->
        narration.logId to localizedLogMessage(narration)
    }
    var logBaselineEstablished by remember(match.matchId) { mutableStateOf(false) }
    var shownLogIds by remember(match.matchId) { mutableStateOf<Set<String>>(emptySet()) }
    val isCharacterInput = inputText.isNotEmpty() && firstGrapheme(inputText) == inputText
    val canSubmitInput = inputText.isNotEmpty() && (isCharacterInput || inputText.isNotBlank())
    val inputLabel = when {
        inputText.isEmpty() -> stringResource(R.string.letter_game_input_placeholder)
        isCharacterInput -> stringResource(R.string.letter_game_input_character_placeholder)
        else -> stringResource(R.string.letter_game_input_guess_placeholder)
    }
    val inputActionLabel = when {
        inputText.isEmpty() -> stringResource(R.string.letter_game_submit_input)
        isCharacterInput -> stringResource(R.string.letter_game_open)
        else -> stringResource(R.string.letter_game_guess)
    }

    LaunchedEffect(match.matchId, match.logs) {
        val currentIds = match.logs.mapTo(mutableSetOf(), LetterGameLogEntry::id)
        if (!logBaselineEstablished) {
            shownLogIds = currentIds
            logBaselineEstablished = true
        } else {
            val newLogs = match.logs.filterNot { it.id in shownLogIds }
            shownLogIds = shownLogIds + currentIds
            newLogs.forEach { log ->
                onShowSnackbar(snackbarMessages[log.id] ?: log.message)
            }
        }
    }
    LaunchedEffect(match.matchId, match.turnDeadline, match.revision) {
        val deadline = match.turnDeadline?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
        if (deadline == null) {
            secondsLeft = 0
            return@LaunchedEffect
        }
        while (true) {
            secondsLeft = ((deadline.toEpochMilli() - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0)
            if (secondsLeft > 0) {
                delay(1_000.milliseconds)
                continue
            }
            if (match.status != "active") break
            val refreshed = runCatching { repository.getMatch(match.matchId) }.getOrNull()
            if (refreshed != null && (refreshed.revision != match.revision || refreshed.turnDeadline != match.turnDeadline || refreshed.status != match.status)) {
                onMatchRefresh(refreshed)
                break
            }
            delay(1_000.milliseconds)
        }
    }
    val send: (JsonObject) -> Unit = { payload ->
        if (socket != null) {
            repository.sendAction(socket, match.matchId, match.revision, UUID.randomUUID().toString(), payload)
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 8.dp, end = 16.dp, bottom = 136.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
        if (showInputTip) {
            item { InputMechanismTip(onDismiss = { showInputTip = false }) }
        }
        if (errorMessage != null) item { ErrorBanner(errorMessage) }
        item { TurnStrip(match.players, match.turnUserId, currentUserId, localAvatarModel) }
        if (englishOnly) item { EnglishLetterProgress(match.logs) }
        items(match.songs, key = LetterGameMatchSong::slotId) { song ->
            LetterSongCard(
                song = song,
                coverImageStore = coverImageStore,
                versions = versions,
                onLongClick = { if (song.status == "active") hintTarget = song },
            )
        }
        }

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                insideMargin = PaddingValues(10.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Timer,
                            contentDescription = null,
                            tint = if (isTurn) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Text(
                            if (isTurn) stringResource(R.string.letter_game_your_turn, secondsLeft)
                            else stringResource(R.string.letter_game_waiting_turn_timer, secondsLeft),
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            label = inputLabel,
                            enabled = canAct,
                            singleLine = true,
                            colors = appTextFieldColors(),
                            modifier = Modifier.weight(1f),
                        )
                        Button(
                            onClick = {
                                val value = inputText
                                if (canSubmitInput) {
                                    send(buildJsonObject {
                                        if (isCharacterInput) {
                                            put("kind", "open_character")
                                            put("character", value)
                                        } else {
                                            put("kind", "guess_song")
                                            put("guess", value)
                                        }
                                    })
                                    inputText = ""
                                }
                            },
                            enabled = canAct && canSubmitInput,
                            colors = ButtonDefaults.buttonColorsPrimary(),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                            Spacer(Modifier.width(5.dp))
                            Text(inputActionLabel)
                        }
                    }
                }
            }
        }
    }

    hintTarget?.let { target ->
        val hintOptions = buildList {
            add("version")
            add("white_chart")
            add("constant")
        }
        val selectedHintType = hintType.takeIf { it in hintOptions } ?: hintOptions.first()
        val hintCost = if (hintVisibility == "public") publicHintCost else privateHintCost
        val canAffordHint = currentScore >= hintCost
        val hintAlreadyKnown = target.facts.any { it.isKnownHint(selectedHintType) }
        WindowDialog(show = true, title = stringResource(R.string.letter_game_buy_hint), onDismissRequest = { hintTarget = null }) {
            HintDropdown(
                title = stringResource(R.string.letter_game_hint_type),
                selected = selectedHintType,
                options = hintOptions,
                optionLabel = { option ->
                    when (option) {
                        "version" -> stringResource(R.string.letter_game_hint_version)
                        "white_chart" -> stringResource(R.string.letter_game_hint_white_chart)
                        else -> stringResource(R.string.letter_game_hint_max_constant)
                    }
                },
                onSelect = { hintType = it },
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { hintVisibility = "public" }, modifier = Modifier.weight(1f), colors = if (hintVisibility == "public") ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) { Text(stringResource(R.string.letter_game_public)) }
                Button(onClick = { hintVisibility = "private" }, modifier = Modifier.weight(1f), colors = if (hintVisibility == "private") ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) { Text(stringResource(R.string.letter_game_private)) }
            }
            if (selectedHintType == "constant") {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.letter_game_hint_max_constant),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            if (hintAlreadyKnown) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.letter_game_hint_already_known),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    style = MiuixTheme.textStyles.footnote1,
                )
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    send(buildJsonObject {
                        put("kind", "buy_hint")
                        put("slotId", target.slotId)
                        put("hintType", selectedHintType)
                        put("visibility", hintVisibility)
                    })
                    hintTarget = null
                },
                enabled = canBuyHint && canAffordHint && !hintAlreadyKnown,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Text(stringResource(R.string.letter_game_purchase_with_balance, hintCost, currentScore))
            }
        }
    }
}

@Composable
private fun localizedLogMessage(narration: LetterGameLogNarration): String {
    val template = narration.template
    val hintType = when (narration.hintType) {
        "version" -> stringResource(R.string.letter_game_hint_version)
        "constant" -> stringResource(R.string.letter_game_hint_max_constant)
        "white_chart" -> stringResource(R.string.letter_game_hint_remaster_log)
        else -> stringResource(R.string.letter_game_buy_hint)
    }
    val hintResult = when (narration.hintResult) {
        true -> stringResource(R.string.letter_game_hint_remaster_yes)
        false -> stringResource(R.string.letter_game_hint_remaster_no)
        null -> ""
    }
    return when (template) {
        LetterGameLogTemplate.OPEN_NO_PROGRESS_1 -> stringResource(R.string.letter_game_log_open_no_progress_1, narration.actor, narration.character)
        LetterGameLogTemplate.OPEN_NO_PROGRESS_2 -> stringResource(R.string.letter_game_log_open_no_progress_2, narration.actor, narration.character)
        LetterGameLogTemplate.OPEN_NO_PROGRESS_STREAK_1 -> stringResource(R.string.letter_game_log_open_no_progress_streak_1, narration.actor, narration.streak)
        LetterGameLogTemplate.OPEN_NO_PROGRESS_STREAK_2 -> stringResource(R.string.letter_game_log_open_no_progress_streak_2, narration.actor, narration.streak)
        LetterGameLogTemplate.OPEN_REPEATED_1 -> stringResource(R.string.letter_game_log_open_repeated_1, narration.actor, narration.character, narration.count, narration.points)
        LetterGameLogTemplate.OPEN_REPEATED_2 -> stringResource(R.string.letter_game_log_open_repeated_2, narration.actor, narration.character, narration.count, narration.points)
        LetterGameLogTemplate.OPEN_FEW_1 -> stringResource(R.string.letter_game_log_open_few_1, narration.actor, narration.count, narration.points)
        LetterGameLogTemplate.OPEN_FEW_2 -> stringResource(R.string.letter_game_log_open_few_2, narration.actor, narration.count, narration.points)
        LetterGameLogTemplate.OPEN_MANY_1 -> stringResource(R.string.letter_game_log_open_many_1, narration.actor, narration.count, narration.points)
        LetterGameLogTemplate.OPEN_MANY_2 -> stringResource(R.string.letter_game_log_open_many_2, narration.actor, narration.count, narration.points)
        LetterGameLogTemplate.OPEN_SUCCESS_STREAK_1 -> stringResource(R.string.letter_game_log_open_success_streak_1, narration.actor, narration.streak, narration.count, narration.points)
        LetterGameLogTemplate.OPEN_SUCCESS_STREAK_2 -> stringResource(R.string.letter_game_log_open_success_streak_2, narration.actor, narration.streak, narration.count, narration.points)
        LetterGameLogTemplate.GUESS_BLIND_1 -> stringResource(R.string.letter_game_log_guess_blind_1, narration.actor, narration.points)
        LetterGameLogTemplate.GUESS_BLIND_2 -> stringResource(R.string.letter_game_log_guess_blind_2, narration.actor, narration.points)
        LetterGameLogTemplate.GUESS_CORRECT_1 -> stringResource(R.string.letter_game_log_guess_correct_1, narration.actor, narration.points)
        LetterGameLogTemplate.GUESS_CORRECT_2 -> stringResource(R.string.letter_game_log_guess_correct_2, narration.actor, narration.points)
        LetterGameLogTemplate.GUESS_CORRECT_STREAK_1 -> stringResource(R.string.letter_game_log_guess_correct_streak_1, narration.actor, narration.streak, narration.points)
        LetterGameLogTemplate.GUESS_CORRECT_STREAK_2 -> stringResource(R.string.letter_game_log_guess_correct_streak_2, narration.actor, narration.streak, narration.points)
        LetterGameLogTemplate.GUESS_INCORRECT_1 -> stringResource(R.string.letter_game_log_guess_incorrect_1, narration.actor)
        LetterGameLogTemplate.GUESS_INCORRECT_2 -> stringResource(R.string.letter_game_log_guess_incorrect_2, narration.actor)
        LetterGameLogTemplate.GUESS_INCORRECT_STREAK_1 -> stringResource(R.string.letter_game_log_guess_incorrect_streak_1, narration.actor, narration.streak)
        LetterGameLogTemplate.GUESS_INCORRECT_STREAK_2 -> stringResource(R.string.letter_game_log_guess_incorrect_streak_2, narration.actor, narration.streak)
        LetterGameLogTemplate.PROGRESS_RECOVERED_1 -> stringResource(R.string.letter_game_log_progress_recovered_1, narration.actor, narration.count, narration.points)
        LetterGameLogTemplate.PROGRESS_RECOVERED_2 -> stringResource(R.string.letter_game_log_progress_recovered_2, narration.actor, narration.count, narration.points)
        LetterGameLogTemplate.HINT_PUBLIC_1 -> stringResource(R.string.letter_game_log_hint_public_1, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, hintResult)
        LetterGameLogTemplate.HINT_PUBLIC_2 -> stringResource(R.string.letter_game_log_hint_public_2, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, hintResult)
        LetterGameLogTemplate.HINT_PRIVATE_1 -> stringResource(R.string.letter_game_log_hint_private_1, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, "")
        LetterGameLogTemplate.HINT_PRIVATE_2 -> stringResource(R.string.letter_game_log_hint_private_2, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, "")
        LetterGameLogTemplate.HINT_LOW_SCORE_1 -> stringResource(R.string.letter_game_log_hint_low_score_1, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, hintResult)
        LetterGameLogTemplate.HINT_LOW_SCORE_2 -> stringResource(R.string.letter_game_log_hint_low_score_2, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, hintResult)
        LetterGameLogTemplate.HINT_NORMAL_1 -> stringResource(R.string.letter_game_log_hint_normal_1, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, hintResult)
        LetterGameLogTemplate.HINT_NORMAL_2 -> stringResource(R.string.letter_game_log_hint_normal_2, narration.actor, narration.cost, narration.balance, narration.hintCount, hintType, hintResult)
        LetterGameLogTemplate.FALLBACK -> narration.fallbackMessage.orEmpty()
    }
}

@Composable
private fun InputMechanismTip(onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .squircleSurface(
                color = MiuixTheme.colorScheme.surfaceContainer,
                cornerRadius = 18.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = Icons.Rounded.Info,
            contentDescription = null,
            tint = MiuixTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = stringResource(R.string.letter_game_input_tip),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.Cancel,
                contentDescription = stringResource(R.string.letter_game_dismiss),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun TurnStrip(
    players: List<LetterGameMatchPlayer>,
    turnUserId: String?,
    currentUserId: String?,
    localAvatarModel: Any?,
) {
    val orderedPlayers = players.sortedBy(LetterGameMatchPlayer::turnOrder)
    val currentPlayer = orderedPlayers.firstOrNull { it.userId == turnUserId }
    val currentIndex = orderedPlayers.indexOfFirst { it.userId == turnUserId }
    val displayPlayers = if (currentIndex > 0) {
        orderedPlayers.drop(currentIndex) + orderedPlayers.take(currentIndex)
    } else {
        orderedPlayers
    }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            SmallTitle(
                text = stringResource(
                    R.string.letter_game_current_player,
                    displayName(currentPlayer),
                ),
                insideMargin = PaddingValues(0.dp),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            displayPlayers.forEachIndexed { index, player ->
                val active = player.userId == turnUserId
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    PlayerAvatar(
                        url = player.avatarUrl,
                        name = player.displayName ?: player.userId,
                        size = 42.dp,
                        localModel = localAvatarModel.takeIf { player.userId == currentUserId },
                    )
                    Text(
                        stringResource(R.string.letter_game_points, player.score),
                        style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                        color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                if (index < displayPlayers.lastIndex) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            if (displayPlayers.isNotEmpty()) {
                Icon(
                    Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                val first = displayPlayers.first()
                PlayerAvatar(
                    url = first.avatarUrl,
                    name = first.displayName ?: first.userId,
                    size = 30.dp,
                    localModel = localAvatarModel.takeIf { first.userId == currentUserId },
                )
            }
        }
    }
}

@Composable
private fun EnglishLetterProgress(logs: List<LetterGameLogEntry>) {
    val openedLetters = remember(logs) { openedEnglishLetters(logs) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.TextFields,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            SmallTitle(
                text = stringResource(R.string.letter_game_alphabet_progress),
                insideMargin = PaddingValues(0.dp),
            )
        }
        ('A'..'Z').toList().chunked(13).forEach { rowLetters ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                rowLetters.forEach { letter ->
                    val opened = letter in openedLetters
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(24.dp)
                            .squircleSurface(
                                color = if (opened) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.07f),
                                cornerRadius = 6.dp,
                                extension = SquircleExtension,
                            )
                            .squircleBorder(
                                width = 1.dp,
                                color = if (opened) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.1f),
                                cornerRadius = 6.dp,
                                extension = SquircleExtension,
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = letter.toString(),
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = if (opened) Color.White else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
        }
    }
}

private fun openedEnglishLetters(logs: List<LetterGameLogEntry>): Set<Char> =
    logs.asSequence()
        .filter { it.actionType == "open_character" }
        .mapNotNull { log ->
            log.character
                ?.firstOrNull()
                ?.uppercaseChar()
                ?.takeIf { it in 'A'..'Z' }
        }
        .toSet()

@Composable
private fun LetterSongCard(
    song: LetterGameMatchSong,
    coverImageStore: org.rhythmeta.maimaid.core.data.CoverImageStore?,
    versions: List<GameVersionEntity>,
    onLongClick: () -> Unit = {},
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val masterColor = SongVisualUtils.difficultyColor("master", darkTheme = darkTheme, brightenDark = true)
    val remasterColor = SongVisualUtils.difficultyColor("remaster", darkTheme = darkTheme, brightenDark = true)
    val cardColor = SongVisualUtils.songCardSurfaceColor(MiuixTheme.colorScheme.surfaceContainer, darkTheme)
    val difficultyColor = if (song.hasRemaster) remasterColor else masterColor
    val versionText = song.version
        ?.takeIf(String::isNotBlank)
        ?.let { SongVisualUtils.versionAbbreviation(it, versions) }
    val chartTypes = letterGameChartTypes(song.chartTypes)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 76.dp)
            .squircleSurface(
                color = cardColor,
                cornerRadius = 14.dp,
                extension = SquircleExtension,
            )
            .squircleBorder(
                width = 1.dp,
                color = difficultyColor.copy(alpha = 0.12f),
                cornerRadius = 14.dp,
                extension = SquircleExtension,
            )
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(52.dp)
                .width(4.dp)
                .squircleSurface(color = difficultyColor, cornerRadius = 2.dp, extension = SquircleExtension),
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, end = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (song.imageName != null && coverImageStore != null) {
                SongJacket(song.imageName, coverImageStore, size = 52.dp, cornerRadius = 12.dp)
            } else {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(squircleShape(12.dp))
                        .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(stringResource(R.string.letter_game_unknown_song), style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    song.title,
                    style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                song.artist?.takeIf(String::isNotBlank)?.let { artist ->
                    Text(artist, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
            Spacer(Modifier.width(8.dp))
            if (versionText != null || !song.maxConstant.isNullOrBlank()) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    versionText?.let {
                        ChartTypeVersionBadge(
                            text = it,
                            chartTypes = chartTypes,
                            darkTheme = darkTheme,
                        )
                    }
                    song.maxConstant?.takeIf(String::isNotBlank)?.let { constant ->
                        Text(
                            text = formatLetterGameConstant(constant),
                            style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold),
                            color = difficultyColor,
                        )
                    }
                }
            }
        }
    }
}

private fun formatLetterGameConstant(value: String): String {
    val normalized = value.trim()
    if (!normalized.matches(Regex("^[+-]?\\d+(?:\\.\\d+)?$"))) return value
    val separator = normalized.indexOf('.')
    if (separator < 0) return "$normalized.0"
    val fraction = normalized.substring(separator + 1).trimEnd('0')
    return normalized.substring(0, separator) + "." + fraction.ifEmpty { "0" }
}

private fun letterGameChartTypes(chartTypes: List<String>): List<String> {
    val normalized = chartTypes.map { type ->
        when (type.trim().lowercase()) {
            "standard" -> "std"
            else -> type.trim().lowercase()
        }
    }.toSet()
    return when {
        "std" in normalized && "dx" in normalized -> listOf("std", "dx")
        "utage" in normalized -> listOf("utage")
        "dx" in normalized -> listOf("dx")
        else -> listOf("std")
    }
}

@Composable
private fun HintDropdown(
    title: String,
    selected: String,
    options: List<String>,
    optionLabel: @Composable (String) -> String,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        cornerRadius = 14.dp,
                        extension = SquircleExtension,
                    )
                    .clickable(role = Role.Button) { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = optionLabel(selected),
                    modifier = Modifier.weight(1f),
                    color = MiuixTheme.colorScheme.onSurface,
                )
                Icon(
                    imageVector = Icons.Rounded.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            WindowListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.End,
                enableWindowDim = true,
                onDismissRequest = { expanded = false },
            ) {
                ListPopupColumn {
                    options.forEachIndexed { index, option ->
                        DropdownImpl(
                            text = optionLabel(option),
                            optionSize = options.size,
                            isSelected = option == selected,
                            index = index,
                            onSelectedIndexChange = {
                                onSelect(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultsPage(
    match: LetterGameMatchSnapshot,
    currentUserId: String?,
    localAvatarModel: Any?,
    coverImageStore: org.rhythmeta.maimaid.core.data.CoverImageStore,
    versions: List<GameVersionEntity>,
    contentTopPadding: Dp,
    onReopen: () -> Unit,
    onExit: () -> Unit,
) {
    val guessedSongs = match.songs.filter { it.completionReason == "guessed" }
    val unguessedSongs = match.songs.filterNot { it.completionReason == "guessed" }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.EmojiEvents, contentDescription = null, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.letter_game_match_results), style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        items(match.players.sortedByDescending(LetterGameMatchPlayer::score), key = LetterGameMatchPlayer::userId) { player ->
            val isSelf = player.userId == currentUserId
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 12.dp,
                insideMargin = PaddingValues(12.dp),
                colors = CardDefaults.defaultColors(
                    color = if (isSelf) MiuixTheme.colorScheme.secondaryContainer else MiuixTheme.colorScheme.surfaceContainer,
                ),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.letter_game_rank, match.players.sortedByDescending(LetterGameMatchPlayer::score).indexOf(player) + 1),
                        modifier = Modifier.width(21.dp),
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelf) MiuixTheme.colorScheme.onSecondaryContainer else MiuixTheme.colorScheme.onSurface,
                    )
                    PlayerAvatar(
                        url = player.avatarUrl,
                        name = player.displayName ?: player.userId,
                        size = 42.dp,
                        localModel = localAvatarModel.takeIf { player.userId == currentUserId },
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        player.displayName ?: player.userId,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (isSelf) MiuixTheme.colorScheme.onSecondaryContainer else MiuixTheme.colorScheme.onSurface,
                    )
                    Text(
                        stringResource(R.string.letter_game_points, player.score),
                        style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelf) MiuixTheme.colorScheme.onSecondaryContainer else MiuixTheme.colorScheme.onSurface,
                    )
                }
            }
        }
        if (guessedSongs.isNotEmpty()) {
            item {
                LetterGameSectionTitle(
                    text = stringResource(R.string.letter_game_guessed_songs),
                    icon = Icons.Rounded.Check,
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
            items(guessedSongs, key = LetterGameMatchSong::slotId) { song ->
                LetterSongCard(song = song, coverImageStore = coverImageStore, versions = versions)
            }
        }
        if (unguessedSongs.isNotEmpty()) {
            item {
                LetterGameSectionTitle(
                    text = stringResource(R.string.letter_game_unguessed_songs),
                    icon = Icons.Rounded.Cancel,
                    tint = MiuixTheme.colorScheme.error,
                )
            }
            items(unguessedSongs, key = LetterGameMatchSong::slotId) { song ->
                LetterSongCard(song = song, coverImageStore = coverImageStore, versions = versions)
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onReopen, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
                    Icon(Icons.Rounded.Refresh, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.letter_game_reopen))
                }
                Button(onClick = onExit, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.letter_game_leave_room))
                }
            }
        }
    }
}

@Composable
private fun LetterGameSectionTitle(
    text: String,
    icon: ImageVector,
    tint: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp),
        )
        SmallTitle(
            text = text,
            insideMargin = PaddingValues(0.dp),
        )
    }
}

@Composable
private fun PlayerAvatar(url: String?, name: String, size: Dp, localModel: Any? = null) {
    val model = localModel ?: url?.takeIf(String::isNotBlank)
    if (model == null) {
        Box(Modifier.size(size).clip(CircleShape).background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, contentDescription = name, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(size * 0.55f))
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(model).build(),
            contentDescription = name,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(size).clip(CircleShape),
        )
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: (() -> Unit)? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(MiuixTheme.colorScheme.error.copy(alpha = 0.12f)).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(message, modifier = Modifier.weight(1f), color = MiuixTheme.colorScheme.error, maxLines = 2, overflow = TextOverflow.Ellipsis)
        onDismiss?.let { TextButton(text = stringResource(R.string.letter_game_dismiss), onClick = it) }
    }
}

private fun displayName(player: LetterGameMatchPlayer?): String = player?.displayName ?: player?.userId ?: "player"

private fun LetterGameFact.isKnownHint(type: String): Boolean = this.type == type

private fun firstGrapheme(value: String): String {
    val iterator = java.text.BreakIterator.getCharacterInstance()
    iterator.setText(value)
    val end = iterator.following(0)
    return if (end > 0) value.substring(0, end) else ""
}

private fun copyRoomCode(context: android.content.Context, code: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    clipboard?.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.letter_game_room_code), code))
}
