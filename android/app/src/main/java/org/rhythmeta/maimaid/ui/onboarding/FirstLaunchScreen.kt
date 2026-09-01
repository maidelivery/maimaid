package org.rhythmeta.maimaid.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.CatalogSyncStatus
import org.rhythmeta.maimaid.ui.InitialCatalogState
import org.rhythmeta.maimaid.ui.components.CatalogDownloadProgressContent
import org.rhythmeta.maimaid.ui.components.catalogSyncStageText
import org.rhythmeta.maimaid.ui.components.squircleShape
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CircularProgressIndicator
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun InitialCatalogGate(
    state: InitialCatalogState,
    syncStatus: CatalogSyncStatus,
    onStartDownload: () -> Unit,
) {
    when (state) {
        InitialCatalogState.Determining -> Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MiuixTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator()
        }
        InitialCatalogState.Required,
        InitialCatalogState.Synchronizing,
        -> FirstLaunchScreen(
            syncStatus = syncStatus,
            isSyncRequested = state == InitialCatalogState.Synchronizing,
            onStartDownload = onStartDownload,
        )
        InitialCatalogState.Ready -> Unit
    }
}

@Composable
fun FirstLaunchScreen(
    syncStatus: CatalogSyncStatus,
    isSyncRequested: Boolean,
    onStartDownload: () -> Unit,
) {
    BackHandler { }
    var showHero by remember { mutableStateOf(false) }
    var showFeatures by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        showHero = true
        delay(110.milliseconds)
        showFeatures = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MiuixTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, top = 48.dp, end = 16.dp, bottom = 196.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item {
                AnimatedVisibility(
                    visible = showHero,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                ) {
                    OnboardingHero()
                }
            }
            item {
                AnimatedVisibility(
                    visible = showFeatures,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
                ) {
                    OnboardingFeatureList()
                }
            }
        }

        OnboardingBottomAction(
            syncStatus = syncStatus,
            isSyncRequested = isSyncRequested,
            onStartDownload = onStartDownload,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun OnboardingHero() {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_title_prefix),
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Black,
                )
                Text(
                    text = stringResource(R.string.app_name),
                    style = MiuixTheme.textStyles.headline1,
                    fontWeight = FontWeight.Black,
                    color = MiuixTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.width(12.dp))
            val iconShape = squircleShape(22.dp)
            Box(
                modifier = Modifier
                    .size(88.dp)
                    .clip(iconShape)
                    .background(Color(0xFFF5F7F8))
                    .border(1.dp, MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f), iconShape),
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_launcher_foreground_art),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MiuixTheme.textStyles.title3,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
        )
    }
}

@Composable
private fun OnboardingFeatureList() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        insideMargin = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Column {
            OnboardingFeatureRow(
                icon = Icons.Rounded.Bolt,
                title = stringResource(R.string.onboarding_feature_fast_scan),
                detail = stringResource(R.string.onboarding_feature_fast_scan_detail),
            )
            OnboardingDivider()
            OnboardingFeatureRow(
                icon = Icons.Rounded.Lock,
                title = stringResource(R.string.onboarding_feature_local_offline),
                detail = stringResource(R.string.onboarding_feature_local_offline_detail),
            )
            OnboardingDivider()
            OnboardingFeatureRow(
                icon = Icons.Rounded.People,
                title = stringResource(R.string.onboarding_feature_multi_user),
                detail = stringResource(R.string.onboarding_feature_multi_user_detail),
            )
        }
    }
}

@Composable
private fun OnboardingFeatureRow(
    icon: ImageVector,
    title: String,
    detail: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(squircleShape(9.dp))
                .background(MiuixTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MiuixTheme.colorScheme.onPrimary,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                text = title,
                style = MiuixTheme.textStyles.title4,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = detail,
                style = MiuixTheme.textStyles.body2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun OnboardingDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 42.dp)
            .height(1.dp)
            .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
    )
}

@Composable
private fun OnboardingBottomAction(
    syncStatus: CatalogSyncStatus,
    isSyncRequested: Boolean,
    onStartDownload: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isWorking = isSyncRequested ||
        syncStatus == CatalogSyncStatus.Checking ||
        syncStatus is CatalogSyncStatus.Downloading
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MiuixTheme.colorScheme.background)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (syncStatus is CatalogSyncStatus.Downloading) {
            CatalogDownloadProgressContent(progress = syncStatus.progress)
        }
        Button(
            onClick = onStartDownload,
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            enabled = !isWorking,
            colors = ButtonDefaults.buttonColorsPrimary(),
        ) {
            if (isWorking && syncStatus !is CatalogSyncStatus.Downloading) {
                CircularProgressIndicator(size = 20.dp, strokeWidth = 3.dp)
                Spacer(Modifier.width(8.dp))
            }
            Text(
                text = when (syncStatus) {
                    CatalogSyncStatus.Idle -> stringResource(R.string.onboarding_download_start)
                    CatalogSyncStatus.Checking -> stringResource(R.string.catalog_sync_checking)
                    is CatalogSyncStatus.Downloading -> catalogSyncStageText(syncStatus.progress.stage)
                    is CatalogSyncStatus.Failed -> stringResource(R.string.onboarding_download_retry)
                    is CatalogSyncStatus.Ready -> stringResource(R.string.onboarding_download_start)
                },
                style = MiuixTheme.textStyles.button,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
