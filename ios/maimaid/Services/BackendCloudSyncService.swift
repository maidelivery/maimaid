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
    let updatedAt: Date
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

struct CloudRestoreLocalProfile: Identifiable {
    let id: UUID
    let name: String
    let server: String
}

private struct BackendProfilesResponse: Decodable {
    let profiles: [BackendRemoteProfile]
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
    let updatedAt: Date
}

private struct BackendScoresResponse: Decodable {
    let scores: [BackendRemoteScore]
}

private struct BackendPlayRecordsResponse: Decodable {
    let records: [BackendRemotePlayRecord]
}

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

private struct BackendAvatarUploadUrlRequest: Encodable {
    let contentType: String
    let clientUpdatedAt: Date?
}

private struct BackendAvatarUploadUrlResponse: Decodable {
    let key: String
    let uploadUrl: String
    let updatedAt: Date
}

private struct BackendProfileActivityPayload: Encodable {
    let isActive: Bool
}

private struct BackendProfileDeleteResponse: Decodable {
    let profileId: String
}

private struct BackendProfilePatchResponse: Decodable {
    let profile: BackendRemoteProfile
}

struct BackendAvatarUploadResult {
    let avatarURL: String?
    let updatedAt: Date?
}

@MainActor
enum BackendCloudSyncService {
    private static let maxAvatarUploadBytes = 2 * 1024 * 1024
    private static let maxAvatarDimension: CGFloat = 1024

    static func backupToCloud(context: ModelContext) async throws {
        try await BackendSyncOperationGate.shared.withLock {
            try await backupToCloudUnlocked(context: context)
        }
    }

    static func backupToCloudUnlocked(context: ModelContext) async throws {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let localProfiles = try context.fetch(FetchDescriptor<UserProfile>())
        guard !localProfiles.isEmpty else {
            return
        }

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

        try await removeRemoteProfilesAbsentLocally(localProfiles: localProfiles)
        try await BackendIncrementalSyncService.pushAllLocalDataUnlocked(
            context: context,
            forceProfileOverwrite: true,
            forceDataUpload: true
        )

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
                    lastImportDateLxns: remote.lastImportDateLxns,
                    updatedAt: remote.updatedAt
                )
            )

            let escapedProfileId = remote.id.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? remote.id

            let scoresResponse: BackendScoresResponse = try await BackendAPIClient.request(
                path: "v1/scores?profileId=\(escapedProfileId)",
                method: "GET",
                authentication: .required
            )

            let recordsResponse: BackendPlayRecordsResponse = try await BackendAPIClient.request(
                path: "v1/play-records?profileId=\(escapedProfileId)&limit=5000",
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

    static func uploadAvatarIfNeeded(
        for profile: UserProfile,
        clientUpdatedAt: Date?
    ) async throws -> BackendAvatarUploadResult {
        guard
            !MaimaiIcon.isPresetAvatarURL(profile.avatarUrl),
            profile.avatarUrl == nil,
            let avatarData = profile.avatarData,
            !avatarData.isEmpty
        else {
            return BackendAvatarUploadResult(avatarURL: profile.avatarUrl, updatedAt: nil)
        }

        let optimizedAvatar = optimizeAvatarForUpload(avatarData)

        let profileId = profile.id.uuidString.lowercased()
        let escapedProfileId = profileId.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? profileId
        let uploadResponse: BackendAvatarUploadUrlResponse = try await BackendAPIClient.request(
            path: "v1/profiles/\(escapedProfileId)/avatar:createUploadUrl",
            method: "POST",
            body: BackendAvatarUploadUrlRequest(
                contentType: optimizedAvatar.contentType,
                clientUpdatedAt: clientUpdatedAt
            ),
            authentication: .required
        )

        if let context = profile.modelContext {
            let config = ensureSyncConfig(context: context)
            config.setRemoteProfileVersions([profileId: uploadResponse.updatedAt])
            try context.save()
        }

        try await uploadAvatarData(
            optimizedAvatar.data, to: uploadResponse.uploadUrl, contentType: optimizedAvatar.contentType)

        guard
            let avatarEndpoint = BackendConfig.endpoint("v1/profiles/\(profileId)/avatar"),
            var avatarURLComponents = URLComponents(url: avatarEndpoint, resolvingAgainstBaseURL: false)
        else {
            throw BackendAPIError.unconfigured
        }
        avatarURLComponents.queryItems = [
            URLQueryItem(name: "v", value: uploadResponse.updatedAt.ISO8601Format())
        ]
        guard let avatarURL = avatarURLComponents.url?.absoluteString else {
            throw BackendAPIError.badResponse
        }
        profile.avatarUrl = avatarURL
        return BackendAvatarUploadResult(avatarURL: avatarURL, updatedAt: uploadResponse.updatedAt)
    }

    private static func removeRemoteProfilesAbsentLocally(localProfiles: [UserProfile]) async throws {
        let localProfileIds = Set(localProfiles.map { $0.id.uuidString.lowercased() })
        let response: BackendProfilesResponse = try await BackendAPIClient.request(
            path: "v1/profiles",
            method: "GET",
            authentication: .required
        )
        let profilesToDelete = response.profiles.filter { !localProfileIds.contains($0.id.lowercased()) }

        for profile in profilesToDelete where profile.isActive {
            let escapedProfileId =
                profile.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? profile.id
            let _: BackendProfilePatchResponse = try await BackendAPIClient.request(
                path: "v1/profiles/\(escapedProfileId)",
                method: "PATCH",
                body: BackendProfileActivityPayload(isActive: false),
                authentication: .required
            )
        }

        for profile in profilesToDelete {
            let escapedProfileId =
                profile.id.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? profile.id
            let _: BackendProfileDeleteResponse = try await BackendAPIClient.request(
                path: "v1/profiles/\(escapedProfileId)",
                method: "DELETE",
                authentication: .required
            )
        }
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
            pngData.count <= maxAvatarUploadBytes {
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

    private static func uploadAvatarData(_ avatarData: Data, to uploadUrlString: String, contentType: String)
        async throws {
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
            throw BackendAPIError(
                statusCode: httpResponse.statusCode,
                code: nil,
                message: String(localized: "settings.cloud.error.avatarUploadFailed")
            )
        }
    }

    static func previewLocalProfilesAbsentFromCloud(
        context: ModelContext
    ) async throws -> [CloudRestoreLocalProfile] {
        guard BackendSessionManager.shared.isAuthenticated else {
            throw BackendAPIError.unauthorized
        }

        let response: BackendProfilesResponse = try await BackendAPIClient.request(
            path: "v1/profiles",
            method: "GET",
            authentication: .required
        )
        let cloudProfileIds = Set(response.profiles.compactMap { UUID(uuidString: $0.id) })

        return try context.fetch(FetchDescriptor<UserProfile>())
            .filter { !cloudProfileIds.contains($0.id) }
            .map { CloudRestoreLocalProfile(id: $0.id, name: $0.name, server: $0.server) }
    }

    static func restoreFromCloud(
        context: ModelContext,
        removeLocalProfilesAbsentFromCloud: Bool = false
    ) async throws {
        try await BackendIncrementalSyncService.pullUpdates(
            context: context,
            force: true,
            removeLocalProfilesAbsentFromSnapshot: removeLocalProfilesAbsentFromCloud
        )
    }

    private static func ensureSyncConfig(context: ModelContext) -> SyncConfig {
        if let config = try? context.fetch(FetchDescriptor<SyncConfig>()).first {
            return config
        }
        let config = SyncConfig()
        context.insert(config)
        return config
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
