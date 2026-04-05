import Foundation
import SwiftData

private struct DivingFishScoreUploadRecord: Encodable {
    let title: String
    let level_index: Int
    let achievements: Double
    let type: String
    let dxScore: Int
    let fc: String?
    let fs: String?
}

private struct LxnsScoreUploadRecord: Encodable {
    let id: Int
    let song_name: String
    let level_index: Int
    let type: String
    let achievements: Double
    let dx_score: Int
    let fc: String?
    let fs: String?
}

private struct LxnsScoreUploadRequest: Encodable {
    let scores: [LxnsScoreUploadRecord]
}

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
    
    /// Unified sync entry for manual score-save flow:
    /// backend incremental sync + third-party (DF/LXNS) sync.
    func syncAfterScoreSave(sheet: Sheet, score: Score, context: ModelContext) async {
        let backendTask = Task { @MainActor in
            await uploadScoreImmediately(sheet: sheet, score: score, context: context)
        }
        let thirdPartyTask = Task { @MainActor in
            await uploadToThirdPartiesIfNeeded(sheet: sheet, score: score, context: context)
        }
        await backendTask.value
        await thirdPartyTask.value
    }

    /// Legacy entry: syncs multiple scores to third-party services when enabled.
    func uploadScoresIfNeeded(scores: [(Sheet, Score)], config: SyncConfig) async {
        guard config.isAutoUploadEnabled else {
            return
        }
        for (sheet, score) in scores {
            await uploadScoreIfNeeded(sheet: sheet, score: score, config: config)
        }
    }
    
    /// Legacy entry used by old call sites. This path only targets third-party sync.
    func uploadScoreIfNeeded(sheet: Sheet, score: Score, config: SyncConfig) async {
        guard config.isAutoUploadEnabled else {
            return
        }

        let context = config.modelContext ?? score.modelContext ?? sheet.modelContext
        guard let context else {
            print("SyncManager: 成绩上行失败，缺少可用数据上下文。")
            return
        }
        let profile = resolveProfile(for: score, context: context)
        await uploadToThirdPartiesIfNeeded(
            sheet: sheet,
            score: score,
            context: context,
            profile: profile,
            isEnabled: true
        )
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

    private func uploadToThirdPartiesIfNeeded(
        sheet: Sheet,
        score: Score,
        context: ModelContext,
        profile: UserProfile? = nil,
        isEnabled: Bool? = nil
    ) async {
        let thirdPartySyncEnabled = isEnabled ?? loadThirdPartySyncEnabled(context: context)
        guard thirdPartySyncEnabled else {
            return
        }
        guard sheet.regionCn else {
            print("SyncManager: [ThirdParty] 跳过上送，谱面不在国服可玩范围。")
            return
        }

        guard let activeProfile = profile ?? resolveProfile(for: score, context: context) else {
            print("SyncManager: [ThirdParty] 跳过上送，未找到可用档案。")
            return
        }

        let credentials = ProfileCredentialStore.shared.credentials(for: activeProfile.id)
        let divingFishTask = Task { @MainActor in
            await uploadToDivingFishIfPossible(
                sheet: sheet,
                score: score,
                profile: activeProfile,
                dfImportToken: credentials.dfImportToken
            )
        }
        let lxnsTask = Task { @MainActor in
            await uploadToLxnsIfPossible(
                sheet: sheet,
                score: score,
                profile: activeProfile,
                lxnsRefreshToken: credentials.lxnsRefreshToken
            )
        }
        await divingFishTask.value
        await lxnsTask.value
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

    private func loadThirdPartySyncEnabled(context: ModelContext) -> Bool {
        let descriptor = FetchDescriptor<SyncConfig>()
        return (try? context.fetch(descriptor))?.first?.isAutoUploadEnabled ?? false
    }

    private func uploadToDivingFishIfPossible(
        sheet: Sheet,
        score: Score,
        profile: UserProfile,
        dfImportToken: String
    ) async {
        let username = profile.dfUsername.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !username.isEmpty else {
            print("SyncManager: [Diving Fish] 跳过上送，未绑定账号。")
            return
        }
        let token = dfImportToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !token.isEmpty else {
            print("SyncManager: [Diving Fish] 跳过上送，缺少 Import-Token。")
            return
        }

        let normalizedType = normalizeChartType(sheet.type)
        guard normalizedType != "utage" else {
            print("SyncManager: [Diving Fish] 跳过上送，DF 不支持 utage。")
            return
        }
        guard let url = URL(string: "https://www.diving-fish.com/api/maimaidxprober/player/update_records") else {
            return
        }

        let providerType = normalizedType == "dx" ? "DX" : "SD"
        let payload = [
            DivingFishScoreUploadRecord(
                title: sheet.song?.title ?? "",
                level_index: ThemeUtils.mapDifficultyToIndex(sheet.difficulty),
                achievements: score.rate,
                type: providerType,
                dxScore: score.dxScore,
                fc: score.fc,
                fs: score.fs
            )
        ]

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue(token, forHTTPHeaderField: "Import-Token")

        do {
            request.httpBody = try JSONEncoder().encode(payload)
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                print("SyncManager: [Diving Fish] 上送失败，未收到有效响应。")
                return
            }

            if httpResponse.statusCode == 200 {
                print("SyncManager: [Diving Fish] 「\(sheetTitle(sheet))」上送成功。")
            } else {
                let responseBody = String(data: data, encoding: .utf8) ?? "无响应内容"
                print("SyncManager: [Diving Fish] 上送失败，状态码 \(httpResponse.statusCode)，响应：\(responseBody)")
            }
        } catch {
            print("SyncManager: [Diving Fish] 上送出错：\(error.localizedDescription)")
        }
    }

    private func uploadToLxnsIfPossible(
        sheet: Sheet,
        score: Score,
        profile: UserProfile,
        lxnsRefreshToken: String
    ) async {
        let trimmedRefreshToken = lxnsRefreshToken.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmedRefreshToken.isEmpty else {
            print("SyncManager: [LXNS] 跳过上送，缺少 Refresh Token。")
            return
        }
        guard let songId = resolveProviderSongId(for: sheet) else {
            print("SyncManager: [LXNS] 跳过上送，缺少可用歌曲 ID。")
            return
        }
        guard let accessToken = await refreshLxnsToken(profileId: profile.id) else {
            print("SyncManager: [LXNS] 跳过上送，无法获取 Access Token。")
            return
        }
        guard let url = URL(string: "https://maimai.lxns.net/api/v0/user/maimai/player/scores") else {
            return
        }

        let payload = LxnsScoreUploadRequest(
            scores: [
                LxnsScoreUploadRecord(
                    id: songId,
                    song_name: sheet.song?.title ?? "",
                    level_index: ThemeUtils.mapDifficultyToIndex(sheet.difficulty),
                    type: lxnsChartType(for: sheet.type),
                    achievements: score.rate,
                    dx_score: score.dxScore,
                    fc: score.fc,
                    fs: score.fs
                )
            ]
        )

        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("Bearer \(accessToken)", forHTTPHeaderField: "Authorization")

        do {
            request.httpBody = try JSONEncoder().encode(payload)
            let (data, response) = try await URLSession.shared.data(for: request)
            guard let httpResponse = response as? HTTPURLResponse else {
                print("SyncManager: [LXNS] 上送失败，未收到有效响应。")
                return
            }

            if (200...299).contains(httpResponse.statusCode) {
                print("SyncManager: [LXNS] 「\(sheetTitle(sheet))」上送成功。")
            } else {
                let responseBody = String(data: data, encoding: .utf8) ?? "无响应内容"
                print("SyncManager: [LXNS] 上送失败，状态码 \(httpResponse.statusCode)，响应：\(responseBody)")
            }
        } catch {
            print("SyncManager: [LXNS] 上送出错：\(error.localizedDescription)")
        }
    }

    private func resolveProviderSongId(for sheet: Sheet) -> Int? {
        if sheet.songId > 0 {
            return sheet.songId
        }
        let fallbackSongId = sheet.song?.songId ?? 0
        return fallbackSongId > 0 ? fallbackSongId : nil
    }

    private func normalizeChartType(_ rawType: String) -> String {
        let normalized = rawType.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        switch normalized {
        case "sd", "std", "standard":
            return "std"
        case "dx":
            return "dx"
        case "utage":
            return "utage"
        default:
            return normalized
        }
    }

    private func lxnsChartType(for rawType: String) -> String {
        switch normalizeChartType(rawType) {
        case "dx":
            return "dx"
        case "utage":
            return "utage"
        default:
            return "standard"
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
