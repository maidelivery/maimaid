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

@Observable
@MainActor
class MaimaiDataFetcher {
    static let shared = MaimaiDataFetcher()
    
    enum SyncStage: String {
        case idle = "等待中"
        case fetchingData = "正在从云端拉取静态数据..."
        case fetchingAliases = "正在获取歌曲别名..."
        case fetchingLxnsIds = "正在关联官方 ID..."
        case processingSongs = "正在同步歌曲列表..."
        case saving = "正在整理并保存数据..."
        case completed = "同步完成"
        case failed = "同步失败"
    }
    
    var isSyncing = false
    var currentStage: SyncStage = .idle
    var progress: Double = 0
    var statusMessage: String = ""
    
    private let DATA_URL = "https://maimaid.shikoch.in/data.json"
    
    func fetchSongs(modelContext: ModelContext) async throws {
        isSyncing = true
        progress = 0.05
        currentStage = .fetchingData
        statusMessage = "正在拉取核心数据文件..."
        
        let data: Data
        do {
            // Try to load from Bundle first (Offline Mode)
            if let bundleUrl = Bundle.main.url(forResource: "data", withExtension: "json") {
                data = try Data(contentsOf: bundleUrl)
            } else {
                guard let url = URL(string: DATA_URL) else { 
                    isSyncing = false
                    currentStage = .failed
                    return 
                }
                let (networkData, _) = try await URLSession.shared.data(from: url)
                data = networkData
            }
        } catch {
            isSyncing = false
            currentStage = .failed
            statusMessage = "网络错误: \(error.localizedDescription)"
            throw error
        }
        
        progress = 0.15
        currentStage = .fetchingAliases
        statusMessage = "连接至 LXNS 获取别名和 ID 映射..."
        
        let songData = try JSONDecoder().decode(SongData.self, from: data)
        
        // Fetch Aliases and LXNS Song List
        var aliasMap: [String: [String]] = [:]
        var titleToLxnsId: [String: Int] = [:]
        do {
            if let aliasUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/alias/list"),
               let lxnsSongUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/song/list") {
                
                async let (aliasData, _) = URLSession.shared.data(from: aliasUrl)
                async let (lxnsSongData, _) = URLSession.shared.data(from: lxnsSongUrl)
                
                let aliasResponse = try await JSONDecoder().decode(AliasListResponse.self, from: aliasData)
                progress = 0.25
                currentStage = .fetchingLxnsIds
                
                let lxnsSongResponse = try await JSONDecoder().decode(LxnsSongListResponse.self, from: lxnsSongData)
                progress = 0.35
                
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
        
        progress = 0.45
        currentStage = .processingSongs
        statusMessage = "对比现有库，处理 \(songData.songs.count) 首歌曲..."
        
        let existingSongs = try modelContext.fetch(FetchDescriptor<Song>())
        var songMap: [String: Song] = [:]
        for s in existingSongs {
            songMap[s.songId] = s
        }
        
        let totalCount = songData.songs.count
        for (index, internalSong) in songData.songs.enumerated() {
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
                    imageUrl: "",
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
            
            if let title = internalSong.title, let officialId = titleToLxnsId[title] {
                song.lxnsId = officialId
            }
            
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
            
            var sheetMap: [String: Sheet] = [:]
            for sheet in song.sheets {
                let key = "\(sheet.type)_\(sheet.difficulty)"
                sheetMap[key] = sheet
            }
            
            var newSheets: [Sheet] = []
            for internalSheet in internalSong.sheets {
                let key = "\(internalSheet.type)_\(internalSheet.difficulty)"
                
                if let existingSheet = sheetMap[key] {
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
            
            for sheet in song.sheets {
                let key = "\(sheet.type)_\(sheet.difficulty)"
                if !internalSong.sheets.contains(where: { "\($0.type)_\($0.difficulty)" == key }) {
                    if sheet.score == nil {
                        modelContext.delete(sheet)
                    }
                }
            }
            
            song.sheets = newSheets
            
            // Progress updates (from 45% to 90%)
            if index % 20 == 0 {
                progress = 0.45 + (Double(index) / Double(totalCount)) * 0.45
                statusMessage = "正在处理歌曲: \(song.title)"
                await Task.yield()
            }
        }
        
        currentStage = .saving
        statusMessage = "持久化本地数据..."
        progress = 0.95
        
        try modelContext.save()
        
        // Record sync time in config
        if let config = try? modelContext.fetch(FetchDescriptor<SyncConfig>()).first {
            config.lastStaticDataUpdateDate = Date()
        }
        
        UserDefaults.standard.set(true, forKey: "didPerformInitialSync")
        
        progress = 1.0
        currentStage = .completed
        statusMessage = "同步成功"
        
        try? await Task.sleep(nanoseconds: 1_000_000_000)
        isSyncing = false
    }
}
