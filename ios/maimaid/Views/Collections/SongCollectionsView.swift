import SwiftUI
import SwiftData

struct SongCollectionsView: View {
    private static var prefetchedSongsDescriptor: FetchDescriptor<Song> {
        var descriptor = FetchDescriptor<Song>()
        descriptor.relationshipKeyPathsForPrefetching = [\Song.sheets]
        return descriptor
    }

    @Environment(\.modelContext) private var modelContext
    @Environment(CollectionImportCoordinator.self) private var collectionImportCoordinator
    @Query private var collections: [SongCollection]
    @Query private var items: [SongCollectionItem]
    @Query private var songs: [Song]
    @Query private var sheets: [Sheet]
    @State private var showingCreate = false
    @State private var newName = ""
    @State private var sharePayload: CollectionSharePayload?
    @State private var pendingShareCollection: SongCollection?
    @State private var showingShareChoices = false
    @State private var feedbackMessage: String?

    init() {
        _collections = Query()
        _items = Query()
        _songs = Query(Self.prefetchedSongsDescriptor)
        _sheets = Query()
    }

    var body: some View {
        let visibleCollections = orderedCollections
        let songMap = Dictionary(uniqueKeysWithValues: songs.map { ($0.songIdentifier, $0) })
        let sheetMap = Dictionary(
            uniqueKeysWithValues: sheets.map { (sheetKey($0.songIdentifier, $0.type, $0.difficulty), $0) })
        let activeItems = items.filter { $0.deletedAt == nil }
        let itemsByCollection = Dictionary(grouping: activeItems, by: \.collectionId)
        List {
            ForEach(visibleCollections) { collection in
                let collectionItems = itemsByCollection[collection.id] ?? []
                let previews = previewCards(
                    for: collection, items: collectionItems, songMap: songMap, sheetMap: sheetMap)
                NavigationLink {
                    SongCollectionDetailView(collection: collection, songs: songs)
                } label: {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack(spacing: 12) {
                            Image(systemName: "rectangle.stack")
                                .foregroundStyle(.secondary)
                            VStack(alignment: .leading, spacing: 4) {
                                Text(collection.name)
                                Text(String(localized: "collections_item_count \(collectionItems.count)"))
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        if !previews.isEmpty {
                            CollectionPreviewRow(previews: previews)
                        }
                    }
                }
                .swipeActions(edge: .trailing, allowsFullSwipe: false) {
                    Button(role: .destructive) {
                        delete(collection)
                    } label: {
                        Label("collections_delete", systemImage: "trash")
                    }
                    Button {
                        share(collection)
                    } label: {
                        Label("collections_share", systemImage: "square.and.arrow.up")
                    }
                    .tint(.blue)
                }
            }
        }
        .navigationTitle("home_collections")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("collections_import_clipboard", systemImage: "doc.on.clipboard") {
                        importFromClipboard()
                    }
                    Button("collections_new", systemImage: "plus") {
                        newName = ""
                        showingCreate = true
                    }
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .alert("collections_new", isPresented: $showingCreate) {
            TextField("collections_name", text: $newName)
            Button("filter.done", action: create)
            Button("profile.edit.cancel", role: .cancel) { newName = "" }
        }
        .sheet(item: $sharePayload) { payload in
            ShareSheetView(items: [payload.url])
        }
        .confirmationDialog(
            "collections_share_source_title",
            isPresented: $showingShareChoices,
            titleVisibility: .visible
        ) {
            Button("collections_share_current_snapshot", action: sharePendingSnapshot)
            Button("collections_share_cloud_latest", action: sharePendingCloud)
            Button("profile.edit.cancel", role: .cancel) {
                pendingShareCollection = nil
            }
        }
        .onChange(of: collectionImportCoordinator.feedbackID) {
            let message = collectionImportCoordinator.feedbackKey == "collections_import_success"
                ? String(localized: "collections_import_success")
                : String(localized: "collections_import_failed")
            showFeedback(message)
        }
        .overlay(alignment: .bottom) {
            if let feedbackMessage {
                Text(feedbackMessage)
                    .font(.subheadline)
                    .foregroundStyle(.white)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 11)
                    .background(.black.opacity(0.82), in: Capsule())
                    .padding(.bottom, 24)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
            }
        }
    }

    private var orderedCollections: [SongCollection] {
        collections
            .filter { $0.deletedAt == nil }
            .sorted {
                if $0.sortIndex == $1.sortIndex { return $0.createdAt < $1.createdAt }
                return $0.sortIndex < $1.sortIndex
            }
    }

    private func previewCards(
        for collection: SongCollection,
        items: [SongCollectionItem],
        songMap: [String: Song],
        sheetMap: [String: Sheet]
    ) -> [SongCollectionPreview] {
        let matchingCards: [SongCollectionPreview] = items
            .compactMap { item in
                guard let song = songMap[item.songId],
                      let sheet = sheetMap[sheetKey(item.songId, item.chartType, item.difficulty)] else { return nil }
                return SongCollectionPreview(song: song, sheet: sheet, position: item.position)
            }
        return Array(matchingCards.sorted { $0.position < $1.position }.prefix(4))
    }

    private func sheetKey(_ songId: String, _ type: String, _ difficulty: String) -> String {
        "\(songId)|\(type.lowercased())|\(difficulty.lowercased())"
    }

    private func create() {
        let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        let now = Date.now
        let uniqueName = uniqueCollectionName(from: String(name.prefix(40)))
        modelContext.insert(
            SongCollection(
                name: uniqueName, sortIndex: orderedCollections.count, createdAt: now, updatedAt: now,
                clientUpdatedAt: now))
        try? modelContext.save()
        newName = ""
    }

    private func delete(_ collection: SongCollection) {
        let now = Date.now
        collection.deletedAt = now
        collection.updatedAt = now
        collection.clientUpdatedAt = now
        try? modelContext.save()
    }

    private func uniqueCollectionName(from baseName: String) -> String {
        uniqueCollectionName(from: baseName, existingNames: Set(orderedCollections.map { $0.name }))
    }

    private func uniqueCollectionName(from baseName: String, existingNames names: Set<String>) -> String {
        guard names.contains(baseName) else { return baseName }
        var suffix = 2
        while names.contains("\(baseName) (\(suffix))") { suffix += 1 }
        return "\(baseName) (\(suffix))"
    }

    private func share(_ collection: SongCollection) {
        Task {
            if (try? await CollectionSharingService.fetchCloudCollection(collection.id)) != nil {
                pendingShareCollection = collection
                showingShareChoices = true
            } else {
                shareSnapshot(collection)
            }
        }
    }

    private func importFromClipboard() {
        guard let value = UIPasteboard.general.string else {
            showFeedback(String(localized: "collections_import_failed"))
            return
        }
        Task {
            await collectionImportCoordinator.importCollection(from: value, context: modelContext)
        }
    }

    private func sharePendingSnapshot() {
        guard let collection = pendingShareCollection else { return }
        shareSnapshot(collection)
        pendingShareCollection = nil
    }

    private func sharePendingCloud() {
        guard let collection = pendingShareCollection else { return }
        sharePayload = CollectionSharePayload(url: SongCollectionCodec.webURL(for: collection.id))
        pendingShareCollection = nil
    }

    private func shareSnapshot(_ collection: SongCollection) {
        guard let encoded = try? SongCollectionCodec.encode(collection: collection, items: items) else { return }
        sharePayload = CollectionSharePayload(url: SongCollectionCodec.webURL(for: encoded))
    }

    private func showFeedback(_ message: String) {
        withAnimation(.spring(response: 0.3, dampingFraction: 0.85)) { feedbackMessage = message }
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(1.5))
            guard feedbackMessage == message else { return }
            withAnimation(.easeOut(duration: 0.2)) { feedbackMessage = nil }
        }
    }

}

struct SongCollectionDetailView: View {
    @Environment(\.modelContext) private var modelContext
    let collection: SongCollection
    let songs: [Song]
    @Query private var items: [SongCollectionItem]
    @Query(filter: #Predicate<UserProfile> { $0.isActive }) private var activeProfiles: [UserProfile]
    @State private var isGridView = false
    @AppStorage(AppStorageKeys.songsGridColumns) private var gridColumns = 4
    @AppStorage(AppStorageKeys.collectionsSortOption) private var sortOption: SortOption = .defaultOrder
    @AppStorage(AppStorageKeys.collectionsSortAscending) private var sortAscending = true
    @State private var displayedCards: [SongCollectionCard] = []
    @State private var isPreparingCards = true
    @State private var showingRename = false
    @State private var draftName = ""
    @State private var sharePayload: CollectionSharePayload?
    @State private var showingShareChoices = false

    init(collection: SongCollection, songs: [Song]) {
        self.collection = collection
        self.songs = songs
        let collectionId = collection.id
        _items = Query(
            filter: #Predicate<SongCollectionItem> { $0.collectionId == collectionId && $0.deletedAt == nil },
            sort: [SortDescriptor(\.position)])
    }

    var body: some View {
        Group {
            if isPreparingCards {
                ProgressView()
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
            } else if isGridView {
                GeometryReader { geometry in
                    let columns = min(max(gridColumns, 3), 7)
                    let metrics = collectionGridMetrics(columnCount: columns, width: geometry.size.width)
                    ScrollView {
                        LazyVGrid(
                            columns: Array(
                                repeating: GridItem(.fixed(metrics.cellSize), spacing: metrics.spacing),
                                count: columns
                            ),
                            spacing: metrics.spacing
                        ) {
                            ForEach(displayedCards) { card in
                                SongCollectionGridCard(
                                    card: card,
                                    columnCount: columns,
                                    cellSize: metrics.cellSize,
                                    cornerRadius: collectionGridCornerRadius(columnCount: columns),
                                    showDots: columns <= 5,
                                    onDelete: { delete(card.item) }
                                )
                                .frame(width: metrics.cellSize, height: metrics.cellSize)
                            }
                        }
                        .padding(.horizontal, metrics.horizontalPadding)
                        .padding(.vertical, 12)
                    }
                }
            } else {
                List {
                    ForEach(displayedCards) { card in
                        SongCollectionListCard(
                            card: card
                        )
                        .alignmentGuide(.listRowSeparatorLeading) { _ in 0 }
                        .alignmentGuide(.listRowSeparatorTrailing) { dimensions in dimensions.width }
                        .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                            Button(role: .destructive) {
                                delete(card.item)
                            } label: {
                                Label("collections_delete_item", systemImage: "trash")
                            }
                        }
                    }
                }
            }
        }
        .navigationTitle(collection.name)
        .navigationBarTitleDisplayMode(.automatic)
        .task(id: cardsPreparationKey) {
            isPreparingCards = true
            await Task.yield()
            displayedCards = makeSortedCards()
            isPreparingCards = false
        }
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button {
                    withAnimation(.easeInOut(duration: 0.25)) { isGridView.toggle() }
                } label: {
                    Image(systemName: isGridView ? "list.bullet" : "square.grid.2x2")
                        .contentTransition(.symbolEffect(.replace))
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Picker("sort.title", selection: $sortOption) {
                        ForEach(SortOption.allCases) { option in
                            Text(LocalizedStringKey(option.rawValue)).tag(option)
                        }
                    }
                    Divider()
                    Button {
                        withAnimation(.easeInOut) { sortAscending.toggle() }
                    } label: {
                        Label(
                            sortAscending ? "sort.ascending" : "sort.descending",
                            systemImage: sortAscending ? "arrow.up" : "arrow.down"
                        )
                    }
                } label: {
                    Image(systemName: "arrow.up.arrow.down")
                }
            }
            ToolbarItem(placement: .topBarTrailing) {
                Menu {
                    Button("collections_rename", systemImage: "pencil") {
                        draftName = collection.name
                        showingRename = true
                    }
                    Button("collections_share", systemImage: "square.and.arrow.up") { share() }
                } label: {
                    Image(systemName: "ellipsis.circle")
                }
            }
        }
        .alert("collections_rename", isPresented: $showingRename) {
            TextField("collections_name", text: $draftName)
            Button("filter.done", action: rename)
            Button("profile.edit.cancel", role: .cancel) {}
        }
        .sheet(item: $sharePayload) { payload in
            ShareSheetView(items: [payload.url])
        }
        .confirmationDialog(
            "collections_share_source_title",
            isPresented: $showingShareChoices,
            titleVisibility: .visible
        ) {
            Button("collections_share_current_snapshot", action: shareSnapshot)
            Button("collections_share_cloud_latest", action: shareCloud)
            Button("profile.edit.cancel", role: .cancel) {}
        }
    }

    private func collectionGridMetrics(columnCount: Int, width: CGFloat) -> (
        cellSize: CGFloat, spacing: CGFloat, horizontalPadding: CGFloat
    ) {
        let spacing: CGFloat
        switch columnCount {
        case ...3: spacing = 5
        case 4: spacing = 4
        case 5: spacing = 3
        case 6: spacing = 2
        default: spacing = 1
        }
        let horizontalPadding = spacing + 2
        let cellSize = (width - spacing * CGFloat(columnCount - 1) - horizontalPadding * 2) / CGFloat(columnCount)
        return (max(1, cellSize), spacing, horizontalPadding)
    }

    private func collectionGridCornerRadius(columnCount: Int) -> CGFloat {
        switch columnCount {
        case ...3: return 10
        case 4...5: return 6
        default: return 3
        }
    }

    private var activeServer: GameServer {
        activeProfiles.first.flatMap { GameServer(rawValue: $0.server) } ?? .jp
    }

    private var cardsPreparationKey: String {
        let latestUpdate = items.map(\.updatedAt).max() ?? .distantPast
        return "\(items.count)|\(latestUpdate.timeIntervalSinceReferenceDate)|\(sortOption.rawValue)|\(sortAscending)"
    }

    private func makeSortedCards() -> [SongCollectionCard] {
        let songIDs = Set(items.map(\.songId))
        let songMap = Dictionary(
            uniqueKeysWithValues: songs.lazy.filter { songIDs.contains($0.songIdentifier) }.map {
                ($0.songIdentifier, $0)
            })
        return items.compactMap { item in
            let song = songMap[item.songId]
            return SongCollectionCard(
                item: item,
                song: song,
                sheet: song?.sheets.first { sheet in
                    sheet.type.caseInsensitiveCompare(item.chartType) == .orderedSame &&
                    sheet.difficulty.caseInsensitiveCompare(item.difficulty) == .orderedSame
                }
            )
        }
        .sorted { lhs, rhs in
            return collectionCardComesBefore(
                left: lhs,
                right: rhs,
                sortOption: sortOption,
                sortAscending: sortAscending,
                activeServer: activeServer
            )
        }
    }

    private func delete(_ item: SongCollectionItem) {
        let now = Date.now
        item.deletedAt = now
        item.updatedAt = now
        item.clientUpdatedAt = now
        try? modelContext.save()
    }

    private func rename() {
        let name = draftName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        let now = Date.now
        collection.name = String(name.prefix(40))
        collection.updatedAt = now
        collection.clientUpdatedAt = now
        try? modelContext.save()
    }

    private func share() {
        Task {
            if (try? await CollectionSharingService.fetchCloudCollection(collection.id)) != nil {
                showingShareChoices = true
            } else {
                shareSnapshot()
            }
        }
    }

    private func shareSnapshot() {
        guard let encoded = try? SongCollectionCodec.encode(collection: collection, items: items) else { return }
        sharePayload = CollectionSharePayload(url: SongCollectionCodec.webURL(for: encoded))
    }

    private func shareCloud() {
        sharePayload = CollectionSharePayload(url: SongCollectionCodec.webURL(for: collection.id))
    }
}

private struct SongCollectionCard: Identifiable {
    let item: SongCollectionItem
    let song: Song?
    let sheet: Sheet?
    var id: UUID { item.id }
}

private struct SongCollectionPreview: Identifiable {
    let song: Song
    let sheet: Sheet
    let position: Int

    var id: String {
        "\(song.songIdentifier)|\(sheet.type.lowercased())|\(sheet.difficulty.lowercased())"
    }
}

private func collectionCardComesBefore(
    left: SongCollectionCard,
    right: SongCollectionCard,
    sortOption: SortOption,
    sortAscending: Bool,
    activeServer: GameServer
) -> Bool {
    guard let leftSong = left.song, let rightSong = right.song else {
        return left.item.position < right.item.position
    }

    let result: ComparisonResult
    let followsDirection: Bool
    switch sortOption {
    case .defaultOrder:
        result =
            leftSong.sortOrder == rightSong.sortOrder
            ? .orderedSame : (leftSong.sortOrder < rightSong.sortOrder ? .orderedAscending : .orderedDescending)
        followsDirection = true
    case .versionAndDate:
        let leftVersion = ThemeUtils.versionSortOrder(leftSong.version ?? "")
        let rightVersion = ThemeUtils.versionSortOrder(rightSong.version ?? "")
        if leftVersion != rightVersion {
            result = leftVersion < rightVersion ? .orderedAscending : .orderedDescending
        } else if (leftSong.releaseDate ?? "") != (rightSong.releaseDate ?? "") {
            result =
                (leftSong.releaseDate ?? "") < (rightSong.releaseDate ?? "") ? .orderedAscending : .orderedDescending
        } else {
            result =
                leftSong.sortOrder == rightSong.sortOrder
                ? .orderedSame : (leftSong.sortOrder < rightSong.sortOrder ? .orderedAscending : .orderedDescending)
        }
        followsDirection = true
    case .difficulty:
        let leftDifficulty = left.sheet.map {
            ServerChartPolicy.metadata(for: $0, on: activeServer).ratingLevel ?? 0
        } ?? 0
        let rightDifficulty = right.sheet.map {
            ServerChartPolicy.metadata(for: $0, on: activeServer).ratingLevel ?? 0
        } ?? 0
        if leftDifficulty != rightDifficulty {
            result = leftDifficulty < rightDifficulty ? .orderedAscending : .orderedDescending
            followsDirection = true
        } else {
            result = leftSong.title.localizedCaseInsensitiveCompare(rightSong.title)
            followsDirection = false
        }
    }
    if result != .orderedSame {
        return followsDirection
            ? (sortAscending ? result == .orderedAscending : result == .orderedDescending) : result == .orderedAscending
    }
    return left.item.position < right.item.position
}

private struct CollectionPreviewRow: View {
    let previews: [SongCollectionPreview]
    @Environment(\.colorScheme) private var colorScheme

    var body: some View {
        HStack(spacing: 8) {
            ForEach(previews) { preview in
                SongJacketView(
                    imageName: preview.song.imageName,
                    size: 56,
                    cornerRadius: 10,
                    useThumbnail: true
                )
                .overlay {
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(
                            ThemeUtils.colorForDifficulty(preview.sheet.difficulty, preview.sheet.type, colorScheme),
                            lineWidth: 2
                        )
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

private struct SongCollectionListCard: View {
    let card: SongCollectionCard

    var body: some View {
        if let song = card.song {
            NavigationLink {
                SongDetailView(song: song, preferredType: card.item.chartType)
            } label: {
                SongRowView(
                    song: song,
                    chartType: card.item.chartType,
                    actualSheet: card.sheet,
                    showsCardBackground: false
                )
            }
        } else {
            HStack {
                Image(systemName: "music.note")
                    .foregroundStyle(.secondary)
                VStack(alignment: .leading, spacing: 4) {
                    Text(card.item.songId)
                        .font(.headline)
                    Text("collections_missing")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                Spacer()
            }
        }
    }
}

private struct SongCollectionGridCard: View {
    let card: SongCollectionCard
    let columnCount: Int
    let cellSize: CGFloat
    let cornerRadius: CGFloat
    let showDots: Bool
    let onDelete: () -> Void

    var body: some View {
        Group {
            if let song = card.song {
                NavigationLink {
                    SongDetailView(song: song, preferredType: card.item.chartType)
                } label: {
                    SongGridCell(
                        song: song,
                        columnCount: columnCount,
                        cellSize: cellSize,
                        cornerRadius: cornerRadius,
                        showDots: showDots,
                        actualSheet: card.sheet,
                        showActualDifficultyBorder: true
                    )
                }
                .buttonStyle(.plain)
            } else {
                Button(
                    action: {},
                    label: {
                        RoundedRectangle(cornerRadius: 8)
                            .fill(.secondary.opacity(0.12))
                            .frame(height: 104)
                            .overlay { Image(systemName: "music.note").foregroundStyle(.secondary) }
                    }
                )
                .buttonStyle(.plain)
            }
        }
        .contextMenu {
            Button("collections_delete_item", systemImage: "trash", role: .destructive, action: onDelete)
        }
    }
}

struct AddToSongCollectionsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<SongCollection> { $0.deletedAt == nil }, sort: [SortDescriptor(\.sortIndex)]) private
        var collections: [SongCollection]
    @Query private var items: [SongCollectionItem]
    let songId: String
    let chartType: String
    let difficulty: String
    @State private var selected = Set<UUID>()

    var body: some View {
        NavigationStack {
            List {
                if collections.isEmpty {
                    ContentUnavailableView(
                        "collections_picker_empty",
                        systemImage: "rectangle.stack"
                    )
                    .listRowBackground(Color.clear)
                } else {
                    ForEach(collections) { collection in
                        let isSelected = selected.contains(collection.id)
                        Button {
                            if isSelected {
                                selected.remove(collection.id)
                            } else {
                                selected.insert(collection.id)
                            }
                        } label: {
                            HStack(spacing: 12) {
                                Image(systemName: isSelected ? "checkmark.square.fill" : "square")
                                    .font(.title3)
                                    .foregroundStyle(isSelected ? Color.accentColor : .secondary)
                                    .frame(width: 24)
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(collection.name)
                                        .foregroundStyle(.primary)
                                    Text(
                                        String(
                                            localized: "collections_item_count \(displayedItemCount(for: collection))")
                                    )
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                }
                                Spacer(minLength: 0)
                            }
                            .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .navigationTitle("collections_picker_title")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("filter.done", action: save) }
                ToolbarItem(placement: .cancellationAction) {
                    Button("profile.edit.cancel", action: dismiss.callAsFunction)
                }
            }
        }
        .onAppear {
            selected = Set(collections.compactMap { collection in
                let containsSong = items.contains {
                    $0.collectionId == collection.id
                        && $0.songId == songId
                        && $0.chartType.caseInsensitiveCompare(chartType) == .orderedSame
                        && $0.difficulty.caseInsensitiveCompare(difficulty) == .orderedSame
                        && $0.deletedAt == nil
                }
                return containsSong ? collection.id : nil
            })
        }
    }

    private func save() {
        for collection in collections {
            let existing = items.first {
                $0.collectionId == collection.id
                    && $0.songId == songId
                    && $0.chartType.caseInsensitiveCompare(chartType) == .orderedSame
                    && $0.difficulty.caseInsensitiveCompare(difficulty) == .orderedSame
            }
            if selected.contains(collection.id) {
                if let existing {
                    existing.deletedAt = nil
                    existing.updatedAt = .now
                    existing.clientUpdatedAt = .now
                } else {
                    let item = SongCollectionItem(
                        collectionId: collection.id,
                        songId: songId,
                        chartType: chartType,
                        difficulty: difficulty,
                        position: items.count { $0.collectionId == collection.id },
                        clientUpdatedAt: .now
                    )
                    modelContext.insert(item)
                }
            } else if let existing {
                existing.deletedAt = .now
                existing.updatedAt = .now
                existing.clientUpdatedAt = .now
            }
        }
        try? modelContext.save()
        dismiss()
    }

    private func displayedItemCount(for collection: SongCollection) -> Int {
        let persistedCount = items.filter { $0.collectionId == collection.id && $0.deletedAt == nil }.count
        let alreadyContainsChart = items.contains {
            $0.collectionId == collection.id &&
            $0.songId == songId &&
            $0.chartType.caseInsensitiveCompare(chartType) == .orderedSame &&
            $0.difficulty.caseInsensitiveCompare(difficulty) == .orderedSame &&
            $0.deletedAt == nil
        }
        if selected.contains(collection.id) && !alreadyContainsChart { return persistedCount + 1 }
        if !selected.contains(collection.id) && alreadyContainsChart { return max(0, persistedCount - 1) }
        return persistedCount
    }
}
