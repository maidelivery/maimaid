package net.krtl.maimaid.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import net.krtl.maimaid.R
import net.krtl.maimaid.domain.model.GameServer
import net.krtl.maimaid.domain.model.UserProfile
import net.krtl.maimaid.domain.usecase.RatingEngine
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SecondaryLargeTitleScaffold
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilesScreen(container: AppContainer, innerPadding: PaddingValues, onBack: () -> Unit) {
    val profiles by container.profileRepository.observeProfiles().collectAsStateWithLifecycle(initialValue = emptyList())
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    val preferences by container.preferencesRepository.preferences.collectAsStateWithLifecycle(
        initialValue = net.krtl.maimaid.domain.model.AppPreferencesState()
    )
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<UserProfile?>(null) }
    var creating by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<UserProfile?>(null) }
    val sortedProfiles = remember(profiles) { profiles.sortedBy { it.createdAt } }
    val latestVersionByServer = remember(songs, preferences.versionSequence) {
        GameServer.entries.associateWith { server ->
            RatingEngine.latestVersionFor(server, songs, preferences.versionSequence)
        }
    }

    if (creating || editing != null) {
        ProfileEditorScreen(
            initial = editing,
            latestVersionByServer = latestVersionByServer,
            shouldActivateNewProfile = profiles.isEmpty(),
            innerPadding = innerPadding,
            onBack = {
                creating = false
                editing = null
            },
            onSave = { profile ->
                scope.launch {
                    container.profileRepository.saveProfile(profile)
                }
                creating = false
                editing = null
            }
        )
        return
    }

    SecondaryLargeTitleScaffold(
        title = stringResource(R.string.profiles_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        Scaffold(
            floatingActionButton = {
                FloatingActionButton(onClick = { creating = true }) {
                    Icon(Icons.Default.Add, contentDescription = stringResource(R.string.profile_create_content_description))
                }
            }
        ) { scaffoldPadding ->
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding)
                    .padding(bottom = scaffoldPadding.calculateBottomPadding()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (sortedProfiles.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Text(stringResource(R.string.profiles_empty_title), style = MaterialTheme.typography.titleMedium)
                                Text(
                                    stringResource(R.string.profiles_empty_description),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                items(sortedProfiles, key = { it.id }) { profile ->
                    ProfileCard(
                        profile = profile,
                        latestVersion = latestVersionByServer[profile.server],
                        onActivate = {
                            if (!profile.isActive) {
                                scope.launch { container.profileRepository.setActiveProfile(profile.id) }
                            }
                        },
                        onEdit = { editing = profile },
                        onDelete = if (profile.isActive) null else { { pendingDelete = profile } }
                    )
                }
            }
        }
    }

    pendingDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.profiles_delete_title)) },
            text = { Text(stringResource(R.string.profiles_delete_description)) },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch { container.profileRepository.deleteProfile(profile.id) }
                        pendingDelete = null
                    }
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.common_cancel))
                }
            }
        )
    }
}

@Composable
private fun ProfileCard(
    profile: UserProfile,
    latestVersion: String?,
    onActivate: () -> Unit,
    onEdit: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (!profile.isActive) base.clickable(onClick = onActivate) else base }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .background(serverTint(profile.server).copy(alpha = 0.16f), CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = null,
                        tint = serverTint(profile.server),
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxSize()
                    )
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = profile.name.ifBlank { stringResource(R.string.profile_unnamed) },
                            style = MaterialTheme.typography.titleMedium
                        )
                        if (profile.isActive) {
                            ProfileChip(
                                text = stringResource(R.string.profile_active),
                                tint = MaterialTheme.colorScheme.primary,
                                horizontalPadding = 10.dp
                            )
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProfileChip(text = profile.server.displayName, tint = serverTint(profile.server))
                        latestVersion?.let {
                            ProfileChip(
                                text = compactVersionName(it),
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Text(
                        text = if (profile.isActive) {
                            stringResource(R.string.profile_current_rating, profile.playerRating)
                        } else {
                            stringResource(R.string.profile_switch_rating, profile.playerRating)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.profile_delete_content_description))
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.profile_edit_content_description))
                }
            }
        }
    }
}

@Composable
private fun ProfileChip(
    text: String,
    tint: Color,
    horizontalPadding: Dp = 8.dp
) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = CircleShape
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = tint,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = 5.dp)
        )
    }
}

private fun serverTint(server: GameServer): Color = when (server) {
    GameServer.JP -> Color(0xFFE35D6A)
    GameServer.INTL -> Color(0xFF4A90E2)
    GameServer.CN -> Color(0xFFF39C4D)
}

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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileEditorScreen(
    initial: UserProfile?,
    latestVersionByServer: Map<GameServer, String?>,
    shouldActivateNewProfile: Boolean,
    innerPadding: PaddingValues,
    onBack: () -> Unit,
    onSave: (UserProfile) -> Unit
) {
    var name by remember(initial?.id) { mutableStateOf(initial?.name ?: "") }
    var plate by remember(initial?.id) { mutableStateOf(initial?.plate ?: "") }
    var server by remember(initial?.id) { mutableStateOf(initial?.server ?: GameServer.JP) }
    var serverExpanded by remember { mutableStateOf(false) }
    var b35Count by remember(initial?.id) { mutableStateOf((initial?.b35Count ?: 35).toString()) }
    var b15Count by remember(initial?.id) { mutableStateOf((initial?.b15Count ?: 15).toString()) }
    var b35RecLimit by remember(initial?.id) { mutableStateOf((initial?.b35RecLimit ?: 10).toString()) }
    var b15RecLimit by remember(initial?.id) { mutableStateOf((initial?.b15RecLimit ?: 10).toString()) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (initial == null) stringResource(R.string.profile_create_title) else stringResource(R.string.profile_edit_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            val trimmedName = name.trim()
                            val trimmedPlate = plate.trim().ifBlank { null }
                            onSave(
                                initial?.copy(
                                    name = trimmedName,
                                    plate = trimmedPlate,
                                    server = server,
                                    b35Count = b35Count.toPositiveIntOrDefault(35),
                                    b15Count = b15Count.toPositiveIntOrDefault(15),
                                    b35RecLimit = b35RecLimit.toPositiveIntOrDefault(10),
                                    b15RecLimit = b15RecLimit.toPositiveIntOrDefault(10)
                                ) ?: UserProfile(
                                    id = UUID.randomUUID().toString(),
                                    name = trimmedName,
                                    server = server,
                                    avatarUrl = null,
                                    isActive = shouldActivateNewProfile,
                                    createdAt = System.currentTimeMillis(),
                                    playerRating = 0,
                                    plate = trimmedPlate,
                                    b35Count = b35Count.toPositiveIntOrDefault(35),
                                    b15Count = b15Count.toPositiveIntOrDefault(15),
                                    b35RecLimit = b35RecLimit.toPositiveIntOrDefault(10),
                                    b15RecLimit = b15RecLimit.toPositiveIntOrDefault(10)
                                )
                            )
                        },
                        enabled = name.trim().isNotEmpty()
                    ) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            )
        }
    ) { scaffoldPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = 16.dp,
                    top = innerPadding.calculateTopPadding() + scaffoldPadding.calculateTopPadding(),
                    end = 16.dp,
                    bottom = innerPadding.calculateBottomPadding()
                ),
            verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
            item {
                ProfileEditorSection(title = stringResource(R.string.profile_section_basic)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(stringResource(R.string.profile_name)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = plate,
                            onValueChange = { plate = it },
                            label = { Text(stringResource(R.string.profile_plate)) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        ExposedDropdownMenuBox(
                            expanded = serverExpanded,
                            onExpandedChange = { serverExpanded = !serverExpanded }
                        ) {
                            OutlinedTextField(
                                value = server.displayName,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(stringResource(R.string.profile_server)) },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = serverExpanded) },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                                .fillMaxWidth()
                            )
                            DropdownMenu(
                                expanded = serverExpanded,
                                onDismissRequest = { serverExpanded = false }
                            ) {
                                GameServer.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option.displayName) },
                                        onClick = {
                                            server = option
                                            serverExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        latestVersionByServer[server]?.let {
                            Text(
                                text = stringResource(R.string.profile_latest_version, compactVersionName(it)),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            item {
                ProfileEditorSection(title = stringResource(R.string.profile_section_b50)) {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProfileNumberField(
                                value = b35Count,
                                onValueChange = { b35Count = it.digitsOnly() },
                                label = stringResource(R.string.profile_b35_count),
                                modifier = Modifier.weight(1f)
                            )
                            ProfileNumberField(
                                value = b15Count,
                                onValueChange = { b15Count = it.digitsOnly() },
                                label = stringResource(R.string.profile_b15_count),
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            ProfileNumberField(
                                value = b35RecLimit,
                                onValueChange = { b35RecLimit = it.digitsOnly() },
                                label = stringResource(R.string.profile_b35_rec_limit),
                                modifier = Modifier.weight(1f)
                            )
                            ProfileNumberField(
                                value = b15RecLimit,
                                onValueChange = { b15RecLimit = it.digitsOnly() },
                                label = stringResource(R.string.profile_b15_rec_limit),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProfileEditorSection(
    title: String,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary
        )
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = MaterialTheme.shapes.extraLarge
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ProfileNumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier
    )
}

private fun String.digitsOnly(): String = filter(Char::isDigit)

private fun String.toPositiveIntOrDefault(defaultValue: Int): Int = toIntOrNull()?.coerceAtLeast(1) ?: defaultValue
