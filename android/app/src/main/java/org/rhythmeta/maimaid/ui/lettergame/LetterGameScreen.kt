package org.rhythmeta.maimaid.ui.lettergame

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.ExitToApp
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AddHome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.WebSocket
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.LetterGameCreateRequest
import org.rhythmeta.maimaid.core.data.LetterGameEvent
import org.rhythmeta.maimaid.core.data.LetterGameLogEntry
import org.rhythmeta.maimaid.core.data.LetterGameMatchPlayer
import org.rhythmeta.maimaid.core.data.LetterGameMatchSnapshot
import org.rhythmeta.maimaid.core.data.LetterGameMatchSong
import org.rhythmeta.maimaid.core.data.LetterGameRepository
import org.rhythmeta.maimaid.core.data.LetterGameRoom
import org.rhythmeta.maimaid.core.data.LetterGameRoomMember
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.components.appTextFieldColors
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

private enum class RoomVisibility { Public, Private }

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
    onJoinActionAvailabilityChanged: (Boolean) -> Unit = {},
) {
    val session by container.backendSessionManager.state.collectAsStateWithLifecycle()
    val repository = container.letterGameRepository
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf(emptyList<LetterGameRoom>()) }
    var selectedRoom by remember { mutableStateOf<LetterGameRoom?>(null) }
    var match by remember { mutableStateOf<LetterGameMatchSnapshot?>(null) }
    var socket by remember { mutableStateOf<WebSocket?>(null) }
    var showJoinDialog by remember { mutableStateOf(false) }
    var joinCode by remember { mutableStateOf("") }
    var createVisibility by remember { mutableStateOf(RoomVisibility.Public) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    var reconnectAttempt by remember { mutableIntStateOf(0) }
    var leftMatchId by remember(selectedRoom?.id) { mutableStateOf<String?>(null) }
    var showExitConfirmation by remember { mutableStateOf(false) }
    var showRoomSettings by remember { mutableStateOf(false) }
    var handledJoinRequestToken by remember { mutableIntStateOf(joinRequestToken) }
    var handledExitRequestToken by remember { mutableIntStateOf(exitRequestToken) }
    var handledCopyRequestToken by remember { mutableIntStateOf(copyRequestToken) }
    var handledSettingsRequestToken by remember { mutableIntStateOf(settingsRequestToken) }
    val snackbarHostState = remember { SnackbarHostState() }
    val roomCodeCopiedMessage = stringResource(R.string.letter_game_room_code_copied)
    val gameVersions by container.catalogRepository.versions.collectAsStateWithLifecycle(emptyList())
    val songCategories by container.catalogRepository.categories.collectAsStateWithLifecycle(emptyList())
    val collections by container.songCollectionRepository.collections.collectAsStateWithLifecycle(emptyList())
    val collectionItems by container.songCollectionRepository.items.collectAsStateWithLifecycle(emptyList())

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
    LaunchedEffect(selectedRoom != null, session.isAuthenticated) {
        onRoomPresenceChanged(selectedRoom != null)
        onRoomCodeChanged(selectedRoom?.code)
        onJoinActionAvailabilityChanged(selectedRoom == null && session.isAuthenticated)
    }
    DisposableEffect(Unit) {
        onDispose {
            onRoomPresenceChanged(false)
            onRoomCodeChanged(null)
            onJoinActionAvailabilityChanged(false)
        }
    }
    LaunchedEffect(session.user?.id) {
        if (session.user == null) {
            rooms = emptyList()
            selectedRoom = null
            match = null
            return@LaunchedEffect
        }
        runCatching { repository.listPublicRooms() }
            .onSuccess { rooms = it }
            .onFailure { errorMessage = it.message }
    }
    LaunchedEffect(Unit) {
        repository.events.collect { event ->
            when (event) {
                is LetterGameEvent.Room -> {
                    rooms = rooms.map { if (it.id == event.room.id) event.room else it }
                    if (selectedRoom?.id == event.room.id) selectedRoom = event.room
                }
                is LetterGameEvent.Match -> {
                    if (selectedRoom != null && (match?.matchId == null || match?.matchId == event.match.matchId)) {
                        match = event.match
                    }
                }
                is LetterGameEvent.Error -> {
                    errorMessage = event.message ?: event.code
                    if (event.code == "connection_failed" && selectedRoom != null) reconnectAttempt += 1
                    if (event.code == "stale_revision") {
                        match?.let { staleMatch ->
                            scope.launch { runCatching { repository.getMatch(staleMatch.matchId) }.onSuccess { match = it } }
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
        runCatching { repository.getMatch(latest.id) }
            .onSuccess { match = it }
            .onFailure { errorMessage = it.message }
    }
    LaunchedEffect(match?.status) {
        if (match?.status != "active") leftMatchId = null
    }
    LaunchedEffect(selectedRoom?.id) {
        val roomId = selectedRoom?.id ?: return@LaunchedEffect
        while (true) {
            delay(5_000.milliseconds)
            runCatching { repository.getRoom(roomId) }.onSuccess { refreshed ->
                if (selectedRoom?.id == roomId) selectedRoom = refreshed
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

    if (!session.isAuthenticated) {
        LoginRequired(contentTopPadding, onOpenLogin)
        return
    }

    val room = selectedRoom
    val visibleMatch = match?.takeUnless { it.matchId == leftMatchId && it.status == "active" }
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
            LobbyPage(
                rooms = rooms,
                contentTopPadding = contentTopPadding,
                createVisibility = createVisibility,
                loading = loading,
                errorMessage = errorMessage,
                onVisibilityChange = { createVisibility = it },
                onCreate = {
                    scope.launch {
                        loading = true
                        errorMessage = null
                        runCatching {
                            repository.createRoom(
                                LetterGameCreateRequest(visibility = createVisibility.name.lowercase()),
                            )
                        }.onSuccess { selectedRoom = it }
                            .onFailure { errorMessage = it.message }
                        loading = false
                    }
                },
                onJoinPublic = { publicRoom ->
                    scope.launch {
                        loading = true
                        errorMessage = null
                        runCatching { repository.joinRoom(publicRoom.code) }
                            .onSuccess { selectedRoom = it }
                            .onFailure { errorMessage = it.message }
                        loading = false
                    }
                },
            )
        } else {
            val currentUserId = session.user?.id
					when (animatedMatch?.status) {
							"active" -> PlayingPage(
								room = animatedRoom,
								match = animatedMatch,
								currentUserId = currentUserId,
								socket = socket,
								repository = repository,
								coverImageStore = container.coverImageStore,
								contentTopPadding = contentTopPadding,
								errorMessage = errorMessage,
								onLeave = {
									val activeSocket = socket
									if (activeSocket != null) {
										repository.leaveMatch(activeSocket, animatedMatch.matchId)
										leftMatchId = animatedMatch.matchId
									}
								},
								onExitRoom = { showExitConfirmation = true },
							)
							"finished", "abandoned" -> ResultsPage(
								room = animatedRoom,
								match = animatedMatch,
								currentUserId = currentUserId,
								contentTopPadding = contentTopPadding,
								onReopen = {
									scope.launch {
										runCatching { repository.reopenRoom(animatedRoom.id) }
											.onSuccess {
												selectedRoom = it
												match = null
												leftMatchId = null
											}
											.onFailure { errorMessage = it.message }
									}
								},
								onExit = { showExitConfirmation = true },
							)
							else -> WaitingPage(
								room = animatedRoom,
								currentUserId = currentUserId,
								contentTopPadding = contentTopPadding,
								repository = repository,
								loading = loading,
								errorMessage = errorMessage,
								onUpdateRoom = { updated -> selectedRoom = updated },
								onError = { errorMessage = it },
								onStart = {
									scope.launch {
										loading = true
										runCatching { repository.startMatch(animatedRoom.id) }
											.onSuccess { match = it }
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
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
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
                        loading = true
                        runCatching { repository.leaveRoom(room.id) }
                            .onSuccess {
                                showExitConfirmation = false
                                selectedRoom = null
                                match = null
                                leftMatchId = null
                            }
                            .onFailure { errorMessage = it.message }
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

    if (room != null && showRoomSettings) {
        LetterGameRoomSettingsSheet(
            visible = true,
            room = room,
            currentUserId = session.user?.id,
            matchInProgress = visibleMatch?.status == "active",
            gameVersions = gameVersions,
            songCategories = songCategories,
            collections = collections,
            collectionItems = collectionItems,
            onDismiss = { showRoomSettings = false },
            onUpdate = { request ->
                repository.updateRoom(room.id, request).also { selectedRoom = it }
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
    errorMessage: String?,
    onVisibilityChange: (RoomVisibility) -> Unit,
    onCreate: () -> Unit,
    onJoinPublic: (LetterGameRoom) -> Unit,
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
        if (errorMessage != null) item { ErrorBanner(errorMessage) }
    }
}

@Composable
private fun RoomMembers(
    members: List<LetterGameRoomMember>,
    hostUserId: String,
    currentUserId: String?,
    isHost: Boolean,
    onApprove: (LetterGameRoomMember) -> Unit,
    onKick: (LetterGameRoomMember) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 14.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(
                imageVector = Icons.Rounded.Person,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp),
            )
            SmallTitle(text = stringResource(R.string.letter_game_players))
        }
        Spacer(Modifier.height(8.dp))
        members.forEach { member ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                PlayerAvatar(member.avatarUrl, member.displayName ?: member.userId, 36.dp)
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
                    IconButton(onClick = { onApprove(member) }) { Icon(Icons.Rounded.Check, contentDescription = stringResource(R.string.letter_game_approve)) }
                }
                if (isHost && member.userId != hostUserId && member.status == "accepted") {
                    IconButton(onClick = { onKick(member) }) { Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = stringResource(R.string.letter_game_remove)) }
                }
            }
        }
    }
}

@Composable
private fun PlayingPage(
    room: LetterGameRoom,
    match: LetterGameMatchSnapshot,
    currentUserId: String?,
    socket: WebSocket?,
    repository: LetterGameRepository,
    coverImageStore: org.rhythmeta.maimaid.core.data.CoverImageStore,
    contentTopPadding: Dp,
    errorMessage: String?,
    onLeave: () -> Unit,
    onExitRoom: () -> Unit,
) {
    var character by remember { mutableStateOf("") }
    var guessTarget by remember { mutableStateOf<LetterGameMatchSong?>(null) }
    var hintTarget by remember { mutableStateOf<LetterGameMatchSong?>(null) }
    var popupTarget by remember { mutableStateOf<LetterGameMatchSong?>(null) }
    var openCharacterDialog by remember { mutableStateOf(false) }
    var guessText by remember { mutableStateOf("") }
    var hintType by remember { mutableStateOf("version") }
    var hintVisibility by remember { mutableStateOf("public") }
    var hintDifficulty by remember { mutableStateOf("master") }
    var secondsLeft by remember { mutableIntStateOf(0) }
    val isTurn = match.turnUserId == currentUserId
    val canAct = isTurn && socket != null
    val currentPlayer = match.players.firstOrNull { it.userId == currentUserId }
    val canBuyHint = canAct && currentPlayer?.scoringEligible == true
    LaunchedEffect(match.turnDeadline) {
        while (true) {
            val deadline = match.turnDeadline?.let { runCatching { java.time.Instant.parse(it) }.getOrNull() }
            secondsLeft = deadline?.let { ((it.toEpochMilli() - System.currentTimeMillis()) / 1_000L).toInt().coerceAtLeast(0) } ?: 0
            if (secondsLeft == 0) break
            delay(1_000.milliseconds)
        }
    }
    val send: (JsonObject) -> Unit = { payload ->
        if (socket != null) {
            repository.sendAction(socket, match.matchId, match.revision, UUID.randomUUID().toString(), payload)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 8.dp, end = 16.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item { PlayingHeader(room.code, match, onExitRoom) }
        if (errorMessage != null) item { ErrorBanner(errorMessage) }
        item { TurnStrip(match.players, match.turnUserId) }
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                insideMargin = PaddingValues(12.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.Timer, contentDescription = null, tint = if (isTurn) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
                    Text(
                        if (isTurn) stringResource(R.string.letter_game_your_turn, secondsLeft) else stringResource(R.string.letter_game_waiting_turn, displayName(match.players.firstOrNull { it.userId == match.turnUserId }), secondsLeft),
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    TextField(
                        value = character,
                        onValueChange = { character = firstGrapheme(it) },
                        label = stringResource(R.string.letter_game_character_all_songs),
                        enabled = canAct,
                        singleLine = true,
                        colors = appTextFieldColors(),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        onClick = {
                            val value = character
                            if (value.isNotEmpty()) {
                                send(buildJsonObject { put("kind", "open_character"); put("character", value) })
                                character = ""
                            }
                        },
                        enabled = canAct && character.isNotEmpty(),
                        colors = ButtonDefaults.buttonColorsPrimary(),
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = null)
                        Spacer(Modifier.width(5.dp))
                        Text(stringResource(R.string.letter_game_open))
                    }
                }
            }
        }
        item {
            SmallTitle(text = stringResource(R.string.letter_game_log))
        }
        item { GameLog(match) }
        item { SmallTitle(text = stringResource(R.string.letter_game_songs)) }
        items(match.songs, key = LetterGameMatchSong::slotId) { song ->
            Box {
                LetterSongCard(
                    song = song,
                    coverImageStore = coverImageStore,
                    onLongClick = { popupTarget = song },
                )
                OverlayListPopup(
                    show = popupTarget?.slotId == song.slotId,
                    onDismissRequest = { popupTarget = null },
                ) {
                    ListPopupColumn {
                        if (canAct && match.songs.any { it.status == "active" }) PopupAction(stringResource(R.string.letter_game_reveal_all), Icons.Rounded.Lightbulb) {
                            popupTarget = null
                            openCharacterDialog = true
                        }
                        if (song.status == "active") {
                            PopupAction(stringResource(R.string.letter_game_guess_this_song), Icons.AutoMirrored.Rounded.Send) {
                                popupTarget = null
                                guessTarget = song
                                guessText = ""
                            }
                            if (canBuyHint) PopupAction(stringResource(R.string.letter_game_buy_hint), Icons.Rounded.Lightbulb) {
                                popupTarget = null
                                hintTarget = song
                            }
                        }
                    }
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onLeave, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.letter_game_leave_match))
                }
                Button(onClick = onExitRoom, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColors()) {
                    Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text(stringResource(R.string.letter_game_leave_room))
                }
            }
        }
    }

    if (openCharacterDialog) {
        WindowDialog(show = true, title = stringResource(R.string.letter_game_open_character_title), onDismissRequest = { openCharacterDialog = false }) {
            Text(stringResource(R.string.letter_game_open_character_summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            Spacer(Modifier.height(10.dp))
            TextField(
                value = character,
                onValueChange = { character = firstGrapheme(it) },
                label = stringResource(R.string.letter_game_character),
                enabled = canAct,
                singleLine = true,
                colors = appTextFieldColors(),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val value = character
                    if (value.isNotEmpty()) {
                        send(buildJsonObject { put("kind", "open_character"); put("character", value) })
                        character = ""
                        openCharacterDialog = false
                    }
                },
                enabled = canAct && character.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(stringResource(R.string.letter_game_open)) }
        }
    }

    guessTarget?.let { target ->
        WindowDialog(
            show = true,
            title = stringResource(R.string.letter_game_guess_song_title),
            onDismissRequest = { guessTarget = null },
        ) {
            TextField(value = guessText, onValueChange = { guessText = it }, label = stringResource(R.string.letter_game_song_title_alias), colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    send(buildJsonObject { put("kind", "guess_song"); put("slotId", target.slotId); put("guess", guessText) })
                    guessTarget = null
                },
                enabled = canAct && guessText.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(stringResource(R.string.letter_game_submit_guess)) }
        }
    }
    hintTarget?.let { target ->
        WindowDialog(show = true, title = stringResource(R.string.letter_game_buy_hint), onDismissRequest = { hintTarget = null }) {
            HintChoice(stringResource(R.string.letter_game_hint_version), hintType == "version") { hintType = "version" }
            HintChoice(stringResource(R.string.letter_game_hint_white_chart), hintType == "white_chart") { hintType = "white_chart" }
            HintChoice(
                label = stringResource(R.string.letter_game_hint_constant),
                selected = hintType == "constant",
                enabled = target.facts.any { it.type == "white_chart" && it.value.toString() == "true" },
            ) { hintType = "constant" }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { hintVisibility = "public" }, modifier = Modifier.weight(1f), colors = if (hintVisibility == "public") ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) { Text(stringResource(R.string.letter_game_public)) }
                Button(onClick = { hintVisibility = "private" }, modifier = Modifier.weight(1f), colors = if (hintVisibility == "private") ButtonDefaults.buttonColorsPrimary() else ButtonDefaults.buttonColors()) { Text(stringResource(R.string.letter_game_private)) }
            }
            if (hintType == "constant") {
                Spacer(Modifier.height(8.dp))
                TextField(value = hintDifficulty, onValueChange = { hintDifficulty = it }, label = stringResource(R.string.letter_game_difficulty), colors = appTextFieldColors(), modifier = Modifier.fillMaxWidth())
            }
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    send(buildJsonObject {
                        put("kind", "buy_hint")
                        put("slotId", target.slotId)
                        put("hintType", hintType)
                        put("visibility", hintVisibility)
                        if (hintType == "constant") put("difficulty", hintDifficulty)
                    })
                    hintTarget = null
                },
                enabled = canBuyHint,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(stringResource(R.string.letter_game_purchase)) }
        }
    }
}

@Composable
private fun PlayingHeader(code: String, match: LetterGameMatchSnapshot, onExit: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(stringResource(R.string.letter_game_room_name, code), style = MiuixTheme.textStyles.title2.copy(fontWeight = FontWeight.Bold))
            Text(stringResource(R.string.letter_game_revision_summary, match.revision, match.noProgressRounds), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        IconButton(onClick = onExit) { Icon(Icons.AutoMirrored.Rounded.ExitToApp, contentDescription = stringResource(R.string.letter_game_leave_room)) }
    }
}

@Composable
private fun TurnStrip(players: List<LetterGameMatchPlayer>, turnUserId: String?) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        players.sortedBy(LetterGameMatchPlayer::turnOrder).forEachIndexed { index, player ->
            val active = player.userId == turnUserId
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (active) MiuixTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                PlayerAvatar(player.avatarUrl, player.displayName ?: player.userId, 42.dp)
                Text(player.displayName ?: player.userId, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.footnote1)
                Text(stringResource(R.string.letter_game_points, player.score), style = MiuixTheme.textStyles.footnote1.copy(fontWeight = FontWeight.Bold), color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            if (index < players.lastIndex) Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        if (players.isNotEmpty()) {
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            val first = players.minByOrNull(LetterGameMatchPlayer::turnOrder)
            first?.let { PlayerAvatar(it.avatarUrl, it.displayName ?: it.userId, 30.dp) }
        }
    }
}

@Composable
private fun GameLog(match: LetterGameMatchSnapshot) {
    Card(
        modifier = Modifier.fillMaxWidth().height(150.dp),
        cornerRadius = 12.dp,
        insideMargin = PaddingValues(10.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        if (match.logs.isEmpty()) {
            Text(stringResource(R.string.letter_game_log_empty), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(match.logs, key = LetterGameLogEntry::id) { log ->
                    Text(log.message, style = MiuixTheme.textStyles.footnote1, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
private fun LetterSongCard(
    song: LetterGameMatchSong,
    coverImageStore: org.rhythmeta.maimaid.core.data.CoverImageStore?,
    onLongClick: () -> Unit,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val masterColor = SongVisualUtils.difficultyColor("master", darkTheme = darkTheme, brightenDark = true)
    val remasterColor = SongVisualUtils.difficultyColor("remaster", darkTheme = darkTheme, brightenDark = true)
    val guessed = song.completionReason == "guessed"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(82.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MiuixTheme.colorScheme.surfaceContainer)
            .combinedClickable(onClick = {}, onLongClick = onLongClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.fillMaxHeight().width(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Box(Modifier.fillMaxWidth().weight(1f).background(masterColor))
            if (song.hasRemaster) Box(Modifier.fillMaxWidth().weight(1f).background(remasterColor))
        }
        Spacer(Modifier.width(10.dp))
        if (song.imageName != null && coverImageStore != null) {
            SongJacket(song.imageName, coverImageStore, size = 58.dp, cornerRadius = 10.dp)
        } else {
            Box(Modifier.size(58.dp).clip(RoundedCornerShape(10.dp)).background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.letter_game_unknown_song), style = MiuixTheme.textStyles.title2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(song.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.SemiBold))
            song.artist?.takeIf { guessed && it.isNotBlank() }?.let { artist ->
                Text(artist, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            val factLine = buildList {
                song.facts.firstOrNull { it.type == "white_chart" }?.let { add(if (it.value.toString() == "true") "WHITE" else "NO WHITE") }
                song.facts.filter { it.type == "constant" }.forEach { add("CONST ${it.value}") }
                song.masterConstant?.let { add("MAS $it") }
                song.remasterConstant?.let { add("RE $it") }
                if (song.chartTypes.isNotEmpty()) add(song.chartTypes.joinToString(" · ").uppercase())
            }.joinToString(" · ")
            if (factLine.isNotBlank()) Text(factLine, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier.width(62.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center,
        ) {
            song.version?.takeIf(String::isNotBlank)?.let {
                Text(it, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary, maxLines = 1)
            }
        }
        Spacer(Modifier.width(12.dp))
    }
}

@Composable
private fun PopupAction(label: String, icon: ImageVector, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = null).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MiuixTheme.colorScheme.onSurface)
        Spacer(Modifier.width(12.dp))
        Text(label)
    }
}

@Composable
private fun HintChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(enabled = enabled, onClick = onClick, onLongClick = null).padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(if (selected) Icons.Rounded.Visibility else Icons.Rounded.VisibilityOff, contentDescription = null, tint = if (selected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantSummary.copy(alpha = if (enabled) 1f else 0.45f))
        Spacer(Modifier.width(10.dp))
        Text(label, color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}

@Composable
private fun ResultsPage(
    room: LetterGameRoom,
    match: LetterGameMatchSnapshot,
    currentUserId: String?,
    contentTopPadding: Dp,
    onReopen: () -> Unit,
    onExit: () -> Unit,
) {
    val isHost = room.hostUserId == currentUserId
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
                    Text(stringResource(R.string.letter_game_room_name, room.code), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
                }
            }
        }
        items(match.players.sortedByDescending(LetterGameMatchPlayer::score), key = LetterGameMatchPlayer::userId) { player ->
            Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 12.dp, insideMargin = PaddingValues(12.dp), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.letter_game_rank, match.players.sortedByDescending(LetterGameMatchPlayer::score).indexOf(player) + 1), modifier = Modifier.width(36.dp), style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold))
                    PlayerAvatar(player.avatarUrl, player.displayName ?: player.userId, 42.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(player.displayName ?: player.userId, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(stringResource(R.string.letter_game_points, player.score), style = MiuixTheme.textStyles.body1.copy(fontWeight = FontWeight.Bold))
                }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = onReopen, enabled = isHost, modifier = Modifier.weight(1f), colors = ButtonDefaults.buttonColorsPrimary()) {
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
private fun PlayerAvatar(url: String?, name: String, size: Dp) {
    if (url.isNullOrBlank()) {
        Box(Modifier.size(size).clip(CircleShape).background(MiuixTheme.colorScheme.primary.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.Person, contentDescription = name, tint = MiuixTheme.colorScheme.primary, modifier = Modifier.size(size * 0.55f))
        }
    } else {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current).data(url).build(),
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
