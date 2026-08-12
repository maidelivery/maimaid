package org.rhythmeta.maimaid.core.data

import java.time.LocalDate
import kotlin.math.floor
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

object RatingUtils {
    data class RankThreshold(val rank: String, val threshold: Double)

    data class Entry(
        val sheetKey: String,
        val songIdentifier: String,
        val songId: Int,
        val title: String,
        val imageName: String,
        val achievement: Double,
        val rating: Int,
        val level: Double,
        val difficulty: String,
        val type: String,
        val dxScore: Int,
        val maxDxScore: Int,
        val fc: String?,
        val fs: String?,
        val isNew: Boolean,
    )

    private val coefficients = listOf(
        0.0 to 0.0, 10.0 to 1.6, 20.0 to 3.2, 30.0 to 4.8, 40.0 to 6.4,
        50.0 to 8.0, 60.0 to 9.6, 70.0 to 11.2, 75.0 to 12.0, 79.9999 to 12.8,
        80.0 to 13.6, 90.0 to 15.2, 94.0 to 16.8, 96.9999 to 17.6, 97.0 to 20.0,
        98.0 to 20.3, 98.9999 to 20.6, 99.0 to 20.8, 99.5 to 21.1,
        99.9999 to 21.4, 100.0 to 21.6, 100.4999 to 22.2, 100.5 to 22.4,
    )

    val rankThresholds = listOf(
        RankThreshold("D", 0.0),
        RankThreshold("C", 50.0),
        RankThreshold("B", 60.0),
        RankThreshold("BB", 70.0),
        RankThreshold("BBB", 75.0),
        RankThreshold("A", 80.0),
        RankThreshold("AA", 90.0),
        RankThreshold("AAA", 94.0),
        RankThreshold("S", 97.0),
        RankThreshold("S+", 98.0),
        RankThreshold("SS", 99.0),
        RankThreshold("SS+", 99.5),
        RankThreshold("SSS", 100.0),
        RankThreshold("SSS+", 100.5),
    )

    fun coefficient(achievement: Double): Double = coefficients.lastOrNull { achievement >= it.first }?.second ?: 0.0

    fun rank(achievement: Double): String = when {
        achievement >= 100.5 -> "SSS+"
        achievement >= 100.0 -> "SSS"
        achievement >= 99.5 -> "SS+"
        achievement >= 99.0 -> "SS"
        achievement >= 98.0 -> "S+"
        achievement >= 97.0 -> "S"
        achievement >= 94.0 -> "AAA"
        achievement >= 90.0 -> "AA"
        achievement >= 80.0 -> "A"
        achievement >= 75.0 -> "BBB"
        achievement >= 70.0 -> "BB"
        achievement >= 60.0 -> "B"
        achievement >= 50.0 -> "C"
        else -> "D"
    }

    fun isAp(fc: String?): Boolean = fc?.lowercase()?.let { it == "ap" || it == "app" } == true

    fun calculate(internalLevel: Double, achievement: Double, fc: String? = null, afterCircle: Boolean = false): Int {
        if (internalLevel <= 0.0 || achievement <= 0.0) return 0
        val base = floor(internalLevel * coefficient(achievement) / 100.0 * minOf(achievement, 100.5) + 0.000001).toInt()
        return base + if (afterCircle && isAp(fc)) 1 else 0
    }

    fun isAfterCircle(latestVersion: String?, versions: List<String>): Boolean {
        val circle = versions.indexOfFirst { it.contains("circle", ignoreCase = true) }
        val latest = latestVersion?.let { versionIndex(it, versions) } ?: return false
        return circle >= 0 && latest >= circle
    }

    fun category(songVersion: String?, latestVersion: String?, server: String, activeRegion: Boolean, versions: List<String>): Boolean? {
        if (!activeRegion) return null
        val songIndex = songVersion?.let { versionIndex(it, versions) }
        val latestIndex = latestVersion?.let { versionIndex(it, versions) }
        if (songIndex == null || latestIndex == null) return false
        return when (server.lowercase()) {
            "cn" -> songIndex >= latestIndex
            else -> {
                if (songIndex > latestIndex) null
                else {
                    val circle = versions.indexOfFirst { it.equals("CiRCLE", ignoreCase = true) }
                    val oldest = if (circle >= 0 && latestIndex >= circle) maxOf(0, latestIndex - 1) else latestIndex
                    songIndex >= oldest
                }
            }
        }
    }

    fun latestVersionForServer(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        versions: List<GameVersionEntity>,
        server: String,
        currentDate: LocalDate = LocalDate.now(),
    ): String? {
        val orderedVersions = versions.sortedBy(GameVersionEntity::sortOrder).map(GameVersionEntity::name)
        if (orderedVersions.isEmpty()) return null
        val sheetsBySong = sheets.groupBy(SheetEntity::songIdentifier)
        val cutoff = when (server.lowercase()) {
            "cn" -> currentDate.minusMonths(15)
            "intl", "us", "usa" -> currentDate.minusMonths(4)
            else -> LocalDate.MAX
        }
        var serverVersion = orderedVersions.first()

        for (version in orderedVersions) {
            val activeVersionSongs = songs.filter { song ->
                song.version.equals(version, ignoreCase = true) &&
                    !song.isUtage &&
                    sheetsBySong[song.songIdentifier]
                        .orEmpty()
                        .any { sheet -> sheet.isActiveInAnyRegion() }
            }
            if (activeVersionSongs.isEmpty()) continue

            val playableCount = activeVersionSongs.count { song ->
                val songSheets = sheetsBySong[song.songIdentifier].orEmpty()
                songSheets.any { it.isActiveOnServer(server) } ||
                    song.releaseDate
                        ?.takeIf(String::isNotBlank)
                        ?.let { releaseDate ->
                            runCatching { LocalDate.parse(releaseDate) }
                                .getOrNull()
                                ?.let { it <= cutoff }
                                ?: true
                        }
                        ?: true
            }
            if (playableCount == 0) break

            serverVersion = version
            if (playableCount < activeVersionSongs.size) break
        }
        return serverVersion
    }

    private val SongEntity.isUtage: Boolean
        get() = category.contains("utage", ignoreCase = true) || category.contains("宴")

    private fun SheetEntity.isActiveInAnyRegion(): Boolean = regionJp || regionIntl || regionCn

    private fun SheetEntity.isActiveOnServer(server: String): Boolean = when (server.lowercase()) {
        "cn" -> regionCn
        "intl", "us", "usa" -> regionIntl
        else -> regionJp
    }

    private fun versionIndex(version: String, versions: List<String>): Int? {
        versions.indexOfFirst { it.equals(version, ignoreCase = true) }.takeIf { it >= 0 }?.let { return it }
        val normalized = version.lowercase()
        return versions.mapIndexedNotNull { index, candidate ->
            val value = candidate.lowercase()
            if (normalized.contains(value) || value.contains(normalized)) index to value.length else null
        }.maxByOrNull { it.second }?.first
    }
}
