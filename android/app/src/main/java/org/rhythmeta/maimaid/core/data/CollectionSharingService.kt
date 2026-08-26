package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.rhythmeta.maimaid.core.network.BackendApiClient
import org.rhythmeta.maimaid.core.network.BackendApiException

@Serializable
private data class PublicCollectionResponse(val collection: PublicCollectionPayload)

@Serializable
private data class PublicCollectionPayload(
    val name: String,
    val entries: List<PublicCollectionEntry>,
)

@Serializable
private data class PublicCollectionEntry(
    val songId: String,
    val chartType: String,
    val difficulty: String,
)

class CollectionSharingService(
    private val apiClient: BackendApiClient,
    private val json: Json,
) {
    suspend fun fetchCloudCollection(collectionId: String): SongCollectionExport? {
        val response = try {
            apiClient.request("v1/public/collections/$collectionId")
        } catch (error: BackendApiException) {
            if (error.statusCode == 404) return null
            throw error
        }
        val payload = json.decodeFromJsonElement(PublicCollectionResponse.serializer(), response).collection
        return SongCollectionExport(
            name = payload.name,
            entries = payload.entries.map { entry ->
                SongCollectionExportEntry(entry.songId, entry.chartType, entry.difficulty)
            },
        )
    }

    suspend fun resolveImport(value: String): SongCollectionExport {
        SongCollectionCodec.extractToken(value)?.let { return SongCollectionCodec.decode(it) }
        val collectionId = SongCollectionCodec.extractCollectionId(value)
            ?: throw IllegalArgumentException("Invalid collection sharing link")
        return fetchCloudCollection(collectionId)
            ?: throw IllegalArgumentException("Collection not found")
    }
}
