package net.krtl.maimaid.ui.song

data class SongSharedTransitionState(
    val songIdentifier: String,
    val displayMode: String,
    val anchorIndex: Int,
    val anchorOffset: Int,
    val sourceRoute: String = ""
)

typealias SearchSharedTransitionState = SongSharedTransitionState

object SongMotionTokens {
    const val CONTAINER_DURATION_MILLIS = 420
    const val CONTENT_FADE_DURATION_MILLIS = 180
    const val CONTENT_FADE_DELAY_MILLIS = 120
    const val RETURN_SETTLE_MILLIS = 280
    const val CARD_CORNER_RADIUS_DP = 18
    const val SHARED_COVER_CORNER_RADIUS_DP = CARD_CORNER_RADIUS_DP
}

internal const val SONG_SEARCH_DISPLAY_MODE_LIST = "LIST"

internal fun songCardContainerKey(songIdentifier: String): String = "song-card-container/$songIdentifier"

internal fun songCoverKey(songIdentifier: String): String = "song-cover/$songIdentifier"

internal fun songTitleKey(songIdentifier: String): String = "song-title/$songIdentifier"

internal fun songArtistKey(songIdentifier: String): String = "song-artist/$songIdentifier"
