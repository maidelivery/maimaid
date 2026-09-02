import SwiftUI

struct LetterGameSettingsSection<Content: View>: View {
  let title: LocalizedStringKey
  let content: Content

  init(title: LocalizedStringKey, @ViewBuilder content: () -> Content) {
    self.title = title
    self.content = content()
  }

  var body: some View {
    VStack(alignment: .leading, spacing: 12) {
      Text(title)
        .font(.caption)
        .bold()
        .foregroundStyle(.secondary)
        .padding(.leading, 4)

      content
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(.ultraThinMaterial, in: .rect(cornerRadius: 16))
    }
  }
}
