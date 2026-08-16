package org.rhythmeta.maimaid.core.data

import org.rhythmeta.maimaid.core.database.SheetEntity

data class ResolvedSheetMetadata(
    val version: String?,
    val level: String,
    val levelValue: Double?,
    val internalLevel: String?,
    val internalLevelValue: Double?,
) {
    val ratingLevel: Double?
        get() = (internalLevelValue ?: levelValue)?.takeIf { it > 0.0 }

    val displayLevel: String
        get() = internalLevel
            ?.takeIf(String::isNotBlank)
            ?: internalLevelValue?.toString()
            ?: level
}

object ServerChartPolicy {
    fun isPlayable(sheet: SheetEntity, server: String): Boolean = !sheet.isRemoved && when (server.lowercase()) {
        "cn" -> sheet.regionCn
        "intl", "us", "usa" -> sheet.regionIntl
        else -> sheet.regionJp
    }

    fun metadata(sheet: SheetEntity, server: String): ResolvedSheetMetadata {
        val override = when (server.lowercase()) {
            "cn" -> ResolvedSheetMetadata(
                version = sheet.cnVersion,
                level = sheet.cnLevel ?: sheet.level,
                levelValue = sheet.cnLevelValue,
                internalLevel = sheet.cnInternalLevel,
                internalLevelValue = sheet.cnInternalLevelValue,
            )
            "intl", "us", "usa" -> ResolvedSheetMetadata(
                version = sheet.intlVersion,
                level = sheet.intlLevel ?: sheet.level,
                levelValue = sheet.intlLevelValue,
                internalLevel = sheet.intlInternalLevel,
                internalLevelValue = sheet.intlInternalLevelValue,
            )
            else -> null
        }
        val hasOverrideConstant = override?.internalLevelValue != null || override?.levelValue != null

        return ResolvedSheetMetadata(
            version = override?.version ?: sheet.version,
            level = override?.level ?: sheet.level,
            levelValue = override?.levelValue ?: sheet.levelValue,
            internalLevel = override?.internalLevel ?: if (hasOverrideConstant) null else sheet.internalLevel,
            internalLevelValue = override?.internalLevelValue
                ?: override?.levelValue
                ?: sheet.internalLevelValue,
        )
    }
}
