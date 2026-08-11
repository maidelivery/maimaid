package org.rhythmeta.maimaid.core.data

sealed interface CatalogSyncStatus {
    data object Checking : CatalogSyncStatus
    data class Downloading(val version: String) : CatalogSyncStatus
    data class Ready(val version: String, val fromCache: Boolean) : CatalogSyncStatus
    data class Failed(val message: String, val hasLocalCatalog: Boolean) : CatalogSyncStatus
}
