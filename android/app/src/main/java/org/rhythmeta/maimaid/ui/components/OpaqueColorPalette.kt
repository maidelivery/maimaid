package org.rhythmeta.maimaid.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import org.rhythmeta.maimaid.ui.theme.ThemeSeedSaturation
import top.yukonga.miuix.kmp.basic.ColorPalette

@Composable
fun OpaqueColorPalette(
    color: Color,
    onColorChanged: (Color) -> Unit,
    modifier: Modifier = Modifier,
) {
    val paletteColorFilter = remember {
        ColorFilter.colorMatrix(
            ColorMatrix().apply { setToSaturation(ThemeSeedSaturation) },
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(180.dp)
            .clipToBounds(),
    ) {
        ColorPalette(
            color = color.copy(alpha = 1f),
            onColorChanged = { selectedColor ->
                onColorChanged(selectedColor.copy(alpha = 1f))
            },
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(
                    align = Alignment.Top,
                    unbounded = true,
                )
                .graphicsLayer { colorFilter = paletteColorFilter },
            showPreview = false,
        )
    }
}
