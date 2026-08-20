import Foundation

struct BackendImportRunResponse: Decodable {
    let importRunId: String
    let fetchedCount: Int
    let upsertedCount: Int
    let skippedCount: Int
    let latestRevision: String?
}

struct DivingFishAuthorizationResponse: Decodable {
    let authorizationUrl: String
    let expiresAt: String
}

struct DivingFishConnectionResponse: Decodable {
    let connected: Bool
    let externalUsername: String?
}

private struct DivingFishProfileRequest: Encodable {
    let profileId: String
}

private struct LxnsImportRequest: Encodable {
    let profileId: String
    let accessToken: String
}

enum BackendImportService {
    static func authorizeDivingFish(profileId: String) async throws -> DivingFishAuthorizationResponse {
        try await BackendAPIClient.request(
            path: "v1/imports:authorizeDivingFish",
            method: "POST",
            body: DivingFishProfileRequest(profileId: profileId),
            authentication: .required
        )
    }

    static func divingFishConnection(profileId: String) async throws -> DivingFishConnectionResponse {
        try await BackendAPIClient.request(
            path: "v1/imports:divingFishConnection?profileId=\(profileId)",
            authentication: .required
        )
    }

    static func importDivingFish(profileId: String) async throws -> BackendImportRunResponse {
        return try await BackendAPIClient.request(
            path: "v1/imports:importDf",
            method: "POST",
            body: DivingFishProfileRequest(profileId: profileId),
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
