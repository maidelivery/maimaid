package org.rhythmeta.maimaid.core.ml

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

class ScannerSongMatcherTest {
    @Test
    fun maxDxScoreResolvesTheMatchingChart() {
        val song = song("song", "夜に駆ける")
        val sheets = listOf(
            sheet("basic", "dx", "basic", song.songIdentifier, 100),
            sheet("master", "dx", "master", song.songIdentifier, 200),
        )
        val catalog = ScannerCatalog(listOf(song), sheets, emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
            chartType = "dx",
            difficulty = "master",
            maxDxScore = 600,
        )

        val result = ScannerSongMatcher.match(raw, catalog).single()
        assertEquals("master", result.sheet?.difficulty)
    }

    @Test
    fun chooseScanNeverReturnsTheBlankTitleSong() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val sheet = sheet("basic", "dx", "basic", realSong.songIdentifier, 100)
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val catalog = ScannerCatalog(listOf(blankSong, realSong), listOf(blankSheet, sheet), emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Choose,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
        )

        val result = ScannerSongMatcher.matchFast(raw, catalog)
        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun scoreMatchDoesNotTreatBlankTitleAsAUniversalMatch() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val realSheet = sheet("real", "dx", "basic", realSong.songIdentifier, 100)
        val catalog = ScannerCatalog(listOf(blankSong, realSong), listOf(blankSheet, realSheet), emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
            chartType = "dx",
            difficulty = "basic",
        )

        val result = ScannerSongMatcher.match(raw, catalog)
        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun blankTitleSongAliasesDoNotMatchOtherSongs() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val realSheet = sheet("real", "dx", "basic", realSong.songIdentifier, 100)
        val catalog = ScannerCatalog(
            songs = listOf(blankSong, realSong),
            sheets = listOf(blankSheet, realSheet),
            aliasesBySong = mapOf(blankSong.songIdentifier to listOf("夜に駆ける", "任意社区别名")),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "夜に駆ける",
            titleCandidates = listOf("夜に駆ける"),
            chartType = "dx",
            difficulty = "basic",
        )

        val result = ScannerSongMatcher.match(raw, catalog)

        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun fastMatchDoesNotUseBlankTitleSongAsAnUnrelatedFallback() {
        val blankSong = song("　", "")
        val realSong = song("song", "夜に駆ける")
        val blankSheet = sheet("blank", "dx", "basic", blankSong.songIdentifier, 100)
            .copy(providerSongId = 11_422)
        val realSheet = sheet("real", "dx", "basic", realSong.songIdentifier, 101)
        val catalog = ScannerCatalog(
            songs = listOf(blankSong, realSong),
            sheets = listOf(blankSheet, realSheet),
            aliasesBySong = mapOf(blankSong.songIdentifier to listOf("任意社区别名")),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "完全不存在的歌名",
            maxDxScore = 303,
            chartType = "dx",
            difficulty = "basic",
        )

        val result = ScannerSongMatcher.matchFast(raw, catalog)

        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun fastMatchDoesNotFallbackToTheFirstUnknownTotalUtageSong() {
        val loveYou = song("(宴) Love You", "Love You")
        val unknown = song("unknown", "完全不存在的宴谱")
        val catalog = ScannerCatalog(
            songs = listOf(loveYou, unknown),
            sheets = listOf(
                sheet("love", "utage", "【協】", loveYou.songIdentifier, null),
                sheet("unknown", "utage", "【宴】", unknown.songIdentifier, null),
            ),
            aliasesBySong = emptyMap(),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "无法匹配的标题",
            maxDxScore = 1_086,
            chartType = "utage",
            difficulty = "utage",
        )

        val result = ScannerSongMatcher.matchFast(raw, catalog)
        val photoResult = ScannerSongMatcher.match(raw.copy(title = null), catalog)

        assertEquals(emptyList<String>(), result.map { it.song.songIdentifier })
        assertEquals(emptyList<String>(), photoResult.map { it.song.songIdentifier })
    }

    @Test
    fun staleUtageStatsYieldToReliableTitleEvidence() {
        val loveYou = song("(宴) Love You", "Love You")
        val target = song("[耐]星界", "[耐]星界ちゃんと可不ちゃんのおつかい合騒曲")
        val distractor = song("[耐]CYCLES", "[耐]CYCLES")
        val catalog = ScannerCatalog(
            songs = listOf(loveYou, distractor, target),
            sheets = listOf(
                sheet("love", "utage", "【協】", loveYou.songIdentifier, null),
                sheet("cycles", "utage", "【耐】", distractor.songIdentifier, null),
                sheet("target", "utage", "【耐】", target.songIdentifier, 1_015),
            ),
            aliasesBySong = emptyMap(),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "動星界ちゃんと可不ちゃんのおつかい合融曲",
            titleCandidates = listOf(
                "動星界ちゃんと可不ちゃんのおつかい合融曲",
                "耐星界ちゃんと可不ちゃんのおつかい合騒曲",
            ),
            maxDxScore = 3_891,
            dxScore = 3_515,
            chartType = "utage",
            difficulty = "utage",
            kanji = "耐",
        )

        val fastResult = ScannerSongMatcher.matchFast(raw, catalog).single()
        val photoResult = ScannerSongMatcher.match(raw, catalog).single()

        assertEquals(target.songIdentifier, fastResult.song.songIdentifier)
        assertEquals(target.songIdentifier, photoResult.song.songIdentifier)
        assertEquals("target", fastResult.sheet?.sheetKey)
        assertEquals("target", photoResult.sheet?.sheetKey)
    }

    @Test
    fun exactSmallModelTitleRecoversStaleSeikaiUtageStats() {
        val target = song(
            "[耐]星界ちゃんと可不ちゃんのおつかい合騒曲",
            "[耐]星界ちゃんと可不ちゃんのおつかい合騒曲",
        )
        val catalog = ScannerCatalog(
            songs = listOf(target),
            sheets = listOf(sheet("target", "utage", "【耐】", target.songIdentifier, 1_015)),
            aliasesBySong = emptyMap(),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "[耐]星界ちゃんと可不ちゃんのおつかい合騒曲",
            titleCandidates = listOf("[耐]星界ちゃんと可不ちゃんのおつかい合騒曲"),
            maxDxScore = 3_891,
            dxScore = 3_515,
            chartType = "utage",
            difficulty = "utage",
            kanji = "耐",
        )

        val fastResult = ScannerSongMatcher.matchFast(raw, catalog).single()
        val photoResult = ScannerSongMatcher.match(raw, catalog).single()

        assertEquals(target.songIdentifier, fastResult.song.songIdentifier)
        assertEquals(target.songIdentifier, photoResult.song.songIdentifier)
        assertEquals("target", fastResult.sheet?.sheetKey)
        assertEquals("target", photoResult.sheet?.sheetKey)
    }

    @Test
    fun exactSmallModelTitleRecoversStaleLiarDancerUtageStats() {
        val target = song("[嘘]ライアーダンサー", "[嘘]ライアーダンサー")
        val catalog = ScannerCatalog(
            songs = listOf(target),
            sheets = listOf(sheet("target", "utage", "【嘘】", target.songIdentifier, 340)),
            aliasesBySong = emptyMap(),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "ライアーダンサー",
            titleCandidates = listOf("ライアーダンサー"),
            maxDxScore = 1_086,
            dxScore = 1_030,
            chartType = "utage",
            difficulty = "utage",
            kanji = "嘘",
        )

        val fastResult = ScannerSongMatcher.matchFast(raw, catalog).single()
        val photoResult = ScannerSongMatcher.match(raw, catalog).single()

        assertEquals(target.songIdentifier, fastResult.song.songIdentifier)
        assertEquals(target.songIdentifier, photoResult.song.songIdentifier)
        assertEquals("target", fastResult.sheet?.sheetKey)
        assertEquals("target", photoResult.sheet?.sheetKey)
    }

    @Test
    fun noisyUniqueUtageKanjiOverridesStaleNoteCount() {
        val loveYou = song("(宴) Love You", "Love You")
        val target = song("[嘘]ライアーダンサー", "[嘘]ライアーダンサー")
        val catalog = ScannerCatalog(
            songs = listOf(loveYou, target),
            sheets = listOf(
                sheet("love", "utage", "【協】", loveYou.songIdentifier, null),
                sheet("target", "utage", "【嘘】", target.songIdentifier, 340),
            ),
            aliasesBySong = emptyMap(),
        )
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "の「面ライアーダンサーム",
            titleCandidates = listOf("の「面ライアーダンサーム"),
            maxDxScore = 1_086,
            dxScore = 1_030,
            chartType = "utage",
            difficulty = "utage",
            kanji = "C嘘",
        )

        val fastResult = ScannerSongMatcher.matchFast(raw, catalog).single()
        val photoResult = ScannerSongMatcher.match(raw, catalog).single()

        assertEquals(target.songIdentifier, fastResult.song.songIdentifier)
        assertEquals(target.songIdentifier, photoResult.song.songIdentifier)
        assertEquals("target", fastResult.sheet?.sheetKey)
        assertEquals("target", photoResult.sheet?.sheetKey)
    }

    @Test
    fun laterExactOcrCandidateReplacesAnEarlierFuzzyScore() {
        val realSong = song("song", "きゅうくらりん")
        val realSheet = sheet("real", "dx", "master", realSong.songIdentifier, 100)
        val catalog = ScannerCatalog(listOf(realSong), listOf(realSheet), emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "きゅうくらり",
            titleCandidates = listOf("きゅうくらり", "きゅうくらりん"),
            chartType = "dx",
            difficulty = "master",
        )

        val result = ScannerSongMatcher.match(raw, catalog)

        assertEquals(listOf("song"), result.map { it.song.songIdentifier })
    }

    @Test
    fun staleUtageStatsCannotResolveLoveYouWithoutTitleEvidence() {
        val loveYou = song("[協]Love You", "[協]Love You")
        val utageSheet = sheet("utage", "utage", "【協】", loveYou.songIdentifier, 500)
        val catalog = ScannerCatalog(listOf(loveYou), listOf(utageSheet), emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "完全无关的标题",
            chartType = "utage",
            kanji = "協",
            maxDxScore = 1_503,
        )

        val result = ScannerSongMatcher.resolveSheet(loveYou, raw, catalog)

        assertNull(result)
    }

    @Test
    fun maxDxNotesAtOrBelowCandidateTotalRemainCompatible() {
        assertEquals(true, ScannerNoteCountValidator.isCompatible(maxDxScore = 1_500, sheetTotal = 500))
        assertEquals(true, ScannerNoteCountValidator.isCompatible(maxDxScore = 1_497, sheetTotal = 500))
        assertEquals(false, ScannerNoteCountValidator.isCompatible(maxDxScore = 1_503, sheetTotal = 500))
    }

    @Test
    fun titleWinsWhenDetectorConfusesStandardWithDx() {
        val axeria = song("Axeria", "Axeria")
        val huatian = song("華天月兎", "華天月兎")
        val sheets = listOf(
            sheet("axeria-master", "std", "master", axeria.songIdentifier, 888)
                .copy(level = "14", levelValue = 14.0, internalLevel = "14", internalLevelValue = 14.0),
            sheet("huatian-master", "dx", "master", huatian.songIdentifier, 888)
                .copy(level = "14", levelValue = 14.0, internalLevel = "14", internalLevelValue = 14.0),
        )
        val catalog = ScannerCatalog(listOf(axeria, huatian), sheets, emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "Axera",
            titleCandidates = listOf("Axera"),
            chartType = "dx",
            difficulty = "master",
            level = 14.0,
            maxDxScore = 2_664,
        )

        val result = ScannerSongMatcher.match(raw, catalog).single()

        assertEquals("Axeria", result.song.songIdentifier)
        assertEquals("std", result.sheet?.type)
    }

    @Test
    fun fastTitleMatchAlsoRecoversStandardChartAfterTypeMisclassification() {
        val axeria = song("Axeria", "Axeria")
        val huatian = song("華天月兎", "華天月兎")
        val sheets = listOf(
            sheet("axeria-master", "std", "master", axeria.songIdentifier, 888)
                .copy(level = "14", levelValue = 14.0, internalLevel = "14", internalLevelValue = 14.0),
            sheet("huatian-master", "dx", "master", huatian.songIdentifier, 888)
                .copy(level = "14", levelValue = 14.0, internalLevel = "14", internalLevelValue = 14.0),
        )
        val catalog = ScannerCatalog(listOf(axeria, huatian), sheets, emptyMap())
        val raw = ScannerRawResult(
            screenType = MaimaiScreenType.Score,
            title = "Axera",
            titleCandidates = listOf("Axera"),
            chartType = "dx",
            difficulty = "master",
            level = 14.0,
            maxDxScore = 2_664,
        )

        val result = ScannerSongMatcher.matchFast(raw, catalog).first()

        assertEquals("Axeria", result.song.songIdentifier)
        assertEquals("std", result.sheet?.type)
    }

    private fun song(id: String, title: String) = SongEntity(
        songIdentifier = id,
        category = "",
        title = title,
        artist = "",
        imageName = "",
        version = null,
        releaseDate = null,
        sortOrder = 0,
        bpm = null,
        isNew = false,
        isLocked = false,
        comment = null,
    )

    private fun sheet(key: String, type: String, difficulty: String, songId: String, total: Int?) = SheetEntity(
        sheetKey = key,
        songIdentifier = songId,
        type = type,
        difficulty = difficulty,
        version = null,
        level = "10",
        levelValue = 10.0,
        internalLevel = "10+",
        internalLevelValue = 10.5,
        noteDesigner = null,
        tap = null,
        hold = null,
        slide = null,
        touch = null,
        breakCount = null,
        total = total,
        regionJp = true,
        regionIntl = true,
        regionUsa = true,
        regionCn = true,
    )
}
