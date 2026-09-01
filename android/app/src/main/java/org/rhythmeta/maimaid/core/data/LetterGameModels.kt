package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class LetterGameRoomSettings(
    val turnDurationSeconds: Int = 30,
    val stalledRoundLimit: Int = 3,
    val songCountOverride: Int? = null,
    val publicHintCost: Int = 5,
    val privateHintCost: Int = 10,
    val selectionMode: String = "filtered_random",
    val selectionConfig: Map<String, JsonElement> = emptyMap(),
    val selectedCollections: List<LetterGameCollectionSummary> = emptyList(),
)

@Serializable
data class LetterGameCollectionSummary(
    val id: String,
    val name: String,
    val songCount: Int,
)

@Serializable
data class LetterGameRoomMember(
    val id: String? = null,
    val userId: String,
    val status: String,
    val seatOrder: Int,
    val displayName: String? = null,
    val avatarUrl: String? = null,
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
data class LetterGameMatchPlayer(
    val userId: String,
    val score: Int,
    val turnOrder: Int,
    val status: String,
    val scoringEligible: Boolean = true,
    val displayName: String? = null,
    val avatarUrl: String? = null,
)

@Serializable
data class LetterGameLogEntry(
    val id: String,
    val message: String,
    val actorUserId: String? = null,
    val actorName: String? = null,
    val actionType: String? = null,
    val character: String? = null,
    val newlyRevealedCount: Int? = null,
    val points: Int? = null,
    val correct: Boolean? = null,
    val blind: Boolean? = null,
    val balance: Int? = null,
    val hintType: String? = null,
    val hintVisibility: String? = null,
    val hintCost: Int? = null,
    val songNumber: Int? = null,
)

@Serializable
data class LetterGameFact(
    val type: String,
    val visibility: String,
    val value: JsonElement,
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
    val imageName: String? = null,
    val artist: String? = null,
    val version: String? = null,
    val chartTypes: List<String> = emptyList(),
    val hasRemaster: Boolean = false,
    val masterConstant: String? = null,
    val remasterConstant: String? = null,
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
    val roomCode: String? = null,
    val logs: List<LetterGameLogEntry> = emptyList(),
)

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
    val selectionConfig: Map<String, JsonElement> = emptyMap(),
)
