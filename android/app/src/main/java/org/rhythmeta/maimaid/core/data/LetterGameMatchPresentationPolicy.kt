package org.rhythmeta.maimaid.core.data

object LetterGameMatchPresentationPolicy {
    fun shouldAccept(status: String, matchId: String, trackedMatchId: String?): Boolean =
        status == "active" || matchId == trackedMatchId

    fun visiblePlayers(
        players: List<LetterGameMatchPlayer>,
        roomMembers: List<LetterGameRoomMember>,
    ): List<LetterGameMatchPlayer> {
        val acceptedUserIds = roomMembers
            .asSequence()
            .filter { it.status == "accepted" }
            .map(LetterGameRoomMember::userId)
            .toSet()
        return players.filter { it.userId in acceptedUserIds }
    }
}
