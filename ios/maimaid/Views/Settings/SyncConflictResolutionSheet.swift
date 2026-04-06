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
                        HStack(spacing: 12) {
                            ProgressView()
                            Text(processingMessageKey)
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                        .padding(.vertical, 4)
                    }
                }
            }
            .navigationTitle(navigationTitleKey)
            .navigationBarTitleDisplayMode(.inline)
        }
    }

    // MARK: - Summary Section

    @ViewBuilder
    private var summarySection: some View {
        switch context {
        case let .account(state):
            Section("settings.cloud.resolution.title") {
                HStack(spacing: 8) {
                    Text("settings.cloud.resolution.message.current")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(verbatim: state.currentUserId)
                        .font(.footnote.monospaced())
                        .foregroundStyle(.primary)
                }

                HStack(spacing: 8) {
                    Text("settings.cloud.resolution.message.owner")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                    Spacer()
                    Text(verbatim: state.ownerUserId ?? "-")
                        .font(.footnote.monospaced())
                        .foregroundStyle(.primary)
                }
            }

        case let .importPreview(preview):
            Section {
                statRow(
                    icon: "tray.fill",
                    iconTint: .blue,
                    label: String(localized: "settings.import.resolution.summary.localOnly"),
                    value: "\(preview.localOnlyCount)"
                )

                statRow(
                    icon: "arrow.left.arrow.right",
                    iconTint: .orange,
                    label: String(localized: "settings.import.resolution.summary.different"),
                    value: "\(preview.differentCount)"
                )
            } header: {
                Text("settings.import.resolution.section.summary")
            }
        }
    }

    // MARK: - Conflicts Section

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
                            .contentTransition(.symbolEffect(.replace))
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
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .top, spacing: 12) {
                SongJacketView(
                    imageName: conflict.songImageName ?? "",
                    size: 48,
                    cornerRadius: 10
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

    // MARK: - Score Comparison

    @ViewBuilder
    private func scoreComparisonRow(local: ImportScoreValue?, remote: ImportScoreValue?) -> some View {
        HStack(alignment: .top, spacing: 8) {
            scorePanel(titleKey: "settings.import.resolution.value.local", value: local)
            scorePanel(titleKey: "settings.import.resolution.value.remote", value: remote)
        }
    }

    @ViewBuilder
    private func scorePanel(titleKey: LocalizedStringKey, value: ImportScoreValue?) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(titleKey)
                .font(.caption2.bold())
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
        .padding(10)
        .background(Color.primary.opacity(0.04), in: RoundedRectangle(cornerRadius: 10))
    }

    // MARK: - Processing Key

    private var processingMessageKey: LocalizedStringKey {
        switch context {
        case .account:
            return "settings.cloud.resolution.processing"
        case .importPreview:
            return "settings.import.resolution.processing"
        }
    }

    // MARK: - Action Section

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
}

// MARK: - Reusable Components

private extension SyncConflictResolutionSheet {
    func settingsIcon(icon: String, color: Color) -> some View {
        Image(systemName: icon)
            .font(.system(size: 15, weight: .semibold))
            .foregroundStyle(.white)
            .frame(width: 32, height: 32)
            .background(color.gradient, in: RoundedRectangle(cornerRadius: 10))
    }


    func statRow(icon: String, iconTint: Color, label: String, value: String) -> some View {
        HStack(spacing: 12) {
            settingsIcon(icon: icon, color: iconTint)
            Text(label)
                .font(.subheadline)
            Spacer()
            Text(value)
                .font(.subheadline.monospacedDigit().bold())
                .foregroundStyle(.primary)
        }
    }

    func actionRow(
        title: String,
        icon: String,
        tint: Color,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            HStack(spacing: 12) {
                settingsIcon(icon: icon, color: tint)

                Text(LocalizedStringKey(title))
                    .foregroundStyle(.primary)

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.tertiary)
            }
        }
        .buttonStyle(.plain)
        .disabled(isApplying)
        .opacity(isApplying ? 0.6 : 1)
    }

    func capsuleTag(text: String, background: Color) -> some View {
        Text(text)
            .font(.caption2.bold())
            .foregroundStyle(.white)
            .padding(.horizontal, 8)
            .padding(.vertical, 2)
            .background(background, in: Capsule())
    }
}
