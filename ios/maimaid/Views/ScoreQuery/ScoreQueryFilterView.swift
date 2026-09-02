import SwiftUI

struct ScoreQueryFilterView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.colorScheme) private var colorScheme
    @Binding var settings: ScoreQueryFilterSettings

    private let difficulties = [
        (value: "basic", label: "Basic"),
        (value: "advanced", label: "Advanced"),
        (value: "expert", label: "Expert"),
        (value: "master", label: "Master"),
        (value: "remaster", label: "Re:Master")
    ]
    private let ranks = ["SSS+", "SSS", "SS+", "SS", "S+", "S", "AAA", "AA", "A", "BBB", "BB", "B", "C", "D"]
    private let fcOptions = ["AP+", "AP", "FC+", "FC"]
    private let fsOptions = ["FDX+", "FDX", "FS+", "FS"]
    private var cardBackground: Color {
        Color.gray.opacity(colorScheme == .dark ? 0.22 : 0.08)
    }

    var body: some View {
        NavigationStack {
            Form {
                Section("filter.difficulty") {
                    FlowLayout(spacing: 10) {
                        ForEach(difficulties, id: \.value) { difficulty in
                            FilterChip(
                                title: difficulty.label,
                                isSelected: settings.selectedDifficulties.contains(difficulty.value),
                                color: ThemeUtils.colorForDifficulty(difficulty.value, nil, colorScheme)
                            ) {
                                toggle(difficulty.value, in: &settings.selectedDifficulties)
                            }
                        }
                    }
                    .listRowBackground(cardBackground)
                }

                Section("scoreQuery.badge.rank") {
                    FlowLayout(spacing: 10) {
                        ForEach(ranks, id: \.self) { rank in
                            FilterChip(
                                title: rank,
                                isSelected: settings.selectedRanks.contains(rank),
                                color: RatingUtils.colorForRank(rank)
                            ) {
                                toggle(rank, in: &settings.selectedRanks)
                            }
                        }
                    }
                    .listRowBackground(cardBackground)
                }

                Section("scoreQuery.badge.fc") {
                    FlowLayout(spacing: 10) {
                        ForEach(fcOptions, id: \.self) { fc in
                            FilterChip(
                                title: fc,
                                isSelected: settings.selectedFC.contains(fc),
                                color: ThemeUtils.fcColor(fc)
                            ) {
                                toggle(fc, in: &settings.selectedFC)
                            }
                        }
                    }
                    .listRowBackground(cardBackground)
                }

                Section("scoreQuery.badge.fs") {
                    FlowLayout(spacing: 10) {
                        ForEach(fsOptions, id: \.self) { fs in
                            FilterChip(
                                title: fs,
                                isSelected: settings.selectedFS.contains(fs),
                                color: ThemeUtils.fsColor(fs)
                            ) {
                                toggle(fs, in: &settings.selectedFS)
                            }
                        }
                    }
                    .listRowBackground(cardBackground)
                }
            }
            .navigationTitle("filter.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("filter.reset") {
                        withAnimation(.spring(response: 0.3)) {
                            settings = ScoreQueryFilterSettings()
                        }
                    }
                    .disabled(settings.isEmpty)
                }

                ToolbarItem(placement: .topBarTrailing) {
                    Button("filter.done") {
                        dismiss()
                    }
                    .bold()
                }
            }
        }
    }

    private func toggle(_ value: String, in selection: inout Set<String>) {
        if selection.contains(value) {
            selection.remove(value)
        } else {
            selection.insert(value)
        }
    }
}
