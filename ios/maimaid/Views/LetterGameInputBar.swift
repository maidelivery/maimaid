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
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Label {
                    Text(turnLabel)
                } icon: {
                    Image(systemName: isConnected ? "timer" : "wifi.slash")
                }
                .font(.caption.bold())
                .foregroundStyle(isCurrentTurn ? Color.accentColor : Color.secondary)

                TextField(
                    isCharacter ? "letterGame.characterPlaceholder" : "letterGame.inputPlaceholder",
                    text: $input
                )
                .textFieldStyle(.plain)
                .submitLabel(.send)
                .lineLimit(1)
                .disabled(!isCurrentTurn || !isConnected)
                .onSubmit {
                    if canSubmit { onSubmit() }
                }
            }

            Button(
                isCharacter ? "letterGame.open" : "letterGame.guess",
                systemImage: "arrow.up",
                action: onSubmit
            )
            .labelStyle(.iconOnly)
            .buttonStyle(.glassProminent)
            .buttonBorderShape(.circle)
            .controlSize(.large)
            .frame(minWidth: 44, minHeight: 44)
            .disabled(!canSubmit)
        }
        .padding(.leading, 16)
        .padding(.trailing, 8)
        .padding(.vertical, 8)
        .glassEffect(.regular, in: .rect(cornerRadius: 28))
        .padding(.horizontal)
        .padding(.vertical, 8)
    }

    private var turnLabel: LocalizedStringKey {
        isCurrentTurn
            ? "letterGame.yourTurn \(remainingSeconds)"
            : "letterGame.waitingTurn \(remainingSeconds)"
    }
}
