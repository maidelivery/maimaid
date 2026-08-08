package net.krtl.maimaid.ui.score

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.text.TextPaint
import android.text.TextUtils
import androidx.annotation.ColorInt
import androidx.core.content.FileProvider
import androidx.core.graphics.createBitmap
import androidx.core.graphics.toColorInt
import androidx.core.graphics.withSave
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import net.krtl.maimaid.R
import net.krtl.maimaid.data.assets.CoverArtStore
import net.krtl.maimaid.data.remote.dto.RemoteVersion
import net.krtl.maimaid.domain.model.RatingEntry
import net.krtl.maimaid.domain.usecase.RatingEngine
import net.krtl.maimaid.ui.common.compactVersionName
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

private val exportJson = Json { ignoreUnknownKeys = true }

data class B50ExportPayload(
    val b35: List<RatingEntry>,
    val b15: List<RatingEntry>,
    val totalRating: Int,
    val userName: String?,
    val currentVersion: String?,
    val useFitDiff: Boolean,
    val versionsJson: String?
)

object B50ExportRenderer {
    suspend fun exportToCache(context: Context, payload: B50ExportPayload): Result<File> = runCatching {
        require(payload.b15.isNotEmpty() || payload.b35.isNotEmpty()) { "No B50 entries available" }
        withContext(Dispatchers.IO) {
            val coverBitmaps = loadCoverBitmaps(context, payload.b15 + payload.b35)
            val bitmap = renderBitmap(context, payload, coverBitmaps)
            writeBitmapToCache(context, bitmap)
        }
    }

    suspend fun exportAndShare(context: Context, payload: B50ExportPayload): Result<File> = runCatching {
        val file = exportToCache(context, payload).getOrThrow()
        withContext(Dispatchers.Main) {
            shareBitmapFile(context, file)
        }
        file
    }

    private fun loadCoverBitmaps(context: Context, entries: List<RatingEntry>): Map<String, Bitmap> {
        return entries
            .mapNotNull { it.imageName }
            .distinct()
            .mapNotNull { imageName ->
                val file = CoverArtStore.coverFile(context, imageName)
                    ?.takeIf(File::exists)
                    ?: return@mapNotNull null
                val bitmap = BitmapFactory.decodeFile(file.absolutePath) ?: return@mapNotNull null
                imageName to bitmap
            }
            .toMap()
    }

    private fun renderBitmap(
        context: Context,
        payload: B50ExportPayload,
        coverBitmaps: Map<String, Bitmap>
    ): Bitmap {
        val metrics = ExportMetrics()
        val strings = ExportStrings.from(context, payload)
        val totalHeight = (
            metrics.headerHeight +
                sectionHeight(payload.b15.size, metrics) +
                sectionHeight(payload.b35.size, metrics) +
                metrics.footerHeight
            ).roundToInt()
        val bitmap = createBitmap(metrics.totalWidth.roundToInt(), totalHeight)
        val canvas = Canvas(bitmap)
        canvas.drawColor(metrics.backgroundColor)

        var cursorY = 0f
        cursorY = drawHeader(canvas, payload, strings, metrics, cursorY)
        if (payload.b15.isNotEmpty()) {
            cursorY = drawSection(
                canvas = canvas,
                title = strings.newSectionLabel,
                subtitle = strings.b15RatingLabel,
                accentColor = metrics.newSectionAccent,
                entries = payload.b15,
                coverBitmaps = coverBitmaps,
                metrics = metrics,
                top = cursorY
            )
        }
        if (payload.b35.isNotEmpty()) {
            cursorY = drawSection(
                canvas = canvas,
                title = strings.oldSectionLabel,
                subtitle = strings.b35RatingLabel,
                accentColor = metrics.oldSectionAccent,
                entries = payload.b35,
                coverBitmaps = coverBitmaps,
                metrics = metrics,
                top = cursorY
            )
        }
        drawFooter(canvas, strings, metrics, cursorY)
        return bitmap
    }

    private fun drawHeader(
        canvas: Canvas,
        payload: B50ExportPayload,
        strings: ExportStrings,
        metrics: ExportMetrics,
        top: Float
    ): Float {
        val leftX = metrics.sectionPadding
        val rightX = metrics.totalWidth - metrics.sectionPadding
        val nameTop = top + metrics.sectionPadding + 24f
        val labelTop = nameTop + if (payload.userName.isNullOrBlank()) 0f else metrics.userNamePaint.lineHeight + 8f
        val totalTop = top + metrics.sectionPadding + 8f

        payload.userName?.takeUnless(String::isBlank)?.let { name ->
            metrics.userNamePaint.color = metrics.primaryTextColor
            drawTextTop(canvas, name, leftX, nameTop, metrics.userNamePaint)
        }
        metrics.subLabelPaint.color = metrics.secondaryTextColor
        drawTextTop(canvas, strings.ratingLabel, leftX, labelTop, metrics.subLabelPaint)

        val totalWidth = metrics.totalRatingPaint.measureText(payload.totalRating.toString())
        metrics.totalRatingPaint.shader = metrics.ratingShader(
            rating = payload.totalRating,
            x = rightX - totalWidth,
            y = totalTop,
            width = totalWidth,
            height = metrics.totalRatingPaint.lineHeight
        )
        drawTextTop(
            canvas = canvas,
            text = payload.totalRating.toString(),
            x = rightX,
            top = totalTop,
            paint = metrics.totalRatingPaint,
            align = Paint.Align.RIGHT
        )
        metrics.totalRatingPaint.shader = null

        if (payload.useFitDiff) {
            metrics.fitDiffPaint.color = metrics.fitDiffColor
            drawTextTop(
                canvas = canvas,
                text = strings.fitDiffRatingLabel,
                x = rightX,
                top = totalTop + metrics.totalRatingPaint.lineHeight + 6f,
                paint = metrics.fitDiffPaint,
                align = Paint.Align.RIGHT
            )
        }

        val pillsTop = top + metrics.headerHeight - metrics.sectionPadding - metrics.summaryPillHeight
        var pillX = leftX
        pillX += drawSummaryPill(canvas, strings.newSectionLabel, payload.b15.sumOf { it.rating }.toString(), metrics.newSectionAccent, pillX, pillsTop, metrics)
        pillX += metrics.summaryPillGap
        drawSummaryPill(canvas, strings.oldSectionLabel, payload.b35.sumOf { it.rating }.toString(), metrics.oldSectionAccent, pillX, pillsTop, metrics)
        return top + metrics.headerHeight
    }

    private fun drawSummaryPill(
        canvas: Canvas,
        label: String,
        value: String,
        accentColor: Int,
        left: Float,
        top: Float,
        metrics: ExportMetrics
    ): Float {
        val dotRadius = 8f
        val innerPaddingX = 24f
        val labelWidth = metrics.summaryLabelPaint.measureText(label)
        val valueWidth = metrics.summaryValuePaint.measureText(value)
        val width = innerPaddingX * 2 + dotRadius * 2 + 10f + labelWidth + 12f + valueWidth
        val rect = RectF(left, top, left + width, top + metrics.summaryPillHeight)
        metrics.fillPaint.color = withAlpha(accentColor, 0.12f)
        canvas.drawRoundRect(rect, metrics.summaryPillHeight / 2f, metrics.summaryPillHeight / 2f, metrics.fillPaint)
        canvas.drawCircle(left + innerPaddingX + dotRadius, rect.centerY(), dotRadius, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = accentColor })
        drawCenteredText(canvas, label, left + innerPaddingX + dotRadius * 2 + 10f, rect.centerY(), metrics.summaryLabelPaint, Paint.Align.LEFT)
        drawCenteredText(canvas, value, rect.right - innerPaddingX, rect.centerY(), metrics.summaryValuePaint.apply { color = accentColor }, Paint.Align.RIGHT)
        return width
    }

    private fun drawSection(
        canvas: Canvas,
        title: String,
        subtitle: String,
        accentColor: Int,
        entries: List<RatingEntry>,
        coverBitmaps: Map<String, Bitmap>,
        metrics: ExportMetrics,
        top: Float
    ): Float {
        val headerTop = top + metrics.sectionBlockTopPadding
        metrics.sectionTitlePaint.color = metrics.primaryTextColor
        drawTextTop(canvas, title, metrics.sectionPadding, headerTop, metrics.sectionTitlePaint)
        val subtitleX = metrics.sectionPadding + metrics.sectionTitlePaint.measureText(title) + 16f
        drawTextBaseline(
            canvas = canvas,
            text = subtitle,
            x = subtitleX,
            baseline = headerTop - metrics.sectionTitlePaint.fontMetrics.ascent + (metrics.sectionTitlePaint.fontMetrics.ascent - metrics.sectionSubtitlePaint.fontMetrics.ascent),
            paint = metrics.sectionSubtitlePaint.apply { color = accentColor }
        )

        val gridTop = headerTop + metrics.sectionTitlePaint.lineHeight + 24f
        entries.chunked(metrics.columns).forEachIndexed { rowIndex, row ->
            val rowTop = gridTop + rowIndex * (metrics.cardHeight + metrics.cardSpacing)
            row.forEachIndexed { columnIndex, entry ->
                val cardLeft = metrics.sectionPadding + columnIndex * (metrics.cardWidth + metrics.cardSpacing)
                drawCard(
                    canvas = canvas,
                    entry = entry,
                    coverBitmap = entry.imageName?.let(coverBitmaps::get),
                    metrics = metrics,
                    left = cardLeft,
                    top = rowTop
                )
            }
        }
        return top + sectionHeight(entries.size, metrics)
    }

    private fun drawCard(
        canvas: Canvas,
        entry: RatingEntry,
        coverBitmap: Bitmap?,
        metrics: ExportMetrics,
        left: Float,
        top: Float
    ) {
        val diffColor = difficultyColor(entry.diff, entry.type)
        val rect = RectF(left, top, left + metrics.cardWidth, top + metrics.cardHeight)
        metrics.fillPaint.color = withAlpha(diffColor, 0.15f)
        canvas.drawRoundRect(rect, metrics.cardCornerRadius, metrics.cardCornerRadius, metrics.fillPaint)
        metrics.strokePaint.color = withAlpha(diffColor, 0.2f)
        canvas.drawRoundRect(rect, metrics.cardCornerRadius, metrics.cardCornerRadius, metrics.strokePaint)

        val contentLeft = left + metrics.cardInnerPadding
        val contentTop = top + metrics.cardInnerPadding
        val jacketRect = RectF(
            contentLeft,
            contentTop,
            contentLeft + metrics.jacketSize,
            contentTop + metrics.jacketSize
        )
        drawCover(canvas, coverBitmap, jacketRect, metrics)

        if (entry.songId > 0) {
            val songIdText = "#${entry.songId}"
            val badgeRect = RectF(jacketRect.right - 72f, jacketRect.bottom - 30f, jacketRect.right - 6f, jacketRect.bottom - 6f)
            metrics.fillPaint.color = withAlpha(Color.BLACK, 0.6f)
            canvas.drawRoundRect(badgeRect, 4f, 4f, metrics.fillPaint)
            drawCenteredText(canvas, songIdText, badgeRect.centerX(), badgeRect.centerY(), metrics.songIdPaint, Paint.Align.CENTER)
        }

        val infoLeft = jacketRect.right + 16f
        val infoRight = rect.right - metrics.cardInnerPadding
        val titleWidth = infoRight - infoLeft
        val ellipsizedTitle = TextUtils.ellipsize(entry.songTitle, metrics.titlePaint, titleWidth, TextUtils.TruncateAt.END).toString()
        val line1Top = contentTop + 2f
        metrics.titlePaint.color = metrics.primaryTextColor
        drawTextTop(canvas, ellipsizedTitle, infoLeft, line1Top, metrics.titlePaint)

        val line2CenterY = contentTop + 50f
        val rank = RatingEngine.calculateRank(entry.achievement)
        drawCenteredText(canvas, rank, infoLeft, line2CenterY, metrics.rankPaint.apply { color = rankColor(rank) }, Paint.Align.LEFT)
        val rankWidth = metrics.rankPaint.measureText(rank)
        drawCenteredText(
            canvas,
            formatAchievement(entry.achievement),
            infoLeft + rankWidth + 12f,
            line2CenterY,
            metrics.achievementPaint.apply { color = metrics.secondaryTextColor },
            Paint.Align.LEFT
        )
        val stars = dxStars(entry.dxScore, entry.maxDxScore)
        if (stars > 0) {
            drawCenteredText(
                canvas,
                "$stars ★",
                infoRight,
                line2CenterY,
                metrics.starPaint.apply { color = metrics.starColor },
                Paint.Align.RIGHT
            )
        }

        val line3CenterY = contentTop + 82f
        val levelText = formatLevel(entry.level)
        val ratingText = entry.rating.toString()
        val arrowText = "→"
        drawCenteredText(canvas, levelText, infoLeft, line3CenterY, metrics.levelPaint.apply { color = diffColor }, Paint.Align.LEFT)
        val levelWidth = metrics.levelPaint.measureText(levelText)
        drawCenteredText(canvas, arrowText, infoLeft + levelWidth + 10f, line3CenterY, metrics.arrowPaint.apply { color = metrics.subtleTextColor }, Paint.Align.LEFT)
        val arrowWidth = metrics.arrowPaint.measureText(arrowText)
        drawCenteredText(
            canvas,
            ratingText,
            infoLeft + levelWidth + arrowWidth + 22f,
            line3CenterY,
            metrics.levelPaint.apply { color = metrics.ratingTextColor },
            Paint.Align.LEFT
        )
        if (entry.maxDxScore > 0) {
            drawCenteredText(
                canvas,
                "${entry.dxScore}/${entry.maxDxScore}",
                infoRight,
                line3CenterY,
                metrics.dxScorePaint.apply { color = metrics.secondaryTextColor },
                Paint.Align.RIGHT
            )
        }

        var badgeLeft = infoLeft
        val badgesCenterY = contentTop + 112f
        badgeLeft += drawBadge(canvas, entry.type.uppercase(Locale.ROOT), typeBadgeColor(entry.type), badgeLeft, badgesCenterY, metrics)
        entry.fc?.takeUnless(String::isBlank)?.let { fc ->
            badgeLeft += 6f
            badgeLeft += drawBadge(canvas, normalizeFc(fc), fcColor(fc), badgeLeft, badgesCenterY, metrics)
        }
        entry.fs?.takeUnless(String::isBlank)?.let { fs ->
            badgeLeft += 6f
            drawBadge(canvas, normalizeFs(fs), fsColor(fs), badgeLeft, badgesCenterY, metrics)
        }
    }

    private fun drawCover(canvas: Canvas, bitmap: Bitmap?, rect: RectF, metrics: ExportMetrics) {
        if (bitmap == null) {
            metrics.fillPaint.color = metrics.emptyCardColor
            canvas.drawRoundRect(rect, metrics.jacketCornerRadius, metrics.jacketCornerRadius, metrics.fillPaint)
            drawCenteredText(canvas, "♪", rect.centerX(), rect.centerY(), metrics.placeholderPaint, Paint.Align.CENTER)
            return
        }
        canvas.withSave {
            val path = Path().apply {
                addRoundRect(
                    rect,
                    metrics.jacketCornerRadius,
                    metrics.jacketCornerRadius,
                    Path.Direction.CW
                )
            }
            canvas.clipPath(path)
            val scale =
                max(rect.width() / bitmap.width.toFloat(), rect.height() / bitmap.height.toFloat())
            val drawWidth = bitmap.width * scale
            val drawHeight = bitmap.height * scale
            val dx = rect.left + (rect.width() - drawWidth) / 2f
            val dy = rect.top + (rect.height() - drawHeight) / 2f
            canvas.drawBitmap(bitmap, null, RectF(dx, dy, dx + drawWidth, dy + drawHeight), null)
        }
    }

    private fun drawFooter(canvas: Canvas, strings: ExportStrings, metrics: ExportMetrics, top: Float) {
        val version = strings.versionAbbreviation
        if (!version.isNullOrBlank()) {
            val footerCenterY = top + 32f
            val versionWidth = metrics.footerTextPaint.measureText(version)
            val badgeWidth = strings.fitDiffEnabledLabel?.let { measureBadgeWidth(it, metrics) } ?: 0f
            val blockWidth = versionWidth + if (badgeWidth > 0f) 12f + badgeWidth else 0f
            var footerLeft = (metrics.totalWidth - blockWidth) / 2f
            drawCenteredText(canvas,
                version, footerLeft, footerCenterY, metrics.footerTextPaint.apply { color = metrics.secondaryTextColor }, Paint.Align.LEFT)
            footerLeft += versionWidth
            if (strings.fitDiffEnabledLabel != null) {
                footerLeft += 12f
                drawBadge(canvas, strings.fitDiffEnabledLabel, metrics.fitDiffColor, footerLeft, footerCenterY, metrics)
            }
        }
        drawCenteredText(
            canvas = canvas,
            text = strings.watermark,
            x = metrics.totalWidth / 2f,
            centerY = top + metrics.footerHeight - 28f,
            paint = metrics.watermarkPaint.apply { color = metrics.subtleTextColor },
            align = Paint.Align.CENTER
        )
    }

    fun shareBitmapFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, context.getString(R.string.b50_export_share_chooser_title)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }

    suspend fun saveBitmapToGallery(context: Context, file: File): Result<Uri> = runCatching {
        withContext(Dispatchers.IO) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, file.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(
                    MediaStore.Images.Media.RELATIVE_PATH,
                    "${Environment.DIRECTORY_PICTURES}/maimaid"
                )
                put(MediaStore.Images.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Unable to create gallery item")
            try {
                resolver.openOutputStream(uri)?.use { output ->
                    file.inputStream().use { input -> input.copyTo(output) }
                } ?: error("Unable to open gallery output stream")
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri
            } catch (error: Throwable) {
                resolver.delete(uri, null, null)
                throw error
            }
        }
    }

    suspend fun writeBitmapToUri(context: Context, file: File, destination: Uri): Result<Unit> = runCatching {
        withContext(Dispatchers.IO) {
            context.contentResolver.openOutputStream(destination)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: error("Unable to open destination")
        }
    }

    fun createExportFileName(): String {
        val formatter = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss", Locale.US)
        return "maimaid_b50_${LocalDateTime.now().format(formatter)}.png"
    }

    private fun writeBitmapToCache(context: Context, bitmap: Bitmap): File {
        val directory = File(context.cacheDir, "shared_images").apply { mkdirs() }
        val file = File(directory, createExportFileName())
        FileOutputStream(file).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return file
    }
}

private class ExportMetrics(scale: Float = 2f) {
    val columns = 5
    val cardWidth = 220f * scale
    val cardSpacing = 8f * scale
    val sectionPadding = 24f * scale
    val totalWidth = columns * cardWidth + (columns - 1) * cardSpacing + sectionPadding * 2
    val headerHeight = 188f * scale
    val footerHeight = 72f * scale
    val cardHeight = 74f * scale
    val jacketSize = 62f * scale
    val cardInnerPadding = 6f * scale
    val cardCornerRadius = 6f * scale
    val jacketCornerRadius = 4f * scale
    val sectionBlockTopPadding = 16f * scale
    val sectionBlockBottomPadding = 16f * scale
    val sectionHeaderGap = 12f * scale
    val summaryPillHeight = 18f * scale
    val summaryPillGap = 12f * scale

    @ColorInt val backgroundColor = Color.WHITE
    @ColorInt val primaryTextColor = Color.BLACK
    @ColorInt val secondaryTextColor = Color.argb((0.6f * 255).roundToInt(), 0, 0, 0)
    @ColorInt val subtleTextColor = Color.argb((0.3f * 255).roundToInt(), 0, 0, 0)
    @ColorInt val emptyCardColor = Color.argb((0.05f * 255).roundToInt(), 0, 0, 0)
    @ColorInt val ratingTextColor = "#C5A000".toColorInt()
    @ColorInt val fitDiffColor = "#F59E0B".toColorInt()
    @ColorInt val newSectionAccent = "#FF6B6B".toColorInt()
    @ColorInt val oldSectionAccent = "#4ECDC4".toColorInt()
    @ColorInt val starColor = "#FACC15".toColorInt()

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    val userNamePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        textSize = 24f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val subLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textSize = 14f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val totalRatingPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 56f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val fitDiffPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val summaryLabelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = secondaryTextColor
        textSize = 13f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val summaryValuePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 13f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val sectionTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        textSize = 20f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val sectionSubtitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 14f * scale
        typeface = Typeface.MONOSPACE
    }
    val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = primaryTextColor
        textSize = 11f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val rankPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val achievementPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f * scale
        typeface = Typeface.MONOSPACE
    }
    val starPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val levelPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 9f * scale
        typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
    }
    val arrowPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f * scale
    }
    val dxScorePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f * scale
        typeface = Typeface.MONOSPACE
    }
    val badgePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 7f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val footerTextPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 11f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val watermarkPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f * scale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }
    val placeholderPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 20f * scale
        color = subtleTextColor
        textAlign = Paint.Align.CENTER
    }
    val songIdPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 8f * scale
        color = Color.WHITE
        typeface = Typeface.MONOSPACE
        textAlign = Paint.Align.CENTER
    }


    fun ratingShader(rating: Int, x: Float, y: Float, width: Float, height: Float): Shader {
        val colors = when {
            rating >= 15000 -> intArrayOf(
                "#FF5E5E".toColorInt(),
                "#FFBA5E".toColorInt(),
                "#FFF75E".toColorInt(),
                "#5EFF5E".toColorInt(),
                "#5EBAFF".toColorInt(),
                "#BA5EFF".toColorInt(),
                "#FF5EBA".toColorInt()
            )

            rating >= 14500 -> intArrayOf(
                "#D3D3D3".toColorInt(),
                "#FFFFFF".toColorInt(),
                "#D3D3D3".toColorInt()
            )

            rating >= 14000 -> intArrayOf(
                "#FFD700".toColorInt(),
                "#FFA500".toColorInt()
            )

            else -> intArrayOf(ratingColor(rating), ratingColor(rating))
        }
        return LinearGradient(x, y, x + width, y + height, colors, null, Shader.TileMode.CLAMP)
    }
}

private data class ExportStrings(
    val ratingLabel: String,
    val newSectionLabel: String,
    val oldSectionLabel: String,
    val b15RatingLabel: String,
    val b35RatingLabel: String,
    val watermark: String,
    val fitDiffRatingLabel: String,
    val fitDiffEnabledLabel: String?,
    val versionAbbreviation: String?
) {
    companion object {
        fun from(context: Context, payload: B50ExportPayload): ExportStrings {
            val newSection = context.getString(R.string.b50_export_section_new, payload.b15.size)
            val oldSection = context.getString(R.string.b50_export_section_old, payload.b35.size)
            return ExportStrings(
                ratingLabel = context.getString(R.string.b50_export_rating),
                newSectionLabel = newSection,
                oldSectionLabel = oldSection,
                b15RatingLabel = context.getString(R.string.b50_export_section_rating_total, payload.b15.sumOf { it.rating }),
                b35RatingLabel = context.getString(R.string.b50_export_section_rating_total, payload.b35.sumOf { it.rating }),
                watermark = context.getString(R.string.b50_export_watermark),
                fitDiffRatingLabel = context.getString(R.string.b50_export_fit_diff_rating),
                fitDiffEnabledLabel = if (payload.useFitDiff) context.getString(R.string.b50_export_fit_diff) else null,
                versionAbbreviation = payload.currentVersion?.takeUnless(String::isBlank)?.let {
                    versionAbbreviation(version = it, versionsJson = payload.versionsJson)
                }
            )
        }
    }
}

private fun sectionHeight(entryCount: Int, metrics: ExportMetrics): Float {
    if (entryCount <= 0) return 0f
    val rows = ceil(entryCount / metrics.columns.toDouble()).toInt()
    return metrics.sectionBlockTopPadding +
        metrics.sectionTitlePaint.lineHeight +
        metrics.sectionHeaderGap +
        rows * metrics.cardHeight +
        max(rows - 1, 0) * metrics.cardSpacing +
        metrics.sectionBlockBottomPadding
}

private fun drawTextTop(
    canvas: Canvas,
    text: String,
    x: Float,
    top: Float,
    paint: TextPaint,
    align: Paint.Align = Paint.Align.LEFT
) {
    paint.textAlign = align
    val baseline = top - paint.fontMetrics.ascent
    canvas.drawText(text, x, baseline, paint)
}

private fun drawTextBaseline(canvas: Canvas, text: String, x: Float, baseline: Float, paint: TextPaint) {
    paint.textAlign = Paint.Align.LEFT
    canvas.drawText(text, x, baseline, paint)
}

private fun drawCenteredText(
    canvas: Canvas,
    text: String,
    x: Float,
    centerY: Float,
    paint: TextPaint,
    align: Paint.Align
) {
    paint.textAlign = align
    val baseline = centerY - (paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    canvas.drawText(text, x, baseline, paint)
}

private fun drawBadge(
    canvas: Canvas,
    text: String,
    @ColorInt color: Int,
    left: Float,
    centerY: Float,
    metrics: ExportMetrics
): Float {
    val horizontalPadding = 8f
    val verticalPadding = 4f
    val paint = metrics.badgePaint
    val width = paint.measureText(text) + horizontalPadding * 2
    val height = paint.lineHeight + verticalPadding * 2
    val rect = RectF(left, centerY - height / 2f, left + width, centerY + height / 2f)
    metrics.fillPaint.color = color
    canvas.drawRoundRect(rect, 4f, 4f, metrics.fillPaint)
    drawCenteredText(canvas, text, rect.centerX(), rect.centerY(), paint, Paint.Align.CENTER)
    return width
}

private fun measureBadgeWidth(text: String, metrics: ExportMetrics): Float = metrics.badgePaint.measureText(text) + 16f

private val TextPaint.lineHeight: Float
    get() = fontMetrics.descent - fontMetrics.ascent

private fun withAlpha(@ColorInt color: Int, alphaFraction: Float): Int {
    val alpha = (alphaFraction.coerceIn(0f, 1f) * 255).roundToInt()
    return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color))
}

@SuppressLint("UseKtx")
private fun difficultyColor(difficulty: String, type: String?): Int = when {
    difficulty.equals("basic", true) -> Color.parseColor("#36BF63")
    difficulty.equals("advanced", true) -> Color.parseColor("#FCA13B")
    difficulty.equals("expert", true) -> "#F7536A".toColorInt()
    difficulty.equals("remaster", true) -> Color.parseColor("#E3BDFC")
    difficulty.equals("master", true) -> Color.parseColor("#A34EE4")
    type?.contains("utage", true) == true -> Color.parseColor("#EC48E9")
    else -> Color.parseColor("#E35D6A")
}

@SuppressLint("UseKtx")
private fun typeBadgeColor(type: String): Int = when {
    type.equals("dx", true) -> Color.parseColor("#F97316")
    type.contains("utage", true) -> Color.parseColor("#EC4899")
    else -> "#3B82F6".toColorInt()
}

@SuppressLint("UseKtx")
private fun rankColor(rank: String): Int = when (rank) {
    "SSS+", "SSS" -> Color.parseColor("#FFD700")
    "SS+", "SS" -> "#FFBF00".toColorInt()
    "S+", "S" -> "#FF9900".toColorInt()
    "AAA" -> Color.parseColor("#CC99FF")
    "AA" -> Color.parseColor("#99CCFF")
    "A" -> Color.parseColor("#80E680")
    else -> Color.parseColor("#666666")
}

@SuppressLint("UseKtx")
private fun ratingColor(rating: Int): Int = when {
    rating >= 15000 -> Color.parseColor("#FF6100")
    rating >= 14500 -> "#E5E4E2".toColorInt()
    rating >= 14000 -> "#FFD700".toColorInt()
    rating >= 13000 -> "#C0C0C0".toColorInt()
    rating >= 12000 -> "#CD7F32".toColorInt()
    rating >= 10000 -> "#D084FF".toColorInt()
    rating >= 7000 -> Color.parseColor("#FF5E5E")
    rating >= 4000 -> Color.parseColor("#FFD400")
    rating >= 2000 -> Color.parseColor("#46D246")
    rating >= 1000 -> Color.parseColor("#56A6FF")
    else -> Color.GRAY
}

private fun fcColor(fc: String): Int = when {
    fc.contains("ap", ignoreCase = true) -> "#F59E0B".toColorInt()
    fc.contains("fc", ignoreCase = true) -> "#22C55E".toColorInt()
    else -> "#6B7280".toColorInt()
}

@SuppressLint("UseKtx")
private fun fsColor(fs: String): Int = when {
    fs.contains("fsd", ignoreCase = true) -> "#A855F7".toColorInt()
    fs.contains("fs", ignoreCase = true) || fs.contains("sync", ignoreCase = true) -> Color.parseColor("#3B82F6")
    else -> Color.parseColor("#6B7280")
}

private fun normalizeFc(fc: String): String = when (fc.lowercase(Locale.ROOT)) {
    "app" -> "AP+"
    "ap" -> "AP"
    "fcp" -> "FC+"
    "fc" -> "FC"
    else -> fc.uppercase(Locale.ROOT)
}

private fun normalizeFs(fs: String): String = when (fs.lowercase(Locale.ROOT)) {
    "fsdp" -> "FDX+"
    "fsd" -> "FDX"
    "fsp" -> "FS+"
    "fs" -> "FS"
    else -> fs.uppercase(Locale.ROOT)
}

private fun dxStars(dxScore: Int, maxDxScore: Int): Int {
    if (dxScore <= 0 || maxDxScore <= 0) return 0
    val ratio = dxScore.toDouble() / maxDxScore.toDouble()
    return when {
        ratio >= 0.97 -> 5
        ratio >= 0.95 -> 4
        ratio >= 0.93 -> 3
        ratio >= 0.90 -> 2
        ratio >= 0.85 -> 1
        else -> 0
    }
}

private fun formatAchievement(value: Double): String = String.format(Locale.getDefault(), "%.4f%%", value)

private fun formatLevel(value: Double): String = String.format(Locale.getDefault(), "%.1f", value)

private fun versionAbbreviation(version: String, versionsJson: String?): String {
    val versions = versionsJson
        ?.let { json ->
            runCatching {
                exportJson.decodeFromString(ListSerializer(RemoteVersion.serializer()), json)
            }.getOrNull()
        }
        .orEmpty()
    versions.firstOrNull { it.version == version }?.let { return it.abbr }
    versions
        .filter { it.version.contains(version, ignoreCase = true) || version.contains(it.version, ignoreCase = true) }
        .maxByOrNull { it.version.length }
        ?.let { return it.abbr }
    return compactVersionName(version)
}
