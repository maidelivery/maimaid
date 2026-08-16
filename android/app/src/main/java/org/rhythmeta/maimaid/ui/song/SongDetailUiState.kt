package org.rhythmeta.maimaid.ui.song

import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.data.ResolvedSheetMetadata
import org.rhythmeta.maimaid.core.data.StaticBundleResponse

data class SheetScoreUiState(
    val sheet: SheetEntity,
    val score: ScoreEntity?,
    val history: List<PlayRecordEntity>,
    val chartFit: StaticBundleResponse.ChartFitStat? = null,
    val resolvedMetadata: ResolvedSheetMetadata? = null,
)

enum class ScoreSaveStatus {
    Idle,
    Saving,
    Saved,
    Failed,
}

data class SongDetailUiState(
    val profileId: String? = null,
    val charts: List<SheetScoreUiState> = emptyList(),
    val entrySheetKey: String? = null,
    val saveStatus: ScoreSaveStatus = ScoreSaveStatus.Idle,
)
