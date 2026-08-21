import Foundation

struct BackendImportRunResponse: Decodable {
    let importRunId: String
    let fetchedCount: Int
    let upsertedCount: Int
    let skippedCount: Int
    let latestRevision: String?
}

private struct DivingFishImportRequest: Encodable {
    let profileId: String
}

private struct DivingFishScoreSyncRecord: Encodable {
    let title: String
    let chartType: String
    let levelIndex: Int
    let achievements: Double
    let dxScore: Int
    let fc: String?
    let fs: String?
}

private struct DivingFishScoreSyncRequest: Encodable {
    let profileId: String
    let records: [DivingFishScoreSyncRecord]
}

private struct DivingFishScoreSyncResponse: Decodable {
    let syncedCount: Int
}

struct DivingFishAuthorization: Decodable {
    let authorizationId: String
    let authorizationUrl: URL
    let expiresAt: Date
}

struct DivingFishAuthorizationStatus: Decodable {
    let status: String
    let errorCode: String?
    let expiresAt: Date
}

struct DivingFishBindingStatus: Decodable {
    let connected: Bool
    let canWrite: Bool
    let externalUsername: String?
}

private struct LxnsImportRequest: Encodable {
    let profileId: String
    let accessToken: String
}

enum BackendImportService {
    static func importDivingFish(
        profileId: String
    ) async throws -> BackendImportRunResponse {
        let request = DivingFishImportRequest(profileId: profileId)
        return try await BackendAPIClient.request(
            path: "v1/imports:importDf",
            method: "POST",
            body: request,
            authentication: .required
        )
    }

    static func startDivingFishAuthorization(profileId: String) async throws -> DivingFishAuthorization {
        try await BackendAPIClient.request(
            path: "v1/imports:authorizeDivingFish",
            method: "POST",
            body: DivingFishImportRequest(profileId: profileId),
            authentication: .required
        )
    }

    static func divingFishAuthorizationStatus(
        authorizationId: String
    ) async throws -> DivingFishAuthorizationStatus {
        try await BackendAPIClient.request(
            path: "v1/imports:divingFishAuthorizationStatus?authorizationId=\(authorizationId)",
            authentication: .required
        )
    }

    static func divingFishBindingStatus(profileId: String) async throws -> DivingFishBindingStatus {
        try await BackendAPIClient.request(
            path: "v1/imports:divingFishBinding?profileId=\(profileId)",
            authentication: .required
        )
    }

    static func disconnectDivingFish(profileId: String) async throws -> DivingFishBindingStatus {
        try await BackendAPIClient.request(
            path: "v1/imports:divingFishBinding",
            method: "DELETE",
            body: DivingFishImportRequest(profileId: profileId),
            authentication: .required
        )
    }

    static func syncDivingFishScore(
        profileId: String,
        title: String,
        chartType: String,
        levelIndex: Int,
        achievements: Double,
        dxScore: Int,
        fc: String?,
        fs: String?
    ) async throws {
        let request = DivingFishScoreSyncRequest(
            profileId: profileId,
            records: [
                DivingFishScoreSyncRecord(
                    title: title,
                    chartType: chartType,
                    levelIndex: levelIndex,
                    achievements: achievements,
                    dxScore: dxScore,
                    fc: fc,
                    fs: fs
                )
            ]
        )
        let _: DivingFishScoreSyncResponse = try await BackendAPIClient.request(
            path: "v1/imports:syncDivingFishScores",
            method: "POST",
            body: request,
            authentication: .required
        )
    }

    static func importLxns(profileId: String, accessToken: String) async throws -> BackendImportRunResponse {
        let request = LxnsImportRequest(profileId: profileId, accessToken: accessToken)
        return try await BackendAPIClient.request(
            path: "v1/imports:importLxns",
            method: "POST",
            body: request,
            authentication: .required
        )
    }
}
