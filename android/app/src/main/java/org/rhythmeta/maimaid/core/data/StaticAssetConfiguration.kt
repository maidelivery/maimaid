package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable

@Serializable
data class StaticAssetConfiguration(
    val coverBaseUrl: String,
    val coverFallbackBaseUrl: String,
    val presetAvatarBaseUrl: String,
    val presetAvatarFallbackBaseUrl: String,
)
