package net.krtl.maimaid.domain

import com.google.common.truth.Truth.assertThat
import net.krtl.maimaid.domain.model.GameServer
import net.krtl.maimaid.domain.model.PlateType
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.domain.model.UserProfile
import net.krtl.maimaid.domain.usecase.RatingEngine
import org.junit.Test

class RatingEngineTest {
    @Test
    fun `calculate rank matches maimai thresholds`() {
        assertThat(RatingEngine.calculateRank(100.5)).isEqualTo("SSS+")
        assertThat(RatingEngine.calculateRank(100.0)).isEqualTo("SSS")
        assertThat(RatingEngine.calculateRank(99.5)).isEqualTo("SS+")
        assertThat(RatingEngine.calculateRank(97.0)).isEqualTo("S")
        assertThat(RatingEngine.calculateRank(75.0)).isEqualTo("BBB")
    }

    @Test
    fun `ap bonus applies after circle`() {
        val withoutBonus = RatingEngine.calculateRating(
            internalLevel = 14.2,
            achievement = 100.5,
            fc = "ap",
            afterCircle = false
        )
        val withBonus = RatingEngine.calculateRating(
            internalLevel = 14.2,
            achievement = 100.5,
            fc = "ap",
            afterCircle = true
        )
        assertThat(withBonus).isEqualTo(withoutBonus + 1)
    }

    @Test
    fun `b50 splits current and old version songs`() {
        val currentSong = song(
            songIdentifier = "1001",
            title = "Current",
            version = "PRiSM",
            songId = 1001,
            regionIntl = true
        )
        val oldSong = song(
            songIdentifier = "1002",
            title = "Old",
            version = "FESTiVAL",
            songId = 1002,
            regionIntl = true
        )
        val profile = profile(server = GameServer.INTL, b35 = 35, b15 = 15)
        val scores = listOf(
            score(currentSong.sheets.first().sheetId, profile.id, 100.5, "ap"),
            score(oldSong.sheets.first().sheetId, profile.id, 100.0, null)
        )

        val result = RatingEngine.calculateB50(
            profile = profile,
            songs = listOf(currentSong, oldSong),
            scores = scores,
            versionSequence = listOf("FESTiVAL", "PRiSM"),
            chartStatsJson = null,
            useFitDiff = false
        )

        assertThat(result.b15).hasSize(1)
        assertThat(result.b35).hasSize(1)
        assertThat(result.b15.first().songTitle).isEqualTo("Current")
        assertThat(result.b35.first().songTitle).isEqualTo("Old")
    }

    @Test
    fun `b50 entry includes max dx score derived from total notes`() {
        val song = song(
            songIdentifier = "1003",
            title = "DX Score",
            version = "PRiSM",
            songId = 1003,
            regionIntl = true,
            totalNotes = 432
        )
        val profile = profile(server = GameServer.INTL, b35 = 35, b15 = 15)
        val score = score(song.sheets.first().sheetId, profile.id, 100.5, "ap")

        val result = RatingEngine.calculateB50(
            profile = profile,
            songs = listOf(song),
            scores = listOf(score),
            versionSequence = listOf("FESTiVAL", "PRiSM"),
            chartStatsJson = null,
            useFitDiff = false
        )

        assertThat(result.b15).hasSize(1)
        assertThat(result.b15.first().maxDxScore).isEqualTo(1296)
    }

    @Test
    fun `b50 excludes sheet that is unavailable on active server even if song exists`() {
        val intlOnlySong = song(
            songIdentifier = "1004",
            title = "Intl Only",
            version = "PRiSM",
            songId = 1004,
            regionIntl = true,
            regionJp = false
        )
        val profile = profile(server = GameServer.JP, b35 = 35, b15 = 15)
        val score = score(intlOnlySong.sheets.first().sheetId, profile.id, 100.0, null)

        val result = RatingEngine.calculateB50(
            profile = profile,
            songs = listOf(intlOnlySong),
            scores = listOf(score),
            versionSequence = listOf("FESTiVAL", "PRiSM"),
            chartStatsJson = null,
            useFitDiff = false
        )

        assertThat(result.b15).isEmpty()
        assertThat(result.b35).isEmpty()
    }

    @Test
    fun `plate rules use expected score states`() {
        val score = Score(
            scoreKey = "p::s",
            sheetId = "s",
            userProfileId = "p",
            rate = 100.0,
            rank = "SSS",
            achievementDate = 0L,
            dxScore = 0,
            fc = "ap",
            fs = "fsdp"
        )

        assertThat(with(RatingEngine) { PlateType.KIWAMI.isAchieved(score) }).isTrue()
        assertThat(with(RatingEngine) { PlateType.SHO.isAchieved(score) }).isTrue()
        assertThat(with(RatingEngine) { PlateType.SHIN.isAchieved(score) }).isTrue()
        assertThat(with(RatingEngine) { PlateType.MAIMAI.isAchieved(score) }).isTrue()
    }

    @Suppress("SameParameterValue")
    private fun profile(server: GameServer, b35: Int, b15: Int) = UserProfile(
        id = "profile-1",
        name = "Tester",
        server = server,
        avatarUrl = null,
        isActive = true,
        createdAt = 0L,
        playerRating = 0,
        plate = null,
        b35Count = b35,
        b15Count = b15,
        b35RecLimit = 10,
        b15RecLimit = 10
    )

    @Suppress("SameParameterValue")
    private fun song(
        songIdentifier: String,
        title: String,
        version: String,
        songId: Int,
        regionIntl: Boolean,
        regionJp: Boolean = true,
        totalNotes: Int? = null
    ) = Song(
        songIdentifier = songIdentifier,
        category = "POPS",
        title = title,
        artist = "Artist",
        imageName = "$songIdentifier.png",
        version = version,
        releaseDate = "2025-01-01",
        sortOrder = 0,
        bpm = 160.0,
        isNew = false,
        isLocked = false,
        comment = null,
        searchKeywords = null,
        aliases = emptyList(),
        songId = songId,
        isFavorite = false,
        sheets = listOf(
            Sheet(
                sheetId = "${songIdentifier}_dx_master",
                songIdentifier = songIdentifier,
                type = "dx",
                difficulty = "master",
                level = "14+",
                levelValue = 14.7,
                internalLevel = "14.7",
                internalLevelValue = 14.7,
                noteDesigner = null,
                tap = null,
                hold = null,
                slide = null,
                touch = null,
                breakCount = null,
                total = totalNotes,
                regionJp = regionJp,
                regionIntl = regionIntl,
                regionUsa = false,
                regionCn = false,
                songId = songId
            )
        )
    )

    private fun score(sheetId: String, profileId: String, rate: Double, fc: String?) = Score(
        scoreKey = "$profileId::$sheetId",
        sheetId = sheetId,
        userProfileId = profileId,
        rate = rate,
        rank = RatingEngine.calculateRank(rate),
        achievementDate = 0L,
        dxScore = 0,
        fc = fc,
        fs = null
    )
}
