import SwiftUI

struct LetterGameRoomSettingsSummaryView: View {
  let room: LetterGameRoom

  private var acceptedPlayerCount: Int {
    max(1, room.members.count(where: { $0.status == "accepted" }))
  }

  private var songCount: Int {
    room.settings.songCountOverride ?? acceptedPlayerCount * 3
  }

  private var collectionNames: String? {
    let names = room.settings.selectedCollections.map(\.name)
    guard !names.isEmpty else { return nil }
    return names.joined(separator: String(localized: "letterGame.listSeparator"))
  }

  private var sourceName: String {
    if room.settings.selectionMode == "collection" {
      guard let collectionNames else {
        return String(localized: "letterGame.sourceCollection")
      }
      return String(localized: "letterGame.summarySourceCollection \(collectionNames)")
    }
    return String(localized: "letterGame.summarySourceRandom")
  }

  private var filterSummary: String {
    let config = room.settings.selectionConfig
    let separator = String(localized: "letterGame.listSeparator")
    var filters: [String] = []

    if config["excludeDeleted"]?.boolValue != false {
      filters.append(String(localized: "letterGame.excludeDeleted"))
    }
    if config["englishOnly"]?.boolValue != false {
      filters.append(String(localized: "letterGame.englishOnly"))
    }

    let minimumVersion = config["minVersion"]?.stringValue
    let maximumVersion = config["maxVersion"]?.stringValue
    if minimumVersion != nil || maximumVersion != nil {
      let lowerBound = minimumVersion ?? String(localized: "letterGame.earliestSummary")
      let upperBound = maximumVersion ?? String(localized: "letterGame.latestSummary")
      filters.append(
        String(localized: "letterGame.summaryVersionRange \(lowerBound) \(upperBound)")
      )
    }

    let categories = config["categories"]?.stringValues ?? []
    if !categories.isEmpty {
      let categoryNames = categories.joined(separator: separator)
      filters.append(
        String(localized: "letterGame.summaryCategories \(categoryNames)")
      )
    }

    let normalizedChartTypes = (config["chartTypes"]?.stringValues ?? []).compactMap { type in
      switch type.lowercased() {
      case "standard", "std", "sd": "STD"
      case "dx": "DX"
      default: nil
      }
    }
    let chartTypes = ["STD", "DX"].filter(normalizedChartTypes.contains)
    if !chartTypes.isEmpty {
      let chartTypeNames = chartTypes.joined(separator: separator)
      filters.append(
        String(localized: "letterGame.summaryChartTypes \(chartTypeNames)")
      )
    }

    guard !filters.isEmpty else {
      return String(localized: "letterGame.summaryNoFilters")
    }
    let filterNames = filters.joined(separator: separator)
    return String(localized: "letterGame.summaryFilters \(filterNames)")
  }

  private var overview: String {
    let hostMode = room.hostMode == "rotate"
      ? String(localized: "letterGame.summaryHostRotate")
      : String(localized: "letterGame.summaryHostFixed")
    return String(
      // swiftlint:disable:next line_length
      localized: "letterGame.roomSettingsOverview \(hostMode) \(room.settings.turnDurationSeconds) \(room.settings.stalledRoundLimit) \(sourceName) \(songCount) \(filterSummary) \(room.settings.publicHintCost) \(room.settings.privateHintCost)"
    )
  }

  var body: some View {
    Text(overview)
      .foregroundStyle(.secondary)
      .fixedSize(horizontal: false, vertical: true)
      .padding(.vertical, 4)
  }
}
