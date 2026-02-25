import Foundation
import SwiftData

struct SongData: Decodable {
    let songs: [InternalSong]
    let updateTime: String
}

struct AliasListResponse: Decodable {
    let aliases: [AliasItem]
}

struct AliasItem: Decodable {
    let song_id: Int
    let aliases: [String]
}

struct LxnsSongListResponse: Decodable {
    let songs: [LxnsSong]
}

struct LxnsSong: Decodable {
    let id: Int
    let title: String
}

struct InternalSong: Decodable {
    let songId: String
    let category: String?
    let title: String?
    let artist: String?
    let bpm: Double?
    let imageName: String?
    let version: String?
    let releaseDate: String?
    let isNew: Bool?
    let isLocked: Bool?
    let comment: String?
    let sheets: [InternalSheet]
}

struct InternalSheet: Decodable {
    let type: String
    let difficulty: String
    let level: String
    let levelValue: Double?
    let internalLevel: String?
    let internalLevelValue: Double?
    let noteDesigner: String?
    let noteCounts: InternalNoteCounts?
    let isSpecial: Bool?
}

struct InternalNoteCounts: Decodable {
    let tap: Int?
    let hold: Int?
    let slide: Int?
    let touch: Int?
    let breakCount: Int?
    let total: Int?
    
    enum CodingKeys: String, CodingKey {
        case tap, hold, slide, touch, total
        case breakCount = "break"
    }
}

@MainActor
class MaimaiDataFetcher {
    static let shared = MaimaiDataFetcher()
    
    // Default placeholder URL. User should replace this with their hosted data.json URL.
    private let DATA_URL = "https://maimaid.shikoch.in/data.json"
    
    func fetchSongs(modelContext: ModelContext) async throws {
        let data: Data
        
        // Try to load from Bundle first (Offline Mode)
        if let bundleUrl = Bundle.main.url(forResource: "data", withExtension: "json") {
            data = try Data(contentsOf: bundleUrl)
        } else {
            // Fallback to Network
            guard let url = URL(string: DATA_URL) else { return }
            let (networkData, _) = try await URLSession.shared.data(from: url)
            data = networkData
        }
        
        let songData = try JSONDecoder().decode(SongData.self, from: data)
        
        // Fetch Aliases and LXNS Song List to build a Title -> Aliases map
        var aliasMap: [String: [String]] = [:]
        var titleToLxnsId: [String: Int] = [:]
        do {
            if let aliasUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/alias/list"),
               let lxnsSongUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/song/list") {
                
                async let (aliasData, _) = URLSession.shared.data(from: aliasUrl)
                async let (lxnsSongData, _) = URLSession.shared.data(from: lxnsSongUrl)
                
                let aliasResponse = try await JSONDecoder().decode(AliasListResponse.self, from: aliasData)
                let lxnsSongResponse = try await JSONDecoder().decode(LxnsSongListResponse.self, from: lxnsSongData)
                
                var lxnsIdToTitle: [Int: String] = [:]
                for lxnsSong in lxnsSongResponse.songs {
                    lxnsIdToTitle[lxnsSong.id] = lxnsSong.title
                    titleToLxnsId[lxnsSong.title] = lxnsSong.id
                }
                
                for item in aliasResponse.aliases {
                    if let title = lxnsIdToTitle[item.song_id] {
                        aliasMap[title] = item.aliases
                    }
                }
            }
        } catch {
            print("Failed to fetch aliases: \(error)")
        }
        
        // 1. Map existing songs for efficient updates
        let existingSongs = try modelContext.fetch(FetchDescriptor<Song>())
        var songMap: [String: Song] = [:]
        for s in existingSongs {
            songMap[s.songId] = s
        }
        
        // 2. Process songs
        for internalSong in songData.songs {
            let songId = internalSong.songId
            
            let song: Song
            if let existing = songMap[songId] {
                song = existing
            } else {
                song = Song(
                    songId: songId,
                    category: internalSong.category ?? "",
                    title: internalSong.title ?? "",
                    artist: internalSong.artist ?? "",
                    imageName: internalSong.imageName ?? "",
                    imageUrl: "", // We'll rely on imageName/static assets
                    version: internalSong.version,
                    releaseDate: internalSong.releaseDate,
                    sortOrder: 0,
                    bpm: internalSong.bpm,
                    isNew: internalSong.isNew ?? false,
                    isLocked: internalSong.isLocked ?? false,
                    comment: internalSong.comment
                )
                modelContext.insert(song)
            }
            
            // Map LXNS ID by title if available
            if let title = internalSong.title, let officialId = titleToLxnsId[title] {
                song.lxnsId = officialId
                // If the data.json songId is just a placeholder (not matching official), 
                // we technically want to use officialId for sync.
            }
            
            // Update metadata
            song.category = internalSong.category ?? ""
            song.title = internalSong.title ?? ""
            song.artist = internalSong.artist ?? ""
            song.bpm = internalSong.bpm
            song.version = internalSong.version
            song.releaseDate = internalSong.releaseDate
            song.isNew = internalSong.isNew ?? false
            song.isLocked = internalSong.isLocked ?? false
            song.comment = internalSong.comment
            
            if let aliases = aliasMap[internalSong.title ?? ""], !aliases.isEmpty {
                song.aliases = aliases
            }
            
            // Re-sync sheets
            // Simple approach: clear and re-add for accuracy unless score exists
            // To preserve scores, we match by (type, difficulty)
            var sheetMap: [String: Sheet] = [:]
            for sheet in song.sheets {
                let key = "\(sheet.type)_\(sheet.difficulty)"
                sheetMap[key] = sheet
            }
            
            var newSheets: [Sheet] = []
            for internalSheet in internalSong.sheets {
                let key = "\(internalSheet.type)_\(internalSheet.difficulty)"
                
                if let existingSheet = sheetMap[key] {
                    // Update metadata
                    existingSheet.level = internalSheet.level
                    existingSheet.levelValue = internalSheet.levelValue
                    existingSheet.internalLevel = internalSheet.internalLevel
                    existingSheet.internalLevelValue = internalSheet.internalLevelValue
                    existingSheet.noteDesigner = internalSheet.noteDesigner
                    
                    if let notes = internalSheet.noteCounts {
                        existingSheet.tap = notes.tap
                        existingSheet.hold = notes.hold
                        existingSheet.slide = notes.slide
                        existingSheet.touch = notes.touch
                        existingSheet.breakCount = notes.breakCount
                        existingSheet.total = notes.total
                    }
                    newSheets.append(existingSheet)
                } else {
                    let sheet = Sheet(
                        songId: songId,
                        type: internalSheet.type,
                        difficulty: internalSheet.difficulty,
                        level: internalSheet.level,
                        levelValue: internalSheet.levelValue,
                        internalLevel: internalSheet.internalLevel,
                        internalLevelValue: internalSheet.internalLevelValue,
                        noteDesigner: internalSheet.noteDesigner,
                        tap: internalSheet.noteCounts?.tap,
                        hold: internalSheet.noteCounts?.hold,
                        slide: internalSheet.noteCounts?.slide,
                        touch: internalSheet.noteCounts?.touch,
                        breakCount: internalSheet.noteCounts?.breakCount,
                        total: internalSheet.noteCounts?.total
                    )
                    newSheets.append(sheet)
                }
            }
            
            // Remove sheets that are no longer in the data and don't have scores
            for sheet in song.sheets {
                let key = "\(sheet.type)_\(sheet.difficulty)"
                if !internalSong.sheets.contains(where: { "\($0.type)_\($0.difficulty)" == key }) {
                    if sheet.score == nil {
                        modelContext.delete(sheet)
                    }
                }
            }
            
            song.sheets = newSheets
            
            // Yield to keep UI responsive
            if Int.random(in: 0...100) == 0 {
                await Task.yield()
            }
        }
        
        try modelContext.save()
        UserDefaults.standard.set(true, forKey: "didPerformInitialSync")
    }
}
