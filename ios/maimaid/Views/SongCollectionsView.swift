import SwiftUI
import SwiftData
import UIKit

struct SongCollectionsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query private var collections: [SongCollection]
    @Query private var items: [SongCollectionItem]
    @State private var showingCreate = false
    @State private var newName = ""

    init() {
        _collections = Query()
        _items = Query()
    }

    var body: some View {
        let visibleCollections = collections
            .filter { $0.deletedAt == nil }
            .sorted {
                if $0.sortIndex == $1.sortIndex {
                    return $0.createdAt < $1.createdAt
                }
                return $0.sortIndex < $1.sortIndex
            }
        List {
            ForEach(visibleCollections) { collection in
                NavigationLink {
                    SongCollectionDetailView(collection: collection)
                } label: {
                    Label {
                        VStack(alignment: .leading) {
                            Text(collection.name)
                            Text("\(items.filter { $0.collectionId == collection.id }.count) items")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    } icon: {
                        Image(systemName: "rectangle.stack")
                    }
                }
            }
            .onDelete(perform: delete)
            .onMove(perform: move)
        }
        .navigationTitle("Collections")
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { EditButton() }
            ToolbarItem(placement: .topBarTrailing) {
                Button("New Collection", systemImage: "plus") { showingCreate = true }
            }
        }
        .alert("New Collection", isPresented: $showingCreate) {
            TextField("Name", text: $newName)
            Button("Create", action: create)
            Button("Cancel", role: .cancel) { newName = "" }
        }
    }

    private func create() {
        let name = newName.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !name.isEmpty else { return }
        let now = Date.now
        modelContext.insert(SongCollection(name: String(name.prefix(40)), sortIndex: collections.filter { $0.deletedAt == nil }.count, createdAt: now, updatedAt: now, clientUpdatedAt: now))
        try? modelContext.save()
        newName = ""
    }

    private func delete(at offsets: IndexSet) {
        for index in offsets {
            let visibleCollections = collections
                .filter { $0.deletedAt == nil }
                .sorted {
                    if $0.sortIndex == $1.sortIndex { return $0.createdAt < $1.createdAt }
                    return $0.sortIndex < $1.sortIndex
                }
            let now = Date.now
            visibleCollections[index].deletedAt = now
            visibleCollections[index].updatedAt = now
            visibleCollections[index].clientUpdatedAt = now
        }
        try? modelContext.save()
    }

    private func move(from source: IndexSet, to destination: Int) {
        var ordered = collections
            .filter { $0.deletedAt == nil }
            .sorted {
                if $0.sortIndex == $1.sortIndex { return $0.createdAt < $1.createdAt }
                return $0.sortIndex < $1.sortIndex
            }
        ordered.move(fromOffsets: source, toOffset: destination)
        let now = Date.now
        for (index, collection) in ordered.enumerated() {
            collection.sortIndex = index
            collection.updatedAt = now
            collection.clientUpdatedAt = now
        }
        try? modelContext.save()
    }

}

struct SongCollectionDetailView: View {
    @Environment(\.modelContext) private var modelContext
    let collection: SongCollection
    @Query private var items: [SongCollectionItem]
    @Query private var songs: [Song]
    @State private var shareItems: [Any] = []
    @State private var showingShare = false

    init(collection: SongCollection) {
        self.collection = collection
        let collectionId = collection.id
        _items = Query(filter: #Predicate<SongCollectionItem> { $0.collectionId == collectionId && $0.deletedAt == nil }, sort: [SortDescriptor(\.position)])
        _songs = Query()
    }

    var body: some View {
        List {
            ForEach(items) { item in
                let song = songs.first { $0.songIdentifier == item.songId }
                VStack(alignment: .leading) {
                    Text(song?.title ?? item.songId)
                    Text("\(item.chartType.uppercased()) · \(item.difficulty.uppercased())")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            .onDelete { offsets in
                let now = Date.now
                for index in offsets { items[index].deletedAt = now; items[index].updatedAt = now; items[index].clientUpdatedAt = now }
                try? modelContext.save()
            }
            .onMove { source, destination in
                var ordered = items
                ordered.move(fromOffsets: source, toOffset: destination)
                let now = Date.now
                for (index, item) in ordered.enumerated() { item.position = index; item.updatedAt = now; item.clientUpdatedAt = now }
                try? modelContext.save()
            }
        }
        .navigationTitle(collection.name)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { EditButton() }
            ToolbarItem(placement: .topBarTrailing) {
                Menu("Export Collection", systemImage: "square.and.arrow.up") {
                    Button("Export Collection", systemImage: "square.and.arrow.up", action: export)
                    Button("Copy Export Code", systemImage: "doc.on.doc", action: copyExport)
                }
            }
        }
        .sheet(isPresented: $showingShare) { ShareSheetView(items: shareItems) }
    }

    private func encodedExport() -> String? {
        try? SongCollectionCodec.encode(collections: [collection], items: items)
    }

    private func export() {
        guard let encoded = encodedExport() else { return }
        let fileName = "maimaid-collection-\(collection.id.uuidString.lowercased()).mmdcollections"
        let url = URL.temporaryDirectory.appending(path: fileName)
        guard let data = encoded.data(using: .utf8),
              (try? data.write(to: url, options: .atomic)) != nil else { return }
        shareItems = [url]
        showingShare = true
    }

    private func copyExport() {
        UIPasteboard.general.string = encodedExport()
    }
}

struct AddToSongCollectionsView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<SongCollection> { $0.deletedAt == nil }, sort: [SortDescriptor(\.sortIndex)]) private var collections: [SongCollection]
    @Query private var items: [SongCollectionItem]
    let songId: String
    let chartType: String
    let difficulty: String
    @State private var selected = Set<UUID>()

    var body: some View {
        NavigationStack {
            List(collections) { collection in
                Button {
                    if selected.contains(collection.id) { selected.remove(collection.id) } else { selected.insert(collection.id) }
                } label: {
                    HStack {
                        Text(collection.name)
                        Spacer()
                        if selected.contains(collection.id) { Image(systemName: "checkmark") }
                    }
                }
            }
            .navigationTitle("Add to Collections")
            .toolbar {
                ToolbarItem(placement: .confirmationAction) { Button("Save", action: save) }
                ToolbarItem(placement: .cancellationAction) { Button("Cancel", action: dismiss.callAsFunction) }
            }
        }
        .onAppear {
            selected = Set(collections.compactMap { collection in
                items.contains { $0.collectionId == collection.id && $0.songId == songId && $0.chartType.caseInsensitiveCompare(chartType) == .orderedSame && $0.difficulty.caseInsensitiveCompare(difficulty) == .orderedSame && $0.deletedAt == nil } ? collection.id : nil
            })
        }
    }

    private func save() {
        for collection in collections {
            let existing = items.first { $0.collectionId == collection.id && $0.songId == songId && $0.chartType.caseInsensitiveCompare(chartType) == .orderedSame && $0.difficulty.caseInsensitiveCompare(difficulty) == .orderedSame }
            if selected.contains(collection.id) {
                if let existing { existing.deletedAt = nil; existing.updatedAt = .now; existing.clientUpdatedAt = .now }
                else { modelContext.insert(SongCollectionItem(collectionId: collection.id, songId: songId, chartType: chartType, difficulty: difficulty, position: items.filter { $0.collectionId == collection.id }.count, clientUpdatedAt: .now)) }
            } else if let existing { existing.deletedAt = .now; existing.updatedAt = .now; existing.clientUpdatedAt = .now }
        }
        try? modelContext.save()
        dismiss()
    }
}
