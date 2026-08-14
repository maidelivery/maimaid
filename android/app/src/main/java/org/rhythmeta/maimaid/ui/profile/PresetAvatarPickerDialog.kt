package org.rhythmeta.maimaid.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.PresetAvatar
import org.rhythmeta.maimaid.core.data.PresetAvatarRepository
import org.rhythmeta.maimaid.ui.components.squircleShape
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

@Composable
internal fun PresetAvatarPickerDialog(
    show: Boolean,
    repository: PresetAvatarRepository,
    onDismiss: () -> Unit,
    onSelect: (PresetAvatar) -> Unit,
) {
    val avatars by repository.avatars.collectAsStateWithLifecycle(emptyList())
    var query by remember { mutableStateOf("") }

    val filteredAvatars = avatars.filter { avatar ->
        query.isBlank() || avatar.name.contains(query, ignoreCase = true) || avatar.genre.contains(query, ignoreCase = true)
    }

    WindowDialog(
        show = show,
        title = stringResource(R.string.profile_preset_avatar_title),
        onDismissRequest = onDismiss,
        outsideMargin = DpSize(20.dp, 20.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextField(
                value = query,
                onValueChange = { query = it },
                label = stringResource(R.string.profile_preset_avatar_search),
                useLabelAsPlaceholder = true,
                modifier = Modifier.fillMaxWidth(),
            )
            when {
                filteredAvatars.isEmpty() -> Text(
                    text = stringResource(R.string.profile_preset_avatar_empty),
                    style = MiuixTheme.textStyles.body2,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 84.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    contentPadding = PaddingValues(bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredAvatars, key = PresetAvatar::id) { avatar ->
                        Button(
                            onClick = { onSelect(avatar) },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                AsyncImage(
                                    model = repository.imageFileFor(avatar.id) ?: avatar.imageUrl,
                                    contentDescription = avatar.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(squircleShape(12.dp)),
                                )
                                Text(
                                    text = avatar.name,
                                    style = MiuixTheme.textStyles.footnote2,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
