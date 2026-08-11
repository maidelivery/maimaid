package org.rhythmeta.maimaid.core.ml

data class Detection(
    val classIndex: Int,
    val confidence: Float,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

object NonMaximumSuppression {
    fun classAware(
        detections: List<Detection>,
        intersectionOverUnionThreshold: Float,
    ): List<Detection> {
        require(intersectionOverUnionThreshold in 0f..1f)
        val retained = mutableListOf<Detection>()

        detections
            .groupBy(Detection::classIndex)
            .values
            .forEach { classDetections ->
                val remaining = classDetections.sortedByDescending(Detection::confidence).toMutableList()
                while (remaining.isNotEmpty()) {
                    val best = remaining.removeAt(0)
                    retained += best
                    remaining.removeAll { candidate ->
                        intersectionOverUnion(best, candidate) > intersectionOverUnionThreshold
                    }
                }
            }

        return retained.sortedByDescending(Detection::confidence)
    }

    private fun intersectionOverUnion(first: Detection, second: Detection): Float {
        val intersectionWidth = (minOf(first.right, second.right) - maxOf(first.left, second.left)).coerceAtLeast(0f)
        val intersectionHeight = (minOf(first.bottom, second.bottom) - maxOf(first.top, second.top)).coerceAtLeast(0f)
        val intersection = intersectionWidth * intersectionHeight
        val firstArea = (first.right - first.left).coerceAtLeast(0f) * (first.bottom - first.top).coerceAtLeast(0f)
        val secondArea = (second.right - second.left).coerceAtLeast(0f) * (second.bottom - second.top).coerceAtLeast(0f)
        val union = firstArea + secondArea - intersection
        return if (union > 0f) intersection / union else 0f
    }
}
