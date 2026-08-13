package org.rhythmeta.maimaid.core.ml

import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

enum class MaimaiScreenType {
    Score,
    Choose,
    Unknown,
}

data class NormalizedRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
)

data class RecognizedRegion(
    val label: String,
    val bounds: NormalizedRect,
    val confidence: Float,
)

data class ScannerRawResult(
    val screenType: MaimaiScreenType = MaimaiScreenType.Unknown,
    val achievement: Double? = null,
    val difficulty: String? = null,
    val chartType: String? = null,
    val title: String? = null,
    val titleCandidates: List<String> = emptyList(),
    val dxScore: Int? = null,
    val maxDxScore: Int? = null,
    val comboStatus: String? = null,
    val syncStatus: String? = null,
    val level: Double? = null,
    val maxCombo: Int? = null,
    val kanji: String? = null,
    val regions: List<RecognizedRegion> = emptyList(),
    val imageWidth: Int = 0,
    val imageHeight: Int = 0,
)

data class ScannerMatch(
    val song: SongEntity,
    val sheet: SheetEntity?,
    val recognition: ScannerRawResult,
)

data class ScannerCatalog(
    val songs: List<SongEntity>,
    val sheets: List<SheetEntity>,
    val aliasesBySong: Map<String, List<String>>,
) {
    val sheetsBySong: Map<String, List<SheetEntity>> = sheets.groupBy(SheetEntity::songIdentifier)
}
