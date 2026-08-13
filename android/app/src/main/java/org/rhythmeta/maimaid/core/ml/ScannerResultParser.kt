package org.rhythmeta.maimaid.core.ml

object ScannerResultParser {
    private val UtagePrefix = Regex("^(?:【([^】]+)】|\\[([^]]+)]|［([^］]+)］)")

    fun parseAchievement(text: String?): Double? {
        val matched = text
            ?.replace(" ", "")
            ?.let { Regex("(\\d{2,3}[.,]\\d{4})").find(it)?.groupValues?.get(1) }
            ?.replace(',', '.')
            ?.toDoubleOrNull()
        return matched?.takeIf { it <= 101.0 }
    }

    fun parseInteger(text: String?): Int? = text
        ?.replace(" ", "")
        ?.let { Regex("(\\d+)").find(it)?.groupValues?.get(1) }
        ?.toIntOrNull()

    fun parseDifficulty(text: String?): String? {
        val lower = text
            ?.lowercase()
            ?.replace(" ", "")
            ?.replace(":", "")
            .orEmpty()
        if (lower.isEmpty()) return null
        if ("utage" in lower || lower.any { it in setOf('宴', '会', '场') }) return "utage"
        if ("remaster" in lower || "re:master" in lower || "reマスター" in lower) return "remaster"
        if ("master" in lower || "マスター" in lower) return "master"
        if ("expert" in lower || "エキスパート" in lower) return "expert"
        if ("advanced" in lower || "アドバンス" in lower) return "advanced"
        if ("basic" in lower || "ベーシック" in lower) return "basic"
        if ("re" in lower && ("ma" in lower || "ster" in lower)) return "remaster"
        if ("mas" in lower) return "master"
        if ("exp" in lower) return "expert"
        if ("adv" in lower) return "advanced"
        if ("bas" in lower) return "basic"
        val letters = lower.filter(Char::isLetter)
        val excluded = listOf("under", "ultra", "up", "union", "unit", "universe")
        return "utage".takeIf {
            letters.startsWith('u') && excluded.none(letters::startsWith)
        }
    }

    fun extractUtageKanji(candidates: List<String>): String? = candidates.firstNotNullOfOrNull { candidate ->
        val match = UtagePrefix.find(candidate.trim()) ?: return@firstNotNullOfOrNull null
        match.groupValues.drop(1).firstOrNull(String::isNotEmpty)?.trim()?.takeIf(String::isNotEmpty)
    }

    fun inferChartType(
        difficulty: String?,
        kanji: String?,
        titleCandidates: List<String>,
    ): String? = "utage".takeIf {
        difficulty.equals("utage", ignoreCase = true) ||
            !kanji.isNullOrEmpty() ||
            titleCandidates.any { UtagePrefix.containsMatchIn(it.trim()) }
    }
}
