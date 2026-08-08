package net.krtl.maimaid.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import net.krtl.maimaid.R
import net.krtl.maimaid.data.assets.DanCatalogStore
import net.krtl.maimaid.data.assets.DanCategory
import net.krtl.maimaid.data.assets.DanSection
import net.krtl.maimaid.data.assets.DanSheetRef
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.ui.app.AppContainer
import net.krtl.maimaid.ui.common.SecondaryScreenScaffold
import net.krtl.maimaid.ui.common.SectionCard

@Composable
fun DanListScreen(
    innerPadding: PaddingValues,
    openCategory: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var categories by remember { mutableStateOf<List<DanCategory>?>(null) }

    LaunchedEffect(context) {
        categories = DanCatalogStore.load(context)
    }

    SecondaryScreenScaffold(
        title = stringResource(R.string.home_dan_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        when (val current = categories) {
            null -> DanLoadingState(contentPadding)
            emptyList<DanCategory>() -> DanEmptyState(contentPadding)
            else -> LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(contentPadding),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(current, key = DanCategory::id) { category ->
                    DanCategoryCard(
                        category = category,
                        onClick = { openCategory(category.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun DanDetailScreen(
    container: AppContainer,
    innerPadding: PaddingValues,
    categoryId: String,
    openSong: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val songs by container.staticDataRepository.observeSongs().collectAsStateWithLifecycle(initialValue = emptyList())
    var category by remember(categoryId) { mutableStateOf<DanCategory?>(null) }

    LaunchedEffect(context, categoryId) {
        category = DanCatalogStore.load(context).firstOrNull { it.id == categoryId }
    }

    SecondaryScreenScaffold(
        title = category?.title ?: stringResource(R.string.home_dan_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        val currentCategory = category
        when {
            currentCategory == null -> DanLoadingState(contentPadding)
            else -> {
                val songMap = rememberSongMap(songs)
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(contentPadding),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(currentCategory.sections) { section ->
                        DanSectionCard(
                            categoryTitle = currentCategory.title,
                            section = section,
                            songMap = songMap,
                            openSong = openSong
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DanLoadingState(contentPadding: PaddingValues) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Composable
private fun DanEmptyState(contentPadding: PaddingValues) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(20.dp)
    ) {
        SectionCard(
            title = stringResource(R.string.dan_empty_title),
            subtitle = stringResource(R.string.dan_empty_description)
        )
    }
}

@Composable
private fun DanCategoryCard(
    category: DanCategory,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color(0xFFE46A3C).copy(alpha = 0.12f),
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Verified,
                        contentDescription = null,
                        tint = Color(0xFFE46A3C)
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = category.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.dan_list_sections, category.sections.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}

@Composable
private fun DanSectionCard(
    categoryTitle: String,
    section: DanSection,
    songMap: Map<String, Song>,
    openSong: (String) -> Unit
) {
    val title = section.title?.takeIf { it.isNotBlank() } ?: categoryTitle
    val accent = sectionAccent(title)

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black
            )
            section.description?.takeIf { it.isNotBlank() }?.let { description ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accent.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                    )
                }
            }
            section.sheets.forEachIndexed { index, raw ->
                val ref = DanSheetRef.from(raw)
                val song = songMap[ref.title]
                DanSongRow(
                    ref = ref,
                    supportingText = section.sheetDescriptions?.getOrNull(index),
                    song = song,
                    accent = accent,
                    onClick = song?.let { { openSong(it.songIdentifier) } }
                )
            }
        }
    }
}

@Composable
private fun DanSongRow(
    ref: DanSheetRef,
    supportingText: String?,
    song: Song?,
    accent: Color,
    onClick: (() -> Unit)?
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .let { base -> if (onClick != null) base.clickable(onClick = onClick) else base }
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = ref.title.ifBlank { stringResource(R.string.dan_song_unknown) },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (onClick != null) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DanTag(text = ref.type.uppercase().ifBlank { "??" }, tint = accent)
                DanTag(text = ref.difficulty.uppercase().ifBlank { "??" }, tint = accent.copy(alpha = 0.82f))
                supportingText?.takeIf { it.isNotBlank() }?.let {
                    DanTag(text = it, tint = MaterialTheme.colorScheme.primary)
                }
            }
            Text(
                text = when {
                    song != null -> stringResource(R.string.dan_song_matched)
                    ref.extra != null -> stringResource(R.string.dan_song_range, ref.extra)
                    else -> stringResource(R.string.dan_song_unmatched)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DanTag(text: String, tint: Color) {
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            color = tint,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun rememberSongMap(songs: List<Song>): Map<String, Song> = remember(songs) {
    buildMap {
        songs.forEach { song ->
            val hasStandardCharts = song.sheets.any { !it.type.equals("utage", ignoreCase = true) }
            if (hasStandardCharts && song.title !in this) {
                put(song.title, song)
            }
        }
    }
}

private fun sectionAccent(title: String): Color {
    val normalized = title.lowercase()
    return when {
        normalized.contains("master") -> Color(0xFF7B61FF)
        normalized.contains("expert") -> Color(0xFFE46A3C)
        listOf("真", "超", "檄", "橙", "暁", "晓", "桃", "櫻", "樱", "紫", "菫", "白", "雪", "輝", "辉", "熊", "華", "华", "爽", "煌", "舞", "霸")
            .any(title::contains) -> Color(0xFF7B61FF)
        else -> Color(0xFFE46A3C)
    }
}
