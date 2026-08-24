package org.rhythmeta.maimaid.core.data

import android.util.Base64
import java.io.ByteArrayOutputStream
import java.util.zip.Deflater
import java.util.zip.Inflater
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class SongCollectionExport(
    @SerialName("v") val version: Int,
    @SerialName("k") val kind: String,
    @SerialName("c") val collections: List<SongCollectionExportCollection>,
)

@Serializable
data class SongCollectionExportCollection(
    @SerialName("i") val id: String,
    @SerialName("n") val name: String,
    @SerialName("p") val position: Int,
    @SerialName("e") val entries: List<SongCollectionExportEntry>,
)

@Serializable
data class SongCollectionExportEntry(
    @SerialName("s") val songId: String,
    @SerialName("t") val chartType: String,
    @SerialName("d") val difficulty: String,
    @SerialName("p") val position: Int,
)

object SongCollectionCodec {
    const val Prefix = "MMD1."
    const val Kind = "MMD_COLLECTIONS"
    private val json = Json { encodeDefaults = true; explicitNulls = false }

    fun encode(collections: List<SongCollectionExportCollection>): String {
        val raw = json.encodeToString(SongCollectionExport.serializer(), SongCollectionExport(1, Kind, collections))
            .encodeToByteArray()
        val deflater = Deflater(Deflater.BEST_COMPRESSION)
        deflater.setInput(raw)
        deflater.finish()
        val buffer = ByteArray(8192)
        val compressed = ByteArrayOutputStream()
        while (!deflater.finished()) compressed.write(buffer, 0, deflater.deflate(buffer))
        deflater.end()
        return Prefix + Base64.encodeToString(compressed.toByteArray(), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
    }

    fun decode(value: String): SongCollectionExport {
        require(value.startsWith(Prefix) && value.length <= 2_000_000)
        val compressed = Base64.decode(value.removePrefix(Prefix), Base64.URL_SAFE or Base64.NO_WRAP)
        require(compressed.size <= 1_000_000)
        val inflater = Inflater()
        inflater.setInput(compressed)
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (!inflater.finished()) {
            val count = inflater.inflate(buffer)
            require(count > 0 || !inflater.needsInput())
            output.write(buffer, 0, count)
            require(output.size() <= 1_000_000)
        }
        inflater.end()
        val result = json.decodeFromString<SongCollectionExport>(output.toString(Charsets.UTF_8.name()))
        require(result.version == 1 && result.kind == Kind && result.collections.size <= 100)
        return result
    }
}
