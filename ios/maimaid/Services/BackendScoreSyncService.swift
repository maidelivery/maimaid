import Foundation

private struct BackendScoreSyncEntry: Encodable {
    let songIdentifier: String?
    let songId: Int?
    let title: String?
    let type: String?
    let difficulty: String?
    let levelIndex: Int?
    let achievements: Double
    let rank: String?
    let dxScore: Int?
    let fc: String?
    let fs: String?
    let achievedAt: String?
}

private struct BackendPlayRecordEntry: Encodable {
    let songIdentifier: String?
    let songId: Int?
    let title: String?
    let type: String?
    let difficulty: String?
    let levelIndex: Int?
    let achievements: Double
    let rank: String?
    let dxScore: Int?
    let fc: String?
    let fs: String?
    let playTime: String?
}

private struct BackendBulkScoreSyncRequest: Encodable {
    let profileId: String
    let scores: [BackendScoreSyncEntry]
}

private struct BackendBulkRecordSyncRequest: Encodable {
    let profileId: String
    let records: [BackendPlayRecordEntry]
}

private struct BackendProfileUpsertPayload: Encodable {
    let profileId: String
    let name: String
    let server: String
    let isActive: Bool
    let playerRating: Int
    let plate: String?
    let avatarUrl: String?
    let dfUsername: String
    let dfImportToken: String
    let lxnsRefreshToken: String
    let b35Count: Int
    let b15Count: Int
    let b35RecLimit: Int
    let b15RecLimit: Int
    let createdAt: Date
}

private struct BackendScoreSyncAck: Decodable {}

enum BackendScoreSyncService {
    static func ensureProfileExists(profile: UserProfile) async throws {
        guard !profile.id.uuidString.isEmpty else {
            throw URLError(.badURL)
        }

        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let profileUpsert = BackendProfileUpsertPayload(
            profileId: profile.id.uuidString.lowercased(),
            name: profile.name,
            server: profile.server,
            isActive: profile.isActive,
            playerRating: profile.playerRating,
            plate: profile.plate,
            avatarUrl: profile.avatarUrl,
            dfUsername: profile.dfUsername,
            dfImportToken: profile.dfImportToken,
            lxnsRefreshToken: profile.lxnsRefreshToken,
            b35Count: profile.b35Count,
            b15Count: profile.b15Count,
            b35RecLimit: profile.b35RecLimit,
            b15RecLimit: profile.b15RecLimit,
            createdAt: profile.createdAt
        )

        let _: BackendScoreSyncAck = try await BackendAPIClient.request(
            path: "v1/profiles/upsert",
            method: "POST",
            body: profileUpsert,
            authentication: .required
        )
    }

    static func uploadScore(profile: UserProfile, sheet: Sheet, score: Score) async throws {
        guard !profile.id.uuidString.isEmpty else {
            throw URLError(.badURL)
        }
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let chartType = sheet.type.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let difficulty = sheet.difficulty.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        let levelIndex = ThemeUtils.mapDifficultyToIndex(sheet.difficulty)
        let songId = sheet.song?.songId ?? 0
        let normalizedSongId = songId > 0 ? songId : nil
        let achievedAt = score.achievementDate.ISO8601Format()
        let playTime = Date.now.ISO8601Format()

        let upsertRequest = BackendBulkScoreSyncRequest(
            profileId: profile.id.uuidString.lowercased(),
            scores: [
                BackendScoreSyncEntry(
                    songIdentifier: sheet.songIdentifier,
                    songId: normalizedSongId,
                    title: sheet.song?.title,
                    type: chartType,
                    difficulty: difficulty,
                    levelIndex: levelIndex,
                    achievements: score.rate,
                    rank: score.rank,
                    dxScore: score.dxScore,
                    fc: score.fc,
                    fs: score.fs,
                    achievedAt: achievedAt
                )
            ]
        )

        let recordRequest = BackendBulkRecordSyncRequest(
            profileId: profile.id.uuidString.lowercased(),
            records: [
                BackendPlayRecordEntry(
                    songIdentifier: sheet.songIdentifier,
                    songId: normalizedSongId,
                    title: sheet.song?.title,
                    type: chartType,
                    difficulty: difficulty,
                    levelIndex: levelIndex,
                    achievements: score.rate,
                    rank: score.rank,
                    dxScore: score.dxScore,
                    fc: score.fc,
                    fs: score.fs,
                    playTime: playTime
                )
            ]
        )

        try await ensureProfileExists(profile: profile)
        let _: BackendScoreSyncAck = try await BackendAPIClient.request(
            path: "v1/scores/bulk-upsert",
            method: "POST",
            body: upsertRequest,
            authentication: .required
        )
        let _: BackendScoreSyncAck = try await BackendAPIClient.request(
            path: "v1/scores/play-records/bulk-upsert",
            method: "POST",
            body: recordRequest,
            authentication: .required
        )
    }
}
