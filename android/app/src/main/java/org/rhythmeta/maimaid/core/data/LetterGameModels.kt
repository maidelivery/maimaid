package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable

@Serializable
data class LetterGameRoomSettings(
    val turnDurationSeconds: Int = 30,
    val stalledRoundLimit: Int = 3,
    val songCountOverride: Int? = null,
    val publicHintCost: Int = 5,
    val privateHintCost: Int = 10,
    val selectionMode: String = "filtered_random",
)

@Serializable
data class LetterGameRoomMember(
    val userId: String,
    val status: String,
    val seatOrder: Int,
)

@Serializable
data class LetterGameLatestMatch(
    val id: String,
    val sequence: Int,
    val status: String,
    val revision: Int,
)

@Serializable
data class LetterGameRoom(
    val id: String,
    val code: String,
    val visibility: String,
    val hostMode: String,
    val hostUserId: String,
    val status: String,
    val settings: LetterGameRoomSettings = LetterGameRoomSettings(),
    val memberCount: Int = 0,
    val members: List<LetterGameRoomMember> = emptyList(),
    val latestMatch: LetterGameLatestMatch? = null,
)

@Serializable
data class LetterGameRoomsResponse(val rooms: List<LetterGameRoom> = emptyList())

@Serializable
data class LetterGameRoomResponse(val room: LetterGameRoom)

@Serializable
data class LetterGameMatchPlayer(
    val userId: String,
    val score: Int,
    val turnOrder: Int,
    val status: String,
    val scoringEligible: Boolean = true,
)

@Serializable
data class LetterGameFact(
    val type: String,
    val visibility: String,
    val value: kotlinx.serialization.json.JsonElement,
)

@Serializable
data class LetterGameMatchSong(
    val slotId: String,
    val title: String,
    val remainingCharacterCount: Int,
    val status: String,
    val completionReason: String? = null,
    val completedByUserId: String? = null,
    val facts: List<LetterGameFact> = emptyList(),
)

@Serializable
data class LetterGameMatchSnapshot(
    val matchId: String,
    val status: String,
    val revision: Int,
    val turnUserId: String? = null,
    val turnDeadline: String? = null,
    val noProgressRounds: Int = 0,
    val players: List<LetterGameMatchPlayer> = emptyList(),
    val songs: List<LetterGameMatchSong> = emptyList(),
)

@Serializable
data class LetterGameMatchResponse(val match: LetterGameMatchSnapshot)

@Serializable
data class LetterGameCreateRequest(
    val visibility: String,
    val hostMode: String = "fixed",
    val turnDurationSeconds: Int = 30,
    val stalledRoundLimit: Int = 3,
    val songCount: Int? = null,
    val publicHintCost: Int = 5,
    val privateHintCost: Int = 10,
    val selectionMode: String = "filtered_random",
    val selectionConfig: Map<String, String> = emptyMap(),
)

