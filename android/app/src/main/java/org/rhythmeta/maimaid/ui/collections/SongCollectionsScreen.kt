package org.rhythmeta.maimaid.ui.collections

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items as columnItems
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.data.CatalogSortOption
import org.rhythmeta.maimaid.core.data.ServerChartPolicy
import org.rhythmeta.maimaid.core.data.SongCollectionCodec
import org.rhythmeta.maimaid.core.data.SongCollectionExportCollection
import org.rhythmeta.maimaid.core.data.SongCollectionExportEntry
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongCollectionEntity
import org.rhythmeta.maimaid.core.database.SongCollectionItemEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.ui.catalog.SongCard
import org.rhythmeta.maimaid.ui.catalog.SongJacket
import org.rhythmeta.maimaid.ui.catalog.SongGridCell
import org.rhythmeta.maimaid.ui.catalog.CatalogDisplayMode
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.components.squircleShape
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.ListPopupDefaults
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.overlay.OverlayListPopup
import top.yukonga.miuix.kmp.window.WindowDialog

private const val CollectionSnackbarDurationMillis = 3_000L

@Composable
fun SongCollectionsScreen(
    container: AppContainer,
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    gameVersions: List<GameVersionEntity> = emptyList(),
    server: String = "jp",
    coverImageStore: CoverImageStore,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    displayMode: CatalogDisplayMode,
    sortOption: CatalogSortOption = CatalogSortOption.DefaultOrder,
    sortAscending: Boolean = true,
    onOpenSong: (String) -> Unit,
    selectedCollectionId: String?,
    onSelectedCollectionIdChange: (String?) -> Unit,
    createRequested: Boolean,
    importRequested: Boolean,
    renameRequested: Boolean,
    detailOnly: Boolean,
    onCreateRequestHandled: () -> Unit,
    onImportRequestHandled: () -> Unit,
    onRenameRequestHandled: () -> Unit,
) {
    val collections by container.songCollectionRepository.collections.collectAsState(emptyList())
    val items by container.songCollectionRepository.items.collectAsState(emptyList())
    var draftName by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val importSuccessMessage = stringResource(R.string.collections_import_success)
    val importFailureMessage = stringResource(R.string.collections_import_failed)

    LaunchedEffect(importRequested) {
        if (importRequested) {
            val code = context.getSystemService(ClipboardManager::class.java)?.primaryClip
                ?.getItemAt(0)?.coerceToText(context)?.toString()
            val result = runCatching {
                require(!code.isNullOrBlank())
                val payload = SongCollectionCodec.decode(code)
                payload.collections.forEach { source ->
                    container.songCollectionRepository.importCollection(source)
                }
            }
            scope.launch {
                snackbarHostState.showSnackbar(
                    message = result.fold({ importSuccessMessage }, { importFailureMessage }),
                    duration = SnackbarDuration.Custom(CollectionSnackbarDurationMillis),
                )
            }
            onImportRequestHandled()
        }
    }
    LaunchedEffect(createRequested) {
        if (createRequested) {
            draftName = ""
        }
    }
    LaunchedEffect(renameRequested, selectedCollectionId) {
        if (renameRequested) {
            draftName = selectedCollectionId
                ?.let { id -> collections.firstOrNull { it.id == id }?.name }
                .orEmpty()
        }
    }

    Box(Modifier.fillMaxSize()) {
        if (!detailOnly) {
            CollectionList(
                collections = collections,
                items = items,
                songs = songs,
                sheets = sheets,
                gameVersions = gameVersions,
                server = server,
                sortOption = sortOption,
                sortAscending = sortAscending,
                coverImageStore = coverImageStore,
                onOpen = { onSelectedCollectionIdChange(it.id) },
                onShare = { collection -> shareCollection(context, collection, items) },
                onDelete = { collection ->
                    scope.launch { container.songCollectionRepository.delete(collection) }
                },
                contentTopPadding = contentTopPadding,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            selectedCollectionId?.let { id ->
                collections.firstOrNull { it.id == id }?.let { activeCollection ->
                    val entries = items.filter { it.collectionId == activeCollection.id && it.deletedAt == null }
                        .sortedBy(SongCollectionItemEntity::position)
                    CollectionDetail(
                        entries = entries,
                        songs = songs,
                        sheets = sheets,
                        coverImageStore = coverImageStore,
                        contentTopPadding = contentTopPadding,
                        gameVersions = gameVersions,
                        sortOption = sortOption,
                        sortAscending = sortAscending,
                        server = server,
                        displayMode = displayMode,
                        onOpenSong = onOpenSong,
                        onDelete = { scope.launch { container.songCollectionRepository.deleteItem(it) } },
                    )
                }
            }
        }
        SnackbarHost(
            state = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 12.dp),
        )
    }

    val showEditor = createRequested || renameRequested
    if (showEditor) {
        WindowDialog(
            show = true,
            title = stringResource(if (renameRequested) R.string.collections_rename else R.string.collections_new),
            onDismissRequest = {
                onCreateRequestHandled()
                onRenameRequestHandled()
            },
        ) {
            TextField(
                value = draftName,
                onValueChange = { draftName = it.take(40) },
                label = stringResource(R.string.collections_name),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                TextButton(
                    text = stringResource(R.string.action_cancel),
                    onClick = {
                        onCreateRequestHandled()
                        onRenameRequestHandled()
                    },
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = {
                        draftName.trim().takeIf(String::isNotEmpty)?.let { name ->
                            scope.launch {
                                if (renameRequested) {
                                    selectedCollectionId
                                        ?.let { id -> collections.firstOrNull { it.id == id } }
                                        ?.let { container.songCollectionRepository.rename(it, name) }
                                } else {
                                    container.songCollectionRepository.create(name)
                                }
                            }
                            onCreateRequestHandled()
                            onRenameRequestHandled()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColorsPrimary(),
                ) { Text(stringResource(R.string.action_done)) }
            }
        }
    }
}

@Composable
private fun CollectionList(
    collections: List<SongCollectionEntity>,
    items: List<SongCollectionItemEntity>,
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    gameVersions: List<GameVersionEntity>,
    server: String,
    sortOption: CatalogSortOption,
    sortAscending: Boolean,
    coverImageStore: CoverImageStore,
    onOpen: (SongCollectionEntity) -> Unit,
    onShare: (SongCollectionEntity) -> Unit,
    onDelete: (SongCollectionEntity) -> Unit,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val songById = remember(songs) { songs.associateBy(SongEntity::songIdentifier) }
    val sheetByKey = remember(sheets) {
        sheets.associateBy { "${it.songIdentifier}|${it.type.lowercase()}|${it.difficulty.lowercase()}" }
    }
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 10.dp, end = 16.dp, bottom = 36.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            columnItems(collections, key = SongCollectionEntity::id) { collection ->
                CollectionSummaryCard(
                    collection = collection,
                    items = items,
                    songById = songById,
                    sheetByKey = sheetByKey,
                    sheets = sheets,
                    gameVersions = gameVersions,
                    server = server,
                    sortOption = sortOption,
                    sortAscending = sortAscending,
                    coverImageStore = coverImageStore,
                    onOpen = onOpen,
                    onShare = onShare,
                    onDelete = onDelete,
                )
                }
        }
        SongListScrollBar(
            state = listState,
            trackPadding = PaddingValues(top = contentTopPadding + 10.dp, bottom = 36.dp),
        )
    }
}

@Composable
private fun CollectionSummaryCard(
    collection: SongCollectionEntity,
    items: List<SongCollectionItemEntity>,
    songById: Map<String, SongEntity>,
    sheetByKey: Map<String, SheetEntity>,
    sheets: List<SheetEntity>,
    gameVersions: List<GameVersionEntity>,
    server: String,
    sortOption: CatalogSortOption,
    sortAscending: Boolean,
    coverImageStore: CoverImageStore,
    onOpen: (SongCollectionEntity) -> Unit,
    onShare: (SongCollectionEntity) -> Unit,
    onDelete: (SongCollectionEntity) -> Unit,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val previews = remember(
        collection.id,
        items,
        songById,
        sheetByKey,
        sheets,
        gameVersions,
        server,
        sortOption,
        sortAscending,
    ) {
        sortCollectionCards(
            entries = items.filter { it.collectionId == collection.id && it.deletedAt == null },
            songById = songById,
            sheetByKey = sheetByKey,
            gameVersions = gameVersions,
            sortOption = sortOption,
            sortAscending = sortAscending,
            server = server,
        )
            .mapNotNull { card -> card.song?.let { song -> card.sheet?.let { sheet -> song to sheet } } }
            .take(CollectionPreviewLimit)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        insideMargin = PaddingValues(0.dp),
        cornerRadius = 16.dp,
    ) {
        CollectionSummaryRow(
            collection = collection,
            itemCount = items.count { it.collectionId == collection.id && it.deletedAt == null },
            previews = previews,
            darkTheme = darkTheme,
            coverImageStore = coverImageStore,
            onOpen = onOpen,
            onShare = onShare,
            onDelete = onDelete,
        )
    }
}

private const val CollectionPreviewLimit = 4

@Composable
private fun CollectionSummaryRow(
    collection: SongCollectionEntity,
    itemCount: Int,
    previews: List<Pair<SongEntity, SheetEntity>>,
    darkTheme: Boolean,
    coverImageStore: CoverImageStore,
    onOpen: (SongCollectionEntity) -> Unit,
    onShare: (SongCollectionEntity) -> Unit,
    onDelete: (SongCollectionEntity) -> Unit,
) {
    val interactionSource = remember(collection.id) { MutableInteractionSource() }
    var menuExpanded by remember(collection.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .clip(squircleShape(12.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    onClick = { onOpen(collection) },
                    onLongClick = { menuExpanded = true },
                )
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = collection.name,
                            style = MiuixTheme.textStyles.body1,
                            color = MiuixTheme.colorScheme.onSurface,
                            maxLines = 1,
                        )
                        Text(
                            text = stringResource(R.string.collections_item_count, itemCount),
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            maxLines = 1,
                        )
                    }
                }
                if (previews.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        previews.forEach { (song, sheet) ->
                            SongJacket(
                                imageName = song.imageName,
                                coverImageStore = coverImageStore,
                                size = 56.dp,
                                cornerRadius = 10.dp,
                                borderColor = SongVisualUtils.difficultyColor(
                                    difficulty = sheet.difficulty,
                                    type = sheet.type,
                                    darkTheme = darkTheme,
                                    brightenDark = true,
                                ),
                                borderWidth = 2.dp,
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.5f),
            )
        }
        OverlayListPopup(
            show = menuExpanded,
            popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
            alignment = PopupPositionProvider.Align.TopEnd,
            enableWindowDim = true,
            onDismissRequest = { menuExpanded = false },
        ) {
            ListPopupColumn {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onShare(collection)
                            menuExpanded = false
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.collections_share))
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDelete(collection)
                            menuExpanded = false
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        tint = MiuixTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(stringResource(R.string.collections_delete))
                }
            }
        }
    }
}

private fun shareCollection(
    context: Context,
    collection: SongCollectionEntity,
    items: List<SongCollectionItemEntity>,
) {
    val entries = items
        .asSequence()
        .filter { it.collectionId == collection.id && it.deletedAt == null }
        .sortedBy(SongCollectionItemEntity::position)
        .map { item ->
            SongCollectionExportEntry(
                songId = item.songId,
                chartType = item.chartType,
                difficulty = item.difficulty,
                position = item.position,
            )
        }
        .toList()
    val export = SongCollectionExportCollection(
        id = collection.id,
        name = collection.name,
        position = collection.sortIndex,
        entries = entries,
    )
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, SongCollectionCodec.encode(listOf(export)))
    }
    context.startActivity(Intent.createChooser(sendIntent, context.getString(R.string.collections_share_chooser)))
}

private data class CollectionCard(val item: SongCollectionItemEntity, val song: SongEntity?, val sheet: SheetEntity?)

private fun sortCollectionCards(
    entries: List<SongCollectionItemEntity>,
    songById: Map<String, SongEntity>,
    sheetByKey: Map<String, SheetEntity>,
    gameVersions: List<GameVersionEntity>,
    sortOption: CatalogSortOption,
    sortAscending: Boolean,
    server: String,
): List<CollectionCard> {
    val versionOrder = entries
        .asSequence()
        .mapNotNull { item -> songById[item.songId]?.version }
        .distinct()
        .associateWith { version -> SongVisualUtils.versionSortOrder(version, gameVersions) }
    return entries
        .map { item ->
            CollectionCard(
                item = item,
                song = songById[item.songId],
                sheet = sheetByKey["${item.songId}|${item.chartType.lowercase()}|${item.difficulty.lowercase()}"],
            )
        }
        .sortedWith { first, second ->
            val firstSong = first.song
            val secondSong = second.song
            if (firstSong == null || secondSong == null) {
                first.item.position.compareTo(second.item.position)
            } else {
                val comparison = when (sortOption) {
                    CatalogSortOption.DefaultOrder -> {
                        val orderComparison = firstSong.sortOrder.compareTo(secondSong.sortOrder)
                        if (sortAscending) orderComparison else -orderComparison
                    }
                    CatalogSortOption.VersionAndDate -> {
                        val versionComparison = (firstSong.version?.let(versionOrder::get) ?: Int.MAX_VALUE)
                            .compareTo(secondSong.version?.let(versionOrder::get) ?: Int.MAX_VALUE)
                        if (versionComparison != 0) {
                            if (sortAscending) versionComparison else -versionComparison
                        } else {
                            val releaseComparison = (firstSong.releaseDate ?: "0000-00-00")
                                .compareTo(secondSong.releaseDate ?: "0000-00-00")
                            if (releaseComparison != 0) {
                                if (sortAscending) releaseComparison else -releaseComparison
                            } else {
                                firstSong.sortOrder.compareTo(secondSong.sortOrder)
                            }
                        }
                    }
                    CatalogSortOption.Difficulty -> {
                        val firstDifficulty = first.sheet
                            ?.let { ServerChartPolicy.metadata(it, server).ratingLevel }
                            ?: 0.0
                        val secondDifficulty = second.sheet
                            ?.let { ServerChartPolicy.metadata(it, server).ratingLevel }
                            ?: 0.0
                        val difficultyComparison = firstDifficulty.compareTo(secondDifficulty)
                        if (difficultyComparison != 0) {
                            if (sortAscending) difficultyComparison else -difficultyComparison
                        } else {
                            firstSong.title.compareTo(secondSong.title, ignoreCase = true)
                        }
                    }
                }
                if (comparison != 0) comparison else first.item.position.compareTo(second.item.position)
            }
        }
}

@Composable
private fun CollectionDetail(
    entries: List<SongCollectionItemEntity>,
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    coverImageStore: CoverImageStore,
    contentTopPadding: androidx.compose.ui.unit.Dp,
    gameVersions: List<GameVersionEntity>,
    sortOption: CatalogSortOption,
    sortAscending: Boolean,
    server: String,
    displayMode: CatalogDisplayMode,
    onOpenSong: (String) -> Unit,
    onDelete: (SongCollectionItemEntity) -> Unit,
) {
    val songById = remember(songs) { songs.associateBy(SongEntity::songIdentifier) }
    val sheetByKey = remember(sheets) { sheets.associateBy { "${it.songIdentifier}|${it.type.lowercase()}|${it.difficulty.lowercase()}" } }
    val cards = remember(entries, songById, sheetByKey, gameVersions, sortOption, sortAscending, server) {
        sortCollectionCards(
            entries = entries,
            songById = songById,
            sheetByKey = sheetByKey,
            gameVersions = gameVersions,
            sortOption = sortOption,
            sortAscending = sortAscending,
            server = server,
        )
    }
    if (displayMode == CatalogDisplayMode.Grid) {
        LazyVerticalGrid(GridCells.Fixed(3), Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 12.dp, top = contentTopPadding + 12.dp, end = 12.dp, bottom = 96.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            gridItems(cards, key = { it.item.id }) { card -> CollectionGridCard(card, coverImageStore, onOpenSong, onDelete) }
        }
    } else {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, top = contentTopPadding + 8.dp, end = 16.dp, bottom = 96.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            columnItems(cards, key = { it.item.id }) { card ->
                CollectionListCard(card, coverImageStore, onOpenSong, onDelete)
            }
        }
    }
}

@Composable
private fun CollectionListCard(card: CollectionCard, coverImageStore: CoverImageStore, onOpenSong: (String) -> Unit, onDelete: (SongCollectionItemEntity) -> Unit) {
    if (card.song == null || card.sheet == null) return MissingCard(card)
    var menuExpanded by remember(card.item.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        SongCard(
            song = card.song,
            sheets = listOf(card.sheet),
            scoresBySheetKey = emptyMap(),
            versions = emptyList(),
            coverImageStore = coverImageStore,
            actualSheet = card.sheet,
            onClick = { onOpenSong(card.song.songIdentifier) },
            onLongClick = { menuExpanded = true },
        )
        CollectionItemDeletePopup(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onDelete = { onDelete(card.item); menuExpanded = false },
        )
    }
}

@Composable
private fun CollectionGridCard(card: CollectionCard, coverImageStore: CoverImageStore, onOpenSong: (String) -> Unit, onDelete: (SongCollectionItemEntity) -> Unit) {
    if (card.song == null || card.sheet == null) return MissingCard(card)
    var menuExpanded by remember(card.item.id) { mutableStateOf(false) }
    Box(Modifier.fillMaxWidth()) {
        SongGridCell(song = card.song, sheets = listOf(card.sheet), scoresBySheetKey = emptyMap(), coverImageStore = coverImageStore, columnCount = 3, cornerRadius = 8.dp, showDots = true, actualSheet = card.sheet, showActualDifficultyIndicator = false, onLongClick = { menuExpanded = true }, onClick = { onOpenSong(card.song.songIdentifier) })
        CollectionItemDeletePopup(
            expanded = menuExpanded,
            onDismiss = { menuExpanded = false },
            onDelete = { onDelete(card.item); menuExpanded = false },
        )
    }
}

@Composable
private fun CollectionItemDeletePopup(
    expanded: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
) {
    OverlayListPopup(
        show = expanded,
        popupPositionProvider = ListPopupDefaults.ContextMenuPositionProvider,
        alignment = PopupPositionProvider.Align.TopEnd,
        enableWindowDim = true,
        onDismissRequest = onDismiss,
    ) {
        ListPopupColumn {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onDelete)
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MiuixTheme.colorScheme.error)
                Spacer(Modifier.width(12.dp))
                Text(stringResource(R.string.collections_delete_item))
            }
        }
    }
}

@Composable
private fun MissingCard(card: CollectionCard) {
    Card(Modifier.fillMaxWidth(), cornerRadius = 14.dp, insideMargin = PaddingValues(14.dp), colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer)) {
        Text(card.item.songId)
        Text("${card.item.chartType.uppercase()} · ${card.item.difficulty.uppercase()}", color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
    }
}
