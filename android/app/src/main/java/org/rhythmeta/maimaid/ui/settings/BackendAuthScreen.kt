package org.rhythmeta.maimaid.ui.settings

import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.AddCircleOutline
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Login
import androidx.compose.material.icons.rounded.Logout
import androidx.compose.material.icons.rounded.Merge
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.BackendAccountConflict
import org.rhythmeta.maimaid.core.data.BackendAccountResolution
import org.rhythmeta.maimaid.core.data.BackendAuthUser
import org.rhythmeta.maimaid.core.data.BackendCloudRestorePreview
import org.rhythmeta.maimaid.core.data.BackendProfileConflictException
import org.rhythmeta.maimaid.core.data.BackendSessionNotice
import org.rhythmeta.maimaid.core.data.BackendWebAuthMode
import org.rhythmeta.maimaid.ui.common.openInAppBrowser
import top.yukonga.miuix.kmp.basic.BasicComponent
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.LinearProgressIndicator
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.SnackbarDuration
import top.yukonga.miuix.kmp.basic.SnackbarHost
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

private enum class CloudOperation {
    Backup,
    Restore,
    Resolve,
    Logout,
}

@Composable
fun BackendAuthScreen(container: AppContainer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sessionState by container.backendSessionManager.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var operation by remember { mutableStateOf<CloudOperation?>(null) }
    var accountConflict by remember { mutableStateOf<BackendAccountConflict?>(null) }
    var profileConflict by remember { mutableStateOf<BackendProfileConflictException?>(null) }
    var restorePreview by remember { mutableStateOf<BackendCloudRestorePreview?>(null) }
    var showLogoutOptions by remember { mutableStateOf(false) }

    fun showMessage(message: String) {
        scope.launch {
            snackbar.showSnackbar(message, duration = SnackbarDuration.Custom(3_000))
        }
    }

    val restoreSucceeded = stringResource(R.string.cloud_message_restore_success)

    fun performRestore(removeLocalProfilesAbsentFromCloud: Boolean) {
        scope.launch {
            operation = CloudOperation.Restore
            runCatching {
                container.backendSyncCoordinator.restore(removeLocalProfilesAbsentFromCloud)
            }
                .onSuccess { showMessage(restoreSucceeded) }
                .onFailure { error ->
                    if (error is BackendProfileConflictException) profileConflict = error
                    else showMessage(error.localizedMessage ?: error.javaClass.simpleName)
                }
            operation = null
        }
    }

    fun prepareRestore() {
        scope.launch {
            operation = CloudOperation.Restore
            runCatching { container.backendSyncCoordinator.previewRestore() }
                .onSuccess { preview ->
                    operation = null
                    if (preview.localOnlyProfiles.isEmpty()) {
                        performRestore(removeLocalProfilesAbsentFromCloud = false)
                    } else {
                        restorePreview = preview
                    }
                }
                .onFailure { error ->
                    operation = null
                    showMessage(error.localizedMessage ?: error.javaClass.simpleName)
                }
        }
    }

    suspend fun refreshConflict() {
        val user = container.backendSessionManager.state.value.user
        accountConflict = user?.let { container.backendSyncCoordinator.accountConflict(it.id) }
    }

    LaunchedEffect(Unit) {
        container.backendSessionManager.checkSession()
        refreshConflict()
    }
    LaunchedEffect(sessionState.user?.id) {
        refreshConflict()
    }
    val loginSucceeded = stringResource(R.string.cloud_message_login_success)
    val authLinkSucceeded = stringResource(R.string.cloud_message_auth_link_success)
    val authLinkFailed = stringResource(R.string.cloud_message_auth_link_failed)
    LaunchedEffect(sessionState.notice) {
        val message = when (sessionState.notice) {
            BackendSessionNotice.LoginSucceeded -> loginSucceeded
            BackendSessionNotice.AuthLinkSucceeded -> authLinkSucceeded
            BackendSessionNotice.AuthLinkFailed -> authLinkFailed
            null -> null
        }
        message?.let(::showMessage)
        if (sessionState.notice != null) container.backendSessionManager.consumeNotice()
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                AccountSummaryCard(user = sessionState.user)
            }
            if (sessionState.user == null) {
                item {
                    CloudSection(stringResource(R.string.cloud_sign_in_section)) {
                        CloudActionRow(
                            icon = Icons.Rounded.Login,
                            title = stringResource(R.string.cloud_login),
                            enabled = operation == null,
                            onClick = { openWebAuth(context, container, BackendWebAuthMode.Login, ::showMessage) },
                        )
                        CloudActionRow(
                            icon = Icons.Rounded.AddCircleOutline,
                            title = stringResource(R.string.cloud_register),
                            enabled = operation == null,
                            onClick = { openWebAuth(context, container, BackendWebAuthMode.Register, ::showMessage) },
                        )
                        CloudActionRow(
                            icon = Icons.Rounded.Key,
                            title = stringResource(R.string.cloud_forgot_password),
                            enabled = operation == null,
                            onClick = { openWebAuth(context, container, BackendWebAuthMode.Forgot, ::showMessage) },
                        )
                    }
                }
            } else {
                item {
                    CloudSection(stringResource(R.string.cloud_account_section)) {
                        CloudValueRow(stringResource(R.string.cloud_handle), sessionState.user?.displayHandle.orEmpty())
                        CloudValueRow(stringResource(R.string.cloud_email), sessionState.user?.email.orEmpty())
                        CloudValueRow(
                            stringResource(R.string.cloud_status),
                            stringResource(R.string.cloud_logged_in),
                        )
                    }
                }
                item {
                    val backupSucceeded = stringResource(R.string.cloud_message_backup_success)
                    CloudSection(stringResource(R.string.cloud_sync_section)) {
                        CloudActionRow(
                            icon = Icons.Rounded.CloudUpload,
                            title = stringResource(R.string.cloud_backup),
                            enabled = operation == null && accountConflict == null,
                            onClick = {
                                scope.launch {
                                    operation = CloudOperation.Backup
                                    runCatching { container.backendSyncCoordinator.backup() }
                                        .onSuccess { showMessage(backupSucceeded) }
                                        .onFailure { error ->
                                            if (error is BackendProfileConflictException) profileConflict = error
                                            else showMessage(error.localizedMessage ?: error.javaClass.simpleName)
                                        }
                                    operation = null
                                }
                            },
                        )
                        CloudActionRow(
                            icon = Icons.Rounded.CloudDownload,
                            title = stringResource(R.string.cloud_restore),
                            enabled = operation == null && accountConflict == null,
                            onClick = ::prepareRestore,
                        )
                        if (operation == CloudOperation.Backup || operation == CloudOperation.Restore) {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                Text(
                                    text = stringResource(R.string.cloud_syncing),
                                    style = MiuixTheme.textStyles.body2,
                                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                )
                            }
                        }
                    }
                }
                item {
                    Button(
                        onClick = { showLogoutOptions = true },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = operation == null,
                        colors = ButtonDefaults.buttonColors(
                            color = MiuixTheme.colorScheme.errorContainer,
                            contentColor = MiuixTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Logout, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.cloud_logout))
                    }
                }
            }
        }

        SnackbarHost(
            state = snackbar,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }

    accountConflict?.let { conflict ->
        AccountConflictDialog(
            show = true,
            conflict = conflict,
            isApplying = operation == CloudOperation.Resolve,
            onSelect = { resolution ->
                scope.launch {
                    operation = CloudOperation.Resolve
                    runCatching { container.backendSyncCoordinator.resolveAccountConflict(resolution) }
                        .onSuccess {
                            accountConflict = null
                            showMessage(context.getString(R.string.cloud_resolution_success))
                        }
                        .onFailure { showMessage(it.localizedMessage ?: it.javaClass.simpleName) }
                    operation = null
                }
            },
        )
    }
    profileConflict?.let { conflict ->
        ProfileConflictDialog(
            show = true,
            count = conflict.profileIds.size,
            isApplying = operation == CloudOperation.Resolve,
            onDismiss = { profileConflict = null },
            onSelect = { resolution ->
                scope.launch {
                    operation = CloudOperation.Resolve
                    runCatching { container.backendSyncCoordinator.resolveProfileConflict(resolution) }
                        .onSuccess {
                            profileConflict = null
                            showMessage(context.getString(R.string.cloud_resolution_success))
                        }
                        .onFailure { showMessage(it.localizedMessage ?: it.javaClass.simpleName) }
                    operation = null
                }
            },
        )
    }
    restorePreview?.let { preview ->
        RestoreLocalProfilesDialog(
            show = true,
            profileCount = preview.localOnlyProfiles.size,
            isApplying = operation == CloudOperation.Restore,
            onDismiss = { restorePreview = null },
            onKeep = {
                restorePreview = null
                performRestore(removeLocalProfilesAbsentFromCloud = false)
            },
            onRemove = {
                restorePreview = null
                performRestore(removeLocalProfilesAbsentFromCloud = true)
            },
        )
    }
    LogoutDialog(
        show = showLogoutOptions,
        isApplying = operation == CloudOperation.Logout,
        onDismiss = { showLogoutOptions = false },
        onSelect = { clearLocal ->
                scope.launch {
                    operation = CloudOperation.Logout
                    container.backendSyncCoordinator.onLogout(clearLocal)
                    container.backendSessionManager.logout()
                showLogoutOptions = false
                operation = null
                if (clearLocal) showMessage(context.getString(R.string.cloud_local_data_cleared))
            }
        },
    )
}

@Composable
private fun AccountSummaryCard(user: BackendAuthUser?) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(20.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (user == null) Icons.Rounded.AccountCircle else Icons.Rounded.Person,
                contentDescription = null,
                modifier = Modifier.size(64.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = user?.displayHandle ?: stringResource(R.string.cloud_login_title),
                    style = MiuixTheme.textStyles.title3,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = user?.email ?: stringResource(R.string.cloud_login_subtitle),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.Lock,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.cloud_privacy_hint),
                style = MiuixTheme.textStyles.footnote1,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun CloudSection(title: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column {
        SmallTitle(text = title, insideMargin = PaddingValues(horizontal = 4.dp, vertical = 8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            insideMargin = PaddingValues(0.dp),
            cornerRadius = 16.dp,
            content = content,
        )
    }
}

@Composable
private fun CloudActionRow(icon: ImageVector, title: String, enabled: Boolean, onClick: () -> Unit) {
    BasicComponent(
        title = title,
        enabled = enabled,
        onClick = onClick,
        startAction = { MonochromeIcon(icon) },
        endActions = {
            Icon(
                imageVector = Icons.Rounded.Sync,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.55f),
            )
        },
    )
}

@Composable
private fun CloudValueRow(title: String, value: String) {
    BasicComponent(
        title = title,
        endActions = {
            Text(
                text = value,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantActions,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun MonochromeIcon(icon: ImageVector) {
    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = Modifier
            .padding(end = 8.dp)
            .size(24.dp),
        tint = MiuixTheme.colorScheme.onSurfaceVariantActions,
    )
}

@Composable
private fun AccountConflictDialog(
    show: Boolean,
    conflict: BackendAccountConflict,
    isApplying: Boolean,
    onSelect: (BackendAccountResolution) -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.cloud_resolution_title),
        summary = stringResource(R.string.cloud_resolution_account_message),
        onDismissRequest = null,
        outsideMargin = DpSize(24.dp, 24.dp),
    ) {
        ConflictIdentityRow(stringResource(R.string.cloud_resolution_current), conflict.currentUserId)
        ConflictIdentityRow(stringResource(R.string.cloud_resolution_owner), conflict.ownerUserId)
        ConflictButtons(isApplying = isApplying, onSelect = onSelect)
    }
}

@Composable
private fun ProfileConflictDialog(
    show: Boolean,
    count: Int,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onSelect: (BackendAccountResolution) -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.cloud_profile_conflict_title),
        summary = stringResource(R.string.cloud_profile_conflict_message, count),
        onDismissRequest = onDismiss,
        outsideMargin = DpSize(24.dp, 24.dp),
    ) {
        ConflictButtons(isApplying = isApplying, onSelect = onSelect)
    }
}

@Composable
private fun RestoreLocalProfilesDialog(
    show: Boolean,
    profileCount: Int,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onKeep: () -> Unit,
    onRemove: () -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.cloud_restore_local_profiles_title),
        summary = stringResource(R.string.cloud_restore_local_profiles_summary, profileCount),
        onDismissRequest = onDismiss,
        outsideMargin = DpSize(24.dp, 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = onKeep,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isApplying,
            ) {
                Text(stringResource(R.string.cloud_restore_keep_local_profiles))
            }
            Button(
                onClick = onRemove,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isApplying,
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.errorContainer,
                    contentColor = MiuixTheme.colorScheme.onErrorContainer,
                ),
            ) {
                Text(stringResource(R.string.cloud_restore_remove_local_profiles))
            }
            if (isApplying) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ConflictIdentityRow(title: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MiuixTheme.textStyles.body2, color = MiuixTheme.colorScheme.onSurfaceVariantSummary)
        Spacer(Modifier.weight(1f))
        Text(value, style = MiuixTheme.textStyles.footnote1, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ConflictButtons(isApplying: Boolean, onSelect: (BackendAccountResolution) -> Unit) {
    val choices = listOf(
        Triple(BackendAccountResolution.Merge, Icons.Rounded.Merge, stringResource(R.string.cloud_resolution_merge)),
        Triple(BackendAccountResolution.KeepLocal, Icons.Rounded.CloudUpload, stringResource(R.string.cloud_resolution_keep_local)),
        Triple(BackendAccountResolution.UseCloud, Icons.Rounded.CloudDownload, stringResource(R.string.cloud_resolution_use_cloud)),
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 12.dp)) {
        choices.forEach { (resolution, icon, title) ->
            Button(
                onClick = { onSelect(resolution) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isApplying,
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(title)
            }
        }
        if (isApplying) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

@Composable
private fun LogoutDialog(
    show: Boolean,
    isApplying: Boolean,
    onDismiss: () -> Unit,
    onSelect: (Boolean) -> Unit,
) {
    WindowDialog(
        show = show,
        title = stringResource(R.string.cloud_logout_options_title),
        summary = stringResource(R.string.cloud_logout_options_message),
        onDismissRequest = onDismiss,
        outsideMargin = DpSize(24.dp, 24.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = { onSelect(false) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isApplying,
            ) { Text(stringResource(R.string.cloud_logout_keep_local)) }
            Button(
                onClick = { onSelect(true) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isApplying,
                colors = ButtonDefaults.buttonColors(
                    color = MiuixTheme.colorScheme.errorContainer,
                    contentColor = MiuixTheme.colorScheme.onErrorContainer,
                ),
            ) { Text(stringResource(R.string.cloud_logout_clear_local)) }
        }
    }
}

private fun openWebAuth(
    context: Context,
    container: AppContainer,
    mode: BackendWebAuthMode,
    onError: (String) -> Unit,
) {
    val url = container.backendSessionManager.webAuthUrl(mode)
    if (url == null) {
        onError(context.getString(R.string.cloud_unconfigured))
        return
    }
    if (!context.openInAppBrowser(url)) {
        onError(context.getString(R.string.cloud_browser_unavailable))
    }
}
