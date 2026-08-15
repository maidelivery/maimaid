package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchTextNormalizerTest {
    @Test
    fun `normalizes Japanese and Chinese variant forms to the same value`() {
        val pairs = listOf(
            "華天月兎" to "华天月兔",
            "櫻" to "樱",
            "竜" to "龙",
            "龍" to "龙",
            "國" to "国",
            "髙" to "高",
            "邉" to "边",
            "邊" to "边",
            "神" to "神",
            "福" to "福",
            "宿星審判" to "宿星审判",
            "並" to "并",
            "穎" to "颖",
        )

        pairs.forEach { (variant, canonical) ->
            assertEquals(
                "${variant} should match ${canonical}",
                SearchTextNormalizer.normalize(canonical),
                SearchTextNormalizer.normalize(variant),
            )
        }
    }
}
