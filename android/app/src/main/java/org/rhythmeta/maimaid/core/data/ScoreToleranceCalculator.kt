package org.rhythmeta.maimaid.core.data

import kotlin.math.floor

data class ScoreTolerance(
    val great: Int,
    val good: Int,
    val miss: Int,
)

object ScoreToleranceCalculator {
    fun calculate(
        tapCount: Int,
        holdCount: Int,
        slideCount: Int,
        touchCount: Int,
        breakCount: Int,
        targetAchievement: Double,
    ): ScoreTolerance {
        val totalBaseWeight = tapCount.toDouble() +
            holdCount * 2.0 +
            slideCount * 3.0 +
            touchCount.toDouble() +
            breakCount * 5.0
        if (totalBaseWeight <= 0.0) return ScoreTolerance(0, 0, 0)

        val maximumAllowedLoss = 101.0 - targetAchievement
        if (maximumAllowedLoss <= 0.0) return ScoreTolerance(0, 0, 0)

        fun allowedCount(judgementLoss: Double): Int {
            val lossPerTap = judgementLoss / totalBaseWeight * 100.0
            return minOf(floor(maximumAllowedLoss / lossPerTap).toInt(), tapCount)
        }

        return ScoreTolerance(
            great = allowedCount(0.2),
            good = allowedCount(0.5),
            miss = allowedCount(1.0),
        )
    }
}
