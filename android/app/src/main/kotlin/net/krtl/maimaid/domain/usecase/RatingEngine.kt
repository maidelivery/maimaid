package net.krtl.maimaid.domain.usecase

import kotlinx.serialization.json.Json
import net.krtl.maimaid.data.remote.dto.ChartStatDto
import net.krtl.maimaid.data.remote.dto.ChartStatsResponse
import net.krtl.maimaid.domain.model.B50Result
import net.krtl.maimaid.domain.model.GameServer
import net.krtl.maimaid.domain.model.PlateProgressItem
import net.krtl.maimaid.domain.model.PlateType
import net.krtl.maimaid.domain.model.RatingEntry
import net.krtl.maimaid.domain.model.RecommendationItem
import net.krtl.maimaid.domain.model.RecommendationResult
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.domain.model.UserProfile
import java.security.MessageDigest
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Base64
import java.util.UUID
import kotlin.math.abs
import kotlin.math.floor

object RatingEngine {
    private val json = Json { ignoreUnknownKeys = true }
    private data class RatingBreakpoint(val achievement: Double, val coefficient: Double)
    enum class B50SheetStatus {
        B15,
        B35,
        EXCLUDED_UTAGE,
        EXCLUDED_SERVER,
        EXCLUDED_NO_LEVEL
    }

    data class B50SheetClassification(
        val status: B50SheetStatus,
        val internalLevel: Double = 0.0
    ) {
        val isEligible: Boolean
            get() = status == B50SheetStatus.B15 || status == B50SheetStatus.B35
    }

    private val ratingCoefficients = listOf(
        RatingBreakpoint(100.5, 22.4),
        RatingBreakpoint(100.4999, 22.2),
        RatingBreakpoint(100.0, 21.6),
        RatingBreakpoint(99.9999, 21.4),
        RatingBreakpoint(99.5, 21.1),
        RatingBreakpoint(99.0, 20.8),
        RatingBreakpoint(98.99, 20.6),
        RatingBreakpoint(98.0, 20.3),
        RatingBreakpoint(97.0, 20.0),
        RatingBreakpoint(96.99, 17.6),
        RatingBreakpoint(94.0, 16.8),
        RatingBreakpoint(90.0, 15.2),
        RatingBreakpoint(80.0, 13.6),
        RatingBreakpoint(79.99, 12.8),
        RatingBreakpoint(75.0, 12.0),
        RatingBreakpoint(70.0, 11.2),
        RatingBreakpoint(60.0, 9.6),
        RatingBreakpoint(50.0, 8.0),
        RatingBreakpoint(40.0, 6.4),
        RatingBreakpoint(30.0, 4.8),
        RatingBreakpoint(20.0, 3.2),
        RatingBreakpoint(10.0, 1.6),
        RatingBreakpoint(0.0, 0.0)
    )

    fun calculateRank(achievement: Double): String = when {
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

    fun isAllPerfect(fc: String?): Boolean = fc?.lowercase() in setOf("ap", "app")

    fun calculateRating(internalLevel: Double, achievement: Double, fc: String? = null, afterCircle: Boolean = false): Int {
        if (internalLevel <= 0 || achievement <= 0) return 0
        val coefficient = getRatingCoefficient(achievement)
        val rating = internalLevel * (coefficient / 100.0) * achievement.coerceAtMost(100.5)
        var result = floor(rating + 0.000001).toInt()
        if (afterCircle && isAllPerfect(fc)) result += 1
        return result
    }

    private fun getRatingCoefficient(achievement: Double): Double {
        val capped = achievement.coerceAtMost(100.5)
        for (index in 0 until ratingCoefficients.lastIndex) {
            val upper = ratingCoefficients[index]
            val lower = ratingCoefficients[index + 1]
            if (capped >= lower.achievement) {
                if (capped == upper.achievement) return upper.coefficient
                if (capped == lower.achievement) return lower.coefficient
                val range = upper.achievement - lower.achievement
                if (range <= 0) return lower.coefficient
                val fraction = (capped - lower.achievement) / range
                return lower.coefficient + fraction * (upper.coefficient - lower.coefficient)
            }
        }
        return 0.0
    }

    fun isAfterCircle(version: String?, versionSequence: List<String>): Boolean {
        if (version.isNullOrBlank()) return false
        val circleIndex = versionSequence.indexOfFirst { it.contains("circle", ignoreCase = true) }
        if (circleIndex < 0) return false
        val versionIndex = versionSequence.indexOfFirst { version.contains(it) || it.contains(version) }
        return versionIndex >= circleIndex
    }

    fun latestVersionFor(server: GameServer, songs: List<Song>, versionSequence: List<String>): String? {
        val cutoff = when (server) {
            GameServer.JP -> "9999-12-31"
            GameServer.CN -> formatCutoff(monthsBack = 18)
            GameServer.INTL -> formatCutoff(monthsBack = 4)
        }
        val orderedVersions = versionSequence.ifEmpty { songs.mapNotNull { it.version }.distinct() }
        var serverVersion = orderedVersions.firstOrNull()
        for (version in orderedVersions) {
            val versionSongs = songs.filter { it.version == version && !isUtageSong(it) }
            val activeVersionSongs = versionSongs.filterNot { song -> song.sheets.isEmpty() || song.sheets.all { !it.regionJp && !it.regionIntl && !it.regionCn } }
            if (activeVersionSongs.isEmpty()) continue
            val playable = activeVersionSongs.count { isPlayable(it, cutoff, server) }
            if (playable > 0) {
                serverVersion = version
                if (playable < activeVersionSongs.size) break
            } else {
                serverVersion = version
                break
            }
        }
        return serverVersion
    }

    fun parseChartStats(chartStatsJson: String?): Map<String, List<ChartStatDto>> {
        if (chartStatsJson.isNullOrBlank()) return emptyMap()
        return runCatching {
            json.decodeFromString<ChartStatsResponse>(chartStatsJson).charts
        }.getOrDefault(emptyMap())
    }

    fun classifySheetForB50(
        profile: UserProfile,
        song: Song,
        sheet: Sheet,
        versionSequence: List<String>,
        chartStats: Map<String, List<ChartStatDto>> = emptyMap(),
        useFitDiff: Boolean,
        latestVersion: String? = latestVersionFor(profile.server, listOf(song), versionSequence)
    ): B50SheetClassification {
        if (isUtageSong(song) || sheet.type.contains("utage", ignoreCase = true)) {
            return B50SheetClassification(B50SheetStatus.EXCLUDED_UTAGE)
        }

        val isRegionActive = when (profile.server) {
            GameServer.JP -> sheet.regionJp
            GameServer.INTL -> sheet.regionIntl
            GameServer.CN -> sheet.regionCn
        }
        if (!isRegionActive) {
            return B50SheetClassification(B50SheetStatus.EXCLUDED_SERVER)
        }

        val matchedStats = chartStats[sheet.songId.toString()] ?: chartStats[song.songIdentifier] ?: emptyList()
        val internalLevel = if (useFitDiff) {
            findMatchingStat(sheet, matchedStats)?.fitDiff ?: sheet.internalLevelValue ?: sheet.levelValue ?: 0.0
        } else {
            sheet.internalLevelValue ?: sheet.levelValue ?: 0.0
        }
        if (internalLevel <= 0) {
            return B50SheetClassification(B50SheetStatus.EXCLUDED_NO_LEVEL)
        }

        val category = determineSongCategory(song.version, latestVersion, versionSequence)
        return when (category) {
            SongCategory.B15 -> B50SheetClassification(B50SheetStatus.B15, internalLevel)
            SongCategory.B35 -> B50SheetClassification(B50SheetStatus.B35, internalLevel)
            SongCategory.EXCLUDED -> B50SheetClassification(B50SheetStatus.EXCLUDED_SERVER)
        }
    }

    private fun formatCutoff(monthsBack: Long): String {
        val instant = Instant.now().minusSeconds(monthsBack * 30L * 24L * 3600L)
        return DateTimeFormatter.ofPattern("yyyy-MM-dd").withZone(ZoneOffset.UTC).format(instant)
    }

    private fun isPlayable(song: Song, cutoff: String, server: GameServer): Boolean {
        if (isUtageSong(song)) return false
        if (song.sheets.isEmpty() || song.sheets.all { !it.regionJp && !it.regionIntl && !it.regionCn }) return false
        val hasRegion = song.sheets.any {
            when (server) {
                GameServer.JP -> it.regionJp
                GameServer.INTL -> it.regionIntl
                GameServer.CN -> it.regionCn
            }
        }
        if (hasRegion) return true
        return song.releaseDate.isNullOrBlank() || song.releaseDate <= cutoff
    }

    private enum class SongCategory { B15, B35, EXCLUDED }

    private fun determineSongCategory(songVersion: String?, latestServerVersion: String?, versionSequence: List<String>): SongCategory {
        val latest = latestServerVersion ?: return SongCategory.B35
        val songVer = songVersion ?: return SongCategory.B35
        val songIndex = versionSequence.indexOfFirst { songVer.contains(it) || it.contains(songVer) }
        val latestIndex = versionSequence.indexOfFirst { latest.contains(it) || it.contains(latest) }
        if (songIndex < 0 || latestIndex < 0) return SongCategory.B35
        return if (songIndex >= latestIndex) SongCategory.B15 else SongCategory.B35
    }

    fun calculateB50(
        profile: UserProfile,
        songs: List<Song>,
        scores: List<Score>,
        versionSequence: List<String>,
        chartStatsJson: String?,
        useFitDiff: Boolean
    ): B50Result {
        val scoreMap = scores.associateBy { it.sheetId }
        val chartStats = parseChartStats(chartStatsJson)
        val latestVersion = latestVersionFor(profile.server, songs, versionSequence)
        val afterCircle = isAfterCircle(latestVersion, versionSequence)
        val entries = mutableListOf<Pair<RatingEntry, Boolean>>()
        for (song in songs) {
            for (sheet in song.sheets) {
                val score = scoreMap[sheet.sheetId] ?: continue
                val classification = classifySheetForB50(
                    profile = profile,
                    song = song,
                    sheet = sheet,
                    versionSequence = versionSequence,
                    chartStats = chartStats,
                    useFitDiff = useFitDiff,
                    latestVersion = latestVersion
                )
                if (!classification.isEligible) continue
                val isNew = classification.status == B50SheetStatus.B15
                val internalLevel = classification.internalLevel
                val rating = calculateRating(internalLevel, score.rate, score.fc, afterCircle)
                if (rating <= 0) continue
                entries += RatingEntry(
                    sheetId = sheet.sheetId,
                    songId = song.songId,
                    songIdentifier = song.songIdentifier,
                    songTitle = song.title,
                    imageName = song.imageName,
                    achievement = score.rate,
                    rating = rating,
                    level = internalLevel,
                    diff = sheet.difficulty,
                    type = sheet.type,
                    dxScore = score.dxScore,
                    maxDxScore = (sheet.total ?: 0) * 3,
                    fc = score.fc,
                    fs = score.fs
                ) to isNew
            }
        }
        val b15 = entries.filter { it.second }.map { it.first }.sortedByDescending { it.rating }.take(profile.b15Count)
        val b35 = entries.filterNot { it.second }.map { it.first }.sortedByDescending { it.rating }.take(profile.b35Count)
        return B50Result(
            total = b15.sumOf { it.rating } + b35.sumOf { it.rating },
            b35 = b35,
            b15 = b15
        )
    }

    fun calculateRecommendations(
        profile: UserProfile,
        songs: List<Song>,
        scores: List<Score>,
        versionSequence: List<String>,
        chartStatsJson: String?
    ): RecommendationResult {
        val b50 = calculateB50(profile, songs, scores, versionSequence, chartStatsJson, useFitDiff = false)
        val b15Threshold = b50.b15.lastOrNull()?.rating ?: 0
        val b35Threshold = b50.b35.lastOrNull()?.rating ?: 0
        val currentB15Average = b50.b15.map { it.level }.average().takeIf { !it.isNaN() } ?: 0.0
        val latestVersion = latestVersionFor(profile.server, songs, versionSequence)
        val scoreMap = scores.associateBy { it.sheetId }
        val chartStats = parseChartStats(chartStatsJson)
        val targetMilestones = listOf(
            "S" to 97.0,
            "S+" to 98.0,
            "SS" to 99.0,
            "SS+" to 99.5,
            "SSS" to 100.0,
            "SSS+" to 100.5
        )
        val b15Results = mutableListOf<RecommendationItem>()
        val b35Results = mutableListOf<RecommendationItem>()

        for (song in songs) {
            if (isUtageSong(song)) continue
            val isRegionActive = song.sheets.any {
                when (profile.server) {
                    GameServer.JP -> it.regionJp
                    GameServer.INTL -> it.regionIntl
                    GameServer.CN -> it.regionCn
                }
            }
            if (!isRegionActive) continue
            val category = determineSongCategory(song.version, latestVersion, versionSequence)
            if (category == SongCategory.EXCLUDED) continue
            for (sheet in song.sheets) {
                if (sheet.type.contains("utage", ignoreCase = true)) continue
                val internalLevel = sheet.internalLevelValue ?: sheet.levelValue ?: 0.0
                if (internalLevel <= 0) continue
                val currentScore = scoreMap[sheet.sheetId]
                val currentRate = currentScore?.rate ?: 0.0
                if (currentRate >= 100.5) continue
                val isNew = category == SongCategory.B15
                val threshold = if (isNew) b15Threshold else b35Threshold
                val currentRating = currentScore?.let { calculateRating(internalLevel, it.rate, it.fc, isAfterCircle(latestVersion, versionSequence)) } ?: 0
                val isInB50 = (if (isNew) b50.b15 else b50.b35).any { it.songIdentifier == song.songIdentifier && it.diff.equals(sheet.difficulty, true) && it.type.equals(sheet.type, true) }
                var bestTarget: Pair<String, Double>? = null
                var bestPotentialRating = 0
                var bestGain = 0
                for (milestone in targetMilestones) {
                    if (milestone.second <= currentRate + 0.0001) continue
                    val potentialRating = calculateRating(internalLevel, milestone.second)
                    val gain = when {
                        isInB50 -> maxOf(0, potentialRating - currentRating)
                        potentialRating > threshold -> potentialRating - threshold
                        else -> 0
                    }
                    if (gain > 0) {
                        bestTarget = milestone
                        bestPotentialRating = potentialRating
                        bestGain = gain
                        break
                    }
                }
                if (bestTarget != null) {
                    val stats = chartStats[song.songId.toString()] ?: chartStats[song.songIdentifier]
                    val fitDiff = stats?.let { findMatchingStat(sheet, it)?.fitDiff }
                    val diffGap = fitDiff?.let { internalLevel - it }
                    val recommendation = RecommendationItem(
                        song = song,
                        sheet = sheet,
                        fitDiff = fitDiff,
                        diffGap = diffGap,
                        currentRate = currentScore?.rate,
                        potentialRating = bestPotentialRating,
                        potentialGain = bestGain,
                        targetRank = bestTarget.first,
                        targetAchievement = bestTarget.second,
                        isNew = isNew,
                        comprehensiveScore = if (isNew) bestGain + maxOf(0.0, 1.0 - abs(internalLevel - currentB15Average) / 2.0) * 5.0 else bestGain.toDouble()
                    )
                    if (isNew) b15Results += recommendation else b35Results += recommendation
                }
            }
        }

        val sortedB15 = b15Results.sortedByDescending { it.comprehensiveScore }.take(profile.b15RecLimit)
        val sortedB35 = b35Results.sortedWith(
            compareByDescending<RecommendationItem> { it.diffGap ?: Double.NEGATIVE_INFINITY }
                .thenByDescending { it.potentialGain }
        ).take(profile.b35RecLimit)
        return RecommendationResult(sortedB15, sortedB35)
    }

    fun calculatePlateProgress(songs: List<Song>, scores: List<Score>): List<PlateProgressItem> {
        val scoreMap = scores.associateBy { it.sheetId }
        return songs
            .filterNot(::isUtageSong)
            .groupBy { it.version.orEmpty() }
            .flatMap { (version, versionSongs) ->
                PlateType.entries.map { plateType ->
                    val sheets = versionSongs.flatMap { song ->
                        song.sheets.filter { it.regionJp && !it.type.contains("utage", ignoreCase = true) }
                    }
                    PlateProgressItem(
                        version = version,
                        plateType = plateType,
                        totalSheets = sheets.size,
                        completedSheets = sheets.count { plateType.isAchieved(scoreMap[it.sheetId]) }
                    )
                }
            }
            .sortedByDescending { it.version }
    }

    fun PlateType.isAchieved(score: Score?): Boolean {
        score ?: return false
        val fc = score.fc?.lowercase()
        val fs = score.fs?.lowercase()
        return when (this) {
            PlateType.KIWAMI -> fc in setOf("fc", "fcp", "ap", "app")
            PlateType.SHO -> score.rate >= 100.0
            PlateType.SHIN -> fc in setOf("ap", "app")
            PlateType.MAIMAI -> fs in setOf("fsd", "fsdp")
        }
    }

    private fun isUtageSong(song: Song): Boolean = song.category.contains("utage", ignoreCase = true) || song.category.contains("宴")

    private fun findMatchingStat(sheet: Sheet, stats: List<ChartStatDto>): ChartStatDto? {
        val index = when (sheet.difficulty.lowercase()) {
            "basic" -> 0
            "advanced" -> 1
            "expert" -> 2
            "master" -> 3
            "remaster" -> 4
            else -> return null
        }
        return stats.firstOrNull { it.diff == index.toString() }
            ?: stats.minByOrNull { abs((it.fitDiff ?: 0.0) - (sheet.internalLevelValue ?: sheet.levelValue ?: 0.0)) }
    }

    fun bestFc(current: String?, candidate: String?): String? {
        val order = listOf(null, "fc", "fcp", "ap", "app")
        return listOf(current?.lowercase(), candidate?.lowercase()).maxByOrNull { order.indexOf(it).coerceAtLeast(0) }
    }

    fun bestFs(current: String?, candidate: String?): String? {
        val order = listOf(null, "sync", "fs", "fsp", "fsd", "fsdp")
        return listOf(current?.lowercase(), candidate?.lowercase()).maxByOrNull { order.indexOf(it).coerceAtLeast(0) }
    }

    fun generatePkceCodeVerifier(): String = UUID.randomUUID().toString() + UUID.randomUUID().toString()

    fun generatePkceCodeChallenge(verifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray())
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
