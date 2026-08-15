package org.rhythmeta.maimaid.core.data

import java.util.Locale
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongAliasEntity
import org.rhythmeta.maimaid.core.database.SongEntity

enum class ScoreQueryDisplayMode {
    Grid,
    List,
}

enum class ScoreQuerySortMode {
    Rating,
    Achievement,
    Level,
}

data class ScoreQueryFilterSettings(
    val selectedDifficulties: Set<String> = emptySet(),
    val selectedRanks: Set<String> = emptySet(),
    val selectedFc: Set<String> = emptySet(),
    val selectedFs: Set<String> = emptySet(),
) {
    val isEmpty: Boolean
        get() = selectedDifficulties.isEmpty() &&
            selectedRanks.isEmpty() &&
            selectedFc.isEmpty() &&
            selectedFs.isEmpty()
}

data class ScoreQueryStats(
    val totalPlayed: Int = 0,
    val sssPlus: Int = 0,
    val sss: Int = 0,
    val fcCount: Int = 0,
    val apCount: Int = 0,
    val fsCount: Int = 0,
    val fsdCount: Int = 0,
)

data class ScoreQueryEntry(
    val sheetKey: String,
    val songIdentifier: String,
    val songTitle: String,
    val artist: String,
    val aliases: List<String>,
    val imageName: String,
    val difficulty: String,
    val type: String,
    val level: Double,
    val achievement: Double,
    val rank: String,
    val rating: Int,
    val fc: String?,
    val fs: String?,
    val dxScore: Int,
)

data class ScoreQueryResponse(
    val entries: List<ScoreQueryEntry> = emptyList(),
    val stats: ScoreQueryStats = ScoreQueryStats(),
)

object ScoreQueryCalculator {
    fun build(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        scores: List<ScoreEntity>,
        aliases: List<SongAliasEntity>,
    ): ScoreQueryResponse {
        val songsById = songs
            .asSequence()
            .filterNot(SongEntity::isRemoved)
            .filterNot { it.category.contains("utage", ignoreCase = true) || it.category.contains("宴") }
            .associateBy(SongEntity::songIdentifier)
        val sheetsByKey = sheets
            .asSequence()
            .filterNot(SheetEntity::isRemoved)
            .filterNot { it.type.contains("utage", ignoreCase = true) }
            .associateBy(SheetEntity::sheetKey)
        val aliasesBySong = aliases.groupBy(SongAliasEntity::songIdentifier)
            .mapValues { (_, values) -> values.map(SongAliasEntity::alias) }

        val entries = scores.mapNotNull { score ->
            if (score.achievement <= 0.0) return@mapNotNull null
            val sheet = sheetsByKey[score.sheetKey] ?: return@mapNotNull null
            val song = songsById[sheet.songIdentifier] ?: return@mapNotNull null
            val level = sheet.internalLevelValue ?: sheet.levelValue ?: return@mapNotNull null
            if (level <= 0.0) return@mapNotNull null

            ScoreQueryEntry(
                sheetKey = sheet.sheetKey,
                songIdentifier = song.songIdentifier,
                songTitle = song.title,
                artist = song.artist,
                aliases = aliasesBySong[song.songIdentifier].orEmpty(),
                imageName = song.imageName,
                difficulty = sheet.difficulty,
                type = sheet.type,
                level = level,
                achievement = score.achievement,
                rank = RatingUtils.rank(score.achievement),
                rating = RatingUtils.calculate(level, score.achievement),
                fc = ScoreRules.canonicalFc(score.fc),
                fs = ScoreRules.canonicalFs(score.fs),
                dxScore = score.dxScore,
            )
        }

        return ScoreQueryResponse(
            entries = entries,
            stats = calculateStats(entries),
        )
    }

    fun filterAndSort(
        entries: List<ScoreQueryEntry>,
        searchText: String,
        settings: ScoreQueryFilterSettings,
        sortMode: ScoreQuerySortMode,
        ascending: Boolean,
    ): List<ScoreQueryEntry> {
        val query = searchText.normalizedSearchText()
        val filtered = entries.filter { entry ->
            (query.isEmpty() || entry.matches(query)) &&
                (settings.selectedDifficulties.isEmpty() ||
                    entry.difficulty.lowercase(Locale.ROOT) in settings.selectedDifficulties) &&
                (settings.selectedRanks.isEmpty() || entry.rank in settings.selectedRanks) &&
                (settings.selectedFc.isEmpty() || ScoreRules.displayFc(entry.fc) in settings.selectedFc) &&
                (settings.selectedFs.isEmpty() || ScoreRules.displayFs(entry.fs) in settings.selectedFs)
        }
        val primary = when (sortMode) {
            ScoreQuerySortMode.Rating -> compareBy<ScoreQueryEntry>(ScoreQueryEntry::rating)
            ScoreQuerySortMode.Achievement -> compareBy(ScoreQueryEntry::achievement)
            ScoreQuerySortMode.Level -> compareBy(ScoreQueryEntry::level)
        }
        val directed = if (ascending) primary else primary.reversed()
        return filtered.sortedWith(
            directed
                .thenBy { it.songTitle.normalizedSearchText() }
                .thenBy(ScoreQueryEntry::sheetKey),
        )
    }

    private fun calculateStats(entries: List<ScoreQueryEntry>): ScoreQueryStats {
        var sssPlus = 0
        var sss = 0
        var fc = 0
        var ap = 0
        var fs = 0
        var fsd = 0

        entries.forEach { entry ->
            when {
                entry.achievement >= 100.5 -> sssPlus++
                entry.achievement >= 100.0 -> sss++
            }
            when (ScoreRules.canonicalFc(entry.fc)) {
                "ap", "app" -> ap++
                "fc", "fcp" -> fc++
            }
            when (ScoreRules.canonicalFs(entry.fs)) {
                "fsd", "fsdp" -> fsd++
                "fs", "fsp" -> fs++
            }
        }

        return ScoreQueryStats(
            totalPlayed = entries.map(ScoreQueryEntry::songIdentifier).distinct().size,
            sssPlus = sssPlus,
            sss = sss,
            fcCount = fc,
            apCount = ap,
            fsCount = fs,
            fsdCount = fsd,
        )
    }

    private fun ScoreQueryEntry.matches(query: String): Boolean = sequenceOf(
        songTitle,
        artist,
        *aliases.toTypedArray(),
    ).any { it.normalizedSearchText().contains(query) }

    private fun String.normalizedSearchText(): String = SearchTextNormalizer.normalize(this).trim()
}
