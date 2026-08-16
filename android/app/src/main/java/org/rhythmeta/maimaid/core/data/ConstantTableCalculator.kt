package org.rhythmeta.maimaid.core.data

import java.util.Locale
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

data class ConstantTableEntry(
    val id: String,
    val songIdentifier: String,
    val songTitle: String,
    val imageName: String,
    val difficulty: String,
    val type: String,
    val level: Double,
    val rank: String?,
    val fc: String?,
    val fs: String?,
)

data class ConstantTableSection(
    val levelLabel: String,
    val entries: List<ConstantTableEntry>,
)

data class ConstantTableResponse(
    val entries: List<ConstantTableEntry> = emptyList(),
    val userName: String? = null,
) {
    val availableBaseLevels: List<Int>
        get() = entries.map(ConstantTableCalculator::bucketBaseLevel).distinct().sortedDescending()

    fun sections(baseLevel: Int): List<ConstantTableSection> = ConstantTableCalculator.sections(entries, baseLevel)
}

object ConstantTableCalculator {
    fun build(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        scores: List<ScoreEntity>,
        userName: String? = null,
        server: String = "jp",
    ): ConstantTableResponse {
        val songsById = songs
            .asSequence()
            .filterNot(SongEntity::isRemoved)
            .filterNot(::isUtage)
            .associateBy(SongEntity::songIdentifier)
        val scoresBySheet = scores.associateBy(ScoreEntity::sheetKey)
        val entries = sheets.mapNotNull { sheet ->
            val song = songsById[sheet.songIdentifier] ?: return@mapNotNull null
            if (sheet.type.contains("utage", ignoreCase = true) ||
                !ServerChartPolicy.isPlayable(sheet, server)
            ) {
                return@mapNotNull null
            }
            val metadata = ServerChartPolicy.metadata(sheet, server)
            val level = metadata.ratingLevel
                ?: metadata.internalLevel?.toDoubleOrNull()
                ?: metadata.level.toDoubleOrNull()
                ?: return@mapNotNull null
            if (level <= 0.0) return@mapNotNull null
            val score = scoresBySheet[sheet.sheetKey]
            ConstantTableEntry(
                id = sheet.sheetKey,
                songIdentifier = song.songIdentifier,
                songTitle = song.title,
                imageName = song.imageName,
                difficulty = sheet.difficulty,
                type = sheet.type,
                level = level,
                rank = score?.let { ScoreRules.calculateRank(it.achievement) },
                fc = score?.fc,
                fs = score?.fs,
            )
        }.sortedWith(entryComparator)
        return ConstantTableResponse(entries, userName?.trim()?.takeIf(String::isNotEmpty))
    }

    fun sections(entries: List<ConstantTableEntry>, baseLevel: Int): List<ConstantTableSection> = entries
        .filter { bucketBaseLevel(it) == baseLevel }
        .groupBy { constantKey(it.level) }
        .map { (level, group) -> ConstantTableSection(level, group.sortedWith(entryComparator)) }
        .sortedByDescending { it.levelLabel.toDoubleOrNull() ?: 0.0 }

    fun bucketBaseLevel(entry: ConstantTableEntry): Int = bucketBaseLevel(entry.level)

    fun bucketBaseLevel(level: Double): Int = if (level >= 15.0) 14 else kotlin.math.floor(level).toInt()

    fun baseLevelLabel(baseLevel: Int): String = if (baseLevel == 14) "14~15" else baseLevel.toString()

    private fun constantKey(level: Double): String = String.format(
        Locale.ROOT,
        "%.1f",
        kotlin.math.floor(level * 10.0) / 10.0,
    )

    private fun isUtage(song: SongEntity): Boolean =
        song.category.contains("utage", ignoreCase = true) || song.category.contains("宴")

    private val entryComparator = compareBy<ConstantTableEntry>(
        { it.songTitle.lowercase(Locale.ROOT) },
        { -difficultyOrder(it.difficulty) },
        { it.type.lowercase(Locale.ROOT) },
        ConstantTableEntry::id,
    )

    private fun difficultyOrder(difficulty: String): Int = when (difficulty.lowercase()) {
        "basic" -> 0
        "advanced" -> 1
        "expert" -> 2
        "master" -> 3
        "remaster" -> 4
        else -> -1
    }
}
