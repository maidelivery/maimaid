import Foundation
import SwiftData

enum GameServer: String, CaseIterable, Identifiable, Codable {
    case jp = "jp"
    case intl = "intl"
    case cn = "cn"
    
    var id: String { rawValue }
    
    var displayName: String {
        switch self {
        case .jp:   return String(localized: "server.jp")
        case .intl: return String(localized: "server.intl")
        case .cn:   return String(localized: "server.cn")
        }
    }
    
    /// Maps to the corresponding region boolean on Sheet (Legacy, now partially deprecated by time-offset logic)
    var regionKeyPath: KeyPath<Sheet, Bool> {
        switch self {
        case .jp:   return \.regionJp
        case .intl: return \.regionIntl
        case .cn:   return \.regionCn
        }
    }
}

@MainActor
class ServerVersionService {
    static let shared = ServerVersionService()
    
    private init() {}
    
    /// Determines the latest version for a given server by finding the song with
    /// the newest releaseDate that is playable on that server.
    /// Deleted songs (not playable on ANY server) are excluded.
    func cutoffDate(for server: GameServer) -> String {
        let formatter = DateFormatter()
        formatter.dateFormat = "yyyy-MM-dd"
        formatter.locale = Locale(identifier: "en_US_POSIX")
        
        let now = Date()
        let calendar = Calendar.current
        
        switch server {
        case .jp:
            return "9999-12-31" // All JP songs are playable
        case .cn:
            let date = calendar.date(byAdding: .month, value: -18, to: now) ?? now
            return formatter.string(from: date)
        case .intl:
            let date = calendar.date(byAdding: .month, value: -4, to: now) ?? now
            return formatter.string(from: date)
        }
    }
    
    func isPlayable(song: Song, cutoff: String, server: GameServer? = nil) -> Bool {
        // Exclude utage
        if song.category.lowercased().contains("utage") || song.category.contains("宴") {
            return false
        }
        
        // Exclude deleted songs (no regions at all)
        let isDeleted = song.sheets.isEmpty || song.sheets.allSatisfy { sheet in
            !sheet.regionJp && !sheet.regionIntl && !sheet.regionCn
        }
        if isDeleted { return false }
        
        // If the song explicitly has the region set to true for the specified server, it is actively playable
        if let srv = server {
            let hasRegion = song.sheets.contains { sheet in
                switch srv {
                case .jp: return sheet.regionJp
                case .intl: return sheet.regionIntl
                case .cn: return sheet.regionCn
                }
            }
            if hasRegion {
                return true
            }
        }
        
        guard let releaseDateStr = song.releaseDate, !releaseDateStr.isEmpty else {
            return true // Playable if missing date
        }
        
        return releaseDateStr <= cutoff
    }

    func latestVersion(for server: GameServer, songs: [Song]) -> String {
        let cutoff = cutoffDate(for: server)
        
        let sequence = UserDefaults.standard.stringArray(forKey: "MaimaiVersionSequence") ?? []
        let orderedVersions: [String]
        if sequence.isEmpty {
            let uniqueVersions = Array(Set(songs.compactMap { $0.version }))
            orderedVersions = uniqueVersions.sorted()
        } else {
            orderedVersions = sequence
        }
        
        var serverVersion = orderedVersions.first ?? ThemeUtils.latestVersion
        
        for version in orderedVersions {
            let versionSongs = songs.filter { $0.version == version && !($0.category.lowercased().contains("utage") || $0.category.contains("宴")) }
            
            // Exclude completely deleted/removed songs from the version count
            let activeVersionSongs = versionSongs.filter { song in
                !song.sheets.isEmpty && !song.sheets.allSatisfy { !$0.regionJp && !$0.regionIntl && !$0.regionCn }
            }
            if activeVersionSongs.isEmpty { continue }
            
            let playableCount = activeVersionSongs.filter { isPlayable(song: $0, cutoff: cutoff) }.count
            
            if playableCount > 0 {
                serverVersion = version // This version has at least one playable song
                if playableCount < activeVersionSongs.count {
                    // Not fully playable -> server is currently in this version (e.g., PRiSM partially released)
                    break
                }
            } else {
                // Zero playable songs, BUT the previous version was 100% playable.
                // The user logic dictates that if the calculated version is 100% playable, we probe the next one.
                // If it has unplayable songs (0 playable < total), we set the server version to this one.
                serverVersion = version
                break
            }
        }
        
        return serverVersion
    }
    
    /// Returns a dictionary of latest versions for all servers
    func allLatestVersions(songs: [Song]) -> [GameServer: String] {
        var result: [GameServer: String] = [:]
        for server in GameServer.allCases {
            result[server] = latestVersion(for: server, songs: songs)
        }
        return result
    }
    
    /// Returns the active user profile, or nil if none exists
    func activeProfile(context: ModelContext) -> UserProfile? {
        let descriptor = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.isActive == true })
        return try? context.fetch(descriptor).first
    }
    
    /// Returns the latest version for the active user's server
    func latestVersionForActiveUser(songs: [Song], context: ModelContext) -> String {
        guard let profile = activeProfile(context: context),
              let server = GameServer(rawValue: profile.server) else {
            return ThemeUtils.latestVersion
        }
        return latestVersion(for: server, songs: songs)
    }
}
