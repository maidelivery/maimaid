package org.rhythmeta.maimaid.core.network

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import org.rhythmeta.maimaid.core.data.StaticBundleResponse
import org.rhythmeta.maimaid.core.data.StaticManifest
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class StaticBundleClient(
    private val baseUrl: String,
    private val json: Json,
) {
    suspend fun fetchManifest(): StaticManifest = get("/v1/static/manifest")

    suspend fun fetchBundle(version: String): StaticBundleResponse =
        get("/v1/static/bundle/${Uri.encode(version)}")

    suspend fun downloadCover(imageName: String, destination: File) = withContext(Dispatchers.IO) {
        val url = URL("$COVER_BASE_URL${Uri.encode(imageName)}")
        val connection = url.openConnection() as HttpURLConnection
        val temporary = File(destination.parentFile, "${destination.name}.download")
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "image/*")
            connection.setRequestProperty("User-Agent", "maimaid-android")
            check(connection.responseCode in 200..299) { "HTTP ${connection.responseCode}" }
            temporary.parentFile?.mkdirs()
            connection.inputStream.use { input -> temporary.outputStream().use { output -> input.copyTo(output) } }
            check(temporary.length() > 0L) { "Empty cover response" }
            check(temporary.renameTo(destination)) { "Unable to cache cover" }
        } finally {
            connection.disconnect()
            temporary.delete()
        }
    }

    private suspend inline fun <reified T> get(path: String): T = withContext(Dispatchers.IO) {
        val connection = URL("${baseUrl.trimEnd('/')}$path").openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("X-Maimaid-Client", "android")
            connection.setRequestProperty("User-Agent", "maimaid-android")

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            check(status in 200..299) { "HTTP $status: ${body.take(200)}" }
            json.decodeFromString<T>(body)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val COVER_BASE_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/"
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 60_000
    }
}
