package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongEntity

@Serializable
data class DanCategory(
    val title: String,
    val id: String,
    val sections: List<DanSection> = emptyList(),
)

@Serializable
data class DanSection(
    val title: String? = null,
    val description: String? = null,
    val sheets: List<String> = emptyList(),
    @SerialName("sheetDescriptions") val sheetDescriptions: List<String>? = null,
)

data class DanSheetReference(
    val title: String,
    val type: String,
    val difficulty: String,
) {

	companion object {
        fun parse(raw: String): DanSheetReference {
            val parts = raw.split('|')
            return if (parts.size >= 3) {
                DanSheetReference(parts[0], parts[1], parts[2])
            } else {
                DanSheetReference(raw, "", "")
            }
        }
    }
}

data class DanCategoryGroup(
    val version: String,
    val versionLabel: String,
    val categories: List<DanCategory>,
)

data class DanChartEntry(
    val reference: DanSheetReference,
    val description: String?,
    val song: SongEntity?,
    val sheet: SheetEntity?,
    val score: ScoreEntity?,
)

data class DanSectionDetail(
    val title: String?,
    val description: String?,
    val charts: List<DanChartEntry>,
)

data class DanCategoryDetail(
    val category: DanCategory,
    val sections: List<DanSectionDetail>,
)
