import SwiftUI

struct LetterGameResultsView: View {
    let service: LetterGameService
    let match: LetterGameMatchSnapshot
    let localAvatarData: Data?
    let localAvatarURL: String?

    @State private var isConfirmingLeave = false

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
                        LetterGamePointsText(points: player.score)
                            .bold()
                    }
                    .padding()
                    .background(
                        player.userId == service.currentUserId
                            ? Color.accentColor.opacity(0.12)
                            : Color.secondary.opacity(0.08),
                        in: .rect(cornerRadius: 16)
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

                HStack {
                    Button(action: reopenRoom) {
                        Label("letterGame.reopen", systemImage: "arrow.clockwise")
                            .bold()
                            .foregroundStyle(.white)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(Color.accentColor, in: .rect(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)

                    Button(role: .destructive) {
                        isConfirmingLeave = true
                    } label: {
                        Label("letterGame.leave", systemImage: "rectangle.portrait.and.arrow.right")
                            .bold()
                            .foregroundStyle(.red)
                            .frame(maxWidth: .infinity, minHeight: 48)
                            .background(Color.red.opacity(0.12), in: .rect(cornerRadius: 16))
                    }
                    .buttonStyle(.plain)
                    .confirmationDialog(
                        "letterGame.leaveConfirm.title",
                        isPresented: $isConfirmingLeave,
                        titleVisibility: .visible
                    ) {
                        Button("letterGame.leave", role: .destructive, action: leaveRoom)
                        Button("letterGame.cancel", role: .cancel) {}
                    } message: {
                        Text("letterGame.leaveConfirm.message")
                    }
                }
                .padding(.top)
            }
            .padding()
        }
    }

    private func reopenRoom() {
        Task { await service.reopenRoom() }
    }

    private func leaveRoom() {
        Task { await service.leaveRoom() }
    }
}
