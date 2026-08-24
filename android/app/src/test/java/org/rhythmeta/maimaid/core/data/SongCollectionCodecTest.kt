package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCollectionCodecTest {
    @Test
    fun decodesIosRawDeflateExport() {
        val encoded =
            "MMD1.q1ZKVrKKrlZKBZMpSlZKuYnFJalFSjpKBUpWBjpKxUCh4vy8dKBACZCZUqFUG6ujlAlkZqYAxfKAjJDU4hKoepBcNlDI19cl3tnfx8fVOcTT3y8YKFumZGVYCwA"

        val payload = SongCollectionCodec.decode(encoded)

        assertEquals(1, payload.version)
        assertEquals(SongCollectionCodec.Kind, payload.kind)
        assertEquals(1, payload.collections.size)
        assertEquals("Test", payload.collections.single().name)
        assertEquals("song", payload.collections.single().entries.single().songId)
    }

    @Test
    fun androidEncodingRoundTripsWithRawDeflate() {
        val collection = SongCollectionExportCollection(
            id = "collection-id",
            name = "Test",
            position = 0,
            entries = listOf(
                SongCollectionExportEntry(
                    songId = "song",
                    chartType = "dx",
                    difficulty = "master",
                    position = 0,
                ),
            ),
        )

        val encoded = SongCollectionCodec.encode(listOf(collection))
        val decoded = SongCollectionCodec.decode(encoded)

        assertTrue(encoded.startsWith(SongCollectionCodec.Prefix))
        assertEquals(collection, decoded.collections.single())
    }
}
