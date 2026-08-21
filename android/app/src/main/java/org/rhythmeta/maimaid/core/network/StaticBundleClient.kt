package org.rhythmeta.maimaid.core.network

import android.graphics.BitmapFactory
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.rhythmeta.maimaid.core.data.StaticBundleResponse
import org.rhythmeta.maimaid.core.data.StaticAssetConfiguration
import org.rhythmeta.maimaid.core.data.StaticAssetUrls
import org.rhythmeta.maimaid.core.data.StaticManifest
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class StaticBundleClient(
    private val baseUrl: String,
    private val json: Json,
) {
    suspend fun fetchManifest(): StaticManifest = get<StaticManifest>("/manifest.json").also {
        StaticAssetUrls.configure(it.assets)
    }

    suspend fun fetchBundle(
        manifest: StaticManifest,
        onTransfer: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ): StaticBundleResponse {
        val bundleUrl = URL(URL("${baseUrl.trimEnd('/')}/"), manifest.bundle).toString()
        return getUrl<StaticBundleResponse>(bundleUrl, onTransfer).validatedAgainst(manifest)
    }

    suspend fun downloadCover(
        imageName: String,
        destination: File,
        assets: StaticAssetConfiguration?,
        onTransfer: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        downloadImage(
            urlStrings = StaticAssetUrls.coverCandidates(imageName, assets),
            destination = destination,
            emptyResponseMessage = "Empty cover response",
            onTransfer = onTransfer,
        )
    }

    suspend fun downloadPresetAvatar(
        id: Int,
        destination: File,
        assets: StaticAssetConfiguration?,
        onTransfer: (downloadedBytes: Long, totalBytes: Long?) -> Unit = { _, _ -> },
    ) = withContext(Dispatchers.IO) {
        downloadImage(
            urlStrings = StaticAssetUrls.presetAvatarCandidates(id, assets),
            destination = destination,
            emptyResponseMessage = "Empty preset avatar response",
            onTransfer = onTransfer,
        )
    }

    private fun downloadImage(
        urlStrings: List<String>,
        destination: File,
        emptyResponseMessage: String,
        onTransfer: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        var lastError: Exception? = null
        for (urlString in urlStrings) {
            try {
                downloadImage(urlString, destination, emptyResponseMessage, onTransfer)
                return
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException(emptyResponseMessage)
    }

    private fun downloadImage(
        urlString: String,
        destination: File,
        emptyResponseMessage: String,
        onTransfer: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) {
        val url = URL(urlString)
        val connection = url.openConnection() as HttpURLConnection
        val temporary = File(destination.parentFile, "${destination.name}.download")
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", ImageRequestHeaders.ACCEPT)
            connection.setRequestProperty("User-Agent", "maimaid-android")
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            onTransfer(0L, connection.contentLengthLong.takeIf { it > 0L })
            temporary.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                temporary.outputStream().use { output ->
                    val buffer = ByteArray(TransferBufferSize)
                    while (true) {
                        val byteCount = input.read(buffer)
                        if (byteCount < 0) break
                        output.write(buffer, 0, byteCount)
                        onTransfer(byteCount.toLong(), null)
                    }
                }
            }
            check(temporary.length() > 0L) { emptyResponseMessage }
            val imageBounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(temporary.path, imageBounds)
            check(imageBounds.outWidth > 0 && imageBounds.outHeight > 0) { "Unsupported image response" }
            check(temporary.renameTo(destination)) { "Unable to cache cover" }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private suspend inline fun <reified T> get(
        path: String,
        noinline onTransfer: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ): T = getUrl("${baseUrl.trimEnd('/')}$path", onTransfer)

    @OptIn(ExperimentalSerializationApi::class)
    private suspend inline fun <reified T> getUrl(
        url: String,
        noinline onTransfer: ((downloadedBytes: Long, totalBytes: Long?) -> Unit)? = null,
    ): T = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Maimaid-Client", "android")
            connection.setRequestProperty("User-Agent", "maimaid-android")

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            if (status in 200..299) {
                onTransfer?.invoke(0L, connection.contentLengthLong.takeIf { it > 0L })
            }
            if (status !in 200..299) {
                val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                error("HTTP $status: ${body.take(200)}")
            }
            val responseStream = stream ?: error("Empty HTTP response")
            responseStream.use { input ->
                val progressStream = if (onTransfer != null) {
                    TransferInputStream(input, onTransfer)
                } else {
                    input
                }
                json.decodeFromStream<T>(progressStream)
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun StaticBundleResponse.validatedAgainst(manifest: StaticManifest): StaticBundleResponse {
        check(version == manifest.version && md5 == manifest.md5) { "Static bundle metadata mismatch" }
        return this
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val TransferBufferSize = 64 * 1_024
    }

    private class TransferInputStream(
        input: InputStream,
        private val onTransfer: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ) : FilterInputStream(input) {
        override fun read(): Int {
            val value = super.read()
            if (value >= 0) onTransfer(1L, null)
            return value
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            val count = super.read(buffer, offset, length)
            if (count > 0) onTransfer(count.toLong(), null)
            return count
        }
    }
}
