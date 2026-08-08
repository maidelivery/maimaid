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
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.krtl.maimaid.R
import net.krtl.maimaid.ui.common.SecondaryLargeTitleScaffold

private data class UsefulLinkItem(
    val title: String,
    val subtitle: String,
    val url: String,
    val icon: ImageVector,
    val color: Color
)

@Composable
fun UsefulLinksScreen(
    innerPadding: PaddingValues,
    onBack: () -> Unit
) {
    val uriHandler = LocalUriHandler.current
    val links = remember {
        listOf(
            UsefulLinkItem("NearArcade", "Find nearby arcades and locations", "https://nearcade.phizone.cn", Icons.Default.LocationOn, Color(0xFFE45858)),
            UsefulLinkItem("maimai DX JP", "Official Japanese maimai portal", "https://maimai.sega.jp/", Icons.Default.Language, Color(0xFF2D9CDB)),
            UsefulLinkItem("Gamerch", "Japanese community wiki and song lookup", "https://maimai.gamerch.com/", Icons.AutoMirrored.Filled.MenuBook, Color(0xFF27AE60)),
            UsefulLinkItem("DXRating", "Rating and chart reference site", "https://dxrating.net/", Icons.Default.Insights, Color(0xFF00ACC1)),
            UsefulLinkItem("maiLv_Chihooooo", "Maimai-related posts on X", "https://x.com/maiLv_Chihooooo", Icons.Default.Link, Color(0xFF3B82F6))
        )
    }

    SecondaryLargeTitleScaffold(
        title = stringResource(R.string.home_useful_links_title),
        innerPadding = innerPadding,
        onBack = onBack
    ) { contentPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = PaddingValues(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(links, key = UsefulLinkItem::url) { link ->
                UsefulLinkCard(
                    link = link,
                    onClick = { uriHandler.openUri(link.url) }
                )
            }
        }
    }
}

@Composable
private fun UsefulLinkCard(
    link: UsefulLinkItem,
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
                color = link.color.copy(alpha = 0.12f),
                shape = CircleShape
            ) {
                Box(
                    modifier = Modifier.padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = link.icon,
                        contentDescription = null,
                        tint = link.color
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = link.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = link.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}
