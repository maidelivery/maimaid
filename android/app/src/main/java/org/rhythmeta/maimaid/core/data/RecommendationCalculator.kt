package org.rhythmeta.maimaid.core.data

import kotlin.math.abs
import kotlin.math.roundToInt
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity

data class RecommendationResult(
    val song: SongEntity,
    val sheet: SheetEntity,
    val fitDifficulty: Double? = null,
    val difficultyGap: Double? = null,
    val currentAchievement: Double?,
    val potentialRating: Int,
    val potentialGain: Int,
    val targetRank: String,
    val targetAchievement: Double,
    val isNew: Boolean,
    internal val comprehensiveScore: Double,
)

data class RecommendationResponse(
    val b15: List<RecommendationResult> = emptyList(),
    val b35: List<RecommendationResult> = emptyList(),
)

object RecommendationCalculator {
    private val targetMilestones = listOf(
        "S" to 97.0,
        "S+" to 98.0,
        "SS" to 99.0,
        "SS+" to 99.5,
        "SSS" to 100.0,
        "SSS+" to 100.5,
    )

    fun calculate(
        songs: List<SongEntity>,
        sheets: List<SheetEntity>,
        scores: List<ScoreEntity>,
        versions: List<GameVersionEntity>,
        profile: UserProfileEntity,
        chartFit: StaticBundleResponse.ChartFitPayload = StaticBundleResponse.ChartFitPayload(),
    ): RecommendationResponse {
        val activeSongs = songs.filterNot(SongEntity::isRemoved)
        val activeSheets = sheets.filterNot(SheetEntity::isRemoved)
        val versionNames = versions.sortedBy(GameVersionEntity::sortOrder).map(GameVersionEntity::name)
        val latestVersion = RatingUtils.latestVersionForServer(
            songs = activeSongs,
            sheets = activeSheets,
            versions = versions,
            server = profile.server,
        )
        val afterCircle = RatingUtils.isAfterCircle(latestVersion, versionNames)
        val scoresBySheet = scores.associateBy(ScoreEntity::sheetKey)
        val songsById = activeSongs.associateBy(SongEntity::songIdentifier)
        val selectedB15 = mutableListOf<SelectedEntry>()
        val selectedB35 = mutableListOf<SelectedEntry>()

        activeSheets.forEach { sheet ->
            val song = songsById[sheet.songIdentifier] ?: return@forEach
            if (song.isUtage || sheet.isUtage) return@forEach
            if (!ServerChartPolicy.isPlayable(sheet, profile.server)) return@forEach
            val score = scoresBySheet[sheet.sheetKey] ?: return@forEach
            val level = ServerChartPolicy.metadata(sheet, profile.server).ratingLevel ?: return@forEach
            val isNew = category(sheet, song, latestVersion, profile.server, versionNames)
                ?: return@forEach
            val rating = RatingUtils.calculate(
                internalLevel = level,
                achievement = score.achievement,
                fc = score.fc,
                afterCircle = afterCircle,
            )
            if (rating <= 0) return@forEach
            val entry = SelectedEntry(sheet.sheetKey, level, rating)
            (if (isNew) selectedB15 else selectedB35).add(entry)
        }

        val b15Capacity = profile.b15Count.coerceAtLeast(0)
        val b35Capacity = profile.b35Count.coerceAtLeast(0)
        val b15 = selectedB15.sortedByDescending(SelectedEntry::rating).take(b15Capacity)
        val b35 = selectedB35.sortedByDescending(SelectedEntry::rating).take(b35Capacity)
        val b15Threshold = replacementThreshold(b15, b15Capacity)
        val b35Threshold = replacementThreshold(b35, b35Capacity)
        val averageB15Level = b15.map(SelectedEntry::level).average().takeUnless(Double::isNaN) ?: 0.0
        val selectedB15Keys = b15.mapTo(mutableSetOf(), SelectedEntry::sheetKey)
        val selectedB35Keys = b35.mapTo(mutableSetOf(), SelectedEntry::sheetKey)
        val candidateLimit = if (scoresBySheet.isEmpty()) EmptyProfileCandidateLimit else CandidateLimit
        val newRecommendations = mutableListOf<RecommendationResult>()
        val oldRecommendations = mutableListOf<RecommendationResult>()

        activeSheets.forEach { sheet ->
            val song = songsById[sheet.songIdentifier] ?: return@forEach
            if (song.isUtage || sheet.isUtage) return@forEach
            if (!ServerChartPolicy.isPlayable(sheet, profile.server)) return@forEach
            val level = ServerChartPolicy.metadata(sheet, profile.server).ratingLevel ?: return@forEach
            val currentScore = scoresBySheet[sheet.sheetKey]
            val currentAchievement = currentScore?.achievement ?: 0.0
            if (currentAchievement >= 100.5) return@forEach
            val isNew = category(sheet, song, latestVersion, profile.server, versionNames)
                ?: return@forEach
            val threshold = if (isNew) b15Threshold else b35Threshold
            val isSelected = sheet.sheetKey in if (isNew) selectedB15Keys else selectedB35Keys
            val currentRating = currentScore?.let { score ->
                RatingUtils.calculate(level, score.achievement, score.fc, afterCircle)
            } ?: 0
            val target = targetMilestones.firstNotNullOfOrNull { (rank, achievement) ->
                if (achievement <= currentAchievement + AchievementTolerance) return@firstNotNullOfOrNull null
                val potentialRating = RatingUtils.calculate(level, achievement)
                val gain = if (isSelected) {
                    (potentialRating - currentRating).coerceAtLeast(0)
                } else if (potentialRating > threshold) {
                    potentialRating - threshold
                } else {
                    0
                }
                Target(rank, achievement, potentialRating, gain).takeIf { gain > 0 }
            } ?: return@forEach
            val proximity = if (isNew) {
                (1.0 - abs(level - averageB15Level) / 2.0).coerceAtLeast(0.0)
            } else {
                0.0
            }
            val fitDifficulty = fitDifficulty(sheet, chartFit)
            val result = RecommendationResult(
                song = song,
                sheet = sheet,
                fitDifficulty = fitDifficulty,
                difficultyGap = fitDifficulty?.let { level - it },
                currentAchievement = currentScore?.achievement,
                potentialRating = target.rating,
                potentialGain = target.gain,
                targetRank = target.rank,
                targetAchievement = target.achievement,
                isNew = isNew,
                comprehensiveScore = target.gain + proximity * 5.0,
            )
            (if (isNew) newRecommendations else oldRecommendations).add(result)
        }

        return RecommendationResponse(
            b15 = newRecommendations
                .sortedWith(compareByDescending<RecommendationResult> { it.comprehensiveScore }
                    .thenByDescending(RecommendationResult::potentialGain))
                .take(candidateLimit),
            b35 = oldRecommendations
                .sortedWith { left, right -> compareOldRecommendations(left, right) }
                .take(candidateLimit),
        )
    }

    private fun category(
        sheet: SheetEntity,
        song: SongEntity,
        latestVersion: String?,
        server: String,
        versions: List<String>,
    ): Boolean? {
        val metadata = ServerChartPolicy.metadata(sheet, server)
        return RatingUtils.category(
            songVersion = metadata.version ?: song.version,
            latestVersion = latestVersion,
            server = server,
            activeRegion = ServerChartPolicy.isPlayable(sheet, server),
            versions = versions,
        )
    }

    private fun replacementThreshold(entries: List<SelectedEntry>, capacity: Int): Int =
        if (capacity > 0 && entries.size >= capacity) entries.lastOrNull()?.rating ?: 0 else 0

    private fun compareOldRecommendations(
        left: RecommendationResult,
        right: RecommendationResult,
    ): Int {
        val leftGap = left.difficultyGap
        val rightGap = right.difficultyGap
        if (leftGap != null && rightGap != null) {
            val gapBandComparison = gapBand(rightGap).compareTo(gapBand(leftGap))
            if (gapBandComparison != 0) return gapBandComparison
            return right.potentialGain.compareTo(left.potentialGain)
                .takeIf { it != 0 }
                ?: rightGap.compareTo(leftGap)
                    .takeIf { it != 0 }
                ?: left.sheet.sheetKey.compareTo(right.sheet.sheetKey)
        }
        if (leftGap != null) return -1
        if (rightGap != null) return 1
        return right.potentialGain.compareTo(left.potentialGain)
            .takeIf { it != 0 }
            ?: left.sheet.sheetKey.compareTo(right.sheet.sheetKey)
    }

    private fun gapBand(gap: Double): Int = (gap * 10.0).roundToInt()

    private fun fitDifficulty(
        sheet: SheetEntity,
        chartFit: StaticBundleResponse.ChartFitPayload,
    ): Double? {
        val providerSongId = sheet.providerSongId.takeIf { it > 0 } ?: return null
        val candidateIds = buildList {
            if (sheet.type.equals("dx", ignoreCase = true) && providerSongId < 10_000) {
                add(providerSongId + 10_000)
            }
            add(providerSongId)
            if (sheet.type.equals("dx", ignoreCase = true) && providerSongId >= 10_000) {
                add(providerSongId - 10_000)
            }
        }.distinct()
        return candidateIds.firstNotNullOfOrNull { id ->
            chartFit.charts[id.toString()]
                ?.firstOrNull { it.diff == sheet.level }
                ?.fitDifficulty
        }
    }

    private val SongEntity.isUtage: Boolean
        get() = category.contains("utage", ignoreCase = true) || category.contains("宴")

    private val SheetEntity.isUtage: Boolean
        get() = type.contains("utage", ignoreCase = true)

    private data class SelectedEntry(val sheetKey: String, val level: Double, val rating: Int)
    private data class Target(val rank: String, val achievement: Double, val rating: Int, val gain: Int)

    private const val CandidateLimit = 100
    private const val EmptyProfileCandidateLimit = 50
    private const val AchievementTolerance = 0.0001
}
