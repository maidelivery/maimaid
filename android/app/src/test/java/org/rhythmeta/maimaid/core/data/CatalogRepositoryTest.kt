package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertNotEquals
import org.junit.Test

class CatalogRepositoryTest {
    @Test
    fun sheetKeySeparatesTypeAndDifficulty() {
        val standard = CatalogRepository.sheetKey("song", "std", "master")
        val deluxe = CatalogRepository.sheetKey("song", "dx", "master")
        val remaster = CatalogRepository.sheetKey("song", "dx", "remaster")

        assertNotEquals(standard, deluxe)
        assertNotEquals(deluxe, remaster)
    }
}
