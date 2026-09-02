import SwiftUI

struct LetterGameResultsView: View {
    let service: LetterGameService
    let match: LetterGameMatchSnapshot
    let localAvatarData: Data?
    let localAvatarURL: String?

    private var rankedPlayers: [LetterGameMatchPlayer] {
        match.players.sorted { $0.score > $1.score }
    }

    private var guessedSongs: [LetterGameMatchSong] {
        match.songs.filter { $0.completionReason == "guessed" }
    }

    private var unguessedSongs: [LetterGameMatchSong] {
        match.songs.filter { $0.completionReason != "guessed" }
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading) {
                Label("letterGame.results", systemImage: "trophy.fill")
                    .font(.title2.bold())
                    .foregroundStyle(.tint)

                ForEach(rankedPlayers.enumerated(), id: \.element.id) { index, player in
                    HStack {
                        Text(index + 1, format: .number)
                            .font(.headline)
                            .frame(minWidth: 24)
                        LetterGamePlayerAvatar(
                            userId: player.userId,
                            currentUserId: service.currentUserId,
                            name: player.name,
                            avatarURL: player.avatarUrl,
                            localAvatarData: localAvatarData,
                            localAvatarURL: localAvatarURL
                        )
                        Text(player.name)
                            .lineLimit(1)
                        Spacer()
                        Text("letterGame.points \(player.score)")
                            .bold()
                    }
                    .padding()
                    .background(
                        player.userId == service.currentUserId
                            ? Color.accentColor.opacity(0.12)
                            : Color.secondary.opacity(0.08),
                        in: .rect(cornerRadius: 8)
                    )
                }

                if !guessedSongs.isEmpty {
                    Label("letterGame.guessedSongs", systemImage: "checkmark.circle")
                        .font(.headline)
                        .padding(.top)
                    ForEach(guessedSongs) { LetterGameSongCard(song: $0) }
                }

                if !unguessedSongs.isEmpty {
                    Label("letterGame.unguessedSongs", systemImage: "xmark.circle")
                        .font(.headline)
                        .padding(.top)
                    ForEach(unguessedSongs) { LetterGameSongCard(song: $0) }
                }

                Button("letterGame.reopen", systemImage: "arrow.clockwise") {
                    Task { await service.reopenRoom() }
                }
                .buttonStyle(.borderedProminent)
                .frame(maxWidth: .infinity)
                .padding(.top)
            }
            .padding()
        }
    }
}
