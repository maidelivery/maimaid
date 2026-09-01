package org.rhythmeta.maimaid.ui.lettergame

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CollectionsBookmark
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.LetterGameCreateRequest
import org.rhythmeta.maimaid.core.data.LetterGameRoom
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SongCategoryEntity
import org.rhythmeta.maimaid.core.database.SongCollectionEntity
import org.rhythmeta.maimaid.core.database.SongCollectionItemEntity
import org.rhythmeta.maimaid.ui.components.ExpandableBottomSheet
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.appTextFieldColors
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.DropdownImpl
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.ListPopupColumn
import top.yukonga.miuix.kmp.basic.PopupPositionProvider
import top.yukonga.miuix.kmp.basic.RangeSlider
import top.yukonga.miuix.kmp.basic.Switch
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowListPopup
import kotlin.time.Duration.Companion.milliseconds

private const val MinimumTurnSeconds = 15
private const val MaximumTurnSeconds = 120
private const val MinimumStalledRounds = 1
private const val MaximumStalledRounds = 10
private const val MaximumSongCount = 5_000
private const val MinimumHintCost = 1
private const val MaximumHintCost = 100

private data class RoomSettingsDraft(
    val hostMode: String,
    val turnSeconds: String,
    val stalledRounds: String,
    val songCount: String,
    val publicHintCost: String,
    val privateHintCost: String,
    val selectionMode: String,
    val excludeDeleted: Boolean,
    val englishOnly: Boolean,
    val minVersion: String?,
    val maxVersion: String?,
    val categories: Set<String>,
    val chartTypes: Set<String>,
    val collectionIds: Set<String>,
)

@Composable
internal fun LetterGameRoomSettingsSheet(
    visible: Boolean,
    room: LetterGameRoom,
    currentUserId: String?,
    matchInProgress: Boolean,
    gameVersions: List<GameVersionEntity>,
    songCategories: List<SongCategoryEntity>,
    collections: List<SongCollectionEntity>,
    collectionItems: List<SongCollectionItemEntity>,
    onDismiss: () -> Unit,
    onUpdate: suspend (LetterGameCreateRequest) -> LetterGameRoom,
    onError: (String) -> Unit,
) {
    val versions = remember(gameVersions) { gameVersions.sortedBy(GameVersionEntity::sortOrder).map(GameVersionEntity::name) }
    val categories = remember(songCategories) {
        songCategories
            .sortedBy(SongCategoryEntity::sortOrder)
            .map(SongCategoryEntity::name)
            .filterNot(::isUtageValue)
    }
    val activeCollections = remember(collections) { collections.filter { it.deletedAt == null }.sortedBy(SongCollectionEntity::sortIndex) }
    val activeCollectionItems = remember(collectionItems) { collectionItems.filter { it.deletedAt == null } }
    val acceptedPlayerCount = room.members.count { it.status == "accepted" }.coerceAtLeast(1)
    val canEdit = room.hostUserId == currentUserId && !matchInProgress
    var draft by remember(room.id) { mutableStateOf(room.toSettingsDraft(acceptedPlayerCount)) }
    var editRevision by remember(room.id) { mutableIntStateOf(0) }
    var submitting by remember(room.id) { mutableStateOf(false) }

    LaunchedEffect(room.hostMode, room.settings, acceptedPlayerCount) {
        if (!submitting) draft = room.toSettingsDraft(acceptedPlayerCount)
    }

    val selectedSongCount = remember(draft.collectionIds, activeCollectionItems) {
        activeCollectionItems
            .asSequence()
            .filter { it.collectionId in draft.collectionIds }
            .map(SongCollectionItemEntity::songId)
            .distinct()
            .count()
    }
    val validation = draft.validate(acceptedPlayerCount, selectedSongCount)

    LaunchedEffect(editRevision) {
        if (editRevision == 0 || !canEdit) return@LaunchedEffect
        delay(350.milliseconds)
        val request = draft.toRequest(room, versions, selectedSongCount) ?: return@LaunchedEffect
        submitting = true
        try {
            val updated = onUpdate(request)
            if (currentCoroutineContext().isActive) {
                draft = updated.toSettingsDraft(acceptedPlayerCount)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            onError(error.message ?: "Request failed")
        } finally {
            if (currentCoroutineContext().isActive) submitting = false
        }
    }

    fun edit(transform: (RoomSettingsDraft) -> RoomSettingsDraft) {
        if (!canEdit) return
        draft = transform(draft)
        editRevision += 1
    }

    fun resetSettings() {
        if (!canEdit) return
        draft = defaultRoomSettingsDraft(acceptedPlayerCount)
        editRevision += 1
    }

    ExpandableBottomSheet(
        visible = visible,
        onDismissRequest = onDismiss,
        expandActionLabel = stringResource(R.string.letter_game_settings_expand),
        collapseActionLabel = stringResource(R.string.letter_game_settings_collapse),
        expandedStateDescription = stringResource(R.string.letter_game_settings_sheet_expanded),
        halfExpandedStateDescription = stringResource(R.string.letter_game_settings_sheet_half),
        header = {
            IconButton(onClick = ::resetSettings, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.letter_game_settings_reset),
                    tint = if (canEdit) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
            Text(
                text = stringResource(R.string.letter_game_room_settings),
                style = MiuixTheme.textStyles.title3,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.CenterEnd)) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = stringResource(R.string.letter_game_dismiss),
                    tint = MiuixTheme.colorScheme.primary,
                )
            }
        },
    ) { topInset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = topInset + 12.dp, end = 20.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            if (!canEdit) {
                item {
                    Text(
                        text = stringResource(
                            if (matchInProgress) R.string.letter_game_settings_match_locked else R.string.letter_game_host_only,
                        ),
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
            }
            item {
                SettingsSection(stringResource(R.string.letter_game_room_settings)) {
                    SettingsDropdown(
                        title = stringResource(R.string.letter_game_host_rotation),
                        value = draft.hostMode,
                        values = listOf("fixed", "rotate"),
                        valueLabel = { hostModeLabel(it) },
                        enabled = canEdit,
                        onSelect = { value -> edit { it.copy(hostMode = value) } },
                    )
                    ValidatedNumberField(
                        label = stringResource(R.string.letter_game_turn_seconds),
                        value = draft.turnSeconds,
                        enabled = canEdit,
                        valid = validation.turnSeconds,
                        minimum = MinimumTurnSeconds,
                        maximum = MaximumTurnSeconds,
                        onValueChange = { value -> edit { it.copy(turnSeconds = value.numericInput(3)) } },
                    )
                    ValidatedNumberField(
                        label = stringResource(R.string.letter_game_stalled_rounds),
                        value = draft.stalledRounds,
                        enabled = canEdit,
                        valid = validation.stalledRounds,
                        minimum = MinimumStalledRounds,
                        maximum = MaximumStalledRounds,
                        onValueChange = { value -> edit { it.copy(stalledRounds = value.numericInput(2)) } },
                    )
                    ValidatedNumberField(
                        label = stringResource(R.string.letter_game_song_count),
                        value = if (draft.selectionMode == "collection") selectedSongCount.toString() else draft.songCount,
                        enabled = canEdit && draft.selectionMode == "filtered_random",
                        valid = validation.songCount,
                        minimum = acceptedPlayerCount,
                        maximum = MaximumSongCount,
                        onValueChange = { value -> edit { it.copy(songCount = value.numericInput(4)) } },
                    )
                    if (room.visibility == "private") {
                        ValidatedNumberField(
                            label = stringResource(R.string.letter_game_public_hint_cost),
                            value = draft.publicHintCost,
                            enabled = canEdit,
                            valid = validation.publicHintCost,
                            minimum = MinimumHintCost,
                            maximum = MaximumHintCost,
                            onValueChange = { value -> edit { it.copy(publicHintCost = value.numericInput(3)) } },
                        )
                        ValidatedNumberField(
                            label = stringResource(R.string.letter_game_private_hint_cost),
                            value = draft.privateHintCost,
                            enabled = canEdit,
                            valid = validation.privateHintCost,
                            minimum = MinimumHintCost,
                            maximum = MaximumHintCost,
                            onValueChange = { value -> edit { it.copy(privateHintCost = value.numericInput(3)) } },
                        )
                        if (!validation.hintOrder) {
                            ValidationMessage(stringResource(R.string.letter_game_hint_cost_order_error))
                        }
                    }
                }
            }
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    insideMargin = PaddingValues(16.dp),
                ) {
                    SettingsDropdown(
                        title = stringResource(R.string.letter_game_song_source),
                        value = draft.selectionMode,
                        values = listOf("filtered_random", "collection"),
                        valueLabel = { sourceModeLabel(it) },
                        enabled = canEdit,
                        onSelect = { value -> edit { it.copy(selectionMode = value) } },
                    )
                }
            }
            if (draft.selectionMode == "filtered_random") {
                item {
                    SettingsSection(stringResource(R.string.letter_game_random_filters)) {
                        SettingsSwitchRow(
                            title = stringResource(R.string.letter_game_exclude_deleted),
                            checked = draft.excludeDeleted,
                            enabled = canEdit,
                            onCheckedChange = { checked -> edit { it.copy(excludeDeleted = checked) } },
                        )
                        SettingsSwitchRow(
                            title = stringResource(R.string.letter_game_english_only),
                            checked = draft.englishOnly,
                            enabled = canEdit,
                            onCheckedChange = { checked -> edit { it.copy(englishOnly = checked) } },
                        )
                    }
                }
                if (!draft.excludeDeleted && !draft.englishOnly) {
                    item {
                        Text(
                            text = stringResource(R.string.letter_game_filters_may_be_difficult),
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
                item {
                    SettingsCardSection(stringResource(R.string.letter_game_version_range)) {
                        VersionRangeSetting(
                            versions = versions,
                            minVersion = draft.minVersion,
                            maxVersion = draft.maxVersion,
                            enabled = canEdit,
                            onChange = { minVersion, maxVersion ->
                                edit { it.copy(minVersion = minVersion, maxVersion = maxVersion) }
                            },
                        )
                    }
                }
                item {
                    SettingsCardSection(stringResource(R.string.letter_game_filter_category)) {
                        FilterChips(
                            values = categories,
                            selected = draft.categories,
                            enabled = canEdit,
                            displayValue = { it },
                            onToggle = { value -> edit { it.copy(categories = it.categories.toggled(value)) } },
                        )
                    }
                }
                item {
                    SettingsCardSection(stringResource(R.string.letter_game_filter_type)) {
                        FilterChips(
                            values = listOf("standard", "dx"),
                            selected = draft.chartTypes,
                            enabled = canEdit,
                            displayValue = { if (it == "standard") "STD" else "DX" },
                            onToggle = { value -> edit { it.copy(chartTypes = it.chartTypes.toggled(value)) } },
                        )
                    }
                }
            } else {
                item {
                    SettingsSection(stringResource(R.string.letter_game_source_collection)) {
                        if (room.hostUserId == currentUserId) {
                            if (activeCollections.isEmpty()) {
                                Text(
                                    text = stringResource(R.string.letter_game_collections_empty),
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            } else {
                                activeCollections.forEach { collection ->
                                    val itemCount = activeCollectionItems
                                        .asSequence()
                                        .filter { it.collectionId == collection.id }
                                        .map(SongCollectionItemEntity::songId)
                                        .distinct()
                                        .count()
                                    CollectionChoiceRow(
                                        name = collection.name,
                                        songCount = itemCount,
                                        selected = collection.id in draft.collectionIds,
                                        enabled = canEdit,
                                        onToggle = {
                                            edit { it.copy(collectionIds = it.collectionIds.toggled(collection.id)) }
                                        },
                                    )
                                }
                            }
                        } else if (room.settings.selectedCollections.isEmpty()) {
                            Text(
                                text = stringResource(R.string.letter_game_no_collections_selected),
                                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                            )
                        } else {
                            room.settings.selectedCollections.forEach { collection ->
                                CollectionChoiceRow(
                                    name = collection.name,
                                    songCount = collection.songCount,
                                    selected = true,
                                    enabled = false,
                                    onToggle = {},
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class SettingsValidation(
    val turnSeconds: Boolean,
    val stalledRounds: Boolean,
    val songCount: Boolean,
    val publicHintCost: Boolean,
    val privateHintCost: Boolean,
    val hintOrder: Boolean,
) {
    val valid: Boolean
        get() = turnSeconds && stalledRounds && songCount && publicHintCost && privateHintCost && hintOrder
}

private fun RoomSettingsDraft.validate(acceptedPlayerCount: Int, selectedSongCount: Int): SettingsValidation {
    val publicCost = publicHintCost.toIntOrNull()
    val privateCost = privateHintCost.toIntOrNull()
    return SettingsValidation(
        turnSeconds = turnSeconds.toIntOrNull() in MinimumTurnSeconds..MaximumTurnSeconds,
        stalledRounds = stalledRounds.toIntOrNull() in MinimumStalledRounds..MaximumStalledRounds,
        songCount = if (selectionMode == "collection") {
            selectedSongCount == 0 || selectedSongCount in acceptedPlayerCount..MaximumSongCount
        } else {
            songCount.toIntOrNull() in acceptedPlayerCount..MaximumSongCount
        },
        publicHintCost = publicCost in MinimumHintCost..MaximumHintCost,
        privateHintCost = privateCost in MinimumHintCost..MaximumHintCost,
        hintOrder = publicCost != null && privateCost != null && privateCost > publicCost,
    )
}

private fun RoomSettingsDraft.toRequest(
    room: LetterGameRoom,
    versions: List<String>,
    selectedSongCount: Int,
): LetterGameCreateRequest? {
    val validation = validate(room.members.count { it.status == "accepted" }.coerceAtLeast(1), selectedSongCount)
    if (!validation.valid) return null
    val selectionConfig = if (selectionMode == "collection") {
        mapOf("collectionIds" to JsonArray(collectionIds.sorted().map(::JsonPrimitive)))
    } else {
        mapOf(
            "excludeDeleted" to JsonPrimitive(excludeDeleted),
            "englishOnly" to JsonPrimitive(englishOnly),
            "minVersion" to versionBoundary(minVersion, versions.firstOrNull()),
            "maxVersion" to versionBoundary(maxVersion, versions.lastOrNull()),
            "categories" to JsonArray(categories.sorted().map(::JsonPrimitive)),
            "chartTypes" to JsonArray(chartTypes.sorted().map(::JsonPrimitive)),
        )
    }
    return LetterGameCreateRequest(
        visibility = room.visibility,
        hostMode = hostMode,
        turnDurationSeconds = turnSeconds.toInt(),
        stalledRoundLimit = stalledRounds.toInt(),
        songCount = if (selectionMode == "collection") selectedSongCount else songCount.toInt(),
        publicHintCost = publicHintCost.toInt(),
        privateHintCost = privateHintCost.toInt(),
        selectionMode = selectionMode,
        selectionConfig = selectionConfig,
    )
}

private fun versionBoundary(value: String?, defaultValue: String?): JsonElement =
    if (value == null || value == defaultValue) JsonNull else JsonPrimitive(value)

private fun defaultRoomSettingsDraft(acceptedPlayerCount: Int): RoomSettingsDraft =
    RoomSettingsDraft(
        hostMode = "fixed",
        turnSeconds = "30",
        stalledRounds = "3",
        songCount = (acceptedPlayerCount * 3).toString(),
        publicHintCost = "5",
        privateHintCost = "10",
        selectionMode = "filtered_random",
        excludeDeleted = true,
        englishOnly = true,
        minVersion = null,
        maxVersion = null,
        categories = emptySet(),
        chartTypes = setOf("standard", "dx"),
        collectionIds = emptySet(),
    )

private fun LetterGameRoom.toSettingsDraft(acceptedPlayerCount: Int): RoomSettingsDraft {
    val config = settings.selectionConfig
    val chartTypes = config.stringSet("chartTypes").ifEmpty { setOf("standard", "dx") }
    return RoomSettingsDraft(
        hostMode = hostMode,
        turnSeconds = settings.turnDurationSeconds.toString(),
        stalledRounds = settings.stalledRoundLimit.toString(),
        songCount = ((settings.songCountOverride ?: (acceptedPlayerCount * 3))).toString(),
        publicHintCost = settings.publicHintCost.toString(),
        privateHintCost = settings.privateHintCost.toString(),
        selectionMode = settings.selectionMode,
        excludeDeleted = config["excludeDeleted"]?.jsonPrimitive?.booleanOrNull ?: true,
        englishOnly = config["englishOnly"]?.jsonPrimitive?.booleanOrNull ?: true,
        minVersion = config["minVersion"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
        maxVersion = config["maxVersion"]?.takeUnless { it is JsonNull }?.jsonPrimitive?.contentOrNull,
        categories = config.stringSet("categories"),
        chartTypes = chartTypes,
        collectionIds = config.stringSet("collectionIds"),
    )
}

private fun Map<String, JsonElement>.stringSet(key: String): Set<String> =
    runCatching { get(key)?.jsonArray?.mapNotNull { it.jsonPrimitive.contentOrNull }?.toSet().orEmpty() }.getOrDefault(emptySet())

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = title,
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(start = 4.dp),
        )
        Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, insideMargin = PaddingValues(16.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) { content() }
        }
    }
}

@Composable
private fun SettingsCardSection(title: String, content: @Composable () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth(), cornerRadius = 16.dp, insideMargin = PaddingValues(16.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(start = 4.dp),
            )
            content()
        }
    }
}

@Composable
private fun SettingsDropdown(
    title: String,
    value: String,
    values: List<String>,
    valueLabel: @Composable (String) -> String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(text = title, style = MiuixTheme.textStyles.footnote1, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Box(Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .squircleSurface(
                        color = MiuixTheme.colorScheme.surfaceContainerHigh,
                        cornerRadius = 14.dp,
                        extension = SquircleExtension,
                    )
                    .clickable(enabled = enabled, role = Role.Button) { expanded = true }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = valueLabel(value),
                    color = if (enabled) MiuixTheme.colorScheme.onSurface else MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.weight(1f),
                )
                Icon(Icons.Rounded.KeyboardArrowDown, contentDescription = null, tint = MiuixTheme.colorScheme.onSurfaceVariantSummary)
            }
            WindowListPopup(
                show = expanded,
                alignment = PopupPositionProvider.Align.End,
                enableWindowDim = false,
                onDismissRequest = { expanded = false },
            ) {
                ListPopupColumn {
                    values.forEachIndexed { index, option ->
                        DropdownImpl(
                            text = valueLabel(option),
                            optionSize = values.size,
                            isSelected = option == value,
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
private fun ValidatedNumberField(
    label: String,
    value: String,
    enabled: Boolean,
    valid: Boolean,
    minimum: Int,
    maximum: Int,
    onValueChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = label,
            enabled = enabled,
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            colors = appTextFieldColors(),
            modifier = Modifier.fillMaxWidth(),
        )
        if (!valid) {
            ValidationMessage(stringResource(R.string.letter_game_number_range_error, minimum, maximum))
        }
    }
}

@Composable
private fun ValidationMessage(message: String) {
    Text(text = message, style = MiuixTheme.textStyles.footnote2, color = MiuixTheme.colorScheme.error)
}

@Composable
private fun SettingsSwitchRow(title: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.VisibilityOff, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Text(title, modifier = Modifier.weight(1f))
        Switch(checked = checked, enabled = enabled, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun VersionRangeSetting(
    versions: List<String>,
    minVersion: String?,
    maxVersion: String?,
    enabled: Boolean,
    onChange: (String?, String?) -> Unit,
) {
    if (versions.isEmpty()) {
        Text(stringResource(R.string.letter_game_versions_unavailable), color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        return
    }
    val lastIndex = versions.lastIndex
    val start = minVersion?.let(versions::indexOf)?.takeIf { it >= 0 } ?: 0
    val end = maxVersion?.let(versions::indexOf)?.takeIf { it >= 0 } ?: lastIndex
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = "${versions[start]} - ${versions[end]}",
            color = MiuixTheme.colorScheme.primary,
            style = MiuixTheme.textStyles.body1,
        )
        if (lastIndex > 0) {
            RangeSlider(
                value = start.toFloat()..end.toFloat(),
                onValueChange = { range ->
                    val nextStart = range.start.toInt().coerceIn(0, lastIndex)
                    val nextEnd = range.endInclusive.toInt().coerceIn(nextStart, lastIndex)
                    onChange(
                        versions[nextStart].takeUnless { nextStart == 0 },
                        versions[nextEnd].takeUnless { nextEnd == lastIndex },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = enabled,
                valueRange = 0f..lastIndex.toFloat(),
                steps = (lastIndex - 1).coerceAtLeast(0),
            )
        }
    }
}

@Composable
@OptIn(ExperimentalLayoutApi::class)
private fun FilterChips(
    values: List<String>,
    selected: Set<String>,
    enabled: Boolean,
    displayValue: (String) -> String,
    onToggle: (String) -> Unit,
) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            val isSelected = value in selected
            val background by animateColorAsState(
                if (isSelected) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurface.copy(alpha = 0.06f),
                label = "letter-game-filter-chip",
            )
            val contentColor = if (isSelected) Color.White else MiuixTheme.colorScheme.onSurface
            Box(
                modifier = Modifier
                    .squircleSurface(background, 50.dp, SquircleExtension)
                    .squircleBorder(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.09f), 50.dp, SquircleExtension)
                    .toggleable(
                        value = isSelected,
                        enabled = enabled,
                        role = Role.Checkbox,
                        onValueChange = { onToggle(value) },
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(displayValue(value), color = contentColor, style = MiuixTheme.textStyles.footnote1)
            }
        }
    }
}

@Composable
private fun CollectionChoiceRow(
    name: String,
    songCount: Int,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(selected, enabled = enabled, role = Role.Checkbox) { onToggle() }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.CollectionsBookmark, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(name)
            Text(
                stringResource(R.string.letter_game_collection_song_count, songCount),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
        if (selected) Icon(Icons.Rounded.Check, contentDescription = null, tint = MiuixTheme.colorScheme.primary)
    }
}

@Composable
private fun hostModeLabel(mode: String): String = stringResource(
    if (mode == "rotate") R.string.letter_game_rotate_host else R.string.letter_game_fixed_host,
)

@Composable
private fun sourceModeLabel(mode: String): String = stringResource(
    if (mode == "collection") R.string.letter_game_source_collection else R.string.letter_game_source_filtered_random,
)

private fun String.numericInput(maximumLength: Int): String = filter(Char::isDigit).take(maximumLength)

private fun Set<String>.toggled(value: String): Set<String> = if (value in this) this - value else this + value

private fun isUtageValue(value: String): Boolean =
    value.contains("utage", ignoreCase = true) || value.contains("宴")
