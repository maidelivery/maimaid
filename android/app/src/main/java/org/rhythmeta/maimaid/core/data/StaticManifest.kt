package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable

@Serializable
data class StaticManifest(
    val version: String,
    val md5: String,
    val createdAt: String? = null,
    val downloadUrl: String? = null,
    val assets: StaticAssetConfiguration? = null,
)
