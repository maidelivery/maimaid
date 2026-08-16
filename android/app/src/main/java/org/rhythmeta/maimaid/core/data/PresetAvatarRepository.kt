package org.rhythmeta.maimaid.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import org.rhythmeta.maimaid.core.database.MaimaidDatabase
import org.rhythmeta.maimaid.core.database.PresetAvatarDao
import org.rhythmeta.maimaid.core.database.PresetAvatarEntity
import org.rhythmeta.maimaid.core.network.BackendApiClient
import java.io.File

@Serializable
data class PresetAvatar(
    val id: Int,
    val name: String,
    val genre: String,
) {
    val imageUrl: String
        get() = StaticAssetUrls.presetAvatarUrl(id)
}

class PresetAvatarRepository(
    private val apiClient: BackendApiClient,
    private val json: Json,
    private val database: MaimaidDatabase,
    private val avatarDao: PresetAvatarDao,
    private val imageStore: PresetAvatarImageStore,
) {
    val avatars: Flow<List<PresetAvatar>> = avatarDao.observeAvatars()
        .map { entities -> entities.map(PresetAvatarEntity::toPresetAvatar) }

    suspend fun list(): List<PresetAvatar> {
        return avatarDao.avatars().map(PresetAvatarEntity::toPresetAvatar)
    }

    suspend fun refresh(bundledAvatars: List<PresetAvatar>? = null): List<PresetAvatar> {
        val avatars = bundledAvatars
            ?.takeIf { it.isNotEmpty() }
            ?: list().takeIf { it.isNotEmpty() }
            ?: fetchRemoteAvatars()
        if (avatars.isEmpty()) return list()
        database.withTransaction {
            avatarDao.deleteAll()
            avatarDao.upsertAll(avatars.map(PresetAvatar::toEntity))
        }
        return avatars
    }

    fun imageFileFor(id: Int): File? = imageStore.fileFor(id)

    fun imageFileFor(url: String?): File? = PresetAvatarUrl.iconId(url)?.let(imageStore::fileFor)

    suspend fun downloadMissing(
        avatars: Iterable<PresetAvatar>,
        onProgress: (completedItems: Int, totalItems: Int) -> Unit = { _, _ -> },
        download: suspend (id: Int, destination: File) -> Unit,
    ) = imageStore.downloadMissing(avatars, onProgress, download)

    private suspend fun fetchRemoteAvatars(): List<PresetAvatar> {
        val primary = runCatching {
            apiClient.requestAbsolute(LxnsIconListUrl).toAvatars()
        }.getOrDefault(emptyList())
        if (primary.isNotEmpty()) return primary

        return runCatching {
            apiClient.request("v1/catalog/icons").toAvatars()
        }.getOrDefault(emptyList())
    }

    private fun kotlinx.serialization.json.JsonElement.toAvatars(): List<PresetAvatar> =
        json.decodeFromJsonElement(PresetAvatarResponse.serializer(), this)
            .icons
            .sortedBy(PresetAvatar::id)

    private companion object {
        const val LxnsIconListUrl = "https://maimai.lxns.net/api/v0/maimai/icon/list"
    }
}

object PresetAvatarUrl {
    fun forIcon(id: Int): String = StaticAssetUrls.presetAvatarUrl(id)

    fun isPreset(url: String?): Boolean = iconId(url) != null

    fun iconId(url: String?): Int? = StaticAssetUrls.presetAvatarId(url)
}

private fun PresetAvatarEntity.toPresetAvatar() = PresetAvatar(id, name, genre)

private fun PresetAvatar.toEntity() = PresetAvatarEntity(id, name, genre)

@Serializable
private data class PresetAvatarResponse(
    val icons: List<PresetAvatar> = emptyList(),
)
