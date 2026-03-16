import Foundation
import SwiftData

enum PlateType: String, CaseIterable, Identifiable {
    case kiwami = "极牌"
    case sho = "将牌"
    case shin = "神牌"
    case maimai = "舞舞牌"
    
    var id: String { self.rawValue }
    
    var shortName: String {
        switch self {
        case .kiwami: return "极"
        case .sho: return "将"
        case .shin: return "神"
        case .maimai: return "舞舞"
        }
    }
    
    var color: String {
        switch self {
        case .kiwami: return "#36bf63" // Green
        case .sho: return "#fca13b"    // Orange
        case .shin: return "#f7536a"   // Red
        case .maimai: return "#a34ee4" // Purple
        }
    }
    
    /// 检查给定成绩是否达成该牌类型要求
    func isAchieved(score: Score?) -> Bool {
        guard let score = score else { return false }
        let fc = score.fc?.lowercased()
        let fs = score.fs?.lowercased()
        
        switch self {
        case .kiwami:
            if let fc, ["fc", "fcp", "ap", "app"].contains(fc) { return true }
        case .sho:
            if score.rate >= 100.0 { return true }
        case .shin:
            if let fc, ["ap", "app"].contains(fc) { return true }
        case .maimai:
            if let fs, ["fsd", "fsdp"].contains(fs) { return true }
        }
        return false
    }
}

struct VersionPlateGroup: Identifiable, Hashable {
    let id = UUID()
    let name: String
    let platePrefix: String
    let versions: [String]
    let isOldFrame: Bool
    let hasSho: Bool
    let includeReMasterByDefault: Bool
    
    func hash(into hasher: inout Hasher) {
        hasher.combine(name)
        hasher.combine(platePrefix)
    }
    
    static func == (lhs: VersionPlateGroup, rhs: VersionPlateGroup) -> Bool {
        lhs.name == rhs.name && lhs.platePrefix == rhs.platePrefix
    }
}

@MainActor
class PlateService {
    static let shared = PlateService()
    
    private var cachedGroups: [VersionPlateGroup]?
    
    private init() {}
    
    func getVersionGroups() -> [VersionPlateGroup] {
        if let cached = cachedGroups { return cached }
        
        guard let data = UserDefaults.standard.data(forKey: "MaimaiVersionsData"),
              let versionsInfo = try? JSONDecoder().decode([ThemeUtils.AppVersion].self, from: data) else {
            return []
        }
        
        let groups = buildVersionGroups(from: versionsInfo)
        cachedGroups = groups
        return groups
    }
    
    private func buildVersionGroups(from versionsInfo: [ThemeUtils.AppVersion]) -> [VersionPlateGroup] {
        var groups: [VersionPlateGroup] = []
        var currentAbbr: String?
        var currentVersions: [String] = []
        var currentFirstName = ""
        
        let dxVersionIndex = versionsInfo.firstIndex { 
            $0.version.localizedCaseInsensitiveContains("でらっくす") || $0.version.localizedCaseInsensitiveContains(" DX") 
        } ?? versionsInfo.count
        
        let greenVersionIndex = versionsInfo.firstIndex { 
            $0.version.localizedCaseInsensitiveContains("GreeN") 
        } ?? versionsInfo.count
        
        for (_, vInfo) in versionsInfo.enumerated() {
            if vInfo.abbr != currentAbbr {
                if let abbr = currentAbbr {
                    groups.append(makeGroup(
                        abbr: abbr,
                        name: currentFirstName,
                        versions: currentVersions,
                        versionsInfo: versionsInfo,
                        dxVersionIndex: dxVersionIndex,
                        greenVersionIndex: greenVersionIndex
                    ))
                }
                
                currentAbbr = vInfo.abbr
                currentVersions = [vInfo.version]
                currentFirstName = vInfo.version
                    .replacingOccurrences(of: "maimai ", with: "", options: .caseInsensitive)
                    .replacingOccurrences(of: "でらっくす", with: "", options: .caseInsensitive)
                    .trimmingCharacters(in: .whitespaces)
                if currentFirstName.isEmpty { currentFirstName = "maimai" }
            } else {
                currentVersions.append(vInfo.version)
            }
        }
        
        if let abbr = currentAbbr {
            groups.append(makeGroup(
                abbr: abbr,
                name: currentFirstName,
                versions: currentVersions,
                versionsInfo: versionsInfo,
                dxVersionIndex: dxVersionIndex,
                greenVersionIndex: greenVersionIndex
            ))
        }
        
        // Add "舞代" special group
        let oldFrameVersions = groups.filter { $0.isOldFrame }.flatMap { $0.versions }
        if !oldFrameVersions.isEmpty {
            groups.insert(VersionPlateGroup(
                name: "舞代",
                platePrefix: "舞",
                versions: oldFrameVersions,
                isOldFrame: true,
                hasSho: true,
                includeReMasterByDefault: true
            ), at: 0)
        }
        
        return groups
    }
    
    private func makeGroup(
        abbr: String,
        name: String,
        versions: [String],
        versionsInfo: [ThemeUtils.AppVersion],
        dxVersionIndex: Int,
        greenVersionIndex: Int
    ) -> VersionPlateGroup {
        let firstVersionIndex = versionsInfo.firstIndex { $0.version == versions.first } ?? 0
        let isOld = firstVersionIndex < dxVersionIndex
        let isMaimaiOriginal = firstVersionIndex < greenVersionIndex && isOld
        let hasShoPlate = abbr != "真" && !isMaimaiOriginal
        
        return VersionPlateGroup(
            name: name,
            platePrefix: abbr,
            versions: versions,
            isOldFrame: isOld,
            hasSho: hasShoPlate,
            includeReMasterByDefault: false
        )
    }
    
    func isAchieved(plateType: PlateType, sheet: Sheet, context: ModelContext) -> Bool {
        let score = ScoreService.shared.score(for: sheet, context: context)
        return plateType.isAchieved(score: score)
    }
}
