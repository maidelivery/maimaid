import Foundation
import SwiftData

@MainActor
class SyncManager {
    static let shared = SyncManager()
    
    private init() {}
    
    /// Syncs multiple scores if auto-upload is enabled
    func uploadScoresIfNeeded(scores: [(Sheet, Score)], config: SyncConfig) async {
        guard config.isAutoUploadEnabled else { return }
        for (sheet, score) in scores {
            await uploadScoreIfNeeded(sheet: sheet, score: score, config: config)
        }
    }
    
    /// Syncs a score to both services if auto-upload is enabled
    func uploadScoreIfNeeded(sheet: Sheet, score: Score, config: SyncConfig) async {
        print("SyncManager: Update detected for \"\(sheet.song?.title ?? "Unknown")\". Auto-upload is \(config.isAutoUploadEnabled ? "ENABLED" : "DISABLED").")
        guard config.isAutoUploadEnabled else { return }
        
        // 1. Upload to Diving Fish
        if !config.dfUsername.isEmpty && !config.dfImportToken.isEmpty {
            await uploadToDivingFish(sheet: sheet, score: score, config: config)
        }
        
        // 2. Upload to LXNS
        if !config.lxnsRefreshToken.isEmpty {
            await uploadToLxns(sheet: sheet, score: score, config: config)
        }
    }
    
    private func uploadToDivingFish(sheet: Sheet, score: Score, config: SyncConfig) async {
        print("SyncManager: [Diving Fish] Uploading to Diving Fish...")
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
        request.setValue(config.dfImportToken, forHTTPHeaderField: "Import-Token")
        
        do {
            let jsonData = try JSONSerialization.data(withJSONObject: [record])
            if let jsonString = String(data: jsonData, encoding: .utf8) {
                print("SyncManager: [Diving Fish] Sending payload: \(jsonString)")
            }
            request.httpBody = jsonData
            
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                if httpResponse.statusCode == 200 {
                    print("SyncManager: [Diving Fish] Upload SUCCESS for \"\(sheet.song?.title ?? "Unknown")\"")
                } else {
                    let responseString = String(data: data, encoding: .utf8) ?? "No response body"
                    print("SyncManager: [Diving Fish] Upload FAILED with status \(httpResponse.statusCode). Response: \(responseString)")
                }
            }
        } catch {
            print("SyncManager: [Diving Fish] Upload ERROR: \(error.localizedDescription)")
        }
    }
    
    private func uploadToLxns(sheet: Sheet, score: Score, config: SyncConfig) async {
        print("SyncManager: [LXNS] Attempting upload...")
        
        // 1. Get Access Token via Refresh Token
        guard let accessToken = await refreshLxnsToken(config: config) else {
            // Error already logged in refreshLxnsToken
            return 
        }
        
        // 2. POST to /api/v0/user/maimai/player/scores
        guard let url = URL(string: "https://maimai.lxns.net/api/v0/user/maimai/player/scores") else { return }
        
        // LXNS expects: { "id": 123, "song_name": "...", "level_index": 0-4, "type": "dx"/"std", "achievements": 100.0, "dx_score": 0 }
        let record: [String: Any] = [
            "id": sheet.song?.lxnsId ?? (Int(sheet.songId) ?? 0),
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
                print("SyncManager: [LXNS] Sending payload: \(jsonString)")
            }
            request.httpBody = jsonData
            
            let (data, response) = try await URLSession.shared.data(for: request)
            if let httpResponse = response as? HTTPURLResponse {
                if (200...299).contains(httpResponse.statusCode) {
                    print("SyncManager: [LXNS] Upload SUCCESS for \"\(sheet.song?.title ?? "Unknown")\"")
                } else {
                    let responseString = String(data: data, encoding: .utf8) ?? "No response body"
                    print("SyncManager: [LXNS] Upload FAILED with status \(httpResponse.statusCode). Response: \(responseString)")
                }
            }
        } catch {
            print("SyncManager: [LXNS] Upload ERROR: \(error.localizedDescription)")
        }
    }
    
    func refreshLxnsToken(config: SyncConfig) async -> String? {
        guard let url = URL(string: "https://maimai.lxns.net/api/v0/oauth/token") else { return nil }
        
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        
        let bodyString = [
            "grant_type": "refresh_token",
            "client_id": config.lxnsClientId,
            "refresh_token": config.lxnsRefreshToken
        ].compactMap { key, value in
            let encodedValue = value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? ""
            return "\(key)=\(encodedValue)"
        }.joined(separator: "&")
        
        request.httpBody = bodyString.data(using: .utf8)
        
        do {
            let (data, response) = try await URLSession.shared.data(for: request)
            let httpResponse = response as? HTTPURLResponse
            
            if let http = httpResponse, http.statusCode != 200 {
                let errorBody = String(data: data, encoding: .utf8) ?? "No error body"
                print("SyncManager: [LXNS] Refresh FAILED with status \(http.statusCode). Response: \(errorBody)")
                
                // If 400 (Invalid Refresh Token), clear the token
                if http.statusCode == 400 {
                    print("SyncManager: [LXNS] Invalid Refresh Token detected. Clearing credentials...")
                    config.lxnsRefreshToken = ""
                    try? config.modelContext?.save()
                }
                return nil
            }
            
            let decoder = JSONDecoder()
            let tokenResponse = try decoder.decode(LxnsTokenResponse.self, from: data)
            
            if let newData = tokenResponse.data {
                config.lxnsRefreshToken = newData.refresh_token
                try? config.modelContext?.save()
                return newData.access_token
            }
        } catch {
            print("SyncManager: [LXNS] Refresh ERROR: \(error.localizedDescription)")
        }
        return nil
    }
    

}
