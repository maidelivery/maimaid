import Foundation
import SwiftData
import UIKit

struct CloudSnapshot {
    let profiles: [CloudSnapshotProfile]
    let scoresByProfileId: [UUID: [CloudSnapshotScore]]
    let recordsByProfileId: [UUID: [CloudSnapshotPlayRecord]]
}

struct CloudSnapshotProfile: Identifiable {
    let id: UUID
    let name: String
    let server: String
    let avatarUrl: String?
    let isActive: Bool
    let playerRating: Int
    let plate: String?
    let dfUsername: String
    let b35Count: Int
    let b15Count: Int
    let b35RecLimit: Int
    let b15RecLimit: Int
    let createdAt: Date
    let lastImportDateDf: Date?
    let lastImportDateLxns: Date?
}

struct CloudSnapshotSheet {
    let songIdentifier: String
    let songId: Int
    let chartType: String
    let difficulty: String
}

struct CloudSnapshotScore {
    let profileId: UUID
    let achievements: Double
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let achievedAt: Date
    let sheet: CloudSnapshotSheet?
}

struct CloudSnapshotPlayRecord {
    let profileId: UUID
    let achievements: Double
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let playTime: Date
    let sheet: CloudSnapshotSheet?
}

private struct BackendProfilesResponse: Decodable {
    let profiles: [BackendRemoteProfile]
}

private struct BackendProfileUpsertResponse: Decodable {
    let profile: BackendRemoteProfile
}

private struct BackendRemoteProfile: Codable {
    let id: String
    let name: String
    let server: String
    let avatarUrl: String?
    let isActive: Bool
    let playerRating: Int
    let plate: String?
    let dfUsername: String
    let b35Count: Int
    let b15Count: Int
    let b35RecLimit: Int
    let b15RecLimit: Int
    let createdAt: Date
    let lastImportDateDf: Date?
    let lastImportDateLxns: Date?
}

private struct BackendScoresResponse: Decodable {
    let scores: [BackendRemoteScore]
}

private struct BackendPlayRecordsResponse: Decodable {
    let records: [BackendRemotePlayRecord]
}

private struct BackendOverwriteAck: Decodable {}

private struct BackendRemoteSheet: Decodable {
    let songIdentifier: String
    let songId: Int
    let chartType: String
    let difficulty: String
    let song: BackendRemoteSong?
}

private struct BackendRemoteSong: Decodable {
    let title: String
}

private struct BackendRemoteScore: Decodable {
    let profileId: String
    let achievements: BackendSyncFlexibleDouble
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let achievedAt: Date
    let sheet: BackendRemoteSheet?
}

private struct BackendRemotePlayRecord: Decodable {
    let profileId: String
    let achievements: BackendSyncFlexibleDouble
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let playTime: Date
    let sheet: BackendRemoteSheet?
}

private struct BackendProfileUpsertRequest: Encodable {
    let profileId: String
    let name: String
    let server: String
    let isActive: Bool
    let playerRating: Int
    let plate: String?
    let avatarUrl: String?
    let dfUsername: String
    let b35Count: Int
    let b15Count: Int
    let b35RecLimit: Int
    let b15RecLimit: Int
    let createdAt: Date
}

private struct BackendScoreUpsertEntry: Encodable {
    let songIdentifier: String?
    let songId: Int?
    let title: String?
    let type: String?
    let difficulty: String?
    let levelIndex: Int?
    let achievements: Double
    let rank: String?
    let dxScore: Int
    let fc: String?
    let fs: String?
    let achievedAt: String?
}

private struct BackendPlayRecordUpsertEntry: Encodable {
    let songIdentifier: String?
    let songId: Int?
    let title: String?
    let type: String?
    let difficulty: String?
    let levelIndex: Int?
    let achievements: Double
    let rank: String?
    let dxScore: Int
    let fc: String?
    let fs: String?
    let playTime: String?
}

private struct BackendScoresOverwriteRequest: Encodable {
    let profileId: String
    let scores: [BackendScoreUpsertEntry]
}

private struct BackendPlayRecordsOverwriteRequest: Encodable {
    let profileId: String
    let records: [BackendPlayRecordUpsertEntry]
}

private struct BackendAvatarUploadUrlRequest: Encodable {
    let contentType: String
}

private struct BackendAvatarUploadUrlResponse: Decodable {
    let key: String
    let uploadUrl: String
}

@MainActor
enum BackendCloudSyncService {
    private static let maxAvatarUploadBytes = 2 * 1024 * 1024
    private static let maxAvatarDimension: CGFloat = 1024

    static func backupToCloud(context: ModelContext) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let profiles = try context.fetch(FetchDescriptor<UserProfile>())
        let sheets = try context.fetch(FetchDescriptor<Sheet>())

        let activeProfileId = ScoreService.shared.currentActiveProfileId(context: context)
        if let activeProfileId {
            var backfilled = false
            let orphanScores = try context.fetch(
                FetchDescriptor<Score>(
                    predicate: #Predicate<Score> { $0.userProfileId == nil }
                )
            )
            for score in orphanScores {
                score.userProfileId = activeProfileId
                backfilled = true
            }
            let orphanRecords = try context.fetch(
                FetchDescriptor<PlayRecord>(
                    predicate: #Predicate<PlayRecord> { $0.userProfileId == nil }
                )
            )
            for record in orphanRecords {
                record.userProfileId = activeProfileId
                backfilled = true
            }
            if backfilled {
                try context.save()
                ScoreService.shared.notifyScoresChanged(for: activeProfileId)
            }
        }

        let scoreSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["-", "_"])

        for profile in profiles {
            let profileId = profile.id.uuidString.lowercased()
            let resolvedAvatarURL = try await uploadAvatarIfNeeded(for: profile)
            let upsertPayload = BackendProfileUpsertRequest(
                profileId: profileId,
                name: profile.name,
                server: profile.server,
                isActive: profile.isActive,
                playerRating: profile.playerRating,
                plate: profile.plate,
                avatarUrl: resolvedAvatarURL,
                dfUsername: profile.dfUsername,
                b35Count: profile.b35Count,
                b15Count: profile.b15Count,
                b35RecLimit: profile.b35RecLimit,
                b15RecLimit: profile.b15RecLimit,
                createdAt: profile.createdAt
            )

            let _: BackendProfileUpsertResponse = try await BackendAPIClient.request(
                path: "v1/profiles/upsert",
                method: "POST",
                body: upsertPayload,
                authentication: .required
            )

            let profileIdValue = profile.id
            let profileScores = try context.fetch(
                FetchDescriptor<Score>(
                    predicate: #Predicate<Score> { $0.userProfileId == profileIdValue }
                )
            )
            let profileRecords = try context.fetch(
                FetchDescriptor<PlayRecord>(
                    predicate: #Predicate<PlayRecord> { $0.userProfileId == profileIdValue }
                )
            )

            let scoreEntries = profileScores.map { score in
                buildScoreEntry(from: score, sheetMap: scoreSheetMap)
            }
            let recordEntries = profileRecords.map { record in
                buildPlayRecordEntry(from: record, sheetMap: recordSheetMap)
            }

            let _: BackendOverwriteAck = try await BackendAPIClient.request(
                path: "v1/scores/overwrite",
                method: "POST",
                body: BackendScoresOverwriteRequest(profileId: profileId, scores: scoreEntries),
                authentication: .required
            )

            let _: BackendOverwriteAck = try await BackendAPIClient.request(
                path: "v1/scores/play-records/overwrite",
                method: "POST",
                body: BackendPlayRecordsOverwriteRequest(profileId: profileId, records: recordEntries),
                authentication: .required
            )
        }

        let config = ensureSyncConfig(context: context)
        config.lastCloudBackupDate = Date.now
        try context.save()
    }

    static func fetchCloudSnapshot() async throws -> CloudSnapshot {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let profileResponse: BackendProfilesResponse = try await BackendAPIClient.request(
            path: "v1/profiles",
            method: "GET",
            authentication: .required
        )

        var profiles: [CloudSnapshotProfile] = []
        var scoresByProfileId: [UUID: [CloudSnapshotScore]] = [:]
        var recordsByProfileId: [UUID: [CloudSnapshotPlayRecord]] = [:]

        for remote in profileResponse.profiles {
            guard let profileId = UUID(uuidString: remote.id) else { continue }
            profiles.append(
                CloudSnapshotProfile(
                    id: profileId,
                    name: remote.name,
                    server: remote.server,
                    avatarUrl: remote.avatarUrl,
                    isActive: remote.isActive,
                    playerRating: remote.playerRating,
                    plate: remote.plate,
                    dfUsername: remote.dfUsername,
                    b35Count: remote.b35Count,
                    b15Count: remote.b15Count,
                    b35RecLimit: remote.b35RecLimit,
                    b15RecLimit: remote.b15RecLimit,
                    createdAt: remote.createdAt,
                    lastImportDateDf: remote.lastImportDateDf,
                    lastImportDateLxns: remote.lastImportDateLxns
                )
            )

            let escapedProfileId = remote.id.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? remote.id

            let scoresResponse: BackendScoresResponse = try await BackendAPIClient.request(
                path: "v1/scores?profileId=\(escapedProfileId)",
                method: "GET",
                authentication: .required
            )

            let recordsResponse: BackendPlayRecordsResponse = try await BackendAPIClient.request(
                path: "v1/scores/play-records?profileId=\(escapedProfileId)&limit=5000",
                method: "GET",
                authentication: .required
            )

            scoresByProfileId[profileId] = scoresResponse.scores.compactMap { mapRemoteScore($0) }
            recordsByProfileId[profileId] = recordsResponse.records.compactMap { mapRemoteRecord($0) }
        }

        return CloudSnapshot(
            profiles: profiles,
            scoresByProfileId: scoresByProfileId,
            recordsByProfileId: recordsByProfileId
        )
    }

    static func uploadAvatarIfNeeded(for profile: UserProfile) async throws -> String? {
        guard let avatarData = profile.avatarData, !avatarData.isEmpty else {
            return profile.avatarUrl
        }

        let optimizedAvatar = optimizeAvatarForUpload(avatarData)

        let profileId = profile.id.uuidString.lowercased()
        let escapedProfileId = profileId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? profileId
        let uploadResponse: BackendAvatarUploadUrlResponse = try await BackendAPIClient.request(
            path: "v1/profiles/\(escapedProfileId)/avatar/upload-url",
            method: "POST",
            body: BackendAvatarUploadUrlRequest(contentType: optimizedAvatar.contentType),
            authentication: .required
        )

        try await uploadAvatarData(optimizedAvatar.data, to: uploadResponse.uploadUrl, contentType: optimizedAvatar.contentType)

        guard let avatarURL = BackendConfig.endpoint("v1/profiles/\(profileId)/avatar")?.absoluteString else {
            throw BackendAPIError.unconfigured
        }
        profile.avatarUrl = avatarURL
        return avatarURL
    }

    private static func optimizeAvatarForUpload(_ rawData: Data) -> (data: Data, contentType: String) {
        guard
            let image = UIImage(data: rawData),
            let resizedImage = resizedImageForUpload(from: image)
        else {
            return (rawData, "image/png")
        }

        if
            let pngData = resizedImage.pngData(),
            pngData.count <= maxAvatarUploadBytes
        {
            return (pngData, "image/png")
        }

        let jpegQualities: [CGFloat] = [0.9, 0.8, 0.7, 0.6, 0.5, 0.4, 0.3]
        var bestJPEG: Data?
        for quality in jpegQualities {
            guard let jpegData = resizedImage.jpegData(compressionQuality: quality) else {
                continue
            }
            bestJPEG = jpegData
            if jpegData.count <= maxAvatarUploadBytes {
                return (jpegData, "image/jpeg")
            }
        }

        if let bestJPEG {
            return (bestJPEG, "image/jpeg")
        }
        return (rawData, "image/png")
    }

    private static func resizedImageForUpload(from image: UIImage) -> UIImage? {
        let originalSize = image.size
        guard originalSize.width > 0, originalSize.height > 0 else {
            return image
        }

        let longestEdge = max(originalSize.width, originalSize.height)
        if longestEdge <= maxAvatarDimension {
            return image
        }

        let scale = maxAvatarDimension / longestEdge
        let targetSize = CGSize(
            width: floor(originalSize.width * scale),
            height: floor(originalSize.height * scale)
        )

        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.preferredRange = .standard
        format.opaque = false

        let renderer = UIGraphicsImageRenderer(size: targetSize, format: format)
        return renderer.image { _ in
            image.draw(in: CGRect(origin: .zero, size: targetSize))
        }
    }

    private static func uploadAvatarData(_ avatarData: Data, to uploadUrlString: String, contentType: String) async throws {
        guard let uploadURL = URL(string: uploadUrlString) else {
            throw BackendAPIError.badResponse
        }

        var request = URLRequest(url: uploadURL)
        request.httpMethod = "PUT"
        request.setValue(contentType, forHTTPHeaderField: "Content-Type")
        request.setValue(String(avatarData.count), forHTTPHeaderField: "Content-Length")

        let (_, response) = try await URLSession.shared.upload(for: request, from: avatarData)
        guard let httpResponse = response as? HTTPURLResponse else {
            throw BackendAPIError.badResponse
        }
        guard (200...299).contains(httpResponse.statusCode) else {
            throw BackendAPIError(statusCode: httpResponse.statusCode, code: nil, message: "Avatar upload failed.")
        }
    }

    static func restoreFromCloud(context: ModelContext) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let profileResponse: BackendProfilesResponse = try await BackendAPIClient.request(
            path: "v1/profiles",
            method: "GET",
            authentication: .required
        )

        let sheets = try context.fetch(FetchDescriptor<Sheet>())
        let scoreSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["_", "-"])
        let recordSheetMap = BackendSyncShared.buildSheetMap(for: sheets, separators: ["-", "_"])

        let existingProfiles = try context.fetch(FetchDescriptor<UserProfile>())
        let existingProfileMap = Dictionary(uniqueKeysWithValues: existingProfiles.map { ($0.id.uuidString.lowercased(), $0) })
        let remoteProfiles = profileResponse.profiles

        var fetchedProfileIds = Set<UUID>()

        for remote in remoteProfiles {
            guard let profileId = UUID(uuidString: remote.id) else { continue }
            fetchedProfileIds.insert(profileId)
            let targetProfile: UserProfile
            if let existing = existingProfileMap[remote.id.lowercased()] {
                existing.name = remote.name
                existing.server = remote.server
                existing.avatarUrl = remote.avatarUrl
                existing.isActive = remote.isActive
                existing.playerRating = remote.playerRating
                existing.plate = remote.plate
                existing.dfUsername = remote.dfUsername
                existing.b35Count = remote.b35Count
                existing.b15Count = remote.b15Count
                existing.b35RecLimit = remote.b35RecLimit
                existing.b15RecLimit = remote.b15RecLimit
                existing.lastImportDateDF = remote.lastImportDateDf
                existing.lastImportDateLXNS = remote.lastImportDateLxns
                targetProfile = existing
            } else {
                let newProfile = UserProfile(
                    id: profileId,
                    name: remote.name,
                    server: remote.server,
                    avatarData: nil,
                    avatarUrl: remote.avatarUrl,
                    isActive: remote.isActive,
                    createdAt: remote.createdAt,
                    dfUsername: remote.dfUsername,
                    playerRating: remote.playerRating,
                    plate: remote.plate,
                    lastImportDateDF: remote.lastImportDateDf,
                    lastImportDateLXNS: remote.lastImportDateLxns,
                    b35Count: remote.b35Count,
                    b15Count: remote.b15Count,
                    b35RecLimit: remote.b35RecLimit,
                    b15RecLimit: remote.b15RecLimit
                )
                context.insert(newProfile)
                targetProfile = newProfile
            }

            if let avatarData = await BackendSyncShared.downloadAvatarData(from: remote.avatarUrl) {
                targetProfile.avatarData = avatarData
            } else if remote.avatarUrl == nil {
                targetProfile.avatarData = nil
            }
        }

        if !fetchedProfileIds.isEmpty {
            try deleteLocalScoresAndRecords(context: context, profileIds: fetchedProfileIds)
        }

        for remote in remoteProfiles {
            guard let profileId = UUID(uuidString: remote.id) else { continue }
            let escapedProfileId = remote.id.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? remote.id

            let scoresResponse: BackendScoresResponse = try await BackendAPIClient.request(
                path: "v1/scores?profileId=\(escapedProfileId)",
                method: "GET",
                authentication: .required
            )

            let recordsResponse: BackendPlayRecordsResponse = try await BackendAPIClient.request(
                path: "v1/scores/play-records?profileId=\(escapedProfileId)&limit=5000",
                method: "GET",
                authentication: .required
            )

            for remoteRecord in recordsResponse.records {
                guard
                    let remoteSheet = remoteRecord.sheet,
                    let sheet = BackendSyncShared.resolveSheet(
                        songIdentifier: remoteSheet.songIdentifier,
                        songId: remoteSheet.songId,
                        chartType: remoteSheet.chartType,
                        difficulty: remoteSheet.difficulty,
                        sheetMap: recordSheetMap
                    )
                else { continue }
                let playRecord = PlayRecord(
                    sheetId: BackendSyncShared.canonicalRecordSheetId(for: sheet),
                    rate: remoteRecord.achievements.value,
                    rank: remoteRecord.rank,
                    dxScore: remoteRecord.dxScore,
                    fc: remoteRecord.fc,
                    fs: remoteRecord.fs,
                    playDate: remoteRecord.playTime,
                    userProfileId: profileId
                )
                playRecord.sheet = sheet
                context.insert(playRecord)
                if sheet.playRecords == nil {
                    sheet.playRecords = []
                }
                sheet.playRecords?.append(playRecord)
            }

            for remoteScore in scoresResponse.scores {
                guard
                    let remoteSheet = remoteScore.sheet,
                    let sheet = BackendSyncShared.resolveSheet(
                        songIdentifier: remoteSheet.songIdentifier,
                        songId: remoteSheet.songId,
                        chartType: remoteSheet.chartType,
                        difficulty: remoteSheet.difficulty,
                        sheetMap: scoreSheetMap
                    )
                else { continue }
                let score = Score(
                    sheetId: BackendSyncShared.canonicalScoreSheetId(for: sheet),
                    rate: remoteScore.achievements.value,
                    rank: remoteScore.rank,
                    dxScore: remoteScore.dxScore,
                    fc: remoteScore.fc,
                    fs: remoteScore.fs,
                    achievementDate: remoteScore.achievedAt,
                    userProfileId: profileId
                )
                score.sheet = sheet
                context.insert(score)
                sheet.scores.append(score)
            }
        }

        ScoreService.shared.repairDetachedRecordsIfNeeded(context: context, force: true)
        try context.save()
        ScoreService.shared.invalidateAllCaches()
    }

    private static func ensureSyncConfig(context: ModelContext) -> SyncConfig {
        if let config = try? context.fetch(FetchDescriptor<SyncConfig>()).first {
            return config
        }
        let config = SyncConfig()
        context.insert(config)
        return config
    }

    private static func deleteLocalScoresAndRecords(
        context: ModelContext,
        profileIds: Set<UUID>
    ) throws {
        for profileId in profileIds {
            let scoreDescriptor = FetchDescriptor<Score>(
                predicate: #Predicate<Score> { $0.userProfileId == profileId }
            )
            let localScores = try context.fetch(scoreDescriptor)
            for score in localScores {
                context.delete(score)
            }

            let recordDescriptor = FetchDescriptor<PlayRecord>(
                predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId }
            )
            let localRecords = try context.fetch(recordDescriptor)
            for record in localRecords {
                context.delete(record)
            }
        }
        try context.save()
    }

    private static func buildScoreEntry(
        from score: Score,
        sheetMap: [String: Sheet]
    ) -> BackendScoreUpsertEntry {
        let sheet = score.sheet ?? sheetMap[score.sheetId]
        let chartType = sheet?.type.lowercased()
        let difficulty = sheet?.difficulty.lowercased()
        let levelIndex = sheet.map { ThemeUtils.mapDifficultyToIndex($0.difficulty) }
        let songId = sheet.map { $0.songId > 0 ? $0.songId : nil } ?? nil

        return BackendScoreUpsertEntry(
            songIdentifier: sheet?.songIdentifier,
            songId: songId,
            title: sheet?.song?.title,
            type: chartType,
            difficulty: difficulty,
            levelIndex: levelIndex,
            achievements: score.rate,
            rank: score.rank,
            dxScore: score.dxScore,
            fc: score.fc,
            fs: score.fs,
            achievedAt: score.achievementDate.ISO8601Format()
        )
    }

    private static func buildPlayRecordEntry(
        from record: PlayRecord,
        sheetMap: [String: Sheet]
    ) -> BackendPlayRecordUpsertEntry {
        let sheet = record.sheet ?? sheetMap[record.sheetId]
        let chartType = sheet?.type.lowercased()
        let difficulty = sheet?.difficulty.lowercased()
        let levelIndex = sheet.map { ThemeUtils.mapDifficultyToIndex($0.difficulty) }
        let songId = sheet.map { $0.songId > 0 ? $0.songId : nil } ?? nil

        return BackendPlayRecordUpsertEntry(
            songIdentifier: sheet?.songIdentifier,
            songId: songId,
            title: sheet?.song?.title,
            type: chartType,
            difficulty: difficulty,
            levelIndex: levelIndex,
            achievements: record.rate,
            rank: record.rank,
            dxScore: record.dxScore,
            fc: record.fc,
            fs: record.fs,
            playTime: record.playDate.ISO8601Format()
        )
    }


    private static func mapRemoteSheet(_ sheet: BackendRemoteSheet?) -> CloudSnapshotSheet? {
        guard let sheet else {
            return nil
        }
        return CloudSnapshotSheet(
            songIdentifier: sheet.songIdentifier,
            songId: sheet.songId,
            chartType: sheet.chartType,
            difficulty: sheet.difficulty
        )
    }

    private static func mapRemoteScore(_ remoteScore: BackendRemoteScore) -> CloudSnapshotScore? {
        guard let profileId = UUID(uuidString: remoteScore.profileId) else {
            return nil
        }
        return CloudSnapshotScore(
            profileId: profileId,
            achievements: remoteScore.achievements.value,
            rank: remoteScore.rank,
            dxScore: remoteScore.dxScore,
            fc: remoteScore.fc,
            fs: remoteScore.fs,
            achievedAt: remoteScore.achievedAt,
            sheet: mapRemoteSheet(remoteScore.sheet)
        )
    }

    private static func mapRemoteRecord(_ remoteRecord: BackendRemotePlayRecord) -> CloudSnapshotPlayRecord? {
        guard let profileId = UUID(uuidString: remoteRecord.profileId) else {
            return nil
        }
        return CloudSnapshotPlayRecord(
            profileId: profileId,
            achievements: remoteRecord.achievements.value,
            rank: remoteRecord.rank,
            dxScore: remoteRecord.dxScore,
            fc: remoteRecord.fc,
            fs: remoteRecord.fs,
            playTime: remoteRecord.playTime,
            sheet: mapRemoteSheet(remoteRecord.sheet)
        )
    }

}
