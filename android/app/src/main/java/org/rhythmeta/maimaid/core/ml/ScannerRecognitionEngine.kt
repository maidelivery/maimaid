package org.rhythmeta.maimaid.core.ml

import android.graphics.Bitmap
import android.graphics.Color
import ai.onnxruntime.OrtSession
import kotlin.math.roundToInt

class ScannerRecognitionEngine(
    private val sessionFactory: OnnxSessionFactory,
) : AutoCloseable {
    private var classifierSession: OrtSession? = null
    private var scoreSession: OrtSession? = null
    private var chooseSession: OrtSession? = null
    private var textSession: OrtSession? = null
    private var textRecognizer: PaddleTextRecognizer? = null

    suspend fun recognize(bitmap: Bitmap): ScannerRawResult {
        ensureClassifierSession()
        val screenType = classify(bitmap)
        return when (screenType) {
            MaimaiScreenType.Choose -> {
                ensureChooseSessions()
                recognizeChoose(bitmap)
            }
            MaimaiScreenType.Score -> {
                ensureScoreSessions()
                recognizeScore(bitmap)
            }
            MaimaiScreenType.Unknown -> ScannerRawResult(
                imageWidth = bitmap.width,
                imageHeight = bitmap.height,
            )
        }
    }

    private suspend fun ensureClassifierSession() {
        if (classifierSession == null) classifierSession = sessionFactory.create(VisionModel.ScreenClassifier)
    }

    private suspend fun ensureChooseSessions() {
        if (chooseSession == null) chooseSession = sessionFactory.create(VisionModel.RegionDetector)
        ensureTextRecognizer()
    }

    private suspend fun ensureScoreSessions() {
        if (scoreSession == null) scoreSession = sessionFactory.create(VisionModel.ScoreReader)
        ensureTextRecognizer()
    }

    private suspend fun ensureTextRecognizer() {
        if (textSession == null) {
            textSession = sessionFactory.create(VisionModel.TextRecognizer)
        }
        if (textRecognizer == null) {
            textRecognizer = PaddleTextRecognizer(
                requireNotNull(textSession),
                sessionFactory.textCharactersFile(),
            )
        }
    }

    private fun classify(bitmap: Bitmap): MaimaiScreenType {
        val output = YoloImageProcessor.runClassifier(bitmap, requireNotNull(classifierSession))
        val best = output.indices.maxByOrNull(output::get) ?: return MaimaiScreenType.Unknown
        return when (best) {
            0 -> MaimaiScreenType.Choose
            1 -> MaimaiScreenType.Score
            else -> MaimaiScreenType.Unknown
        }
    }

    private fun recognizeChoose(bitmap: Bitmap): ScannerRawResult {
        val regions = YoloImageProcessor.runDetector(
            bitmap = bitmap,
            session = requireNotNull(chooseSession),
            labels = ChooseLabels,
        )
        val titleCandidates = recognizeTitleCandidates(bitmap, regions.best("title")?.bounds)
        return ScannerRawResult(
            screenType = MaimaiScreenType.Choose,
            title = titleCandidates.firstOrNull(),
            titleCandidates = titleCandidates,
            regions = regions,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
        )
    }

    private fun recognizeScore(bitmap: Bitmap): ScannerRawResult {
        val regions = YoloImageProcessor.runDetector(
            bitmap = bitmap,
            session = requireNotNull(scoreSession),
            labels = ScoreLabels,
        )
        val recognizer = requireNotNull(textRecognizer)
        fun text(label: String): String? = regions.best(label)
            ?.let { recognizer.recognize(bitmap, it.bounds) }
            ?.text

        val titleCandidates = recognizeTitleCandidates(bitmap, regions.best("title")?.bounds)
        val title = titleCandidates.firstOrNull()
        val achievement = ScannerResultParser.parseAchievement(text("achievement"))
        val dxScore = ScannerResultParser.parseInteger(text("dxscore"))
        val maxDxScore = ScannerResultParser.parseInteger(text("maxdxscore"))?.takeIf { it > 0 }
        val level = ScannerResultParser.parseInteger(text("lv"))
            ?.takeIf { it in 1..15 }
            ?.toDouble()
        val rawKanji = text("kanji")?.trim()?.takeIf(String::isNotEmpty)
        val kanji = rawKanji ?: ScannerResultParser.extractUtageKanji(titleCandidates)
        val difficultyRegion = regions.best("difficulty")
        val difficultyText = difficultyRegion?.let { recognizer.recognize(bitmap, it.bounds)?.text }
        val difficulty = ScannerResultParser.parseDifficulty(difficultyText)
            ?: difficultyRegion?.let { classifyDifficultyColor(bitmap, it.bounds) }
        val detectedType = listOf("dx", "std", "utage")
            .mapNotNull { label -> regions.best(label)?.let { label to it.confidence } }
            .maxByOrNull(Pair<String, Float>::second)
            ?.first
        val chartType = detectedType ?: ScannerResultParser.inferChartType(difficulty, kanji, titleCandidates)
        val comboStatus = statusWithHighestConfidence(regions, StatusLabels)
        val syncStatus = statusWithHighestConfidence(regions, SyncLabels)
        val resolvedDifficulty = difficulty ?: "utage".takeIf { chartType == "utage" }
        return ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            achievement = achievement,
            difficulty = resolvedDifficulty,
            chartType = chartType,
            title = title,
            titleCandidates = titleCandidates,
            dxScore = dxScore,
            maxDxScore = maxDxScore,
            comboStatus = comboStatus,
            syncStatus = syncStatus,
            level = level,
            maxCombo = maxDxScore?.div(3),
            kanji = kanji,
            regions = regions,
            imageWidth = bitmap.width,
            imageHeight = bitmap.height,
        )
    }

    private fun classifyDifficultyColor(bitmap: Bitmap, bounds: NormalizedRect): String {
        val left = (bounds.left * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (bounds.top * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (bounds.right * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bounds.bottom * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L
        val step = ((right - left).coerceAtMost(bottom - top) / 24).coerceAtLeast(1)
        for (y in top until bottom step step) {
            for (x in left until right step step) {
                val pixel = bitmap.getPixel(x, y)
                red += Color.red(pixel)
                green += Color.green(pixel)
                blue += Color.blue(pixel)
                count += 1
            }
        }
        if (count == 0L) return "master"
        val hsv = FloatArray(3)
        Color.RGBToHSV((red / count).toInt(), (green / count).toInt(), (blue / count).toInt(), hsv)
        val hue = hsv[0]
        return when {
            hue > 320f || hue < 20f -> "expert"
            hue < 60f -> "advanced"
            hue < 160f -> "basic"
            hue in 260f..320f && hsv[2] > 0.75f && hsv[1] < 0.5f -> "remaster"
            else -> "master"
        }
    }

    override fun close() {
        classifierSession?.close()
        scoreSession?.close()
        chooseSession?.close()
        textSession?.close()
        classifierSession = null
        scoreSession = null
        chooseSession = null
        textSession = null
        textRecognizer = null
    }

    private fun recognizeTitleCandidates(bitmap: Bitmap, bounds: NormalizedRect?): List<String> {
        if (bounds == null) return emptyList()
        return textRecognizer?.recognize(bitmap, bounds)?.text?.let(::listOf) ?: emptyList()
    }

    private fun List<RecognizedRegion>.best(label: String): RecognizedRegion? =
        filter { it.label == label }.maxByOrNull(RecognizedRegion::confidence)

    private fun statusWithHighestConfidence(
        regions: List<RecognizedRegion>,
        labels: List<Pair<String, String>>,
    ): String? {
        val values = labels.toMap()
        return regions
            .filter { it.label in values }
            .maxByOrNull(RecognizedRegion::confidence)
            ?.label
            ?.let(values::get)
    }

    private companion object {
        val ChooseLabels = listOf("title")
        val ScoreLabels = listOf(
            "achievement", "ap", "app", "difficulty", "dx", "dxscore", "fc", "fcp",
            "fdx", "fdxp", "fs", "fsp", "kanji", "lv", "maxdxscore", "std", "sync", "title",
        )
        val StatusLabels = listOf(
            "fc" to "fc",
            "fcp" to "fcp",
            "ap" to "ap",
            "app" to "app",
        )
        val SyncLabels = listOf(
            "sync" to "sync",
            "fs" to "fs",
            "fsp" to "fs+",
            "fdx" to "fsd",
            "fdxp" to "fsdp",
        )
    }
}
