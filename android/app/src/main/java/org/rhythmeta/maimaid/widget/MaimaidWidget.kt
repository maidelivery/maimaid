package org.rhythmeta.maimaid.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.glance.appwidget.cornerRadius
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import org.rhythmeta.maimaid.MainActivity
import org.rhythmeta.maimaid.MaimaidApplication
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.Best50ConstantMode
import org.rhythmeta.maimaid.core.network.ImageRequestHeaders
import java.util.Locale
import androidx.core.graphics.scale

class MaimaidWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(
            DpSize(110.dp, 110.dp),
            DpSize(250.dp, 110.dp),
            DpSize(250.dp, 180.dp),
        ),
    )

    @RequiresApi(Build.VERSION_CODES.S)
		override suspend fun provideGlance(context: Context, id: GlanceId) {
        Log.d(TAG, "provideGlance start id=$id")
        try {
            val container = (context.applicationContext as MaimaidApplication).container
            val profile = container.profileRepository.activeProfile.first()
            Log.d(TAG, "provideGlance profile=${profile?.id}")
            val best50 = container.best50Repository.observeBest50(
                b35CountOverride = 35,
                b15CountOverride = 15,
                constantMode = Best50ConstantMode.Server,
            ).first()
            Log.d(TAG, "provideGlance best50=${best50.b35.size + best50.b15.size}")
            val snapshot = WidgetSnapshotBuilder.build(profile, best50, System.currentTimeMillis())
            val accentArgb = widgetAccentArgb(context, container)
            val themedSnapshot = snapshot.copy(accentArgb = accentArgb)
            val images = loadWidgetImages(container, themedSnapshot)
            Log.d(TAG, "provideGlance images avatar=${images.avatar != null} covers=${images.covers.size}")
            provideContent {
                MaimaidWidgetContent(themedSnapshot, images)
            }
            Log.d(TAG, "provideGlance content provided id=$id")
        } catch (throwable: Throwable) {
            Log.e(TAG, "provideGlance failed id=$id", throwable)
            throw throwable
        }
    }

    private companion object {
        const val TAG = "MaimaidWidget"
    }
}

class MaimaidWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MaimaidWidget()
}

private object WidgetColors {
    @SuppressLint("RestrictedApi")
    val primary = ColorProvider(R.color.widget_primary)
	  @SuppressLint("RestrictedApi")
    val secondary = ColorProvider(R.color.widget_secondary)
    @SuppressLint("RestrictedApi")
    val surface = ColorProvider(R.color.widget_surface)
}

const val WidgetDestinationExtra = "maimaid_widget_destination"
private val widgetDestinationKey = ActionParameters.Key<String>(WidgetDestinationExtra)

private data class WidgetImages(
    val avatar: Bitmap? = null,
    val covers: Map<String, Bitmap> = emptyMap(),
)

@RequiresApi(Build.VERSION_CODES.S)
private suspend fun widgetAccentArgb(
    context: Context,
    container: org.rhythmeta.maimaid.core.AppContainer,
): Int {
    val settings = container.appPreferencesRepository.themeSettings.first()
    if (settings.keyColor != 0) return settings.keyColor
    val dark = settings.colorMode.isDark || (settings.colorMode.isSystem &&
        (context.resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)
    // The generated light/dark Monet primary uses the 600/200 tonal pair.
    val resource = if (dark) android.R.color.system_accent1_200 else android.R.color.system_accent1_600
    return runCatching { context.getColor(resource) }.getOrDefault(context.getColor(R.color.widget_accent))
}

private suspend fun loadWidgetImages(
    container: org.rhythmeta.maimaid.core.AppContainer,
    snapshot: WidgetSnapshot,
): WidgetImages = withContext(Dispatchers.IO) {
    val avatarFile = snapshot.avatarPath
        ?.takeIf(String::isNotBlank)
        ?.let(::File)
        ?.takeIf(File::isFile)
        ?: container.presetAvatarRepository.imageFileFor(snapshot.avatarUrl)
    val avatar = avatarFile?.let(::decodeWidgetBitmap)
        ?: snapshot.avatarUrl
            ?.takeUnless(org.rhythmeta.maimaid.core.data.PresetAvatarUrl::isPreset)
            ?.let(::fetchRemoteWidgetBitmap)
            ?.also { bitmap -> cacheRemoteAvatar(container, snapshot, bitmap) }
    val covers = snapshot.topScores
        .take(16)
        .mapNotNull { score ->
            container.coverImageStore.fileFor(score.imageName)
                ?.let(::decodeWidgetBitmap)
                ?.let { score.imageName to it }
        }
        .toMap()
    WidgetImages(avatar = avatar, covers = covers)
}

private suspend fun cacheRemoteAvatar(
    container: org.rhythmeta.maimaid.core.AppContainer,
    snapshot: WidgetSnapshot,
    bitmap: Bitmap,
) {
    val profileId = snapshot.profileId ?: return
    val current = container.database.profileDao().activeProfile() ?: return
    val currentAvatarPath = current.avatarPath?.let(::File)
    if (
        current.id != profileId ||
        current.avatarUrl != snapshot.avatarUrl ||
        currentAvatarPath?.isFile == true
    ) return
    val bytes = ByteArrayOutputStream().use { output ->
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) return
        output.toByteArray()
    }
    val path = container.profileAvatarStore.saveRemote(bytes, profileId) ?: return
    container.profileRepository.save(current.copy(avatarPath = path))
}

private fun fetchRemoteWidgetBitmap(urlString: String): Bitmap? {
    val url = runCatching { URL(urlString) }.getOrNull() ?: return null
    if (url.protocol != "https" && url.protocol != "http") return null
    val connection = (url.openConnection() as? HttpURLConnection) ?: return null
    return runCatching {
        connection.connectTimeout = 10_000
        connection.readTimeout = 15_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("Accept", ImageRequestHeaders.ACCEPT)
        connection.connect()
        if (connection.responseCode !in 200..299) return@runCatching null
        connection.inputStream.use { stream ->
            decodeWidgetBitmap(stream)
        }
    }.getOrNull().also {
        connection.disconnect()
    }
}

private fun decodeWidgetBitmap(stream: java.io.InputStream): Bitmap? {
    val bitmap = BitmapFactory.decodeStream(
        stream,
        null,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        },
    ) ?: return null
    return normalizeWidgetBitmap(bitmap)
}

private fun decodeWidgetBitmap(file: File): Bitmap? {
    val bitmap = BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inScaled = false
        },
    ) ?: return null
    return normalizeWidgetBitmap(bitmap)
}

private fun normalizeWidgetBitmap(bitmap: Bitmap): Bitmap? {
    val normalized = if (bitmap.config == Bitmap.Config.ARGB_8888) {
        bitmap
    } else {
        bitmap.copy(Bitmap.Config.ARGB_8888, false)?.also { bitmap.recycle() } ?: return null
    }
    val maxDimension = maxOf(normalized.width, normalized.height)
    if (maxDimension <= 256) return normalized
    val scale = 256f / maxDimension
    return normalized.scale(
	    (normalized.width * scale).toInt().coerceAtLeast(1),
	    (normalized.height * scale).toInt().coerceAtLeast(1),
    ).also { normalized.recycle() }
}

@Composable
private fun MaimaidWidgetContent(snapshot: WidgetSnapshot, images: WidgetImages) {
    val size = LocalSize.current
    val isLarge = size.height >= 160.dp
    val isMedium = size.width >= 180.dp
    val contentPadding = if (isLarge) 16.dp else 12.dp
    val modifier = GlanceModifier
        .fillMaxSize()
        .background(WidgetColors.surface)
        .cornerRadius(20.dp)
        .padding(contentPadding)

    when {
        snapshot.status == WidgetSnapshot.Status.NoProfile -> EmptyWidget(
            modifier.clickable(actionFor(WidgetDestination.Home)),
            R.string.widget_no_profile,
        )
        snapshot.status == WidgetSnapshot.Status.NoScores -> EmptyWidget(
            modifier.clickable(actionFor(WidgetDestination.Home)),
            R.string.widget_no_scores,
					images,
        )
        isLarge -> LargeWidget(
            modifier.clickable(actionFor(WidgetDestination.Best50)),
            snapshot,
            images,
        )
        isMedium -> MediumWidget(
            modifier.clickable(actionFor(WidgetDestination.Home)),
            snapshot,
            images,
            pinStatsToBottom = true,
        )
        else -> SmallWidget(
            modifier.clickable(actionFor(WidgetDestination.Home)),
            snapshot,
            images,
        )
    }
}

private enum class WidgetDestination { Home, Best50 }

private fun actionFor(destination: WidgetDestination): Action {
    return actionStartActivity<MainActivity>(
        actionParametersOf(widgetDestinationKey to destination.name.lowercase()),
    )
}

@Composable
private fun EmptyWidget(
    modifier: GlanceModifier,
    message: Int,
    images: WidgetImages = WidgetImages(),
) {
    val context = LocalContext.current
    Column(modifier, verticalAlignment = Alignment.CenterVertically) {
        WidgetAvatar(images.avatar, 42.dp)
        Text(text = "maimaid", style = titleStyle())
        Spacer(GlanceModifier.height(8.dp))
        Text(text = context.getString(message), style = secondaryStyle())
    }
}

@Composable
private fun SmallWidget(modifier: GlanceModifier, snapshot: WidgetSnapshot, images: WidgetImages) {
    val context = LocalContext.current
    Column(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WidgetAvatar(images.avatar, 38.dp)
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(text = snapshot.profileName.orEmpty(), style = titleStyle(), maxLines = 1)
                Text(text = serverLabel(context, snapshot.server), style = secondaryStyle())
            }
        }
        Spacer(GlanceModifier.defaultWeight())
        RatingText(snapshot.displayRating)
        Text(text = context.getString(R.string.widget_dx_rating), style = secondaryStyle())
    }
}

@Composable
private fun MediumWidget(
    modifier: GlanceModifier,
    snapshot: WidgetSnapshot,
    images: WidgetImages,
    pinStatsToBottom: Boolean = false,
) {
    val context = LocalContext.current
    Column(modifier) {
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            WidgetAvatar(images.avatar, 42.dp)
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(snapshot.profileName.orEmpty(), style = titleStyle(), maxLines = 1)
                Text(serverLabel(context, snapshot.server), style = secondaryStyle())
            }
            Column(horizontalAlignment = Alignment.End) {
                RatingText(snapshot.displayRating)
                Text(context.getString(R.string.widget_dx_rating), style = secondaryStyle())
            }
        }
        if (pinStatsToBottom) {
            Spacer(GlanceModifier.defaultWeight())
        } else {
            Spacer(GlanceModifier.height(8.dp))
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            Stat(context.getString(R.string.widget_b35), snapshot.b35Rating)
            Spacer(GlanceModifier.width(12.dp))
            Stat(context.getString(R.string.widget_b15), snapshot.b15Rating)
        }
    }
}

@Composable
private fun LargeWidget(modifier: GlanceModifier, snapshot: WidgetSnapshot, images: WidgetImages) {
    val context = LocalContext.current
    Column(modifier) {
        MediumWidget(
            GlanceModifier
                .fillMaxWidth()
                .clickable(actionFor(WidgetDestination.Home)),
            snapshot,
            images,
        )
        Spacer(GlanceModifier.height(14.dp))
        Column(
            modifier = GlanceModifier
                .fillMaxWidth()
                .defaultWeight()
                .clickable(actionFor(WidgetDestination.Best50)),
        ) {
            Text(
                text = context.getString(R.string.widget_personal_best),
                style = sectionStyle(snapshot.accentArgb),
            )
            Spacer(GlanceModifier.height(6.dp))
            val visibleScores = snapshot.topScores.take(3)
            visibleScores.forEachIndexed { index, score ->
                Row(
                    modifier = GlanceModifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    images.covers[score.imageName]?.let { bitmap ->
                        Image(
                            provider = ImageProvider(bitmap),
                            contentDescription = null,
                            modifier = GlanceModifier.size(34.dp).cornerRadius(7.dp),
                        )
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        Text(score.title, style = bodyStyle(), maxLines = 1)
                        Row {
                            Text(score.difficulty.displayDifficulty(), style = difficultyStyle(score.difficulty))
                            Text(" · ${score.achievement.formatAchievement()}", style = secondaryStyle())
                        }
                    }
                    ScoreRatingText(score.rating)
                }
                if (index < visibleScores.lastIndex) Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun WidgetAvatar(bitmap: Bitmap?, dimension: androidx.compose.ui.unit.Dp) {
    bitmap?.let {
        Image(
            provider = ImageProvider(it),
            contentDescription = null,
            modifier = GlanceModifier.size(dimension).cornerRadius(dimension / 2),
        )
    }
}

@Composable
private fun RowScope.Stat(label: String, value: Int) {
    Column(modifier = GlanceModifier.defaultWeight()) {
        Text(value.toString(), style = statValueStyle())
        Text(label, style = statLabelStyle())
    }
}

private fun titleStyle() = TextStyle(color = WidgetColors.primary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
@SuppressLint("RestrictedApi")
private fun sectionStyle(accentArgb: Int) = TextStyle(
    color = ColorProvider(Color(accentArgb)),
    fontSize = 14.sp,
    fontWeight = FontWeight.Bold,
)
private fun bodyStyle() = TextStyle(color = WidgetColors.primary, fontSize = 13.sp)
private fun secondaryStyle() = TextStyle(color = WidgetColors.secondary, fontSize = 11.sp)
private fun statValueStyle() = TextStyle(
    color = WidgetColors.primary,
    fontSize = 18.sp,
    fontWeight = FontWeight.Bold,
)
private fun statLabelStyle() = TextStyle(
    color = WidgetColors.secondary,
    fontSize = 13.sp,
    fontWeight = FontWeight.Bold,
)
private fun difficultyStyle(difficulty: String) = TextStyle(color = difficultyColorProvider(difficulty), fontSize = 11.sp)

@SuppressLint("RestrictedApi")
@Composable
private fun ScoreRatingText(rating: Int) {
    Text(
        rating.toString(),
        style = TextStyle(
            color = ColorProvider(R.color.widget_score_rating),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@Composable
private fun RatingText(rating: Int, compact: Boolean = false) {
    Text(
        rating.toString(),
        style = TextStyle(
            color = ratingColorProvider(rating),
            fontSize = if (compact) 13.sp else 28.sp,
            fontWeight = FontWeight.Bold,
        ),
    )
}

@SuppressLint("RestrictedApi")
private fun ratingColorProvider(rating: Int): ColorProvider = ColorProvider(
    when {
        rating >= 15_000 -> R.color.widget_rating_rainbow
        rating >= 14_500 -> R.color.widget_rating_platinum
        rating >= 14_000 -> R.color.widget_rating_gold
        rating >= 13_000 -> R.color.widget_rating_silver
        rating >= 12_000 -> R.color.widget_rating_bronze
        rating >= 10_000 -> R.color.widget_rating_purple
        rating >= 7_000 -> R.color.widget_rating_red
        rating >= 4_000 -> R.color.widget_rating_yellow
        rating >= 2_000 -> R.color.widget_rating_green
        rating >= 1_000 -> R.color.widget_rating_blue
        else -> R.color.widget_rating_gray
    },
)

@SuppressLint("RestrictedApi")
private fun difficultyColorProvider(difficulty: String): ColorProvider = ColorProvider(
    when {
        difficulty.contains("basic", true) -> R.color.widget_difficulty_basic
        difficulty.contains("advanced", true) -> R.color.widget_difficulty_advanced
        difficulty.contains("expert", true) -> R.color.widget_difficulty_expert
        difficulty.contains("remaster", true) -> R.color.widget_difficulty_remaster
        difficulty.contains("master", true) -> R.color.widget_difficulty_master
        else -> R.color.widget_difficulty_unknown
    },
)

private fun Double.formatAchievement(): String = "%.4f%%".format(Locale.ROOT, this)
private fun String.displayDifficulty(): String = when {
    equals("remaster", true) -> "RE:MASTER"
    else -> uppercase(Locale.ROOT)
}
private fun serverLabel(context: Context, server: String?): String = when (server?.lowercase()) {
    "cn" -> context.getString(R.string.server_cn)
    "intl", "us", "usa" -> context.getString(R.string.server_intl)
    else -> context.getString(R.string.server_jp)
}
