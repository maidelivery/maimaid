import SwiftUI

struct LetterGameAlphabetProgress: View {
  let openedLetters: Set<Character>
  private let columns = Array(
    repeating: GridItem(.flexible(minimum: 16, maximum: 32), spacing: 4), count: 13)

  var body: some View {
    VStack(alignment: .leading) {
      Label("letterGame.alphabetProgress", systemImage: "character")
        .font(.headline)
      LazyVGrid(columns: columns, spacing: 4) {
        ForEach(Array("ABCDEFGHIJKLMNOPQRSTUVWXYZ"), id: \.self) { letter in
          Text(String(letter))
            .font(.caption.bold())
            .lineLimit(1)
            .minimumScaleFactor(0.6)
            .foregroundStyle(openedLetters.contains(letter) ? .white : .secondary)
            .frame(maxWidth: .infinity, minHeight: 24)
            .background(
              openedLetters.contains(letter) ? Color.accentColor : Color.secondary.opacity(0.1),
              in: .rect(cornerRadius: 6)
            )
            .accessibilityLabel(
              openedLetters.contains(letter)
                ? String(localized: "letterGame.letterOpened \(String(letter))")
                : String(localized: "letterGame.letterClosed \(String(letter))")
            )
        }
      }
    }
  }
}
