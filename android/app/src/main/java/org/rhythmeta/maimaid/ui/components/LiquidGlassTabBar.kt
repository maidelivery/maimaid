package org.rhythmeta.maimaid.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastCoerceIn
import androidx.compose.ui.util.fastRoundToInt
import androidx.compose.ui.util.lerp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.highlight.Highlight
import com.kyant.backdrop.shadow.InnerShadow
import com.kyant.backdrop.shadow.Shadow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.abs
import kotlin.math.sign

data class LiquidGlassTab(
    val icon: ImageVector,
    val label: String,
)

@Composable
fun LiquidGlassTabBar(
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
    backdrop: Backdrop,
    tabs: List<LiquidGlassTab>,
    modifier: Modifier = Modifier,
) {
    require(tabs.isNotEmpty())

    val isLight = MiuixTheme.colorScheme.background.luminance() > 0.5f
    val accentColor = MiuixTheme.colorScheme.primary
    val tabContentColor = MiuixTheme.colorScheme.onSurfaceVariantSummary
    val containerColor = MiuixTheme.colorScheme.surfaceContainer.copy(alpha = 0.4f)
    val tabsBackdrop = rememberLayerBackdrop()
    val combinedBackdrop = rememberCombinedBackdrop(backdrop, tabsBackdrop)
    val density = LocalDensity.current
    val isLtr = LocalLayoutDirection.current == LayoutDirection.Ltr
    val animationScope = rememberCoroutineScope()

    BoxWithConstraints(
        modifier = modifier
            .width(312.dp)
            .height(64.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        val horizontalPadding = 4.dp
        val horizontalPaddingPx = with(density) { horizontalPadding.toPx() }
        val tabWidthPx = (constraints.maxWidth - horizontalPaddingPx * 2f) / tabs.size
        val totalWidthPx = constraints.maxWidth.toFloat()
        val tabWidth = with(density) { tabWidthPx.toDp() }

        val offsetAnimation = remember { Animatable(0f) }
        val rubberBandPx = with(density) { 4.dp.toPx() }
        val panelOffset by remember(rubberBandPx, totalWidthPx) {
            derivedStateOf {
                val fraction = (offsetAnimation.value / totalWidthPx).fastCoerceIn(-1f, 1f)
                rubberBandPx * fraction.sign * EaseOut.transform(abs(fraction))
            }
        }

        var currentIndex by remember { mutableIntStateOf(selectedIndex.coerceIn(tabs.indices)) }

        class AnimationHolder {
            var instance: DampedDragAnimation? = null
        }

        val holder = remember { AnimationHolder() }
        val dragAnimation = remember(animationScope, tabs.size, density, isLtr) {
            DampedDragAnimation(
                animationScope = animationScope,
                initialValue = selectedIndex.coerceIn(tabs.indices).toFloat(),
                valueRange = 0f..tabs.lastIndex.toFloat(),
                visibilityThreshold = 0.001f,
                initialScale = 1f,
                pressedScale = 78f / 56f,
                canDrag = { offset ->
                    val animation = holder.instance ?: return@DampedDragAnimation true
                    val indicatorStart = animation.value * tabWidthPx
                    val globalTouchX = if (isLtr) {
                        horizontalPaddingPx + indicatorStart + offset.x
                    } else {
                        totalWidthPx - horizontalPaddingPx - tabWidthPx - indicatorStart + offset.x
                    }
                    globalTouchX in 0f..totalWidthPx
                },
                onDragStarted = {},
                onDragStopped = {
                    val targetIndex = targetValue.fastRoundToInt().fastCoerceIn(0, tabs.lastIndex)
                    currentIndex = targetIndex
                    animateToValue(targetIndex.toFloat())
                    animationScope.launch {
                        offsetAnimation.animateTo(0f, spring(1f, 300f, 0.5f))
                    }
                },
                onDrag = { _, dragAmount ->
                    updateValue(
                        (targetValue + dragAmount.x / tabWidthPx * if (isLtr) 1f else -1f)
                            .fastCoerceIn(0f, tabs.lastIndex.toFloat()),
                    )
                    animationScope.launch {
                        offsetAnimation.snapTo(offsetAnimation.value + dragAmount.x)
                    }
                },
            ).also { holder.instance = it }
        }

        LaunchedEffect(selectedIndex) {
            val index = selectedIndex.coerceIn(tabs.indices)
            currentIndex = index
            dragAnimation.animateToValue(index.toFloat())
        }
        LaunchedEffect(dragAnimation) {
            snapshotFlow { currentIndex }
                .drop(1)
                .collectLatest { index ->
                    dragAnimation.animateToValue(index.toFloat())
                    onSelected(index)
                }
        }

        Row(
            modifier = Modifier
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { SquircleCapsule },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(
                            refractionHeight = 24.dp.toPx(),
                            refractionAmount = 24.dp.toPx(),
                        )
                    },
                    layerBlock = {
                        val width = size.width.coerceAtLeast(1f)
                        val scale = lerp(
                            1f,
                            1f + 16.dp.toPx() / width,
                            dragAnimation.pressProgress,
                        )
                        scaleX = scale
                        scaleY = scale
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .fillMaxWidth()
                .height(64.dp)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                LiquidGlassTabItem(
                    tab = tab,
                    color = tabContentColor,
                    scale = { 1f },
                    onClick = { currentIndex = index },
                )
            }
        }

        Row(
            modifier = Modifier
                .clearAndSetSemantics { }
                .alpha(0f)
                .layerBackdrop(tabsBackdrop)
                .graphicsLayer { translationX = panelOffset }
                .drawBackdrop(
                    backdrop = backdrop,
                    shape = { SquircleCapsule },
                    effects = {
                        vibrancy()
                        blur(4.dp.toPx())
                        lens(
                            refractionHeight = 24.dp.toPx(),
                            refractionAmount = 24.dp.toPx(),
                        )
                    },
                    onDrawSurface = { drawRect(containerColor) },
                )
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = horizontalPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            tabs.forEachIndexed { index, tab ->
                LiquidGlassTabItem(
                    tab = tab,
                    color = accentColor,
                    scale = { lerp(1f, 1.2f, dragAnimation.pressProgress) },
                    onClick = { currentIndex = index },
                )
            }
        }

        Box(
            modifier = Modifier
                .graphicsLayer {
                    val progressOffset = dragAnimation.value * tabWidthPx
                    translationX = if (isLtr) {
                        horizontalPaddingPx + progressOffset + panelOffset
                    } else {
                        totalWidthPx - horizontalPaddingPx - tabWidthPx - progressOffset + panelOffset
                    }
                }
                .then(dragAnimation.modifier)
                .drawBackdrop(
                    backdrop = combinedBackdrop,
                    shape = { SquircleCapsule },
                    effects = {
                        val progress = dragAnimation.pressProgress
                        lens(
                            refractionHeight = 10.dp.toPx() * progress,
                            refractionAmount = 14.dp.toPx() * progress,
                            depthEffect = true,
                            chromaticAberration = true,
                        )
                    },
                    highlight = {
                        Highlight.Default.copy(alpha = dragAnimation.pressProgress)
                    },
                    shadow = {
                        Shadow(alpha = dragAnimation.pressProgress)
                    },
                    innerShadow = {
                        InnerShadow(
                            radius = 8.dp * dragAnimation.pressProgress,
                            alpha = dragAnimation.pressProgress,
                        )
                    },
                    layerBlock = {
                        scaleX = dragAnimation.scaleX
                        scaleY = dragAnimation.scaleY
                        val velocity = dragAnimation.velocity / 10f
                        scaleX /= 1f - (velocity * 0.75f).fastCoerceIn(-0.2f, 0.2f)
                        scaleY *= 1f - (velocity * 0.25f).fastCoerceIn(-0.2f, 0.2f)
                    },
                    onDrawSurface = {
                        val progress = dragAnimation.pressProgress
                        drawRect(
                            color = if (isLight) {
                                Color.Black.copy(alpha = 0.1f)
                            } else {
                                Color.White.copy(alpha = 0.1f)
                            },
                            alpha = 1f - progress,
                        )
                        drawRect(Color.Black.copy(alpha = 0.03f * progress))
                    },
                )
                .width(tabWidth)
                .height(56.dp),
        )
    }
}

@Composable
private fun RowScope.LiquidGlassTabItem(
    tab: LiquidGlassTab,
    color: Color,
    scale: () -> Float,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Tab,
                onClick = onClick,
            )
            .fillMaxHeight()
            .weight(1f)
            .graphicsLayer {
                scaleX = scale()
                scaleY = scale()
            },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(
            1.dp,
            Alignment.CenterVertically,
        ),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = tab.icon,
            contentDescription = tab.label,
            tint = color,
        )
        Text(
            text = tab.label,
            style = MiuixTheme.textStyles.footnote2,
            color = color,
            maxLines = 1,
        )
    }
}
