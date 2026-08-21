package org.rhythmeta.maimaid.core.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.rhythmeta.maimaid.core.data.StaticBundleResponse
import org.rhythmeta.maimaid.core.data.StaticAssetConfiguration
import org.rhythmeta.maimaid.core.data.StaticAssetUrls
import org.rhythmeta.maimaid.core.data.StaticManifest
import java.io.ByteArrayOutputStream
import java.io.File
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
            val body = if (status in 200..299 && onTransfer != null) {
                stream?.use { readBody(it, onTransfer) }.orEmpty()
            } else {
                stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            }
            check(status in 200..299) { "HTTP $status: ${body.take(200)}" }
            json.decodeFromString<T>(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun StaticBundleResponse.validatedAgainst(manifest: StaticManifest): StaticBundleResponse {
        check(version == manifest.version && md5 == manifest.md5) { "Static bundle metadata mismatch" }
        return this
    }

    private fun readBody(
        input: InputStream,
        onTransfer: (downloadedBytes: Long, totalBytes: Long?) -> Unit,
    ): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(TransferBufferSize)
        while (true) {
            val byteCount = input.read(buffer)
            if (byteCount < 0) break
            output.write(buffer, 0, byteCount)
            onTransfer(byteCount.toLong(), null)
        }
        return output.toByteArray().toString(Charsets.UTF_8)
    }

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
        const val TransferBufferSize = 64 * 1_024
    }
}
