package net.krtl.maimaid.feature.imports

import android.widget.Toast
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import net.krtl.maimaid.R
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SecondaryLargeTitleScaffold

@Composable
fun DataImportScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val vm: DataImportViewModel = viewModel(
        factory = DataImportViewModelFactory(container)
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(vm) {
        vm.events.collect { event ->
            when (event) {
                is DataImportEvent.OpenUrl -> {
                    CustomTabsIntent.Builder()
                        .build()
                        .launchUrl(context, event.url.toUri())
                }

                is DataImportEvent.Toast -> {
                    Toast.makeText(context, event.message, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    SecondaryLargeTitleScaffold(
        title = stringResource(R.string.data_import_title),
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
            val activeProfileMessage = if (state.activeProfileId.isNullOrBlank()) {
                stringResource(R.string.data_import_no_active_profile)
            } else {
                stringResource(R.string.data_import_active_profile, state.activeProfileId.orEmpty())
            }
            Text(
                text = activeProfileMessage,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.data_import_df_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.data_import_df_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = state.username,
                        onValueChange = { vm.onIntent(DataImportIntent.UpdateUsername(it)) },
                        label = { Text(stringResource(R.string.data_import_df_username)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.qq,
                        onValueChange = { vm.onIntent(DataImportIntent.UpdateQq(it)) },
                        label = { Text(stringResource(R.string.data_import_df_qq)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { vm.onIntent(DataImportIntent.ImportDivingFish) },
                        enabled = !state.isImportingDf && !state.activeProfileId.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.isImportingDf) {
                                stringResource(R.string.data_import_importing)
                            } else {
                                stringResource(R.string.data_import_df_action)
                            }
                        )
                    }
                }
            }

            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(R.string.data_import_lxns_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = stringResource(R.string.data_import_lxns_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { vm.onIntent(DataImportIntent.OpenLxnsAuthPage) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.data_import_lxns_open_auth))
                    }
                    OutlinedButton(
                        onClick = { vm.onIntent(DataImportIntent.RegenerateLxnsCodeVerifier) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.data_import_lxns_regenerate_verifier))
                    }
                    OutlinedTextField(
                        value = state.lxnsCodeVerifier,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.data_import_lxns_code_verifier)) },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = state.lxnsCode,
                        onValueChange = { vm.onIntent(DataImportIntent.UpdateLxnsCode(it)) },
                        label = { Text(stringResource(R.string.data_import_lxns_code)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { vm.onIntent(DataImportIntent.ImportLxns) },
                        enabled = !state.isImportingLxns && !state.activeProfileId.isNullOrBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            if (state.isImportingLxns) {
                                stringResource(R.string.data_import_importing)
                            } else {
                                stringResource(R.string.data_import_lxns_action)
                            }
                        )
                    }
                }
            }

            state.statusMessage?.let { status ->
                Text(
                    text = status,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (status.contains("failed", ignoreCase = true)) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

private class DataImportViewModelFactory(
    private val container: AppContainer
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return DataImportViewModel(
            profileRepository = container.profileRepository,
            importRepository = container.importRepository,
            syncRepository = container.syncRepository
        ) as T
    }
}

