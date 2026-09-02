import SwiftUI

struct LetterGameInputBar: View {
    @Binding var input: String
    let isCurrentTurn: Bool
    let isConnected: Bool
    let remainingSeconds: Int
    let onSubmit: () -> Void

    private var isCharacter: Bool {
        input.trimmingCharacters(in: .whitespacesAndNewlines).count == 1
    }

    private var canSubmit: Bool {
        isCurrentTurn && isConnected && !input.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }

    var body: some View {
        VStack(alignment: .leading) {
            Label {
                Text(turnLabel)
            } icon: {
                Image(systemName: isConnected ? "timer" : "wifi.slash")
            }
            .font(.subheadline.bold())
            .foregroundStyle(isCurrentTurn ? Color.accentColor : Color.secondary)

            HStack {
                TextField(
                    isCharacter ? "letterGame.characterPlaceholder" : "letterGame.inputPlaceholder",
                    text: $input
                )
                .textFieldStyle(.roundedBorder)
                .disabled(!isCurrentTurn || !isConnected)
                .onSubmit {
                    if canSubmit { onSubmit() }
                }

                Button(
                    isCharacter ? "letterGame.open" : "letterGame.guess",
                    systemImage: "paperplane.fill",
                    action: onSubmit
                )
                .buttonStyle(.borderedProminent)
                .disabled(!canSubmit)
            }
        }
        .padding()
        .background(.bar)
    }

    private var turnLabel: LocalizedStringKey {
        isCurrentTurn
            ? "letterGame.yourTurn \(remainingSeconds)"
            : "letterGame.waitingTurn \(remainingSeconds)"
    }
}
