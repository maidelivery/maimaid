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
    
    /// Syncs multiple scores if auto-upload is enabled
    func uploadScoresIfNeeded(scores: [(Sheet, Score)], config: SyncConfig) async {
        guard config.isAutoUploadEnabled else { return }
        for (sheet, score) in scores {
            await uploadScoreIfNeeded(sheet: sheet, score: score, config: config)
        }
    }
    
    /// Syncs a score to backend if auto-upload is enabled
    func uploadScoreIfNeeded(sheet: Sheet, score: Score, config: SyncConfig) async {
        print("SyncManager: 检测到「\(sheetTitle(sheet))」成绩更新，自动上传\(config.isAutoUploadEnabled ? "已开启" : "已关闭")。")
        guard config.isAutoUploadEnabled else { return }
        
        // Find active profile
        let descriptor = FetchDescriptor<UserProfile>(predicate: #Predicate<UserProfile> { $0.isActive == true })
        let profile = (try? config.modelContext?.fetch(descriptor))?.first
        
        guard let profile = profile else {
            print("SyncManager: 未找到可用于上传的当前激活档案。")
            return
        }
        
        await uploadToBackend(sheet: sheet, score: score, profile: profile)
    }
    
    private func uploadToBackend(sheet: Sheet, score: Score, profile: UserProfile) async {
        print("SyncManager: [Backend] 开始上传成绩。")

        do {
            try await BackendScoreSyncService.uploadScore(profile: profile, sheet: sheet, score: score)
            print("SyncManager: [Backend] 「\(sheetTitle(sheet))」上传成功。")
        } catch {
            print("SyncManager: [Backend] 上传出错：\(error.localizedDescription)")
        }
    }
    
    func refreshLxnsToken(profile: UserProfile) async -> String? {
        switch await refreshLxnsTokenResult(profile: profile) {
        case .success(let accessToken):
            return accessToken
        case .expired, .failed:
            return nil
        }
    }

    func refreshLxnsTokenResult(profile: UserProfile) async -> LxnsTokenRefreshResult {
        guard let url = URL(string: "https://maimai.lxns.net/api/v0/oauth/token") else { return .failed }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        
        let bodyString = [
            "grant_type": "refresh_token",
            "client_id": profile.lxnsClientId,
            "refresh_token": profile.lxnsRefreshToken
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
                    profile.lxnsRefreshToken = ""
                    try? profile.modelContext?.save()
                    return .expired
                }
                return .failed
            }
            
            let decoder = JSONDecoder()
            let tokenResponse = try decoder.decode(LxnsTokenResponse.self, from: data)
            
            if let newData = tokenResponse.data {
                profile.lxnsRefreshToken = newData.refresh_token
                try? profile.modelContext?.save()
                return .success(newData.access_token)
            }
        } catch {
            print("SyncManager: [LXNS] 刷新令牌出错：\(error.localizedDescription)")
        }
        return .failed
    }
    

}
