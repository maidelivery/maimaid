import SwiftUI

struct LetterGameHintView: View {
    @Environment(\.dismiss) private var dismiss

    let service: LetterGameService
    let room: LetterGameRoom
    let song: LetterGameMatchSong
    let player: LetterGameMatchPlayer?

    @State private var hintType = "version"
    @State private var visibility = "public"

    private var cost: Int {
        visibility == "public" ? room.settings.publicHintCost : room.settings.privateHintCost
    }

    private var isKnown: Bool {
        song.facts.contains { $0.type == hintType }
    }

    private var canPurchase: Bool {
        service.isCurrentTurn
            && service.isConnected
            && player?.scoringEligible == true
            && (player?.score ?? 0) >= cost
            && !isKnown
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("letterGame.hintType") {
                    Picker("letterGame.hintType", selection: $hintType) {
                        Text("letterGame.hintVersion").tag("version")
                        Text("letterGame.hintRemaster").tag("white_chart")
                        Text("letterGame.hintConstant").tag("constant")
                    }
                }
                Section("letterGame.hintVisibility") {
                    Picker("letterGame.hintVisibility", selection: $visibility) {
                        Text("letterGame.public").tag("public")
                        Text("letterGame.private").tag("private")
                    }
                    .pickerStyle(.segmented)
                }
                Section {
                    LabeledContent("letterGame.cost") {
                        LetterGamePointsText(points: cost)
                    }
                    LabeledContent("letterGame.balance") {
                        LetterGamePointsText(points: player?.score ?? 0)
                    }
                    if isKnown {
                        Label("letterGame.hintKnown", systemImage: "info.circle")
                            .foregroundStyle(.secondary)
                    }
                }
                Section {
                    Button("letterGame.purchase", systemImage: "lightbulb.fill", action: purchase)
                        .disabled(!canPurchase)
                }
            }
            .navigationTitle("letterGame.buyHint")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("letterGame.cancel", action: dismiss.callAsFunction)
                }
            }
        }
        .presentationDetents([.medium])
    }

    private func purchase() {
        Task {
            await service.buyHint(song: song, type: hintType, visibility: visibility)
            dismiss()
        }
    }
}
