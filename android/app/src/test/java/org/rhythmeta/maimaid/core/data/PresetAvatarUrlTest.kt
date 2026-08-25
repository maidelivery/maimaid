package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetAvatarUrlTest {
    @Test
    fun `builds and recognizes LXNS preset avatar urls`() {
        val url = PresetAvatarUrl.forIcon(123)

        assertEquals("https://assets2.lxns.net/maimai/icon/123.png", url)
        assertTrue(PresetAvatarUrl.isPreset(url))
        assertTrue(PresetAvatarUrl.isPreset("$url?v=2026"))
        assertEquals(123, PresetAvatarUrl.iconId(url))
        assertEquals(123, PresetAvatarUrl.iconId("$url?v=2026"))
    }

    @Test
    fun `does not classify R2 avatar urls as presets`() {
        assertFalse(PresetAvatarUrl.isPreset("https://api.example.com/v1/profiles/id/avatar"))
        assertEquals(null, PresetAvatarUrl.iconId("https://api.example.com/v1/profiles/id/avatar"))
    }

    @Test
    fun `requests png with raw and upstream fallbacks`() {
        val assets = StaticAssetConfiguration(
            coverBaseUrl = "https://static.example.com/cdn-cgi/image/format=avif/static-assets/covers/",
            coverFallbackBaseUrl = "https://static.example.com/static-assets/covers/",
            presetAvatarBaseUrl = "https://static.example.com/cdn-cgi/image/format=avif/static-assets/lxns-icons/",
            presetAvatarFallbackBaseUrl = "https://static.example.com/static-assets/lxns-icons/",
        )
        StaticAssetUrls.configure(assets)
        try {
            assertEquals(
                listOf(
                    "https://static.example.com/cdn-cgi/image/format=png/static-assets/covers/cover.png",
                    "https://static.example.com/static-assets/covers/cover.png",
                    "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/cover.png",
                ),
                StaticAssetUrls.coverCandidates("cover.png"),
            )
            val avatarUrl = PresetAvatarUrl.forIcon(123)
            assertEquals(
                "https://static.example.com/cdn-cgi/image/format=png/static-assets/lxns-icons/123.png",
                avatarUrl,
            )
            assertTrue(PresetAvatarUrl.isPreset(avatarUrl))
            assertEquals(123, PresetAvatarUrl.iconId(avatarUrl))
        } finally {
            StaticAssetUrls.configure(null)
        }
    }

    @Test
    fun `normalizes legacy image transformations to png`() {
        listOf(
            "format=avif",
            "f=avif",
            "format=auto",
            "f=auto",
            "format=webp",
            "f=webp",
        ).forEach { transformation ->
            assertEquals(
                "https://static.example.com/cdn-cgi/image/format=png/covers/",
                StaticAssetUrls.normalizeImageTransformation(
                    "https://static.example.com/cdn-cgi/image/$transformation/covers/",
                ),
            )
        }
    }
}
