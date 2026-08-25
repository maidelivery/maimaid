import Observation
import SwiftData
import Foundation

enum OtogameImportOutcome {
    case imported
    case noChanges
    case loginRequired
    case japaneseProfileRequired
    case failed
}

@MainActor
@Observable
final class OtogameImportViewModel {
    private(set) var hasSession = false
    private(set) var isImporting = false
    private(set) var outcome: OtogameImportOutcome?
    private(set) var result: OtogameImportResult?
    private(set) var currentPage = 0
    private(set) var totalPages = 0

    @ObservationIgnored
    private var authorizationHeader: String?

    func captureAuthorizationHeader(_ value: String) {
        let header = value.trimmingCharacters(in: .whitespacesAndNewlines)
        guard header.lowercased().hasPrefix("bearer ") else {
            return
        }
        guard authorizationHeader != header else {
            return
        }
        authorizationHeader = header
        hasSession = true
        outcome = nil
    }

    func synchronize(profile: UserProfile, context: ModelContext) async {
        guard !isImporting else {
            return
        }
        guard OtogameImportPolicy.isEligibleServer(profile.server) else {
            outcome = .japaneseProfileRequired
            return
        }
        guard let authorizationHeader else {
            outcome = .loginRequired
            return
        }

        isImporting = true
        outcome = nil
        result = nil
        currentPage = 0
        totalPages = 0
        defer { isImporting = false }

        do {
            let importResult = try await OtogameImportService.shared.importRecent(
                authorizationHeader: authorizationHeader,
                expectedProfileID: profile.id,
                context: context,
                onPageProgress: { [weak self] currentPage, totalPages in
                    self?.currentPage = currentPage
                    self?.totalPages = totalPages
                }
            )
            result = importResult
            outcome = importResult.importedCount > 0 ? .imported : .noChanges
        } catch OtogameImportError.unauthorized, OtogameImportError.invalidAuthorization {
            self.authorizationHeader = nil
            hasSession = false
            outcome = .loginRequired
        } catch OtogameImportError.japaneseProfileRequired {
            outcome = .japaneseProfileRequired
        } catch is CancellationError {
            return
        } catch {
            outcome = .failed
        }
    }
}
