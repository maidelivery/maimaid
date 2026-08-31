package org.rhythmeta.maimaid.ui.lettergame

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.util.UUID
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.LetterGameCreateRequest
import org.rhythmeta.maimaid.core.data.LetterGameEvent
import org.rhythmeta.maimaid.core.data.LetterGameMatchSnapshot
import org.rhythmeta.maimaid.core.data.LetterGameMatchSong
import org.rhythmeta.maimaid.core.data.LetterGameRoom
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun LetterGameScreen(container: AppContainer, contentTopPadding: Dp) {
    val session by container.backendSessionManager.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var rooms by remember { mutableStateOf<List<LetterGameRoom>>(emptyList()) }
    var activeRoom by remember { mutableStateOf<LetterGameRoom?>(null) }
    var match by remember { mutableStateOf<LetterGameMatchSnapshot?>(null) }
    var joinCode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var socket by remember { mutableStateOf<okhttp3.WebSocket?>(null) }

    suspend fun reload() {
        runCatching { container.letterGameRepository.listPublicRooms() }
            .onSuccess { rooms = it; error = null }
            .onFailure { error = it.message }
    }

    LaunchedEffect(session.isAuthenticated) {
        if (session.isAuthenticated) reload()
    }
    LaunchedEffect(Unit) {
        container.letterGameRepository.events.collect { event ->
            when (event) {
                is LetterGameEvent.Room -> activeRoom = event.room
                is LetterGameEvent.Match -> match = event.match
                is LetterGameEvent.Error -> error = event.message ?: event.code
                is LetterGameEvent.ActionAccepted -> Unit
            }
        }
    }

    if (!session.isAuthenticated) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = contentTopPadding, start = 20.dp, end = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(stringResource(R.string.letter_game_login_required), style = MiuixTheme.textStyles.title3)
            Text(stringResource(R.string.letter_game_login_required_summary), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 12.dp, end = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.letter_game_join_title), style = MiuixTheme.textStyles.title3)
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = joinCode,
                    onValueChange = { joinCode = it.uppercase().filter(Char::isLetterOrDigit).take(6) },
                    label = stringResource(R.string.letter_game_room_code),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        scope.launch {
                            runCatching { container.letterGameRepository.joinRoom(joinCode) }
                                .onSuccess { room -> activeRoom = room; socket = container.letterGameRepository.connect(room.code); error = null }
                                .onFailure { error = it.message }
                        }
                    },
                    enabled = joinCode.length == 6,
                ) { Text(stringResource(R.string.letter_game_join)) }
            }
        }
        item {
            Button(
                onClick = {
                    scope.launch {
                        runCatching { container.letterGameRepository.createRoom(LetterGameCreateRequest(visibility = "private")) }
                            .onSuccess { room -> activeRoom = room; socket = container.letterGameRepository.connect(room.code); error = null }
                            .onFailure { error = it.message }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.letter_game_create_private)) }
        }
        activeRoom?.let { room ->
            item {
                LetterGameRoomCard(room, match, session.user?.id, onStart = {
                    scope.launch {
                        runCatching { container.letterGameRepository.startMatch(room.id) }
                            .onSuccess { match = it; socket?.let { ws -> container.letterGameRepository.resume(ws, it.matchId, it.revision) }; error = null }
                            .onFailure { error = it.message }
                    }
                }) { kind, slotId, value ->
                    val currentMatch = match
                    val currentSocket = socket
                    if (currentMatch != null && currentSocket != null) {
                        val payload = buildJsonObject {
                            put("kind", kind)
                            put("slotId", slotId)
                            if (kind == "open_character") put("character", value) else put("guess", value)
                        }
                        container.letterGameRepository.sendAction(
                            currentSocket,
                            currentMatch.matchId,
                            currentMatch.revision,
                            UUID.randomUUID().toString(),
                            payload,
                        )
                    }
                }
            }
        }
        error?.let { message -> item { Text(message, color = MiuixTheme.colorScheme.error) } }
        item { Text(stringResource(R.string.letter_game_public_rooms), style = MiuixTheme.textStyles.title3) }
        items(rooms, key = LetterGameRoom::id) { room ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                insideMargin = PaddingValues(16.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
                onClick = {
                    joinCode = room.code
                    scope.launch {
                        runCatching { container.letterGameRepository.joinRoom(room.code) }
                            .onSuccess { activeRoom = it; socket = container.letterGameRepository.connect(it.code) }
                            .onFailure { error = it.message }
                    }
                },
            ) {
                Text(room.code, style = MiuixTheme.textStyles.title3)
                Text(stringResource(R.string.letter_game_member_count, room.memberCount), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
        }
    }
}

@Composable
private fun LetterGameRoomCard(
    room: LetterGameRoom,
    match: LetterGameMatchSnapshot?,
    currentUserId: String?,
    onStart: () -> Unit,
    onAction: (kind: String, slotId: String, value: String) -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.secondaryContainer),
    ) {
        Text(room.code, style = MiuixTheme.textStyles.title2)
        Text(stringResource(R.string.letter_game_member_count, room.memberCount))
        Spacer(Modifier.height(10.dp))
        if (match == null) {
            Button(onClick = onStart, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.letter_game_start)) }
        } else {
            Text(stringResource(R.string.letter_game_turn, match.turnUserId ?: "-"))
            match.songs.forEach { song ->
                LetterGameSongCard(
                    song = song,
                    canAct = match.turnUserId == currentUserId && song.status == "active",
                    onAction = onAction,
                )
            }
        }
    }
}

@Composable
private fun LetterGameSongCard(
    song: LetterGameMatchSong,
    canAct: Boolean,
    onAction: (kind: String, slotId: String, value: String) -> Unit,
) {
    var character by remember(song.slotId) { mutableStateOf("") }
    var guess by remember(song.slotId) { mutableStateOf("") }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(song.title, style = MiuixTheme.textStyles.body1)
        Text(stringResource(R.string.letter_game_remaining, song.remainingCharacterCount), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        if (canAct) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = character,
                    onValueChange = { character = it.take(2) },
                    label = stringResource(R.string.letter_game_character),
                    modifier = Modifier.weight(0.8f),
                )
                Button(
                    onClick = {
                        onAction("open_character", song.slotId, character)
                        character = ""
                    },
                    enabled = character.isNotBlank(),
                ) { Text(stringResource(R.string.letter_game_open)) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextField(
                    value = guess,
                    onValueChange = { guess = it },
                    label = stringResource(R.string.letter_game_guess),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        onAction("guess_song", song.slotId, guess)
                        guess = ""
                    },
                    enabled = guess.isNotBlank(),
                ) { Text(stringResource(R.string.letter_game_submit)) }
            }
        }
    }
}
