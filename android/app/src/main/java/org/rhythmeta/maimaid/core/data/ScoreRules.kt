package org.rhythmeta.maimaid.core.data

import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import kotlin.math.abs

data class ScoreInput(
    val achievement: Double,
    val dxScore: Int = 0,
    val fc: String? = null,
    val fs: String? = null,
)

enum class ScoreValidationError {
    AchievementOutOfRange,
    DxScoreOutOfRange,
}

object ScoreRules {
    fun effectiveMaxDxScore(sheetTotal: Int?, override: Int? = null): Int =
			override?.takeIf { it > 0 } ?: ((sheetTotal ?: 0) * 3)

    fun validate(input: ScoreInput, maxDxScore: Int): ScoreValidationError? = when {
        !input.achievement.isFinite() || input.achievement !in 0.0..101.0 ->
            ScoreValidationError.AchievementOutOfRange
        input.dxScore < 0 || (maxDxScore > 0 && input.dxScore > maxDxScore) ->
            ScoreValidationError.DxScoreOutOfRange
        else -> null
    }

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

    fun canonicalFc(value: String?): String? = when (val normalized = value.normalizedCode()) {
        "fc+", "fcp" -> "fcp"
        "ap+", "app" -> "app"
        else -> normalized
    }

    fun canonicalFs(value: String?): String? = when (val normalized = value.normalizedCode()) {
        "s" -> "sync"
        "fs+" -> "fsp"
        "fdx", "fsd" -> "fsd"
        "fdx+", "fsdp" -> "fsdp"
        else -> normalized
    }

    fun displayFc(value: String?): String? = when (val canonical = canonicalFc(value)) {
        "fcp" -> "FC+"
        "app" -> "AP+"
        null -> null
        else -> canonical.uppercase()
    }

    fun displayFs(value: String?): String? = when (val canonical = canonicalFs(value)) {
        "sync" -> "S"
        "fsp" -> "FS+"
        "fsd" -> "FDX"
        "fsdp" -> "FDX+"
        null -> null
        else -> canonical.uppercase()
    }

    fun mergeScore(
        profileId: String,
        sheetKey: String,
        existing: ScoreEntity?,
        input: ScoreInput,
        now: Long,
    ): ScoreEntity {
        val rank = calculateRank(input.achievement)
        val incomingFc = canonicalFc(input.fc)
        val incomingFs = canonicalFs(input.fs)
        if (existing == null) {
            return ScoreEntity(
                profileId = profileId,
                sheetKey = sheetKey,
                achievement = input.achievement,
                rank = rank,
                dxScore = input.dxScore,
                fc = incomingFc,
                fs = incomingFs,
                achievedAt = now,
            )
        }

        val isAchievementBetter = input.achievement > existing.achievement
        return existing.copy(
            achievement = if (isAchievementBetter) input.achievement else existing.achievement,
            rank = if (isAchievementBetter) rank else existing.rank,
            dxScore = maxOf(existing.dxScore, input.dxScore),
            fc = bestFc(existing.fc, incomingFc),
            fs = bestFs(existing.fs, incomingFs),
            achievedAt = if (isAchievementBetter) now else existing.achievedAt,
        )
    }

    fun bestHistoryRecord(records: List<PlayRecordEntity>): PlayRecordEntity? =
        records.maxWithOrNull(compareBy<PlayRecordEntity> { it.achievement }.thenBy { it.playedAt })

    fun deletedRecordWasBest(score: ScoreEntity, deleted: PlayRecordEntity): Boolean =
        abs(score.achievement - deleted.achievement) < AchievementEpsilon

    private fun bestFc(first: String?, second: String?): String? =
        if (fcOrder(first) >= fcOrder(second)) canonicalFc(first) else canonicalFc(second)

    private fun bestFs(first: String?, second: String?): String? =
        if (fsOrder(first) >= fsOrder(second)) canonicalFs(first) else canonicalFs(second)

    private fun fcOrder(value: String?): Int = when (canonicalFc(value)) {
        "app" -> 4
        "ap" -> 3
        "fcp" -> 2
        "fc" -> 1
        else -> 0
    }

    private fun fsOrder(value: String?): Int = when (canonicalFs(value)) {
        "fsdp" -> 5
        "fsd" -> 4
        "fsp" -> 3
        "fs" -> 2
        "sync" -> 1
        else -> 0
    }

    private fun String?.normalizedCode(): String? = this
        ?.trim()
        ?.lowercase()
        ?.takeIf(String::isNotEmpty)

    private const val AchievementEpsilon = 0.0001
}
