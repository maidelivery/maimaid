import SwiftData
import SwiftUI

// swiftlint:disable:next type_body_length
struct LetterGameRoomSettingsView: View {
  @Environment(\.dismiss) private var dismiss

  let service: LetterGameService
  let room: LetterGameRoom
  let versions: [String]
  let categories: [String]
  let collections: [SongCollection]
  let collectionItems: [SongCollectionItem]

  @State private var hostMode: String
  @State private var turnSeconds: Int
  @State private var stalledRounds: Int
  @State private var songCount: Int
  @State private var publicHintCost: Int
  @State private var privateHintCost: Int
  @State private var selectionMode: String
  @State private var excludeDeleted: Bool
  @State private var englishOnly: Bool
  @State private var minimumVersionIndex: Double
  @State private var maximumVersionIndex: Double
  @State private var selectedCategories: Set<String>
  @State private var selectedChartTypes: Set<String>
  @State private var selectedCollectionIDs: Set<String>
  @State private var isSaving = false

  init(
    service: LetterGameService,
    room: LetterGameRoom,
    versions: [String],
    categories: [String],
    collections: [SongCollection],
    collectionItems: [SongCollectionItem]
  ) {
    self.service = service
    self.room = room
    self.versions = versions
    self.categories = categories
    self.collections = collections
    self.collectionItems = collectionItems

    let config = room.settings.selectionConfig
    let acceptedCount = max(1, room.members.count(where: { $0.status == "accepted" }))
    _hostMode = State(initialValue: room.hostMode)
    _turnSeconds = State(initialValue: room.settings.turnDurationSeconds)
    _stalledRounds = State(initialValue: room.settings.stalledRoundLimit)
    _songCount = State(initialValue: room.settings.songCountOverride ?? acceptedCount * 3)
    _publicHintCost = State(initialValue: room.settings.publicHintCost)
    _privateHintCost = State(initialValue: room.settings.privateHintCost)
    _selectionMode = State(initialValue: room.settings.selectionMode)
    _excludeDeleted = State(initialValue: config["excludeDeleted"]?.boolValue ?? true)
    _englishOnly = State(initialValue: config["englishOnly"]?.boolValue ?? true)
    let minimumIndex = config["minVersion"]?.stringValue.flatMap(versions.firstIndex(of:)) ?? 0
    let maximumIndex = config["maxVersion"]?.stringValue.flatMap(versions.firstIndex(of:))
      ?? max(0, versions.count - 1)
    _minimumVersionIndex = State(initialValue: Double(minimumIndex))
    _maximumVersionIndex = State(initialValue: Double(maximumIndex))
    _selectedCategories = State(initialValue: Set(config["categories"]?.stringValues ?? []))
    let configuredChartTypes = Set(config["chartTypes"]?.stringValues ?? [])
    _selectedChartTypes = State(
      initialValue: configuredChartTypes.isEmpty ? Set(["standard", "dx"]) : configuredChartTypes
    )
    _selectedCollectionIDs = State(initialValue: Set(config["collectionIds"]?.stringValues ?? []))
  }

  private var canEdit: Bool {
    service.isHost && service.match?.status != "active"
  }

  private var acceptedPlayerCount: Int {
    max(1, room.members.count(where: { $0.status == "accepted" }))
  }

  private var selectedCollectionSongCount: Int {
    Set(
      collectionItems
        .filter { selectedCollectionIDs.contains($0.collectionId.uuidString) }
        .map(\.songId)
    ).count
  }

  private var canSave: Bool {
    canEdit
      && turnSeconds >= 15 && turnSeconds <= 120
      && stalledRounds >= 1 && stalledRounds <= 10
      && publicHintCost >= 1 && publicHintCost <= 100
      && privateHintCost > publicHintCost && privateHintCost <= 100
      && effectiveSongCount >= acceptedPlayerCount && effectiveSongCount <= 5_000
      && (selectionMode != "collection" || !selectedCollectionIDs.isEmpty)
  }

  private var effectiveSongCount: Int {
    selectionMode == "collection" ? selectedCollectionSongCount : songCount
  }

  private var minimumVersion: String? {
    guard !versions.isEmpty else { return nil }
    let index = min(max(Int(minimumVersionIndex.rounded()), 0), versions.count - 1)
    return index == 0 ? nil : versions[index]
  }

  private var maximumVersion: String? {
    guard !versions.isEmpty else { return nil }
    let index = min(max(Int(maximumVersionIndex.rounded()), 0), versions.count - 1)
    return index == versions.count - 1 ? nil : versions[index]
  }

  private var minimumVersionLabel: String {
    guard !versions.isEmpty else { return String(localized: "letterGame.versionsUnavailable") }
    let index = min(max(Int(minimumVersionIndex.rounded()), 0), versions.count - 1)
    return versions[index]
  }

  private var maximumVersionLabel: String {
    guard !versions.isEmpty else { return String(localized: "letterGame.versionsUnavailable") }
    let index = min(max(Int(maximumVersionIndex.rounded()), 0), versions.count - 1)
    return versions[index]
  }

  var body: some View {
    NavigationStack {
      ScrollView {
        VStack(spacing: 24) {
          if !canEdit {
            Label(
              service.match?.status == "active"
                ? "letterGame.settingsLocked"
                : "letterGame.hostOnlySettings",
              systemImage: "lock"
            )
            .foregroundStyle(.secondary)
            .frame(maxWidth: .infinity, alignment: .leading)
          }

          LetterGameSettingsSection(title: "letterGame.roomRules") {
            Picker("letterGame.hostMode", selection: $hostMode) {
              Text("letterGame.hostFixed").tag("fixed")
              Text("letterGame.hostRotate").tag("rotate")
            }
            .pickerStyle(.segmented)

            Stepper(value: $turnSeconds, in: 15...120) {
              LabeledContent("letterGame.turnDuration") {
                Text("letterGame.seconds \(turnSeconds)")
              }
            }
            Stepper(value: $stalledRounds, in: 1...10) {
              LabeledContent("letterGame.stalledRounds") {
                Text(stalledRounds, format: .number)
              }
            }
            if selectionMode == "filtered_random" {
              Stepper(value: $songCount, in: acceptedPlayerCount...5_000) {
                LabeledContent("letterGame.songCount") {
                  Text(songCount, format: .number)
                }
              }
            } else {
              LabeledContent("letterGame.songCount") {
                Text(selectedCollectionSongCount, format: .number)
              }
            }

            if room.visibility == "private" {
              Stepper(value: $publicHintCost, in: 1...100) {
                LabeledContent("letterGame.publicHintCost") {
                  Text(publicHintCost, format: .number)
                }
              }
              Stepper(value: $privateHintCost, in: 1...100) {
                LabeledContent("letterGame.privateHintCost") {
                  Text(privateHintCost, format: .number)
                }
              }
              if privateHintCost <= publicHintCost {
                Label("letterGame.hintCostOrder", systemImage: "exclamationmark.triangle")
                  .foregroundStyle(.red)
              }
            }
          }
          .disabled(!canEdit)

          LetterGameSettingsSection(title: "letterGame.songSource") {
            Picker("letterGame.songSource", selection: $selectionMode) {
              Text("letterGame.sourceRandom").tag("filtered_random")
              Text("letterGame.sourceCollection").tag("collection")
            }
            .pickerStyle(.segmented)
          }
          .disabled(!canEdit)

          if selectionMode == "collection" {
            LetterGameSettingsSection(title: "letterGame.collections") {
              if service.isHost, collections.isEmpty {
                ContentUnavailableView(
                  "letterGame.noCollections",
                  systemImage: "rectangle.stack"
                )
              } else if service.isHost {
                FlowLayout(spacing: 10) {
                  ForEach(collections) { collection in
                    FilterChip(
                      title: "\(collection.name) · \(collectionSongCount(collection.id))",
                      isSelected: selectedCollectionIDs.contains(collection.id.uuidString)
                    ) {
                      toggleSelection(
                        collection.id.uuidString,
                        in: &selectedCollectionIDs
                      )
                    }
                  }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
              } else if room.settings.selectedCollections.isEmpty {
                Text("letterGame.noCollectionsSelected")
                  .foregroundStyle(.secondary)
              } else {
                FlowLayout(spacing: 10) {
                  ForEach(room.settings.selectedCollections) { collection in
                    FilterChip(
                      title: "\(collection.name) · \(collection.songCount)",
                      isSelected: true,
                      action: {}
                    )
                  }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
              }
            }
            .disabled(!canEdit)
          }

          LetterGameSettingsSection(title: "letterGame.filters") {
            Toggle(isOn: $excludeDeleted) {
              Label(
                "letterGame.excludeDeleted",
                systemImage: excludeDeleted ? "eye.slash.fill" : "eye.slash"
              )
            }
            Toggle(isOn: $englishOnly) {
              Label(
                "letterGame.englishOnly",
                systemImage: englishOnly ? "character.book.closed.fill" : "character.book.closed"
              )
            }

          }
          .disabled(!canEdit)

          LetterGameSettingsSection(title: "letterGame.versionRange") {
            HStack {
              Text(minimumVersionLabel)
              Spacer()
              Image(systemName: "arrow.right")
                .foregroundStyle(.tertiary)
                .accessibilityHidden(true)
              Spacer()
              Text(maximumVersionLabel)
            }
            .foregroundStyle(.tint)

            if versions.count > 1 {
              RangeSlider(
                minValue: $minimumVersionIndex,
                maxValue: $maximumVersionIndex,
                range: 0...Double(versions.count - 1),
                step: 1,
                isActive: canEdit
              )
              .padding(.horizontal, 8)
            }
          }
          .disabled(!canEdit)

          LetterGameSettingsSection(title: "letterGame.categories") {
            FlowLayout(spacing: 10) {
              ForEach(categories, id: \.self) { category in
                FilterChip(
                  title: category,
                  isSelected: selectedCategories.contains(category)
                ) {
                  toggleSelection(category, in: &selectedCategories)
                }
              }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
          }
          .disabled(!canEdit)

          LetterGameSettingsSection(title: "letterGame.chartTypes") {
            FlowLayout(spacing: 10) {
              FilterChip(
                title: "STD",
                isSelected: selectedChartTypes.contains("standard")
              ) {
                toggleSelection("standard", in: &selectedChartTypes)
              }
              FilterChip(
                title: "DX",
                isSelected: selectedChartTypes.contains("dx"),
                color: .orange
              ) {
                toggleSelection("dx", in: &selectedChartTypes)
              }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
          }
          .disabled(!canEdit)
        }
        .padding(20)
      }
      .background(.background)
      .navigationTitle("letterGame.settings")
      .navigationBarTitleDisplayMode(.inline)
      .toolbar {
        ToolbarItem(placement: .cancellationAction) {
          Button("letterGame.cancel", action: dismiss.callAsFunction)
        }
        ToolbarItem(placement: .confirmationAction) {
          Button("letterGame.save", action: save)
            .disabled(!canSave || isSaving)
        }
      }
      .overlay {
        if isSaving { ProgressView() }
      }
    }
    .presentationDetents([.medium, .large])
  }

  private func toggleSelection(_ value: String, in selection: inout Set<String>) {
    if selection.contains(value) {
      selection.remove(value)
    } else {
      selection.insert(value)
    }
  }

  private func collectionSongCount(_ collectionID: UUID) -> Int {
    Set(collectionItems.filter { $0.collectionId == collectionID }.map(\.songId)).count
  }

  private func save() {
    let config: [String: LetterGameJSONValue] = [
      "excludeDeleted": .boolean(excludeDeleted),
      "englishOnly": .boolean(englishOnly),
      "minVersion": minimumVersion.map(LetterGameJSONValue.string) ?? .null,
      "maxVersion": maximumVersion.map(LetterGameJSONValue.string) ?? .null,
      "categories": .array(selectedCategories.sorted().map(LetterGameJSONValue.string)),
      "chartTypes": .array(selectedChartTypes.sorted().map(LetterGameJSONValue.string)),
      "collectionIds": .array(selectedCollectionIDs.sorted().map(LetterGameJSONValue.string))
    ]
    let request = LetterGameCreateRequest(
      visibility: room.visibility,
      hostMode: hostMode,
      turnDurationSeconds: turnSeconds,
      stalledRoundLimit: stalledRounds,
      songCount: effectiveSongCount,
      publicHintCost: publicHintCost,
      privateHintCost: privateHintCost,
      selectionMode: selectionMode,
      selectionConfig: config
    )
    isSaving = true
    Task {
      let saved = await service.updateRoom(request)
      isSaving = false
      if saved { dismiss() }
    }
  }
}
