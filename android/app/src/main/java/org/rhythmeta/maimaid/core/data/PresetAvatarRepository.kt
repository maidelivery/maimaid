package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.rhythmeta.maimaid.core.network.BackendApiClient

@Serializable
data class PresetAvatar(
    val id: Int,
    val name: String,
    val genre: String,
) {
    val imageUrl: String
        get() = PresetAvatarUrl.forIcon(id)
}

class PresetAvatarRepository(
    private val apiClient: BackendApiClient,
    private val json: Json,
) {
    suspend fun list(): List<PresetAvatar> {
        val response = apiClient.request("v1/catalog/icons")
        return json.decodeFromJsonElement(PresetAvatarResponse.serializer(), response)
            .icons
            .sortedBy(PresetAvatar::id)
    }
}

object PresetAvatarUrl {
    private const val ImageBaseUrl = "https://assets2.lxns.net/maimai/icon/"
    private val PresetUrlPattern = Regex("^https://assets2\\.lxns\\.net/maimai/icon/\\d+\\.png(?:\\?.*)?$")

    fun forIcon(id: Int): String = "$ImageBaseUrl$id.png"

    fun isPreset(url: String?): Boolean = url?.matches(PresetUrlPattern) == true
}

@Serializable
private data class PresetAvatarResponse(
    val icons: List<PresetAvatar> = emptyList(),
)
