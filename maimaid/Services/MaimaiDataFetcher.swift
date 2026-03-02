import Foundation
import SwiftData
import UIKit

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

struct LxnsPresetIconListResponse: Decodable {
    let icons: [LxnsPresetIcon]
}

struct LxnsPresetIcon: Decodable {
    let id: Int
    let name: String
    let description: String
    let genre: String
}

struct LxnsSong: Decodable {
    let id: Int
    let title: String
    let artist: String
}

struct RemoteDataResponse: Decodable {
    let songs: [RemoteSong]
    let categories: [RemoteCategory]
    let versions: [RemoteVersion]
}

struct RemoteVersion: Codable {
    let version: String
    let abbr: String
    let releaseDate: String?
}

struct RemoteCategory: Decodable {
    let category: String
}

struct RemoteSong: Decodable {
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
    let sheets: [RemoteSheet]
}

struct RemoteSheet: Decodable {
    let type: String
    let difficulty: String
    let level: String
    let levelValue: Double?
    let internalLevel: String?
    let internalLevelValue: Double?
    let noteDesigner: String?
    let noteCounts: RemoteNoteCounts?
    let regions: [String: Bool]?
    let isSpecial: Bool?
}

struct RemoteNoteCounts: Decodable {
    let tap: Int?
    let hold: Int?
    let slide: Int?
    let touch: Int?
    let breakNote: Int?
    let total: Int?
    
    enum CodingKeys: String, CodingKey {
        case tap, hold, slide, touch, total
        case breakNote = "break"
    }
}

@Observable
@MainActor
class MaimaiDataFetcher {
    static let shared = MaimaiDataFetcher()
    
    private init() {}
    
    enum SyncStage: String {
        case idle = "等待中"
        case fetchingRemoteData = "［1/6］获取汇总数据 (data.json)..."
        case fetchingAliases = "［2/6］获取歌曲别名与歌曲 ID..."
        case fetchingIcons = "［3/6］获取预设头像..."
        case processingSongs = "［4/6］正在合并并分析数据..."
        case downloadingImages = "［5/7］正在并发下载歌曲封面..."
        case downloadingIcons = "［6/7］正在并发下载预设头像..."
        case saving = "［7/7］正在保存到本地数据库..."
        case completed = "同步完成"
        case failed = "同步失败"
    }
    
    var isSyncing = false
    var currentStage: SyncStage = .idle
    var progress: Double = 0
    var statusMessage: String = ""
    var syncLogs: String = ""
    var estimatedTimeRemaining: TimeInterval? = nil
    
    func log(_ message: String) {
        print(message)
        self.syncLogs += "[\(Date().formatted(date: .omitted, time: .standard))] \(message)\n"
    }
    private var syncStartTime: Date? = nil
    
    var formattedETA: String {
        guard let eta = estimatedTimeRemaining, eta > 0 else {
            return "计算中..."
        }
        let minutes = Int(eta) / 60
        let seconds = Int(eta) % 60
        if minutes > 0 {
            return String(format: "约剩余 %d分%02d秒", minutes, seconds)
        } else {
            return String(format: "约剩余 %d秒", seconds)
        }
    }
    
    // State references to update from backgrounds
    private func updateProgress(_ subProgress: Double, totalForStage: Double, baseForStage: Double, status: String) {
        self.progress = baseForStage + (subProgress * totalForStage)
        self.statusMessage = status
        
        if let start = self.syncStartTime, self.progress > 0.02 {
            let elapsed = Date().timeIntervalSince(start)
            let totalEstimated = elapsed / self.progress
            self.estimatedTimeRemaining = max(0, totalEstimated - elapsed)
        }
    }
    
    private func updateStage(_ stage: SyncStage, base: Double, message: String) {
        self.currentStage = stage
        self.progress = base
        self.statusMessage = message
        self.log(message)
    }
    
    struct SyncOptions {
        var updateRemoteData = true
        var updateAliases = true
        var updateCovers = true
        var updateIcons = true
    }
    
    func fetchSongs(modelContext: ModelContext, options: SyncOptions = SyncOptions()) async throws {
        isSyncing = true
        progress = 0.0
        syncStartTime = Date()
        estimatedTimeRemaining = nil
        syncLogs = ""
        log("开始更新流程...")
        
        do {
            var remoteSongs: [RemoteSong] = []
            var aliasMap: [String: [String]] = [:]
            var titleToLxnsId: [String: Int] = [:]
            var lxnsIcons: [LxnsPresetIcon] = []
            
            // --- 阶段 1: 远程 data.json ---
            if options.updateRemoteData {
                updateStage(.fetchingRemoteData, base: 0.1, message: "下载远程 data.json...")
                // source 2: https://maimaid.shikoch.in/maimai/data.json
                guard let url = URL(string: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json") else {
                    throw URLError(.badURL)
                }
                let (data, _) = try await URLSession.shared.data(from: url)
                let response = try JSONDecoder().decode(RemoteDataResponse.self, from: data)
                remoteSongs = response.songs
                log("成功下载数据，共 \(remoteSongs.count) 首歌曲")
                
                // 存一下版本序列用于排序
                if let encodedVersions = try? JSONEncoder().encode(response.versions) {
                    UserDefaults.standard.set(encodedVersions, forKey: "MaimaiVersionsData")
                }
                let sequence = response.versions.map { $0.version }
                UserDefaults.standard.set(sequence, forKey: "MaimaiVersionSequence")
                
                let catSequence = response.categories.map { $0.category }
                UserDefaults.standard.set(catSequence, forKey: "MaimaiCategorySequence")
            }
            
            // --- 阶段 2: Aliases & IDs ---
            if options.updateAliases {
                updateStage(.fetchingAliases, base: 0.30, message: "连接 LXNS API 获取别名...")
                if let aliasUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/alias/list"),
                   let lxnsSongUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/song/list") {
                    
                    async let aliasDataFetch = URLSession.shared.data(from: aliasUrl)
                    async let lxnsSongDataFetch = URLSession.shared.data(from: lxnsSongUrl)
                    
                    if let (aliasData, _) = try? await aliasDataFetch,
                       let (lxnsSongData, _) = try? await lxnsSongDataFetch {
                        
                        let aliasResponse = try? JSONDecoder().decode(AliasListResponse.self, from: aliasData)
                        let lxnsSongResponse = try? JSONDecoder().decode(LxnsSongListResponse.self, from: lxnsSongData)
                        
                        if let aliasResponse = aliasResponse, let lxnsSongResponse = lxnsSongResponse {
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
                    }
                }
            }
            
            // --- 阶段 3: Icons ---
            if options.updateIcons {
                updateStage(.fetchingIcons, base: 0.45, message: "获取预设头像列表...")
                if let iconUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/icon/list") {
                    let (data, _) = try await URLSession.shared.data(from: iconUrl)
                    let response = try JSONDecoder().decode(LxnsPresetIconListResponse.self, from: data)
                    lxnsIcons = response.icons
                    log("成功获取 \(lxnsIcons.count) 个预设头像")
                }
            }
            
            // --- 阶段 4: 合并数据入库 ---
            if options.updateRemoteData || options.updateAliases || options.updateIcons {
                updateStage(.processingSongs, base: 0.55, message: "分析整理并入库本地乐曲数据...")
                
                let existingSongsFromDB = try modelContext.fetch(FetchDescriptor<Song>())
                var existingSongMap: [String: Song] = [:]
                for s in existingSongsFromDB { existingSongMap[s.songId] = s }

                var songsToProcess: [RemoteSong] = remoteSongs
                if !options.updateRemoteData {
                    songsToProcess = existingSongsFromDB.map { s in
                        RemoteSong(
                            songId: s.songId, category: s.category, title: s.title, artist: s.artist, bpm: s.bpm,
                            imageName: s.imageName, version: s.version, releaseDate: s.releaseDate,
                            isNew: s.isNew, isLocked: s.isLocked, comment: s.comment,
                            sheets: s.sheets.map { sh in
                                RemoteSheet(
                                    type: sh.type, difficulty: sh.difficulty, level: sh.level, levelValue: sh.levelValue,
                                    internalLevel: sh.internalLevel, internalLevelValue: sh.internalLevelValue,
                                    noteDesigner: sh.noteDesigner,
                                    noteCounts: RemoteNoteCounts(tap: sh.tap, hold: sh.hold, slide: sh.slide, touch: sh.touch, breakNote: sh.breakCount, total: sh.total),
                                    regions: ["jp": sh.regionJp, "intl": sh.regionIntl, "usa": sh.regionUsa, "cn": sh.regionCn],
                                    isSpecial: nil
                                )
                            }
                        )
                    }
                } else {
                    let currentRemoteIds = Set(remoteSongs.map { $0.songId })
                    for existing in existingSongsFromDB {
                        if !currentRemoteIds.contains(existing.songId) {
                            modelContext.delete(existing)
                        }
                    }
                }
                
                for (index, remoteSong) in songsToProcess.enumerated() {
                    let song: Song
                    if let existing = existingSongMap[remoteSong.songId] {
                        song = existing
                    } else if options.updateRemoteData {
                        song = Song(
                            songId: remoteSong.songId, category: remoteSong.category ?? "", 
                            title: (remoteSong.title ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
                            artist: remoteSong.artist ?? "", 
                            imageName: (remoteSong.imageName ?? "").trimmingCharacters(in: .whitespacesAndNewlines),                             version: remoteSong.version ?? "", releaseDate: remoteSong.releaseDate ?? "", sortOrder: 0,
                            bpm: remoteSong.bpm, isNew: remoteSong.isNew ?? false, isLocked: remoteSong.isLocked ?? false,
                            comment: remoteSong.comment
                        )
                        modelContext.insert(song)
                        log("新增歌曲: \(song.title)")
                    } else { continue }
                    
                    if options.updateRemoteData {
                        song.category = remoteSong.category ?? ""
                        song.title = (remoteSong.title ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                        song.artist = remoteSong.artist ?? ""
                        song.imageName = (remoteSong.imageName ?? "").trimmingCharacters(in: .whitespacesAndNewlines)
                        song.bpm = remoteSong.bpm
                        song.isNew = remoteSong.isNew ?? false
                        song.isLocked = remoteSong.isLocked ?? false
                        song.comment = remoteSong.comment
                        if let version = remoteSong.version { song.version = version }
                        if let date = remoteSong.releaseDate { song.releaseDate = date }
                    }
                    
                    if options.updateAliases {
                        if let officialId = titleToLxnsId[song.title] { song.lxnsId = officialId }
                        if let aliases = aliasMap[song.title], !aliases.isEmpty { song.aliases = aliases }
                    }
                    
                    var sheetMap: [String: Sheet] = [:]
                    for sh in song.sheets { sheetMap["\(sh.type)_\(sh.difficulty)"] = sh }
                    
                    for remoteSheet in remoteSong.sheets {
                        let key = "\(remoteSheet.type)_\(remoteSheet.difficulty)"
                        let sheet: Sheet
                        if let existingSheet = sheetMap[key] {
                            sheet = existingSheet
                        } else {
                            sheet = Sheet(
                                songId: song.songId, type: remoteSheet.type, difficulty: remoteSheet.difficulty,
                                level: remoteSheet.level, levelValue: remoteSheet.levelValue ?? 0
                            )
                            sheet.song = song
                            modelContext.insert(sheet)
                            if song.sheets.isEmpty { song.sheets = [sheet] } else { song.sheets.append(sheet) }
                        }
                        
                        if options.updateRemoteData {
                            sheet.level = remoteSheet.level
                            sheet.levelValue = remoteSheet.levelValue ?? 0
                            sheet.internalLevel = remoteSheet.internalLevel
                            sheet.internalLevelValue = remoteSheet.internalLevelValue
                            sheet.noteDesigner = remoteSheet.noteDesigner
                            
                            if let nc = remoteSheet.noteCounts {
                                sheet.tap = nc.tap; sheet.hold = nc.hold; sheet.slide = nc.slide; sheet.touch = nc.touch;
                                sheet.breakCount = nc.breakNote; sheet.total = nc.total
                            }
                            
                            if let regions = remoteSheet.regions {
                                sheet.regionJp = regions["jp"] ?? false
                                sheet.regionIntl = regions["intl"] ?? false
                                sheet.regionUsa = regions["usa"] ?? false
                                sheet.regionCn = regions["cn"] ?? false
                            }
                        }
                    }
                    
                    if options.updateRemoteData {
                        let currentSheetKeys = Set(remoteSong.sheets.map { "\($0.type)_\($0.difficulty)" })
                        for sh in song.sheets {
                            let key = "\(sh.type)_\(sh.difficulty)"
                            if !currentSheetKeys.contains(key) && sh.score == nil {
                                modelContext.delete(sh)
                            }
                        }
                    }
                    
                    if index % 100 == 0 {
                        updateProgress(Double(index) / Double(songsToProcess.count), totalForStage: 0.20, baseForStage: 0.55, status: "整理数据: \(song.title)")
                        try? modelContext.save()
                    }
                }
                
                // Clear temporary song data
                remoteSongs = []
                aliasMap = [:]
                titleToLxnsId = [:]
                songsToProcess = []
                existingSongMap = [:]
                
                // 处理 Icons 入库
                if options.updateIcons && !lxnsIcons.isEmpty {
                    let existingIcons = try modelContext.fetch(FetchDescriptor<MaimaiIcon>())
                    var existingIconMap: [Int: MaimaiIcon] = [:]
                    for icon in existingIcons { existingIconMap[icon.id] = icon }
                    
                    for lxIcon in lxnsIcons {
                        if let existing = existingIconMap[lxIcon.id] {
                            existing.name = lxIcon.name
                            existing.descriptionText = lxIcon.description
                            existing.genre = lxIcon.genre
                        } else {
                            let newIcon = MaimaiIcon(
                                id: lxIcon.id,
                                name: lxIcon.name,
                                descriptionText: lxIcon.description,
                                genre: lxIcon.genre
                            )
                            modelContext.insert(newIcon)
                        }
                    }
                    lxnsIcons = []
                }
            }

            // --- 阶段 5: 下载图片资源 ---
            if options.updateCovers {
                updateStage(.downloadingImages, base: 0.75, message: "扫描并下载缺失封面...")
                let descriptor = FetchDescriptor<Song>()
                let allSongs = try modelContext.fetch(descriptor)
                var coverDownloadTasks: [(String, String)] = []
                for song in allSongs {
                    if !ImageDownloader.shared.imageExists(imageName: song.imageName) {
                        coverDownloadTasks.append(("https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/\(song.imageName)", song.imageName))
                    }
                }
                
                if !coverDownloadTasks.isEmpty {
                    let batchSize = 30
                    for chunk in stride(from: 0, to: coverDownloadTasks.count, by: batchSize) {
                        let endIndex = min(chunk + batchSize, coverDownloadTasks.count)
                        let subTasks = coverDownloadTasks[chunk..<endIndex]
                        updateProgress(Double(chunk) / Double(coverDownloadTasks.count), totalForStage: 0.15, baseForStage: 0.75, status: "下载封面 (\(chunk)/\(coverDownloadTasks.count))")
                        await withTaskGroup(of: Void.self) { group in
                            for task in subTasks {
                                group.addTask { _ = try? await ImageDownloader.shared.downloadImage(from: task.0, as: task.1) }
                            }
                        }
                    }
                }
            }
            
            // --- 阶段 6: 下载预设头像 ---
            if options.updateIcons {
                updateStage(.downloadingIcons, base: 0.85, message: "下载预设头像...")
                let descriptor = FetchDescriptor<MaimaiIcon>()
                let allIcons = try modelContext.fetch(descriptor)
                var iconDownloadTasks: [(String, Int)] = []
                for icon in allIcons {
                    if !ImageDownloader.shared.iconExists(iconId: icon.id) {
                        iconDownloadTasks.append((icon.iconUrl, icon.id))
                    }
                }
                
                if !iconDownloadTasks.isEmpty {
                    log("正在下载 \(iconDownloadTasks.count) 个缺失的头像...")
                    let total = Double(iconDownloadTasks.count)
                    var completed = 0
                    
                    let batchSize = 30
                    for chunk in stride(from: 0, to: iconDownloadTasks.count, by: batchSize) {
                        let endIndex = min(chunk + batchSize, iconDownloadTasks.count)
                        let subTasks = iconDownloadTasks[chunk..<endIndex]
                        
                        // Concurrent downloading for this batch
                        await withTaskGroup(of: Void.self) { group in
                            for task in subTasks {
                                group.addTask {
                                    do {
                                        _ = try await ImageDownloader.shared.downloadIcon(from: task.0, id: task.1)
                                    } catch {
                                        print("Failed to download icon \(task.1): \(error)")
                                    }
                                }
                            }
                            
                            for await _ in group {
                                completed += 1
                                if completed % 10 == 0 || completed == iconDownloadTasks.count {
                                    updateProgress(Double(completed) / total, totalForStage: 0.10, baseForStage: 0.85, status: "下载头像: \(completed)/\(Int(total))")
                                }
                            }
                        }
                    }
                }
            }
            
            // --- 阶段 7: 持久化 ---
            updateStage(.saving, base: 0.95, message: "持久化数据...")
            try modelContext.save()
            
            if let config = try? modelContext.fetch(FetchDescriptor<SyncConfig>()).first {
                config.lastStaticDataUpdateDate = Date()
            } else {
                let newConfig = SyncConfig()
                newConfig.lastStaticDataUpdateDate = Date()
                modelContext.insert(newConfig)
            }
            UserDefaults.standard.set(true, forKey: "didPerformInitialSync")
            updateStage(.completed, base: 1.0, message: "更新完成！")
            _ = try? await Task.sleep(nanoseconds: 1_000_000_000)
            isSyncing = false
        } catch {
            print("Fetch failed: \(error)")
            updateStage(.failed, base: 0.0, message: "异常: \(error.localizedDescription)")
            isSyncing = false
            throw error
        }
    }
}
