import SwiftUI

enum SyncConflictResolutionSheetContext {
    case account(AccountConflictState)
    case importPreview(ImportSyncConflictPreview)
}

enum SyncConflictResolutionSheetAction: String, Identifiable {
    case merge
    case keepLocal
    case useRemote

    var id: String { rawValue }
}

struct SyncConflictResolutionSheet: View {
    let context: SyncConflictResolutionSheetContext
    let isApplying: Bool
    let onSelect: (SyncConflictResolutionSheetAction) -> Void
    @Environment(\.colorScheme) private var colorScheme
    @State private var isShowingAllImportConflicts = false

    private var navigationTitleKey: LocalizedStringKey {
        switch context {
        case .account:
            return "settings.cloud.resolution.title"
        case .importPreview:
            return "settings.import.resolution.title"
        }
    }

    private var actionSectionTitleKey: LocalizedStringKey {
        switch context {
        case .account:
            return "settings.cloud.resolution.section.actions"
        case .importPreview:
            return "settings.import.resolution.section.actions"
        }
    }

    var body: some View {
        NavigationStack {
            List {
                summarySection

                if case let .importPreview(preview) = context, !preview.conflicts.isEmpty {
                    importConflictsSection(preview: preview)
                }

                actionSection

                if isApplying {
                    Section {
                        HStack {
                            ProgressView()
                            Text(processingMessageKey)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
            .navigationTitle(navigationTitleKey)
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    @ViewBuilder
    private var summarySection: some View {
        switch context {
        case let .account(state):
            Section {
                VStack(alignment: .leading) {
                    Text("settings.cloud.resolution.message.current")
                        .font(.subheadline)
                    Text(verbatim: state.currentUserId)
                        .font(.footnote.monospaced())
                        .foregroundStyle(.secondary)
                    Text("settings.cloud.resolution.message.owner")
                        .font(.subheadline)
                    Text(verbatim: state.ownerUserId ?? "-")
                        .font(.footnote.monospaced())
                        .foregroundStyle(.secondary)
                }
                .fixedSize(horizontal: false, vertical: true)
            } header: {
                Text("settings.cloud.resolution.title")
            }
        case let .importPreview(preview):
            Section {
                VStack(alignment: .leading) {
                    LabeledContent(
                        String(localized: "settings.import.resolution.summary.localOnly"),
                        value: "\(preview.localOnlyCount)"
                    )
                    LabeledContent(
                        String(localized: "settings.import.resolution.summary.different"),
                        value: "\(preview.differentCount)"
                    )
                }
                .font(.subheadline)
            } header: {
                Text("settings.import.resolution.section.summary")
            } footer: {
                Label {
                    Text("settings.import.resolution.summary.footer")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                        .fixedSize(horizontal: false, vertical: true)
                } icon: {
                    Image(systemName: "info.circle")
                }
            }
        }
    }

    @ViewBuilder
    private func importConflictsSection(preview: ImportSyncConflictPreview) -> some View {
        let visibleConflicts = isShowingAllImportConflicts
            ? preview.conflicts
            : Array(preview.conflicts.prefix(1))

        Section("settings.import.resolution.section.conflicts") {
            ForEach(visibleConflicts) { conflict in
                importConflictRow(conflict)
                    .listRowInsets(EdgeInsets(top: 12, leading: 18, bottom: 10, trailing: 18))
            }

            if preview.conflicts.count > 1 {
                Button {
                    withAnimation(.easeInOut(duration: 0.2)) {
                        isShowingAllImportConflicts.toggle()
                    }
                } label: {
                    HStack(spacing: 8) {
                        Text(
                            isShowingAllImportConflicts
                                ? String(localized: "settings.import.resolution.more.hide")
                                : String(localized: "settings.import.resolution.more.show \(preview.conflicts.count - 1)")
                        )
                        .font(.footnote)

                        Spacer()

                        Image(systemName: isShowingAllImportConflicts ? "chevron.up" : "chevron.down")
                            .font(.caption)
                    }
                    .foregroundStyle(.secondary)
                }
                .buttonStyle(.plain)
                .listRowInsets(EdgeInsets(top: 0, leading: 18, bottom: 6, trailing: 18))
            }
        }
        .onChange(of: preview.id) {
            isShowingAllImportConflicts = false
        }
    }

    @ViewBuilder
    private func importConflictRow(_ conflict: ImportScoreConflictItem) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .top, spacing: 12) {
                SongJacketView(
                    imageName: conflict.songImageName ?? "",
                    size: 44,
                    cornerRadius: 8
                )

                VStack(alignment: .leading, spacing: 6) {
                    Text(conflict.songTitle)
                        .font(.subheadline.bold())
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        capsuleTag(
                            text: chartTypeText(conflict.chartType),
                            background: ThemeUtils.badgeColorForChartType(conflict.chartType, colorScheme)
                        )
                        capsuleTag(
                            text: ThemeUtils.diffShort(conflict.difficulty),
                            background: ThemeUtils.colorForDifficulty(conflict.difficulty, conflict.chartType, colorScheme)
                        )
                    }
                }

                Spacer(minLength: 0)
            }

            scoreComparisonRow(local: conflict.local, remote: conflict.remote)
        }
        .padding(.vertical, 2)
    }

    @ViewBuilder
    private func scoreComparisonRow(local: ImportScoreValue?, remote: ImportScoreValue?) -> some View {
        HStack(alignment: .top, spacing: 10) {
            scorePanel(titleKey: "settings.import.resolution.value.local", value: local)
            scorePanel(titleKey: "settings.import.resolution.value.remote", value: remote)
        }
    }

    @ViewBuilder
    private func scorePanel(titleKey: LocalizedStringKey, value: ImportScoreValue?) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(titleKey)
                .font(.caption2)
                .foregroundStyle(.secondary)

            if let value {
                HStack(spacing: 3) {
                    Text(value.rate.formatted(.number.precision(.fractionLength(4))))
                        .font(.caption.monospacedDigit())
                    Text(value.rank)
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }

                HStack(spacing: 4) {
                    Text("DX \(value.dxScore)")
                        .font(.caption2.monospacedDigit())
                        .foregroundStyle(.secondary)

                    if let fc = value.fc, !fc.isEmpty {
                        Text(ThemeUtils.normalizeFC(fc))
                            .font(.caption2)
                            .foregroundStyle(ThemeUtils.fcColor(fc))
                    }

                    if let fs = value.fs, !fs.isEmpty {
                        Text(ThemeUtils.normalizeFS(fs))
                            .font(.caption2)
                            .foregroundStyle(ThemeUtils.fsColor(fs))
                    }
                }

                Text(value.achievementDate.formatted(date: .numeric, time: .omitted))
                    .font(.caption2.monospacedDigit())
                    .foregroundStyle(.tertiary)
            } else {
                Text("settings.import.resolution.value.missing")
                    .font(.caption2)
                    .foregroundStyle(.secondary)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private var processingMessageKey: LocalizedStringKey {
        switch context {
        case .account:
            return "settings.cloud.resolution.processing"
        case .importPreview:
            return "settings.import.resolution.processing"
        }
    }

    @ViewBuilder
    private var actionSection: some View {
        Section(actionSectionTitleKey) {
            actionRow(
                title: mergeTitleKey,
                icon: "arrow.triangle.merge",
                tint: .blue
            ) {
                onSelect(.merge)
            }

            actionRow(
                title: keepLocalTitleKey,
                icon: "externaldrive.badge.plus",
                tint: .orange
            ) {
                onSelect(.keepLocal)
            }

            actionRow(
                title: useRemoteTitleKey,
                icon: "arrow.clockwise.icloud",
                tint: .green
            ) {
                onSelect(.useRemote)
            }
        }
    }

    private var mergeTitleKey: String {
        switch context {
        case .account:
            return "settings.cloud.resolution.option.merge"
        case .importPreview:
            return "settings.import.resolution.option.merge"
        }
    }

    private var keepLocalTitleKey: String {
        switch context {
        case .account:
            return "settings.cloud.resolution.option.overwriteCloud"
        case .importPreview:
            return "settings.import.resolution.option.keepLocal"
        }
    }

    private var useRemoteTitleKey: String {
        switch context {
        case .account:
            return "settings.cloud.resolution.option.overwriteLocal"
        case .importPreview:
            return "settings.import.resolution.option.overwriteLocal"
        }
    }

    private func chartTypeText(_ chartType: String) -> String {
        let normalized = chartType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if normalized == "std" {
            return String(localized: "scanner.chart.std")
        }
        return chartType.uppercased()
    }

    private func actionRow(
        title: String,
        icon: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(tint)
                    .frame(width: 22, height: 22, alignment: .center)

                Text(LocalizedStringKey(title))
                    .foregroundStyle(.primary)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
        .disabled(isApplying)
        .opacity(isApplying ? 0.6 : 1)
    }

    private func capsuleTag(text: String, background: Color) -> some View {
        Text(text)
            .font(.caption2.bold())
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(background, in: Capsule())
    }
}
