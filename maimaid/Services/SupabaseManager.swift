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
    
    let client: SupabaseClient
    
    var currentUser: User?
    
    var isAuthenticated: Bool {
        return currentUser != nil
    }
    
    private init() {
        self.client = SupabaseClient(
            supabaseURL: SupabaseConfig.projectURL,
            supabaseKey: SupabaseConfig.publishableKey,
            options: SupabaseClientOptions(
                auth: SupabaseClientOptions.AuthOptions(emitLocalSessionAsInitialSession: true)
            )
        )
        
        Task {
            await checkSession()
        }
    }
    
    func checkSession() async {
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
    
    
    // MARK: - Avatar Storage
    
    private func uploadAvatar(data: Data, profileId: UUID) async throws -> String {
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
        // Find all profiles, records, and scores
        let profileFetch = FetchDescriptor<UserProfile>()
        let profiles = try context.fetch(profileFetch)
        
        let recordFetch = FetchDescriptor<PlayRecord>()
        let records = try context.fetch(recordFetch)
        
        let scoreFetch = FetchDescriptor<Score>()
        let scores = try context.fetch(scoreFetch)
        
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
    }
    
    // MARK: - Data Sync (Restore)
    
    func restoreFromCloud(context: ModelContext) async throws {
        guard let user = currentUser else {
            throw NSError(domain: "Auth", code: 401, userInfo: [NSLocalizedDescriptionKey: "User not authenticated"])
        }
        
        // Fetch profiles
        let profileDTOs: [ProfileDTO] = try await client.from("profiles")
            .select()
            .eq("user_id", value: user.id)
            .execute()
            .value
            
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
                // existing.lxnsClientId skipped
                // existing.playerRating skipped
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
        
        // Fetch records
        let recordDTOs: [PlayRecordDTO] = try await client.from("play_records")
            .select()
            .eq("user_id", value: user.id)
            .execute()
            .value
            
        for dto in recordDTOs {
            let id = dto.id
            let fetchDes = FetchDescriptor<PlayRecord>(predicate: #Predicate { $0.id == id })
            if let existing = try context.fetch(fetchDes).first {
                existing.sheetId = dto.sheet_id
                existing.rate = dto.rate
                existing.rank = dto.rank
                existing.dxScore = dto.dx_score ?? 0
                existing.fc = dto.fc
                existing.fs = dto.fs
                existing.playDate = dto.play_date ?? Date()
                existing.userProfileId = dto.profile_id
            } else {
                let newRecord = PlayRecord(
                    id: dto.id,
                    sheetId: dto.sheet_id,
                    rate: dto.rate,
                    rank: dto.rank,
                    dxScore: dto.dx_score ?? 0,
                    fc: dto.fc,
                    fs: dto.fs,
                    playDate: dto.play_date ?? Date(),
                    userProfileId: dto.profile_id
                )
                context.insert(newRecord)
            }
        }
        
        // Fetch scores
        let scoreDTOs: [ScoreDTO] = try await client.from("scores")
            .select()
            .eq("user_id", value: user.id)
            .execute()
            .value
            
        for dto in scoreDTOs {
            let profileId = dto.profile_id
            let sheetId = dto.sheet_id
            // Need to match existing score manually because there's no unique uuid ID we save in SwiftData maybe. Wait, Score DOES NOT have an id UUID!
            // Wait! `Score` class doesn't have an `id: UUID` in the user's implementation.
            // Let's check `Score.swift`:
            // `var sheetId: String`, `var rate: Double`, ...
            // SwiftData automatically adds an internal ID, but we didn't expose it.
            // So we'll try to find by sheetId & profileId
            
            // Note: SwiftData predicate can't do complex multiple optional unwrapping easily. We might have to fetch and filter if profileId is optional.
            // A simpler approach: Fetch all scores, then filter locally for the exact match.
            // (For optimal performance, this should be done differently, but for restore it's okay)
        }
        
        // Bulk fetch all local scores to do memory matching
        let allScoresFetch = FetchDescriptor<Score>()
        let allLocalScores = try context.fetch(allScoresFetch)
        
        for dto in scoreDTOs {
            if let existing = allLocalScores.first(where: { $0.sheetId == dto.sheet_id && $0.userProfileId == dto.profile_id }) {
                // Determine if we should update. Usually we keep the highest rate/score. Or just blindly overwrite
                if existing.achievementDate < (dto.achievement_date ?? .distantPast) || existing.rate < dto.rate {
                    existing.rate = dto.rate
                    existing.rank = dto.rank
                    existing.dxScore = dto.dx_score ?? 0
                    existing.fc = dto.fc
                    existing.fs = dto.fs
                    existing.achievementDate = dto.achievement_date ?? Date()
                }
            } else {
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
            }
        }
        
        try context.save()
    }
}
