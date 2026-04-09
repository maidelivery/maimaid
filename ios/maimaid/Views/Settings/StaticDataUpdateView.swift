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
    @Query private var configs: [SyncConfig]
    @State private var fetcher = MaimaiDataFetcher.shared

    @State private var updateState: StaticUpdateState = .idle
    @State private var syncErrorMessage: String?
    @State private var lastCheckedAt: Date?

    private var config: SyncConfig? { configs.first }
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

            Section("更新操作") {
                if isSyncing {
                    VStack(alignment: .leading, spacing: 10) {
                        ProgressView(value: fetcher.progress, total: 1)
                            .tint(.accentColor)

                        Text(
                            fetcher.statusMessage.isEmpty
                                ? String(localized: LocalizedStringResource(stringLiteral: fetcher.currentStage.rawValue))
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
                        title: "重新检查更新",
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

            Section("自动更新") {
                let currentInterval = config?.backgroundSyncInterval ?? 0
                Picker("检查频率", selection: Binding(
                    get: { currentInterval },
                    set: { newValue in
                        if let config {
                            config.backgroundSyncInterval = newValue
                        } else {
                            let newConfig = SyncConfig()
                            newConfig.backgroundSyncInterval = newValue
                            modelContext.insert(newConfig)
                        }
                        try? modelContext.save()
                        Task {
                            await StaticDataAutoUpdate.scheduleNextRefresh(container: modelContext.container)
                        }
                    }
                )) {
                    Text("关闭").tag(0)
                    Text("每天").tag(24)
                    Text("每 7 天").tag(168)
                    Text("每 14 天").tag(336)
                    Text("每 30 天").tag(720)
                }
                .pickerStyle(.menu)

                if let lastUpdate = config?.lastStaticDataUpdateDate {
                    LabeledContent("上次更新", value: lastUpdate.formatted(date: .numeric, time: .shortened))
                } else {
                    LabeledContent("上次更新", value: "从未")
                }

                if let lastCheckedAt {
                    LabeledContent("上次检查", value: lastCheckedAt.formatted(date: .numeric, time: .shortened))
                }
            }
        }
        .navigationTitle("静态数据更新")
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
        defer { lastCheckedAt = .now }

        guard BackendSessionManager.shared.isConfigured else {
            updateState = .backendUnconfigured
            return
        }

        do {
            let manifest: StaticManifestSummary = try await BackendAPIClient.request(
                path: "v1/static/manifest",
                method: "GET",
                authentication: .none
            )

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
            await StaticDataAutoUpdate.scheduleNextRefresh(container: modelContext.container)
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
            "准备检查更新"
        case .checking:
            "正在检查更新…"
        case .upToDate:
            "已是最新静态数据"
        case .available:
            "发现可用更新"
        case .backendUnconfigured:
            "未配置后端"
        case let .failed(message):
            "检查失败：\(message)"
        }
    }

    private var statusDescription: String {
        switch updateState {
        case .idle:
            "进入页面后会自动检查静态数据更新。"
        case .checking:
            "正在从后端获取最新 manifest。"
        case .upToDate:
            "当前本地 md5 与服务端一致。"
        case .available:
            "点击下方按钮下载并应用完整更新。"
        case .backendUnconfigured:
            "请先在设置中配置后端地址。"
        case .failed:
            "请检查网络或后端状态后重试。"
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
        let createdText = manifest.createdAt?.formatted(date: .numeric, time: .shortened) ?? "未知时间"
        return "版本：\(manifest.version) · 构建：\(createdText)"
    }

    private var actionButtonTitle: String {
        switch updateState {
        case .available:
            "下载并更新"
        case .upToDate:
            "重新安装当前版本"
        default:
            "立即更新"
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
