import SwiftData
import SwiftUI

struct OtogameImportView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(filter: #Predicate<UserProfile> { $0.isActive == true }) private var activeProfiles: [UserProfile]
    @State private var viewModel = OtogameImportViewModel()

    private var activeProfile: UserProfile? {
        activeProfiles.first
    }

    var body: some View {
        List {
            Section {
                OtogameSessionStatusRow(
                    profile: activeProfile,
                    hasSession: viewModel.hasSession
                )
            } footer: {
                Text("import.otogame.description")
            }

            Section {
                NavigationLink {
                    OtogameLoginView(viewModel: viewModel)
                } label: {
                    Label("import.otogame.action.openLogin", systemImage: "person.crop.circle.badge.checkmark")
                }
                .disabled(activeProfile.map { !OtogameImportPolicy.isEligibleServer($0.server) } ?? true)

                Button("import.otogame.action.sync", systemImage: "arrow.triangle.2.circlepath") {
                    guard let activeProfile else {
                        return
                    }
                    Task {
                        await viewModel.synchronize(profile: activeProfile, context: modelContext)
                    }
                }
                .disabled(
                    activeProfile.map { !OtogameImportPolicy.isEligibleServer($0.server) } ?? true
                    || !viewModel.hasSession
                    || viewModel.isImporting
                )
            }

            if viewModel.isImporting || viewModel.outcome != nil {
                OtogameImportStatusSection(
                    isImporting: viewModel.isImporting,
                    outcome: viewModel.outcome,
                    result: viewModel.result,
                    currentPage: viewModel.currentPage,
                    totalPages: viewModel.totalPages
                )
            }
        }
        .navigationTitle("import.otogame.title")
        .navigationBarTitleDisplayMode(.inline)
    }
}

private struct OtogameSessionStatusRow: View {
    let profile: UserProfile?
    let hasSession: Bool

    var body: some View {
        HStack {
            Label {
                VStack(alignment: .leading) {
                    Text(hasSession ? "import.otogame.session.ready" : "import.otogame.session.required")
                    if let profile {
                        Text("import.otogame.activeProfile \(profile.name)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            } icon: {
                Image(systemName: hasSession ? "checkmark.circle.fill" : "person.crop.circle.badge.exclamationmark")
                    .foregroundStyle(hasSession ? .green : .secondary)
            }
            Spacer()
        }
    }
}

private struct OtogameImportStatusSection: View {
    let isImporting: Bool
    let outcome: OtogameImportOutcome?
    let result: OtogameImportResult?
    let currentPage: Int
    let totalPages: Int

    var body: some View {
        Section("import.otogame.status.header") {
            if isImporting {
                HStack {
                    ProgressView()
                    Text("import.otogame.action.syncing")
                        .foregroundStyle(.secondary)
                }
                if totalPages > 0 {
                    VStack(alignment: .leading) {
                        ProgressView(value: Double(currentPage), total: Double(totalPages))
                        Text("import.otogame.progress \(currentPage) \(totalPages)")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                }
            } else if let outcome {
                Label {
                    Text(messageKey(for: outcome))
                } icon: {
                    Image(systemName: isFailure(outcome) ? "xmark.circle.fill" : "checkmark.circle.fill")
                }
                .foregroundStyle(isFailure(outcome) ? .red : .green)
            }

            if let result {
                Text(
                    "import.otogame.result.counts \(result.fetchedCount) \(result.importedCount) \(result.duplicateCount) \(result.unmatchedCount)"
                )
                .font(.caption)
                .foregroundStyle(.secondary)
            }
        }
    }

    private func messageKey(for outcome: OtogameImportOutcome) -> LocalizedStringKey {
        switch outcome {
        case .imported: "import.otogame.result.imported"
        case .noChanges: "import.otogame.result.noChanges"
        case .loginRequired: "import.otogame.result.loginRequired"
        case .japaneseProfileRequired: "import.otogame.profileRequired"
        case .failed: "import.otogame.result.failed"
        }
    }

    private func isFailure(_ outcome: OtogameImportOutcome) -> Bool {
        switch outcome {
        case .imported, .noChanges: false
        case .loginRequired, .japaneseProfileRequired, .failed: true
        }
    }
}
