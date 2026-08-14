package org.rhythmeta.maimaid.core.data

sealed interface CatalogSyncStatus {
    data object Idle : CatalogSyncStatus
    data object Checking : CatalogSyncStatus
    data class Downloading(
        val version: String,
        val progress: CatalogSyncProgress,
    ) : CatalogSyncStatus
    data class Ready(val version: String, val fromCache: Boolean) : CatalogSyncStatus
    data class Failed(val message: String, val hasLocalCatalog: Boolean) : CatalogSyncStatus
}

enum class CatalogSyncStage {
    CatalogBundle,
    ImportingCatalog,
    Covers,
    PresetAvatars,
    Finalizing,
}

data class CatalogSyncProgress(
    val stage: CatalogSyncStage,
    val overallFraction: Float,
    val completedItems: Int = 0,
    val totalItems: Int = 0,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val bytesPerSecond: Long = 0,
)
