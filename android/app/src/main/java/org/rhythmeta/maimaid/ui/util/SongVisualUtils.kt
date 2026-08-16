package org.rhythmeta.maimaid.ui.util

import android.graphics.Bitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import kotlin.math.max
import kotlin.math.roundToInt

internal object SongVisualUtils {
    data class DetailColors(
        val background: Color,
        val surface: Color,
        val selectedSurface: Color,
        val accent: Color,
    )

    data class ExternalSearchColors(
        val youtubeSurface: Color,
        val youtubeContent: Color,
        val bilibiliSurface: Color,
        val bilibiliContent: Color,
    )

    fun isDarkTheme(background: Color): Boolean = background.luminance() < 0.5f

    fun detailColors(raw: Color, darkTheme: Boolean): DetailColors {
        val accentHsv = raw.toHsv()
        accentHsv[1] = (accentHsv[1] * 0.85f).coerceIn(0.25f, 0.75f)
        accentHsv[2] = if (darkTheme) 0.82f else 0.62f

        fun surface(selected: Boolean): Color {
            val hsv = raw.toHsv()
            hsv[1] = if (darkTheme) {
                (hsv[1] * if (selected) 0.48f else 0.34f)
                    .coerceIn(0.08f, if (selected) 0.30f else 0.22f)
            } else {
                (hsv[1] * if (selected) 0.28f else 0.16f)
                    .coerceIn(0.03f, if (selected) 0.18f else 0.11f)
            }
            hsv[2] = when {
                darkTheme && selected -> 0.3f
                darkTheme -> 0.2f
                selected -> 0.94f
                else -> 0.98f
            }
            return hsv.toColor()
        }

        return DetailColors(
            background = tonedBackground(raw, darkTheme),
            surface = surface(selected = false),
            selectedSurface = surface(selected = true),
            accent = accentHsv.toColor(),
        )
    }

    fun averageJacketColor(bitmap: Bitmap): Color? {
        if (bitmap.width <= 0 || bitmap.height <= 0) return null

        val scale = minOf(1f, 96f / max(bitmap.width, bitmap.height).toFloat())
        val sampledWidth = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val sampledHeight = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        val sampledBitmap = if (sampledWidth == bitmap.width && sampledHeight == bitmap.height) {
            bitmap
        } else {
            Bitmap.createScaledBitmap(bitmap, sampledWidth, sampledHeight, true)
        }

        return try {
            val pixels = IntArray(sampledWidth * sampledHeight)
            sampledBitmap.getPixels(pixels, 0, sampledWidth, 0, 0, sampledWidth, sampledHeight)
            var red = 0L
            var green = 0L
            var blue = 0L
            var alpha = 0L
            pixels.forEach { pixel ->
                red += android.graphics.Color.red(pixel)
                green += android.graphics.Color.green(pixel)
                blue += android.graphics.Color.blue(pixel)
                alpha += android.graphics.Color.alpha(pixel)
            }
            val pixelCount = pixels.size.toFloat()
            Color(
                red = red / pixelCount / 255f,
                green = green / pixelCount / 255f,
                blue = blue / pixelCount / 255f,
                alpha = alpha / pixelCount / 255f,
            )
        } finally {
            if (sampledBitmap !== bitmap) sampledBitmap.recycle()
        }
    }

    fun difficultyColor(
        difficulty: String,
        type: String? = null,
        darkTheme: Boolean,
        brightenDark: Boolean = false,
        fallbackColor: Color = Color(0xFFFF2D55).copy(alpha = 0.5f),
    ): Color {
        if (type?.contains("utage", ignoreCase = true) == true) {
            return utageColor(darkTheme)
        }

        val normalized = difficulty.lowercase()
        val baseColor = when {
            "basic" in normalized -> if (darkTheme) Color(0xFF2A974E) else Color(0xFF36BF63)
            "advanced" in normalized -> if (darkTheme) Color(0xFFC8802D) else Color(0xFFFCA13B)
            "expert" in normalized -> if (darkTheme) Color(0xFFC54153) else Color(0xFFF7536A)
            "remaster" in normalized -> if (darkTheme) Color(0xFFBF8CFC) else Color(0xFFE3BDFC)
            "master" in normalized -> if (darkTheme) Color(0xFF813DB4) else Color(0xFFA34EE4)
            else -> return fallbackColor
        }
        if (!darkTheme || !brightenDark) return baseColor

        val hsv = baseColor.toHsv()
        hsv[2] = (hsv[2] * 1.22f).coerceIn(0.78f, 1f)
        return hsv.toColor()
    }

    fun chartTypeColor(type: String, darkTheme: Boolean, fallbackColor: Color): Color {
        val normalizedType = type.lowercase()
        return when {
            normalizedType == "std" || normalizedType == "standard" -> {
                if (darkTheme) Color(0xFF64B5F6) else Color(0xFF1976D2)
            }
            normalizedType == "dx" -> if (darkTheme) Color(0xFFFFB74D) else Color(0xFFE87500)
            "utage" in normalizedType -> if (darkTheme) Color(0xFFD6549A) else Color(0xFFFF69B4)
            else -> fallbackColor
        }
    }

    fun versionBadgeChartTypes(
        song: SongEntity,
        sheets: List<SheetEntity>,
        explicitType: String? = null,
    ): List<String> {
        explicitType
            ?.trim()
            ?.takeIf(String::isNotEmpty)
            ?.let { return listOf(canonicalChartType(it)) }

        val isUtage = sheets.any { it.type.contains("utage", ignoreCase = true) } ||
            song.category.contains("utage", ignoreCase = true) ||
            song.category.contains("宴")
        val types = sheets.map { canonicalChartType(it.type) }.toSet()
        return when {
            "std" in types && "dx" in types -> listOf("std", "dx")
            isUtage -> listOf("utage")
            "dx" in types -> listOf("dx")
            else -> listOf("std")
        }
    }

    private fun canonicalChartType(type: String): String = when (type.trim().lowercase()) {
        "standard" -> "std"
        else -> type.trim().lowercase()
    }

    fun utageColor(darkTheme: Boolean): Color =
        if (darkTheme) Color(0xFFBB38B9) else Color(0xFFEC48E9)

    fun externalSearchColors(darkTheme: Boolean): ExternalSearchColors =
        if (darkTheme) {
            ExternalSearchColors(
                youtubeSurface = Color(0xFF65373C),
                youtubeContent = Color(0xFFFFA0A9),
                bilibiliSurface = Color(0xFF24566A),
                bilibiliContent = Color(0xFF8BDCF7),
            )
        } else {
            ExternalSearchColors(
                youtubeSurface = Color(0xFFFFD9DD),
                youtubeContent = Color(0xFFC92D3B),
                bilibiliSurface = Color(0xFFD5F2FC),
                bilibiliContent = Color(0xFF007EAA),
            )
        }

    fun songCardSurfaceColor(baseColor: Color, darkTheme: Boolean): Color =
        baseColor.copy(alpha = if (darkTheme) 0.82f else 0.88f)

    fun difficultyOrder(difficulty: String): Int = when (difficulty.lowercase()) {
        "basic" -> 0
        "advanced" -> 1
        "expert" -> 2
        "master" -> 3
        "remaster" -> 4
        else -> -1
    }

    fun formatVersionName(version: String): String {
        val trimmed = version.trim()
        if (trimmed.isEmpty()) return trimmed

        val plusSuffix = Regex("\\s+PLUS$", RegexOption.IGNORE_CASE)
        val hasPlusSuffix = plusSuffix.containsMatchIn(trimmed)
        val baseVersion = trimmed.replace(plusSuffix, "").trimEnd()
        val dxMatch = Regex(
            pattern = "^(?:maimai\\s*)?(?:でらっくす|DX)(\\+)?\\s*(\\([^)]*\\))?$",
            option = RegexOption.IGNORE_CASE,
        ).matchEntire(baseVersion)
        if (dxMatch != null) {
            val hasInlinePlus = dxMatch.groupValues[1].isNotEmpty()
            val kanjiName = dxMatch.groupValues[2]
            return buildString {
                append("DX")
                if (hasPlusSuffix || hasInlinePlus) append('+')
                if (kanjiName.isNotEmpty()) {
                    append(' ')
                    append(kanjiName)
                }
            }
        }
        return baseVersion + if (hasPlusSuffix) "+" else ""
    }

    fun versionAbbreviation(version: String, versions: List<GameVersionEntity>): String {
        val formattedVersion = formatVersionName(version)
        val match = versions.firstOrNull { it.name == version }
            ?: versions
                .filter { version.contains(it.name) || it.name.contains(version) }
                .maxByOrNull { it.name.length }
        return match
            ?.abbreviation
            ?.takeIf(String::isNotBlank)
            ?.let(::formatVersionName)
            ?: formattedVersion
    }

    fun versionSortOrder(version: String, versions: List<GameVersionEntity>): Int {
        versions.firstOrNull { it.name == version }?.let { return it.sortOrder }
        return versions
            .filter { candidate ->
                version.contains(candidate.name) || candidate.name.contains(version)
            }
            .maxByOrNull { it.name.length }
            ?.sortOrder
            ?: Int.MAX_VALUE
    }

    private fun tonedBackground(raw: Color, darkTheme: Boolean): Color {
        val hsv = raw.toHsv()
        hsv[1] = if (darkTheme) {
            (hsv[1] * 0.75f).coerceIn(0.20f, 0.45f)
        } else {
            (hsv[1] * 0.45f).coerceIn(0.08f, 0.30f)
        }
        hsv[2] = if (darkTheme) {
            (hsv[2] * 0.35f).coerceIn(0.12f, 0.28f)
        } else {
            (0.88f + (hsv[2] - 0.5f) * 0.08f).coerceIn(0.84f, 0.94f)
        }
        return hsv.toColor()
    }

    private fun Color.toHsv(): FloatArray = FloatArray(3).also {
        android.graphics.Color.colorToHSV(toArgb(), it)
    }

    private fun FloatArray.toColor(): Color = Color(android.graphics.Color.HSVToColor(this))
}
