package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StaticManifestTest {
    @Test
    fun decodesR2DownloadUrl() {
        val manifest = Json.decodeFromString<StaticManifest>(
            """{"version":"bundle-1","md5":"abc","downloadUrl":"https://static.example.com/static-bundles/bundle-1.json"}""",
        )

        assertEquals("https://static.example.com/static-bundles/bundle-1.json", manifest.downloadUrl)
    }

    @Test
    fun acceptsLegacyManifestWithoutDownloadUrl() {
        val manifest = Json.decodeFromString<StaticManifest>("""{"version":"bundle-1","md5":"abc"}""")

        assertEquals(null, manifest.downloadUrl)
    }
}
