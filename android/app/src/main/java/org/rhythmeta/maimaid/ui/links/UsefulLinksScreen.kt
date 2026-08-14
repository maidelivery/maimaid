package org.rhythmeta.maimaid.ui.links

import android.widget.Toast
import androidx.annotation.StringRes
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Book
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.ui.common.openInAppBrowser
import org.rhythmeta.maimaid.ui.components.SongListScrollBar
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.CardDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

private data class UsefulLink(
    @StringRes val title: Int,
    @StringRes val subtitle: Int,
    val url: String,
    val icon: ImageVector,
    val color: Color,
)

@Composable
internal fun UsefulLinksScreen(contentTopPadding: Dp) {
    val context = LocalContext.current
    val links = remember { usefulLinks() }
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                top = contentTopPadding + 12.dp,
                end = 16.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(links, key = UsefulLink::url) { link ->
                UsefulLinkRow(link) {
                    if (!context.openInAppBrowser(link.url)) {
                        Toast.makeText(context, R.string.cloud_browser_unavailable, Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
        SongListScrollBar(
            state = listState,
            trackPadding = PaddingValues(top = contentTopPadding + 12.dp, bottom = 36.dp),
        )
    }
}

@Composable
private fun UsefulLinkRow(link: UsefulLink, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .squircleBorder(
                width = 1.dp,
                color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                cornerRadius = 16.dp,
                extension = SquircleExtension,
            ),
        cornerRadius = 16.dp,
        insideMargin = PaddingValues(16.dp),
        colors = CardDefaults.defaultColors(color = MiuixTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .squircleSurface(
                        color = link.color.copy(alpha = 0.1f),
                        cornerRadius = 24.dp,
                        extension = SquircleExtension,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = link.icon,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = link.color,
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = stringResource(link.title),
                    style = MiuixTheme.textStyles.body1,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(link.subtitle),
                    style = MiuixTheme.textStyles.footnote1,
                    color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                contentDescription = stringResource(R.string.action_open),
                modifier = Modifier.size(18.dp),
                tint = MiuixTheme.colorScheme.onSurfaceVariantActions.copy(alpha = 0.45f),
            )
        }
    }
}

private fun usefulLinks(): List<UsefulLink> = listOf(
    UsefulLink(R.string.links_nearcade_title, R.string.links_nearcade_subtitle, "https://nearcade.phizone.cn", Icons.Rounded.LocationOn, Color(0xFFE64A4A)),
    UsefulLink(R.string.links_dx_jp_title, R.string.links_dx_jp_subtitle, "https://maimai.sega.jp/", Icons.Rounded.Language, Color(0xFF2F78D0)),
    UsefulLink(R.string.links_df_title, R.string.links_df_subtitle, "https://www.maimai.cn/", Icons.Rounded.Insights, Color(0xFFE87500)),
    UsefulLink(R.string.links_lxns_title, R.string.links_lxns_subtitle, "https://maimai.lxns.net/", Icons.Rounded.Insights, Color(0xFF8A4FB8)),
    UsefulLink(R.string.links_gamerch_title, R.string.links_gamerch_subtitle, "https://maimai.gamerch.com/", Icons.Rounded.Book, Color(0xFF2E9D61)),
    UsefulLink(R.string.links_dxrating_title, R.string.links_dxrating_subtitle, "https://dxrating.net/", Icons.Rounded.Book, Color(0xFF168E9A)),
    UsefulLink(R.string.links_mailv_title, R.string.links_mailv_subtitle, "https://x.com/maiLv_Chihooooo", Icons.Rounded.Book, Color(0xFF2F78D0)),
)
