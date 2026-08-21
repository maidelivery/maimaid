package org.rhythmeta.maimaid.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageRequestHeadersTest {
    @Test
    fun `prefers avif and webp when both decoders are available`() {
        assertEquals(
            "image/avif,image/webp,image/png,image/jpeg",
            ImageRequestHeaders.acceptHeader(supportsAvif = true, supportsWebp = true),
        )
    }

    @Test
    fun `omits avif when its decoder is unavailable`() {
        assertEquals(
            "image/webp,image/png,image/jpeg",
            ImageRequestHeaders.acceptHeader(supportsAvif = false, supportsWebp = true),
        )
    }
}
