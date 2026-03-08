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
    
    private init() {}
    
    func getVersionGroups() -> [VersionPlateGroup] {
        guard let data = UserDefaults.standard.data(forKey: "MaimaiVersionsData"),
              let versionsInfo = try? JSONDecoder().decode([ThemeUtils.AppVersion].self, from: data) else {
            return []
        }
        
        var groups: [VersionPlateGroup] = []
        var currentAbbr: String? = nil
        var currentVersions: [String] = []
        var currentFirstName: String = ""
        
        // Find the boundary for Old Frame (anything before "maimai でらっくす")
        let dxVersionIndex = versionsInfo.firstIndex { $0.version.localizedCaseInsensitiveContains("でらっくす") || $0.version.localizedCaseInsensitiveContains(" DX") } ?? versionsInfo.count
        
        for (_, vInfo) in versionsInfo.enumerated() {
            if vInfo.abbr != currentAbbr {
                if let abbr = currentAbbr {
                    let isOld = versionsInfo.firstIndex(where: { $0.version == currentVersions.first }) ?? 0 < dxVersionIndex
                    groups.append(VersionPlateGroup(
                        name: currentFirstName,
                        platePrefix: abbr,
                        versions: currentVersions,
                        isOldFrame: isOld,
                        hasSho: abbr != "真",
                        includeReMasterByDefault: false
                    ))
                }
                
                currentAbbr = vInfo.abbr
                currentVersions = [vInfo.version]
                var cleanedName = vInfo.version
                    .replacingOccurrences(of: "maimai ", with: "", options: .caseInsensitive)
                    .replacingOccurrences(of: "でらっくす", with: "", options: .caseInsensitive)
                    .trimmingCharacters(in: .whitespaces)
                if cleanedName.isEmpty { cleanedName = "maimai" }
                currentFirstName = cleanedName
            } else {
                currentVersions.append(vInfo.version)
            }
        }
        
        if let abbr = currentAbbr {
            let isOld = versionsInfo.firstIndex(where: { $0.version == currentVersions.first }) ?? 0 < dxVersionIndex
            groups.append(VersionPlateGroup(
                name: currentFirstName,
                platePrefix: abbr,
                versions: currentVersions,
                isOldFrame: isOld,
                hasSho: abbr != "真",
                includeReMasterByDefault: false
            ))
        }
        
        // Add "舞代" special group
        let oldFrameVersions = groups.filter { $0.isOldFrame }.flatMap { $0.versions }
        if !oldFrameVersions.isEmpty {
            let group = VersionPlateGroup(
                name: "舞代",
                platePrefix: "舞",
                versions: oldFrameVersions,
                isOldFrame: true,
                hasSho: true,
                includeReMasterByDefault: true
            )
            groups.insert(group, at: 0)
        }
        
        return groups
    }
    
    func isAchieved(plateType: PlateType, sheet: Sheet) -> Bool {
        guard let score = sheet.score() else { return false }
        
        switch plateType {
        case .kiwami:
            // 极: FC or better
            if let fc = score.fc?.lowercased(), ["fc", "fcp", "ap", "app"].contains(fc) {
                return true
            }
        case .sho:
            // 将: SSS or better
            if score.rate >= 100.0 {
                return true
            }
        case .shin:
            // 神: AP or better
            if let fc = score.fc?.lowercased(), ["ap", "app"].contains(fc) {
                return true
            }
        case .maimai:
            // 舞舞: FDX or better (FSD/FSDP)
            if let fs = score.fs?.lowercased(), ["fsd", "fsdp"].contains(fs) {
                return true
            }
        }
        return false
    }
}
