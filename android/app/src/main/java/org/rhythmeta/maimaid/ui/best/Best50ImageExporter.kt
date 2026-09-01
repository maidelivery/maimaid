package org.rhythmeta.maimaid.ui.best

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import androidx.annotation.ColorInt
import java.io.File
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rhythmeta.maimaid.R
import org.rhythmeta.maimaid.core.data.Best50State
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.data.RatingUtils
import org.rhythmeta.maimaid.core.data.ScoreRules
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

internal object Best50ImageExporter {
    suspend fun renderToCache(
        context: Context,
        state: Best50State,
        userName: String?,
        version: String?,
        coverImageStore: CoverImageStore,
        darkTheme: Boolean,
    ): File? = withContext(Dispatchers.Default) {
        runCatching {
            val labels = Labels(
                rating = context.getString(R.string.best50_rating),
                newSection = context.getString(R.string.best50_new_section, state.b15.size),
                oldSection = context.getString(R.string.best50_old_section, state.b35.size),
                sectionRating = { rating ->
                    context.getString(R.string.best50_export_section_rating, rating)
                },
                watermark = context.getString(R.string.best50_export_watermark),
            )
            val bitmap = Renderer(
                state = state,
                userName = userName,
                version = version,
                coverImageStore = coverImageStore,
                darkTheme = darkTheme,
                labels = labels,
            ).render()
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "best50.png").also { destination ->
                    destination.outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    bitmap.recycle()
                }
            }
        }.getOrNull()
    }

    private data class Labels(
        val rating: String,
        val newSection: String,
        val oldSection: String,
        val sectionRating: (Int) -> String,
        val watermark: String,
    )

    private class Renderer(
        private val state: Best50State,
        private val userName: String?,
        private val version: String?,
        private val coverImageStore: CoverImageStore,
        darkTheme: Boolean,
        private val labels: Labels,
    ) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        private val palette = Palette(darkTheme)

        fun render(): Bitmap {
            val sections = listOf(
                ExportSection(labels.newSection, NewAccent, state.b15),
                ExportSection(labels.oldSection, OldAccent, state.b35),
            ).filter { it.entries.isNotEmpty() }
            val logicalHeight = HeaderHeight +
                sections.sumOf { sectionHeight(it.entries.size) } +
                FooterHeight
            val bitmap = createBitmap(CanvasWidth * OutputScale, logicalHeight * OutputScale)
            val canvas = Canvas(bitmap)
            canvas.drawColor(palette.background)
            canvas.scale(OutputScale.toFloat(), OutputScale.toFloat())
            drawHeader(canvas)
            var top = HeaderHeight
            sections.forEach { section ->
                drawSection(canvas, section, top)
                top += sectionHeight(section.entries.size)
            }
            drawFooter(canvas, top)
            return bitmap
        }

        private fun drawHeader(canvas: Canvas) {
            val name = userName?.trim().orEmpty()
            val labelY: Float
            if (name.isNotEmpty()) {
                drawText(canvas, name, SectionPadding.toFloat(), 64f, 24f, palette.primary, true)
                labelY = 95f
            } else {
                labelY = 72f
            }
            drawText(canvas, labels.rating, SectionPadding.toFloat(), labelY, 14f, palette.secondary)
            drawRating(canvas, state.total)

            var pillLeft = SectionPadding.toFloat()
            pillLeft += drawSummaryPill(
                canvas = canvas,
                left = pillLeft,
                top = 126f,
                label = labels.newSection,
                value = state.b15.sumOf(RatingUtils.Entry::rating).toString(),
                accent = NewAccent,
            ) + 12f
            drawSummaryPill(
                canvas = canvas,
                left = pillLeft,
                top = 126f,
                label = labels.oldSection,
                value = state.b35.sumOf(RatingUtils.Entry::rating).toString(),
                accent = OldAccent,
            )
        }

        private fun drawRating(canvas: Canvas, rating: Int) {
            setText(56f, Color.WHITE, bold = true)
            val text = rating.toString()
            val width = paint.measureText(text)
            val left = CanvasWidth - SectionPadding - width
            paint.shader = ratingShader(rating, left, 30f, left + width, 98f)
            canvas.drawText(text, left, 84f, paint)
            paint.shader = null
        }

        private fun drawSummaryPill(
            canvas: Canvas,
            left: Float,
            top: Float,
            label: String,
            value: String,
            @ColorInt accent: Int,
        ): Float {
            setText(13f, palette.secondary)
            val labelWidth = paint.measureText(label)
            setText(13f, accent, bold = true)
            val valueWidth = paint.measureText(value)
            val width = 12f + 8f + 6f + labelWidth + 6f + valueWidth + 12f
            paint.color = withAlpha(accent, 0.12f)
            canvas.drawRoundRect(RectF(left, top, left + width, top + 32f), 16f, 16f, paint)
            paint.color = accent
            canvas.drawCircle(left + 16f, top + 16f, 4f, paint)
            drawText(canvas, label, left + 26f, top + 21f, 13f, palette.secondary)
            drawText(canvas, value, left + 32f + labelWidth, top + 21f, 13f, accent, true)
            return width
        }

        private fun drawSection(canvas: Canvas, section: ExportSection, top: Int) {
            val titleBaseline = top + 37f
            drawText(
                canvas,
                section.title,
                SectionPadding.toFloat(),
                titleBaseline,
                20f,
                palette.primary,
                true,
            )
            val titleWidth = paint.measureText(section.title)
            drawText(
                canvas,
                labels.sectionRating(section.entries.sumOf(RatingUtils.Entry::rating)),
                SectionPadding + titleWidth + 12f,
                titleBaseline,
                14f,
                section.accent,
                true,
            )
            val gridTop = top + SectionHeaderHeight
            section.entries.chunked(Columns).forEachIndexed { row, entries ->
                entries.forEachIndexed { column, entry ->
                    val left = SectionPadding + column * (CardWidth + CardSpacing)
                    val cardTop = gridTop + row * (CardHeight + CardSpacing)
                    drawSongCard(canvas, entry, left, cardTop)
                }
            }
        }

        private fun drawSongCard(
            canvas: Canvas,
            entry: RatingUtils.Entry,
            left: Int,
            top: Int,
        ) {
            val difficulty = difficultyColor(entry.difficulty)
            val bounds = RectF(
                left.toFloat(),
                top.toFloat(),
                (left + CardWidth).toFloat(),
                (top + CardHeight).toFloat(),
            )
            paint.style = Paint.Style.FILL
            paint.color = compositeOver(palette.background, difficulty, 0.15f)
            canvas.drawRoundRect(bounds, 6f, 6f, paint)
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = 1f
            paint.color = withAlpha(difficulty, 0.34f)
            canvas.drawRoundRect(bounds, 6f, 6f, paint)
            paint.style = Paint.Style.FILL

            val jacketLeft = left + 6
            val jacketTop = top + 6
            drawJacket(canvas, entry.imageName, entry.songId, jacketLeft, jacketTop)
            val contentLeft = left + 76f
            val contentRight = left + CardWidth - 6f
            val contentWidth = contentRight - contentLeft
            drawEllipsizedText(
                canvas,
                entry.title,
                contentLeft,
                top + 19f,
                contentWidth,
                11f,
                palette.primary,
                bold = true,
            )
            val rank = RatingUtils.rank(entry.achievement)
            drawText(canvas, rank, contentLeft, top + 37f, 12f, rankColor(rank), true)
            setText(12f, palette.primary, bold = true)
            val rankWidth = paint.measureText(rank)
            drawText(
                canvas,
                String.format(Locale.ROOT, "%.4f%%", entry.achievement),
                contentLeft + rankWidth + 4f,
                top + 37f,
                9f,
                palette.secondary,
                monospaced = true,
            )
            dxStars(entry.dxScore, entry.maxDxScore).takeIf { it > 0 }?.let { stars ->
                val starText = "$stars ★"
                setText(8f, StarColor, bold = true)
                drawText(
                    canvas,
                    starText,
                    contentRight - paint.measureText(starText),
                    top + 36f,
                    8f,
                    StarColor,
                    true,
                )
            }
            val level = String.format(Locale.ROOT, "%.1f", entry.level)
            drawText(canvas, level, contentLeft, top + 53f, 9f, difficulty, true)
            setText(9f, difficulty, bold = true)
            val levelWidth = paint.measureText(level)
            drawText(canvas, "→", contentLeft + levelWidth + 3f, top + 53f, 7f, palette.subtle)
            drawText(
                canvas,
                entry.rating.toString(),
                contentLeft + levelWidth + 13f,
                top + 53f,
                9f,
                palette.rating,
                true,
            )
            if (entry.maxDxScore > 0) {
                setText(8f, palette.secondary, monospaced = true)
                val dxText = "${entry.dxScore}/${entry.maxDxScore}"
                drawText(
                    canvas,
                    dxText,
                    contentRight - paint.measureText(dxText),
                    top + 53f,
                    8f,
                    palette.secondary,
                    monospaced = true,
                )
            }
            var badgeLeft = contentLeft
            badgeLeft += drawBadge(
                canvas,
                entry.type.uppercase(),
                badgeLeft,
                top + 55f,
                typeColor(entry.type),
            ) + 3f
            entry.fc?.takeIf(String::isNotBlank)?.let { fc ->
                badgeLeft += drawBadge(
                    canvas,
                    ScoreRules.displayFc(fc) ?: fc.uppercase(),
                    badgeLeft,
                    top + 55f,
                    comboColor(fc),
                ) + 3f
            }
            entry.fs?.takeIf(String::isNotBlank)?.let { fs ->
                val display = ScoreRules.displayFs(fs)?.let { value ->
                    if (value == "S") "SYNC" else value
                } ?: fs.uppercase()
                drawBadge(canvas, display, badgeLeft, top + 55f, syncColor(fs))
            }
        }

        private fun drawJacket(
            canvas: Canvas,
            imageName: String,
            songId: Int,
            left: Int,
            top: Int,
        ) {
            val destination = RectF(
                left.toFloat(),
                top.toFloat(),
                (left + JacketSize).toFloat(),
                (top + JacketSize).toFloat(),
            )
            val jacket = coverImageStore.fileFor(imageName)?.let { file ->
                decodeSampledBitmap(
                    file,
                    JacketSize * OutputScale,
                    JacketSize * OutputScale,
                )
            }
            canvas.withClip(Path().apply { addRoundRect(destination, 4f, 4f, Path.Direction.CW) }) {
							if (jacket == null) {
								paint.color = palette.emptyCard
								drawRect(destination, paint)
								drawText(this, "♪", left + 24f, top + 40f, 22f, palette.subtle)
							} else {
								val source = centerCropSource(jacket.width, jacket.height)
								paint.alpha = 255
								paint.isFilterBitmap = true
								drawBitmap(jacket, source, destination, paint)
								jacket.recycle()
							}
						}
            if (songId > 0) {
                val idText = "#$songId"
                setText(8f, Color.WHITE, bold = true, monospaced = true)
                val width = paint.measureText(idText) + 6f
                val badgeLeft = left + JacketSize - width - 2f
                val badgeTop = top + JacketSize - 13f
                paint.color = Color.argb(153, 0, 0, 0)
                canvas.drawRoundRect(
                    RectF(badgeLeft, badgeTop, badgeLeft + width, badgeTop + 11f),
                    2f,
                    2f,
                    paint,
                )
                drawText(
                    canvas,
                    idText,
                    badgeLeft + 3f,
                    badgeTop + 8f,
                    8f,
                    Color.WHITE,
                    true,
                    monospaced = true,
                )
            }
        }

        private fun drawBadge(
            canvas: Canvas,
            text: String,
            left: Float,
            top: Float,
            @ColorInt color: Int,
        ): Float {
            setText(7f, Color.WHITE, bold = true)
            val width = paint.measureText(text) + 8f
            paint.color = color
            canvas.drawRoundRect(RectF(left, top, left + width, top + 13f), 2f, 2f, paint)
            drawText(canvas, text, left + 4f, top + 9.5f, 7f, Color.WHITE, true)
            return width
        }

        private fun drawFooter(canvas: Canvas, top: Int) {
            var baseline = top + 27f
            version?.takeIf(String::isNotBlank)?.let { currentVersion ->
                setText(11f, palette.secondary, bold = true)
                val text = "●  $currentVersion"
                val left = (CanvasWidth - paint.measureText(text)) / 2f
                canvas.drawText(text, left, baseline, paint)
                baseline += 22f
            }
            setText(12f, palette.subtle)
            canvas.drawText(
                labels.watermark,
                (CanvasWidth - paint.measureText(labels.watermark)) / 2f,
                baseline,
                paint,
            )
        }

        private fun drawEllipsizedText(
            canvas: Canvas,
            text: String,
            x: Float,
            baseline: Float,
            maxWidth: Float,
            size: Float,
            @ColorInt color: Int,
            bold: Boolean = false,
        ) {
            setText(size, color, bold)
            if (paint.measureText(text) <= maxWidth) {
                canvas.drawText(text, x, baseline, paint)
                return
            }
            val ellipsis = "…"
            val count = paint.breakText(text, true, maxWidth - paint.measureText(ellipsis), null)
            canvas.drawText(text.take(count) + ellipsis, x, baseline, paint)
        }

        private fun drawText(
            canvas: Canvas,
            text: String,
            x: Float,
            baseline: Float,
            size: Float,
            @ColorInt color: Int,
            bold: Boolean = false,
            monospaced: Boolean = false,
        ) {
            setText(size, color, bold, monospaced)
            canvas.drawText(text, x, baseline, paint)
        }

        private fun setText(
            size: Float,
            @ColorInt color: Int,
            bold: Boolean = false,
            monospaced: Boolean = false,
        ) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = color
            paint.textSize = size
            paint.typeface = when {
                monospaced && bold -> android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                monospaced -> android.graphics.Typeface.MONOSPACE
                bold -> android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
                else -> android.graphics.Typeface.DEFAULT
            }
        }

        private fun ratingShader(
            rating: Int,
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
        ): Shader {
            val colors = when {
                rating >= 15_000 -> intArrayOf(
                    Color.rgb(255, 94, 94),
                    Color.rgb(255, 186, 94),
                    Color.rgb(255, 247, 94),
                    Color.rgb(94, 255, 94),
                    Color.rgb(94, 186, 255),
                    Color.rgb(186, 94, 255),
                    Color.rgb(255, 94, 186),
                )
                rating >= 14_500 -> intArrayOf(
                    Color.rgb(211, 211, 211),
                    Color.WHITE,
                    Color.rgb(211, 211, 211),
                )
                rating >= 14_000 -> intArrayOf(Color.rgb(255, 215, 0), Color.rgb(255, 165, 0))
                else -> intArrayOf(ratingColor(rating), ratingColor(rating))
            }
            return LinearGradient(left, top, right, bottom, colors, null, Shader.TileMode.CLAMP)
        }

        private fun sectionHeight(entryCount: Int): Int =
            SectionHeaderHeight + rowsFor(entryCount) * CardHeight + (rowsFor(entryCount) - 1) * CardSpacing + SectionBottomPadding

        private fun rowsFor(entryCount: Int): Int = (entryCount + Columns - 1) / Columns
    }

    private data class ExportSection(
        val title: String,
        @ColorInt val accent: Int,
        val entries: List<RatingUtils.Entry>,
    )

    private class Palette(darkTheme: Boolean) {
        @ColorInt val background = if (darkTheme) Color.rgb(15, 15, 19) else Color.WHITE
        @ColorInt val primary = if (darkTheme) Color.WHITE else Color.BLACK
        @ColorInt val secondary = if (darkTheme) Color.rgb(158, 158, 160) else Color.rgb(102, 102, 102)
        @ColorInt val subtle = if (darkTheme) Color.rgb(87, 87, 90) else Color.rgb(179, 179, 179)
        @ColorInt val emptyCard = if (darkTheme) Color.rgb(27, 27, 31) else Color.rgb(242, 242, 242)
        @ColorInt val rating = if (darkTheme) Color.rgb(255, 215, 0) else Color.rgb(197, 160, 0)
    }

    private fun decodeSampledBitmap(file: File, requestedWidth: Int, requestedHeight: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sampleSize = 1
        while (bounds.outWidth / (sampleSize * 2) >= requestedWidth &&
            bounds.outHeight / (sampleSize * 2) >= requestedHeight
        ) {
            sampleSize *= 2
        }
        return BitmapFactory.decodeFile(
            file.path,
            BitmapFactory.Options().apply { inSampleSize = sampleSize },
        )
    }

    private fun centerCropSource(width: Int, height: Int): Rect = if (width > height) {
        val offset = (width - height) / 2
        Rect(offset, 0, offset + height, height)
    } else {
        val offset = (height - width) / 2
        Rect(0, offset, width, offset + width)
    }

    private fun dxStars(dxScore: Int, maxDxScore: Int): Int {
        if (maxDxScore <= 0) return 0
        return when (dxScore.toDouble() / maxDxScore) {
            in 0.97..Double.MAX_VALUE -> 5
            in 0.95..<0.97 -> 4
            in 0.93..<0.95 -> 3
            in 0.90..<0.93 -> 2
            in 0.85..<0.90 -> 1
            else -> 0
        }
    }

    @ColorInt
    private fun difficultyColor(difficulty: String): Int = when {
        difficulty.contains("basic", ignoreCase = true) -> Color.rgb(54, 191, 99)
        difficulty.contains("advanced", ignoreCase = true) -> Color.rgb(252, 161, 59)
        difficulty.contains("expert", ignoreCase = true) -> Color.rgb(247, 83, 106)
        difficulty.contains("remaster", ignoreCase = true) -> Color.rgb(227, 189, 252)
        difficulty.contains("master", ignoreCase = true) -> Color.rgb(163, 78, 228)
        else -> Color.rgb(255, 45, 85)
    }

    @ColorInt
    private fun typeColor(type: String): Int = when (type.lowercase()) {
        "dx" -> Color.rgb(232, 117, 0)
        else -> Color.rgb(25, 118, 210)
    }

    @ColorInt
    private fun rankColor(rank: String): Int = when (rank) {
        "SSS+", "SSS" -> Color.rgb(255, 217, 0)
        "SS+", "SS" -> Color.rgb(255, 191, 0)
        "S+", "S" -> Color.rgb(255, 153, 0)
        "AAA" -> Color.rgb(204, 153, 255)
        "AA" -> Color.rgb(153, 204, 255)
        "A" -> Color.rgb(128, 230, 128)
        else -> Color.LTGRAY
    }

    @ColorInt
    private fun comboColor(value: String): Int = when (ScoreRules.canonicalFc(value)) {
        "ap", "app" -> Color.rgb(255, 153, 0)
        else -> Color.rgb(51, 191, 51)
    }

    @ColorInt
    private fun syncColor(value: String): Int = when (ScoreRules.canonicalFs(value)) {
        "fsd", "fsdp" -> Color.rgb(255, 215, 0)
        else -> Color.rgb(77, 128, 255)
    }

    @ColorInt
    private fun ratingColor(rating: Int): Int = when {
        rating >= 15_000 -> Color.rgb(255, 97, 0)
        rating >= 14_500 -> Color.rgb(229, 228, 226)
        rating >= 14_000 -> Color.rgb(255, 215, 0)
        rating >= 13_000 -> Color.rgb(192, 192, 192)
        rating >= 12_000 -> Color.rgb(205, 127, 50)
        rating >= 10_000 -> Color.rgb(208, 132, 255)
        rating >= 7_000 -> Color.rgb(255, 94, 94)
        rating >= 4_000 -> Color.rgb(255, 212, 0)
        rating >= 2_000 -> Color.rgb(70, 210, 70)
        rating >= 1_000 -> Color.rgb(86, 166, 255)
        else -> Color.rgb(142, 142, 147)
    }

    @ColorInt
    private fun compositeOver(@ColorInt background: Int, @ColorInt foreground: Int, alpha: Float): Int {
        val inverse = 1f - alpha
        return Color.rgb(
            (Color.red(foreground) * alpha + Color.red(background) * inverse).toInt(),
            (Color.green(foreground) * alpha + Color.green(background) * inverse).toInt(),
            (Color.blue(foreground) * alpha + Color.blue(background) * inverse).toInt(),
        )
    }

    @ColorInt
    private fun withAlpha(@ColorInt color: Int, alpha: Float): Int =
        Color.argb((alpha * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    private const val OutputScale = 2
    private const val Columns = 5
    private const val CardWidth = 220
    private const val CardHeight = 74
    private const val CardSpacing = 8
    private const val SectionPadding = 24
    private const val JacketSize = 62
    private const val CanvasWidth = Columns * CardWidth + (Columns - 1) * CardSpacing + SectionPadding * 2
    private const val HeaderHeight = 184
    private const val SectionHeaderHeight = 50
    private const val SectionBottomPadding = 16
    private const val FooterHeight = 70
    @ColorInt private val NewAccent = Color.rgb(255, 107, 107)
    @ColorInt private val OldAccent = Color.rgb(78, 205, 196)
    @ColorInt private val StarColor = Color.rgb(255, 204, 0)
}
