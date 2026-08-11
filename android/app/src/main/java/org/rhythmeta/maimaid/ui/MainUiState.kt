package org.rhythmeta.maimaid.ui

import org.rhythmeta.maimaid.core.data.CatalogSyncStatus
import org.rhythmeta.maimaid.core.database.GameVersionEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.SongAliasEntity
import org.rhythmeta.maimaid.core.database.SongCategoryEntity
import org.rhythmeta.maimaid.core.database.SongEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity

data class MainUiState(
    val activeProfile: UserProfileEntity? = null,
    val songCount: Int = 0,
    val sheetCount: Int = 0,
    val featuredSongs: List<SongEntity> = emptyList(),
    val songs: List<SongEntity> = emptyList(),
    val sheets: List<SheetEntity> = emptyList(),
    val songAliases: List<SongAliasEntity> = emptyList(),
    val scores: List<ScoreEntity> = emptyList(),
    val songCategories: List<SongCategoryEntity> = emptyList(),
    val gameVersions: List<GameVersionEntity> = emptyList(),
    val catalogSyncStatus: CatalogSyncStatus = CatalogSyncStatus.Checking,
)
