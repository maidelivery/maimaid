import SwiftUI

struct LetterGameTurnStrip: View {
    let players: [LetterGameMatchPlayer]
    let turnUserId: String?
    let currentUserId: String?
    let localAvatarData: Data?
    let localAvatarURL: String?

    private var orderedPlayers: [LetterGameMatchPlayer] {
        let sorted = players.sorted { $0.turnOrder < $1.turnOrder }
        guard let currentIndex = sorted.firstIndex(where: { $0.userId == turnUserId }) else { return sorted }
        return Array(sorted[currentIndex...]) + sorted[..<currentIndex]
    }

    var body: some View {
        VStack(alignment: .leading) {
            Label("letterGame.currentPlayer \(activePlayerName)", systemImage: "person.fill")
                .font(.headline)

            ScrollView(.horizontal) {
                HStack {
                    ForEach(orderedPlayers) { player in
                        VStack {
                            LetterGamePlayerAvatar(
                                userId: player.userId,
                                currentUserId: currentUserId,
                                name: player.name,
                                avatarURL: player.avatarUrl,
                                localAvatarData: localAvatarData,
                                localAvatarURL: localAvatarURL
                            )
                            LetterGamePointsText(points: player.score)
                                .font(.caption.bold())
                                .foregroundStyle(player.userId == turnUserId ? Color.accentColor : Color.secondary)
                        }
                        .padding(8)
                        .background(
                            player.userId == turnUserId ? Color.accentColor.opacity(0.12) : Color.clear,
                            in: .rect(cornerRadius: 8)
                        )
                    }
                }
            }
            .scrollIndicators(.hidden)
        }
    }

    private var activePlayerName: String {
        players.first(where: { $0.userId == turnUserId })?.name ?? String(localized: "letterGame.player")
    }
}
