package net.krtl.maimaid.scanner

import android.graphics.BitmapFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import net.krtl.maimaid.scanner.analysis.ScannerAnalyzer
import net.krtl.maimaid.scanner.model.ScannerRecognition
import org.junit.Test
import org.junit.Assert.assertTrue
import org.junit.runner.RunWith
import java.util.Locale
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ScannerSampleRegressionTest {
    @Test
    fun mairesultSamples_matchExpectedFields() = runBlocking {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val testContext = instrumentation.context
        val samples = ScannerSampleDetailParser.parse(
            testContext.assets.open("detail.json").bufferedReader().use { it.readText() }
        )
        val analyzer = ScannerAnalyzer(context)
        val failures = mutableListOf<String>()

        try {
            samples.forEach { sample ->
                val fileName = "mairesult (${sample.id}).heic"
                val bitmap = testContext.assets.open(fileName).use { input ->
                    BitmapFactory.decodeStream(input)
                }
                if (bitmap == null) {
                    failures += "${sample.id}: failed to decode $fileName"
                    return@forEach
                }

                val recognition = analyzer.analyze(bitmap)
                failures += compare(sample, recognition).map { failure ->
                    "$failure\n${recognition.debugText}"
                }
            }
        } finally {
            analyzer.close()
        }

        val strict = InstrumentationRegistry.getArguments().getString("scannerSamplesStrict") == "true"
        if (failures.isNotEmpty()) {
            println(failures.joinToString(separator = "\n"))
        }
        if (strict) {
            assertTrue(failures.joinToString(separator = "\n"), failures.isEmpty())
        }
    }

    private fun compare(
        sample: ExpectedScannerSample,
        recognition: ScannerRecognition
    ): List<String> {
        val failures = mutableListOf<String>()
        val prefix = sample.id

        if (recognition.titleCandidates.none { titleMatches(it, sample.title) } && !titleMatches(recognition.title, sample.title)) {
            failures += "$prefix title expected=${sample.title} actual=${recognition.title} candidates=${recognition.titleCandidates.joinToString(" | ")}"
        }
        if (!equalsWithin(recognition.rate, sample.achievement, tolerance = 0.0001)) {
            failures += "$prefix achievement expected=${sample.achievement} actual=${recognition.rate}"
        }
        if (!recognition.difficulty.equals(sample.difficulty.lowercase(), ignoreCase = true)) {
            failures += "$prefix difficulty expected=${sample.difficulty.lowercase()} actual=${recognition.difficulty}"
        }
        if (!levelMatches(recognition.level, sample.lv)) {
            failures += "$prefix lv expected=${sample.lv} actual=${recognition.level}"
        }
        compareNullableInt(prefix, "dxscore", sample.dxScore, recognition.dxScore, failures)
        compareNullableInt(prefix, "maxdxscore", sample.maxDxScore, recognition.maxDxScore, failures)
        compareFlag(prefix, "dx", sample.dx, recognition.type == "dx", failures)
        compareFlag(prefix, "std", sample.std, recognition.type == "std", failures)
        compareFlag(prefix, "utage", sample.utage, recognition.type == "utage", failures)
        compareFlag(prefix, "sync", sample.sync, recognition.fs == "sync", failures)
        compareFlag(prefix, "fc", sample.fc, recognition.fc == "fc", failures)
        compareFlag(prefix, "fcp", sample.fcp, recognition.fc == "fcp", failures)
        compareFlag(prefix, "ap", sample.ap, recognition.fc == "ap", failures)
        compareFlag(prefix, "app", sample.app, recognition.fc == "app", failures)
        compareFlag(prefix, "fs", sample.fs, recognition.fs == "fs", failures)
        compareFlag(prefix, "fsp", sample.fsp, recognition.fs == "fsp", failures)
        compareFlag(prefix, "fdx", sample.fdx, recognition.fs == "fsd", failures)
        compareFlag(prefix, "fdxp", sample.fdxp, recognition.fs == "fsdp", failures)

        return failures
    }

    private fun compareNullableInt(
        prefix: String,
        label: String,
        expected: Int?,
        actual: Int?,
        failures: MutableList<String>
    ) {
        if (expected != actual) {
            failures += "$prefix $label expected=$expected actual=$actual"
        }
    }

    private fun compareFlag(
        prefix: String,
        label: String,
        expected: Boolean,
        actual: Boolean,
        failures: MutableList<String>
    ) {
        if (expected != actual) {
            failures += "$prefix $label expected=$expected actual=$actual"
        }
    }

    private fun equalsWithin(actual: Double?, expected: Double?, tolerance: Double): Boolean {
        if (actual == null || expected == null) return actual == expected
        return abs(actual - expected) <= tolerance
    }

    private fun levelMatches(actual: Double?, expected: String): Boolean {
        val normalized = expected.trim().removeSuffix("+")
        val expectedBase = normalized.toDoubleOrNull() ?: return actual == null
        return actual?.toInt() == expectedBase.toInt()
    }

    private fun titleMatches(actual: String?, expected: String): Boolean {
        actual ?: return false
        val normalizedActual = normalizeTitleForComparison(actual)
        val normalizedExpected = normalizeTitleForComparison(expected)
        if (normalizedActual == normalizedExpected) return true
        if (normalizedActual.contains(normalizedExpected) || normalizedExpected.contains(normalizedActual)) return true
        val distance = levenshteinDistance(normalizedActual, normalizedExpected)
        return distance <= maxOf(1, normalizedExpected.length / 8)
    }

    private fun normalizeTitleForComparison(title: String): String {
        return java.text.Normalizer.normalize(title, java.text.Normalizer.Form.NFKC)
            .lowercase(Locale.US)
            .replace("·", "・")
            .replace("♡", "")
            .replace("○", "")
            .replace("・", "")
            .replace(Regex("""[\s、。，.()（）!！~～]"""), "")
    }

    private fun levenshteinDistance(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        val dist = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dist[i][0] = i
        for (j in 0..b.length) dist[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dist[i][j] = minOf(
                    dist[i - 1][j] + 1,
                    dist[i][j - 1] + 1,
                    dist[i - 1][j - 1] + cost
                )
            }
        }
        return dist[a.length][b.length]
    }
}

private data class ExpectedScannerSample(
    val id: String,
    val achievement: Double?,
    val title: String,
    val difficulty: String,
    val lv: String,
    val dxScore: Int?,
    val maxDxScore: Int?,
    val dx: Boolean,
    val sync: Boolean,
    val std: Boolean,
    val fc: Boolean,
    val fcp: Boolean,
    val ap: Boolean,
    val fsp: Boolean,
    val app: Boolean,
    val fdx: Boolean,
    val utage: Boolean,
    val kanji: String?,
    val fdxp: Boolean,
    val fs: Boolean
)

private object ScannerSampleDetailParser {
    fun parse(raw: String): List<ExpectedScannerSample> {
        return raw
            .replace("\r\n", "\n")
            .split(Regex("""\n{2,}"""))
            .mapNotNull { block -> parseBlock(block.trim()) }
    }

    private fun parseBlock(block: String): ExpectedScannerSample? {
        if (block.isBlank()) return null
        val lines = block.lines().map(String::trim).filter(String::isNotBlank)
        val id = lines.firstOrNull() ?: return null
        val values = lines.drop(1).associate { line ->
            val key = line.substringBefore(' ').trim()
            val value = line.substringAfter(' ', missingDelimiterValue = "").trim()
            key.lowercase(Locale.US) to value
        }

        return ExpectedScannerSample(
            id = id,
            achievement = values["achievement"]?.removeSuffix("%")?.toDoubleOrNull(),
            title = values.getValue("title"),
            difficulty = values.getValue("difficulty"),
            lv = values.getValue("lv"),
            dxScore = values["dxscore"].toNullableInt(),
            maxDxScore = values["maxdxscore"].toNullableInt(),
            dx = values["dx"].toFlag(),
            sync = values["sync"].toFlag(),
            std = values["std"].toFlag(),
            fc = values["fc"].toFlag(),
            fcp = values["fcp"].toFlag(),
            ap = values["ap"].toFlag(),
            fsp = values["fsp"].toFlag(),
            app = values["app"].toFlag(),
            fdx = values["fdx"].toFlag(),
            utage = values["utage"].toFlag(),
            kanji = values["kanji"]?.takeUnless { it == "n" },
            fdxp = values["fdxp"].toFlag(),
            fs = values["fs"].toFlag()
        )
    }

    private fun String?.toFlag(): Boolean = this?.equals("y", ignoreCase = true) == true

    private fun String?.toNullableInt(): Int? {
        val value = this ?: return null
        if (value.equals("n", ignoreCase = true)) return null
        return value.toIntOrNull()
    }
}
