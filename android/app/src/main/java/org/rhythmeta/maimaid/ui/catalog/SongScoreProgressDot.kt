package org.rhythmeta.maimaid.ui.catalog

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.ui.util.SongVisualUtils

@Composable
internal fun SongScoreProgressDot(
    sheet: SheetEntity,
    score: ScoreEntity?,
    darkTheme: Boolean,
) {
    val color = SongVisualUtils.difficultyColor(sheet.difficulty, sheet.type, darkTheme)
    val progress = score
        ?.achievement
        ?.takeIf { it > 0.0 }
        ?.minus(100.0)
        ?.coerceIn(0.0, 1.0)
        ?.toFloat()
        ?: 0f

    Canvas(modifier = Modifier.size(8.dp)) {
        val outerStroke = 1.2.dp.toPx()
        drawCircle(
            color = color.copy(alpha = 0.3f),
            radius = (size.minDimension - outerStroke) / 2f,
            style = Stroke(width = outerStroke),
        )
        if (progress > 0f) {
            val innerSize = 4.dp.toPx()
            val inset = (size.minDimension - innerSize) / 2f
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = progress * 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = Size(innerSize, innerSize),
                style = Stroke(width = 4.dp.toPx(), cap = StrokeCap.Butt),
            )
        }
    }
}
