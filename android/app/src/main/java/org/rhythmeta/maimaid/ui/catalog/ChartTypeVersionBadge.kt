package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import org.rhythmeta.maimaid.ui.components.squircleShape
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun ChartTypeVersionBadge(
    text: String,
    chartTypes: List<String>,
    darkTheme: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = chartTypes.map { type ->
        SongVisualUtils.chartTypeColor(
            type = type,
            darkTheme = darkTheme,
            fallbackColor = MiuixTheme.colorScheme.primary,
        )
    }
    val backgroundColor = colors.firstOrNull() ?: MiuixTheme.colorScheme.primary
    Box(
        modifier = modifier
            .squircleSurface(
                color = backgroundColor,
                cornerRadius = 4.dp,
                extension = SquircleExtension,
            )
            .clip(squircleShape(4.dp)),
        contentAlignment = Alignment.Center,
    ) {
        if (colors.size > 1) {
            Canvas(Modifier.matchParentSize()) {
                val split = Path().apply {
                    moveTo(size.width * 0.44f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height)
                    lineTo(size.width * 0.56f, size.height)
                    close()
                }
                drawPath(split, colors[1])
            }
        }
        Text(
            text = text,
            style = MiuixTheme.textStyles.footnote2.copy(
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
            ),
            color = Color.White,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
        )
    }
}
