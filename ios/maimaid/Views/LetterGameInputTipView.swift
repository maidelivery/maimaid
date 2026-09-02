import SwiftUI

struct LetterGameInputTipView: View {
    let onDismiss: () -> Void

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: "info.circle.fill")
                .foregroundStyle(.tint)
                .accessibilityHidden(true)

            Text("letterGame.inputTip")
                .font(.subheadline)
                .foregroundStyle(.secondary)
                .frame(maxWidth: .infinity, alignment: .leading)

            Button("letterGame.dismissTip", systemImage: "xmark", action: onDismiss)
                .labelStyle(.iconOnly)
                .foregroundStyle(.secondary)
                .frame(minWidth: 44, minHeight: 44)
                .contentShape(.circle)
        }
        .padding(14)
        .background(.regularMaterial, in: .rect(cornerRadius: 16))
    }
}
