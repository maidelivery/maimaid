import Foundation
import SwiftData

enum LxnsTokenRefreshResult {
    case success(String)
    case expired
    case failed
}

@MainActor
class SyncManager {
    static let shared = SyncManager()
    
    private init() {}

    private func sheetTitle(_ sheet: Sheet) -> String {
        sheet.song?.title ?? String(localized: "common.unknown")
    }
    
    /// Syncs multiple scores immediately when backend session is available.
    func uploadScoresIfNeeded(scores: [(Sheet, Score)], config: SyncConfig) async {
        for (sheet, score) in scores {
            await uploadScoreIfNeeded(sheet: sheet, score: score, config: config)
        }
    }
    
    /// Backward-compatible entry used by legacy call sites.
    func uploadScoreIfNeeded(sheet: Sheet, score: Score, config: SyncConfig) async {
        let context = config.modelContext ?? score.modelContext ?? sheet.modelContext
        guard let context else {
            print("SyncManager: 成绩上行失败，缺少可用数据上下文。")
            return
        }

        await uploadScoreImmediately(sheet: sheet, score: score, context: context)
    }

    /// Syncs a score immediately to backend when user is authenticated.
    func uploadScoreImmediately(sheet: Sheet, score: Score, context: ModelContext) async {
        guard BackendSessionManager.shared.isAuthenticated else {
            return
        }

        print("SyncManager: 检测到「\(sheetTitle(sheet))」成绩更新，准备即时增量上行。")
        guard let profile = resolveProfile(for: score, context: context) else {
            print("SyncManager: 未找到可用于上传的当前激活档案。")
            return
        }

        await uploadToBackend(sheet: sheet, score: score, profile: profile)
    }

    /// Deletion path fallback: run a full overwrite backup so removed records can be reflected server-side.
    func syncCloudSnapshotIfNeeded(context: ModelContext) async {
        guard BackendSessionManager.shared.isAuthenticated else {
            return
        }

        do {
            try await BackendCloudSyncService.backupToCloud(context: context)
            print("SyncManager: [Backend] 已完成覆盖式云端同步。")
        } catch {
            print("SyncManager: [Backend] 覆盖式云端同步失败：\(error.localizedDescription)")
        }
    }
    
    private func uploadToBackend(sheet: Sheet, score: Score, profile: UserProfile) async {
        print("SyncManager: [Backend] 开始上传成绩。")

        do {
            try await BackendIncrementalSyncService.pushScoreUpdate(profile: profile, sheet: sheet, score: score)
            print("SyncManager: [Backend] 「\(sheetTitle(sheet))」上传成功。")
        } catch {
            print("SyncManager: [Backend] 上传出错：\(error.localizedDescription)")
        }
    }

    private func resolveProfile(for score: Score, context: ModelContext) -> UserProfile? {
        if let profileId = score.userProfileId {
            let profileById = FetchDescriptor<UserProfile>(
                predicate: #Predicate<UserProfile> { $0.id == profileId }
            )
            if let profile = (try? context.fetch(profileById))?.first {
                return profile
            }
        }

        let activeProfile = FetchDescriptor<UserProfile>(predicate: #Predicate<UserProfile> { $0.isActive == true })
        if let profile = (try? context.fetch(activeProfile))?.first {
            return profile
        }

        let allProfiles = FetchDescriptor<UserProfile>()
        return (try? context.fetch(allProfiles))?.first
    }
    
    func refreshLxnsToken(profileId: UUID) async -> String? {
        switch await refreshLxnsTokenResult(profileId: profileId) {
        case .success(let accessToken):
            return accessToken
        case .expired, .failed:
            return nil
        }
    }

    func refreshLxnsTokenResult(profileId: UUID) async -> LxnsTokenRefreshResult {
        let credentials = ProfileCredentialStore.shared.credentials(for: profileId)
        let refreshToken = credentials.lxnsRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !refreshToken.isEmpty else {
            return .expired
        }
        guard let url = URL(string: "https://maimai.lxns.net/api/v0/oauth/token") else { return .failed }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        
        let bodyString = [
            "grant_type": "refresh_token",
            "client_id": LxnsOAuthConfiguration.clientId,
            "refresh_token": refreshToken
        ].compactMap { key, value in
            let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            return "\(key)=\(encodedValue)"
        }.joined(separator: "&")
        
        request.httpBody = bodyString.data(using: .utf8)
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            let httpResponse = response as? HTTPURLResponse
            
            if let http = httpResponse, http.statusCode != 200 {
                let errorBody = String(data: data, encoding: .utf8) ?? "无错误响应内容"
                print("SyncManager: [LXNS] 刷新令牌失败，状态码 \(http.statusCode)，响应：\(errorBody)")
                
                // If 400 (Invalid Refresh Token), clear the token
                if http.statusCode == 400 {
                    print("SyncManager: [LXNS] 检测到无效 Refresh Token，正在清除凭据。")
                    ProfileCredentialStore.shared.setLxnsRefreshToken("", for: profileId)
                    return .expired
                }
                return .failed
            }
            
            let decoder = JSONDecoder()
            let tokenResponse = try decoder.decode(LxnsTokenResponse.self, from: data)
            
            if let newData = tokenResponse.data {
                ProfileCredentialStore.shared.setLxnsRefreshToken(newData.refresh_token, for: profileId)
                return .success(newData.access_token)
            }
        } catch {
            print("SyncManager: [LXNS] 刷新令牌出错：\(error.localizedDescription)")
        }
        return .failed
    }
    

}
