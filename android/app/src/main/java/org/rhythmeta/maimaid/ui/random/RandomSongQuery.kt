package org.rhythmeta.maimaid.ui.random

import kotlin.random.Random
import org.rhythmeta.maimaid.core.data.CatalogSortOption
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.ui.catalog.CatalogFilterSettings
import org.rhythmeta.maimaid.ui.catalog.CatalogQuery

internal object RandomSongQuery {
    fun filter(
        songs: List<SongEntity>,
        sheetsBySong: Map<String, List<SheetEntity>>,
        versions: List<GameVersionEntity>,
        settings: CatalogFilterSettings,
        server: String = "jp",
    ): List<SongEntity> = CatalogQuery.filterAndSort(
        songs = songs,
        sheetsBySong = sheetsBySong,
        aliasesBySong = emptyMap(),
        versions = versions,
        settings = settings,
        searchText = "",
        sortOption = CatalogSortOption.DefaultOrder,
        sortAscending = true,
        server = server,
    )

    fun draw(
        pool: List<SongEntity>,
        count: Int,
        random: Random = Random.Default,
    ): List<SongEntity> {
        if (pool.isEmpty()) return emptyList()
        return List(count.coerceIn(3, 4)) { pool[random.nextInt(pool.size)] }
    }
}
