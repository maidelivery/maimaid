package net.krtl.maimaid.feature.cloud

import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.krtl.maimaid.R
import net.krtl.maimaid.core.domain.SessionState
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SecondaryLargeTitleScaffold
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun CloudAuthScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val vm: CloudAuthViewModel = viewModel(
        factory = CloudAuthViewModelFactory(container)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is CloudAuthEvent.OpenUrl -> {
                    CustomTabsIntent.Builder().build().launchUrl(context, event.url.toUri())
                }
                is CloudAuthEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SecondaryLargeTitleScaffold(
        title = stringResource(R.string.cloud_auth_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(contentPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SessionCard(state = state)

            when (state.sessionState) {
                SessionState.Unknown,
                SessionState.LoggedOut,
                is SessionState.Recovery -> LoggedOutActions(
                    state = state,
                    onIntent = vm::onIntent
                )
                is SessionState.LoggedIn -> LoggedInActions(
                    state = state,
                    onIntent = vm::onIntent
                )
            }

            state.statusMessage?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (message.contains("failed", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun SessionCard(state: CloudAuthUiState) {
    val sessionText = when (val session = state.sessionState) {
        SessionState.Unknown -> stringResource(R.string.cloud_auth_session_checking)
        SessionState.LoggedOut -> stringResource(R.string.cloud_auth_not_signed_in)
        is SessionState.LoggedIn -> stringResource(R.string.cloud_auth_signed_in_as, session.user.email)
        is SessionState.Recovery -> stringResource(
            R.string.cloud_auth_password_recovery,
            session.email ?: stringResource(R.string.cloud_auth_unknown_email)
        )
    }
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = sessionText,
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = "Profiles ${state.profileCount} · Active ${state.activeProfileName ?: "None"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Active profile local data: ${state.activeScoreCount} scores / ${state.activeRecordCount} records",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Last sync revision: ${state.lastSyncRevision}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Last cloud backup: ${state.lastCloudBackupDate?.let(::formatDateTime) ?: "Never"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (state.isLoading || state.isSyncing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun LoggedOutActions(
    state: CloudAuthUiState,
    onIntent: (CloudAuthIntent) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Sign in to enable backend account sync and cloud backup.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(
                onClick = { onIntent(CloudAuthIntent.Login) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text(stringResource(R.string.cloud_auth_sign_in))
            }
            OutlinedButton(
                onClick = { onIntent(CloudAuthIntent.Register) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text(stringResource(R.string.cloud_auth_create_account))
            }
            OutlinedButton(
                onClick = { onIntent(CloudAuthIntent.ForgotPassword) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text(stringResource(R.string.cloud_auth_forgot_password))
            }
            OutlinedButton(
                onClick = { onIntent(CloudAuthIntent.Refresh) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading
            ) {
                Text(stringResource(R.string.cloud_auth_refresh))
            }
        }
    }
}

@Composable
private fun LoggedInActions(
    state: CloudAuthUiState,
    onIntent: (CloudAuthIntent) -> Unit
) {
    OutlinedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Android currently syncs through full snapshots. Pull downloads the latest cloud state, and push uploads all local profiles, scores and records.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { onIntent(CloudAuthIntent.PullFromCloud) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isLoading && !state.isSyncing
                ) {
                    Text("Pull cloud")
                }
                Button(
                    onClick = { onIntent(CloudAuthIntent.PushToCloud) },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isLoading && !state.isSyncing
                ) {
                    Text("Push local")
                }
            }
            OutlinedButton(
                onClick = { onIntent(CloudAuthIntent.Refresh) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && !state.isSyncing
            ) {
                Text(stringResource(R.string.cloud_auth_refresh))
            }
            OutlinedButton(
                onClick = { onIntent(CloudAuthIntent.Logout) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !state.isLoading && !state.isSyncing
            ) {
                Text(stringResource(R.string.cloud_auth_sign_out))
            }
        }
    }
}

private fun formatDateTime(timestampMillis: Long): String {
    return Instant.ofEpochMilli(timestampMillis)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}

private class CloudAuthViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return CloudAuthViewModel(
            authRepository = container.authRepository,
            syncRepository = container.syncRepository,
            profileRepository = container.profileRepository,
            scoreRepository = container.scoreRepository,
            staticDataRepository = container.staticDataRepository
        ) as T
    }
}
