import Foundation
import OSLog
import SwiftData
import UIKit

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

private struct BackendStaticManifestResponse: Decodable {
    let version: String
    let md5: String
    let createdAt: Date?
}

private struct BackendStaticBundleResponse: Decodable {
    let version: String
    let md5: String
    let payload: BackendStaticBundlePayload
}

private struct BackendStaticBundlePayload: Decodable {
    let resources: BackendStaticBundleResources
}

private struct BackendStaticBundleResources: Decodable {
    let dataJSON: RemoteDataResponse?
    let songIDJSON: [SongIdItem]?
    let utageNoteJSON: [UtageChartStatsItem]?
    let lxnsAliases: AliasListResponse?
    let danInfo: [DanCategory]?

    enum CodingKeys: String, CodingKey {
        case dataJSON = "data_json"
        case songIDJSON = "songid_json"
        case utageNoteJSON = "utage_note_json"
        case lxnsAliases = "lxns_aliases"
        case danInfo = "dan_info"
    }
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
    let version: String?
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

struct UtageChartStatsItem: Decodable {
    let id: Int
    let title: String
    let notes: Int
}

@Observable
@MainActor
class MaimaiDataFetcher {
    static let shared = MaimaiDataFetcher()
    private let logger = Logger(
        subsystem: Bundle.main.bundleIdentifier ?? "maimaid",
        category: "MaimaiDataFetcher"
    )
    
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
        case fetchingUtageChartStats = "data.sync.stage.fetchingUtageChartStats"
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
        logger.info("\(message, privacy: .public)")
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
    
    nonisolated struct SyncOptions: Sendable {
        var updateRemoteData = true
        var updateAliases = true
        var updateCovers = true
        var updateIcons = true
        var updateDanData = true
        var updateChartStats = true
        var updateUtageChartStats = true
    }

    /// Sync approved community aliases into local Song/CommunityAliasCache.
    /// This path is intentionally login-agnostic (anonymous users can also pull approved aliases).
    func syncApprovedCommunityAliasesIfNeeded(
        container: ModelContainer,
        minimumInterval: TimeInterval = 10 * 60
    ) async {
        if let lastPoll = UserDefaults.app.communityAliasLastPollAt,
           Date().timeIntervalSince(lastPoll) < minimumInterval {
            return
        }

        UserDefaults.app.communityAliasLastPollAt = Date()
        let context = ModelContext(container)
        await CommunityAliasService.shared.syncApprovedAliasesIntoSongs(modelContext: context)
    }

    /// Force a full approved-alias pull, typically after static data refresh.
    func forceSyncApprovedCommunityAliases(modelContext: ModelContext) async {
        await CommunityAliasService.shared.syncApprovedAliasesIntoSongs(modelContext: modelContext, force: true)
    }

    private func fetchBackendStaticBundleIfNeeded(forceApply: Bool = false) async throws -> (bundle: BackendStaticBundleResponse?, isUpToDate: Bool) {
        guard BackendSessionManager.shared.isConfigured else {
            return (nil, false)
        }

        let manifest: BackendStaticManifestResponse = try await BackendAPIClient.request(
            path: "v1/static/manifest",
            method: "GET",
            authentication: .none
        )

        if !forceApply, UserDefaults.app.staticBundleMd5 == manifest.md5 {
            return (nil, true)
        }

        let encodedVersion = manifest.version.addingPercentEncoding(withAllowedCharacters: .urlPathAllowed) ?? manifest.version
        let bundle: BackendStaticBundleResponse = try await BackendAPIClient.request(
            path: "v1/static/bundle/\(encodedVersion)",
            method: "GET",
            authentication: .none
        )
        return (bundle, false)
    }
    
    func fetchSongs(
        modelContext: ModelContext,
        options: SyncOptions = SyncOptions(),
        forceBundleApply: Bool = false
    ) async throws {
        isSyncing = true
        progress = 0.0
        syncStartTime = Date()
        estimatedTimeRemaining = nil
        syncLogs = ""
        log(String(localized: "data.sync.log.start"))
        
        do {
            var remoteSongs: [RemoteSong] = []
            var aliasMap: [String: [String]] = [:]
            var aliasMapByNormalizedTitle: [String: [String]] = [:]
            var hasFreshAliasSnapshot = false
            var titleToSongId: [String: Int] = [:]
            var titleToSongIdByNormalized: [String: Int] = [:]
            var lxnsIcons: [LxnsPresetIcon] = []
            var nameToProviderIds: [String: [Int]] = [:]
            var nameToProviderIdsByNormalized: [String: [Int]] = [:]
            var utageNotesById: [Int: Int] = [:]
            var utageNotesByKey: [String: [Int]] = [:]
            let staticBundle = try? await fetchBackendStaticBundleIfNeeded(forceApply: forceBundleApply)
            let bundleResources = staticBundle?.bundle?.payload.resources

            if staticBundle?.isUpToDate == true {
                updateStage(.completed, base: 1.0, message: "No static data update available.")
                isSyncing = false
                return
            }
            
            // --- 阶段 1: 远程 data.json ---
            if options.updateRemoteData {
                updateStage(.fetchingRemoteData, base: 0.1, message: String(localized: "data.sync.status.fetchingData"))
                let response: RemoteDataResponse
                if let bundled = bundleResources?.dataJSON {
                    response = bundled
                } else {
                    guard let url = URL(string: "https://dp4p6x0xfi5o9.cloudfront.net/maimai/data.json") else {
                        throw URLError(.badURL)
                    }
                    let (data, _) = try await URLSession.shared.data(from: url)
                    response = try JSONDecoder().decode(RemoteDataResponse.self, from: data)
                }
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
                var aliasItems: [AliasItem] = []
                var songIdToTitle: [Int: String] = [:]

                if let bundledAliases = bundleResources?.lxnsAliases {
                    aliasItems = bundledAliases.aliases
                    for song in remoteSongs {
                        if let id = Int(song.songId) {
                            let rawTitle = song.title ?? ""
                            let trimmed = rawTitle.trimmingCharacters(in: .whitespacesAndNewlines)
                            let title = trimmed.isEmpty ? rawTitle : trimmed
                            if !title.isEmpty {
                                songIdToTitle[id] = title
                                titleToSongId[title] = id
                                let normalizedTitle = Self.normalizeSongLookupTitle(title)
                                if titleToSongIdByNormalized[normalizedTitle] == nil {
                                    titleToSongIdByNormalized[normalizedTitle] = id
                                }
                            }
                        }
                    }

                    let providerIds = bundleResources?.songIDJSON ?? []
                    for item in providerIds {
                        let rawName = item.name
                        let trimmed = rawName.trimmingCharacters(in: .whitespacesAndNewlines)
                        let trimmedName = trimmed.isEmpty ? rawName : trimmed
                        guard !trimmedName.isEmpty else { continue }
                        nameToProviderIds[trimmedName, default: []].append(item.id)
                        let normalizedName = Self.normalizeSongLookupTitle(trimmedName)
                        var normalizedIds = nameToProviderIdsByNormalized[normalizedName] ?? []
                        if !normalizedIds.contains(item.id) {
                            normalizedIds.append(item.id)
                            nameToProviderIdsByNormalized[normalizedName] = normalizedIds
                        }
                        if songIdToTitle[item.id] == nil {
                            songIdToTitle[item.id] = trimmedName
                        }
                        if titleToSongId[trimmedName] == nil {
                            titleToSongId[trimmedName] = item.id
                        }
                        if titleToSongIdByNormalized[normalizedName] == nil {
                            titleToSongIdByNormalized[normalizedName] = item.id
                        }
                    }
                    log(String(localized: "data.sync.log.fetchedProviderIds \(providerIds.count)"))
                } else if let aliasUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/alias/list"),
                          let lxnsSongUrl = URL(string: "https://maimai.lxns.net/api/v0/maimai/song/list"),
                          let providerIdUrl = URL(string: "https://maimaid.shikoch.in/songid.json") {

                    async let aliasDataFetch = URLSession.shared.data(from: aliasUrl)
                    async let lxnsSongDataFetch = URLSession.shared.data(from: lxnsSongUrl)
                    async let providerIdDataFetch = URLSession.shared.data(from: providerIdUrl)

                    if let (aliasData, _) = try? await aliasDataFetch,
                       let (lxnsSongData, _) = try? await lxnsSongDataFetch {

                        let aliasResponse = try? JSONDecoder().decode(AliasListResponse.self, from: aliasData)
                        let lxnsSongResponse = try? JSONDecoder().decode(LxnsSongListResponse.self, from: lxnsSongData)

                        if let aliasResponse = aliasResponse {
                            aliasItems = aliasResponse.aliases
                        }
                        if let lxnsSongResponse = lxnsSongResponse {
                            for lxnsSong in lxnsSongResponse.songs {
                                songIdToTitle[lxnsSong.id] = lxnsSong.title
                                titleToSongId[lxnsSong.title] = lxnsSong.id
                                let normalizedTitle = Self.normalizeSongLookupTitle(lxnsSong.title)
                                if titleToSongIdByNormalized[normalizedTitle] == nil {
                                    titleToSongIdByNormalized[normalizedTitle] = lxnsSong.id
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
                                guard !trimmedName.isEmpty else { continue }
                                nameToProviderIds[trimmedName, default: []].append(item.id)
                                let normalizedName = Self.normalizeSongLookupTitle(trimmedName)
                                var normalizedIds = nameToProviderIdsByNormalized[normalizedName] ?? []
                                if !normalizedIds.contains(item.id) {
                                    normalizedIds.append(item.id)
                                    nameToProviderIdsByNormalized[normalizedName] = normalizedIds
                                }
                                if songIdToTitle[item.id] == nil {
                                    songIdToTitle[item.id] = trimmedName
                                }
                                if titleToSongId[trimmedName] == nil {
                                    titleToSongId[trimmedName] = item.id
                                }
                                if titleToSongIdByNormalized[normalizedName] == nil {
                                    titleToSongIdByNormalized[normalizedName] = item.id
                                }
                            }
                            log(String(localized: "data.sync.log.fetchedProviderIds \(providerIds.count)"))
                        } catch {
                            log(String(localized: "data.sync.log.providerIdError \(error.localizedDescription)"))
                        }
                    }
                }

                if !aliasItems.isEmpty {
                    hasFreshAliasSnapshot = true
                    for item in aliasItems {
                        let resolvedTitles = Self.resolveLxnsAliasTitles(songId: item.song_id, songIdToTitle: songIdToTitle)
                        guard !resolvedTitles.isEmpty else { continue }

                        for title in resolvedTitles {
                            let normalizedTitle = Self.normalizeSongLookupTitle(title)
                            var merged = aliasMapByNormalizedTitle[normalizedTitle] ?? aliasMap[title] ?? []
                            var seen = Set(merged.map { $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased() })
                            for alias in item.aliases {
                                let normalized = alias.trimmingCharacters(in: .whitespacesAndNewlines)
                                let key = normalized.lowercased()
                                guard !normalized.isEmpty else { continue }
                                guard seen.insert(key).inserted else { continue }
                                merged.append(normalized)
                            }
                            aliasMap[title] = merged
                            aliasMapByNormalizedTitle[normalizedTitle] = merged
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
                if let bundledDan = bundleResources?.danInfo {
                    let cleanedCategories = sanitizeDanCategories(bundledDan)
                    let danJsonData = try JSONEncoder().encode(cleanedCategories)
                    let fileURL = getDanDataFileURL()
                    try danJsonData.write(to: fileURL)
                    log(String(localized: "data.sync.log.fetchedDanData \(cleanedCategories.count)"))
                } else {
                    log("Dan info is unavailable in backend static bundle; keeping existing local cache.")
                }
            }

            if options.updateChartStats {
                updateStage(.fetchingChartStats, base: 0.53, message: String(localized: "data.sync.status.fetchingChartStats"))
                await ChartStatsService.shared.fetchStats(forceRefresh: true)
                log(String(localized: "data.sync.log.chartStatsUpdated"))
            }

            if options.updateUtageChartStats {
                updateStage(.fetchingUtageChartStats, base: 0.54, message: String(localized: "data.sync.status.fetchingUtageChartStats"))
                if let bundledStats = bundleResources?.utageNoteJSON {
                    utageNotesById = Dictionary(uniqueKeysWithValues: bundledStats.map { ($0.id, $0.notes) })
                    utageNotesByKey = Self.buildUtageNotesByKey(bundledStats)
                    log(String(localized: "data.sync.log.fetchedUtageChartStats \(bundledStats.count)"))
                } else if let utageStatsURL = URL(string: "https://maimaid.shikoch.in/utage_chart_stats.json") {
                    var request = URLRequest(url: utageStatsURL)
                    request.setValue("Mozilla/5.0 (maimaid)", forHTTPHeaderField: "User-Agent")
                    let (data, _) = try await URLSession.shared.data(for: request)
                    let stats = try JSONDecoder().decode([UtageChartStatsItem].self, from: data)
                    utageNotesById = Dictionary(uniqueKeysWithValues: stats.map { ($0.id, $0.notes) })
                    utageNotesByKey = Self.buildUtageNotesByKey(stats)
                    log(String(localized: "data.sync.log.fetchedUtageChartStats \(stats.count)"))
                }
            }
            
            // --- 阶段 4: 合并数据入库 ---
            if options.updateRemoteData || options.updateAliases || options.updateIcons || options.updateUtageChartStats {
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
                                    version: sh.version,
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
                var utageNotesMergeCount = 0
                
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
                        let normalizedSongTitle = Self.normalizeSongLookupTitle(song.title)
                        if let officialId = titleToSongId[song.title] ?? titleToSongIdByNormalized[normalizedSongTitle] {
                            song.songId = officialId
                        }
                        if hasFreshAliasSnapshot {
                            // Full replace on each successful alias sync so stale local aliases
                            // (e.g. removed community aliases) do not survive future refreshes.
                            let officialAliases = aliasMap[song.title] ?? aliasMapByNormalizedTitle[normalizedSongTitle] ?? []
                            if officialAliases.isEmpty {
                                song.aliases = []
                            } else {
                                var seen = Set<String>()
                                song.aliases = officialAliases.filter {
                                    let norm = $0.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
                                    guard !norm.isEmpty else { return false }
                                    return seen.insert(norm).inserted
                                }
                            }
                        }
                        
                        let trimmedSearch = song.title.trimmingCharacters(in: .whitespacesAndNewlines)
                        let searchTitle = trimmedSearch.isEmpty ? song.title : trimmedSearch
                        let normalizedSearchTitle = Self.normalizeSongLookupTitle(searchTitle)
                        if let possibleIds = nameToProviderIds[searchTitle] ?? nameToProviderIdsByNormalized[normalizedSearchTitle] {
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
                                version: remoteSheet.version,
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
                        let normalizedSearchTitle = Self.normalizeSongLookupTitle(searchTitle)
                        if let possibleIds = nameToProviderIds[searchTitle] ?? nameToProviderIdsByNormalized[normalizedSearchTitle] {
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
                            sheet.version = remoteSheet.version
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

                        if options.updateUtageChartStats && remoteSheet.type.lowercased() == "utage" {
                            let mergedNotes = Self.resolveUtageTotalNotes(
                                for: sheet,
                                songTitle: song.title,
                                songIdentifier: song.songIdentifier,
                                sheetLevel: sheet.level,
                                notesById: utageNotesById,
                                notesByKey: utageNotesByKey
                            )
                            if let mergedNotes, sheet.total != mergedNotes {
                                sheet.total = mergedNotes
                                utageNotesMergeCount += 1
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
                if options.updateUtageChartStats {
                    log(String(localized: "data.sync.log.mergedUtageChartStats \(utageNotesMergeCount)"))
                }
                
                remoteSongs = []
                aliasMap = [:]
                aliasMapByNormalizedTitle = [:]
                titleToSongId = [:]
                titleToSongIdByNormalized = [:]
                nameToProviderIds = [:]
                nameToProviderIdsByNormalized = [:]
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
            if let md5 = staticBundle?.bundle?.md5 {
                UserDefaults.app.staticBundleMd5 = md5
            }
            try modelContext.save()

            // Pull newly approved community aliases after static data refresh so merged aliases remain visible.
            await forceSyncApprovedCommunityAliases(modelContext: modelContext)
            
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

    private static func buildUtageNotesByKey(_ stats: [UtageChartStatsItem]) -> [String: [Int]] {
        var lookup: [String: [Int]] = [:]

        for item in stats {
            guard let kanji = extractUtageKanji(from: item.title),
                  let key = utageLookupKey(title: item.title, kanji: kanji) else {
                continue
            }
            lookup[key, default: []].append(item.notes)
        }

        for key in lookup.keys {
            let unique = Array(Set(lookup[key] ?? [])).sorted()
            lookup[key] = unique
        }

        return lookup
    }

    private static func resolveUtageTotalNotes(
        for sheet: Sheet,
        songTitle: String,
        songIdentifier: String,
        sheetLevel: String,
        notesById: [Int: Int],
        notesByKey: [String: [Int]]
    ) -> Int? {
        if sheet.songId > 0, let exact = notesById[sheet.songId] {
            return exact
        }

        let fallbackKanji = extractUtageKanji(from: songTitle)
        guard let kanji = extractUtageKanji(from: sheet.difficulty) ?? fallbackKanji else {
            return nil
        }

        let normalizedIdentifier = songIdentifier
            .replacingOccurrences(of: #"^\s*[\(（]宴[\)）]\s*"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"（[^）]+）\s*$"#, with: "", options: .regularExpression)

        var candidateKeys: [String] = []
        if let key = utageLookupKey(title: songTitle, kanji: kanji) {
            candidateKeys.append(key)
        }
        if let key = utageLookupKey(title: normalizedIdentifier, kanji: kanji) {
            candidateKeys.append(key)
        }

        for key in Set(candidateKeys) {
            guard let candidates = notesByKey[key], !candidates.isEmpty else { continue }
            return selectUtageNotesCandidate(candidates, songIdentifier: songIdentifier, sheetLevel: sheetLevel)
        }

        return nil
    }

    private static func selectUtageNotesCandidate(_ candidates: [Int], songIdentifier: String, sheetLevel: String) -> Int? {
        let unique = Array(Set(candidates)).sorted()
        guard !unique.isEmpty else { return nil }
        if unique.count == 1 { return unique.first }

        let identifierUpper = songIdentifier.uppercased()
        let difficultyOrder = ["(EASY)", "(BASIC)", "(ADVANCED)", "(EXPERT)", "(MASTER)", "(RE:MASTER)"]
        if let idx = difficultyOrder.firstIndex(where: { identifierUpper.contains($0) }) {
            return unique[min(idx, unique.count - 1)]
        }

        if songIdentifier.contains("入門") {
            return unique.first
        }

        if songIdentifier.contains("ヒーロー") {
            return unique.last
        }

        if let level = parseApproximateUtageLevel(sheetLevel) {
            let ratio = max(0, min((level - 1.0) / 14.0, 1.0))
            let idx = Int((Double(unique.count - 1) * ratio).rounded())
            return unique[idx]
        }

        return unique.last
    }

    private static func parseApproximateUtageLevel(_ raw: String) -> Double? {
        let cleaned = raw
            .replacingOccurrences(of: "?", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleaned != "*" else { return nil }

        let hasPlus = cleaned.contains("+")
        let numeric = cleaned.filter { $0.isNumber || $0 == "." }
        guard let value = Double(numeric) else { return nil }
        return hasPlus ? value + 0.7 : value
    }

    private static func utageLookupKey(title: String, kanji: String) -> String? {
        let normalizedKanji = normalizeUtageKanji(kanji)
        let normalizedTitle = normalizeUtageTitle(title)
        guard !normalizedKanji.isEmpty, !normalizedTitle.isEmpty else { return nil }
        return "\(normalizedKanji)|\(normalizedTitle)"
    }

    private static func normalizeUtageKanji(_ raw: String) -> String {
        raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: "藏", with: "蔵")
            .folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .lowercased()
    }

    private static func normalizeUtageTitle(_ raw: String) -> String {
        let stripped = raw
            .replacingOccurrences(of: "\u{3000}", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: #"^\s*(?:\[[^\]]+\]|【[^】]+】)\s*"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"^\s*[\(（]宴[\)）]\s*"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"\s*[\(（](?:EASY|BASIC|ADVANCED|EXPERT|MASTER|Re:MASTER)[\)）]\s*$"#, with: "", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: "藏", with: "蔵")
            .folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .lowercased()

        return stripped.replacingOccurrences(of: #"[[:space:]\[\]【】\(\)（）]+"#, with: "", options: .regularExpression)
    }

    private static func normalizeSongLookupTitle(_ raw: String) -> String {
        raw
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .folding(options: [.caseInsensitive, .diacriticInsensitive, .widthInsensitive], locale: Locale(identifier: "en_US_POSIX"))
            .lowercased()
            .replacingOccurrences(of: #"\s+"#, with: "", options: .regularExpression)
    }

    private static func resolveLxnsAliasTitles(songId: Int, songIdToTitle: [Int: String]) -> [String] {
        var titles: [String] = []
        var seen = Set<String>()
        for candidateId in expandLxnsSongIdCandidates(songId) {
            guard let title = songIdToTitle[candidateId] else { continue }
            let normalizedTitle = normalizeSongLookupTitle(title)
            guard !normalizedTitle.isEmpty else { continue }
            guard seen.insert(normalizedTitle).inserted else { continue }
            titles.append(title)
        }
        return titles
    }

    private static func expandLxnsSongIdCandidates(_ songId: Int) -> [Int] {
        guard songId > 0 else {
            return []
        }

        var candidates: [Int] = []
        let appendCandidate: (Int) -> Void = { value in
            guard value > 0 else { return }
            guard !candidates.contains(value) else { return }
            candidates.append(value)
        }

        appendCandidate(songId)

        if songId < 10000 {
            appendCandidate(songId + 10000)
        }

        if songId > 10000 && songId < 100000 {
            let baseId = songId % 10000
            if baseId > 0 {
                appendCandidate(baseId)
                appendCandidate(baseId + 10000)
            }
        }

        if songId >= 100000 {
            let baseId = songId % 100000
            if baseId > 0 {
                appendCandidate(baseId)
                if baseId < 10000 {
                    appendCandidate(baseId + 10000)
                }
            }
        }

        return candidates
    }

    private static func extractUtageKanji(from text: String) -> String? {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard let first = trimmed.first else { return nil }

        if first == "【", let close = trimmed.firstIndex(of: "】"), close > trimmed.startIndex {
            let start = trimmed.index(after: trimmed.startIndex)
            return normalizeUtageKanji(String(trimmed[start..<close]))
        }

        if first == "[", let close = trimmed.firstIndex(of: "]"), close > trimmed.startIndex {
            let start = trimmed.index(after: trimmed.startIndex)
            return normalizeUtageKanji(String(trimmed[start..<close]))
        }

        return nil
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
