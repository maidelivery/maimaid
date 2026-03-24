import Foundation
import SwiftData

/// 成绩管理服务 - 确保所有成绩操作都在当前用户作用域下进行
@MainActor
final class ScoreService {
    static let shared = ScoreService()
    
    private init() {}
    
    // MARK: - Cache
    
    private struct ScoreSnapshot {
        let scores: [Score]
        let scoreMap: [String: Score]
    }
    
    private var cachedActiveProfileId: UUID?
    private var didResolveActiveProfile = false
    
    private var cachedSnapshotsByProfileKey: [String: ScoreSnapshot] = [:]
    
    private func profileKey(_ profileId: UUID?) -> String {
        profileId?.uuidString ?? "none"
    }
    
    private func invalidateActiveProfileCache() {
        didResolveActiveProfile = false
        cachedActiveProfileId = nil
    }
    
    private func invalidateScoreCache(for profileId: UUID?) {
        cachedSnapshotsByProfileKey.removeValue(forKey: profileKey(profileId))
    }
    
    private func ensureActiveProfile(context: ModelContext) -> UserProfile {
        let activeDescriptor = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.isActive })
        if let activeProfile = (try? context.fetch(activeDescriptor))?.first {
            cachedActiveProfileId = activeProfile.id
            didResolveActiveProfile = true
            return activeProfile
        }
        
        let descriptor = FetchDescriptor<UserProfile>()
        if let existingProfile = (try? context.fetch(descriptor))?.sorted(by: { $0.createdAt < $1.createdAt }).first {
            existingProfile.isActive = true
            try? context.save()
            cachedActiveProfileId = existingProfile.id
            didResolveActiveProfile = true
            return existingProfile
        }
        
        let defaultProfile = UserProfile(
            name: String(localized: "userProfile.defaultName"),
            server: "jp",
            isActive: true
        )
        context.insert(defaultProfile)
        try? context.save()
        
        cachedActiveProfileId = defaultProfile.id
        didResolveActiveProfile = true
        return defaultProfile
    }
    
    func invalidateAllCaches() {
        invalidateActiveProfileCache()
        cachedSnapshotsByProfileKey.removeAll()
    }
    
    // MARK: - 获取当前活跃用户
    
    /// 获取当前活跃用户的 ID，如果没有则返回 nil
    func currentActiveProfileId(context: ModelContext) -> UUID? {
        if didResolveActiveProfile {
            return cachedActiveProfileId
        }
        return ensureActiveProfile(context: context).id
    }
    
    /// 获取当前活跃用户
    func currentActiveProfile(context: ModelContext) -> UserProfile? {
        ensureActiveProfile(context: context)
    }
    
    // MARK: - 成绩读取（严格用户隔离）
    
    private func loadSnapshot(context: ModelContext, profileId: UUID?) -> ScoreSnapshot {
        let key = profileKey(profileId)
        if let cached = cachedSnapshotsByProfileKey[key] {
            return cached
        }
        
        let scores: [Score]
        if let profileId {
            let descriptor = FetchDescriptor<Score>(
                predicate: #Predicate<Score> { $0.userProfileId == profileId }
            )
            scores = (try? context.fetch(descriptor)) ?? []
        } else {
            let descriptor = FetchDescriptor<Score>(
                predicate: #Predicate<Score> { $0.userProfileId == nil }
            )
            scores = (try? context.fetch(descriptor)) ?? []
        }
        
        var map: [String: Score] = [:]
        map.reserveCapacity(scores.count)
        for score in scores {
            map[score.sheetId] = score
        }
        
        let snapshot = ScoreSnapshot(scores: scores, scoreMap: map)
        cachedSnapshotsByProfileKey[key] = snapshot
        return snapshot
    }
    
    private func currentSnapshot(context: ModelContext) -> ScoreSnapshot {
        let profileId = currentActiveProfileId(context: context)
        return loadSnapshot(context: context, profileId: profileId)
    }
    
    private func candidateScoreSheetIds(for sheet: Sheet) -> [String] {
        let rawIdentifiers: [String?] = [
            sheet.songIdentifier,
            String(sheet.songId),
            sheet.song?.songIdentifier,
            sheet.song.map { String($0.songId) },
        ]
        
        var songIdentifiers: [String] = []
        for rawIdentifier in rawIdentifiers {
            guard let rawIdentifier, !rawIdentifier.isEmpty, rawIdentifier != "0" else { continue }
            songIdentifiers.append(rawIdentifier)
        }
        
        var seen = Set<String>()
        var result: [String] = []
        
        for songIdentifier in songIdentifiers {
            for separator in ["_", "-"] {
                let sheetId = "\(songIdentifier)\(separator)\(sheet.type)\(separator)\(sheet.difficulty)"
                if seen.insert(sheetId).inserted {
                    result.append(sheetId)
                }
            }
        }
        
        return result
    }
    
    /// 获取指定谱面对当前用户的成绩
    func score(for sheet: Sheet, context: ModelContext) -> Score? {
        let snapshot = currentSnapshot(context: context)
        for sheetId in candidateScoreSheetIds(for: sheet) {
            if let score = snapshot.scoreMap[sheetId] {
                return score
            }
        }
        return nil
    }
    
    /// 获取当前用户的所有成绩
    func allScores(context: ModelContext) -> [Score] {
        currentSnapshot(context: context).scores
    }
    
    /// 获取成绩映射表（用于批量计算）
    func scoreMap(context: ModelContext) -> [String: Score] {
        currentSnapshot(context: context).scoreMap
    }
    
    // MARK: - 成绩写入（严格用户隔离）
    
    /// 计算等级（内联实现，避免依赖循环）
    private func calculateRank(achievement: Double) -> String {
        if achievement >= 100.5 { return "SSS+" }
        if achievement >= 100.0 { return "SSS" }
        if achievement >= 99.5 { return "SS+" }
        if achievement >= 99.0 { return "SS" }
        if achievement >= 98.0 { return "S+" }
        if achievement >= 97.0 { return "S" }
        if achievement >= 94.0 { return "AAA" }
        if achievement >= 90.0 { return "AA" }
        if achievement >= 80.0 { return "A" }
        if achievement >= 75.0 { return "BBB" }
        if achievement >= 70.0 { return "BB" }
        if achievement >= 60.0 { return "B" }
        if achievement >= 50.0 { return "C" }
        return "D"
    }
    
    /// 保存或更新成绩 - 自动关联当前用户
    @discardableResult
    func saveScore(
        sheet: Sheet,
        rate: Double,
        rank: String,
        dxScore: Int = 0,
        fc: String? = nil,
        fs: String? = nil,
        context: ModelContext
    ) -> Score {
        let profileId = currentActiveProfileId(context: context)
        let existingScore = score(for: sheet, context: context)
        
        let result: Score
        
        if let existing = existingScore {
            let isNewRateBetter = rate > existing.rate
            if isNewRateBetter {
                existing.rate = rate
                existing.rank = rank
                existing.achievementDate = Date()
            }
            
            existing.dxScore = max(existing.dxScore, dxScore)
            existing.fc = ThemeUtils.bestFC(existing.fc, fc)
            existing.fs = ThemeUtils.bestFS(existing.fs, fs)
            result = existing
        } else {
            let newScore = Score(
                sheetId: "\(sheet.songIdentifier)_\(sheet.type)_\(sheet.difficulty)",
                rate: rate,
                rank: rank,
                dxScore: dxScore,
                fc: fc,
                fs: fs,
                achievementDate: Date(),
                userProfileId: profileId
            )
            context.insert(newScore)
            sheet.scores.append(newScore)
            result = newScore
        }
        
        invalidateScoreCache(for: profileId)
        return result
    }
    
    /// 记录游玩历史 - 自动关联当前用户
    func recordPlay(
        sheet: Sheet,
        rate: Double,
        rank: String,
        dxScore: Int = 0,
        fc: String? = nil,
        fs: String? = nil,
        context: ModelContext
    ) -> PlayRecord {
        let profileId = currentActiveProfileId(context: context)
        
        let record = PlayRecord(
            sheetId: "\(sheet.songIdentifier)-\(sheet.type)-\(sheet.difficulty)",
            rate: rate,
            rank: rank,
            dxScore: dxScore,
            fc: fc,
            fs: fs,
            playDate: Date(),
            userProfileId: profileId
        )
        context.insert(record)
        
        if sheet.playRecords == nil {
            sheet.playRecords = []
        }
        sheet.playRecords?.append(record)
        
        return record
    }
    
    // MARK: - 删除成绩
    
    /// 删除当前用户的成绩
    func deleteScore(for sheet: Sheet, context: ModelContext) -> Bool {
        let profileId = currentActiveProfileId(context: context)
        guard let score = score(for: sheet, context: context) else { return false }
        context.delete(score)
        invalidateScoreCache(for: profileId)
        return true
    }
    
    // MARK: - 用户切换 / 外部更新
    
    /// 当用户切换时调用，清空 active profile 和成绩缓存
    func notifyActiveProfileChanged() {
        invalidateAllCaches()
    }
    
    /// 当外部导入、恢复、批量更新成绩后调用
    func notifyScoresChanged(for profileId: UUID? = nil) {
        invalidateScoreCache(for: profileId)
    }
    
    // MARK: - 历史记录
    
    /// 获取当前用户在指定谱面的游玩历史
    func playHistory(for sheet: Sheet, context: ModelContext) -> [PlayRecord] {
        guard let profileId = currentActiveProfileId(context: context) else {
            var records = sheet.playRecords?.filter { $0.userProfileId == nil } ?? []
            records = repairDetachedRecords(records, for: sheet, profileId: nil, context: context)
            
            if let bestScore = score(for: sheet, context: context) {
                let hasMatch = records.contains { abs($0.rate - bestScore.rate) < 0.0001 }
                if !hasMatch && bestScore.rate > 0 {
                    let generatedRecord = PlayRecord(
                        sheetId: bestScore.sheetId,
                        rate: bestScore.rate,
                        rank: bestScore.rank,
                        dxScore: bestScore.dxScore,
                        fc: bestScore.fc,
                        fs: bestScore.fs,
                        playDate: bestScore.achievementDate,
                        userProfileId: bestScore.userProfileId
                    )
                    generatedRecord.sheet = sheet
                    context.insert(generatedRecord)
                    
                    if sheet.playRecords == nil {
                        sheet.playRecords = []
                    }
                    sheet.playRecords?.append(generatedRecord)
                    records.append(generatedRecord)
                    
                    try? context.save()
                }
            }
            
            return records
        }
        
        var records = sheet.playRecords?.filter { $0.userProfileId == profileId } ?? []
        records = repairDetachedRecords(records, for: sheet, profileId: profileId, context: context)
        
        // Auto-repair missing PlayRecord from imported Score
        if let bestScore = score(for: sheet, context: context) {
            let hasMatch = records.contains { abs($0.rate - bestScore.rate) < 0.0001 }
            if !hasMatch && bestScore.rate > 0 {
                let generatedRecord = PlayRecord(
                    sheetId: bestScore.sheetId,
                    rate: bestScore.rate,
                    rank: bestScore.rank,
                    dxScore: bestScore.dxScore,
                    fc: bestScore.fc,
                    fs: bestScore.fs,
                    playDate: bestScore.achievementDate,
                    userProfileId: bestScore.userProfileId
                )
                generatedRecord.sheet = sheet
                context.insert(generatedRecord)
                
                if sheet.playRecords == nil {
                    sheet.playRecords = []
                }
                sheet.playRecords?.append(generatedRecord)
                records.append(generatedRecord)
                
                try? context.save()
            }
        }
        
        return records
    }
    
    private func repairDetachedRecords(
        _ existingRecords: [PlayRecord],
        for sheet: Sheet,
        profileId: UUID?,
        context: ModelContext
    ) -> [PlayRecord] {
        let descriptor: FetchDescriptor<PlayRecord>
        if let profileId {
            descriptor = FetchDescriptor<PlayRecord>(
                predicate: #Predicate<PlayRecord> { $0.userProfileId == profileId }
            )
        } else {
            descriptor = FetchDescriptor<PlayRecord>(
                predicate: #Predicate<PlayRecord> { $0.userProfileId == nil }
            )
        }
        
        let allRecords = (try? context.fetch(descriptor)) ?? []
        let candidateIds = Set(candidateScoreSheetIds(for: sheet))
        
        var recordsById = Dictionary(uniqueKeysWithValues: existingRecords.map { ($0.id, $0) })
        var didRepair = false
        let canonicalSheetId = "\(sheet.songIdentifier)-\(sheet.type)-\(sheet.difficulty)"
        
        for record in allRecords where candidateIds.contains(record.sheetId) {
            if record.sheetId != canonicalSheetId {
                record.sheetId = canonicalSheetId
                didRepair = true
            }
            
            if record.sheet == nil {
                record.sheet = sheet
                if sheet.playRecords == nil {
                    sheet.playRecords = []
                }
                if !(sheet.playRecords?.contains(where: { $0.id == record.id }) ?? false) {
                    sheet.playRecords?.append(record)
                }
                didRepair = true
            }
            
            if recordsById[record.id] == nil {
                recordsById[record.id] = record
            }
        }
        
        let repairedRecords = recordsById.values.sorted { $0.playDate > $1.playDate }
        
        if didRepair {
            try? context.save()
        }
        
        return repairedRecords
    }
}
