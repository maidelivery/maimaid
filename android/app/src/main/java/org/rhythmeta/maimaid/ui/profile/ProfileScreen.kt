package org.rhythmeta.maimaid.ui.profile

import android.graphics.Bitmap
import android.graphics.ImageDecoder
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddPhotoAlternate
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.ProfileCredentials
import org.rhythmeta.maimaid.core.data.RatingUtils
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity
import org.rhythmeta.maimaid.ui.components.ExpandableBottomSheet
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private sealed interface ProfileEditorTarget {
    data object Create : ProfileEditorTarget
    data class Edit(val profile: UserProfileEntity) : ProfileEditorTarget
}

@Composable
fun ProfileScreen(
    container: AppContainer,
    versions: List<GameVersionEntity>,
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    createRequested: Boolean,
    onCreateRequestHandled: () -> Unit,
) {
    val profiles by container.profileRepository.profiles.collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editorTarget by remember { mutableStateOf<ProfileEditorTarget?>(null) }

    LaunchedEffect(createRequested) {
        if (createRequested) {
            editorTarget = ProfileEditorTarget.Create
            onCreateRequestHandled()
        }
    }

    if (profiles.isEmpty()) {
        ProfileEmptyState(onCreate = { editorTarget = ProfileEditorTarget.Create })
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(profiles, key = UserProfileEntity::id) { profile ->
                ProfileListCard(
                    profile = profile,
                    versions = versions,
                    songs = songs,
                    sheets = sheets,
                    onActivate = {
                        if (!profile.isActive) {
                            scope.launch { container.profileRepository.activate(profile) }
                        }
                    },
                    onEdit = { editorTarget = ProfileEditorTarget.Edit(profile) },
                    onDelete = {
                        scope.launch {
                            if (container.profileRepository.delete(profile)) {
                                container.profileAvatarStore.deleteStored(profile.avatarPath)
                                container.profileCredentialStore.delete(profile.id)
                            }
                        }
                    },
                )
            }
        }
    }

    ProfileEditorSheet(
        visible = editorTarget != null,
        profile = (editorTarget as? ProfileEditorTarget.Edit)?.profile,
        container = container,
        onDismiss = { editorTarget = null },
    )
}

@Composable
private fun ProfileEmptyState(onCreate: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            modifier = Modifier.size(54.dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        Text(
            text = stringResource(R.string.profile_empty_title),
            style = MiuixTheme.textStyles.title2,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp),
        )
        Text(
            text = stringResource(R.string.profile_empty_description),
            style = MiuixTheme.textStyles.body2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            modifier = Modifier.padding(top = 4.dp, bottom = 18.dp),
        )
        Button(onClick = onCreate, colors = ButtonDefaults.buttonColorsPrimary()) {
            Text(stringResource(R.string.profile_create))
        }
    }
}

@Composable
private fun ProfileListCard(
    profile: UserProfileEntity,
    versions: List<GameVersionEntity>,
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val version = RatingUtils.latestVersionForServer(songs, sheets, versions, profile.server)?.let {
        SongVisualUtils.versionAbbreviation(it, versions)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        colors = CardDefaults.defaultColors(
            color = if (profile.isActive) {
                MiuixTheme.colorScheme.secondaryContainer
            } else {
                MiuixTheme.colorScheme.surfaceContainer
            },
        ),
        showIndication = true,
        onClick = onActivate,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProfileAvatar(profile = profile, size = 50)
            Spacer(Modifier.width(14.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = profile.name.ifBlank { stringResource(R.string.profile_unnamed) },
                        style = MiuixTheme.textStyles.body1,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (profile.isActive) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = stringResource(R.string.profile_active),
                            modifier = Modifier.size(16.dp),
                            tint = ProfileActiveColor,
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ProfileServerBadge(profile.server)
                    version?.let {
                        Text(
                            text = it,
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }
            IconButton(onClick = onEdit) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.profile_edit),
                    tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                )
            }
            if (!profile.isActive) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.profile_delete),
                        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
                    )
                }
            }
        }
    }
}

@Composable
internal fun ProfileEditorSheet(
    visible: Boolean,
    profile: UserProfileEntity?,
    container: AppContainer,
    onDismiss: () -> Unit,
) {
    val editing = profile != null
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var plate by remember { mutableStateOf("") }
    var server by remember { mutableStateOf("jp") }
    var dfUsername by remember { mutableStateOf("") }
    var dfToken by remember { mutableStateOf("") }
    var lxnsToken by remember { mutableStateOf("") }
    var b35Text by remember { mutableStateOf("35") }
    var b15Text by remember { mutableStateOf("15") }
    var avatarPath by remember { mutableStateOf<String?>(null) }
    var stagedAvatarPath by remember { mutableStateOf<String?>(null) }
    var clearAvatar by remember { mutableStateOf(false) }
    var avatarEditorBitmap by remember { mutableStateOf<Bitmap?>(null) }
    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        uri?.let {
            scope.launch {
                avatarEditorBitmap = withContext(Dispatchers.IO) {
                    runCatching {
                        val source = ImageDecoder.createSource(context.contentResolver, it)
                        ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val maxDimension = maxOf(info.size.width, info.size.height)
                            if (maxDimension > 4_096) {
                                val scale = 4_096f / maxDimension
                                decoder.setTargetSize(
                                    (info.size.width * scale).toInt(),
                                    (info.size.height * scale).toInt(),
                                )
                            }
                            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        }
                    }.getOrNull()
                }
            }
        }
    }

    LaunchedEffect(visible, profile?.id) {
        if (!visible) return@LaunchedEffect
        val credentials = profile?.let { container.profileCredentialStore.credentials(it.id) }
        name = profile?.name.orEmpty()
        plate = profile?.plate.orEmpty()
        server = profile?.server ?: "jp"
        dfUsername = profile?.dfUsername.orEmpty()
        dfToken = credentials?.divingFishToken.orEmpty()
        lxnsToken = credentials?.lxnsToken.orEmpty()
        b35Text = (profile?.b35Count ?: 35).toString()
        b15Text = (profile?.b15Count ?: 15).toString()
        avatarPath = profile?.avatarPath
        stagedAvatarPath = null
        clearAvatar = false
        avatarEditorBitmap = null
    }

    val dismiss = {
        container.profileAvatarStore.discard(stagedAvatarPath)
        stagedAvatarPath = null
        onDismiss()
    }
    val save: () -> Unit = {
        scope.launch {
            val targetProfile = profile ?: container.profileRepository.create(
                name = name,
                server = server,
                avatarPath = null,
                dfUsername = dfUsername,
                plate = plate,
            )
            val committedAvatar = stagedAvatarPath?.let {
                container.profileAvatarStore.commit(it, targetProfile.id)
            }
            container.profileRepository.save(
                targetProfile.copy(
                    name = name,
                    plate = plate,
                    server = server,
                    dfUsername = dfUsername,
                    avatarPath = when {
                        clearAvatar -> null
                        committedAvatar != null -> committedAvatar
                        else -> targetProfile.avatarPath
                    },
                    avatarUrl = when {
                        clearAvatar || committedAvatar != null -> null
                        else -> targetProfile.avatarUrl
                    },
                    b35Count = b35Text.toIntOrNull()?.coerceAtLeast(1) ?: targetProfile.b35Count,
                    b15Count = b15Text.toIntOrNull()?.coerceAtLeast(1) ?: targetProfile.b15Count,
                ),
            )
            if (clearAvatar || committedAvatar != null) {
                container.profileAvatarStore.deleteStored(targetProfile.avatarPath)
            }
            container.profileCredentialStore.save(
                targetProfile.id,
                ProfileCredentials(dfToken.trim(), lxnsToken.trim()),
            )
            stagedAvatarPath = null
            onDismiss()
        }
        Unit
    }

    ExpandableBottomSheet(
        visible = visible,
        onDismissRequest = dismiss,
        expandActionLabel = stringResource(R.string.profile_sheet_expand),
        collapseActionLabel = stringResource(R.string.profile_sheet_collapse),
        expandedStateDescription = stringResource(R.string.profile_sheet_expanded),
        halfExpandedStateDescription = stringResource(R.string.profile_sheet_half),
        header = {
            IconButton(onClick = dismiss, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.action_cancel),
                )
            }
            Text(
                text = stringResource(
                    if (editing) R.string.profile_edit_title else R.string.profile_create_title,
                ),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
                maxLines = 1,
            )
            if (name.isNotBlank()) {
                IconButton(onClick = save, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = stringResource(R.string.profile_save),
                        tint = MiuixTheme.colorScheme.primary,
                    )
                }
            }
        },
    ) { topInset ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = topInset + 12.dp,
                end = 16.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                ProfileAvatarEditor(
                    avatarPath = avatarPath,
                    avatarUrl = profile?.avatarUrl?.takeUnless { clearAvatar },
                    onSelect = {
                        photoLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                    onClear = {
                        container.profileAvatarStore.discard(stagedAvatarPath)
                        stagedAvatarPath = null
                        avatarPath = null
                        clearAvatar = true
                    },
                )
            }
            item {
                ProfileEditorSection(stringResource(R.string.profile_section_basic)) {
                    TextField(
                        value = name,
                        onValueChange = { name = it },
                        label = stringResource(R.string.profile_name),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                    )
                    TextField(
                        value = plate,
                        onValueChange = { plate = it },
                        label = stringResource(R.string.profile_plate),
                        useLabelAsPlaceholder = true,
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 14.dp,
                    )
                    WindowDropdownPreference(
                        items = listOf(
                            stringResource(R.string.server_jp),
                            stringResource(R.string.server_intl),
                            stringResource(R.string.server_cn),
                        ),
                        selectedIndex = ServerValues.indexOf(server).coerceAtLeast(0),
                        title = stringResource(R.string.profile_server),
                        insideMargin = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                        onSelectedIndexChange = { index -> server = ServerValues[index] },
                    )
                }
            }
            if (editing) {
                item {
                    ProfileEditorSection(stringResource(R.string.profile_section_b50)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ProfileNumberField(
                                value = b35Text,
                                onValueChange = { b35Text = it },
                                label = stringResource(R.string.best50_capacity_old),
                                modifier = Modifier.weight(1f),
                            )
                            ProfileNumberField(
                                value = b15Text,
                                onValueChange = { b15Text = it },
                                label = stringResource(R.string.best50_capacity_new),
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                item {
                    ProfileEditorSection(stringResource(R.string.profile_section_credentials)) {
                        TextField(
                            value = dfUsername,
                            onValueChange = { dfUsername = it },
                            label = stringResource(R.string.profile_df_username),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                        )
                        TextField(
                            value = dfToken,
                            onValueChange = { dfToken = it },
                            label = stringResource(R.string.profile_df_token),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                        )
                        TextField(
                            value = lxnsToken,
                            onValueChange = { lxnsToken = it },
                            label = stringResource(R.string.profile_lxns_token),
                            useLabelAsPlaceholder = true,
                            singleLine = true,
                            visualTransformation = PasswordVisualTransformation(),
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 14.dp,
                        )
                    }
                }
            }
        }
    }

    avatarEditorBitmap?.let { editorBitmap ->
        AvatarCropEditor(
            bitmap = editorBitmap,
            onDismiss = { avatarEditorBitmap = null },
            onApply = { croppedBitmap ->
                scope.launch {
                    container.profileAvatarStore.stage(croppedBitmap)?.let { staged ->
                        container.profileAvatarStore.discard(stagedAvatarPath)
                        stagedAvatarPath = staged
                        avatarPath = staged
                        clearAvatar = false
                    }
                    avatarEditorBitmap = null
                }
            },
        )
    }
}

@Composable
private fun ProfileAvatarEditor(
    avatarPath: String?,
    avatarUrl: String?,
    onSelect: () -> Unit,
    onClear: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ProfileAvatar(
            profile = UserProfileEntity(
                id = "preview",
                name = "",
                server = "jp",
                avatarPath = avatarPath,
                avatarUrl = avatarUrl,
                isActive = false,
                createdAt = 0,
            ),
            size = 84,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Button(onClick = onSelect) {
                Icon(Icons.Rounded.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(7.dp))
                Text(stringResource(R.string.profile_change_avatar))
            }
            if (avatarPath != null || avatarUrl != null) {
                Button(onClick = onClear) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(7.dp))
                    Text(stringResource(R.string.profile_clear_avatar))
                }
            }
        }
    }
}

@Composable
private fun ProfileAvatar(profile: UserProfileEntity, size: Int) {
    val model = remember(profile.avatarPath, profile.avatarUrl) {
        profile.avatarPath
            ?.let(::File)
            ?.takeIf(File::isFile)
            ?: profile.avatarUrl?.takeIf(String::isNotBlank)
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .squircleSurface(
                color = MiuixTheme.colorScheme.secondaryContainer,
                cornerRadius = (size / 2).dp,
                extension = SquircleExtension,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = Icons.Rounded.Person,
            contentDescription = null,
            modifier = Modifier.size((size * 0.56f).dp),
            tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
        )
        model?.let {
            AsyncImage(
                model = it,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        }
    }
}

@Composable
private fun ProfileServerBadge(server: String) {
    val index = ServerValues.indexOf(server.lowercase()).coerceAtLeast(0)
    val label = stringResource(listOf(R.string.server_jp, R.string.server_intl, R.string.server_cn)[index])
    val color = listOf(ServerJapanColor, ServerInternationalColor, ServerChinaColor)[index]
    Text(
        text = label,
        style = MiuixTheme.textStyles.footnote2,
        fontWeight = FontWeight.Bold,
        color = color,
        modifier = Modifier
            .squircleSurface(
                color = color.copy(alpha = 0.14f),
                cornerRadius = 10.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 7.dp, vertical = 3.dp),
    )
}

@Composable
private fun ProfileEditorSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column {
        SmallTitle(
            text = title,
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 7.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(14.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable
private fun ProfileNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    TextField(
        value = value,
        onValueChange = { input ->
            if (input.length <= 2 && input.all(Char::isDigit)) onValueChange(input)
        },
        label = label,
        useLabelAsPlaceholder = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        singleLine = true,
        insideMargin = DpSize(width = 14.dp, height = 12.dp),
        cornerRadius = 14.dp,
        modifier = modifier.heightIn(min = 52.dp),
    )
}

private val ServerValues = listOf("jp", "intl", "cn")
private val ProfileActiveColor = Color(0xFF35A854)
private val ServerJapanColor = Color(0xFFD9535B)
private val ServerInternationalColor = Color(0xFF4B84D9)
private val ServerChinaColor = Color(0xFFE98535)
