package org.rhythmeta.maimaid.core.data

import org.rhythmeta.maimaid.core.database.SheetEntity

internal object Best50ConstantResolver {
    fun resolve(
        sheet: SheetEntity,
        serverConstant: Double?,
        selectedVersion: String?,
        mode: Best50ConstantMode,
        chartFit: StaticBundleResponse.ChartFitPayload,
    ): Double? = when (mode) {
        Best50ConstantMode.Server -> serverConstant
        Best50ConstantMode.Fitted -> fittedConstant(sheet, chartFit) ?: serverConstant
        Best50ConstantMode.Version -> versionConstant(sheet, selectedVersion) ?: serverConstant
    }

    private fun fittedConstant(
        sheet: SheetEntity,
        chartFit: StaticBundleResponse.ChartFitPayload,
    ): Double? {
        val providerSongId = sheet.providerSongId.takeIf { it > 0 } ?: return null
        val candidateIds = buildList {
            if (sheet.type.equals("dx", ignoreCase = true) && providerSongId < 10_000) {
                add(providerSongId + 10_000)
            }
            add(providerSongId)
            if (sheet.type.equals("dx", ignoreCase = true) && providerSongId >= 10_000) {
                add(providerSongId - 10_000)
            }
        }.distinct()
        return candidateIds.firstNotNullOfOrNull { id ->
            chartFit.charts[id.toString()]
                ?.firstOrNull { it.diff == sheet.level }
                ?.fitDifficulty
                ?.takeIf { it > 0.0 }
        }
    }

    private fun versionConstant(sheet: SheetEntity, selectedVersion: String?): Double? {
        if (selectedVersion.isNullOrBlank()) return null
        return sheet.multiverInternalLevelValue
            ?.entries
            ?.firstOrNull { (version, _) -> version.equals(selectedVersion, ignoreCase = true) }
            ?.value
            ?.takeIf { it > 0.0 }
    }
}
