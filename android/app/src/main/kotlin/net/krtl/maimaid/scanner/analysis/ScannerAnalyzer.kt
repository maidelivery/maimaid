package net.krtl.maimaid.scanner.analysis

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.scale
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.google.mlkit.vision.text.japanese.JapaneseTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import net.krtl.maimaid.scanner.ml.TfliteDetector
import net.krtl.maimaid.scanner.ml.TfliteImageClassifier
import net.krtl.maimaid.scanner.model.ScannerDetection
import net.krtl.maimaid.scanner.model.ScannerImageType
import net.krtl.maimaid.scanner.model.ScannerRecognition
import net.krtl.maimaid.scanner.model.ScannerTextLine
import net.krtl.maimaid.scanner.text.ScannerTextParser
import java.util.Locale
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

class ScannerAnalyzer(
    context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val latinRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val japaneseRecognizer = TextRecognition.getClient(JapaneseTextRecognizerOptions.Builder().build())
    private val chineseRecognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

    private val scoreDetector = TfliteDetector(
        context = context,
        assetPath = "scanner/maimaid-v141n.tflite",
        labels = SCORE_LABELS,
        confidenceThreshold = 0.22f
    )
    private val chooseDetector = TfliteDetector(
        context = context,
        assetPath = "scanner/maimaidetector-v12n.tflite",
        labels = CHOOSE_LABELS,
        confidenceThreshold = 0.18f
    )
    private val imageClassifier = TfliteImageClassifier(
        context = context,
        assetPath = "scanner/maimaidistinguish-v12n.tflite",
        labels = CLASSIFIER_LABELS
    )

    val debugModelInfo: String = "classify(${imageClassifier.tensorInfo}) score(${scoreDetector.tensorInfo}) choose(${chooseDetector.tensorInfo})"

    suspend fun analyze(bitmap: Bitmap): ScannerRecognition = withContext(dispatcher) {
        coroutineContext.ensureActive()
        val classification = runCatching { imageClassifier.classify(bitmap) }.getOrNull()

        if (classification?.label == "score" && classification.score >= CLASSIFIER_CONFIDENCE_THRESHOLD) {
            val scoreRecognition = analyzeScoreCandidates(bitmap).firstOrNull()
                ?: ScannerRecognition(
                    imageType = ScannerImageType.UNKNOWN,
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height
                )
            if (scoreRecognition.imageType == ScannerImageType.SCORE) {
                return@withContext scoreRecognition.copy(
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                    debugText = buildDebugText(classification, scoreRecognition.detections, emptyList(), scoreRecognition.debugText)
                )
            }
        }

        if (classification?.label == "choose" && classification.score >= CLASSIFIER_CONFIDENCE_THRESHOLD) {
            val chooseDetections = runCatching { chooseDetector.detect(bitmap) }.getOrDefault(emptyList())
            val chooseRecognition = parseChoose(bitmap, chooseDetections)
            if (chooseRecognition.imageType == ScannerImageType.CHOOSE) {
                return@withContext chooseRecognition.copy(
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                    debugText = buildDebugText(classification, emptyList(), chooseDetections, chooseRecognition.debugText)
                )
            }
        }

        coroutineContext.ensureActive()
        val scoreRecognitions = analyzeScoreCandidates(bitmap)
        val scoreDetections = scoreRecognitions.firstOrNull()?.detections.orEmpty()
        coroutineContext.ensureActive()
        val chooseDetections = runCatching { chooseDetector.detect(bitmap) }.getOrDefault(emptyList())
        val scoreRecognition = scoreRecognitions.firstOrNull()
            ?: ScannerRecognition(
                imageType = ScannerImageType.UNKNOWN,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height
            )
        val chooseRecognition = parseChoose(bitmap, chooseDetections)

        when (selectRecognitionType(scoreRecognition, chooseRecognition, scoreDetections, chooseDetections)) {
            ScannerImageType.SCORE -> return@withContext scoreRecognition.copy(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                debugText = buildDebugText(classification, scoreDetections, chooseDetections, scoreRecognition.debugText)
            )

            ScannerImageType.CHOOSE -> return@withContext chooseRecognition.copy(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                debugText = buildDebugText(classification, scoreDetections, chooseDetections, chooseRecognition.debugText)
            )

            ScannerImageType.UNKNOWN -> Unit
        }

        if (chooseRecognition.titleCandidates.isNotEmpty() && scoreRecognition.scoreSignalCount() <= 0) {
            return@withContext chooseRecognition.copy(
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height,
                debugText = buildDebugText(classification, scoreDetections, chooseDetections, chooseRecognition.debugText)
            )
        }

        coroutineContext.ensureActive()
        val fullFrameLines = recognizeBitmap(bitmap)
        val fullFrameRecognition = ScannerTextParser.parse(fullFrameLines)
        fullFrameRecognition.copy(
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            detections = scoreDetections + chooseDetections,
            debugText = buildDebugText(classification, scoreDetections, chooseDetections, fullFrameRecognition.debugText)
        )
    }

    private fun analyzeScoreCandidates(bitmap: Bitmap): List<ScannerRecognition> {
        return scoreBitmapCandidates(bitmap)
            .map { candidate ->
                val detections = runCatching { scoreDetector.detect(candidate.bitmap) }.getOrDefault(emptyList())
                val recognition = parseScore(candidate.bitmap, detections)
                val fallbackRecognition = if (candidate.isOriginal) {
                    recognition
                } else {
                    applyAlbumMainScreenFallback(candidate.bitmap, recognition)
                }
                fallbackRecognition.copy(
                    sourceWidth = bitmap.width,
                    sourceHeight = bitmap.height,
                    detections = if (candidate.isOriginal) {
                        detections
                    } else {
                        detections.map { it.translateFromCrop(candidate) }
                    },
                    debugText = buildString {
                        if (!candidate.isOriginal) appendLine("preprocess=albumMainScreenCrop")
                        append(fallbackRecognition.debugText)
                    }.trim()
                )
            }
            .sortedByDescending(::scoreRecognitionQuality)
    }

    private data class ScoreBitmapCandidate(
        val bitmap: Bitmap,
        val left: Float,
        val top: Float,
        val width: Float,
        val height: Float,
        val isOriginal: Boolean
    )

    private fun scoreBitmapCandidates(bitmap: Bitmap): List<ScoreBitmapCandidate> {
        val original = ScoreBitmapCandidate(
            bitmap = bitmap,
            left = 0f,
            top = 0f,
            width = 1f,
            height = 1f,
            isOriginal = true
        )
        val crop = albumMainScreenCrop(bitmap) ?: return listOf(original)
        return listOf(crop, original)
    }

    private fun albumMainScreenCrop(bitmap: Bitmap): ScoreBitmapCandidate? {
        val aspect = bitmap.height / bitmap.width.toFloat()
        if (bitmap.height < 1800 || aspect < 1.2f) return null

        val leftNorm = 0.18f
        val topNorm = 0.45f
        val rightNorm = 0.88f
        val bottomNorm = 0.99f
        val left = (bitmap.width * leftNorm).roundToInt().coerceIn(0, bitmap.width - 2)
        val top = (bitmap.height * topNorm).roundToInt().coerceIn(0, bitmap.height - 2)
        val right = (bitmap.width * rightNorm).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * bottomNorm).roundToInt().coerceIn(top + 1, bitmap.height)

        return ScoreBitmapCandidate(
            bitmap = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top),
            left = leftNorm,
            top = topNorm,
            width = rightNorm - leftNorm,
            height = bottomNorm - topNorm,
            isOriginal = false
        )
    }

    private fun applyAlbumMainScreenFallback(
        bitmap: Bitmap,
        recognition: ScannerRecognition
    ): ScannerRecognition {
        val achievementOcr = recognizeRegionText(bitmap, 0.00f, 0.26f, 0.58f, 0.50f)
        val dxPairOcr = recognizeRegionText(bitmap, 0.52f, 0.62f, 0.98f, 0.86f)
        val headerOcr = recognizeRegionText(bitmap, 0.12f, 0.08f, 0.98f, 0.28f)

        val achievement = selectAchievement(achievementOcr + headerOcr, emptyList())
            ?: recognition.rate

        val fallbackDxPair = selectDxScorePair(dxPairOcr, emptyList(), preferDxLabel = true)
        val detectedDxPair = recognition.dxScore?.let { dxScore ->
            recognition.maxDxScore?.let { maxDxScore ->
                (dxScore to maxDxScore).takeIf { dxScore <= maxDxScore && maxDxScore >= 900 }
            }
        }
        val dxPair = when {
            fallbackDxPair == null -> detectedDxPair
            detectedDxPair == null -> fallbackDxPair
            detectedDxPair.second >= fallbackDxPair.second + 200 -> detectedDxPair
            else -> fallbackDxPair
        }
        val headerLines = headerOcr.map { text ->
            ScannerTextLine(
                text = text,
                left = 0.12f,
                top = 0.08f,
                right = 0.98f,
                bottom = 0.28f
            )
        }
        val fallbackTitles = ScannerTextParser.extractTitleCandidates(headerLines)
            .filterNot(::isScoreTitleNoise)
        val titleCandidates = (fallbackTitles + recognition.titleCandidates)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(6)
        val difficulty = ScannerTextParser.parseDifficultyFromOcr(headerOcr.joinToString(" "))
            ?: recognition.difficulty
        val level = parseLevelCandidates(headerOcr)
            ?: recognition.level
        val type = when {
            headerOcr.any { it.contains("standard", ignoreCase = true) || it.contains("标准") || Regex("""(^|[^a-z])std([^a-z]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(it) } -> "std"
            headerOcr.any { it.contains("deluxe", ignoreCase = true) || Regex("""(^|[^a-z])dx([^a-z]|$)""", RegexOption.IGNORE_CASE).containsMatchIn(it) } -> "dx"
            else -> recognition.type
        }

        val fallbackDebug = buildString {
            appendLine(recognition.debugText)
            appendLine("albumAchievementOCR=" + achievementOcr.joinToString(" | "))
            appendLine("albumDxPairOCR=" + dxPairOcr.joinToString(" | "))
            appendLine("albumHeaderOCR=" + headerOcr.joinToString(" | "))
        }.trim()

        return recognition.copy(
            title = titleCandidates.firstOrNull() ?: recognition.title,
            titleCandidates = titleCandidates,
            rate = achievement,
            difficulty = difficulty,
            type = type,
            dxScore = dxPair?.first ?: recognition.dxScore,
            maxDxScore = dxPair?.second ?: recognition.maxDxScore,
            level = level,
            maxCombo = (dxPair?.second ?: recognition.maxDxScore)?.div(3),
            debugText = fallbackDebug
        )
    }

    private fun recognizeRegionText(
        bitmap: Bitmap,
        leftNorm: Float,
        topNorm: Float,
        rightNorm: Float,
        bottomNorm: Float
    ): List<String> {
        val left = (bitmap.width * leftNorm).roundToInt().coerceIn(0, bitmap.width - 2)
        val top = (bitmap.height * topNorm).roundToInt().coerceIn(0, bitmap.height - 2)
        val right = (bitmap.width * rightNorm).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bitmap.height * bottomNorm).roundToInt().coerceIn(top + 1, bitmap.height)
        val crop = Bitmap.createBitmap(bitmap, left, top, right - left, bottom - top)
        return recognizeBitmap(crop)
            .map { it.text.trim() }
            .filter(String::isNotBlank)
            .distinct()
    }

    private fun ScannerDetection.translateFromCrop(candidate: ScoreBitmapCandidate): ScannerDetection {
        return copy(
            left = candidate.left + left * candidate.width,
            top = candidate.top + top * candidate.height,
            right = candidate.left + right * candidate.width,
            bottom = candidate.top + bottom * candidate.height
        )
    }

    private fun scoreRecognitionQuality(recognition: ScannerRecognition): Int {
        return listOfNotNull(
            recognition.rate?.takeIf { it >= 80.0 },
            recognition.difficulty,
            recognition.level,
            recognition.dxScore,
            recognition.maxDxScore,
            recognition.type,
            recognition.fc,
            recognition.fs
        ).size * 10 +
            recognition.titleCandidates.count { !isScoreTitleNoise(it) } * 3 +
            recognition.detections.count { it.label != "title" }
    }

    fun close() {
        latinRecognizer.close()
        japaneseRecognizer.close()
        chineseRecognizer.close()
        scoreDetector.close()
        chooseDetector.close()
        imageClassifier.close()
    }

    private fun parseScore(bitmap: Bitmap, detections: List<ScannerDetection>): ScannerRecognition {
        if (detections.isEmpty()) {
            return ScannerRecognition(
                imageType = ScannerImageType.UNKNOWN,
                sourceWidth = bitmap.width,
                sourceHeight = bitmap.height
            )
        }

        val byLabel = detections.groupBy { it.label }
        val titleOcr = byLabel["title"].orEmpty()
            .asSequence()
            .flatMap { detection -> recognizeCrop(bitmap, detection) }
            .map(String::trim)
            .filter(String::isNotBlank)
            .filterNot(::isScoreTitleNoise)
            .distinct()
            .take(6)
            .toList()
        val titleCandidates = titleOcr

        val achievementOcr = byLabel["achievement"].orEmpty()
            .flatMap { recognizeCrop(bitmap, it) }
        val fullFrameLines = recognizeBitmap(bitmap)
        val achievement = selectAchievement(achievementOcr, fullFrameLines)

        val difficultyOcr = byLabel["difficulty"].orEmpty()
            .flatMap { recognizeCrop(bitmap, it) }
        val difficultyText = difficultyOcr
            .joinToString(" ")
        val difficulty = ScannerTextParser.parseDifficultyFromOcr(difficultyText)
            ?: byLabel["difficulty"].orEmpty()
                .firstNotNullOfOrNull { classifyDifficultyColor(cropBitmap(bitmap, it, upscale = false)) }

        val dxScoreOcr = byLabel["dxscore"].orEmpty()
            .flatMap { recognizeCrop(bitmap, it) }
        val maxDxScoreOcr = byLabel["maxdxscore"].orEmpty()
            .flatMap { recognizeCrop(bitmap, it) }
        val detectedDxScore = dxScoreOcr.firstNotNullOfOrNull { parseBoundedInt(it, 300..9_999) }
        val detectedMaxDxScore = maxDxScoreOcr.firstNotNullOfOrNull { parseBoundedInt(it, 300..9_999) }
        val detectedDxPair = if (
            detectedDxScore != null &&
            detectedMaxDxScore != null &&
            detectedDxScore <= detectedMaxDxScore
        ) {
            detectedDxScore to detectedMaxDxScore
        } else {
            null
        }
        val dxPair = detectedDxPair ?: selectDxScorePair(dxScoreOcr + maxDxScoreOcr, fullFrameLines, preferDxLabel = false)
        val dxScore = dxPair?.first ?: detectedDxScore
        val maxDxScore = dxPair?.second ?: detectedMaxDxScore
        val levelOcr = byLabel["lv"].orEmpty()
            .flatMap { recognizeCrop(bitmap, it) }
        val level = parseLevelCandidates(levelOcr)
        val kanjiOcr = byLabel["kanji"].orEmpty()
            .flatMap { recognizeCrop(bitmap, it) }
        val kanjiText = kanjiOcr
            .firstOrNull()
            ?.trim()
        val kanji = kanjiText
            ?.takeIf { it.isNotBlank() }
            ?: ScannerTextParser.extractKanjiFromTitleCandidates(titleCandidates, titleCandidates.firstOrNull())

        val inferredType = inferChartType(
            difficulty = difficulty,
            kanji = kanji,
            titleCandidates = titleCandidates
        )
        val type = when {
            byLabel.containsKey("utage") || difficulty == "utage" -> "utage"
            dxScore != null && maxDxScore != null && maxDxScore >= 900 && (byLabel["std"]?.maxOfOrNull { it.score } ?: 0f) < 0.75f -> "dx"
            else -> selectChartType(byLabel) ?: inferredType
        }

        val fc = selectBestDetectionLabel(
            byLabel = byLabel,
            labels = listOf("app", "ap", "fcp", "fc"),
            minimumScore = 0.50f
        )?.let { label ->
            when (label) {
                "app" -> "app"
                "ap" -> "ap"
                "fcp" -> "fcp"
                "fc" -> "fc"
                else -> null
            }
        }
        val fs = selectBestDetectionLabel(
            byLabel = byLabel,
            labels = listOf("fdxp", "fdx", "fsp", "fs", "sync"),
            minimumScore = 0.50f
        )?.let { label ->
            when (label) {
                "fdxp" -> "fsdp"
                "fdx" -> "fsd"
                "fsp" -> "fsp"
                "fs" -> "fs"
                "sync" -> "sync"
                else -> null
            }
        }

        val nonTitleDetections = detections.count { it.label != "title" }
        val scoreSignals = nonTitleDetections + listOfNotNull(achievement, difficulty, dxScore, maxDxScore, fc, fs).size
        val debugText = buildString {
            appendLine("titleOCR=" + titleOcr.joinToString(" | "))
            appendLine("achievementOCR=" + achievementOcr.joinToString(" | "))
            appendLine("difficultyOCR=" + difficultyOcr.joinToString(" | "))
            appendLine("dxscoreOCR=" + dxScoreOcr.joinToString(" | "))
            appendLine("maxdxscoreOCR=" + maxDxScoreOcr.joinToString(" | "))
            appendLine("levelOCR=" + levelOcr.joinToString(" | "))
            appendLine("kanjiOCR=" + kanjiOcr.joinToString(" | "))
            if (titleCandidates.isNotEmpty()) appendLine("title=" + titleCandidates.joinToString(" | "))
        }.trim()

        return ScannerRecognition(
            imageType = if (scoreSignals >= 2 || (scoreSignals >= 1 && titleCandidates.isNotEmpty())) {
                ScannerImageType.SCORE
            } else {
                ScannerImageType.UNKNOWN
            },
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            title = titleCandidates.firstOrNull(),
            titleCandidates = titleCandidates,
            rate = achievement,
            difficulty = difficulty,
            type = type,
            dxScore = dxScore,
            maxDxScore = maxDxScore,
            fc = fc,
            fs = fs,
            level = level,
            maxCombo = maxDxScore?.div(3),
            kanji = kanji,
            detections = detections,
            debugText = debugText
        )
    }

    private fun parseChoose(bitmap: Bitmap, detections: List<ScannerDetection>): ScannerRecognition {
        val titleCandidates = detections
            .asSequence()
            .flatMap { recognizeCrop(bitmap, it) }
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinct()
            .take(6)
            .toList()

        return ScannerRecognition(
            imageType = if (titleCandidates.isNotEmpty()) ScannerImageType.CHOOSE else ScannerImageType.UNKNOWN,
            sourceWidth = bitmap.width,
            sourceHeight = bitmap.height,
            title = titleCandidates.firstOrNull(),
            titleCandidates = titleCandidates,
            detections = detections,
            debugText = if (titleCandidates.isEmpty()) "" else "chooseTitle=" + titleCandidates.joinToString(" | ")
        )
    }

    private fun recognizeBitmap(bitmap: Bitmap): List<ScannerTextLine> {
        val recognizers = listOf(latinRecognizer, japaneseRecognizer, chineseRecognizer)
        return recognizers.flatMap { recognizer ->
            runCatching {
                val inputImage = InputImage.fromBitmap(bitmap, 0)
                val result = Tasks.await(recognizer.process(inputImage))
                result.textBlocks.flatMap { block ->
                    block.lines.mapNotNull { line ->
                        val box = line.boundingBox ?: return@mapNotNull null
                        ScannerTextLine(
                            text = line.text,
                            left = box.left / bitmap.width.toFloat(),
                            top = box.top / bitmap.height.toFloat(),
                            right = box.right / bitmap.width.toFloat(),
                            bottom = box.bottom / bitmap.height.toFloat()
                        )
                    }
                }
            }.getOrDefault(emptyList())
        }.distinctBy { "${it.text.lowercase()}_${it.left}_${it.top}" }
    }

    private fun recognizeCrop(bitmap: Bitmap, detection: ScannerDetection): List<String> {
        val crop = cropBitmap(bitmap, detection) ?: return emptyList()
        val recognizers = when (detection.label) {
            "achievement", "dxscore", "maxdxscore", "lv" -> listOf(latinRecognizer)
            else -> listOf(japaneseRecognizer, chineseRecognizer, latinRecognizer)
        }
        return recognizers.flatMap { recognizer ->
            runCatching {
                val inputImage = InputImage.fromBitmap(crop, 0)
                val result = Tasks.await(recognizer.process(inputImage))
                result.textBlocks.flatMap { block ->
                    block.lines.map { it.text.trim() }.filter { it.isNotBlank() }
                }
            }.getOrDefault(emptyList())
        }.distinct()
    }

    private fun cropBitmap(bitmap: Bitmap, detection: ScannerDetection, upscale: Boolean = true): Bitmap? {
        val expansion = cropExpansionFor(detection.label)
        val boxWidth = (detection.right - detection.left).coerceAtLeast(0f)
        val boxHeight = (detection.bottom - detection.top).coerceAtLeast(0f)
        val leftNorm = detection.left - boxWidth * expansion.horizontal
        val rightNorm = detection.right + boxWidth * expansion.horizontal
        val topNorm = detection.top - boxHeight * expansion.vertical
        val bottomNorm = detection.bottom + boxHeight * expansion.vertical

        val left = (leftNorm * bitmap.width).roundToInt().coerceIn(0, bitmap.width - 1)
        val top = (topNorm * bitmap.height).roundToInt().coerceIn(0, bitmap.height - 1)
        val right = (rightNorm * bitmap.width).roundToInt().coerceIn(left + 1, bitmap.width)
        val bottom = (bottomNorm * bitmap.height).roundToInt().coerceIn(top + 1, bitmap.height)
        val width = right - left
        val height = bottom - top
        if (width <= 2 || height <= 2) return null
        val crop = Bitmap.createBitmap(bitmap, left, top, width, height)
        return if (upscale) upscaleForOcr(crop) else crop
    }

    private data class CropExpansion(
        val horizontal: Float,
        val vertical: Float
    )

    private fun cropExpansionFor(label: String): CropExpansion = when (label) {
        "title" -> CropExpansion(horizontal = 0.12f, vertical = 1.10f)
        "achievement" -> CropExpansion(horizontal = 0.24f, vertical = 0.85f)
        "difficulty" -> CropExpansion(horizontal = 0.35f, vertical = 1.20f)
        "lv" -> CropExpansion(horizontal = 0.45f, vertical = 1.00f)
        "dx", "std", "utage" -> CropExpansion(horizontal = 0.30f, vertical = 0.90f)
        "dxscore", "maxdxscore", "kanji" -> CropExpansion(horizontal = 0.20f, vertical = 0.80f)
        else -> CropExpansion(horizontal = 0.08f, vertical = 0.30f)
    }

    private fun upscaleForOcr(crop: Bitmap): Bitmap {
        val targetMinHeight = 96
        val targetMinWidth = 320
        val scale = maxOf(
            1f,
            targetMinHeight / crop.height.toFloat(),
            targetMinWidth / crop.width.toFloat()
        ).coerceAtMost(4f)
        if (scale <= 1.01f) return crop
        return crop.scale(
            width = (crop.width * scale).roundToInt().coerceAtLeast(crop.width),
            height = (crop.height * scale).roundToInt().coerceAtLeast(crop.height),
            filter = true
        )
    }

    private fun parseFirstInt(text: String): Int? = Regex("""\d+""")
        .find(text.replace(" ", ""))
        ?.value
        ?.toIntOrNull()

    private fun parseBoundedInt(text: String, range: IntRange): Int? {
        return Regex("""\d+""")
            .findAll(text.replace(" ", ""))
            .flatMap { match ->
                val raw = match.value
                sequenceOf(raw, raw.drop(1).takeIf { raw.length == 5 })
            }
            .mapNotNull { it?.toIntOrNull() }
            .firstOrNull { it in range }
    }

    private fun parseAchievement(text: String): Double? {
        val compact = text
            .replace(" ", "")
            .replace(',', '.')
            .replace('O', '0')
            .replace('o', '0')
            .replace('g', '9')
            .replace('G', '9')
        Regex("""(\d{2,3})[.](\d{4})""").find(compact)?.let { match ->
            val value = "${match.groupValues[1]}.${match.groupValues[2]}".toDoubleOrNull()
            if (value != null && value in 0.0..101.0) return value
        }
        Regex("""(?<!\d)(\d{2,3})(\d{4})(?!\d)""").find(compact)?.let { match ->
            val value = "${match.groupValues[1]}.${match.groupValues[2]}".toDoubleOrNull()
            if (value != null && value in 0.0..101.0) return value
        }
        return null
    }

    private fun parseLevel(text: String): Double? {
        return ScannerTextParser.parseLevelText(text)
    }

    private fun parseLevelCandidates(texts: List<String>): Double? {
        return texts.firstNotNullOfOrNull { parseLevel(it) }
            ?: parseLevel(texts.joinToString(""))
            ?: parseLevel(texts.joinToString(" "))
    }

    private fun selectAchievement(
        cropOcr: List<String>,
        fullFrameLines: List<ScannerTextLine>
    ): Double? {
        data class Candidate(val value: Double, val score: Float)

        val cropCandidates = cropOcr.flatMap { text ->
            ScannerTextParser.parseCurrentAchievementCandidates(text).map { (value, score) ->
                Candidate(value, 180f + score)
            }
        }
        val frameCandidates = fullFrameLines.flatMap { line ->
            val currentCandidates = ScannerTextParser.parseCurrentAchievementCandidates(line.text)
            val current = currentCandidates.maxByOrNull { it.second }?.first
            val values = (currentCandidates + ScannerTextParser.parseAchievementValues(line.text).map { it to 0f })
                .distinctBy { it.first }
            values.map { (value, parseScore) ->
                val text = line.text.lowercase()
                val historicalPenalty = if (
                    text.contains("my best") ||
                    text.contains("我的最佳") ||
                    text.contains("歴代") ||
                    text.contains("best")
                ) {
                    120f
                } else {
                    0f
                }
                val isInferredCurrent = current != null && abs(current - value) < 0.0001
                val mainNumberBonus = (line.height * 400f) + (line.width * 40f)
                val effectiveHistoricalPenalty = if (isInferredCurrent) 0f else historicalPenalty
                val overHundredBonus = if (value >= 100.0) 12f else 0f
                val currentInferenceBonus = if (isInferredCurrent) 80f else 0f
                val score = mainNumberBonus -
                    effectiveHistoricalPenalty +
                    overHundredBonus +
                    currentInferenceBonus +
                    parseScore
                Candidate(value, score)
            }
        }

        val candidates = (cropCandidates + frameCandidates)
            .filter { it.value in 0.0..101.0 }
        val plausibleCandidates = candidates.filter { it.value >= 80.0 }
        return plausibleCandidates.ifEmpty { candidates }
            .maxWithOrNull(compareBy<Candidate> { it.score }.thenBy { it.value })
            ?.value
    }

    private fun selectDxScorePair(
        cropOcr: List<String>,
        fullFrameLines: List<ScannerTextLine>,
        preferDxLabel: Boolean
    ): Pair<Int, Int>? {
        data class PairCandidate(val value: Pair<Int, Int>, val score: Int)

        val lines = cropOcr + fullFrameLines.map { it.text }
        val joined = lines.joinToString("\n")
        val candidates = mutableListOf<PairCandidate>()

        Regex("""(?<!\d)(\d{3,4})\s*[／/]\s*(\d{3,4})(?!\d)""")
            .findAll(joined)
            .mapNotNull { match ->
                val first = match.groupValues[1].toIntOrNull()
                val second = match.groupValues[2].toIntOrNull()
                if (first != null && second != null && first <= second && second <= 9999) {
                    val line = lines.firstOrNull { it.contains(match.value) }.orEmpty()
                    PairCandidate(first to second, dxPairLineScore(line, first, second, preferDxLabel))
                } else {
                    null
                }
            }
            .forEach(candidates::add)

        val normalizedLines = lines.map { it.trim() }.filter(String::isNotBlank)
        val partialCurrent = normalizedLines.firstNotNullOfOrNull { line ->
            Regex("""(?<!\d)(\d{3,4})\s*[／/]\s*(?:[-–—]|\d)?(?!\d)""")
                .find(line)
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
        val partialMax = normalizedLines.firstNotNullOfOrNull { line ->
            Regex("""(?:[／/]\s*)?(\d{3,4})(?!\d)""")
                .matchEntire(line.replace(Regex("""^[A-Za-z]+"""), "").trim())
                ?.groupValues
                ?.getOrNull(1)
                ?.toIntOrNull()
        }
        if (partialCurrent != null && partialMax != null && partialCurrent <= partialMax) {
            candidates += PairCandidate(partialCurrent to partialMax, 120)
        }

        return candidates
            .filter { it.score > -100 }
            .maxWithOrNull(
                compareBy<PairCandidate> { it.score }
                    .thenBy { it.value.second }
                    .thenBy { it.value.first }
            )
            ?.value
    }

    private fun dxPairLineScore(line: String, current: Int, max: Int, preferDxLabel: Boolean): Int {
        val lower = line.lowercase()
        var score = 0
        if (lower.contains("dx") || line.contains("DX分数") || line.contains("dx分数", ignoreCase = true) || line.contains("でらっくスコア")) score += 70
        if (preferDxLabel) score += 10
        if (
            lower.contains("max combo") ||
            lower.contains("max sync") ||
            line.contains("最大连击") ||
            line.contains("最大同步") ||
            (line.contains("最大") && !line.contains("DX分数") && !line.contains("dx分数", ignoreCase = true))
        ) {
            score -= 180
        }
        if (lower.contains("combo") || lower.contains("sync")) score -= 80
        if (max >= 900) score += 20
        if (max >= 1400) score += 20
        if (max >= 1800) score += 20
        if (max < 900) score -= 60
        if (current < 300) score -= 40
        return score
    }

    private fun selectBestDetectionLabel(
        byLabel: Map<String, List<ScannerDetection>>,
        labels: List<String>,
        minimumScore: Float = 0.0f
    ): String? {
        return labels
            .mapNotNull { label ->
                val score = byLabel[label]?.maxOfOrNull { it.score } ?: return@mapNotNull null
                label.takeIf { score >= minimumScore }?.let { it to score }
            }
            .maxWithOrNull(compareBy<Pair<String, Float>> { it.second }.thenBy { labels.indexOf(it.first) * -1 })
            ?.first
    }

    private fun selectChartType(byLabel: Map<String, List<ScannerDetection>>): String? {
        val dxScore = byLabel["dx"]?.maxOfOrNull { it.score }
        val stdScore = byLabel["std"]?.maxOfOrNull { it.score }
        return when {
            dxScore == null && stdScore == null -> null
            dxScore != null && stdScore == null -> "dx"
            stdScore != null && dxScore == null -> "std"
            dxScore != null && stdScore != null && dxScore + 0.16f >= stdScore -> "dx"
            else -> "std"
        }
    }

    private fun isScoreTitleNoise(text: String): Boolean {
        val compact = text.lowercase()
            .replace(" ", "")
            .replace("》", "")
            .replace(")", "")
            .replace(">", "")
        return compact.isBlank() ||
            compact.contains("mybest") ||
            compact.contains("newrecord") ||
            compact.contains("我的最佳") ||
            compact.contains("exp") ||
            compact.contains("expert") ||
            compact.contains("master") ||
            compact.contains("basic") ||
            compact.contains("advanced") ||
            compact.contains("達成率") ||
            compact.contains("dx分数") ||
            ScannerTextParser.parseAchievementText(text) != null
    }

    private fun selectRecognitionType(
        scoreRecognition: ScannerRecognition,
        chooseRecognition: ScannerRecognition,
        scoreDetections: List<ScannerDetection>,
        chooseDetections: List<ScannerDetection>
    ): ScannerImageType {
        val scoreSignals = scoreRecognition.scoreSignalCount()
        val chooseSignals = chooseRecognition.titleCandidates.size + chooseDetections.count { it.label == "title" }
        val scoreConfidence = scoreDetections.sumOf { detectionWeight(it.label, it.score).toDouble() }.toFloat()
        val chooseConfidence = chooseDetections.sumOf { it.score.toDouble() }.toFloat()

        if (scoreSignals >= 3) return ScannerImageType.SCORE
        if (scoreSignals >= 2 && scoreConfidence >= chooseConfidence * 0.85f) return ScannerImageType.SCORE
        if (chooseSignals > 0 && scoreSignals == 0) return ScannerImageType.CHOOSE
        if (chooseSignals >= 2 && chooseConfidence > scoreConfidence * 1.25f) return ScannerImageType.CHOOSE
        if (scoreRecognition.imageType == ScannerImageType.SCORE) return ScannerImageType.SCORE
        if (chooseRecognition.imageType == ScannerImageType.CHOOSE) return ScannerImageType.CHOOSE
        return ScannerImageType.UNKNOWN
    }

    private fun ScannerRecognition.scoreSignalCount(): Int {
        val nonTitleDetections = detections.count { it.label != "title" }
        return nonTitleDetections + listOfNotNull(rate, difficulty, dxScore, maxDxScore, fc, fs, level, kanji).size
    }

    private fun detectionWeight(label: String, score: Float): Float {
        val weight = when (label) {
            "achievement", "difficulty", "dxscore", "maxdxscore", "lv" -> 1.5f
            "dx", "std", "utage" -> 1.25f
            "title" -> 0.6f
            else -> 1f
        }
        return score * weight
    }

    private fun inferChartType(
        difficulty: String?,
        kanji: String?,
        titleCandidates: List<String>
    ): String? {
        if (difficulty == "utage") return "utage"
        if (!kanji.isNullOrBlank()) return "utage"
        if (titleCandidates.any { ScannerTextParser.extractKanjiFromTitleCandidates(emptyList(), it) != null }) {
            return "utage"
        }
        return null
    }

    private fun classifyDifficultyColor(crop: Bitmap?): String? {
        crop ?: return null
        val sampleStepX = maxOf(1, crop.width / 24)
        val sampleStepY = maxOf(1, crop.height / 24)
        var hueSin = 0.0
        var hueCos = 0.0
        var saturation = 0.0
        var brightness = 0.0
        var count = 0
        val hsv = FloatArray(3)
        var y = 0
        while (y < crop.height) {
            var x = 0
            while (x < crop.width) {
                val color = crop.getPixel(x, y)
                Color.colorToHSV(color, hsv)
                if (hsv[1] >= 0.18f && hsv[2] >= 0.18f) {
                    val radians = Math.toRadians(hsv[0].toDouble())
                    hueSin += kotlin.math.sin(radians)
                    hueCos += kotlin.math.cos(radians)
                    saturation += hsv[1]
                    brightness += hsv[2]
                    count += 1
                }
                x += sampleStepX
            }
            y += sampleStepY
        }
        if (count == 0) return null
        var hue = Math.toDegrees(kotlin.math.atan2(hueSin / count, hueCos / count))
        if (hue < 0) hue += 360.0
        val sat = saturation / count
        val bri = brightness / count

        return when {
            hue > 320 || hue < 20 -> "expert"
            hue >= 20 && hue < 60 -> "advanced"
            hue >= 60 && hue < 160 -> "basic"
            hue >= 260 && hue <= 320 -> if (bri > 0.75 && sat < 0.5) "remaster" else "master"
            abs(hue - 280.0) < 50.0 -> "master"
            else -> null
        }
    }

    private fun buildDebugText(
        classification: TfliteImageClassifier.Classification?,
        scoreDetections: List<ScannerDetection>,
        chooseDetections: List<ScannerDetection>,
        recognitionText: String
    ): String = buildString {
        appendLine(debugModelInfo)
        if (classification != null) {
            appendLine("classification=${classification.label}@${String.format(Locale.US, "%.2f", classification.score)}")
        }
        if (scoreDetections.isNotEmpty()) {
            appendLine("scoreDetections=" + scoreDetections.joinToString { "${it.label}@${String.format(Locale.US, "%.2f", it.score)}" })
        }
        if (chooseDetections.isNotEmpty()) {
            appendLine("chooseDetections=" + chooseDetections.joinToString { "${it.label}@${String.format(Locale.US, "%.2f", it.score)}" })
        }
        if (recognitionText.isNotBlank()) append(recognitionText)
    }.trim()

    companion object {
        private val SCORE_LABELS = listOf(
            "achievement",
            "ap",
            "app",
            "difficulty",
            "dx",
            "dxscore",
            "fc",
            "fcp",
            "fdx",
            "fdxp",
            "fs",
            "fsp",
            "kanji",
            "lv",
            "maxdxscore",
            "std",
            "sync",
            "title",
            "utage"
        )

        private val CHOOSE_LABELS = listOf("title")
        private val CLASSIFIER_LABELS = listOf("choose", "score")
        private const val CLASSIFIER_CONFIDENCE_THRESHOLD = 0.55f
    }
}
