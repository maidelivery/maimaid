import SwiftUI

struct SongDetailConstantHistorySection: View {
    let changes: [ChartConstantHistoryEntry]
    @State private var isExpanded = false

    var body: some View {
        VStack(spacing: 0) {
            Button {
                withAnimation(.spring(response: 0.35, dampingFraction: 0.8)) {
                    isExpanded.toggle()
                }
            } label: {
                HStack {
                    Text("song.detail.constantHistory")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(.primary)
                    Spacer()
                    Image(systemName: "chevron.right")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.secondary.opacity(0.4))
                        .rotationEffect(.degrees(isExpanded ? 90 : 0))
                }
                .padding(.horizontal, 20)
                .padding(.bottom, isExpanded ? 8 : 0)
            }
            .buttonStyle(.plain)

            if isExpanded {
            VStack(spacing: 0) {
                ForEach(changes.indices, id: \.self) { index in
                    let change = changes[index]
                    HStack {
                        Text(ThemeUtils.versionAbbreviation(change.version))
                            .foregroundStyle(.secondary)
                        Spacer()
                        if let amount = change.change {
                            let changeColor = amount > 0 ? Color.red : Color.green
                            Text(
                                amount,
                                format: .number
                                    .sign(strategy: .always())
                                    .precision(.fractionLength(1))
                            )
                            .bold()
                            .foregroundStyle(changeColor)
                            Image(systemName: amount > 0 ? "arrow.up" : "arrow.down")
                                .foregroundStyle(changeColor)
                                .accessibilityHidden(true)
                        }
                        Text(change.constant, format: .number.precision(.fractionLength(1)))
                            .bold()
                            .foregroundStyle(.primary)
                    }
                    .font(.system(size: 11))
                    .padding(.horizontal, 20)
                    .padding(.vertical, 5)
                    .background(index.isMultiple(of: 2) ? Color.primary.opacity(0.02) : .clear)
                }
            }
            .transition(.opacity.combined(with: .move(edge: .top)))
            }
        }
    }
}
