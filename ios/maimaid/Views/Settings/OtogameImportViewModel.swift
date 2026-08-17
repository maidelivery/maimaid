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
        defer { isImporting = false }

        do {
            let importResult = try await OtogameImportService.shared.importRecent(
                authorizationHeader: authorizationHeader,
                expectedProfileID: profile.id,
                context: context
            )
            result = importResult
            outcome = importResult.importedCount > 0 ? .imported : .noChanges
        } catch OtogameImportError.unauthorized, OtogameImportError.invalidAuthorization {
            self.authorizationHeader = nil
            hasSession = false
            outcome = .loginRequired
        } catch OtogameImportError.japaneseProfileRequired {
            outcome = .japaneseProfileRequired
        } catch {
            outcome = .failed
        }
    }
}
