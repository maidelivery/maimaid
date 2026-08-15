package org.rhythmeta.maimaid.core.data

import java.text.Normalizer
import java.util.Locale
import kotlin.math.roundToInt

class UtageChartStatsIndex(items: List<UtageChartStatsItem>) {
    private val byId = items.associateBy(UtageChartStatsItem::id)
    private val byKey = items
        .mapNotNull { item ->
            val kanji = extractKanji(item.title) ?: return@mapNotNull null
            lookupKey(item.title, kanji)?.let { it to item }
        }
        .groupBy(Pair<String, UtageChartStatsItem>::first, Pair<String, UtageChartStatsItem>::second)
        .mapValues { (_, values) -> values.distinctBy(UtageChartStatsItem::id).sortedWith(StatsComparator) }

    fun resolve(
        providerSongId: Int,
        songTitle: String,
        songIdentifier: String,
        sheetDifficulty: String,
        sheetLevel: String,
    ): UtageChartStatsItem? {
        if (providerSongId > 0) {
            byId[providerSongId]?.let { return it }
        }

        val kanji = extractKanji(sheetDifficulty) ?: extractKanji(songTitle) ?: return null
        val normalizedIdentifier = songIdentifier
            .replace(LegacyUtagePrefix, "")
            .replace(TrailingVariant, "")
        val candidateKeys = listOfNotNull(
            lookupKey(songTitle, kanji),
            lookupKey(normalizedIdentifier, kanji),
        ).distinct()
        candidateKeys.forEach { key ->
            val candidates = byKey[key].orEmpty()
            if (candidates.isNotEmpty()) {
                return selectCandidate(candidates, songIdentifier, sheetLevel)
            }
        }
        return null
    }

    private fun selectCandidate(
        candidates: List<UtageChartStatsItem>,
        songIdentifier: String,
        sheetLevel: String,
    ): UtageChartStatsItem {
        if (candidates.size == 1) return candidates.first()

        val identifierUpper = songIdentifier.uppercase(Locale.ROOT)
        val difficultyIndex = DifficultyOrder.indexOfFirst(identifierUpper::contains)
        if (difficultyIndex >= 0) return candidates[minOf(difficultyIndex, candidates.lastIndex)]
        if ("入門" in songIdentifier) return candidates.first()
        if ("ヒーロー" in songIdentifier) return candidates.last()

        val level = parseApproximateLevel(sheetLevel) ?: return candidates.last()
        val ratio = ((level - 1.0) / 14.0).coerceIn(0.0, 1.0)
        return candidates[(candidates.lastIndex * ratio).roundToInt()]
    }

    private fun parseApproximateLevel(raw: String): Double? {
        val cleaned = raw.replace("?", "").trim()
        if (cleaned == "*") return null
        val value = cleaned.filter { it.isDigit() || it == '.' }.toDoubleOrNull() ?: return null
        return value + if ('+' in cleaned) 0.7 else 0.0
    }

    private fun lookupKey(title: String, kanji: String): String? {
        val normalizedKanji = normalizeKanji(kanji)
        val normalizedTitle = normalizeTitle(title)
        if (normalizedKanji.isEmpty() || normalizedTitle.isEmpty()) return null
        return "$normalizedKanji|$normalizedTitle"
    }

    private fun extractKanji(value: String): String? {
        val match = UtagePrefix.find(value.trim()) ?: return null
        return match.groupValues.drop(1).firstOrNull(String::isNotEmpty)?.let(::normalizeKanji)
    }

    private fun normalizeKanji(value: String): String = fold(value.trim().replace('藏', '蔵'))

    private fun normalizeTitle(value: String): String = fold(
        value.replace('\u3000', ' ')
            .trim()
            .replace(UtagePrefix, "")
            .replace(LegacyUtagePrefix, "")
            .replace(TrailingDifficulty, "")
            .replace('藏', '蔵'),
    ).replace(IgnoredTitleCharacters, "")

    private fun fold(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKD)
        .replace(CombiningMarks, "")
        .trim()
        .lowercase(Locale.ROOT)

    private companion object {
        val StatsComparator = compareBy<UtageChartStatsItem> { it.notes }.thenBy { it.id }
        val DifficultyOrder = listOf("(EASY)", "(BASIC)", "(ADVANCED)", "(EXPERT)", "(MASTER)", "(RE:MASTER)")
        val UtagePrefix = Regex("^(?:【([^】]+)】|\\[([^]]+)])\\s*")
        val LegacyUtagePrefix = Regex("^\\s*[（(]宴[）)]\\s*")
        val TrailingVariant = Regex("（[^）]+）\\s*$")
        val TrailingDifficulty = Regex(
            "\\s*[（(](?:EASY|BASIC|ADVANCED|EXPERT|MASTER|RE:MASTER)[）)]\\s*$",
            RegexOption.IGNORE_CASE,
        )
        val IgnoredTitleCharacters = Regex("[\\s\\[\\]【】()（）]+")
        val CombiningMarks = Regex("\\p{M}+")
    }
}
