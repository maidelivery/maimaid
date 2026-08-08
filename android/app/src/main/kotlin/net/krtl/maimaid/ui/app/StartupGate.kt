package net.krtl.maimaid.ui.app

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.krtl.maimaid.R
import net.krtl.maimaid.core.di.VersionAccessDecision
import net.krtl.maimaid.domain.model.StaticSyncStatus
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale

private const val VERSION_CHECK_MAX_AGE_MS = 7L * 24L * 60L * 60L * 1000L

private sealed interface StartupUiState {
    data object CheckingVersion : StartupUiState
    data class AwaitingInitialSync(
        val versionDescription: String? = null,
        val checkErrorMessage: String? = null
    ) : StartupUiState

    data object Syncing : StartupUiState
    data class SyncFailed(
        val message: String,
        val retryAction: RetryAction
    ) : StartupUiState

    data class VersionCheckNetworkIssue(
        val message: String,
        val allowContinue: Boolean
    ) : StartupUiState

    data class Revoked(val versionDescription: String? = null) : StartupUiState
    data object EnteringApp : StartupUiState
    data object Ready : StartupUiState
    data object Warning : StartupUiState
}

private enum class RetryAction {
    ENTER_APP,
    SYNC_AND_ENTER
}

private data class VersionCheckAttempt(
    val decision: VersionAccessDecision?,
    val error: Throwable?
)

@Composable
fun StartupGate(
    container: AppContainer,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val scope = rememberCoroutineScope()
    val syncStatus by container.syncStaticDataUseCase.status.collectAsStateWithLifecycle(
        initialValue = StaticSyncStatus()
    )
    var state by remember { mutableStateOf<StartupUiState>(StartupUiState.EnteringApp) }
    var checkTrigger by remember { mutableIntStateOf(0) }

    var approved by remember { mutableStateOf(false) }


    fun enter() {
        if (state is StartupUiState.Revoked) {
            approved = false
            return
        }
        state = StartupUiState.Ready
        approved = true
    }


    fun enterApp() {
        scope.launch {
            state = StartupUiState.EnteringApp
            runCatching { container.profileRepository.ensureActiveProfile() }
                .onSuccess { enter() }
                .onFailure { error ->
                    if (state !is StartupUiState.Revoked) {
                        state = StartupUiState.SyncFailed(
                            message = error.message ?: "Unknown error",
                            retryAction = RetryAction.ENTER_APP
                        )
                    }
                }
        }
    }

    fun syncThenEnter() {
        scope.launch {
            state = StartupUiState.Syncing
            runCatching {
                val syncOptions = container.preferencesRepository.preferences.first().syncOptions
                container.syncStaticDataUseCase(syncOptions)
                container.profileRepository.ensureActiveProfile()
            }.onSuccess {
                enter()
            }.onFailure { error ->
                if (state !is StartupUiState.Revoked) {
                    state = StartupUiState.SyncFailed(
                        message = error.message ?: "Unknown error",
                        retryAction = RetryAction.SYNC_AND_ENTER
                    )
                }
            }
        }
    }

    suspend fun runVersionCheck(appType: String, versionCode: Int): VersionCheckAttempt {
        val result = runCatching {
            container.checkVersionAccess(
                type = appType,
                versionCode = versionCode
            )
        }
        val decision = result.getOrNull()
        if (decision != null) {
            runCatching {
                container.preferencesRepository.setLastVersionCheckSuccessAt(System.currentTimeMillis())
            }
        }
        return VersionCheckAttempt(
            decision = decision,
            error = result.exceptionOrNull()
        )
    }

    suspend fun runBackgroundVersionCheck(appType: String, versionCode: Int) {
        val attempt = runVersionCheck(appType, versionCode)
        val decision = attempt.decision ?: return
        if (decision.revoked) {
            approved = false
            state = StartupUiState.Revoked(decision.versionDescription)
        }
    }

    suspend fun checkIfInGraceWindow(): Boolean {
        val now = System.currentTimeMillis()
        val lastSuccessAt =
            runCatching { container.preferencesRepository.getLastVersionCheckSuccessAt() }.getOrNull()
        if (lastSuccessAt == null) {
            return false
        }
        val timeElapsed = (now - lastSuccessAt)
        return timeElapsed < VERSION_CHECK_MAX_AGE_MS
    }

    LaunchedEffect(checkTrigger) {
        if (approved) {
            enterApp()
            return@LaunchedEffect
        }

        val appType = context.resolveAppType()
        val versionCode = context.resolveVersionCode()

        val initialSyncPerformed = runCatching {
            val appPreferences = container.preferencesRepository.preferences.first()
            val hasLocalSongs = container.staticDataRepository.observeSongs().first().isNotEmpty()
            appPreferences.didPerformInitialSync && hasLocalSongs
        }.getOrDefault(false)

        if (initialSyncPerformed && checkIfInGraceWindow()) {
            enterApp()
            launch {
                runBackgroundVersionCheck(
                    appType = appType,
                    versionCode = versionCode
                )
            }
            return@LaunchedEffect
        }

        state = StartupUiState.CheckingVersion
        val attempt = runVersionCheck(appType, versionCode)
        val decision = attempt.decision
        val decisionError = attempt.error

        if (decision?.revoked == true) {
            state = StartupUiState.Revoked(decision.versionDescription)
            return@LaunchedEffect
        }

        if (decision == null) {
            val networkMessage = buildVersionCheckNetworkMessage(
                context = context,
                throwable = decisionError
            )
            state = StartupUiState.VersionCheckNetworkIssue(
                message = networkMessage,
                allowContinue = checkIfInGraceWindow()
            )
            return@LaunchedEffect
        }

        if (!initialSyncPerformed) {
            state = StartupUiState.AwaitingInitialSync(
                versionDescription = decision.versionDescription,
                checkErrorMessage = decisionError?.let {
                    buildVersionCheckNetworkMessage(
                        context = context,
                        throwable = it
                    )
                }
            )
            return@LaunchedEffect
        }

        if (context.resolveAppType() == "release") {
            enterApp()
        } else {
            state = StartupUiState.Warning
        }
    }

    when (val current = state) {
        StartupUiState.Warning -> StartupScreenWrapper {
            Text(
                text = stringResource(R.string.snapshot_warning_title),
                style = MaterialTheme.typography.headlineSmall,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.snapshot_warning_description),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = { enterApp() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.startup_continue_anyway))
            }

        }

        StartupUiState.Ready -> content()
        StartupUiState.CheckingVersion -> StartupMessageScreen(
            title = stringResource(R.string.startup_checking_version),
            body = null
        )

        is StartupUiState.AwaitingInitialSync -> StartupSyncPromptScreen(
            versionDescription = current.versionDescription,
            checkErrorMessage = current.checkErrorMessage,
            onSyncNow = { syncThenEnter() }
        )

        StartupUiState.Syncing -> StartupMessageScreen(
            title = stringResource(R.string.startup_syncing_title),
            body = syncStatus.message.ifBlank { stringResource(R.string.startup_syncing_default) },
            progressText = buildSyncProgressText(syncStatus),
            progress = syncStatus.progress.toFloat().coerceIn(0f, 1f)
        )

        is StartupUiState.SyncFailed -> StartupSyncFailedScreen(
            message = current.message,
            buttonText = stringResource(
                if (current.retryAction == RetryAction.SYNC_AND_ENTER) {
                    R.string.startup_retry_sync
                } else {
                    R.string.startup_retry_enter
                }
            ),
            onRetry = {
                if (current.retryAction == RetryAction.SYNC_AND_ENTER) {
                    syncThenEnter()
                } else {
                    enterApp()
                }
            }
        )

        is StartupUiState.VersionCheckNetworkIssue -> StartupVersionCheckNetworkIssueScreen(
            message = current.message,
            allowContinue = current.allowContinue,
            onRetry = { checkTrigger += 1 },
            onContinue = { enterApp() }
        )

        is StartupUiState.Revoked -> StartupRevokedScreen(
            versionDescription = current.versionDescription,
            onExit = { activity?.finishAffinity() }
        )

        StartupUiState.EnteringApp -> StartupMessageScreen(
            title = stringResource(R.string.startup_entering_app),
            body = null
        )
    }
}

@Composable
fun StartupScreenWrapper(
    content: @Composable () -> Unit
) {

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            content()
        }
    }
}

fun Context.resolveAppType(): String = when {
    packageName.endsWith(".snapshot", ignoreCase = true) -> "snapshot"
    packageName.endsWith(".debug", ignoreCase = true) -> "debug"
    else -> "release"
}

fun Context.resolveVersionCode(): Int = runCatching {
    packageManager.getPackageInfo(packageName, 0).longVersionCode.toInt()
}.getOrDefault(1)

private fun buildVersionCheckNetworkMessage(
    context: Context,
    throwable: Throwable?
): String {
    val prefix = when (throwable) {
        is HttpException -> context.getString(R.string.startup_network_error_http, throwable.code())
        is IOException -> context.getString(R.string.startup_network_error_offline)
        null -> context.getString(R.string.startup_network_error_generic)
        else -> context.getString(
            R.string.startup_network_error_detail,
            throwable.message ?: throwable::class.java.simpleName
        )
    }
    return context.getString(R.string.startup_network_error_expired_72h, prefix)

}

@Composable
private fun StartupMessageScreen(
    title: String,
    body: String?,
    progressText: String? = null,
    progress: Float? = null
) {
    StartupScreenWrapper {
        if (progress != null) {
            CircularProgressIndicator(progress = { progress })
        } else {
            CircularProgressIndicator()
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        if (!body.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!progressText.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = progressText,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

    }
}

private fun buildSyncProgressText(status: StaticSyncStatus): String {
    val percent = (status.progress * 100).toInt().coerceIn(0, 100)
    val speed = status.downloadSpeedBytesPerSecond
    val speedText = if (speed > 0.0) {
        "${formatBytes(speed)}/s"
    } else {
        null
    }
    return if (!speedText.isNullOrBlank()) {
        "$percent% · $speedText"
    } else {
        "$percent%"
    }
}

private fun formatBytes(bytes: Double): String {
    if (bytes <= 0.0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var value = bytes
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    val formatted = if (value >= 100) {
        value.toInt().toString()
    } else {
        String.format(Locale.ROOT, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}

@Composable
private fun StartupSyncPromptScreen(
    versionDescription: String?,
    checkErrorMessage: String?,
    onSyncNow: () -> Unit
) {
    StartupScreenWrapper {
        Text(
            text = stringResource(R.string.startup_sync_prompt_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.startup_sync_prompt_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!versionDescription.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.startup_version_description, versionDescription),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        if (!checkErrorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = stringResource(R.string.startup_version_check_failed, checkErrorMessage),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onSyncNow,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.startup_sync_now))
        }
    }

}

@Composable
private fun StartupSyncFailedScreen(
    message: String,
    buttonText: String,
    onRetry: () -> Unit
) {
    StartupScreenWrapper {
        Text(
            text = stringResource(R.string.startup_sync_failed_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.startup_sync_failed_message, message),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonText)
        }
    }

}

@Composable
private fun StartupVersionCheckNetworkIssueScreen(
    message: String,
    allowContinue: Boolean,
    onRetry: () -> Unit,
    onContinue: () -> Unit
) {
    StartupScreenWrapper {
        Text(
            text = stringResource(R.string.startup_network_error_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.startup_retry_check))
        }
        if (allowContinue) {
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = onContinue,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.startup_continue_anyway))
            }
        }

    }
}

@Composable
private fun StartupRevokedScreen(
    versionDescription: String?,
    onExit: () -> Unit
) {
    StartupScreenWrapper {
        Text(
            text = stringResource(R.string.startup_revoked_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.startup_revoked_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (!versionDescription.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.startup_revoked_description, versionDescription),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Button(
            onClick = onExit,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.startup_exit_app))
        }

    }
}
