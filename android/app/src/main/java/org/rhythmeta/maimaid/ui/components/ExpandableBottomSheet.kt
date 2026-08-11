package org.rhythmeta.maimaid.ui.components

import android.view.Window
import android.view.WindowManager
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.AnchoredDraggableDefaults
import androidx.compose.foundation.gestures.AnchoredDraggableState
import androidx.compose.foundation.gestures.DraggableAnchors
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.gestures.animateTo
import androidx.compose.foundation.gestures.snapTo
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.kyant.backdrop.drawPlainBackdrop
import com.kyant.backdrop.effects.blur
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.roundToInt

private enum class BottomSheetAnchor {
    Expanded,
    HalfExpanded,
    Hidden,
}

@Composable
internal fun ExpandableBottomSheet(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    expandActionLabel: String,
    collapseActionLabel: String,
    expandedStateDescription: String,
    halfExpandedStateDescription: String,
    header: @Composable BoxScope.() -> Unit,
    content: @Composable (topInset: Dp) -> Unit,
) {
    var mounted by remember { mutableStateOf(visible) }
    var locallyDismissed by remember { mutableStateOf(false) }
    val currentDismissRequest by rememberUpdatedState(onDismissRequest)
    val dialogWindowHandle = remember { DialogWindowHandle() }

    LaunchedEffect(visible) {
        if (visible) {
            mounted = true
        } else {
            locallyDismissed = false
        }
    }

    if ((visible || mounted) && !locallyDismissed) {
        Dialog(
            onDismissRequest = currentDismissRequest,
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
        ) {
            ConfigurePlatformDialog(dialogWindowHandle)
            ExpandableBottomSheetLayout(
                visible = visible,
                expandActionLabel = expandActionLabel,
                collapseActionLabel = collapseActionLabel,
                expandedStateDescription = expandedStateDescription,
                halfExpandedStateDescription = halfExpandedStateDescription,
                onDismissRequest = currentDismissRequest,
                onExitAnimationFinished = { mounted = false },
                onGestureDismissed = {
                    dialogWindowHandle.disableTouch()
                    locallyDismissed = true
                    mounted = false
                    currentDismissRequest()
                },
                header = header,
                content = content,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ExpandableBottomSheetLayout(
    visible: Boolean,
    expandActionLabel: String,
    collapseActionLabel: String,
    expandedStateDescription: String,
    halfExpandedStateDescription: String,
    onDismissRequest: () -> Unit,
    onExitAnimationFinished: () -> Unit,
    onGestureDismissed: () -> Unit,
    header: @Composable BoxScope.() -> Unit,
    content: @Composable (topInset: Dp) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val sheetHeight = (maxHeight - statusBarHeight).coerceAtLeast(1.dp)
        val hiddenOffset = with(density) { sheetHeight.toPx() }
        val halfExpandedOffset = hiddenOffset * HalfExpandedOffsetFraction
        val state = remember { AnchoredDraggableState(BottomSheetAnchor.Hidden) }
        val allAnchors = remember(hiddenOffset, halfExpandedOffset) {
            DraggableAnchors {
                BottomSheetAnchor.Expanded at 0f
                BottomSheetAnchor.HalfExpanded at halfExpandedOffset
                BottomSheetAnchor.Hidden at hiddenOffset
            }
        }
        val anchors = remember(hiddenOffset, halfExpandedOffset, state.settledValue) {
            DraggableAnchors {
                when (state.settledValue) {
                    BottomSheetAnchor.Expanded -> {
                        BottomSheetAnchor.Expanded at 0f
                        BottomSheetAnchor.HalfExpanded at halfExpandedOffset
                        BottomSheetAnchor.Hidden at hiddenOffset
                    }

                    BottomSheetAnchor.HalfExpanded -> {
                        BottomSheetAnchor.Expanded at 0f
                        BottomSheetAnchor.HalfExpanded at halfExpandedOffset
                        BottomSheetAnchor.Hidden at hiddenOffset
                    }

                    BottomSheetAnchor.Hidden -> {
                        BottomSheetAnchor.HalfExpanded at halfExpandedOffset
                        BottomSheetAnchor.Hidden at hiddenOffset
                    }
                }
            }
        }
        var entranceComplete by remember { mutableStateOf(false) }

        LaunchedEffect(anchors) {
            state.updateAnchors(anchors)
        }
        LaunchedEffect(visible) {
            state.updateAnchors(allAnchors)
            if (visible) {
                entranceComplete = false
                state.snapTo(BottomSheetAnchor.Hidden)
                state.animateTo(BottomSheetAnchor.HalfExpanded, BottomSheetAnimationSpec)
                entranceComplete = true
            } else {
                entranceComplete = false
                state.animateTo(BottomSheetAnchor.Hidden, BottomSheetAnimationSpec)
                onExitAnimationFinished()
            }
        }
        LaunchedEffect(state.settledValue, entranceComplete, visible) {
            if (
                visible &&
                entranceComplete &&
                state.settledValue == BottomSheetAnchor.Hidden
            ) {
                onGestureDismissed()
            }
        }

        val offset = state.offset.takeUnless(Float::isNaN) ?: hiddenOffset
        val visibleSheetHeight = sheetHeight - with(density) { offset.toDp() }
        val dimProgress = ((hiddenOffset - offset) / (hiddenOffset - halfExpandedOffset))
            .coerceIn(0f, 1f)
        val sheetBackground = MiuixTheme.colorScheme.background
        val sheetShape = remember { squircleShape(BottomSheetCornerRadius) }
        val sheetBackdrop = rememberLayerBackdrop {
            drawRect(sheetBackground)
            drawContent()
        }
        val outsideInteraction = remember { MutableInteractionSource() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = dimProgress }
                .background(MiuixTheme.colorScheme.windowDimming)
                .clickable(
                    interactionSource = outsideInteraction,
                    indication = null,
                    onClick = onDismissRequest,
                ),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(sheetHeight)
                .offset { IntOffset(0, offset.roundToInt()) }
                .graphicsLayer {
                    shape = sheetShape
                    clip = true
                    shadowElevation = 18.dp.toPx()
                }
                .background(sheetBackground),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(visibleSheetHeight.coerceAtLeast(0.dp))
                    .layerBackdrop(sheetBackdrop)
                    .navigationBarsPadding(),
            ) {
                content(BottomSheetHeaderHeight)
            }
            ExpandableBottomSheetHeader(
                state = state,
                backdrop = sheetBackdrop,
                expandActionLabel = expandActionLabel,
                collapseActionLabel = collapseActionLabel,
                expandedStateDescription = expandedStateDescription,
                halfExpandedStateDescription = halfExpandedStateDescription,
                content = header,
            )
        }
    }
}

@Composable
@OptIn(ExperimentalFoundationApi::class)
private fun ExpandableBottomSheetHeader(
    state: AnchoredDraggableState<BottomSheetAnchor>,
    backdrop: Backdrop,
    expandActionLabel: String,
    collapseActionLabel: String,
    expandedStateDescription: String,
    halfExpandedStateDescription: String,
    content: @Composable BoxScope.() -> Unit,
) {
    val scope = rememberCoroutineScope()
    val expanded = state.settledValue == BottomSheetAnchor.Expanded
    val actionLabel = if (expanded) collapseActionLabel else expandActionLabel
    val stateLabel = if (expanded) expandedStateDescription else halfExpandedStateDescription
    val flingBehavior = AnchoredDraggableDefaults.flingBehavior(
        state = state,
        animationSpec = BottomSheetAnimationSpec,
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .drawPlainBackdrop(
                backdrop = backdrop,
                shape = { RectangleShape },
                effects = { blur(BottomSheetHeaderBlurRadius.toPx()) },
            )
            .anchoredDraggable(
                state = state,
                orientation = Orientation.Vertical,
                flingBehavior = flingBehavior,
            )
            .semantics {
                stateDescription = stateLabel
                onClick(label = actionLabel) {
                    scope.launch {
                        state.animateTo(
                            if (expanded) {
                                BottomSheetAnchor.HalfExpanded
                            } else {
                                BottomSheetAnchor.Expanded
                            },
                            BottomSheetAnimationSpec,
                        )
                    }
                    true
                }
            },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .padding(top = 8.dp, bottom = 4.dp)
                .size(width = 36.dp, height = 4.dp)
                .squircleSurface(
                    color = MiuixTheme.colorScheme.onSurface.copy(alpha = 0.24f),
                    cornerRadius = 2.dp,
                    extension = SquircleExtension,
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = BottomSheetHeaderHorizontalPadding),
            content = content,
        )
    }
}

private class DialogWindowHandle {
    var window: Window? = null

    fun disableTouch() {
        window?.addFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
    }
}

@Composable
private fun ConfigurePlatformDialog(handle: DialogWindowHandle) {
    val view = LocalView.current
    DisposableEffect(view, handle) {
        val window = (view.parent as? DialogWindowProvider)?.window
        handle.window = window
        window?.apply {
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE)
            setDimAmount(0f)
            clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        }
        onDispose {
            if (handle.window === window) {
                handle.window = null
            }
        }
    }
}

private const val HalfExpandedOffsetFraction = 0.48f
private val BottomSheetHeaderHeight = 68.dp
private val BottomSheetCornerRadius = 24.dp
private val BottomSheetHeaderBlurRadius = 24.dp
private val BottomSheetHeaderHorizontalPadding = 12.dp
private val BottomSheetAnimationSpec = spring<Float>(
    dampingRatio = 0.86f,
    stiffness = 420f,
)
