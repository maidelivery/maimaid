package org.rhythmeta.maimaid.core.data

import java.text.Normalizer
import java.util.Locale
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

internal class OtogameSheetMatcher(
    songs: List<SongEntity>,
    sheets: List<SheetEntity>,
) {
    private val songsById = songs.associateBy(SongEntity::songIdentifier)
    private val japaneseSheets = sheets.filter { it.regionJp && !it.isRemoved }

    fun match(playlog: OtogamePlaylog): SheetEntity? {
        val difficulty = OtogameImportPolicy.difficulty(OtogameImportPolicy.difficultyCode(playlog)) ?: return null
        val chartType = OtogameImportPolicy.chartType(playlog)
        val eligible = japaneseSheets.filter { sheet ->
            typeMatches(sheet, chartType) && difficultyMatches(sheet, difficulty, playlog.music.utageKanjiName)
        }

        val normalizedTitle = normalizeTitle(playlog.music.name)
        if (normalizedTitle.isEmpty()) return null
        return eligible.filter { sheet ->
            normalizeTitle(songsById[sheet.songIdentifier]?.title.orEmpty()) == normalizedTitle
        }.distinctBy(SheetEntity::sheetKey).singleOrNull()
    }

    private fun typeMatches(sheet: SheetEntity, chartType: String): Boolean =
        canonicalChartType(sheet.type) == canonicalChartType(chartType)

    private fun canonicalChartType(value: String): String = when (value.trim().lowercase(Locale.ROOT)) {
        "standard", "std", "sd" -> "std"
        "deluxe", "dx" -> "dx"
        "utage" -> "utage"
        else -> value.trim().lowercase(Locale.ROOT)
    }

    private fun difficultyMatches(sheet: SheetEntity, difficulty: String, utageKanji: String?): Boolean {
        if (difficulty != "utage") return sheet.difficulty.equals(difficulty, ignoreCase = true)
        val expectedKanji = normalizeUtageKanji(utageKanji.orEmpty())
        return expectedKanji.isEmpty() || normalizeUtageKanji(sheet.difficulty) == expectedKanji
    }

    private fun normalizeTitle(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .replace(UtageTitlePrefix, "")
        .lowercase(Locale.ROOT)
        .filter(Char::isLetterOrDigit)

    private fun normalizeUtageKanji(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .filter(Char::isLetterOrDigit)

    private companion object {
        val UtageTitlePrefix = Regex("^(?:\\[[^]]+]|【[^】]+】|［[^］]+］)\\s*")
    }
}
