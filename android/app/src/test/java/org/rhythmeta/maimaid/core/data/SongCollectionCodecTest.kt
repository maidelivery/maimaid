package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SongCollectionCodecTest {
    @Test
    fun decodesSharedGoldenPayloadAndPreservesEntryOrder() {
        val payload = SongCollectionCodec.decode(GOLDEN_PAYLOAD)

        assertEquals("Road to SSS", payload.name)
        assertEquals(listOf("100", "200", "300"), payload.entries.map(SongCollectionExportEntry::songId))
        assertEquals(listOf("master", "expert", "remaster"), payload.entries.map(SongCollectionExportEntry::difficulty))
    }

    @Test
    fun androidEncodingRoundTripsWithRawDeflate() {
        val collection = SongCollectionExport(
            name = "Test",
            entries = listOf(
                SongCollectionExportEntry(
                    songId = "song",
                    chartType = "dx",
                    difficulty = "master",
                ),
            ),
        )

        val encoded = SongCollectionCodec.encode(collection)
        val decoded = SongCollectionCodec.decode(encoded)

        assertTrue(encoded.startsWith(SongCollectionCodec.PREFIX))
        assertEquals(collection, decoded)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsLegacyPayload() {
        SongCollectionCodec.decode("MMD1.invalid")
    }

    private companion object {
        const val GOLDEN_PAYLOAD =
            "MMD2.4-IOyk9MUSjJVwgODhYS5GI2NDAQYkqpkGLLTSwuSS0SEuJiNgIKMReXpEixpVYUpBaVCAlzMRtDlXEUpUIUAgA"
    }
}
