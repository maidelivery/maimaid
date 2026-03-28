import Foundation
import SwiftData
import SwiftUI

@MainActor
@Observable
final class B50CacheService {
    static let shared = B50CacheService()

    var b50Result: (total: Int, b35: [RatingUtils.RatingEntry], b15: [RatingUtils.RatingEntry]) = (0, [], [])
    var isLoading = false
    var isFirstLoad = true
    
    private var lastCalculationParams: String = ""
    private var songMap: [String: Song] = [:]
    private var allSongs: [Song] = []
    
    // MARK: - Cached fingerprints / results
    
    private var lastScoreFingerprintByProfile: [String: String] = [:]
    private var lastScoreMapByProfile: [String: [String: Score]] = [:]
    private var currentCalculationTask: Task<(total: Int, b35: [RatingUtils.RatingEntry], b15: [RatingUtils.RatingEntry]), Never>?
    
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
    
    // MARK: - Score Snapshot
    
    private func buildScoreSnapshot(
        modelContext: ModelContext,
        profileId: UUID?
    ) -> (fingerprint: String, scoreMap: [String: Score]) {
        let profileKey = profileId?.uuidString ?? "none"
        
        let scores: [Score]
        if let profileId {
            let descriptor = FetchDescriptor<Score>(
                predicate: #Predicate<Score> { $0.userProfileId == profileId }
            )
            scores = (try? modelContext.fetch(descriptor)) ?? []
        } else {
            let descriptor = FetchDescriptor<Score>(
                predicate: #Predicate<Score> { $0.userProfileId == nil }
            )
            scores = (try? modelContext.fetch(descriptor)) ?? []
        }
        
        var scoreMap: [String: Score] = [:]
        scoreMap.reserveCapacity(scores.count)
        
        var latestUpdate: TimeInterval = 0
        var totalRate: Double = 0
        var totalDxScore: Int = 0
        
        for score in scores {
            scoreMap[score.sheetId] = score
            totalRate += score.rate
            totalDxScore += score.dxScore
            latestUpdate = max(latestUpdate, score.achievementDate.timeIntervalSince1970)
        }
        
        let fingerprint = "\(scores.count)_\(String(format: "%.4f", totalRate))_\(totalDxScore)_\(Int(latestUpdate))"
        
        lastScoreFingerprintByProfile[profileKey] = fingerprint
        lastScoreMapByProfile[profileKey] = scoreMap
        
        return (fingerprint, scoreMap)
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
        let profileKey = profileId?.uuidString ?? "none"
        let b35Limit = activeProfile?.b35Count ?? configs.first?.b35Count ?? 35
        let b15Limit = activeProfile?.b15Count ?? configs.first?.b15Count ?? 15
        let version = overriddenVersion ?? "auto"
        let serverRaw = activeProfile?.server ?? "none"
        let songCount = allSongs.count
        
        // Single score fetch instead of fetchCount + latest fetch + scoreMap rebuild
        let snapshot = buildScoreSnapshot(modelContext: modelContext, profileId: profileId)
        
        let params = [
            profileKey,
            serverRaw,
            "\(songCount)",
            "\(b35Limit)",
            "\(b15Limit)",
            version,
            "fitDiff:\(useFitDiff)",
            "scores:\(snapshot.fingerprint)"
        ].joined(separator: "|")
        
        if !force && params == lastCalculationParams && !isFirstLoad {
            return false
        }
        
        lastCalculationParams = params
        isLoading = true
        
        currentCalculationTask?.cancel()
        
        let server = activeProfile.flatMap { GameServer(rawValue: $0.server) }
        let latestVersion: String?
        if let overriddenVersion {
            latestVersion = overriddenVersion
        } else if let server {
            latestVersion = ServerVersionService.shared.latestVersion(for: server, songs: allSongs)
        } else {
            latestVersion = nil
        }
        
        let input = await allSongs.toCalculationInput(
            userProfileId: profileId,
            server: server,
            preloadedScores: snapshot.scoreMap,
            useFitDiff: useFitDiff
        )
        
        let task = Task<(total: Int, b35: [RatingUtils.RatingEntry], b15: [RatingUtils.RatingEntry]), Never> {
            await RatingUtils.calculateB50(
                input: input,
                b35Count: b35Limit,
                b15Count: b15Limit,
                latestVersion: latestVersion
            )
        }
        
        currentCalculationTask = task
        let result = await task.value
        
        guard !Task.isCancelled else { return false }
        guard currentCalculationTask?.isCancelled != true else { return false }
        
        // Ensure no newer params replaced this calculation while awaiting
        if lastCalculationParams != params {
            return false
        }
        
        self.b50Result = result
        self.isLoading = false
        self.isFirstLoad = false
        
        return true
    }
    
    // MARK: - Manual invalidation
    
    func invalidate() {
        currentCalculationTask?.cancel()
        currentCalculationTask = nil
        lastCalculationParams = ""
    }
    
    func invalidateScores(for profileId: UUID?) {
        let profileKey = profileId?.uuidString ?? "none"
        lastScoreFingerprintByProfile.removeValue(forKey: profileKey)
        lastScoreMapByProfile.removeValue(forKey: profileKey)
        lastCalculationParams = ""
    }
}
