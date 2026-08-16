import Foundation

struct ResolvedSheetMetadata: Sendable {
    let version: String?
    let level: String
    let levelValue: Double?
    let internalLevel: String?
    let internalLevelValue: Double?

    var ratingLevel: Double? {
        (internalLevelValue ?? levelValue).flatMap { $0 > 0 ? $0 : nil }
    }

    var displayLevel: String {
        if let internalLevel, !internalLevel.isEmpty {
            return internalLevel
        }
        if let internalLevelValue {
            return String(internalLevelValue)
        }
        return level
    }
}

enum ServerChartPolicy {
    static func isPlayable(_ sheet: Sheet, on server: GameServer) -> Bool {
        switch server {
        case .jp:
            sheet.regionJp
        case .intl:
            sheet.regionIntl
        case .cn:
            sheet.regionCn
        }
    }

    static func metadata(for sheet: Sheet, on server: GameServer) -> ResolvedSheetMetadata {
        let override: ResolvedSheetMetadata?
        switch server {
        case .jp:
            override = nil
        case .intl:
            override = ResolvedSheetMetadata(
                version: sheet.intlVersion,
                level: sheet.intlLevel ?? sheet.level,
                levelValue: sheet.intlLevelValue,
                internalLevel: sheet.intlInternalLevel,
                internalLevelValue: sheet.intlInternalLevelValue
            )
        case .cn:
            override = ResolvedSheetMetadata(
                version: sheet.cnVersion,
                level: sheet.cnLevel ?? sheet.level,
                levelValue: sheet.cnLevelValue,
                internalLevel: sheet.cnInternalLevel,
                internalLevelValue: sheet.cnInternalLevelValue
            )
        }

        let hasOverrideConstant = override?.internalLevelValue != nil || override?.levelValue != nil

        return ResolvedSheetMetadata(
            version: override?.version ?? sheet.version,
            level: override?.level ?? sheet.level,
            levelValue: override?.levelValue ?? sheet.levelValue,
            internalLevel: override?.internalLevel ?? (hasOverrideConstant ? nil : sheet.internalLevel),
            internalLevelValue: override?.internalLevelValue ?? override?.levelValue ?? sheet.internalLevelValue
        )
    }
}
