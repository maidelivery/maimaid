package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ProviderSongIdResolverTest {
    @Test
    fun `dx internal id resolves to provider id 11422`() {
        assertEquals(11_422, ProviderSongIdResolver.resolve(internalId = 1_422, chartType = "dx"))
    }

    @Test
    fun `already expanded dx id remains unchanged`() {
        assertEquals(11_422, ProviderSongIdResolver.resolve(internalId = 11_422, chartType = "dx"))
    }

    @Test
    fun `base and dx ids are related for alias lookup`() {
        assertEquals(listOf(1_422, 11_422), ProviderSongIdResolver.relatedIds(1_422))
        assertEquals(listOf(11_422, 1_422), ProviderSongIdResolver.relatedIds(11_422))
    }
}
