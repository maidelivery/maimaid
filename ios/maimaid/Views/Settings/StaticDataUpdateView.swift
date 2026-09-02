import SwiftData
import SwiftUI

private struct StaticManifestSummary: Decodable {
    let version: String
    let md5: String
    let createdAt: Date?
}

private enum StaticUpdateState {
    case idle
    case checking
    case upToDate(manifest: StaticManifestSummary)
    case available(manifest: StaticManifestSummary)
    case backendUnconfigured
    case failed(message: String)
}

struct StaticDataUpdateView: View {
    @Environment(\.modelContext) private var modelContext
    @State private var fetcher = MaimaiDataFetcher.shared

    @State private var updateState: StaticUpdateState = .idle
    @State private var syncErrorMessage: String?

    private var isSyncing: Bool { fetcher.isSyncing }

    var body: some View {
        List {
            Section {
                VStack(spacing: 10) {
                    Image(systemName: statusIconName)
                        .font(.system(size: 44, weight: .semibold))
                        .foregroundStyle(statusIconColor)

                    Text(statusTitle)
                        .font(.title3.bold())
                        .multilineTextAlignment(.center)

                    Text(statusDescription)
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                        .multilineTextAlignment(.center)
                        .frame(maxWidth: .infinity)

                    if let versionText {
                        Text(versionText)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 8)
            }

            Section("update.actions") {
                if isSyncing {
                    VStack(alignment: .leading, spacing: 10) {
                        ProgressView(value: fetcher.progress, total: 1)
                            .tint(.accentColor)

                        Text(
                            fetcher.statusMessage.isEmpty
                                ? String(
                                    localized: LocalizedStringResource(stringLiteral: fetcher.currentStage.rawValue))
                                : fetcher.statusMessage
                        )
                            .font(.footnote)
                            .foregroundStyle(.secondary)
                    }
                    .padding(.vertical, 4)
                } else {
                    actionRow(
                        title: actionButtonTitle,
                        icon: primaryActionIcon,
                        tint: primaryActionTint,
                        disabled: isSyncing
                    ) {
                        Task {
                            await runFullUpdate()
                        }
                    }

                    actionRow(
                        title: String(localized: "update.action.checkAgain"),
                        icon: "magnifyingglass",
                        tint: .green,
                        disabled: isSyncing
                    ) {
                        Task {
                            await checkForUpdate()
                        }
                    }
                }

                if let syncErrorMessage, !syncErrorMessage.isEmpty {
                    Text(syncErrorMessage)
                        .font(.footnote)
                        .foregroundStyle(.red)
                }
            }

        }
        .navigationTitle("update.title")
        .navigationBarTitleDisplayMode(.inline)
        .interactiveDismissDisabled(isSyncing)
        .navigationBarBackButtonHidden(isSyncing)
        .task {
            await checkForUpdate()
        }
        .refreshable {
            await checkForUpdate()
        }
    }

    @MainActor
    private func checkForUpdate() async {
        syncErrorMessage = nil
        updateState = .checking

        guard let manifestURL = BackendConfig.staticAssetsEndpoint("manifest.json") else {
            updateState = .backendUnconfigured
            return
        }

        do {
            let (data, response) = try await URLSession.shared.data(from: manifestURL)
            guard let httpResponse = response as? HTTPURLResponse,
                  (200...299).contains(httpResponse.statusCode) else {
                throw BackendAPIError.badResponse
            }
            let manifest = try BackendAPIClient.decoder.decode(StaticManifestSummary.self, from: data)

            if UserDefaults.app.staticBundleMd5 == manifest.md5 {
                updateState = .upToDate(manifest: manifest)
            } else {
                updateState = .available(manifest: manifest)
            }
        } catch {
            updateState = .failed(message: error.localizedDescription)
        }
    }

    @MainActor
    private func runFullUpdate() async {
        syncErrorMessage = nil
        do {
            let forceBundleApply: Bool = {
                if case .upToDate = updateState {
                    return true
                }
                return false
            }()
            try await fetcher.fetchSongs(modelContext: modelContext, forceBundleApply: forceBundleApply)
            await checkForUpdate()
        } catch {
            syncErrorMessage = error.localizedDescription
            updateState = .failed(message: error.localizedDescription)
        }
    }

    private var statusIconName: String {
        switch updateState {
        case .idle, .checking:
            "arrow.triangle.2.circlepath.circle.fill"
        case .upToDate:
            "checkmark.circle.fill"
        case .available:
            "arrow.down.circle.fill"
        case .backendUnconfigured:
            "exclamationmark.triangle.fill"
        case .failed:
            "xmark.octagon.fill"
        }
    }

    private var statusIconColor: Color {
        switch updateState {
        case .idle, .checking:
            .accentColor
        case .upToDate:
            .green
        case .available:
            .blue
        case .backendUnconfigured:
            .orange
        case .failed:
            .red
        }
    }

    private var statusTitle: String {
        switch updateState {
        case .idle:
            String(localized: "update.status.ready")
        case .checking:
            String(localized: "update.status.checking")
        case .upToDate:
            String(localized: "update.status.upToDate")
        case .available:
            String(localized: "update.status.available")
        case .backendUnconfigured:
            String(localized: "update.status.unconfigured")
        case let .failed(message):
            String(localized: "update.status.failed \(message)")
        }
    }

    private var statusDescription: String {
        switch updateState {
        case .idle:
            String(localized: "update.description.ready")
        case .checking:
            String(localized: "update.description.checking")
        case .upToDate:
            String(localized: "update.description.upToDate")
        case .available:
            String(localized: "update.description.available")
        case .backendUnconfigured:
            String(localized: "update.description.unconfigured")
        case .failed:
            String(localized: "update.description.failed")
        }
    }

    private var versionText: String? {
        let manifest: StaticManifestSummary? = {
            switch updateState {
            case let .upToDate(manifest), let .available(manifest):
                return manifest
            default:
                return nil
            }
        }()

        guard let manifest else { return nil }
        let createdText = manifest.createdAt?.formatted(date: .numeric, time: .shortened)
            ?? String(localized: "update.version.unknownTime")
        return String(localized: "update.version \(manifest.version) \(createdText)")
    }

    private var actionButtonTitle: String {
        switch updateState {
        case .available:
            String(localized: "update.action.download")
        case .upToDate:
            String(localized: "update.action.reinstall")
        default:
            String(localized: "update.action.updateNow")
        }
    }

    private var primaryActionIcon: String {
        switch updateState {
        case .available:
            "arrow.down.circle"
        case .upToDate:
            "arrow.clockwise.circle"
        default:
            "arrow.triangle.2.circlepath"
        }
    }

    private var primaryActionTint: Color {
        switch updateState {
        case .available:
            .blue
        case .upToDate:
            .orange
        default:
            .blue
        }
    }

    private func actionRow(
        title: String,
        icon: String,
        tint: Color,
        disabled: Bool,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack {
                Image(systemName: icon)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 28, height: 28)
                    .background(tint, in: .rect(cornerRadius: 8))

                Text(title)
                    .foregroundStyle(.primary)

                Spacer()

                Image(systemName: "arrow.up.forward.app")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(tint)
            }
        }
        .buttonStyle(.plain)
        .disabled(disabled)
        .opacity(disabled ? 0.6 : 1)
    }
}

#Preview {
    StaticDataUpdateView()
}
