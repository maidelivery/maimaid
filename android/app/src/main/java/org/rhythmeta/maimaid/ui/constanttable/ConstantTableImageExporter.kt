package org.rhythmeta.maimaid.ui.constanttable

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
import org.rhythmeta.maimaid.core.data.ConstantTableCalculator
import org.rhythmeta.maimaid.core.data.ConstantTableSection
import org.rhythmeta.maimaid.core.data.CoverImageStore
import org.rhythmeta.maimaid.core.data.ScoreRules
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withClip

internal object ConstantTableImageExporter {
    suspend fun renderToCache(
        context: Context,
        baseLevel: Int,
        sections: List<ConstantTableSection>,
        includeScores: Boolean,
        userName: String?,
        coverImageStore: CoverImageStore,
        darkTheme: Boolean,
    ): File? = withContext(Dispatchers.Default) {
        runCatching {
            val displayLevel = ConstantTableCalculator.baseLevelLabel(baseLevel)
            val labels = Labels(
                title = context.getString(
                    if (includeScores) R.string.constant_table_image_title_scores
                    else R.string.constant_table_image_title_constants,
                    displayLevel,
                ),
                summary = context.getString(
                    R.string.constant_table_image_summary,
                    sections.size,
                    sections.sumOf { it.entries.size },
                ),
                watermark = context.getString(R.string.constant_table_watermark),
            )
            val bitmap = Renderer(
                sections = sections,
                includeScores = includeScores,
                userName = userName,
                coverImageStore = coverImageStore,
                darkTheme = darkTheme,
                labels = labels,
            ).render()
            withContext(Dispatchers.IO) {
                File(context.cacheDir, "constant-table-$baseLevel.png").also { destination ->
                    destination.outputStream().use { output ->
                        check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                    }
                    bitmap.recycle()
                }
            }
        }.getOrNull()
    }

    private data class Labels(
        val title: String,
        val summary: String,
        val watermark: String,
    )

    private class Renderer(
        private val sections: List<ConstantTableSection>,
        private val includeScores: Boolean,
        private val userName: String?,
        private val coverImageStore: CoverImageStore,
        darkTheme: Boolean,
        private val labels: Labels,
    ) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
        private val palette = Palette(darkTheme)
        private val jacketSize = if (includeScores) 58 else 52
        private val chartSpacing = if (includeScores) 8 else 6
        private val columns = ((CANVAS_WIDTH - HORIZONTAL_PADDING * 2 - LABEL_WIDTH - 20 + chartSpacing) /
            (jacketSize + chartSpacing)).coerceAtLeast(1)

        fun render(): Bitmap {
            val logicalHeight = HEADER_HEIGHT +
                sections.sumOf { sectionHeight(it.entries.size) } +
                FOOTER_HEIGHT
            val bitmap = createBitmap(CANVAS_WIDTH * OUTPUT_SCALE, logicalHeight * OUTPUT_SCALE)
            val canvas = Canvas(bitmap)
            canvas.scale(OUTPUT_SCALE.toFloat(), OUTPUT_SCALE.toFloat())
            paint.shader = LinearGradient(
                0f,
                0f,
                CANVAS_WIDTH.toFloat(),
                logicalHeight.toFloat(),
                intArrayOf(palette.background, palette.secondaryBackground, palette.tertiaryBackground),
                null,
                Shader.TileMode.CLAMP,
            )
            canvas.drawRect(0f, 0f, CANVAS_WIDTH.toFloat(), logicalHeight.toFloat(), paint)
            paint.shader = null
            drawHeader(canvas)
            var top = HEADER_HEIGHT
            sections.forEachIndexed { index, section ->
                drawSection(canvas, section, index, top)
                top += sectionHeight(section.entries.size)
            }
            drawFooter(canvas, top)
            return bitmap
        }

        private fun drawHeader(canvas: Canvas) {
            drawText(canvas, labels.title, HORIZONTAL_PADDING.toFloat(), 58f, 36f, palette.primary, true)
            drawText(canvas, labels.summary, HORIZONTAL_PADDING.toFloat(), 89f, 16f, palette.secondary)
            if (includeScores) {
                userName?.trim()?.takeIf(String::isNotEmpty)?.let { name ->
                    setText(14f, ACCENT, true)
                    val width = paint.measureText(name) + 30f
                    val left = CANVAS_WIDTH - HORIZONTAL_PADDING - width
                    paint.color = accentBackgroundColor()
                    canvas.drawRoundRect(RectF(left, 31f, left + width, 65f), 17f, 17f, paint)
                    drawText(canvas, name, left + 15f, 53f, 14f, ACCENT, true)
                }
            }
            paint.color = palette.divider
            canvas.drawRect(
                HORIZONTAL_PADDING.toFloat(),
                HEADER_HEIGHT - 14f,
                (CANVAS_WIDTH - HORIZONTAL_PADDING).toFloat(),
                HEADER_HEIGHT - 13f,
                paint,
            )
        }

        private fun drawSection(canvas: Canvas, section: ConstantTableSection, index: Int, top: Int) {
            val bottom = top + sectionHeight(section.entries.size) - SECTION_GAP
            paint.color = if (index % 2 == 0) palette.sectionA else palette.sectionB
            canvas.drawRoundRect(
                RectF(
                    HORIZONTAL_PADDING.toFloat(),
                    top.toFloat(),
                    (CANVAS_WIDTH - HORIZONTAL_PADDING).toFloat(),
                    bottom.toFloat(),
                ),
                18f,
                18f,
                paint,
            )
            drawText(
                canvas,
                section.levelLabel,
                (HORIZONTAL_PADDING + 12).toFloat(),
                top + 47f,
                32f,
                levelColor(section.levelLabel, index),
                true,
            )
            val gridLeft = HORIZONTAL_PADDING + 12 + LABEL_WIDTH + 16
            val gridTop = top + 12
            section.entries.chunked(columns).forEachIndexed { row, entries ->
                entries.forEachIndexed { column, entry ->
                    val left = gridLeft + column * (jacketSize + chartSpacing)
                    val cellTop = gridTop + row * (jacketSize + chartSpacing)
                    drawJacket(canvas, entry.imageName, left, cellTop)
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = 2f
                    paint.color = difficultyColor(entry.difficulty)
                    canvas.drawRoundRect(
                        RectF(
                            left.toFloat(),
                            cellTop.toFloat(),
                            (left + jacketSize).toFloat(),
                            (cellTop + jacketSize).toFloat(),
                        ),
                        8f,
                        8f,
                        paint,
                    )
                    paint.style = Paint.Style.FILL
                    if (includeScores) drawScoreBadges(canvas, entry.rank, entry.fc, entry.fs, left, cellTop)
                }
            }
        }

        private fun drawJacket(canvas: Canvas, imageName: String, left: Int, top: Int) {
            val destination = RectF(
                left.toFloat(),
                top.toFloat(),
                (left + jacketSize).toFloat(),
                (top + jacketSize).toFloat(),
            )
            val jacket = coverImageStore.fileFor(imageName)?.let { file ->
                decodeSampledBitmap(file, jacketSize * OUTPUT_SCALE, jacketSize * OUTPUT_SCALE)
            }
            canvas.withClip(Path().apply { addRoundRect(destination, 8f, 8f, Path.Direction.CW) }) {
							if (jacket == null) {
								paint.color = palette.emptyJacket
								drawRect(destination, paint)
								drawText(
			            this,
			            "♪",
			            left + jacketSize * 0.32f,
			            top + jacketSize * 0.66f,
			            20f,
			            palette.secondary
		            )
							} else {
								paint.alpha = 255
								paint.isFilterBitmap = true
								drawBitmap(
			            jacket,
			            centerCropSource(jacket.width, jacket.height),
			            destination,
			            paint
		            )
								jacket.recycle()
							}
						}
        }

        private fun drawScoreBadges(
            canvas: Canvas,
            rank: String?,
            fc: String?,
            fs: String?,
            left: Int,
            top: Int,
        ) {
            val badges = buildList {
                rank?.takeIf(String::isNotBlank)?.let { add(it to rankColor(it)) }
                fc?.takeIf(String::isNotBlank)?.let {
                    add((ScoreRules.displayFc(it) ?: it.uppercase()) to comboColor(it))
                }
                fs?.takeIf(String::isNotBlank)?.let {
                    val normalized = ScoreRules.displayFs(it) ?: it.uppercase()
                    add((if (normalized == "S") "SYNC" else normalized) to syncColor(it))
                }
            }
            var bottom = top + jacketSize - 2f
            badges.asReversed().forEach { (text, color) ->
                setText(9f, Color.WHITE, true)
                val width = paint.measureText(text) + 8f
                val badgeTop = bottom - 14f
                val badgeLeft = left + jacketSize - width - 2f
                paint.color = color
                canvas.drawRoundRect(RectF(badgeLeft, badgeTop, badgeLeft + width, bottom), 3f, 3f, paint)
                drawText(canvas, text, badgeLeft + 4f, bottom - 3.5f, 9f, Color.WHITE, true)
                bottom = badgeTop - 2f
            }
        }

        private fun drawFooter(canvas: Canvas, top: Int) {
            drawText(
                canvas,
                labels.watermark,
                HORIZONTAL_PADDING.toFloat(),
                top + 34f,
                12f,
                palette.secondary,
                true,
            )
        }

        private fun drawText(
            canvas: Canvas,
            text: String,
            x: Float,
            baseline: Float,
            size: Float,
            @ColorInt color: Int,
            bold: Boolean = false,
        ) {
            setText(size, color, bold)
            canvas.drawText(text, x, baseline, paint)
        }

        private fun setText(size: Float, @ColorInt color: Int, bold: Boolean = false) {
            paint.style = Paint.Style.FILL
            paint.shader = null
            paint.color = color
            paint.textSize = size
            paint.typeface = if (bold) {
                android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
            } else {
                android.graphics.Typeface.DEFAULT
            }
        }

        private fun sectionHeight(entryCount: Int): Int {
            val rows = (entryCount + columns - 1) / columns
            return 24 + rows * jacketSize + (rows - 1).coerceAtLeast(0) * chartSpacing + SECTION_GAP
        }
    }

    private class Palette(darkTheme: Boolean) {
        @ColorInt val background = if (darkTheme) Color.rgb(17, 18, 22) else Color.rgb(255, 245, 251)
        @ColorInt val secondaryBackground = if (darkTheme) Color.rgb(23, 25, 34) else Color.rgb(250, 238, 255)
        @ColorInt val tertiaryBackground = if (darkTheme) Color.rgb(32, 23, 43) else Color.rgb(248, 240, 255)
        @ColorInt val primary = if (darkTheme) Color.WHITE else Color.rgb(138, 36, 92)
        @ColorInt val secondary = if (darkTheme) Color.rgb(184, 184, 188) else Color.rgb(92, 82, 88)
        @ColorInt val divider = if (darkTheme) Color.argb(31, 255, 255, 255) else Color.argb(217, 255, 255, 255)
        @ColorInt val sectionA = if (darkTheme) Color.argb(15, 255, 255, 255) else Color.argb(92, 255, 255, 255)
        @ColorInt val sectionB = if (darkTheme) Color.argb(8, 255, 255, 255) else Color.argb(56, 255, 255, 255)
        @ColorInt val emptyJacket = if (darkTheme) Color.rgb(38, 38, 44) else Color.rgb(235, 226, 235)
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
        return BitmapFactory.decodeFile(file.path, BitmapFactory.Options().apply { inSampleSize = sampleSize })
    }

    private fun centerCropSource(width: Int, height: Int): Rect = if (width > height) {
        val offset = (width - height) / 2
        Rect(offset, 0, offset + height, height)
    } else {
        val offset = (height - width) / 2
        Rect(0, offset, width, offset + width)
    }

    @ColorInt
    private fun levelColor(label: String, index: Int): Int = when (((label.toDoubleOrNull() ?: 0.0) * 10).toInt() % 10) {
        0, 5 -> Color.rgb(211, 74, 99)
        1, 6 -> Color.rgb(77, 120, 255)
        2, 7 -> Color.rgb(63, 155, 116)
        3, 8 -> Color.rgb(180, 91, 255)
        else -> if (index % 2 == 0) Color.rgb(200, 74, 123) else Color.rgb(84, 137, 255)
    }

    @ColorInt
    private fun difficultyColor(difficulty: String): Int = when {
        difficulty.contains("basic", true) -> Color.rgb(54, 191, 99)
        difficulty.contains("advanced", true) -> Color.rgb(252, 161, 59)
        difficulty.contains("expert", true) -> Color.rgb(247, 83, 106)
        difficulty.contains("remaster", true) -> Color.rgb(227, 189, 252)
        difficulty.contains("master", true) -> Color.rgb(163, 78, 228)
        else -> Color.rgb(255, 45, 85)
    }

    @ColorInt
    private fun rankColor(rank: String): Int = when (rank.uppercase(Locale.ROOT)) {
        "SSS+", "SSS" -> Color.rgb(255, 217, 0)
        "SS+", "SS" -> Color.rgb(255, 191, 0)
        "S+", "S" -> Color.rgb(255, 153, 0)
        "AAA" -> Color.rgb(204, 153, 255)
        "AA" -> Color.rgb(153, 204, 255)
        "A" -> Color.rgb(128, 230, 128)
        else -> Color.GRAY
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
    private fun accentBackgroundColor(): Int =
        Color.argb((0.12f * 255).toInt(), Color.red(ACCENT), Color.green(ACCENT), Color.blue(ACCENT))

    private const val CANVAS_WIDTH = 1440
    private const val OUTPUT_SCALE = 2
    private const val HORIZONTAL_PADDING = 28
    private const val LABEL_WIDTH = 72
    private const val HEADER_HEIGHT = 120
    private const val FOOTER_HEIGHT = 62
    private const val SECTION_GAP = 20
    private const val ACCENT = 0xFF8E3DFF.toInt()
}
