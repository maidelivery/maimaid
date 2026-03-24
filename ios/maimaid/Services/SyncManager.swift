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
    
    /// Syncs a score to both services if auto-upload is enabled
    func uploadScoreIfNeeded(sheet: Sheet, score: Score, config: SyncConfig) async {
        print("SyncManager: 检测到「\(sheetTitle(sheet))」成绩更新，自动上传\(config.isAutoUploadEnabled ? "已开启" : "已关闭")。")
        guard config.isAutoUploadEnabled else { return }
        
        // Find active profile for credentials
        let descriptor = FetchDescriptor<UserProfile>(predicate: #Predicate<UserProfile> { $0.isActive == true })
        let profile = (try? config.modelContext?.fetch(descriptor))?.first
        
        guard let profile = profile else {
            print("SyncManager: 未找到可用于上传的当前激活档案。")
            return
        }
        
        // 1. Upload to Diving Fish
        if !profile.dfUsername.isEmpty && !profile.dfImportToken.isEmpty {
            await uploadToDivingFish(sheet: sheet, score: score, profile: profile)
        }
        
        // 2. Upload to LXNS
        if !profile.lxnsRefreshToken.isEmpty {
            await uploadToLxns(sheet: sheet, score: score, profile: profile)
        }
    }
    
    private func uploadToDivingFish(sheet: Sheet, score: Score, profile: UserProfile) async {
        print("SyncManager: [Diving Fish] 开始上传成绩。")
        // Use the specific player update endpoint
        guard let url = URL(string: "https://www.diving-fish.com/api/maimaidxprober/player/update_records") else { return }
        
        // Diving Fish expects: { "level_index": 0-14, "achievements": 100.0000, "type": "DX"/"SD", "title": "...", "dxScore": 0, "fc": "", "fs": "" }
        let record: [String: Any] = [
            "title": sheet.song?.title ?? "",
            "level_index": ThemeUtils.mapDifficultyToIndex(sheet.difficulty),
            "achievements": Double(String(format: "%.4f", score.rate)) ?? score.rate,
            "type": sheet.type.lowercased() == "dx" ? "DX" : "SD",
            "dxScore": score.dxScore,
            "fc": score.fc as Any,
            "fs": score.fs as Any
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(profile.dfImportToken, forHTTPHeaderField: "Import-Token")
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: [record])
            if let jsonString = String(data: jsonData, encoding: .utf8) {
                print("SyncManager: [Diving Fish] 请求载荷：\(jsonString)")
            }
            request.httpBody = jsonData
            
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode == 200 {
                    print("SyncManager: [Diving Fish] 「\(sheetTitle(sheet))」上传成功。")
                } else {
                    let responseString = String(data: data, encoding: .utf8) ?? "无响应内容"
                    print("SyncManager: [Diving Fish] 上传失败，状态码 \(httpResponse.statusCode)，响应：\(responseString)")
                }
            }
        } catch {
            print("SyncManager: [Diving Fish] 上传出错：\(error.localizedDescription)")
        }
    }
    
    private func uploadToLxns(sheet: Sheet, score: Score, profile: UserProfile) async {
        print("SyncManager: [LXNS] 开始上传成绩。")
        
        // 1. Get Access Token via Refresh Token
        guard let accessToken = await refreshLxnsToken(profile: profile) else {
            // Error already logged in refreshLxnsToken
            return 
        }
        
        // 2. POST to /api/v0/user/maimai/player/scores
        guard let url = URL(string: "https://maimai.lxns.net/api/v0/user/maimai/player/scores") else { return }
        
        // LXNS expects: { "id": 123, "song_name": "...", "level_index": 0-4, "type": "dx"/"std", "achievements": 100.0, "dx_score": 0 }
        let record: [String: Any] = [
            "id": sheet.song?.songId ?? 0,
            "song_name": sheet.song?.title ?? "",
            "level_index": ThemeUtils.mapDifficultyToIndex(sheet.difficulty),
            "type": sheet.type.lowercased() == "dx" ? "dx" : "std",
            "achievements": score.rate,
            "dx_score": score.dxScore,
            "fc": score.fc as Any,
            "fs": score.fs as Any
        ]
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: ["scores": [record]])
            if let jsonString = String(data: jsonData, encoding: .utf8) {
                print("SyncManager: [LXNS] 请求载荷：\(jsonString)")
            }
            request.httpBody = jsonData
            
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                if (200...299).contains(httpResponse.statusCode) {
                    print("SyncManager: [LXNS] 「\(sheetTitle(sheet))」上传成功。")
                } else {
                    let responseString = String(data: data, encoding: .utf8) ?? "无响应内容"
                    print("SyncManager: [LXNS] 上传失败，状态码 \(httpResponse.statusCode)，响应：\(responseString)")
                }
            }
        } catch {
            print("SyncManager: [LXNS] 上传出错：\(error.localizedDescription)")
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
