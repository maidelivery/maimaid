package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class StaticManifestTest {
    @Test
    fun decodesWorkerBundlePath() {
        val manifest = Json.decodeFromString<StaticManifest>(
            """{"schemaVersion":1,"version":"bundle-1","md5":"abc","bundle":"/bundles/abc.json"}""",
        )

        assertEquals("/bundles/abc.json", manifest.bundle)
    }

    @Test
    fun defaultsManifestSchemaVersion() {
        val manifest = Json.decodeFromString<StaticManifest>(
            """{"version":"bundle-1","md5":"abc","bundle":"/bundles/abc.json"}""",
        )

        assertEquals(1, manifest.schemaVersion)
    }
}
