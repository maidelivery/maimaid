import Foundation
import SwiftData

// MARK: - Codable Transfer Models

struct TransferData: Codable {
    let version: Int
    let exportDate: Date
    let scores: [TransferScore]
    let favorites: [String] // songId list
    let config: TransferConfig?
}

struct TransferScore: Codable {
    let sheetId: String
    let rate: Double
    let rank: String
    let dxScore: Int
    let fc: String?
    let fs: String?
    let achievementDate: Date
}

struct TransferConfig: Codable {
    let dfUsername: String?
    let dfImportToken: String?
    let lxnsRefreshToken: String?
    let lxnsClientId: String?
    let userName: String?
    let playerRating: Int?
    let plate: String?
    let isAutoUploadEnabled: Bool?
    let themeRawValue: Int?
    let b35Count: Int?
    let b15Count: Int?
    let b35RecLimit: Int?
    let b15RecLimit: Int?
}

// MARK: - Service

enum DataTransferError: LocalizedError {
    case noStaticData
    case encodingFailed
    case decodingFailed(String)
    case noScoresFound
    
    var errorDescription: String? {
        switch self {
        case .noStaticData: return "Please download static data first."
        case .encodingFailed: return "Failed to encode data."
        case .decodingFailed(let msg): return "Failed to decode data: \(msg)"
        case .noScoresFound: return "No scores found in import file."
        }
    }
}

struct DataTransferService {
    
    // MARK: - Export
    
    static func exportData(context: ModelContext) throws -> Data {
        // 1. Gather scores
        let scoreDescriptor = FetchDescriptor<Score>()
        let scores = try context.fetch(scoreDescriptor)
        
        let transferScores = scores.map { score in
            TransferScore(
                sheetId: score.sheetId,
                rate: score.rate,
                rank: score.rank,
                dxScore: score.dxScore,
                fc: score.fc,
                fs: score.fs,
                achievementDate: score.achievementDate
            )
        }
        
        // 2. Gather favorites
        let songDescriptor = FetchDescriptor<Song>()
        let songs = try context.fetch(songDescriptor)
        let favorites = songs.filter { $0.isFavorite }.map { $0.songIdentifier }
        
        // 3. Gather config
        let configDescriptor = FetchDescriptor<SyncConfig>()
        let configs = try context.fetch(configDescriptor)
        let transferConfig: TransferConfig? = configs.first.map { c in
            TransferConfig(
                dfUsername: c.dfUsername.isEmpty ? nil : c.dfUsername,
                dfImportToken: c.dfImportToken.isEmpty ? nil : c.dfImportToken,
                lxnsRefreshToken: c.lxnsRefreshToken.isEmpty ? nil : c.lxnsRefreshToken,
                lxnsClientId: c.lxnsClientId,
                userName: c.userName,
                playerRating: c.playerRating,
                plate: c.plate,
                isAutoUploadEnabled: c.isAutoUploadEnabled,
                themeRawValue: c.themeRawValue,
                b35Count: c.b35Count,
                b15Count: c.b15Count,
                b35RecLimit: c.b35RecLimit,
                b15RecLimit: c.b15RecLimit
            )
        }
        
        // 4. Build transfer object
        let transfer = TransferData(
            version: 1,
            exportDate: Date(),
            scores: transferScores,
            favorites: favorites,
            config: transferConfig
        )
        
        let encoder = JSONEncoder()
        encoder.dateEncodingStrategy = .iso8601
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        
        guard let data = try? encoder.encode(transfer) else {
            throw DataTransferError.encodingFailed
        }
        return data
    }
    
    // MARK: - Import
    
    struct ImportSummary {
        let scoresImported: Int
        let favoritesRestored: Int
        let configRestored: Bool
    }
    
    static func importData(from data: Data, context: ModelContext) throws -> ImportSummary {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        
        let transfer: TransferData
        do {
            transfer = try decoder.decode(TransferData.self, from: data)
        } catch {
            throw DataTransferError.decodingFailed(error.localizedDescription)
        }
        
        // 1. Import scores – match by sheetId to existing sheets
        var scoresImported = 0
        let sheetDescriptor = FetchDescriptor<Sheet>()
        let allSheets = try context.fetch(sheetDescriptor)
        let sheetLookup = Dictionary(uniqueKeysWithValues: allSheets.compactMap { sheet -> (String, Sheet)? in
            let key = "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)"
            return (key, sheet)
        })
        
        for transferScore in transfer.scores {
            // Try to find the matching sheet
            if let sheet = sheetLookup[transferScore.sheetId] {
                if let existingScore = sheet.score() {
                    // Update if imported score is better
                    if transferScore.rate > existingScore.rate {
                        existingScore.rate = transferScore.rate
                        existingScore.rank = transferScore.rank
                        existingScore.dxScore = transferScore.dxScore
                        existingScore.fc = transferScore.fc
                        existingScore.fs = transferScore.fs
                        existingScore.achievementDate = transferScore.achievementDate
                        scoresImported += 1
                    }
                } else {
                    // Create new score
                    let newScore = Score(
                        sheetId: transferScore.sheetId,
                        rate: transferScore.rate,
                        rank: transferScore.rank,
                        dxScore: transferScore.dxScore,
                        fc: transferScore.fc,
                        fs: transferScore.fs,
                        achievementDate: transferScore.achievementDate
                    )
                    sheet.scores.append(newScore)
                    context.insert(newScore)
                    scoresImported += 1
                }
            }
        }
        
        // 2. Restore favorites
        var favoritesRestored = 0
        if !transfer.favorites.isEmpty {
            let songDescriptor = FetchDescriptor<Song>()
            let allSongs = try context.fetch(songDescriptor)
            let songLookup = Dictionary(uniqueKeysWithValues: allSongs.map { ($0.songIdentifier, $0) })
            
            for songId in transfer.favorites {
                if let song = songLookup[songId] {
                    song.isFavorite = true
                    favoritesRestored += 1
                }
            }
        }
        
        // 3. Restore config
        var configRestored = false
        if let tc = transfer.config {
            let configDescriptor = FetchDescriptor<SyncConfig>()
            let configs = try context.fetch(configDescriptor)
            let config = configs.first ?? {
                let newConfig = SyncConfig()
                context.insert(newConfig)
                return newConfig
            }()
            
            if let v = tc.dfUsername { config.dfUsername = v }
            if let v = tc.dfImportToken { config.dfImportToken = v }
            if let v = tc.lxnsRefreshToken { config.lxnsRefreshToken = v }
            if let v = tc.lxnsClientId { config.lxnsClientId = v }
            if let v = tc.userName { config.userName = v }
            if let v = tc.playerRating { config.playerRating = v }
            if let v = tc.plate { config.plate = v }
            if let v = tc.isAutoUploadEnabled { config.isAutoUploadEnabled = v }
            if let v = tc.themeRawValue { config.themeRawValue = v }
            if let v = tc.b35Count { config.b35Count = v }
            if let v = tc.b15Count { config.b15Count = v }
            if let v = tc.b35RecLimit { config.b35RecLimit = v }
            if let v = tc.b15RecLimit { config.b15RecLimit = v }
            
            configRestored = true
        }
        
        try context.save()
        
        return ImportSummary(
            scoresImported: scoresImported,
            favoritesRestored: favoritesRestored,
            configRestored: configRestored
        )
    }
}

// MARK: - iCloud Backup

extension DataTransferService {
    
    static let iCloudBackupFileName = "maimaid_backup.json"
    static let iCloudEnabledKey = "iCloudBackupEnabled"
    static let iCloudLastBackupKey = "iCloudLastBackupDate"
    
    static var isICloudEnabled: Bool {
        get { UserDefaults.standard.bool(forKey: iCloudEnabledKey) }
        set { UserDefaults.standard.set(newValue, forKey: iCloudEnabledKey) }
    }
    
    static var lastICloudBackupDate: Date? {
        get { UserDefaults.standard.object(forKey: iCloudLastBackupKey) as? Date }
        set { UserDefaults.standard.set(newValue, forKey: iCloudLastBackupKey) }
    }
    
    static var isICloudAvailable: Bool {
        FileManager.default.ubiquityIdentityToken != nil
    }
    
    private static func iCloudDocumentsURL() -> URL? {
        guard let containerURL = FileManager.default.url(
            forUbiquityContainerIdentifier: "iCloud.com.shiko.maimaid"
        ) else { return nil }
        
        let documentsURL = containerURL.appendingPathComponent("Documents")
        if !FileManager.default.fileExists(atPath: documentsURL.path) {
            try? FileManager.default.createDirectory(at: documentsURL, withIntermediateDirectories: true)
        }
        return documentsURL
    }
    
    /// Save a backup to iCloud Documents
    static func backupToICloud(context: ModelContext) async throws {
        guard isICloudEnabled else { return }
        
        let data = try exportData(context: context)
        
        guard let docsURL = iCloudDocumentsURL() else {
            throw DataTransferError.noStaticData // iCloud not available
        }
        
        let fileURL = docsURL.appendingPathComponent(iCloudBackupFileName)
        try data.write(to: fileURL, options: .atomic)
        
        await MainActor.run {
            lastICloudBackupDate = Date()
        }
    }
    
    /// Restore from iCloud Documents backup
    static func restoreFromICloud(context: ModelContext) throws -> ImportSummary? {
        guard let docsURL = iCloudDocumentsURL() else { return nil }
        
        let fileURL = docsURL.appendingPathComponent(iCloudBackupFileName)
        guard FileManager.default.fileExists(atPath: fileURL.path) else { return nil }
        
        let data = try Data(contentsOf: fileURL)
        return try importData(from: data, context: context)
    }
    
    /// Check if an iCloud backup file exists
    static func hasICloudBackup() -> Bool {
        guard let docsURL = iCloudDocumentsURL() else { return false }
        let fileURL = docsURL.appendingPathComponent(iCloudBackupFileName)
        return FileManager.default.fileExists(atPath: fileURL.path)
    }
    
    /// Get the date of the iCloud backup file
    static func iCloudBackupDate() -> Date? {
        guard let docsURL = iCloudDocumentsURL() else { return nil }
        let fileURL = docsURL.appendingPathComponent(iCloudBackupFileName)
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: fileURL.path) else { return nil }
        return attrs[.modificationDate] as? Date
    }
}
