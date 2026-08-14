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
}
