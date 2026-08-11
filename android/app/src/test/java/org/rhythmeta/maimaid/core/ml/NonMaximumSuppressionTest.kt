package org.rhythmeta.maimaid.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NonMaximumSuppressionTest {
    @Test
    fun removesLowerConfidenceOverlapWithinOneClass() {
        val result = NonMaximumSuppression.classAware(
            detections = listOf(
                detection(classIndex = 1, confidence = 0.9f, left = 0f),
                detection(classIndex = 1, confidence = 0.7f, left = 1f),
            ),
            intersectionOverUnionThreshold = 0.5f,
        )

        assertEquals(1, result.size)
        assertEquals(0.9f, result.single().confidence)
    }

    @Test
    fun retainsOverlappingBoxesFromDifferentClasses() {
        val result = NonMaximumSuppression.classAware(
            detections = listOf(
                detection(classIndex = 1, confidence = 0.9f, left = 0f),
                detection(classIndex = 2, confidence = 0.8f, left = 0f),
            ),
            intersectionOverUnionThreshold = 0.5f,
        )

        assertEquals(2, result.size)
        assertTrue(result[0].confidence > result[1].confidence)
    }

    private fun detection(classIndex: Int, confidence: Float, left: Float) = Detection(
        classIndex = classIndex,
        confidence = confidence,
        left = left,
        top = 0f,
        right = left + 10f,
        bottom = 10f,
    )
}
