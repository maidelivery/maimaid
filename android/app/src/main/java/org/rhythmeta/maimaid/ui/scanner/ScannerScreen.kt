package org.rhythmeta.maimaid.ui.scanner

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.rememberScrollableState
import androidx.compose.foundation.gestures.scrollable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.ui.components.SquircleExtension
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.TabRowWithContour
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Photos
import top.yukonga.miuix.kmp.icon.extended.Scan
import top.yukonga.miuix.kmp.squircle.squircleBorder
import top.yukonga.miuix.kmp.squircle.squircleSurface
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun ScannerScreen(
    showBoundingBoxes: Boolean,
    contentTopPadding: Dp,
) {
    var selectedMode by remember { mutableIntStateOf(0) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pageScrollState = rememberScrollableState { 0f }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap: Bitmap? ->
        preview = bitmap?.asImageBitmap()
    }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            scope.launch {
                preview = withContext(Dispatchers.IO) {
                    runCatching {
                        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
                        android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                            val maxDimension = maxOf(info.size.width, info.size.height)
                            if (maxDimension > 2_048) {
                                val scale = 2_048f / maxDimension
                                decoder.setTargetSize(
                                    (info.size.width * scale).toInt(),
                                    (info.size.height * scale).toInt(),
                                )
                            }
                            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
                        }.asImageBitmap()
                    }.getOrNull()
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .scrollable(
                state = pageScrollState,
                orientation = Orientation.Vertical,
            )
            .padding(
                start = 16.dp,
                top = contentTopPadding + 10.dp,
                end = 16.dp,
                bottom = 92.dp,
            ),
    ) {
        TabRowWithContour(
            tabs = listOf(
                stringResource(R.string.scanner_score_result),
                stringResource(R.string.scanner_chart_result),
            ),
            selectedTabIndex = selectedMode,
            onTabSelected = { selectedMode = it },
        )
        Spacer(Modifier.height(12.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .squircleSurface(
                    color = MiuixTheme.colorScheme.surfaceContainer,
                    cornerRadius = 16.dp,
                    extension = SquircleExtension,
                )
                .squircleBorder(
                    width = 2.dp,
                    color = MiuixTheme.colorScheme.primary.copy(alpha = 0.6f),
                    cornerRadius = 16.dp,
                    extension = SquircleExtension,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (preview == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = MiuixIcons.Scan,
                        contentDescription = null,
                        modifier = Modifier.size(56.dp),
                        tint = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.scanner_no_image),
                        color = MiuixTheme.colorScheme.onBackgroundVariant,
                    )
                }
            } else {
                preview?.let { currentPreview ->
                    Image(
                        bitmap = currentPreview,
                        contentDescription = stringResource(R.string.scanner_frame_description),
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit,
                    )
                }
            }
            if (showBoundingBoxes && preview != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.72f)
                        .height(96.dp)
                        .squircleBorder(
                            width = 2.dp,
                            color = MiuixTheme.colorScheme.primary,
                            cornerRadius = 8.dp,
                            extension = SquircleExtension,
                        ),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(if (preview == null) R.string.scanner_status_idle else R.string.scanner_status_image),
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onBackgroundVariant,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Button(
                onClick = { photoLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                modifier = Modifier.weight(1f),
            ) {
                Icon(MiuixIcons.Photos, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_photos))
            }
            Button(
                onClick = { cameraLauncher.launch(null) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) {
                Icon(MiuixIcons.Scan, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text(stringResource(R.string.action_camera))
            }
        }
    }
}
