import Foundation
import SwiftData
import UIKit
import Yams

struct AliasListResponse: Decodable {
    let aliases: [AliasItem]
}

struct AliasItem: Decodable {
    let song_id: Int
    let aliases: [String]
}

struct SongIdItem: Decodable {
    let id: Int
    let name: String
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
    
    nonisolated private init() {}
    
    enum SyncStage: String {
        case idle = "data.sync.stage.idle"
        case fetchingRemoteData = "data.sync.stage.fetchingRemoteData"
        case fetchingAliases = "data.sync.stage.fetchingAliases"
        case fetchingIcons = "data.sync.stage.fetchingIcons"
        case processingSongs = "data.sync.stage.processingSongs"
        case downloadingImages = "data.sync.stage.downloadingImages"
        case downloadingIcons = "data.sync.stage.downloadingIcons"
        case fetchingDanData = "data.sync.stage.fetchingDanData"
        case fetchingChartStats = "data.sync.stage.fetchingChartStats"
        case saving = "data.sync.stage.saving"
        case completed = "data.sync.stage.completed"
        case failed = "data.sync.stage.failed"
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
            return String(localized: "data.sync.eta.calculating")
        }
        let minutes = Int(eta) / 60
        let seconds = Int(eta) % 60
        if minutes > 0 {
            return String(localized: "data.sync.eta.minutes \(minutes) \(seconds)")
        } else {
            return String(localized: "data.sync.eta.seconds \(seconds)")
        }
    }
    
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
    
    struct SyncOptions: Sendable {
        var updateRemoteData = true
        var updateAliases = true
        var updateCovers = true
        var updateIcons = true
        var updateDanData = true
        var updateChartStats = true
    }
    
    func fetchSongs(modelContext: ModelContext, options: SyncOptions = SyncOptions()) async throws {
        isSyncing = true
        progress = 0.0
        syncStartTime = Date()
        estimatedTimeRemaining = nil
        syncLogs = ""
        log(String(localized: "data.sync.log.start"))
        
        do {
            var remoteSongs: [RemoteSong] = []
            var aliasMap: [String: [String]] = [:]
            var titleToSongId: [String: Int] = [:]
            var lxnsIcons: [LxnsPresetIcon] = []
            var nameToProviderIds: [String: [Int]] = [:]
            
            // --- 阶段 1: 远程 data.json ---
            if options.updateRemoteData {
                updateStage(.fetchingRemoteData, base: 0.1, message: String(localized: "data.sync.status.fetchingData"))
                guard let url = URL(string: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json") else {
                    throw URLError(.badURL)
                }
                let (data, _) = try await URLSession.shared.data(from: url)
                let response = try JSONDecoder().decode(RemoteDataResponse.self, from: data)
                remoteSongs = response.songs
                log(String(localized: "data.sync.log.fetchedData \(remoteSongs.count)"))
                
                if let encodedVersions = try? JSONEncoder().encode(response.versions) {
                    UserDefaults.app.maimaiVersionsData = encodedVersions
                }
                let sequence = response.versions.map { $0.version }
                UserDefaults.app.maimaiVersionSequence = sequence
                
                let catSequence = response.categories.map { $0.category }
                UserDefaults.app.maimaiCategorySequence = catSequence
            }
            
            // --- 阶段 2: Aliases & IDs ---
            if options.updateAliases {
                updateStage(.fetchingAliases, base: 0.30, message: String(localized: "data.sync.status.fetchingAliases"))
                if let aliasUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/alias/list"),
                   let lxnsSongUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/song/list"),
                   let providerIdUrl = URL(string: "https://maimaid.shikoch.in/songid.json") {
                    
                    async let aliasDataFetch = URLSession.shared.data(from: aliasUrl)
                    async let lxnsSongDataFetch = URLSession.shared.data(from: lxnsSongUrl)
                    async let providerIdDataFetch = URLSession.shared.data(from: providerIdUrl)
                    
                    if let (aliasData, _) = try? await aliasDataFetch,
                       let (lxnsSongData, _) = try? await lxnsSongDataFetch {
                        
                        let aliasResponse = try? JSONDecoder().decode(AliasListResponse.self, from: aliasData)
                        let lxnsSongResponse = try? JSONDecoder().decode(LxnsSongListResponse.self, from: lxnsSongData)
                        
                        if let aliasResponse = aliasResponse, let lxnsSongResponse = lxnsSongResponse {
                            var songIdToTitle: [Int: String] = [:]
                            for lxnsSong in lxnsSongResponse.songs {
                                songIdToTitle[lxnsSong.id] = lxnsSong.title
                                titleToSongId[lxnsSong.title] = lxnsSong.id
                            }
                            for item in aliasResponse.aliases {
                                if let title = songIdToTitle[item.song_id] {
                                    aliasMap[title] = item.aliases
                                }
                            }
                        }
                    }
                    
                    if let (providerIdData, _) = try? await providerIdDataFetch {
                        do {
                            let providerIds = try JSONDecoder().decode([SongIdItem].self, from: providerIdData)
                            for item in providerIds {
                                let rawName = item.name
                                let trimmed = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
                                let trimmedName = trimmed.isEmpty ? rawName : trimmed
                                nameToProviderIds[trimmedName, default: []].append(item.id)
                            }
                            log(String(localized: "data.sync.log.fetchedProviderIds \(providerIds.count)"))
                        } catch {
                            log(String(localized: "data.sync.log.providerIdError \(error.localizedDescription)"))
                        }
                    }
                }
            }
            
            // --- 阶段 3: Icons ---
            if options.updateIcons {
                updateStage(.fetchingIcons, base: 0.45, message: String(localized: "data.sync.status.fetchingIcons"))
                if let iconUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/icon/list") {
                    let (data, _) = try await URLSession.shared.data(from: iconUrl)
                    let response = try JSONDecoder().decode(LxnsPresetIconListResponse.self, from: data)
                    lxnsIcons = response.icons
                    log(String(localized: "data.sync.log.fetchedIcons \(lxnsIcons.count)"))
                }
            }
            
            // --- Stage 3.5: Dan Data ---
            if options.updateDanData {
                updateStage(.fetchingDanData, base: 0.50, message: String(localized: "data.sync.status.fetchingDanData"))
                if let danUrl = URL(string: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/gallery.yaml") {
                    let (data, _) = try await URLSession.shared.data(from: danUrl)
                    if let yamlString = String(data: data, encoding: .utf8) {
                        do {
                            let decoder = YAMLDecoder()
                            let decodedCategories = try decoder.decode([DanCategory].self, from: yamlString)
                            let cleanedCategories = sanitizeDanCategories(decodedCategories)
                            
                            let danJsonData = try JSONEncoder().encode(cleanedCategories)
                            let fileURL = getDanDataFileURL()
                            try danJsonData.write(to: fileURL)
                            log(String(localized: "data.sync.log.fetchedDanData \(cleanedCategories.count)"))
                        } catch {
                            log("Failed to parse Dan YAML: \(error)")
                        }
                    }
                }
            }

            if options.updateChartStats {
                updateStage(.fetchingChartStats, base: 0.53, message: "正在下载谱面拟合信息…")
                await ChartStatsService.shared.fetchStats(forceRefresh: true)
                log("谱面拟合信息已更新并写入本地缓存")
            }
            
            // --- 阶段 4: 合并数据入库 ---
            if options.updateRemoteData || options.updateAliases || options.updateIcons {
                updateStage(.processingSongs, base: 0.55, message: String(localized: "data.sync.status.processing"))
                
                let existingSongsFromDB = try modelContext.fetch(FetchDescriptor<Song>())
                var existingSongMap: [String: Song] = [:]
                for s in existingSongsFromDB {
                    existingSongMap[s.songIdentifier] = s
                }
                
                var songsToProcess: [RemoteSong] = remoteSongs
                if !options.updateRemoteData {
                    songsToProcess = existingSongsFromDB.map { s in
                        RemoteSong(
                            songId: s.songIdentifier,
                            category: s.category,
                            title: s.title,
                            artist: s.artist,
                            bpm: s.bpm,
                            imageName: s.imageName,
                            version: s.version,
                            releaseDate: s.releaseDate,
                            isNew: s.isNew,
                            isLocked: s.isLocked,
                            comment: s.comment,
                            sheets: s.sheets.map { sh in
                                RemoteSheet(
                                    type: sh.type,
                                    difficulty: sh.difficulty,
                                    level: sh.level,
                                    levelValue: sh.levelValue,
                                    internalLevel: sh.internalLevel,
                                    internalLevelValue: sh.internalLevelValue,
                                    noteDesigner: sh.noteDesigner,
                                    noteCounts: RemoteNoteCounts(
                                        tap: sh.tap,
                                        hold: sh.hold,
                                        slide: sh.slide,
                                        touch: sh.touch,
                                        breakNote: sh.breakCount,
                                        total: sh.total
                                    ),
                                    regions: [
                                        "jp": sh.regionJp,
                                        "intl": sh.regionIntl,
                                        "usa": sh.regionUsa,
                                        "cn": sh.regionCn
                                    ],
                                    isSpecial: nil
                                )
                            }
                        )
                    }
                } else {
                    let currentRemoteIds = Set(remoteSongs.map { $0.songId })
                    for existing in existingSongsFromDB {
                        if !currentRemoteIds.contains(existing.songIdentifier) {
                            modelContext.delete(existing)
                        }
                    }
                }
                
                var providerMatchCount = 0
                var sheetMatchCount = 0
                
                for (index, remoteSong) in songsToProcess.enumerated() {
                    let song: Song
                    if let existing = existingSongMap[remoteSong.songId] {
                        song = existing
                    } else if options.updateRemoteData {
                        song = Song(
                            songIdentifier: remoteSong.songId,
                            category: remoteSong.category ?? "",
                            title: {
                                let t = remoteSong.title ?? ""
                                let trimmed = t.trimmingCharacters(in: .whitespacesAndNewlines)
                                return trimmed.isEmpty ? t : trimmed
                            }(),
                            artist: remoteSong.artist ?? "",
                            imageName: (remoteSong.imageName ?? "").trimmingCharacters(in: .whitespacesAndNewlines),
                            version: remoteSong.version ?? "",
                            releaseDate: remoteSong.releaseDate ?? "",
                            sortOrder: 0,
                            bpm: remoteSong.bpm,
                            isNew: remoteSong.isNew ?? false,
                            isLocked: remoteSong.isLocked ?? false,
                            comment: remoteSong.comment
                        )
                        modelContext.insert(song)
                        log(String(localized: "data.sync.log.newSong \(song.title)"))
                    } else {
                        continue
                    }
                    
                    if options.updateRemoteData {
                        song.category = remoteSong.category ?? ""
                        let newTitle = remoteSong.title ?? ""
                        let trimmedTitle = newTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                        song.title = trimmedTitle.isEmpty ? newTitle : trimmedTitle
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
                        if let officialId = titleToSongId[song.title] {
                            song.songId = officialId
                        }
                        if let aliases = aliasMap[song.title], !aliases.isEmpty {
                            song.aliases = aliases
                        }
                        
                        let trimmedSearch = song.title.trimmingCharacters(in: .whitespacesAndNewlines)
                        let searchTitle = trimmedSearch.isEmpty ? song.title : trimmedSearch
                        if let possibleIds = nameToProviderIds[searchTitle] {
                            let hasUtage = remoteSong.sheets.contains { $0.type.lowercased() == "utage" }
                            let hasDX = remoteSong.sheets.contains { $0.type.lowercased() == "dx" }
                            let hasStd = remoteSong.sheets.contains { $0.type.lowercased() == "std" }
                            
                            var assignedId: Int? = nil
                            if hasUtage {
                                assignedId = possibleIds.first { $0 >= 100000 }
                            }
                            if assignedId == nil && hasDX {
                                assignedId = possibleIds.first { $0 >= 10000 && $0 < 100000 }
                            }
                            if assignedId == nil && hasStd {
                                assignedId = possibleIds.first { $0 < 10000 }
                            }
                            
                            if let finalId = assignedId {
                                song.songId = finalId
                                providerMatchCount += 1
                            }
                        }
                    }
                    
                    var sheetMap: [String: Sheet] = [:]
                    for sh in song.sheets {
                        sheetMap["\(sh.type)_\(sh.difficulty)"] = sh
                    }
                    
                    for remoteSheet in remoteSong.sheets {
                        let key = "\(remoteSheet.type)_\(remoteSheet.difficulty)"
                        let sheet: Sheet
                        if let existingSheet = sheetMap[key] {
                            sheet = existingSheet
                        } else {
                            sheet = Sheet(
                                songIdentifier: song.songIdentifier,
                                type: remoteSheet.type,
                                difficulty: remoteSheet.difficulty,
                                level: remoteSheet.level,
                                levelValue: remoteSheet.levelValue ?? 0
                            )
                            sheet.song = song
                            modelContext.insert(sheet)
                            if song.sheets.isEmpty {
                                song.sheets = [sheet]
                            } else {
                                song.sheets.append(sheet)
                            }
                        }
                        
                        let trimmedSearchSheet = song.title.trimmingCharacters(in: .whitespacesAndNewlines)
                        let searchTitle = trimmedSearchSheet.isEmpty ? song.title : trimmedSearchSheet
                        if let possibleIds = nameToProviderIds[searchTitle] {
                            let type = remoteSheet.type.lowercased()
                            if type == "utage" {
                                if let id = possibleIds.first(where: { $0 >= 100000 }) { sheet.songId = id }
                            } else if type == "dx" {
                                if let id = possibleIds.first(where: { $0 >= 10000 && $0 < 100000 }) { sheet.songId = id }
                            } else if type == "std" {
                                if let id = possibleIds.first(where: { $0 < 10000 }) { sheet.songId = id }
                            }
                            if sheet.songId > 0 {
                                sheetMatchCount += 1
                            }
                        }
                        
                        if options.updateRemoteData {
                            sheet.level = remoteSheet.level
                            sheet.levelValue = remoteSheet.levelValue ?? 0
                            sheet.internalLevel = remoteSheet.internalLevel
                            sheet.internalLevelValue = remoteSheet.internalLevelValue
                            sheet.noteDesigner = remoteSheet.noteDesigner
                            
                            if let nc = remoteSheet.noteCounts {
                                sheet.tap = nc.tap
                                sheet.hold = nc.hold
                                sheet.slide = nc.slide
                                sheet.touch = nc.touch
                                sheet.breakCount = nc.breakNote
                                sheet.total = nc.total
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
                            let hasPlayRecords = !(sh.playRecords?.isEmpty ?? true)
                            if !currentSheetKeys.contains(key) && sh.scores.isEmpty && !hasPlayRecords {
                                modelContext.delete(sh)
                            }
                        }
                    }
                    
                    if index % 100 == 0 {
                        updateProgress(
                            Double(index) / Double(max(songsToProcess.count, 1)),
                            totalForStage: 0.20,
                            baseForStage: 0.55,
                            status: String(localized: "data.sync.status.processingSong \(song.title)")
                        )
                        do {
                            try modelContext.save()
                        } catch {
                            log("Intermediate save failed while processing songs: \(error.localizedDescription)")
                        }
                    }
                }
                
                log(String(localized: "data.sync.log.processingCompleted \(providerMatchCount) \(songsToProcess.count)"))
                log(String(localized: "data.sync.log.syncedSummary \(providerMatchCount) \(sheetMatchCount)"))
                
                remoteSongs = []
                aliasMap = [:]
                titleToSongId = [:]
                songsToProcess = []
                existingSongMap = [:]
                
                if options.updateIcons && !lxnsIcons.isEmpty {
                    let existingIcons = try modelContext.fetch(FetchDescriptor<MaimaiIcon>())
                    var existingIconMap: [Int: MaimaiIcon] = [:]
                    for icon in existingIcons {
                        existingIconMap[icon.id] = icon
                    }
                    
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
                updateStage(.downloadingImages, base: 0.75, message: String(localized: "data.sync.status.scanningCovers"))
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
                        updateProgress(
                            Double(chunk) / Double(max(coverDownloadTasks.count, 1)),
                            totalForStage: 0.15,
                            baseForStage: 0.75,
                            status: String(localized: "data.sync.status.downloadingCovers \(chunk) \(coverDownloadTasks.count)")
                        )
                        await withTaskGroup(of: Void.self) { group in
                            for task in subTasks {
                                group.addTask {
                                    _ = try? await ImageDownloader.shared.downloadImage(from: task.0, as: task.1)
                                }
                            }
                        }
                    }
                }
            }
            
            // --- 阶段 6: 下载预设头像 ---
            if options.updateIcons {
                updateStage(.downloadingIcons, base: 0.85, message: String(localized: "data.sync.status.downloadingIcons"))
                let descriptor = FetchDescriptor<MaimaiIcon>()
                let allIcons = try modelContext.fetch(descriptor)
                var iconDownloadTasks: [(String, Int)] = []
                for icon in allIcons {
                    if !ImageDownloader.shared.iconExists(iconId: icon.id) {
                        iconDownloadTasks.append((icon.iconUrl, icon.id))
                    }
                }
                
                if !iconDownloadTasks.isEmpty {
                    log(String(localized: "data.sync.log.downloadingIcons \(iconDownloadTasks.count)"))
                    let total = Double(iconDownloadTasks.count)
                    var completed = 0
                    
                    let batchSize = 30
                    for chunk in stride(from: 0, to: iconDownloadTasks.count, by: batchSize) {
                        let endIndex = min(chunk + batchSize, iconDownloadTasks.count)
                        let subTasks = iconDownloadTasks[chunk..<endIndex]
                        
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
                                    updateProgress(
                                        Double(completed) / max(total, 1),
                                        totalForStage: 0.10,
                                        baseForStage: 0.85,
                                        status: String(localized: "data.sync.status.downloadingIconsProgress \(completed) \(Int(total))")
                                    )
                                }
                            }
                        }
                    }
                }
            }
            
            // --- 阶段 7: 持久化 ---
            updateStage(.saving, base: 0.95, message: String(localized: "data.sync.status.saving"))
            try modelContext.save()
            
            if let config = try? modelContext.fetch(FetchDescriptor<SyncConfig>()).first {
                config.lastStaticDataUpdateDate = Date()
            } else {
                let newConfig = SyncConfig()
                newConfig.lastStaticDataUpdateDate = Date()
                modelContext.insert(newConfig)
            }
            
            UserDefaults.app.didPerformInitialSync = true
            updateStage(.completed, base: 1.0, message: String(localized: "data.sync.status.completed"))
            _ = try? await Task.sleep(nanoseconds: 1_000_000_000)
            isSyncing = false
        } catch {
            print("Fetch failed: \(error)")
            updateStage(.failed, base: 0.0, message: String(localized: "data.sync.status.failed \(error.localizedDescription)"))
            isSyncing = false
            throw error
        }
    }
    
    func getDanDataFileURL() -> URL {
        let paths = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)
        return paths[0].appendingPathComponent("dan_data.json")
    }
    
    func loadCachedDanData() -> [DanCategory] {
        let fileURL = getDanDataFileURL()
        guard let data = try? Data(contentsOf: fileURL),
              let categories = try? JSONDecoder().decode([DanCategory].self, from: data) else {
            return []
        }
        
        return sanitizeDanCategories(categories)
    }
    
    // MARK: - Dan Sanitization
    
    private func sanitizeDanCategories(_ categories: [DanCategory]) -> [DanCategory] {
        var result: [DanCategory] = []
        
        for category in categories {
            let categoryTitle = category.title.trimmingCharacters(in: .whitespacesAndNewlines)
            let lowerTitle = categoryTitle.lowercased()
            
            if lowerTitle.contains("test") || lowerTitle.contains("author's choice") {
                continue
            }
            
            var cleanedSections: [DanSection] = []
            
            for section in category.sections {
                let cleanedRefs = section.sheets.filter { isValidDanRawSheetRef($0) }
                
                guard !cleanedRefs.isEmpty else { continue }
                
                let cleanedDescriptions: [String]? = {
                    guard let descriptions = section.sheetDescriptions else { return nil }
                    let paired = zip(section.sheets, descriptions)
                        .filter { isValidDanRawSheetRef($0.0) }
                        .map { $0.1 }
                    return paired.isEmpty ? nil : paired
                }()
                
                let cleanedTitle: String? = {
                    let trimmed = section.title?.trimmingCharacters(in: .whitespacesAndNewlines)
                    return (trimmed?.isEmpty == false) ? trimmed : nil
                }()
                
                let cleanedDescription: String? = {
                    let trimmed = section.description?.trimmingCharacters(in: .whitespacesAndNewlines)
                    return (trimmed?.isEmpty == false) ? trimmed : nil
                }()
                
                cleanedSections.append(
                    DanSection(
                        title: cleanedTitle,
                        description: cleanedDescription,
                        sheets: cleanedRefs,
                        sheetDescriptions: cleanedDescriptions
                    )
                )
            }
            
            guard !cleanedSections.isEmpty else { continue }
            
            result.append(
                DanCategory(
                    title: category.title,
                    id: category.id,
                    sections: cleanedSections
                )
            )
        }
        
        return result
    }
    
    private func isValidDanRawSheetRef(_ raw: String) -> Bool {
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return false }
        
        let ref = DanSheetRef(raw: trimmed)
        guard !ref.title.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty else { return false }
        guard !ref.isPlaceholder else { return false }
        
        let type = ref.type.lowercased()
        let difficulty = ref.difficulty.lowercased()
        
        if type.contains("utage") { return false }
        if difficulty.contains("utage") { return false }
        
        let validTypes = Set(["dx", "std"])
        guard validTypes.contains(type) else { return false }
        
        let validDifficulties = Set(["basic", "advanced", "expert", "master", "remaster"])
        guard validDifficulties.contains(difficulty) else { return false }
        
        return true
    }
}
