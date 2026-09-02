import SwiftUI

struct LetterGamePlayingView: View {
    let service: LetterGameService
    let room: LetterGameRoom
    let match: LetterGameMatchSnapshot
    let localAvatarData: Data?
    let localAvatarURL: String?

    @State private var input = ""
    @State private var remainingSeconds = 0
    @State private var hintTarget: LetterGameMatchSong?
    @State private var isShowingInputTip = true
    @State private var didEstablishLogBaseline = false
    @State private var shownLogIDs: Set<String> = []
    @State private var toastMessage: String?
    @State private var toastToken = UUID()

    private var currentPlayer: LetterGameMatchPlayer? {
        match.players.first { $0.userId == service.currentUserId }
    }

    private var canAct: Bool {
        service.isCurrentTurn && service.isConnected
    }

    private var openedLetters: Set<Character> {
        Set(match.logs.compactMap { log in
            guard log.actionType == "open_character",
                  let letter = log.character?.uppercased().first,
                  letter.isASCII && letter.isLetter else { return nil }
            return letter
        })
    }

    var body: some View {
        ScrollView {
            LazyVStack(alignment: .leading) {
                if isShowingInputTip {
                    LetterGameInputTipView {
                        withAnimation(.easeOut) {
                            isShowingInputTip = false
                        }
                    }
                    .transition(.opacity)
                }

                LetterGameTurnStrip(
                    players: match.players,
                    turnUserId: match.turnUserId,
                    currentUserId: service.currentUserId,
                    localAvatarData: localAvatarData,
                    localAvatarURL: localAvatarURL
                )

                if room.settings.selectionConfig["englishOnly"]?.boolValue == true {
                    LetterGameAlphabetProgress(openedLetters: openedLetters)
                }

                Label("letterGame.songs", systemImage: "music.note.list")
                    .font(.headline)
                    .padding(.top)

                ForEach(match.songs) { song in
                    LetterGameSongCard(song: song) {
                        hintTarget = song
                    }
                }

            }
            .padding()
        }
        .safeAreaInset(edge: .bottom) {
            LetterGameInputBar(
                input: $input,
                isCurrentTurn: service.isCurrentTurn,
                isConnected: service.isConnected,
                remainingSeconds: remainingSeconds,
                onSubmit: submit
            )
        }
        .task(id: match.turnDeadline) {
            await updateCountdown()
        }
        .task(id: match.matchId) {
            shownLogIDs = Set(match.logs.map(\.id))
            didEstablishLogBaseline = true
        }
        .onChange(of: match.logs) { _, logs in
            handleLogChanges(logs)
        }
        .overlay(alignment: .bottom) {
            if let toastMessage {
                Text(toastMessage)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .background(.black.opacity(0.8), in: .capsule)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .padding(.horizontal)
                    .padding(.bottom, 112)
                    .zIndex(100)
            }
        }
        .sheet(item: $hintTarget) { song in
            LetterGameHintView(
                service: service,
                room: room,
                song: song,
                player: currentPlayer
            )
        }
    }

    private func submit() {
        let value = input
        input = ""
        Task { await service.sendInput(value) }
    }

    private func handleLogChanges(_ logs: [LetterGameLogEntry]) {
        let currentIDs = Set(logs.map(\.id))
        guard didEstablishLogBaseline else {
            shownLogIDs = currentIDs
            didEstablishLogBaseline = true
            return
        }

        let newLogs = logs.filter { !shownLogIDs.contains($0.id) }
        shownLogIDs.formUnion(currentIDs)
        guard let latestLog = newLogs.last else { return }
        showToast(LetterGameLogMessage.localizedString(for: latestLog))
    }

    private func showToast(_ message: String) {
        let token = UUID()
        toastToken = token
        withAnimation(.spring(response: 0.35, dampingFraction: 0.85)) {
            toastMessage = message
        }

        Task { @MainActor in
            try? await Task.sleep(for: .seconds(2.5))
            guard toastToken == token else { return }
            withAnimation(.easeOut) {
                toastMessage = nil
            }
        }
    }

    private func updateCountdown() async {
        guard let deadline = match.turnDeadline,
              let date = LetterGameDeadlineParser.date(from: deadline) else {
            remainingSeconds = 0
            return
        }
        while !Task.isCancelled {
            remainingSeconds = max(0, Int(date.timeIntervalSinceNow.rounded(.up)))
            if remainingSeconds == 0 { break }
            do {
                try await Task.sleep(for: .seconds(1))
            } catch {
                break
            }
        }
    }
}
