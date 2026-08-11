package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ScoreToleranceCalculatorTest {
    @Test
    fun `calculates tap based tolerance using weighted note total`() {
        assertEquals(
            ScoreTolerance(great = 5, good = 2, miss = 1),
            ScoreToleranceCalculator.calculate(
                tapCount = 100,
                holdCount = 0,
                slideCount = 0,
                touchCount = 0,
                breakCount = 0,
                targetAchievement = 100.0,
            ),
        )
    }

    @Test
    fun `perfect target and empty chart allow no errors`() {
        assertEquals(
            ScoreTolerance(0, 0, 0),
            ScoreToleranceCalculator.calculate(10, 10, 10, 10, 10, 101.0),
        )
        assertEquals(
            ScoreTolerance(0, 0, 0),
            ScoreToleranceCalculator.calculate(0, 0, 0, 0, 0, 100.0),
        )
    }
}
