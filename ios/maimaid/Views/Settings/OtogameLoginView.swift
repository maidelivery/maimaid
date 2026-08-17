import SwiftUI

struct OtogameLoginView: View {
    let viewModel: OtogameImportViewModel
    @State private var webViewIdentity = UUID()

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Label(
                    viewModel.hasSession
                        ? "import.otogame.session.ready"
                        : "import.otogame.session.required",
                    systemImage: viewModel.hasSession ? "checkmark.circle.fill" : "person.crop.circle.badge.exclamationmark"
                )
                .foregroundStyle(viewModel.hasSession ? .green : .secondary)
                Spacer()
                Button("import.otogame.login.reload", systemImage: "arrow.clockwise") {
                    webViewIdentity = UUID()
                }
                .labelStyle(.iconOnly)
            }
            .padding()

            Divider()

            OtogameWebView(onAuthorizationHeader: viewModel.captureAuthorizationHeader)
                .id(webViewIdentity)
        }
        .navigationTitle("import.otogame.login.title")
        .navigationBarTitleDisplayMode(.inline)
    }
}
