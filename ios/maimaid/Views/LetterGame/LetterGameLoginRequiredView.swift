import SwiftUI

struct LetterGameLoginRequiredView: View {
    var body: some View {
        ContentUnavailableView {
            Label("letterGame.login.title", systemImage: "person.crop.circle.badge.exclamationmark")
        } description: {
            Text("letterGame.login.message")
        } actions: {
            NavigationLink {
                BackendAuthView()
            } label: {
                Label("letterGame.login.action", systemImage: "person.badge.key")
            }
            .buttonStyle(.borderedProminent)
        }
    }
}
