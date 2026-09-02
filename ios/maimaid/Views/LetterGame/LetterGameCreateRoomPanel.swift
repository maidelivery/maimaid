import SwiftUI

struct LetterGameCreateRoomPanel: View {
  @Binding var visibility: String
  let isLoading: Bool
  let onCreate: () -> Void

  var body: some View {
    VStack(spacing: 12) {
      HStack(spacing: 10) {
        Button {
          visibility = "public"
        } label: {
          HStack {
            Label("letterGame.public", systemImage: "globe")
            Spacer(minLength: 0)
            if visibility == "public" {
              Image(systemName: "checkmark.circle.fill")
                .accessibilityHidden(true)
            }
          }
          .foregroundStyle(visibility == "public" ? Color.white : Color.primary)
          .frame(maxWidth: .infinity, minHeight: 44)
          .padding(.horizontal, 12)
          .background(
            visibility == "public" ? Color.accentColor : Color.secondary.opacity(0.1),
            in: .rect(cornerRadius: 16)
          )
        }
        .buttonStyle(.plain)

        Button {
          visibility = "private"
        } label: {
          HStack {
            Label("letterGame.private", systemImage: "lock.fill")
            Spacer(minLength: 0)
            if visibility == "private" {
              Image(systemName: "checkmark.circle.fill")
                .accessibilityHidden(true)
            }
          }
          .foregroundStyle(visibility == "private" ? Color.white : Color.primary)
          .frame(maxWidth: .infinity, minHeight: 44)
          .padding(.horizontal, 12)
          .background(
            visibility == "private" ? Color.accentColor : Color.secondary.opacity(0.1),
            in: .rect(cornerRadius: 16)
          )
        }
        .buttonStyle(.plain)
      }

      Button(action: onCreate) {
        HStack {
          if isLoading {
            ProgressView()
              .tint(.white)
          } else {
            Image(systemName: visibility == "public" ? "globe" : "lock.fill")
          }
          Text(visibility == "public" ? "letterGame.createPublic" : "letterGame.createPrivate")
            .bold()
        }
        .foregroundStyle(.white)
        .frame(maxWidth: .infinity, minHeight: 48)
        .background(Color.accentColor, in: .rect(cornerRadius: 16))
      }
      .buttonStyle(.plain)
      .disabled(isLoading)
      .opacity(isLoading ? 0.7 : 1)
    }
  }
}
