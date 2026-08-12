package org.rhythmeta.maimaid.ui.best

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import java.io.File
import java.io.FileOutputStream
import java.util.Locale
import kotlinx.coroutines.launch
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.AppContainer
import org.rhythmeta.maimaid.core.data.Best50State
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.data.RatingUtils
import org.rhythmeta.maimaid.core.data.ScoreRules
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.util.ScoreStatusColors
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SmallTitle
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.preference.WindowDropdownPreference
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BestTableScreen(
    container: AppContainer,
    activeProfile: UserProfileEntity?,
    versions: List<GameVersionEntity>,
    exportRequested: Boolean,
    onExportRequestHandled: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    var selectedVersion by remember { mutableStateOf<String?>(null) }
    val best50Flow = remember(selectedVersion) {
        container.best50Repository.observeBest50(selectedVersion)
    }
    val best50 by best50Flow.collectAsStateWithLifecycle(Best50State())
    var b35Text by remember(activeProfile?.id) {
        mutableStateOf((activeProfile?.b35Count ?: 35).toString())
    }
    var b15Text by remember(activeProfile?.id) {
        mutableStateOf((activeProfile?.b15Count ?: 15).toString())
    }
    val versionOptions = remember(versions) {
        listOf<String?>(null) + versions.sortedByDescending(GameVersionEntity::sortOrder).map { it.name }
    }
    val versionLabels = versionOptions.map { version ->
        version?.let { SongVisualUtils.versionAbbreviation(it, versions) }
            ?: stringResource(R.string.best50_version_auto)
    }

    LaunchedEffect(activeProfile?.b35Count, activeProfile?.b15Count) {
        activeProfile?.let {
            b35Text = it.b35Count.toString()
            b15Text = it.b15Count.toString()
        }
    }
    LaunchedEffect(exportRequested, best50) {
        if (exportRequested) {
            if (!best50.isEmpty) shareBest50(context, best50)
            onExportRequestHandled()
        }
    }

    val b35Sum = best50.b35.sumOf(RatingUtils.Entry::rating)
    val b15Sum = best50.b15.sumOf(RatingUtils.Entry::rating)
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BestRatingSummary(
                total = best50.total,
                b35Sum = b35Sum,
                b15Sum = b15Sum,
            )
        }
        item {
            SmallTitle(
                text = stringResource(R.string.best50_version_section),
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            )
            Card(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                insideMargin = PaddingValues(0.dp),
                colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
            ) {
                WindowDropdownPreference(
                    items = versionLabels,
                    selectedIndex = versionOptions.indexOf(selectedVersion).coerceAtLeast(0),
                    title = stringResource(R.string.best50_current_version),
                    summary = selectedVersion?.let { stringResource(R.string.best50_version_overridden) },
                    onSelectedIndexChange = { index -> selectedVersion = versionOptions[index] },
                )
            }
        }
        item {
            SmallTitle(
                text = stringResource(R.string.best50_capacity_section),
                insideMargin = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
            )
            BestCapacityCard(
                b35Text = b35Text,
                b15Text = b15Text,
                onB35Change = { input ->
                    if (input.length <= 2 && input.all(Char::isDigit)) {
                        b35Text = input
                    }
                },
                onB15Change = { input ->
                    if (input.length <= 2 && input.all(Char::isDigit)) {
                        b15Text = input
                    }
                },
                onCommit = {
                    val b35 = b35Text.toIntOrNull()?.coerceAtLeast(1) ?: 35
                    val b15 = b15Text.toIntOrNull()?.coerceAtLeast(1) ?: 15
                    b35Text = b35.toString()
                    b15Text = b15.toString()
                    focusManager.clearFocus()
                    scope.launch { container.profileRepository.updateBestCapacity(b35, b15) }
                },
            )
        }
        item {
            BestEntrySection(
                title = stringResource(
                    R.string.best50_new_section,
                    activeProfile?.b15Count ?: 15,
                ),
                entries = best50.b15,
                coverImageStore = container.coverImageStore,
            )
        }
        item {
            BestEntrySection(
                title = stringResource(
                    R.string.best50_old_section,
                    activeProfile?.b35Count ?: 35,
                ),
                entries = best50.b35,
                coverImageStore = container.coverImageStore,
            )
        }
    }
}

@Composable
private fun BestRatingSummary(total: Int, b35Sum: Int, b15Sum: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        insideMargin = PaddingValues(horizontal = 18.dp, vertical = 16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.best50_rating),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = total.toString(),
                    style = MiuixTheme.textStyles.headline1.copy(
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                    ),
                    color = ratingColor(total),
                )
            }
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = stringResource(R.string.best50_old_sum, b35Sum),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = stringResource(R.string.best50_new_sum, b15Sum),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
            }
        }
    }
}

@Composable
private fun BestCapacityCard(
    b35Text: String,
    b15Text: String,
    onB35Change: (String) -> Unit,
    onB15Change: (String) -> Unit,
    onCommit: () -> Unit,
) {
    val total = (b35Text.toIntOrNull() ?: 0) + (b15Text.toIntOrNull() ?: 0)
    Card(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(14.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BestCapacityField(
                label = stringResource(R.string.best50_capacity_old),
                value = b35Text,
                onValueChange = onB35Change,
                onCommit = onCommit,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "+",
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
            BestCapacityField(
                label = stringResource(R.string.best50_capacity_new),
                value = b15Text,
                onValueChange = onB15Change,
                onCommit = onCommit,
                modifier = Modifier.weight(1f),
            )
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(36.dp)
                    .background(MiuixTheme.colorScheme.onSurface.copy(alpha = 0.08f)),
            )
            Column(
                modifier = Modifier.weight(0.8f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = stringResource(R.string.best50_capacity_total),
                    style = MiuixTheme.textStyles.footnote2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                Text(
                    text = total.toString(),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    color = BestAccent,
                )
            }
        }
    }
}

@Composable
private fun BestCapacityField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    onCommit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var wasFocused by remember { mutableStateOf(false) }
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Text(
            text = label,
            style = MiuixTheme.textStyles.footnote2,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
        TextField(
            value = value,
            onValueChange = onValueChange,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Number,
                imeAction = ImeAction.Done,
            ),
            keyboardActions = KeyboardActions(onDone = { onCommit() }),
            textStyle = MiuixTheme.textStyles.body1.copy(
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                textAlign = TextAlign.Center,
            ),
            singleLine = true,
            cornerRadius = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (wasFocused && !focusState.isFocused) onCommit()
                    wasFocused = focusState.isFocused
                },
        )
    }
}

@Composable
private fun BestEntrySection(
    title: String,
    entries: List<RatingUtils.Entry>,
    coverImageStore: CoverImageStore,
) {
    Column {
        SmallTitle(
            text = title,
            insideMargin = PaddingValues(horizontal = 4.dp, vertical = 6.dp),
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 16.dp,
            insideMargin = PaddingValues(vertical = 4.dp),
            colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        ) {
            if (entries.isEmpty()) {
                Text(
                    text = stringResource(R.string.best50_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
                )
            } else {
                entries.forEach { entry ->
                    BestEntryRow(entry = entry, coverImageStore = coverImageStore)
                }
            }
        }
    }
}

@Composable
private fun BestEntryRow(
    entry: RatingUtils.Entry,
    coverImageStore: CoverImageStore,
) {
    val darkTheme = SongVisualUtils.isDarkTheme(MiuixTheme.colorScheme.background)
    val difficultyColor = SongVisualUtils.difficultyColor(
        difficulty = entry.difficulty,
        type = entry.type,
        darkTheme = darkTheme,
        brightenDark = true,
    )
    val typeColor = SongVisualUtils.chartTypeColor(entry.type, darkTheme, difficultyColor)
    val rank = RatingUtils.rank(entry.achievement)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(84.dp)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .height(52.dp)
                .width(4.dp)
                .squircleSurface(
                    color = difficultyColor,
                    cornerRadius = 2.dp,
                    extension = SquircleExtension,
                ),
        )
        Spacer(Modifier.width(10.dp))
        BestJacket(entry.imageName, coverImageStore)
        Spacer(Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.title,
                style = MiuixTheme.textStyles.body1,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = rank,
                    style = MiuixTheme.textStyles.footnote1,
                    fontWeight = FontWeight.Black,
                    color = ScoreStatusColors.rank(rank) ?: MiuixTheme.colorScheme.onSurface,
                )
                Text(
                    text = String.format(Locale.ROOT, "%.4f%%", entry.achievement),
                    style = MiuixTheme.textStyles.footnote1.copy(fontFamily = FontFamily.Monospace),
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                if (entry.dxScore > 0) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = null,
                            modifier = Modifier.size(10.dp),
                            tint = DxScoreColor,
                        )
                        Text(
                            text = entry.dxScore.toString(),
                            style = MiuixTheme.textStyles.footnote2,
                            fontWeight = FontWeight.Bold,
                            color = DxScoreColor,
                        )
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                BestBadge(entry.type.uppercase(), typeColor)
                entry.fc?.takeIf(String::isNotBlank)?.let {
                    BestBadge(ScoreRules.displayFc(it) ?: it.uppercase(), ScoreStatusColors.combo(it))
                }
                entry.fs?.takeIf(String::isNotBlank)?.let {
                    BestBadge(ScoreRules.displayFs(it) ?: it.uppercase(), ScoreStatusColors.sync(it))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        Column(
            modifier = Modifier.padding(end = 14.dp),
            horizontalAlignment = Alignment.End,
        ) {
            Text(
                text = entry.rating.toString(),
                style = MiuixTheme.textStyles.title3,
                fontWeight = FontWeight.Black,
                color = BestAccent,
            )
            Text(
                text = stringResource(R.string.best50_base, entry.level),
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
            )
        }
    }
}

@Composable
private fun BestJacket(imageName: String, coverImageStore: CoverImageStore) {
    val model = remember(imageName) {
        coverImageStore.fileFor(imageName)
            ?: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/$imageName"
    }
    Box(
        modifier = Modifier
            .size(56.dp)
            .squircleSurface(
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                cornerRadius = 10.dp,
                extension = SquircleExtension,
            ),
    ) {
        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun BestBadge(text: String, color: Color?) {
    val resolved = color ?: MiuixTheme.colorScheme.onSurfaceVariantActions
    Text(
        text = text,
        style = MiuixTheme.textStyles.footnote2.copy(fontSize = 9.sp),
        fontWeight = FontWeight.Bold,
        color = resolved,
        modifier = Modifier
            .squircleSurface(
                color = resolved.copy(alpha = 0.13f),
                cornerRadius = 4.dp,
                extension = SquircleExtension,
            )
            .padding(horizontal = 5.dp, vertical = 2.dp),
    )
}

private fun shareBest50(context: Context, state: Best50State) {
    val bitmap = Bitmap.createBitmap(
        1200,
        180 + (state.b35.size + state.b15.size) * 70,
        Bitmap.Config.ARGB_8888,
    )
    val canvas = Canvas(bitmap)
    canvas.drawColor(android.graphics.Color.WHITE)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.BLACK
        textSize = 42f
    }
    canvas.drawText("maimaid Best 50  ${state.total}", 48f, 64f, paint)
    var y = 130f
    listOf("B15" to state.b15, "B35" to state.b35).forEach { (label, entries) ->
        paint.textSize = 30f
        canvas.drawText(label, 48f, y, paint)
        y += 42f
        paint.textSize = 24f
        entries.forEach { entry ->
            val achievement = String.format(Locale.ROOT, "%.4f", entry.achievement)
            canvas.drawText("${entry.rating}  ${entry.title.take(36)}  $achievement%", 72f, y, paint)
            y += 58f
        }
    }
    val file = File(context.cacheDir, "best50.png")
    FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    val uri = runCatching {
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }.getOrNull() ?: return
    context.startActivity(
        Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            },
            null,
        ),
    )
}

private fun ratingColor(rating: Int): Color = when {
    rating >= 15_000 -> Color(0xFFFF6100)
    rating >= 14_500 -> Color(0xFFE5E4E2)
    rating >= 14_000 -> Color(0xFFFFD700)
    rating >= 13_000 -> Color(0xFFC0C0C0)
    rating >= 12_000 -> Color(0xFFCD7F32)
    rating >= 10_000 -> Color(0xFFD084FF)
    rating >= 7_000 -> Color(0xFFFF5E5E)
    rating >= 4_000 -> Color(0xFFFFD400)
    rating >= 2_000 -> Color(0xFF46D246)
    rating >= 1_000 -> Color(0xFF56A6FF)
    else -> Color(0xFF8E8E93)
}

private val BestAccent = Color(0xFFFF9500)
private val DxScoreColor = Color(0xFFE9B820)
