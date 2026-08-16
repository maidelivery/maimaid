package org.rhythmeta.maimaid.core.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongAliasEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class ScoreQueryCalculatorTest {
    @Test
    fun `build excludes utage and calculates rating`() {
        val normal = song("normal", "Normal")
        val utage = song("utage", "宴", category = "Utage")
        val normalSheet = sheet("normal", 13.4)
        val utageSheet = sheet("utage", 14.0, type = "utage")

        val response = ScoreQueryCalculator.build(
            songs = listOf(normal, utage),
            sheets = listOf(normalSheet, utageSheet),
            scores = listOf(score(normalSheet, 100.5), score(utageSheet, 101.0)),
            aliases = emptyList(),
        )

        assertEquals(1, response.entries.size)
        assertEquals(RatingUtils.calculate(13.4, 100.5), response.entries.single().rating)
    }

    @Test
    fun `cn build uses cn constant and excludes charts unavailable in cn`() {
        val cnSong = song("cn", "CN Song")
        val jpOnlySong = song("jp", "JP Song")
        val cnSheet = sheet("cn", 13.9).copy(cnLevelValue = 13.8, regionCn = true)
        val jpOnlySheet = sheet("jp", 13.9).copy(regionCn = false)

        val response = ScoreQueryCalculator.build(
            songs = listOf(cnSong, jpOnlySong),
            sheets = listOf(cnSheet, jpOnlySheet),
            scores = listOf(score(cnSheet, 100.0), score(jpOnlySheet, 100.0)),
            aliases = emptyList(),
            server = "cn",
        )

        assertEquals(listOf("cn"), response.entries.map(ScoreQueryEntry::songIdentifier))
        assertEquals(13.8, response.entries.single().level, 0.0)
        assertEquals(RatingUtils.calculate(13.8, 100.0), response.entries.single().rating)
    }

    @Test
    fun `search matches title artist and aliases`() {
        val song = song("id", "World's end", artist = "xi")
        val sheet = sheet("id", 13.0)
        val response = ScoreQueryCalculator.build(
            songs = listOf(song),
            sheets = listOf(sheet),
            scores = listOf(score(sheet, 100.0)),
            aliases = listOf(SongAliasEntity("id", "世界终结")),
        )

        listOf("WORLD", "XI", "世界终结").forEach { query ->
            assertEquals(1, filter(response, search = query).size)
        }
    }

    @Test
    fun `combined filters require every selected category`() {
        val first = entry(rank = "SSS+", difficulty = "master", fc = "app", fs = "fsdp")
        val second = entry(rank = "SSS", difficulty = "expert", fc = "fc", fs = "fs")
        val settings = ScoreQueryFilterSettings(
            selectedDifficulties = setOf("master"),
            selectedRanks = setOf("SSS+"),
            selectedFc = setOf("AP+"),
            selectedFs = setOf("FDX+"),
        )

        val result = ScoreQueryCalculator.filterAndSort(
            entries = listOf(first, second),
            searchText = "",
            settings = settings,
            sortMode = ScoreQuerySortMode.Rating,
            ascending = false,
        )

        assertEquals(listOf(first), result)
    }

    @Test
    fun `sorting follows selected direction and remains stable on ties`() {
        val low = entry(sheetKey = "low", title = "B", rating = 100)
        val highB = entry(sheetKey = "high-b", title = "B", rating = 200)
        val highA = entry(sheetKey = "high-a", title = "A", rating = 200)

        assertEquals(
            listOf("high-a", "high-b", "low"),
            filter(ScoreQueryResponse(listOf(low, highB, highA)), ascending = false)
                .map(ScoreQueryEntry::sheetKey),
        )
        assertEquals(
            listOf("low", "high-a", "high-b"),
            filter(ScoreQueryResponse(listOf(low, highB, highA)), ascending = true)
                .map(ScoreQueryEntry::sheetKey),
        )
    }

    @Test
    fun `stats count unique songs and score badges`() {
        val first = entry(sheetKey = "a", songId = "song", achievement = 100.5, fc = "app", fs = "fsdp")
        val second = entry(sheetKey = "b", songId = "song", achievement = 100.0, fc = "fcp", fs = "fsp")
        val third = entry(sheetKey = "c", songId = "other", achievement = 99.0)
        val stats = ScoreQueryCalculator.build(
            songs = listOf(song("song", "Song"), song("other", "Other")),
            sheets = listOf(sheet("song", 13.0, key = "a"), sheet("song", 12.0, key = "b"), sheet("other", 11.0, key = "c")),
            scores = listOf(
                score(sheet("song", 13.0, key = "a"), first.achievement, first.fc, first.fs),
                score(sheet("song", 12.0, key = "b"), second.achievement, second.fc, second.fs),
                score(sheet("other", 11.0, key = "c"), third.achievement),
            ),
            aliases = emptyList(),
        ).stats

        assertEquals(2, stats.totalPlayed)
        assertEquals(1, stats.sssPlus)
        assertEquals(1, stats.sss)
        assertEquals(1, stats.fcCount)
        assertEquals(1, stats.apCount)
        assertEquals(1, stats.fsCount)
        assertEquals(1, stats.fsdCount)
    }

    private fun filter(
        response: ScoreQueryResponse,
        search: String = "",
        ascending: Boolean = false,
    ) = ScoreQueryCalculator.filterAndSort(
        entries = response.entries,
        searchText = search,
        settings = ScoreQueryFilterSettings(),
        sortMode = ScoreQuerySortMode.Rating,
        ascending = ascending,
    )

    private fun song(id: String, title: String, artist: String = "", category: String = "maimai") = SongEntity(
        songIdentifier = id,
        category = category,
        title = title,
        artist = artist,
        imageName = "$id.png",
        version = null,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet(songId: String, level: Double, type: String = "dx", key: String = "$songId-$type-master") = SheetEntity(
        sheetKey = key,
        songIdentifier = songId,
        type = type,
        difficulty = "master",
        version = null,
        level = level.toString(),
        levelValue = level,
        internalLevel = level.toString(),
        internalLevelValue = level,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = null,
        regionJp = true,
        regionIntl = true,
        regionUsa = true,
        regionCn = true,
    )

    private fun score(sheet: SheetEntity, achievement: Double, fc: String? = null, fs: String? = null) = ScoreEntity(
        profileId = "profile",
        sheetKey = sheet.sheetKey,
        achievement = achievement,
        rank = RatingUtils.rank(achievement),
        dxScore = 0,
        fc = fc,
        fs = fs,
        achievedAt = 0,
    )

    private fun entry(
        sheetKey: String = "sheet",
        songId: String = "song",
        title: String = "Song",
        rating: Int = 200,
        achievement: Double = 100.5,
        rank: String = "SSS+",
        difficulty: String = "master",
        fc: String? = null,
        fs: String? = null,
    ) = ScoreQueryEntry(
        sheetKey = sheetKey,
        songIdentifier = songId,
        songTitle = title,
        artist = "",
        aliases = emptyList(),
        imageName = "",
        difficulty = difficulty,
        type = "dx",
        level = 13.0,
        achievement = achievement,
        rank = rank,
        rating = rating,
        fc = fc,
        fs = fs,
        dxScore = 0,
    )
}
