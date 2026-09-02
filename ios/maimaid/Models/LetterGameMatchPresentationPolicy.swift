import Foundation

nonisolated enum LetterGameMatchPresentationPolicy {
    static func shouldAccept(status: String, matchId: String, trackedMatchId: String?) -> Bool {
        status == "active" || matchId == trackedMatchId
    }

    static func visiblePlayers(
        _ players: [LetterGameMatchPlayer],
        roomMembers: [LetterGameRoomMember]
    ) -> [LetterGameMatchPlayer] {
        let acceptedUserIds = Set(
            roomMembers.lazy
                .filter { $0.status == "accepted" }
                .map(\.userId)
        )
        return players.filter { acceptedUserIds.contains($0.userId) }
    }
}
