package org.rhythmeta.maimaid.core.data

import java.util.Locale
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

enum class PlateType {
    Kiwami,
    Sho,
    Shin,
    Maimai,
    ;

    fun isAchieved(score: ScoreEntity?): Boolean = when (this) {
        Kiwami -> ScoreRules.canonicalFc(score?.fc) in setOf("fc", "fcp", "ap", "app")
        Sho -> (score?.achievement ?: 0.0) >= 100.0
        Shin -> ScoreRules.canonicalFc(score?.fc) in setOf("ap", "app")
        Maimai -> ScoreRules.canonicalFs(score?.fs) in setOf("fsd", "fsdp")
    }
}

data class VersionPlateGroup(
    val name: String,
    val platePrefix: String,
    val versions: List<String>,
    val isOldFrame: Boolean,
    val hasSho: Boolean,
    val includeReMasterByDefault: Boolean,
) {
    val id: String get() = "$platePrefix:${versions.joinToString("|")}"
    val displayName: String get() = if (name == platePrefix || name == "舞代") name else platePrefix
}

data class PlateChartEntry(
    val song: SongEntity,
    val sheet: SheetEntity,
    val score: ScoreEntity?,
    val achieved: Boolean,
)

data class PlateLevelSection(
    val level: String,
    val charts: List<PlateChartEntry>,
) {
    val completedCount: Int get() = charts.count(PlateChartEntry::achieved)
}

data class PlateProgressResponse(
    val groups: List<VersionPlateGroup> = emptyList(),
    val selectedGroup: VersionPlateGroup? = null,
    val difficulty: String = "master",
    val plateType: PlateType = PlateType.Sho,
    val sections: List<PlateLevelSection> = emptyList(),
) {
    val totalCount: Int get() = sections.sumOf { it.charts.size }
    val completedCount: Int get() = sections.sumOf(PlateLevelSection::completedCount)
    val remainingCount: Int get() = (totalCount - completedCount).coerceAtLeast(0)
    val progress: Float get() = if (totalCount == 0) 0f else completedCount.toFloat() / totalCount
}

object PlateProgressCalculator {
    fun buildGroups(versions: List<GameVersionEntity>): List<VersionPlateGroup> {
        val ordered = versions.sortedBy(GameVersionEntity::sortOrder)
        if (ordered.isEmpty()) return emptyList()
        val dxIndex = ordered.indexOfFirst {
            it.name.contains("でらっくす", ignoreCase = true) || it.name.contains(" DX", ignoreCase = true)
        }.takeIf { it >= 0 } ?: ordered.size
        val greenIndex = ordered.indexOfFirst { it.name.contains("GreeN", ignoreCase = true) }
            .takeIf { it >= 0 } ?: ordered.size
        val groups = ordered
            .fold(mutableListOf<MutableList<GameVersionEntity>>()) { result, version ->
                val current = result.lastOrNull()
                if (current == null || current.first().abbreviation != version.abbreviation) {
                    result.add(mutableListOf(version))
                } else {
                    current.add(version)
                }
                result
            }
            .map { items ->
                val first = items.first()
                val firstIndex = ordered.indexOf(first)
                val isOld = firstIndex < dxIndex
                val isOriginal = firstIndex < greenIndex && isOld
                val cleanName = first.name
                    .replace(Regex("maimai\\s*", RegexOption.IGNORE_CASE), "")
                    .replace("でらっくす", "", ignoreCase = true)
                    .trim()
                    .ifEmpty { "maimai" }
                VersionPlateGroup(
                    name = cleanName,
                    platePrefix = first.abbreviation,
                    versions = items.map(GameVersionEntity::name),
                    isOldFrame = isOld,
                    hasSho = first.abbreviation != "真" && !isOriginal,
                    includeReMasterByDefault = false,
                )
            }
            .toMutableList()
        val oldVersions = groups.filter(VersionPlateGroup::isOldFrame).flatMap(VersionPlateGroup::versions)
        if (oldVersions.isNotEmpty()) {
            groups.add(
                0,
                VersionPlateGroup("舞代", "舞", oldVersions, true, true, true),
            )
        }
        return groups
    }

    fun calculate(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        scores: List<ScoreEntity>,
        versions: List<GameVersionEntity>,
        groupId: String?,
        difficulty: String,
        plateType: PlateType,
    ): PlateProgressResponse {
        val groups = buildGroups(versions)
        val group = groups.firstOrNull { it.id == groupId } ?: groups.firstOrNull()
        if (group == null) return PlateProgressResponse(groups = groups)
        val resolvedDifficulty = if (group.name != "舞代" && difficulty == "remaster") "master" else difficulty
        val resolvedPlate = if (!group.hasSho && plateType == PlateType.Sho) PlateType.Kiwami else plateType
        val songsById = songs
            .asSequence()
            .filterNot(SongEntity::isRemoved)
            .filter { it.version in group.versions }
            .filterNot { it.category.contains("utage", true) || it.category.contains("宴") }
            .associateBy(SongEntity::songIdentifier)
        val scoresBySheet = scores.associateBy(ScoreEntity::sheetKey)
        val charts = sheets.mapNotNull { sheet ->
            val song = songsById[sheet.songIdentifier] ?: return@mapNotNull null
            if (
                sheet.isRemoved ||
                !sheet.regionJp ||
                sheet.type.contains("utage", true) ||
                !sheet.difficulty.equals(resolvedDifficulty, true) ||
                !sheet.version.isNullOrBlank() && sheet.version !in group.versions
            ) return@mapNotNull null
            val score = scoresBySheet[sheet.sheetKey]
            PlateChartEntry(song, sheet, score, resolvedPlate.isAchieved(score))
        }.sortedWith(compareBy({ it.song.sortOrder }, { it.song.title.lowercase(Locale.ROOT) }))
        val sections = charts
            .groupBy { chartLevel(it.sheet) }
            .map { (level, entries) -> PlateLevelSection(level, entries) }
            .sortedByDescending { parseLevel(it.level) }
        return PlateProgressResponse(groups, group, resolvedDifficulty, resolvedPlate, sections)
    }

    private fun chartLevel(sheet: SheetEntity): String = sheet.internalLevel
        ?.takeIf(String::isNotBlank)
        ?: sheet.internalLevelValue?.let { String.format(Locale.ROOT, "%.1f", it) }
        ?: sheet.level

    private fun parseLevel(level: String): Double = level.replace("+", ".7").toDoubleOrNull() ?: 0.0
}
