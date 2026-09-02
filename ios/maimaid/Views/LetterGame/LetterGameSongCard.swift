import SwiftUI

struct LetterGameSongCard: View {
  let song: LetterGameMatchSong
  var hintAction: (() -> Void)?

  @Environment(\.colorScheme) private var colorScheme
  @State private var isLongPressing = false

  private var accentColor: Color {
    ThemeUtils.colorForDifficulty(song.hasRemaster ? "remaster" : "master", nil, colorScheme)
  }

  private var normalizedChartTypes: [String] {
    let types = Set(
      song.chartTypes.map { type in
        type.lowercased() == "standard" ? "std" : type.lowercased()
      })
    if types.contains("std") && types.contains("dx") { return ["std", "dx"] }
    if types.contains("utage") { return ["utage"] }
    if types.contains("dx") { return ["dx"] }
    return ["std"]
  }

  private var formattedConstant: String? {
    guard let value = song.maxConstant, !value.isEmpty else { return nil }
    guard let number = Double(value) else { return value }
    return number.formatted(.number.precision(.fractionLength(1)))
  }

  var body: some View {
    let card = HStack(spacing: 0) {
      RoundedRectangle(cornerRadius: 2)
        .fill(accentColor)
        .frame(width: 4)
        .padding(.vertical, 8)

      HStack(spacing: 14) {
        Group {
          if let imageName = song.imageName, !imageName.isEmpty {
            SongJacketView(imageName: imageName, size: 52, cornerRadius: 12)
          } else {
            Image(systemName: "questionmark")
              .foregroundStyle(.secondary)
              .frame(width: 52, height: 52)
              .background(.quaternary, in: .rect(cornerRadius: 12))
          }
        }

        VStack(alignment: .leading, spacing: 3) {
          Text(song.title)
            .font(.subheadline)
            .bold()
            .lineLimit(2)
          if let artist = song.artist, !artist.isEmpty {
            Text(artist)
              .font(.footnote)
              .foregroundStyle(.secondary)
              .lineLimit(1)
          }
        }

        Spacer(minLength: 8)

        VStack(alignment: .trailing, spacing: 4) {
          if let version = song.version, !version.isEmpty {
            ChartTypeVersionBadge(
              text: ThemeUtils.versionAbbreviation(version),
              chartTypes: normalizedChartTypes
            )
          }
          if let formattedConstant {
            Text(formattedConstant)
              .font(.footnote)
              .bold()
              .foregroundStyle(accentColor)
          }
        }
      }
      .padding(.leading, 10)
      .padding(.trailing, 14)
    }
    .padding(.vertical, 12)
    .background(.ultraThinMaterial, in: .rect(cornerRadius: 14))
    .overlay {
      RoundedRectangle(cornerRadius: 14)
        .strokeBorder(accentColor.opacity(0.12), lineWidth: 1)
    }
    .contentShape(.rect)

    if let hintAction, song.status == "active" {
      card
        .overlay {
          RoundedRectangle(cornerRadius: 14)
            .fill(.white.opacity(isLongPressing ? 0.12 : 0))
            .allowsHitTesting(false)
        }
        .onLongPressGesture(
          minimumDuration: 0.2,
          maximumDistance: 2,
          perform: hintAction,
          onPressingChanged: { isLongPressing = $0 }
        )
        .accessibilityAction(named: Text("letterGame.buyHint"), hintAction)
    } else {
      card
    }
  }
}
