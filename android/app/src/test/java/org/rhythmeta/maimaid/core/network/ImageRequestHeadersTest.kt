package org.rhythmeta.maimaid.core.network

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageRequestHeadersTest {
    @Test
    fun `requests png directly`() {
        assertEquals("image/png", ImageRequestHeaders.ACCEPT)
    }
}
