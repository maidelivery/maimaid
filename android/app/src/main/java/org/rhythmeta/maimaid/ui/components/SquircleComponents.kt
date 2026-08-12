package org.rhythmeta.maimaid.ui.components

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.squircle.addSquircleRect

const val SquircleExtension = 1.2f

fun squircleShape(cornerRadius: Dp): Shape = FixedRadiusSquircleShape(cornerRadius)

val SquircleCapsule: Shape = RoundedCornerShape(percent = 50)

val TopBarBottomShape: Shape = RoundedCornerShape(
    bottomStart = 18.dp,
    bottomEnd = 18.dp,
)

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
