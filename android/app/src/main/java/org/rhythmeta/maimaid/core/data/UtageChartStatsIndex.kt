package org.rhythmeta.maimaid.core.data

import java.text.Normalizer

class UtageChartStatsIndex(items: List<UtageChartStatsItem>) {
    private val byId = items.associateBy(UtageChartStatsItem::id)
    private val byTitle = items.groupBy { normalizeTitle(it.title) }

    fun resolve(providerSongId: Int, songTitle: String): UtageChartStatsItem? {
        if (providerSongId > 0) {
            byId[providerSongId]?.let { return it }
        }
        return byTitle[normalizeTitle(songTitle)]?.singleOrNull()
    }

    private fun normalizeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .trim()
        .lowercase()
        .replace(WhitespaceRegex, " ")

    private companion object {
        val WhitespaceRegex = Regex("\\s+")
    }
}
