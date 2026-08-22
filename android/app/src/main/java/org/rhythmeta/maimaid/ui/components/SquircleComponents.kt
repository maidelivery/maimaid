package org.rhythmeta.maimaid.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.addSquircleRect

const val SquircleExtension = 1.2f

fun squircleShape(cornerRadius: Dp): Shape = FixedRadiusSquircleShape(cornerRadius)

val SquircleCapsule: Shape = RoundedCornerShape(percent = 50)

val TopBarBottomShape: Shape = RoundedCornerShape(0.dp)

fun Modifier.dashedSquircleBorder(
    width: Dp,
    color: Color,
    cornerRadius: Dp,
    dashLength: Dp = 4.dp,
    gapLength: Dp = 3.dp,
    extension: Float = SquircleExtension,
): Modifier = drawWithCache {
    val widthPx = width.toPx()
    val halfStroke = widthPx / 2f
    val innerWidth = size.width - widthPx
    val innerHeight = size.height - widthPx
    val path = Path()
    val drawable = widthPx > 0f && innerWidth > 0f && innerHeight > 0f
    if (drawable) {
        path.addSquircleRect(
            width = innerWidth,
            height = innerHeight,
            cornerRadius = (cornerRadius.toPx() - halfStroke).coerceAtLeast(0f),
            extension = extension,
        )
    }
    val stroke = Stroke(
        width = widthPx,
        pathEffect = PathEffect.dashPathEffect(
            intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
        ),
    )
    onDrawBehind {
        if (drawable) {
            translate(halfStroke, halfStroke) {
                drawPath(path = path, color = color, style = stroke)
            }
        }
    }
}

@Immutable
private data class FixedRadiusSquircleShape(
    val cornerRadius: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline = Outline.Generic(
        Path().apply {
            addSquircleRect(
                width = size.width,
                height = size.height,
                cornerRadius = with(density) { cornerRadius.toPx() },
                extension = SquircleExtension,
            )
        },
    )
}
