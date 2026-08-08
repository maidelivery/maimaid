package net.krtl.maimaid.scanner.text

import net.krtl.maimaid.scanner.model.ScannerImageType
import net.krtl.maimaid.scanner.model.ScannerRecognition
import net.krtl.maimaid.scanner.model.ScannerTextLine
import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.min

object ScannerTextParser {
    private val rateRegex = Regex("""(\d{1,3})[.,](\d{4})""")
    private val compactRateRegex = Regex("""(?<!\d)(\d{2,3})(\d{4})(?!\d)""")
    private val dxPairRegex = Regex("""(?<!\d)(\d{3,6})\s*[／/]\s*(\d{3,6})(?!\d)""")
    private val digitsRegex = Regex("""\d+""")
    private val kanjiBracketRegex = Regex("""^(?:【([^】]+)】|\[([^\]]+)]|［([^］]+)］)""")
    private val noiseExact = setOf(
        "dx",
        "std",
        "utage",
        "master",
        "remaster",
        "expert",
        "advanced",
        "basic",
        "achievement",
        "score",
        "dxscore",
        "rating",
        "sync",
        "combo",
        "fc",
        "fs",
        "lv"
    )

    private val ocrSubstitutions: Map<Char, List<Char>> = mapOf(
        'O' to listOf('0', 'D', 'Q'),
        '0' to listOf('O', 'D'),
        'D' to listOf('O', '0'),
        'I' to listOf('1', 'l', 'L'),
        '1' to listOf('I', 'l'),
        'l' to listOf('I', '1'),
        'L' to listOf('I', '1'),
        'S' to listOf('5', 'Z'),
        '5' to listOf('S'),
        'Z' to listOf('S', '2'),
        '2' to listOf('Z'),
        'B' to listOf('8'),
        '8' to listOf('B'),
        'G' to listOf('6', 'C'),
        '6' to listOf('G'),
        'C' to listOf('G'),
        'Q' to listOf('O', '0'),
        '職' to listOf('蔵', '藏', '概'),
        '蔵' to listOf('職', '藏'),
        '藏' to listOf('職', '蔵'),
        '黒' to listOf('黑'),
        '黑' to listOf('黒'),
        '響' to listOf('郷'),
        '郷' to listOf('響'),
        '桜' to listOf('櫻'),
        '櫻' to listOf('桜'),
        '竜' to listOf('龍'),
        '龍' to listOf('竜'),
        '斬' to listOf('斷'),
        '国' to listOf('國'),
        '國' to listOf('国'),
        '円' to listOf('圓'),
        '圓' to listOf('円'),
        '変' to listOf('變'),
        '變' to listOf('変'),
        '戦' to listOf('戰'),
        '戰' to listOf('戦'),
        '関' to listOf('關'),
        '關' to listOf('関'),
        '広' to listOf('廣'),
        '廣' to listOf('広'),
        '駅' to listOf('驛'),
        '驛' to listOf('駅'),
        '帯' to listOf('帶'),
        '帶' to listOf('帯'),
        '極' to listOf('极'),
        '极' to listOf('極'),
        '転' to listOf('轉'),
        '轉' to listOf('転'),
        '検' to listOf('檢'),
        '檢' to listOf('検'),
        '権' to listOf('權'),
        '權' to listOf('権'),
        '譲' to listOf('讓'),
        '讓' to listOf('譲'),
        '説' to listOf('說'),
        '說' to listOf('説'),
        '読' to listOf('讀'),
        '讀' to listOf('読'),
        '弾' to listOf('彈'),
        '彈' to listOf('弾'),
        '個' to listOf('箇'),
        '箇' to listOf('個'),
        '号' to listOf('號'),
        '號' to listOf('号'),
        '声' to listOf('聲'),
        '聲' to listOf('声'),
        '栄' to listOf('榮'),
        '榮' to listOf('栄'),
        '営' to listOf('營'),
        '營' to listOf('営'),
        '様' to listOf('樣'),
        '樣' to listOf('様'),
        '画' to listOf('畫'),
        '畫' to listOf('画')
    )

    fun parse(lines: List<ScannerTextLine>): ScannerRecognition {
        val cleanedLines = lines
            .map { it.copy(text = normalizeWhitespace(it.text)) }
            .filter { it.text.isNotBlank() }
            .distinctBy { normalizeWhitespace(it.text).lowercase() }

        val joinedText = cleanedLines.joinToString("\n") { it.text }
        val normalizedJoined = joinedText.lowercase()

        val difficulty = parseDifficultyFromOcr(joinedText)
        val type = parseChartType(joinedText, difficulty)
        val rate = parseAchievement(cleanedLines)
        val dxPair = parseDxScores(cleanedLines)
        val level = parseLevel(cleanedLines)
        val fc = parseFc(joinedText)
        val fs = parseFs(joinedText)
        val titleCandidates = extractTitleCandidates(cleanedLines)
        val title = titleCandidates.firstOrNull()
        val kanji = extractKanjiFromTitleCandidates(titleCandidates, title)
        val imageType = classifyImageType(
            normalizedJoined = normalizedJoined,
            titleCandidates = titleCandidates,
            rate = rate,
            difficulty = difficulty,
            dxScore = dxPair.first,
            maxDxScore = dxPair.second,
            fc = fc,
            fs = fs
        )

        return ScannerRecognition(
            imageType = imageType,
            title = title,
            titleCandidates = titleCandidates,
            rate = rate,
            difficulty = difficulty,
            type = type,
            dxScore = dxPair.first,
            maxDxScore = dxPair.second,
            fc = fc,
            fs = fs,
            level = level,
            maxCombo = dxPair.second?.div(3),
            kanji = kanji,
            debugLines = cleanedLines,
            debugText = joinedText
        )
    }

    fun generateOcrVariants(text: String, maxVariants: Int = 8): List<String> {
        val variants = mutableListOf(text)
        val queue = ArrayDeque<String>()
        val seen = linkedSetOf(text)
        queue.addLast(text)

        while (queue.isNotEmpty() && variants.size < maxVariants) {
            val current = queue.removeFirst()
            for ((original, replacements) in ocrSubstitutions) {
                if (current.contains(original)) {
                    for (replacement in replacements) {
                        val variant = current.replace(original, replacement)
                        if (seen.add(variant)) {
                            variants += variant
                            queue.addLast(variant)
                        }
                    }
                }
                for (replacement in replacements) {
                    if (current.contains(replacement)) {
                        val variant = current.replace(replacement, original)
                        if (seen.add(variant)) {
                            variants += variant
                            queue.addLast(variant)
                        }
                    }
                }
            }
        }

        return variants
    }

    fun stripUtagePrefix(title: String): String {
        val stripped = title
            .replace(Regex("""^【[^】]+】\s*"""), "")
            .replace(Regex("""^\[[^\]]+]\s*"""), "")
            .replace(Regex("""^［[^］]+］\s*"""), "")
            .trim()
        // Width-insensitive folding: NFKC normalizes full-width Latin letters, digits, and
        // punctuation to their ASCII equivalents (matching iOS .widthInsensitive folding).
        return Normalizer.normalize(stripped, Normalizer.Form.NFKC)
    }

    fun parseAchievementText(text: String): Double? {
        val values = parseAchievementValues(text)
        return values.firstOrNull { it >= 80.0 } ?: values.firstOrNull()
    }

    fun parseAchievementValues(text: String): List<Double> {
        val compact = normalizeNumericOcr(text)
            .replace(" ", "")
        val values = linkedSetOf<Double>()
        rateRegex.findAll(compact).forEach { match ->
            val value = "${match.groupValues[1]}.${match.groupValues[2]}".toDoubleOrNull()
            if (value != null && value in 0.0..101.0) values += value
        }
        compactRateRegex.findAll(compact).forEach { match ->
            val value = "${match.groupValues[1]}.${match.groupValues[2]}".toDoubleOrNull()
            if (value != null && value in 0.0..101.0) values += value
        }
        Regex("""(?<=%)\.(\d{4})""").findAll(compact).forEach { match ->
            val value = "0.${match.groupValues[1]}".toDoubleOrNull()
            if (value != null && value in 0.0..5.0) values += value
        }
        return values.toList()
    }

    fun parseCurrentAchievementCandidates(text: String): List<Pair<Double, Float>> {
        val compact = normalizeNumericOcr(text).replace(" ", "")
        val values = parseAchievementValues(text)
        if (values.isEmpty()) return emptyList()

        val candidates = mutableListOf<Pair<Double, Float>>()
        Regex("""(\d{2,3})[.,](\d{4})%\.(\d{4})""")
            .findAll(compact)
            .forEach { match ->
                val best = "${match.groupValues[1]}.${match.groupValues[2]}".toDoubleOrNull()
                val delta = "0.${match.groupValues[3]}".toDoubleOrNull()
                if (best != null && delta != null && best in 80.0..101.0 && best + delta <= 101.0) {
                    candidates += roundAchievement(best + delta) to 130f
                }
            }
        values.zipWithNext().forEach { (first, second) ->
            when {
                first == 0.0 && second in 80.0..101.0 -> candidates += second to 120f
                first in 80.0..101.0 && second in 0.0001..5.0 && first + second <= 101.0 -> {
                    candidates += roundAchievement(first + second) to 110f
                }
            }
        }

        values.filter { it >= 80.0 }.forEachIndexed { index, value ->
            candidates += value to (50f - index)
        }
        if (candidates.isEmpty()) {
            values.forEachIndexed { index, value -> candidates += value to (10f - index) }
        }
        return candidates
            .groupBy({ it.first }, { it.second })
            .map { (value, scores) -> value to scores.max() }
    }

    fun parseCurrentAchievementText(text: String): Double? {
        return parseCurrentAchievementCandidates(text)
            .maxWithOrNull(compareBy<Pair<Double, Float>> { it.second }.thenBy { it.first })
            ?.first
    }

    fun parseDxScores(lines: List<ScannerTextLine>): Pair<Int?, Int?> {
        lines.forEach { line ->
            dxPairRegex.find(line.text)?.let { match ->
                val first = match.groupValues[1].toIntOrNull()
                val second = match.groupValues[2].toIntOrNull()
                if (first != null && second != null && first <= second) {
                    return first to second
                }
            }
        }

        val joined = lines.joinToString(" ") { it.text }
        dxPairRegex.find(joined)?.let { match ->
            val first = match.groupValues[1].toIntOrNull()
            val second = match.groupValues[2].toIntOrNull()
            if (first != null && second != null && first <= second) {
                return first to second
            }
        }

        val numberPool = lines
            .asSequence()
            .map { it.text }
            .filter { it.contains("dx", ignoreCase = true) || it.contains("score", ignoreCase = true) }
            .flatMap { digitsRegex.findAll(it).map { match -> match.value.toIntOrNull() } }
            .filterNotNull()
            .filter { it >= 1000 }
            .sortedDescending()
            .toList()

        return if (numberPool.size >= 2) {
            val max = numberPool.first()
            val current = numberPool.firstOrNull { it <= max } ?: max
            current to max
        } else {
            null to null
        }
    }

    fun parseLevelText(text: String): Double? {
        val compact = text.replace(Regex("""\s+"""), "")
        val explicit = Regex("""(?:lv|level|v)([uUiIlL|!1]?\d|1[0-5]|[1-9])""", RegexOption.IGNORE_CASE)
            .find(compact)
            ?.groupValues
            ?.getOrNull(1)
            ?.replace(Regex("""[uUiIlL|!]"""), "1")
            ?.toDoubleOrNull()
        if (explicit != null) return explicit.takeIf { it in 1.0..15.0 }

        val normalized = compact.uppercase()
            .replace('A', '1')
            .replace('T', '1')
            .replace('O', '0')
        if (compact.length <= 4 && normalized == "110") return 10.0

        if (compact.length > 3) return null
        return compact.toDoubleOrNull()?.takeIf { it in 1.0..15.0 }
    }

    fun parseFirstInt(text: String): Int? = Regex("""\d+""")
        .find(text.replace(" ", ""))
        ?.value
        ?.toIntOrNull()

    fun parseFc(text: String): String? {
        val normalized = text.lowercase().replace(" ", "")
        return when {
            normalized.contains("app") || normalized.contains("ap+") -> "app"
            normalized.contains("fcp") || normalized.contains("fc+") -> "fcp"
            normalized.contains("ap") -> "ap"
            normalized.contains("fc") -> "fc"
            else -> null
        }
    }

    fun parseFs(text: String): String? {
        val normalized = text.lowercase().replace(" ", "")
        return when {
            normalized.contains("fdxp") || normalized.contains("fdx+") -> "fsdp"
            normalized.contains("fsp") || normalized.contains("fs+") -> "fsp"
            normalized.contains("fdx") -> "fsd"
            normalized.contains("fs") -> "fs"
            normalized.contains("sync") -> "sync"
            else -> null
        }
    }

    fun parseChartType(text: String, difficulty: String?): String? {
        val normalized = text.lowercase()
        return when {
            difficulty == "utage" || normalized.contains("utage") || normalized.contains("宴") -> "utage"
            Regex("""(^|[^a-z])std([^a-z]|$)""").containsMatchIn(normalized) || normalized.contains("standard") -> "std"
            Regex("""(^|[^a-z])dx([^a-z]|$)""").containsMatchIn(normalized) || normalized.contains("deluxe") -> "dx"
            else -> null
        }
    }

    fun extractTitleCandidates(lines: List<ScannerTextLine>): List<String> {
        data class Candidate(val text: String, val score: Float)

        return lines.asSequence().mapNotNull { line ->
            val text = normalizeWhitespace(line.text).trim()
            val compact = text.lowercase().replace(" ", "")
            if (text.length !in 2..64) return@mapNotNull null
            if (text.all { !it.isLetter() && !isCjk(it) }) return@mapNotNull null
            if (compact in noiseExact) return@mapNotNull null
            if (compact.startsWith("achievement")) return@mapNotNull null
            if (compact.startsWith("dxscore")) return@mapNotNull null
            if (compact.startsWith("level")) return@mapNotNull null
            if (text.count { it.isLetterOrDigit() || isCjk(it) } < 2) return@mapNotNull null

            val letters = text.count { it.isLetter() || isCjk(it) }
            val digits = text.count(Char::isDigit)
            val centerBonus = 1f - abs(line.centerY - 0.32f)
            val widthBonus = line.width.coerceIn(0f, 1f)
            val score = letters * 2f - digits * 0.4f + widthBonus * 30f + centerBonus * 12f
            Candidate(text, score)
        }
            .sortedByDescending { it.score }
            .distinctBy { it.text.lowercase() }
            .map { it.text }
            .take(6)
            .toList()
    }

    fun classifyImageType(
        normalizedJoined: String,
        titleCandidates: List<String>,
        rate: Double?,
        difficulty: String?,
        dxScore: Int?,
        maxDxScore: Int?,
        fc: String?,
        fs: String?
    ): ScannerImageType {
        val scoreSignals = listOfNotNull(
            rate,
            difficulty,
            dxScore,
            maxDxScore,
            fc,
            fs
        ).size + listOf("achievement", "master", "expert", "advanced", "basic", "dx score").count { normalizedJoined.contains(it) }

        return when {
            scoreSignals >= 2 -> ScannerImageType.SCORE
            titleCandidates.isNotEmpty() -> ScannerImageType.CHOOSE
            else -> ScannerImageType.UNKNOWN
        }
    }

    fun extractKanjiFromTitleCandidates(candidates: List<String>, fallback: String?): String? {
        val allTexts = buildList {
            fallback?.let(::add)
            addAll(candidates)
        }
        return allTexts.firstNotNullOfOrNull { text ->
            kanjiBracketRegex.find(text.trim())
                ?.groupValues
                ?.drop(1)
                ?.firstOrNull { it.isNotBlank() }
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
        }
    }

    fun normalizedSongMatchTitle(title: String): String {
        // Port of iOS .folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive]).
        // NFKC handles width-insensitive and compatibility normalization.
        // NFD + strip combining marks handles diacritic-insensitive folding.
        val nfkc = Normalizer.normalize(stripUtagePrefix(title), Normalizer.Form.NFKC)
        val decomposed = Normalizer.normalize(nfkc, Normalizer.Form.NFD)
        val withoutDiacritics = decomposed.replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
        return withoutDiacritics
            .lowercase()
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    fun parseDifficultyFromOcr(text: String): String? {
        val lower = text.lowercase()
            .replace(" ", "")
            .replace(":", "")

        if (lower.contains("utage") || lower.contains("宴")) return "utage"
        if (lower.contains("remaster") || lower.contains("re:master") || lower.contains("reマスター")) return "remaster"
        if (lower.contains("master") || lower.contains("マスター")) return "master"
        if (lower.contains("expert") || lower.contains("エキスパート")) return "expert"
        if (lower.contains("advanced") || lower.contains("アドバンス")) return "advanced"
        if (lower.contains("basic") || lower.contains("ベーシック")) return "basic"
        if (lower.contains("re") && (lower.contains("ma") || lower.contains("ster"))) return "remaster"
        if (lower.contains("mas")) return "master"
        if (lower.contains("exp")) return "expert"
        if (lower.contains("xpert") || lower.contains("pert")) return "expert"
        if (lower.contains("adv")) return "advanced"
        if (lower.contains("bas")) return "basic"

        val alphaOnly = lower.filter(Char::isLetter)
        if (alphaOnly.startsWith("u")) {
            val nonUtage = listOf("under", "ultra", "up", "union", "unit", "universe")
            if (nonUtage.none(alphaOnly::startsWith)) {
                return "utage"
            }
        }
        return null
    }

    fun levenshteinDistance(s1: String, s2: String): Int {
        val a = s1.lowercase().toCharArray()
        val b = s2.lowercase().toCharArray()
        if (a.isEmpty()) return b.size
        if (b.isEmpty()) return a.size

        val dist = Array(a.size + 1) { IntArray(b.size + 1) }
        for (i in a.indices.plus(a.size)) dist[i][0] = i
        for (j in b.indices.plus(b.size)) dist[0][j] = j

        for (i in 1..a.size) {
            for (j in 1..b.size) {
                dist[i][j] = if (a[i - 1] == b[j - 1]) {
                    dist[i - 1][j - 1]
                } else {
                    min(
                        min(dist[i - 1][j] + 1, dist[i][j - 1] + 1),
                        dist[i - 1][j - 1] + 1
                    )
                }
            }
        }
        return dist[a.size][b.size]
    }

    private fun parseAchievement(lines: List<ScannerTextLine>): Double? {
        return lines.firstNotNullOfOrNull { line ->
            parseCurrentAchievementText(line.text)
        }
    }

    private fun parseLevel(lines: List<ScannerTextLine>): Double? {
        val explicit = lines.firstNotNullOfOrNull { line ->
            if (!line.text.contains("lv", ignoreCase = true) && !line.text.contains("level", ignoreCase = true)) return@firstNotNullOfOrNull null
            parseLevelText(line.text)
        }
        if (explicit != null) return explicit
        return lines.firstNotNullOfOrNull { line ->
            val trimmed = line.text.trim()
            if (trimmed.length > 3) return@firstNotNullOfOrNull null
            trimmed.toDoubleOrNull()?.takeIf { it in 1.0..15.0 }
        }
    }

    private fun normalizeWhitespace(text: String): String = text.replace(Regex("""\s+"""), " ").trim()

    private fun normalizeNumericOcr(text: String): String = buildString(text.length) {
        text.forEachIndexed { index, char ->
            val previous = text.getOrNull(index - 1)
            val next = text.getOrNull(index + 1)
            val numericNeighbor = previous?.isDigit() == true || next?.isDigit() == true
            append(
                when {
                    char == ',' || char == '。' || char == '·' -> '.'
                    char == 'O' || char == 'o' -> '0'
                    (char == 'g' || char == 'G') && numericNeighbor -> '9'
                    (char == 's' || char == 'S') && numericNeighbor -> '5'
                    else -> char
                }
            )
        }
    }

    private fun roundAchievement(value: Double): Double = (value * 10_000.0).let { kotlin.math.round(it) / 10_000.0 }

    private fun isCjk(char: Char): Boolean {
        val block = Character.UnicodeBlock.of(char)
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
            block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
            block == Character.UnicodeBlock.HIRAGANA ||
            block == Character.UnicodeBlock.KATAKANA
    }
}
