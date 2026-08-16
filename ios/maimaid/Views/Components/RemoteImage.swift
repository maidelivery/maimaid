import SwiftUI

@MainActor
struct RemoteImage<Placeholder: View, Failure: View>: View {
    let url: URL?
    let contentMode: ContentMode
    @ViewBuilder let placeholder: Placeholder
    @ViewBuilder let failure: Failure

    @State private var image: Image?
    @State private var didFail = false

    init(
        url: URL?,
        contentMode: ContentMode = .fill,
        @ViewBuilder placeholder: () -> Placeholder,
        @ViewBuilder failure: () -> Failure
    ) {
        self.url = url
        self.contentMode = contentMode
        self.placeholder = placeholder()
        self.failure = failure()
    }

    var body: some View {
        Group {
            if let image {
                image
                    .resizable()
                    .aspectRatio(contentMode: contentMode)
            } else if didFail {
                failure
            } else {
                placeholder
            }
        }
        .task(id: url) {
            image = nil
            didFail = false
            guard let url else { return }

            do {
                let fetchedImage = try await ImageDownloader.shared.fetchImage(from: url)
                try Task.checkCancellation()
                image = Image(uiImage: fetchedImage)
            } catch is CancellationError {
                return
            } catch {
                didFail = true
            }
        }
    }
}

extension RemoteImage where Failure == Placeholder {
    init(
        url: URL?,
        contentMode: ContentMode = .fill,
        @ViewBuilder placeholder: () -> Placeholder
    ) {
        let placeholder = placeholder()
        self.init(
            url: url,
            contentMode: contentMode,
            placeholder: { placeholder },
            failure: { placeholder }
        )
    }
}
