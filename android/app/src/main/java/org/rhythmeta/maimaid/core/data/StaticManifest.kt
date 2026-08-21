package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable

@Serializable
data class StaticManifest(
    val schemaVersion: Int = 1,
    val version: String,
    val md5: String,
    val createdAt: String? = null,
    val bundle: String,
    val assets: StaticAssetConfiguration? = null,
)
