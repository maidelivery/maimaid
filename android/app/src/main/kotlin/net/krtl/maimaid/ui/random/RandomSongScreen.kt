package net.krtl.maimaid.ui.random

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.imageLoader
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.krtl.maimaid.data.assets.CoverArtStore
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SecondaryLargeTitleScaffold
import net.krtl.maimaid.ui.common.SongListCard
import net.krtl.maimaid.ui.song.SongSharedTransitionState

private const val EmptyRandomSlotId = "__empty__"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalSharedTransitionApi::class)
@Composable
fun RandomSongScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    activeSharedTransitionSongId: String? = null,
    sharedTransitionScope: SharedTransitionScope? = null,
    animatedVisibilityScope: AnimatedVisibilityScope? = null,
    openSong: (String, SongSharedTransitionState) -> Unit,
    onBack: () -> Unit
) {
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val songById = remember(songs) { songs.associateBy { it.songIdentifier } }
    val context = LocalContext.current
    val imageLoader = context.imageLoader
    val scope = rememberCoroutineScope()
    var songCount by rememberSaveable { mutableIntStateOf(3) }
    var displayedSongIds by rememberSaveable { mutableStateOf(List(4) { EmptyRandomSlotId }) }
    var pendingResultIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var resultIds by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    var isSpinning by rememberSaveable { mutableStateOf(false) }
    var spinJob by remember { mutableStateOf<Job?>(null) }
    val displayedSongs = remember(displayedSongIds, songById) {
        displayedSongIds.map { id -> songById[id] }
    }
    val results = remember(resultIds, songById) {
        resultIds.mapNotNull(songById::get)
    }
    val slotHeight = if (songCount == 4) 96.dp else 120.dp

    fun finishSpinImmediately() {
        if (pendingResultIds.isEmpty()) return
        spinJob?.cancel()
        displayedSongIds = List(4) { index -> pendingResultIds.getOrNull(index) ?: EmptyRandomSlotId }
        resultIds = pendingResultIds
        pendingResultIds = emptyList()
        isSpinning = false
    }

    fun startSpin() {
        if (songs.isEmpty() || isSpinning) return
        val nextResultIds = List(songCount) { songs.random().songIdentifier }
        pendingResultIds = nextResultIds
        resultIds = emptyList()
        isSpinning = true
        spinJob?.cancel()
        spinJob = scope.launch {
            repeat(songCount) { slotIndex ->
                val steps = 18 + slotIndex * 4
                repeat(steps) {
                    displayedSongIds = displayedSongIds.toMutableList().also { list ->
                        list[slotIndex] = songs.random().songIdentifier
                    }
                    delay(65L)
                }
                displayedSongIds = displayedSongIds.toMutableList().also { list ->
                    list[slotIndex] = nextResultIds[slotIndex]
                }
                delay(120L)
            }
            resultIds = nextResultIds
            pendingResultIds = emptyList()
            isSpinning = false
        }
    }

    LaunchedEffect(isSpinning, pendingResultIds) {
        if (isSpinning && pendingResultIds.isEmpty()) {
            isSpinning = false
        }
    }

    SecondaryLargeTitleScaffold(
        title = "Random song",
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = contentPadding.calculateTopPadding(),
                bottom = contentPadding.calculateBottomPadding() + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = "Tap the slot area while spinning to skip straight to the result batch.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = songCount == 3, onClick = { songCount = 3 }, label = { Text("3 songs") })
                    FilterChip(selected = songCount == 4, onClick = { songCount = 4 }, label = { Text("4 songs") })
                }
            }
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = isSpinning, onClick = ::finishSpinImmediately)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        repeat(songCount) { index ->
                            SlotCard(
                                song = displayedSongs[index],
                                slotHeight = slotHeight,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
            item {
                Button(
                    onClick = {
                        if (isSpinning) finishSpinImmediately() else startSpin()
                    },
                    enabled = songs.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Casino, contentDescription = null)
                    Text(
                        text = if (isSpinning) "Skip animation" else "Randomize",
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            if (results.isNotEmpty()) {
                item { Text("Results", style = MaterialTheme.typography.titleMedium) }
                items(results) { song ->
                    SongListCard(
                        song = song,
                        subtitle = song.artist,
                        isTransitioning = activeSharedTransitionSongId == song.songIdentifier,
                        sharedTransitionScope = sharedTransitionScope,
                        animatedVisibilityScope = animatedVisibilityScope,
                        onClick = {
                            openSong(
                                song.songIdentifier,
                                SongSharedTransitionState(
                                    songIdentifier = song.songIdentifier,
                                    displayMode = "LIST",
                                    anchorIndex = 0,
                                    anchorOffset = 0,
                                    sourceRoute = "random"
                                )
                            )
                        }
                    )
                }
            }
        }
    }

    val currentSongs by rememberUpdatedState(displayedSongs.filterNotNull() + results)
    LaunchedEffect(currentSongs) {
        currentSongs
            .mapNotNull { song ->
                CoverArtStore.buildImageRequest(
                    context = context,
                    imageName = song.imageName
                )
            }
            .forEach(imageLoader::enqueue)
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
private fun SlotCard(
    song: Song?,
    slotHeight: Dp,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val imageRequest = remember(context, song?.imageName) {
        song?.imageName?.let { CoverArtStore.buildImageRequest(context = context, imageName = it) }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.height(slotHeight)
    ) {
        Box(contentAlignment = Alignment.Center) {
            AnimatedContent(
                targetState = song,
                label = "slot-card",
                transitionSpec = {
                    slideInVertically { it / 2 } + fadeIn() togetherWith
                        slideOutVertically { -it / 2 } + fadeOut()
                }
            ) { targetSong ->
                if (targetSong == null) {
                    Text("Ready", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        if (imageRequest != null) {
                            AsyncImage(
                                model = imageRequest,
                                contentDescription = targetSong.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(20.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(targetSong.title, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}
