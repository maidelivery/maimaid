package org.rhythmeta.maimaid.ui.song

import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.ui.util.SongVisualUtils
import kotlin.math.roundToInt

data class ChartConstantHistoryEntry(
    val version: String,
    val constant: Double,
    val change: Double? = null,
)

data class ChartVersionCandidate(
    val difficulty: String,
    val version: String?,
)

object ChartVersionHistory {
    fun mainVersion(
        candidates: List<ChartVersionCandidate>,
        versions: List<GameVersionEntity>,
        fallback: String?,
    ): String? {
        val normalizedCandidates = candidates.mapNotNull { candidate ->
            candidate.version.normalizedVersion()?.let { candidate.copy(version = it) }
        }
        val primaryCandidates = normalizedCandidates
            .filterNot { it.difficulty.equals("remaster", ignoreCase = true) }
            .ifEmpty { normalizedCandidates }
        return primaryCandidates
            .minWithOrNull(
                compareBy<ChartVersionCandidate> {
                    SongVisualUtils.versionSortOrder(it.version.orEmpty(), versions)
                }.thenBy { difficultyOrder(it.difficulty) },
            )
            ?.version
            ?: fallback.normalizedVersion()
    }

    fun additionVersion(songVersion: String?, chartVersion: String?): String? {
        val normalizedSongVersion = songVersion.normalizedVersion()
        val normalizedChartVersion = chartVersion.normalizedVersion() ?: return null
        return normalizedChartVersion.takeUnless {
            normalizedSongVersion != null &&
                it.equals(normalizedSongVersion, ignoreCase = true)
        }
    }

    fun constantChanges(
        values: Map<String, Double>?,
        versions: List<GameVersionEntity>,
    ): List<ChartConstantHistoryEntry> {
        val sortedValues = values
            .orEmpty()
            .asSequence()
            .mapNotNull { (version, constant) ->
                version.normalizedVersion()
                    ?.takeIf { constant.isFinite() }
                    ?.let { ChartConstantHistoryEntry(it, constant) }
            }
            .sortedWith(
                compareBy<ChartConstantHistoryEntry> {
                    SongVisualUtils.versionSortOrder(it.version, versions)
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.version },
            )
            .toList()

        val changes = sortedValues.fold(emptyList<ChartConstantHistoryEntry>()) { result, entry ->
            if (result.lastOrNull()?.constant == entry.constant) result else result + entry
        }
        if (changes.map { it.constant }.distinct().size < 2) return emptyList()
        return changes
            .mapIndexed { index, entry ->
                entry.copy(
                    change = changes.getOrNull(index - 1)?.let { previous ->
                        ((entry.constant - previous.constant) * 10.0).roundToInt() / 10.0
                    },
                )
            }
            .asReversed()
    }

    private fun String?.normalizedVersion(): String? = this
        ?.trim()
        ?.takeIf(String::isNotEmpty)

    private fun difficultyOrder(difficulty: String): Int = when (difficulty.lowercase()) {
        "basic" -> 0
        "advanced" -> 1
        "expert" -> 2
        "master" -> 3
        else -> 4
    }
}
