import SwiftData
import SwiftUI
import UIKit

struct ConstantTableExportView: View {
    enum Mode: String, CaseIterable {
        case constantsOnly
        case withScores
    }

    struct Entry: Identifiable, Sendable {
        let id: String
        let songTitle: String
        let imageName: String
        let difficulty: String
        let type: String
        let level: Double
        let rank: String?
        let fc: String?
        let fs: String?
    }

    struct ExportSection: Identifiable, Sendable {
        let levelLabel: String
        let entries: [Entry]

        var id: String { levelLabel }
    }

    struct SharePayload: Identifiable {
        let id = UUID()
        let image: UIImage
    }

    private static var prefetchedSongsDescriptor: FetchDescriptor<Song> {
        var descriptor = FetchDescriptor<Song>()
        descriptor.relationshipKeyPathsForPrefetching = [\Song.sheets]
        return descriptor
    }

    @Environment(\.modelContext) private var modelContext
    @Environment(\.colorScheme) private var colorScheme
    @Query(Self.prefetchedSongsDescriptor) private var songs: [Song]
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]

    @State private var allEntries: [Entry] = []
    @State private var selectedBaseLevel = 14
    @State private var includesScores = false
    @State private var isLoading = true
    @State private var isExporting = false
    @State private var sharePayload: SharePayload?

    private var activeProfile: UserProfile? { activeProfiles.first }
    private var mode: Mode { includesScores ? .withScores : .constantsOnly }

    private var availableBaseLevels: [Int] {
        Array(Set(allEntries.map { exportBucketBaseLevel(for: $0.level) }))
            .sorted(by: >)
    }

    private var exportEntries: [Entry] {
        allEntries
            .filter { exportBucketBaseLevel(for: $0.level) == selectedBaseLevel }
            .sorted(by: exportEntryComparator)
    }

    private var exportSections: [ExportSection] {
        let grouped = Dictionary(grouping: exportEntries) { constantKey(for: $0.level) }

        return grouped
            .map { levelKey, entries in
                ExportSection(levelLabel: levelKey, entries: entries.sorted(by: exportEntryComparator))
            }
            .sorted { lhs, rhs in
                (Double(lhs.levelLabel) ?? 0) > (Double(rhs.levelLabel) ?? 0)
            }
    }

    var body: some View {
        Form {
            if isLoading {
                Section {
                    HStack {
                        Spacer()
                        ProgressView()
                        Spacer()
                    }
                }
            } else if availableBaseLevels.isEmpty {
                ContentUnavailableView(
                    "scoreQuery.export.empty",
                    systemImage: "music.note.list",
                    description: Text("scoreQuery.export.empty.description")
                )
            } else {
                Section {
                    Picker("scoreQuery.export.level", selection: $selectedBaseLevel) {
                        ForEach(availableBaseLevels, id: \.self) { value in
                            Text(exportBaseLevelLabel(for: value)).tag(value)
                        }
                    }

                    Toggle(isOn: $includesScores) {
                        Label(
                            "scoreQuery.export.mode.scores",
                            systemImage: includesScores ? "person.text.rectangle.fill" : "person.text.rectangle"
                        )
                    }
                }

                Section {
                    LabeledContent {
                        Text(
                            "\(exportEntries.count.formatted()) \(String(localized: "scoreQuery.export.charts")) · "
                            + "\(exportSections.count.formatted()) \(String(localized: "scoreQuery.export.sections"))"
                        )
                        .foregroundStyle(.secondary)
                        .monospacedDigit()
                    } label: {
                        Label("scoreQuery.export.regularOnly", systemImage: "music.note")
                    }
                }

                Section {
                    Button {
                        exportConstantTable()
                    } label: {
                        HStack {
                            Spacer()
                            if isExporting {
                                ProgressView()
                                    .controlSize(.small)
                            } else {
                                Image(systemName: "photo.on.rectangle.angled")
                            }
                            Text(isExporting ? "scoreQuery.export.exporting" : "scoreQuery.export.button")
                            Spacer()
                        }
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)
                    .disabled(exportEntries.isEmpty || isExporting)
                    .listRowBackground(Color.clear)
                }
            }
        }
        .navigationTitle("scoreQuery.export.title")
        .sheet(item: $sharePayload) { payload in
            ShareSheetView(items: [payload.image])
        }
        .task {
            await loadData()
        }
        .onReceive(NotificationCenter.default.publisher(for: .maimaiScoresDidChange)) { notification in
            if let changedProfileID = notification.object as? UUID,
               changedProfileID != activeProfile?.id {
                return
            }
            Task { await loadData() }
        }
    }

    private func loadData() async {
        isLoading = true
        let scoreMap = ScoreService.shared.scoreMap(context: modelContext)
        var entries: [Entry] = []

        for (index, song) in songs.enumerated() {
            if index.isMultiple(of: 32) {
                await Task.yield()
            }
            if song.category.localizedStandardContains("utage") || song.category.contains("宴") {
                continue
            }

            for sheet in song.sheets {
                if sheet.type.localizedStandardContains("utage") {
                    continue
                }

                let level = sheet.internalLevelValue ?? sheet.levelValue ?? 0
                guard level > 0 else { continue }
                let score = scoreForSheet(sheet, in: scoreMap)

                entries.append(
                    Entry(
                        id: "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)",
                        songTitle: song.title,
                        imageName: song.imageName,
                        difficulty: sheet.difficulty,
                        type: sheet.type,
                        level: level,
                        rank: score.map { RatingUtils.calculateRank(achievement: $0.rate) },
                        fc: score?.fc,
                        fs: score?.fs
                    )
                )
            }
        }

        allEntries = entries
        if let firstLevel = availableBaseLevels.first, !availableBaseLevels.contains(selectedBaseLevel) {
            selectedBaseLevel = firstLevel
        }
        isLoading = false
    }

    private func scoreForSheet(_ sheet: Sheet, in map: [String: Score]) -> Score? {
        for candidate in candidateSheetIDs(for: sheet) {
            if let score = map[candidate] {
                return score
            }
        }
        return nil
    }

    private func candidateSheetIDs(for sheet: Sheet) -> [String] {
        let rawIdentifiers: [String?] = [
            sheet.songIdentifier,
            sheet.song.map { String($0.songId) },
            sheet.song?.songIdentifier,
            sheet.songId == 0 ? nil : String(sheet.songId)
        ]
        var candidates: [String] = []
        var seen = Set<String>()

        for raw in rawIdentifiers {
            guard let raw, !raw.isEmpty, raw != "0" else { continue }
            for separator in ["_", "-"] {
                let sheetID = "\(raw)\(separator)\(sheet.type)\(separator)\(sheet.difficulty)"
                if seen.insert(sheetID).inserted {
                    candidates.append(sheetID)
                }
            }
        }

        return candidates
    }

    private func constantKey(for level: Double) -> String {
        let normalized = (level * 10).rounded(.towardZero) / 10
        return normalized.formatted(.number.precision(.fractionLength(1)))
    }

    private func exportBucketBaseLevel(for level: Double) -> Int {
        level >= 15 ? 14 : Int(level.rounded(.down))
    }

    private func exportBaseLevelLabel(for level: Int) -> String {
        level == 14 ? "14~15" : level.formatted()
    }

    private func exportEntryComparator(_ lhs: Entry, _ rhs: Entry) -> Bool {
        if lhs.songTitle != rhs.songTitle {
            return lhs.songTitle.localizedStandardCompare(rhs.songTitle) == .orderedAscending
        }

        let lhsDifficulty = ThemeUtils.difficultyOrder(lhs.difficulty)
        let rhsDifficulty = ThemeUtils.difficultyOrder(rhs.difficulty)
        if lhsDifficulty != rhsDifficulty {
            return lhsDifficulty > rhsDifficulty
        }

        if lhs.type != rhs.type {
            return lhs.type.localizedStandardCompare(rhs.type) == .orderedAscending
        }

        return lhs.id < rhs.id
    }

    private func exportConstantTable() {
        guard !exportSections.isEmpty else { return }
        isExporting = true

        Task { @MainActor in
            await Task.yield()
            let image = ConstantTableExportImageView.renderImage(
                baseLevel: selectedBaseLevel,
                sections: exportSections,
                mode: mode,
                userName: activeProfile?.name,
                colorScheme: colorScheme
            )
            isExporting = false
            if let image {
                sharePayload = SharePayload(image: image)
            }
        }
    }
}
