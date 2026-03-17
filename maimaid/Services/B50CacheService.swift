import Foundation
import SwiftData
import SwiftUI
import Combine

@MainActor
final class B50CacheService: ObservableObject {
    static let shared = B50CacheService()
    
    @Published var b50Result: (total: Int, b35: [RatingUtils.RatingEntry], b15: [RatingUtils.RatingEntry]) = (0, [], [])
    @Published var isLoading = false
    @Published var isFirstLoad = true
    
    private var lastCalculationParams: String = ""
    private var songMap: [String: Song] = [:]
    private var allSongs: [Song] = []
    
    private init() {}
    
    /// Pre-warm the song map to make lookups O(1)
    func updateSongs(_ songs: [Song]) {
        if songs.count == allSongs.count && !allSongs.isEmpty {
            return
        }
        self.allSongs = songs
        self.songMap = Dictionary(uniqueKeysWithValues: songs.map { ($0.songIdentifier, $0) })
    }
    
    func getSong(identifier: String) -> Song? {
        return songMap[identifier]
    }
    
    /// Triggers calculation if parameters have changed or if forced.
    /// Returns true if a calculation was performed.
    @discardableResult
    func calculateIfNeeded(
        modelContext: ModelContext,
        activeProfile: UserProfile?,
        configs: [SyncConfig],
        overriddenVersion: String?,
        useFitDiff: Bool = false,
        force: Bool = false
    ) async -> Bool {
        let profileId = activeProfile?.id
        let profileIdString = profileId?.uuidString ?? "none"
        let b35Limit = activeProfile?.b35Count ?? configs.first?.b35Count ?? 35
        let b15Limit = activeProfile?.b15Count ?? configs.first?.b15Count ?? 15
        let version = overriddenVersion ?? "auto"
        
        let targetProfileId = activeProfile?.id
        let scoreCountPredicate: Predicate<Score>
        if let targetId = targetProfileId {
            scoreCountPredicate = #Predicate<Score> { $0.userProfileId == targetId }
        } else {
            scoreCountPredicate = #Predicate<Score> { $0.userProfileId == nil }
        }
        
        let scoreCount = (try? modelContext.fetchCount(FetchDescriptor<Score>(predicate: scoreCountPredicate))) ?? 0
        
        var latestScoreDescriptor = FetchDescriptor<Score>(
            predicate: scoreCountPredicate,
            sortBy: [SortDescriptor(\.achievementDate, order: .reverse)]
        )
        latestScoreDescriptor.fetchLimit = 1
        
        let latestScore = (try? modelContext.fetch(latestScoreDescriptor))?.first
        let latestUpdate = latestScore?.achievementDate.timeIntervalSince1970 ?? 0
        
        let params = "\(profileIdString)_\(b35Limit)_\(b15Limit)_\(version)_\(scoreCount)_\(latestUpdate)_fitDiff_\(useFitDiff)"
        
        if !force && params == lastCalculationParams && !isFirstLoad {
            return false
        }
        
        isLoading = true
        lastCalculationParams = params
        
        let profileIdVal = activeProfile?.id
        let server = activeProfile.flatMap { GameServer(rawValue: $0.server) }
        let scoreMap = ScoreService.shared.scoreMap(context: modelContext)
        
        let input = allSongs.toCalculationInput(
            userProfileId: profileIdVal,
            server: server,
            preloadedScores: scoreMap,
            useFitDiff: useFitDiff
        )
        
        // Use effective version logic
        let latestVersion: String?
        if let overridden = overriddenVersion {
            latestVersion = overridden
        } else if let srv = server {
            latestVersion = ServerVersionService.shared.latestVersion(for: srv, songs: allSongs)
        } else {
            latestVersion = nil
        }
        
        let result = await Task.detached(priority: .userInitiated) {
            await RatingUtils.calculateB50(input: input, b35Count: b35Limit, b15Count: b15Limit, latestVersion: latestVersion)
        }.value
        
        self.b50Result = result
        self.isLoading = false
        self.isFirstLoad = false
        
        return true
    }
}
