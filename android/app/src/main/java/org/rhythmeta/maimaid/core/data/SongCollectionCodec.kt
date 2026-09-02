package org.rhythmeta.maimaid.core.data

import com.google.protobuf.InvalidProtocolBufferException
import java.io.ByteArrayOutputStream
import java.util.Base64
import java.util.zip.Deflater
import java.util.zip.Inflater
import org.rhythmeta.maimaid.sharing.SongCollectionShare

data class SongCollectionExport(
    val name: String,
    val entries: List<SongCollectionExportEntry>,
)

data class SongCollectionExportEntry(
    val songId: String,
    val chartType: String,
    val difficulty: String,
)

object SongCollectionCodec {
    const val PREFIX = "MMD2."
    const val WEB_BASE_URL = "https://maimaid.rhythmeta.org/collection/"

	private const val MAX_TEXT_LENGTH = 2_000_000
    private const val MAX_COMPRESSED_BYTES = 1_000_000
    private const val MAX_RAW_BYTES = 1_000_000
    private const val MAX_ENTRIES_PER_COLLECTION = 10_000

    fun encode(collection: SongCollectionExport): String {
        require(collection.name.length <= 200)
        require(collection.entries.size <= MAX_ENTRIES_PER_COLLECTION)
        val message = SongCollectionShare.newBuilder()
            .setName(collection.name)
            .addAllEntries(collection.entries.map { entry ->
                require(entry.songId.length <= 200)
                require(entry.chartType.length <= 32)
                require(entry.difficulty.length <= 64)
                org.rhythmeta.maimaid.sharing.SongCollectionEntry.newBuilder()
                    .setSongId(entry.songId)
                    .setChartType(entry.chartType.lowercase())
                    .setDifficulty(entry.difficulty.lowercase())
                    .build()
            })
            .build()
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(compress(message.toByteArray()))
    }

    fun webUrl(collection: SongCollectionExport): String = WEB_BASE_URL + encode(collection)

	fun decode(value: String): SongCollectionExport {
        val token = extractToken(value) ?: throw IllegalArgumentException("Invalid collection sharing link")
        val compressed = Base64.getUrlDecoder().decode(token.removePrefix(PREFIX))
        require(compressed.size <= MAX_COMPRESSED_BYTES)
        val message = try {
            SongCollectionShare.parseFrom(decompress(compressed))
        } catch (error: InvalidProtocolBufferException) {
            throw IllegalArgumentException("Invalid collection payload", error)
        }
        require(message.entriesCount <= MAX_ENTRIES_PER_COLLECTION)
        return SongCollectionExport(
            name = message.name,
            entries = message.entriesList.map { entry ->
                SongCollectionExportEntry(
                    songId = entry.songId,
                    chartType = entry.chartType,
                    difficulty = entry.difficulty,
                )
            },
        )
    }

    fun extractToken(value: String): String? {
        val normalized = value.filterNot(Char::isWhitespace)
        if (normalized.length > MAX_TEXT_LENGTH) return null
        if (normalized.startsWith(PREFIX)) return normalized
        return extractSegment(normalized)?.takeIf { it.startsWith(PREFIX) && it.length <= MAX_TEXT_LENGTH }
    }

    fun extractCollectionId(value: String): String? = extractSegment(value.filterNot(Char::isWhitespace))
        ?.takeIf { CollectionIdPattern.matches(it) }

    private fun extractSegment(value: String): String? {
        val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
        return when (uri.scheme) {
					"https" if uri.host == "maimaid.rhythmeta.org" ->
						uri.path.removePrefix("/collection/").trim('/').takeIf(String::isNotEmpty)

					"maimaid" if uri.host == "collection" ->
						uri.path.trim('/').takeIf(String::isNotEmpty)

					else -> null
				}
    }

    private fun compress(raw: ByteArray): ByteArray {
        val deflater = Deflater(Deflater.BEST_COMPRESSION, true)
        return try {
            deflater.setInput(raw)
            deflater.finish()
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!deflater.finished()) output.write(buffer, 0, deflater.deflate(buffer))
            output.toByteArray()
        } finally {
            deflater.end()
        }
    }

    private fun decompress(compressed: ByteArray): ByteArray {
        val inflater = Inflater(true)
        return try {
            inflater.setInput(compressed)
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                require(count > 0 || !inflater.needsInput())
                output.write(buffer, 0, count)
                require(output.size() <= MAX_RAW_BYTES)
            }
            output.toByteArray()
        } finally {
            inflater.end()
        }
    }

    private val CollectionIdPattern = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}$")
}
