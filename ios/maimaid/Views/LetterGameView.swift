import SwiftUI

struct LetterGameView: View {
    @State private var service = LetterGameService()
    @State private var roomCode = ""

    var body: some View {
        Group {
            if !BackendSessionManager.shared.isAuthenticated {
                ContentUnavailableView("letterGame.login.title", systemImage: "person.crop.circle.badge.exclamationmark", description: Text("letterGame.login.message"))
            } else {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        Text("letterGame.title").font(.title2.bold())
                        HStack {
                            TextField("letterGame.roomCode", text: $roomCode)
                                .textInputAutocapitalization(.characters)
                                .textFieldStyle(.roundedBorder)
                            Button("letterGame.join") { Task { await service.join(code: roomCode) } }
                                .buttonStyle(.borderedProminent)
                        }
                        Button("letterGame.createPrivate") { Task { await service.createPrivateRoom() } }
                            .buttonStyle(.bordered)
                        if let room = service.room {
                            VStack(alignment: .leading, spacing: 8) {
                                Text("letterGame.roomCodeLabel \(room.code)").bold()
                                Text("letterGame.memberCount \(room.memberCount)")
                                if service.match == nil {
                                    Button("letterGame.start") { Task { await service.start() } }
                                        .buttonStyle(.borderedProminent)
                                }
                            }
                        }
                        if let match = service.match {
                            Text("letterGame.turn \(match.turnUserId ?? "-")").font(.headline)
                            ForEach(match.songs) { song in
                                LetterGameSongInput(
                                    song: song,
                                    canAct: match.turnUserId == BackendSessionManager.shared.currentUser?.id && song.status == "active",
                                    onAction: { kind, slotId, value in
                                        Task { await service.sendAction(payload: [
                                            "kind": kind,
                                            "slotId": slotId,
                                            kind == "open_character" ? "character" : "guess": value,
                                        ]) }
                                    },
                                )
                            }
                        }
                        if let error = service.errorMessage { Text(error).foregroundStyle(.red) }
                    }
                    .padding()
                }
            }
        }
        .navigationTitle("letterGame.title")
    }
}

private struct LetterGameSongInput: View {
    let song: LetterGameMatchSong
    let canAct: Bool
    let onAction: (String, String, String) -> Void
    @State private var character = ""
    @State private var guess = ""

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(song.title).font(.title3.monospaced())
            Text("letterGame.remaining \(song.remainingCharacterCount)").foregroundStyle(.secondary)
            if canAct {
                HStack {
                    TextField("letterGame.character", text: $character)
                        .textFieldStyle(.roundedBorder)
                    Button("letterGame.open") {
                        onAction("open_character", song.slotId, character)
                        character = ""
                    }
                    .buttonStyle(.borderedProminent)
                    .disabled(character.isEmpty)
                }
                HStack {
                    TextField("letterGame.guess", text: $guess)
                        .textFieldStyle(.roundedBorder)
                    Button("letterGame.submit") {
                        onAction("guess_song", song.slotId, guess)
                        guess = ""
                    }
                    .buttonStyle(.bordered)
                    .disabled(guess.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
        }
        .padding()
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.thinMaterial, in: .rect(cornerRadius: 8))
    }
}
