import Foundation
import SwiftData
import Supabase

// MARK: - Data Transfer Objects (DTOs) for Supabase

struct ProfileDTO: Codable {
    var id: UUID
    var user_id: UUID?
    var name: String
    var server: String
    var avatar_url: String?
    var is_active: Bool
    var created_at: Date
    
    var df_username: String?
    var df_import_token: String?
    var lxns_refresh_token: String?
    
    var plate: String?
    
    var last_import_date_df: Date?
    var last_import_date_lxns: Date?
    
    var b35_count: Int?
    var b15_count: Int?
    var b35_rec_limit: Int?
    var b15_rec_limit: Int?
}

struct PlayRecordDTO: Codable {
    var id: UUID
    var user_id: UUID?
    var profile_id: UUID?
    var sheet_id: String
    var rate: Double
    var rank: String
    var dx_score: Int?
    var fc: String?
    var fs: String?
    var play_date: Date?
}

struct ScoreDTO: Codable {
    var user_id: UUID?
    var profile_id: UUID?
    var sheet_id: String
    var rate: Double
    var rank: String
    var dx_score: Int?
    var fc: String?
    var fs: String?
    var achievement_date: Date?
}

// MARK: - Extensions for Hex Encoding
extension Data {
    struct HexEncodingOptions: OptionSet {
        let rawValue: Int
        static let upperCase = HexEncodingOptions(rawValue: 1 << 0)
    }

    func hexEncodedString(options: HexEncodingOptions = []) -> String {
        let format = options.contains(.upperCase) ? "%02hhX" : "%02hhx"
        return "\\x" + map { String(format: format, $0) }.joined()
    }
    
    init?(hexString: String) {
        let len = hexString.count / 2
        var data = Data(capacity: len)
        var i = hexString.startIndex
        if hexString.hasPrefix("\\x") || hexString.hasPrefix("0x") {
            i = hexString.index(i, offsetBy: 2)
        }
        for _ in 0..<len {
            let nextIndex = hexString.index(i, offsetBy: 2)
            if let b = UInt8(hexString[i..<nextIndex], radix: 16) {
                data.append(b)
            } else {
                return nil
            }
            i = nextIndex
        }
        self = data
    }
}

// MARK: - Supabase Manager

@Observable
final class SupabaseManager {
    static let shared = SupabaseManager()
    static let authRedirectURL = URL(string: "maimaid://auth/callback")
    static let recoveryRedirectURL = URL(string: "maimaid://auth/callback?flow=recovery")
    
    let client: SupabaseClient?
    
    var currentUser: User?
    var isPasswordRecoveryFlow = false
    var pendingAuthMessage: String?
    var pendingAuthMessageIsError = false

    var isConfigured: Bool {
        client != nil
    }

    var configurationErrorDescription: String? {
        SupabaseConfig.configurationError?.errorDescription
    }
    
    var isAuthenticated: Bool {
        return currentUser != nil
    }
    
    private init() {
        if let projectURL = SupabaseConfig.projectURL,
           let publishableKey = SupabaseConfig.publishableKey {
            self.client = SupabaseClient(
                supabaseURL: projectURL,
                supabaseKey: publishableKey,
                options: SupabaseClientOptions(
                    auth: SupabaseClientOptions.AuthOptions(
                        redirectToURL: Self.authRedirectURL,
                        emitLocalSessionAsInitialSession: true
                    )
                )
            )
        } else {
            self.client = nil
        }

        if client != nil {
            Task {
                await checkSession()
            }
        }
    }
    
    func checkSession() async {
        guard let client else {
            await MainActor.run {
                self.currentUser = nil
            }
            return
        }

        do {
            let session = try await client.auth.session
            await MainActor.run {
                self.currentUser = session.user
            }
        } catch {
            await MainActor.run {
                self.currentUser = nil
            }
        }
    }

    func handleAuthRedirect(_ url: URL) async {
        guard let client else { return }
        guard isAppAuthCallbackURL(url) else { return }

        do {
            let callbackType = authCallbackType(from: url)
            let callbackFlow = authCallbackFlow(from: url)
            _ = try await client.auth.session(from: url)
            await checkSession()

            await MainActor.run {
                let isRecoveryFlow = callbackType == "recovery" || callbackFlow == "recovery"
                self.isPasswordRecoveryFlow = isRecoveryFlow
                self.pendingAuthMessage = isRecoveryFlow
                    ? "settings.cloud.message.recoveryLinkOpened"
                    : "settings.cloud.message.authLinkSuccess"
                self.pendingAuthMessageIsError = false
            }
        } catch {
            await MainActor.run {
                self.pendingAuthMessage = error.localizedDescription
                self.pendingAuthMessageIsError = true
            }
        }
    }

    func clearPendingAuthMessage() {
        pendingAuthMessage = nil
        pendingAuthMessageIsError = false
    }

    func clearPasswordRecoveryFlow() {
        isPasswordRecoveryFlow = false
    }

    func emailExists(_ email: String) async throws -> Bool {
        let normalizedEmail = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !normalizedEmail.isEmpty else { return false }

        let client = try requireClient()
        let exists: Bool = try await client
            .rpc("auth_email_exists", params: ["p_email": normalizedEmail])
            .execute()
            .value
        return exists
    }

    private func isAppAuthCallbackURL(_ url: URL) -> Bool {
        guard let redirect = Self.authRedirectURL else { return false }
        guard
            let redirectComponents = URLComponents(url: redirect, resolvingAgainstBaseURL: false),
            let incomingComponents = URLComponents(url: url, resolvingAgainstBaseURL: false)
        else {
            return false
        }

        return incomingComponents.scheme == redirectComponents.scheme
            && incomingComponents.host == redirectComponents.host
            && incomingComponents.path == redirectComponents.path
    }

    private func authCallbackType(from url: URL) -> String? {
        value(of: "type", from: url)
    }

    private func authCallbackFlow(from url: URL) -> String? {
        value(of: "flow", from: url)
    }

    private func value(of name: String, from url: URL) -> String? {
        guard let components = URLComponents(url: url, resolvingAgainstBaseURL: false) else {
            return nil
        }

        if let value = components.queryItems?.first(where: { $0.name == name })?.value {
            return value
        }

        guard let fragment = components.fragment else {
            return nil
        }

        return URLComponents(string: "?\(fragment)")?
            .queryItems?
            .first(where: { $0.name == name })?
            .value
    }
    
    
    // MARK: - Avatar Storage
    
    private func uploadAvatar(data: Data, profileId: UUID) async throws -> String {
        let client = try requireClient()
        let fileName = "\(profileId.uuidString).png"
        let path = "\(fileName)"
        
        // Upload to "avatars" bucket
        try await client.storage
            .from("avatars")
            .upload(
                path,
                data: data,
                options: FileOptions(contentType: "image/png", upsert: true)
            )
        
        // Get public URL
        let url = try client.storage
            .from("avatars")
            .getPublicURL(path: path)
        
        return url.absoluteString
    }
    
    // MARK: - Data Sync (Backup)
    
    func backupToCloud(context: ModelContext) async throws {
        let client = try requireClient()
        
        if currentUser == nil {
            await checkSession()
        }

        // Find all profiles, records, and scores
        let profileFetch = FetchDescriptor<UserProfile>()
        let profiles = try context.fetch(profileFetch)
        
        let recordFetch = FetchDescriptor<PlayRecord>()
        let records = try context.fetch(recordFetch)
        
        let scoreFetch = FetchDescriptor<Score>()
        let scores = try context.fetch(scoreFetch)
        
        let activeProfileId = ScoreService.shared.currentActiveProfileId(context: context)
        if let activeProfileId {
            var didBackfillProfileId = false
            
            for record in records where record.userProfileId == nil {
                record.userProfileId = activeProfileId
                didBackfillProfileId = true
            }
            
            for score in scores where score.userProfileId == nil {
                score.userProfileId = activeProfileId
                didBackfillProfileId = true
            }
            
            if didBackfillProfileId {
                try? context.save()
                ScoreService.shared.notifyScoresChanged(for: activeProfileId)
            }
        }
        
        guard let user = currentUser else {
            throw NSError(domain: "Auth", code: 401, userInfo: [NSLocalizedDescriptionKey: "User not authenticated"])
        }
        
        // Upsert Profiles
        var profileDTOs: [ProfileDTO] = []
        print("SupabaseManager: Starting backup for \(profiles.count) profiles...")
        
        for p in profiles {
            var finalAvatarUrl = p.avatarUrl
            
            // If there's custom data, upload it to Storage
            if let data = p.avatarData {
                do {
                    print("SupabaseManager: Uploading avatar for profile \(p.name) (\(p.id))...")
                    finalAvatarUrl = try await uploadAvatar(data: data, profileId: p.id)
                    p.avatarUrl = finalAvatarUrl // Save it back to local model!
                } catch {
                    print("Error uploading avatar for \(p.name): \(error)")
                }
            }
            
            print("SupabaseManager: Preparing DTO for \(p.name), avatarUrl: \(finalAvatarUrl ?? "nil")")
            let dto = ProfileDTO(
                id: p.id,
                user_id: user.id,
                name: p.name,
                server: p.server,
                avatar_url: finalAvatarUrl,
                is_active: p.isActive,
                created_at: p.createdAt,
                df_username: p.dfUsername,
                df_import_token: p.dfImportToken,
                lxns_refresh_token: p.lxnsRefreshToken,
                plate: p.plate,
                last_import_date_df: p.lastImportDateDF,
                last_import_date_lxns: p.lastImportDateLXNS,
                b35_count: p.b35Count,
                b15_count: p.b15Count,
                b35_rec_limit: p.b35RecLimit,
                b15_rec_limit: p.b15RecLimit
            )
            profileDTOs.append(dto)
        }
        
        if !profileDTOs.isEmpty {
            try await client.from("profiles").upsert(profileDTOs).execute()
            print("SupabaseManager: \(profileDTOs.count) profiles upserted.")
        }
        
        // Save those avatarUrl updates if any
        try? context.save()
        
        // Upsert Play Records
        let recordDTOs = records.map { r in
            PlayRecordDTO(
                id: r.id,
                user_id: user.id,
                profile_id: r.userProfileId,
                sheet_id: r.sheetId,
                rate: r.rate,
                rank: r.rank,
                dx_score: r.dxScore,
                fc: r.fc,
                fs: r.fs,
                play_date: r.playDate
            )
        }
        
        if !recordDTOs.isEmpty {
            // chunking might be necessary for very large arrays, but we'll try straight upsert for now
            try await client.from("play_records").upsert(recordDTOs).execute()
        }
        
        // Upsert Scores
        let scoreDTOs = scores.map { s in
            ScoreDTO(
                user_id: user.id,
                profile_id: s.userProfileId,
                sheet_id: s.sheetId,
                rate: s.rate,
                rank: s.rank,
                dx_score: s.dxScore,
                fc: s.fc,
                fs: s.fs,
                achievement_date: s.achievementDate
            )
        }
        
        if !scoreDTOs.isEmpty {
            // Supabase upsert on custom unique constraints (profile_id, sheet_id) might need specific onConflict params
            try await client.from("scores").upsert(scoreDTOs, onConflict: "profile_id,sheet_id").execute()
        }
        
        if let config = try? context.fetch(FetchDescriptor<SyncConfig>()).first {
            config.lastSupabaseBackupDate = Date()
        } else {
            let newConfig = SyncConfig()
            newConfig.lastSupabaseBackupDate = Date()
            context.insert(newConfig)
        }
        
        try? context.save()
    }
    
    private func restoreScopeFilter(
        userId: UUID,
        scopedIds: Set<UUID>,
        ownershipColumn: String = "profile_id"
    ) -> String {
        var filters = ["user_id.eq.\(userId.uuidString)"]
        
        let sortedScopedIds = scopedIds
            .map(\.uuidString)
            .sorted()
        
        if !sortedScopedIds.isEmpty {
            filters.append("\(ownershipColumn).in.(\(sortedScopedIds.joined(separator: ",")))")
        }
        
        return filters.joined(separator: ",")
    }
    
    private func fetchRestoreRows<DTO: Decodable>(
        from table: String,
        as type: DTO.Type,
        client: SupabaseClient,
        userId: UUID,
        scopedIds: Set<UUID>,
        ownershipColumn: String = "profile_id"
    ) async throws -> [DTO] {
        let query = client.from(table).select()
        
        if scopedIds.isEmpty {
            return try await query
                .eq("user_id", value: userId)
                .execute()
                .value
        }
        
        return try await query
            .or(restoreScopeFilter(userId: userId, scopedIds: scopedIds, ownershipColumn: ownershipColumn))
            .execute()
            .value
    }
    
    private func canonicalScoreSheetId(for sheet: Sheet) -> String {
        "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
    }
    
    private func canonicalRecordSheetId(for sheet: Sheet) -> String {
        "\(sheet.songIdentifier)-\(sheet.type)-\(sheet.difficulty)"
    }
    
    private func sheetIdentifiers(for sheet: Sheet, separators: [String]) -> Set<String> {
        let identifiers = candidateSongIdentifiers(for: sheet)
        return Set(
            identifiers.flatMap { songIdentifier in
                separators.map { separator in
                    "\(songIdentifier)\(separator)\(sheet.type)\(separator)\(sheet.difficulty)"
                }
            }
        )
    }
    
    private func candidateSongIdentifiers(for sheet: Sheet) -> Set<String> {
        var identifiers = Set<String>()
        
        if !sheet.songIdentifier.isEmpty {
            identifiers.insert(sheet.songIdentifier)
        }
        
        if sheet.songId > 0 {
            identifiers.insert(String(sheet.songId))
        }
        
        if let song = sheet.song {
            if !song.songIdentifier.isEmpty {
                identifiers.insert(song.songIdentifier)
            }
            if song.songId > 0 {
                identifiers.insert(String(song.songId))
            }
        }
        
        return identifiers
    }
    
    private func scoreSheetIdentifiers(for sheet: Sheet) -> Set<String> {
        sheetIdentifiers(for: sheet, separators: ["_", "-"])
    }
    
    private func playRecordSheetIdentifiers(for sheet: Sheet) -> Set<String> {
        sheetIdentifiers(for: sheet, separators: ["-", "_"])
    }
    
    private func canonicalize(_ dto: ScoreDTO, using sheetMap: [String: Sheet]) -> ScoreDTO {
        guard let sheet = sheetMap[dto.sheet_id] else { return dto }
        
        return ScoreDTO(
            user_id: dto.user_id,
            profile_id: dto.profile_id,
            sheet_id: canonicalScoreSheetId(for: sheet),
            rate: dto.rate,
            rank: dto.rank,
            dx_score: dto.dx_score,
            fc: dto.fc,
            fs: dto.fs,
            achievement_date: dto.achievement_date
        )
    }
    
    private func mergedScoreDTO(_ lhs: ScoreDTO, _ rhs: ScoreDTO) -> ScoreDTO {
        var merged = lhs
        
        if rhs.rate > merged.rate {
            merged.rate = rhs.rate
            merged.rank = rhs.rank
            merged.achievement_date = rhs.achievement_date
        } else if rhs.rate == merged.rate {
            let mergedDate = merged.achievement_date ?? .distantPast
            let rhsDate = rhs.achievement_date ?? .distantPast
            if rhsDate > mergedDate {
                merged.rank = rhs.rank
                merged.achievement_date = rhs.achievement_date
            }
        }
        
        merged.dx_score = max(merged.dx_score ?? 0, rhs.dx_score ?? 0)
        merged.fc = ThemeUtils.bestFC(merged.fc, rhs.fc)
        merged.fs = ThemeUtils.bestFS(merged.fs, rhs.fs)
        return merged
    }

    // MARK: - Data Sync (Restore)
    
    func restoreFromCloud(context: ModelContext) async throws {
        let client = try requireClient()

        guard let user = currentUser else {
            throw NSError(domain: "Auth", code: 401, userInfo: [NSLocalizedDescriptionKey: "User not authenticated"])
        }
        
        let existingLocalProfileIds = Set(
            try context.fetch(FetchDescriptor<UserProfile>()).map(\.id)
        )
        
        let profileDTOs: [ProfileDTO] = try await fetchRestoreRows(
            from: "profiles",
            as: ProfileDTO.self,
            client: client,
            userId: user.id,
            scopedIds: existingLocalProfileIds,
            ownershipColumn: "id"
        )
        
        let restoredProfileIds = Set(profileDTOs.map(\.id))
        var restoreScopeProfileIds = existingLocalProfileIds
        restoreScopeProfileIds.formUnion(restoredProfileIds)
        
        // Pre-fetch and map Sheets to link relationships correctly
        let allSheets = try context.fetch(FetchDescriptor<Sheet>())
        var sheetMapScoreId: [String: Sheet] = [:]
        var sheetMapRecordId: [String: Sheet] = [:]
        for sheet in allSheets {
            for scoreId in scoreSheetIdentifiers(for: sheet) {
                sheetMapScoreId[scoreId] = sheet
            }
            
            for recordId in playRecordSheetIdentifiers(for: sheet) {
                sheetMapRecordId[recordId] = sheet
            }
        }
        
        let recordDTOs: [PlayRecordDTO] = try await fetchRestoreRows(
            from: "play_records",
            as: PlayRecordDTO.self,
            client: client,
            userId: user.id,
            scopedIds: restoreScopeProfileIds
        )
        
        let rawScoreDTOs: [ScoreDTO] = try await fetchRestoreRows(
            from: "scores",
            as: ScoreDTO.self,
            client: client,
            userId: user.id,
            scopedIds: restoreScopeProfileIds
        )
        
        var fetchedProfileIds = restoredProfileIds
        fetchedProfileIds.formUnion(recordDTOs.compactMap(\.profile_id))
        fetchedProfileIds.formUnion(rawScoreDTOs.compactMap(\.profile_id))
            
        // Process profiles
        for dto in profileDTOs {
            let id = dto.id
            let fetchDes = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.id == id })
            if let existing = try context.fetch(fetchDes).first {
                existing.name = dto.name
                existing.server = dto.server
                if let urlString = dto.avatar_url, let url = URL(string: urlString) {
                    // Download from storage if needed
                    do {
                        let (data, _) = try await URLSession.shared.data(from: url)
                        existing.avatarData = data
                    } catch {
                        print("Failed to download avatar: \(error)")
                    }
                } else {
                    existing.avatarData = nil
                }
                existing.avatarUrl = dto.avatar_url
                existing.isActive = dto.is_active
                existing.createdAt = dto.created_at
                existing.dfUsername = dto.df_username ?? ""
                existing.dfImportToken = dto.df_import_token ?? ""
                existing.lxnsRefreshToken = dto.lxns_refresh_token ?? ""
                existing.plate = dto.plate
                existing.lastImportDateDF = dto.last_import_date_df
                existing.lastImportDateLXNS = dto.last_import_date_lxns
                existing.b35Count = dto.b35_count ?? 35
                existing.b15Count = dto.b15_count ?? 15
                existing.b35RecLimit = dto.b35_rec_limit ?? 10
                existing.b15RecLimit = dto.b15_rec_limit ?? 10
            } else {
                let profileId = dto.id
                var initialData: Data? = nil
                
                if let urlString = dto.avatar_url, let url = URL(string: urlString) {
                    initialData = try? await URLSession.shared.data(from: url).0
                }
                
                let newProfile = UserProfile(
                    id: profileId,
                    name: dto.name,
                    server: dto.server,
                    avatarData: initialData,
                    avatarUrl: dto.avatar_url,
                    isActive: dto.is_active,
                    createdAt: dto.created_at,
                    dfUsername: dto.df_username ?? "",
                    dfImportToken: dto.df_import_token ?? "",
                    lxnsRefreshToken: dto.lxns_refresh_token ?? "",
                    plate: dto.plate,
                    lastImportDateDF: dto.last_import_date_df,
                    lastImportDateLXNS: dto.last_import_date_lxns,
                    b35Count: dto.b35_count ?? 35,
                    b15Count: dto.b15_count ?? 15,
                    b35RecLimit: dto.b35_rec_limit ?? 10,
                    b15RecLimit: dto.b15_rec_limit ?? 10
                )
                context.insert(newProfile)
            }
        }
        
        // Delete existing local records and scores for the restored profiles (Complete Overwrite)
        if !fetchedProfileIds.isEmpty {
            let allLocalRecords = try context.fetch(FetchDescriptor<PlayRecord>())
            for record in allLocalRecords {
                if let pid = record.userProfileId, fetchedProfileIds.contains(pid) {
                    context.delete(record)
                }
            }
            
            let allLocalScores = try context.fetch(FetchDescriptor<Score>())
            for score in allLocalScores {
                if let pid = score.userProfileId, fetchedProfileIds.contains(pid) {
                    context.delete(score)
                }
            }
            // Save state after clearing
            try context.save()
        }
            
        for dto in recordDTOs {
            let resolvedSheet = sheetMapRecordId[dto.sheet_id]
            let newRecord = PlayRecord(
                id: dto.id,
                sheetId: resolvedSheet.map(canonicalRecordSheetId(for:)) ?? dto.sheet_id,
                rate: dto.rate,
                rank: dto.rank,
                dxScore: dto.dx_score ?? 0,
                fc: dto.fc,
                fs: dto.fs,
                playDate: dto.play_date ?? Date(),
                userProfileId: dto.profile_id
            )
            context.insert(newRecord)
            
            if let sheet = resolvedSheet {
                newRecord.sheet = sheet
                if sheet.playRecords == nil {
                    sheet.playRecords = []
                }
                sheet.playRecords?.append(newRecord)
            }
        }
        
        var dedupedScoreDTOs: [String: ScoreDTO] = [:]
        for dto in rawScoreDTOs {
            let canonicalDTO = canonicalize(dto, using: sheetMapScoreId)
            let profileKey = canonicalDTO.profile_id?.uuidString ?? "nil"
            let dedupeKey = "\(profileKey)|\(canonicalDTO.sheet_id)"
            
            if let existing = dedupedScoreDTOs[dedupeKey] {
                dedupedScoreDTOs[dedupeKey] = mergedScoreDTO(existing, canonicalDTO)
            } else {
                dedupedScoreDTOs[dedupeKey] = canonicalDTO
            }
        }
            
        for dto in dedupedScoreDTOs.values {
            let newScore = Score(
                sheetId: dto.sheet_id,
                rate: dto.rate,
                rank: dto.rank,
                dxScore: dto.dx_score ?? 0,
                fc: dto.fc,
                fs: dto.fs,
                achievementDate: dto.achievement_date ?? Date(),
                userProfileId: dto.profile_id
            )
            context.insert(newScore)
            
            if let sheet = sheetMapScoreId[dto.sheet_id] {
                newScore.sheet = sheet
                sheet.scores.append(newScore)
            }
        }
        
        try context.save()
        await MainActor.run {
            ScoreService.shared.invalidateAllCaches()
        }
    }

    private func requireClient() throws -> SupabaseClient {
        guard let client else {
            throw SupabaseConfig.configurationError ?? SupabaseConfigError.missingURL
        }
        return client
    }
}
