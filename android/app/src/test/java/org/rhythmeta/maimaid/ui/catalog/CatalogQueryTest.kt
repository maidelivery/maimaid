package org.rhythmeta.maimaid.ui.catalog

import org.junit.Assert.assertEquals
import org.junit.Test
import org.rhythmeta.maimaid.core.data.CatalogSortOption
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class CatalogQueryTest {
    private val versions = listOf(
        GameVersionEntity("V1", "V1", "2020-01-01", 0),
        GameVersionEntity("V2", "V2", "2021-01-01", 1),
    )

    @Test
    fun `combined filters follow the iOS chart matching contract`() {
        val matching = song("matching", category = "POPS", version = "V1", favorite = true)
        val other = song("other", category = "POPS", version = "V1", favorite = true)
        val sheets = mapOf(
            matching.songIdentifier to listOf(
                sheet(matching, type = "dx", difficulty = "basic", constant = 5.0, playable = true),
                sheet(matching, type = "std", difficulty = "master", constant = 13.0, playable = false),
            ),
            other.songIdentifier to listOf(
                sheet(other, type = "std", difficulty = "master", constant = 13.0, playable = true),
            ),
        )

        val result = query(
            songs = listOf(matching, other),
            sheets = sheets,
            settings = CatalogFilterSettings(
                selectedCategories = setOf("POPS"),
                selectedVersions = setOf("V1"),
                selectedDifficulties = setOf("master"),
                selectedTypes = setOf("dx"),
                minLevel = 12.5,
                maxLevel = 13.5,
                showFavoritesOnly = true,
                hideUnavailableSongs = true,
            ),
        )

        assertEquals(listOf("matching"), result.map(SongEntity::songIdentifier))
    }

    @Test
    fun `playable filter checks all supported regions`() {
        val playable = song("playable")
        val unavailable = song("unavailable")
        val result = query(
            songs = listOf(playable, unavailable),
            sheets = mapOf(
                playable.songIdentifier to listOf(sheet(playable, playable = true)),
                unavailable.songIdentifier to listOf(sheet(unavailable, playable = false)),
            ),
            settings = CatalogFilterSettings(hideUnavailableSongs = true),
        )

        assertEquals(listOf("playable"), result.map(SongEntity::songIdentifier))
    }

    @Test
    fun `hide unavailable keeps jp only songs for cn profiles`() {
        val jpOnly = song("jp-only")
        val deleted = song("deleted")

        val result = query(
            songs = listOf(jpOnly, deleted),
            sheets = mapOf(
                jpOnly.songIdentifier to listOf(sheet(jpOnly, playable = true)),
                deleted.songIdentifier to listOf(sheet(deleted, playable = false)),
            ),
            settings = CatalogFilterSettings(hideUnavailableSongs = true),
            server = "cn",
        )

        assertEquals(listOf("jp-only"), result.map(SongEntity::songIdentifier))
    }

    @Test
    fun `playable only filters songs by the active server`() {
        val cnPlayable = song("cn-playable")
        val jpOnly = song("jp-only")

        val result = query(
            songs = listOf(cnPlayable, jpOnly),
            sheets = mapOf(
                cnPlayable.songIdentifier to listOf(sheet(cnPlayable, playable = true).copy(regionCn = true)),
                jpOnly.songIdentifier to listOf(sheet(jpOnly, playable = true)),
            ),
            settings = CatalogFilterSettings(showPlayableSongsOnly = true),
            server = "cn",
        )

        assertEquals(listOf("cn-playable"), result.map(SongEntity::songIdentifier))
    }

    @Test
    fun `cn constant controls level filtering and difficulty sorting`() {
        val lower = song("lower", sortOrder = 0)
        val higher = song("higher", sortOrder = 1)
        val sheets = mapOf(
            lower.songIdentifier to listOf(
                sheet(lower, constant = 13.9).copy(cnLevelValue = 13.7, regionCn = true),
            ),
            higher.songIdentifier to listOf(
                sheet(higher, constant = 13.9).copy(cnLevelValue = 13.8, regionCn = true),
            ),
        )

        val filtered = query(
            songs = listOf(lower, higher),
            sheets = sheets,
            settings = CatalogFilterSettings(
                selectedDifficulties = setOf("master"),
                minLevel = 13.8,
                maxLevel = 13.8,
            ),
            server = "cn",
        )
        val sorted = query(
            songs = listOf(lower, higher),
            sheets = sheets,
            sortOption = CatalogSortOption.Difficulty,
            ascending = false,
            server = "cn",
        )

        assertEquals(listOf("higher"), filtered.map(SongEntity::songIdentifier))
        assertEquals(listOf("higher", "lower"), sorted.map(SongEntity::songIdentifier))
    }

    @Test
    fun `version and date sorting uses version order then release date`() {
        val newestV1 = song("new-v1", version = "V1", releaseDate = "2020-06-01", sortOrder = 0)
        val oldestV1 = song("old-v1", version = "V1", releaseDate = "2020-02-01", sortOrder = 1)
        val v2 = song("v2", version = "V2", releaseDate = "2021-01-01", sortOrder = 2)
        val songs = listOf(newestV1, v2, oldestV1)

        val ascending = query(
            songs = songs,
            sortOption = CatalogSortOption.VersionAndDate,
            ascending = true,
        )
        val descending = query(
            songs = songs,
            sortOption = CatalogSortOption.VersionAndDate,
            ascending = false,
        )

        assertEquals(listOf("old-v1", "new-v1", "v2"), ascending.map(SongEntity::songIdentifier))
        assertEquals(listOf("v2", "new-v1", "old-v1"), descending.map(SongEntity::songIdentifier))
    }

    @Test
    fun `difficulty sorting uses the highest chart constant`() {
        val easy = song("easy", sortOrder = 0)
        val hard = song("hard", sortOrder = 1)
        val sheets = mapOf(
            easy.songIdentifier to listOf(sheet(easy, constant = 12.0)),
            hard.songIdentifier to listOf(
                sheet(hard, difficulty = "expert", constant = 12.8),
                sheet(hard, difficulty = "master", constant = 14.2),
            ),
        )

        val descending = query(
            songs = listOf(easy, hard),
            sheets = sheets,
            sortOption = CatalogSortOption.Difficulty,
            ascending = false,
        )

        assertEquals(listOf("hard", "easy"), descending.map(SongEntity::songIdentifier))
    }

    @Test
    fun `default sorting applies both directions`() {
        val first = song("first", sortOrder = 1)
        val second = song("second", sortOrder = 2)

        assertEquals(
            listOf("first", "second"),
            query(listOf(second, first), ascending = true).map(SongEntity::songIdentifier),
        )
        assertEquals(
            listOf("second", "first"),
            query(listOf(first, second), ascending = false).map(SongEntity::songIdentifier),
        )
    }

    @Test
    fun `search matches aliases while ignoring whitespace`() {
        val matching = song("matching")
        val other = song("other")

        val result = query(
            songs = listOf(matching, other),
            aliases = mapOf(matching.songIdentifier to listOf("World Vanquisher")),
            searchText = "worldvanquisher",
        )

        assertEquals(listOf("matching"), result.map(SongEntity::songIdentifier))
    }

    @Test
    fun `search matches simplified traditional and variant Chinese title forms`() {
        val matching = song("matching").copy(title = "華天月兎")
        val other = song("other")

        listOf("华天月兔", "華天月兔", "華天月兎").forEach { queryText ->
            val result = query(
                songs = listOf(matching, other),
                searchText = queryText,
            )

            assertEquals(listOf("matching"), result.map(SongEntity::songIdentifier))
        }
    }

    @Test
    fun `search matches simplified query against Japanese traditional title`() {
        val matching = song("matching").copy(title = "宿星審判")
        val other = song("other")

        assertEquals(
            listOf("matching"),
            query(listOf(matching, other), searchText = "审判")
                .map(SongEntity::songIdentifier),
        )
    }

    @Test
    fun `search matches note designer and exact provider song id`() {
        val matching = song("matching")
        val other = song("other")
        val sheets = mapOf(
            matching.songIdentifier to listOf(
                sheet(matching, noteDesigner = "mai star", providerSongId = 10_001),
            ),
            other.songIdentifier to listOf(sheet(other)),
        )

        assertEquals(
            listOf("matching"),
            query(listOf(matching, other), sheets = sheets, searchText = "maistar")
                .map(SongEntity::songIdentifier),
        )
        assertEquals(
            listOf("matching"),
            query(listOf(matching, other), sheets = sheets, searchText = "10001")
                .map(SongEntity::songIdentifier),
        )
    }

    private fun query(
        songs: List<SongEntity>,
        sheets: Map<String, List<SheetEntity>> = emptyMap(),
        aliases: Map<String, List<String>> = emptyMap(),
        settings: CatalogFilterSettings = CatalogFilterSettings(),
        searchText: String = "",
        sortOption: CatalogSortOption = CatalogSortOption.DefaultOrder,
        ascending: Boolean = true,
        server: String = "jp",
    ): List<SongEntity> = CatalogQuery.filterAndSort(
        songs = songs,
        sheetsBySong = sheets,
        aliasesBySong = aliases,
        versions = versions,
        settings = settings,
        searchText = searchText,
        sortOption = sortOption,
        sortAscending = ascending,
        server = server,
    )

    private fun song(
        id: String,
        category: String = "POPS",
        version: String? = "V1",
        releaseDate: String? = "2020-01-01",
        sortOrder: Int = 0,
        favorite: Boolean = false,
    ) = SongEntity(
        songIdentifier = id,
        category = category,
        title = id,
        artist = "artist",
        imageName = "$id.png",
        version = version,
        releaseDate = releaseDate,
        sortOrder = sortOrder,
        bpm = 120.0,
        isNew = false,
        isLocked = false,
        comment = null,
        isFavorite = favorite,
    )

    private fun sheet(
        song: SongEntity,
        type: String = "dx",
        difficulty: String = "master",
        constant: Double = 13.0,
        playable: Boolean = true,
        noteDesigner: String? = null,
        providerSongId: Int = 0,
    ) = SheetEntity(
        sheetKey = "${song.songIdentifier}-$type-$difficulty-$constant-$playable",
        songIdentifier = song.songIdentifier,
        type = type,
        difficulty = difficulty,
        version = song.version,
        level = constant.toString(),
        levelValue = constant,
        internalLevel = constant.toString(),
        internalLevelValue = constant,
        noteDesigner = noteDesigner,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = playable,
        regionIntl = false,
        regionUsa = false,
        regionCn = false,
        providerSongId = providerSongId,
    )
}
