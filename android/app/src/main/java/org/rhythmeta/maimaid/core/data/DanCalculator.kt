package org.rhythmeta.maimaid.core.data

import java.util.Locale
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

object DanCalculator {
    fun groupCategories(
        categories: List<DanCategory>,
        versions: List<GameVersionEntity>,
        unknownLabel: String,
    ): List<DanCategoryGroup> {
        val versionCandidates = versions.sortedByDescending { it.name.length }
        val versionOrder = versions.associate { it.name.lowercase(Locale.ROOT) to it.sortOrder }
        val versionLabels = versions.associate {
            it.name.lowercase(Locale.ROOT) to it.abbreviation.ifBlank { it.name }
        }

        return categories
            .groupBy { category ->
                versionCandidates.firstOrNull { version ->
                    category.title.contains(version.name, ignoreCase = true) ||
                        category.id.contains(version.name, ignoreCase = true)
                }?.name ?: category.id.substringBefore('_').takeIf(String::isNotBlank) ?: unknownLabel
            }
            .map { (version, items) ->
                DanCategoryGroup(
                    version = version,
                    versionLabel = versionLabels[version.lowercase(Locale.ROOT)] ?: version,
                    categories = items.sortedBy { rankOrder(it.title) },
                )
            }
            .sortedWith(
                compareByDescending<DanCategoryGroup> {
                    versionOrder[it.version.lowercase(Locale.ROOT)] ?: -1
                }.thenByDescending(DanCategoryGroup::version),
            )
    }

    fun buildDetail(
        category: DanCategory,
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        scores: List<ScoreEntity>,
    ): DanCategoryDetail {
        val sheetsBySong = sheets
            .asSequence()
            .filterNot(SheetEntity::isRemoved)
            .groupBy(SheetEntity::songIdentifier)
        val songByTitle = buildMap {
            songs.asSequence()
                .filterNot(SongEntity::isRemoved)
                .filter { song ->
                    sheetsBySong[song.songIdentifier].orEmpty().any { sheet ->
                        !sheet.type.contains("utage", ignoreCase = true)
                    }
                }
                .forEach { song -> putIfAbsent(song.title, song) }
        }
        val scoreBySheet = scores.associateBy(ScoreEntity::sheetKey)

        return DanCategoryDetail(
            category = category,
            sections = category.sections.map { section ->
                DanSectionDetail(
                    title = section.title,
                    description = section.description,
                    charts = section.sheets.mapIndexed { index, raw ->
                        val reference = DanSheetReference.parse(raw)
                        val song = songByTitle[reference.title]
                        val sheet = song?.let { matchedSong ->
                            sheetsBySong[matchedSong.songIdentifier].orEmpty().firstOrNull { candidate ->
                                !candidate.type.contains("utage", ignoreCase = true) &&
                                    candidate.type.equals(reference.type, ignoreCase = true) &&
                                    candidate.difficulty.equals(reference.difficulty, ignoreCase = true)
                            }
                        }
                        DanChartEntry(
                            reference = reference,
                            description = section.sheetDescriptions?.getOrNull(index),
                            song = song,
                            sheet = sheet,
                            score = sheet?.let { scoreBySheet[it.sheetKey] },
                        )
                    },
                )
            },
        )
    }

    internal fun rankOrder(title: String): Int {
        val normalized = title.lowercase(Locale.ROOT)
        return when {
            "expert" in normalized -> -2
            "master" in normalized -> -1
            "真" in normalized -> 0
            "超" in normalized -> 1
            "檄" in normalized -> 2
            "橙" in normalized -> 3
            "暁" in normalized || "晓" in normalized -> 4
            "桃" in normalized -> 5
            "櫻" in normalized || "樱" in normalized -> 6
            "紫" in normalized -> 7
            "菫" in normalized -> 8
            "白" in normalized -> 9
            "雪" in normalized -> 10
            "輝" in normalized || "辉" in normalized -> 11
            "熊" in normalized -> 12
            "華" in normalized || "华" in normalized -> 13
            "爽" in normalized -> 14
            "煌" in normalized -> 15
            "舞" in normalized -> 16
            "霸" in normalized -> 17
            "初" in normalized -> 100
            "二" in normalized -> 101
            "三" in normalized -> 102
            "四" in normalized -> 103
            "五" in normalized -> 104
            "六" in normalized -> 105
            "七" in normalized -> 106
            "八" in normalized -> 107
            "九" in normalized -> 108
            "十" in normalized -> 109
            else -> 999
        }
    }
}
