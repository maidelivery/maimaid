import Foundation
import SwiftData

/// 成绩管理服务 - 确保所有成绩操作都在当前用户作用域下进行
@MainActor
final class ScoreService {
    static let shared = ScoreService()
    
    private init() {}
    
    // MARK: - 获取当前活跃用户
    
    /// 获取当前活跃用户的 ID，如果没有则返回 nil
    func currentActiveProfileId(context: ModelContext) -> UUID? {
        let descriptor = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.isActive })
        return (try? context.fetch(descriptor))?.first?.id
    }
    
    /// 获取当前活跃用户
    func currentActiveProfile(context: ModelContext) -> UserProfile? {
        let descriptor = FetchDescriptor<UserProfile>(predicate: #Predicate { $0.isActive })
        return (try? context.fetch(descriptor))?.first
    }
    
    // MARK: - 成绩读取（严格用户隔离）
    
    /// 获取指定谱面对当前用户的成绩
    func score(for sheet: Sheet, context: ModelContext) -> Score? {
        guard let profileId = currentActiveProfileId(context: context) else {
            return sheet.scores.first { $0.userProfileId == nil }
        }
        return sheet.scores.first { $0.userProfileId == profileId }
    }
    
    /// 获取当前用户的所有成绩
    func allScores(context: ModelContext) -> [Score] {
        guard let profileId = currentActiveProfileId(context: context) else {
            let descriptor = FetchDescriptor<Score>()
            let allScores = (try? context.fetch(descriptor)) ?? []
            return allScores.filter { $0.userProfileId == nil }
        }
        
        var descriptor = FetchDescriptor<Score>()
        descriptor.predicate = #Predicate { $0.userProfileId == profileId }
        return (try? context.fetch(descriptor)) ?? []
    }
    
    /// 获取成绩映射表（用于批量计算）
    func scoreMap(context: ModelContext) -> [String: Score] {
        let scores = allScores(context: context)
        var map: [String: Score] = [:]
        for score in scores {
            map[score.sheetId] = score
        }
        return map
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
            
            return existing
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
            
            return newScore
        }
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
        guard let score = score(for: sheet, context: context) else { return false }
        context.delete(score)
        return true
    }
    
    // MARK: - 历史记录
    
    /// 获取当前用户在指定谱面的游玩历史
    func playHistory(for sheet: Sheet, context: ModelContext) -> [PlayRecord] {
        guard let profileId = currentActiveProfileId(context: context) else {
            return sheet.playRecords?.filter { $0.userProfileId == nil } ?? []
        }
        var records = sheet.playRecords?.filter { $0.userProfileId == profileId } ?? []
        
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
}
