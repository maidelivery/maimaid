import SwiftUI

struct ScannerModelDownloadView: View {
    let state: ScannerModelDownloadState
    let download: () -> Void
    let retry: () -> Void
    let cancel: () -> Void

    var body: some View {
        if shouldShow {
            VStack(spacing: 12) {
                switch state {
                case .checking:
                    ProgressView()
                        .tint(.white)
                    Text("scanner.models.checking")
                case let .downloadRequired(totalBytes):
                    Image(systemName: "arrow.down.circle")
                        .font(.title)
                    Text("scanner.models.required")
                        .bold()
                    Text(String(localized: "scanner.models.size \(formatted(totalBytes))"))
                        .foregroundStyle(.secondary)
                    Button("scanner.models.download.action", systemImage: "arrow.down.circle", action: download)
                        .buttonStyle(.borderedProminent)
                case let .downloading(progress, _):
                    Text("scanner.models.downloading")
                        .bold()
                    ProgressView(value: progress.fraction)
                    Text("\(formatted(progress.downloadedBytes)) / \(formatted(progress.totalBytes))")
                        .foregroundStyle(.secondary)
                    Button("scanner.models.cancel", systemImage: "xmark", action: cancel)
                        .buttonStyle(.bordered)
                case let .failed(message, _):
                    Image(systemName: "exclamationmark.triangle")
                        .font(.title)
                    Text("scanner.models.failed")
                        .bold()
                    Text(message)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                    Button("scanner.models.retry", systemImage: "arrow.clockwise", action: retry)
                        .buttonStyle(.borderedProminent)
                case .ready, .updateAvailable:
                    EmptyView()
                }
            }
            .foregroundStyle(.white)
            .padding()
            .frame(maxWidth: 320)
            .background(.ultraThinMaterial, in: .rect(cornerRadius: 8))
            .padding()
        }
    }

    private var shouldShow: Bool {
        blocksScanner || state.isDownloading || state.isFailure
    }

    private var blocksScanner: Bool {
        switch state {
        case let .checking(cachedModelsAvailable): !cachedModelsAvailable
        case .downloadRequired: true
        case let .downloading(_, isUpdate): !isUpdate
        case let .failed(_, cachedModelsAvailable): !cachedModelsAvailable
        case .ready, .updateAvailable: false
        }
    }

    private func formatted(_ bytes: Int64) -> String {
        bytes.formatted(.byteCount(style: .file))
    }
}

private extension ScannerModelDownloadState {
    var isDownloading: Bool {
        if case .downloading = self { return true }
        return false
    }

    var isFailure: Bool {
        if case .failed = self { return true }
        return false
    }
}
