package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable

@Serializable
data class StaticAssetConfiguration(
    val coverBaseUrl: String,
    val presetAvatarBaseUrl: String,
    val coverFallbackBaseUrl: String? = null,
    val presetAvatarFallbackBaseUrl: String? = null,
)
