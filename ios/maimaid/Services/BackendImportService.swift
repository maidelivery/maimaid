import Foundation

struct BackendImportRunResponse: Decodable {
    let importRunId: String
    let fetchedCount: Int
    let upsertedCount: Int
    let skippedCount: Int
}

private struct DivingFishImportRequest: Encodable {
    let profileId: String
    let username: String?
    let qq: String?
}

private struct LxnsImportRequest: Encodable {
    let profileId: String
    let accessToken: String
}

enum BackendImportService {
    static func importDivingFish(
        profileId: String,
        username: String?,
        qq: String?
    ) async throws -> BackendImportRunResponse {
        let request = DivingFishImportRequest(profileId: profileId, username: username, qq: qq)
        return try await BackendAPIClient.request(
            path: "v1/import/df",
            method: "POST",
            body: request,
            authentication: .required
        )
    }
    
    static func importLxns(profileId: String, accessToken: String) async throws -> BackendImportRunResponse {
        let request = LxnsImportRequest(profileId: profileId, accessToken: accessToken)
        return try await BackendAPIClient.request(
            path: "v1/import/lxns",
            method: "POST",
            body: request,
            authentication: .required
        )
    }
}
