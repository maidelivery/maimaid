package org.rhythmeta.maimaid.core.network

import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

class BackendApiException(
    val statusCode: Int?,
    val code: String?,
    override val message: String,
) : Exception(message)

class BackendApiClient(
    baseUrl: String,
    private val json: Json,
) {
    private val normalizedBaseUrl = baseUrl.trim().trimEnd('/')

    suspend fun request(
        path: String,
        method: String = "GET",
        body: JsonElement? = null,
        accessToken: String? = null,
    ): JsonElement {
        if (normalizedBaseUrl.isEmpty()) {
            throw BackendApiException(null, "unconfigured", "Cloud service is not configured.")
        }
        return requestAbsolute(
            url = "$normalizedBaseUrl/${path.trimStart('/')}",
            method = method,
            body = body,
            accessToken = accessToken,
        )
    }

    suspend fun requestAbsolute(
        url: String,
        method: String = "GET",
        body: JsonElement? = null,
        accessToken: String? = null,
    ): JsonElement = withContext(Dispatchers.IO) {
        val connection = URL(url)
            .openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Accept-Encoding", "gzip")
            connection.setRequestProperty("X-Maimaid-Client", "app")
            accessToken?.let { connection.setRequestProperty("Authorization", "Bearer $it") }
            if (body != null) {
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json")
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
                    writer.write(json.encodeToString(JsonElement.serializer(), body))
                }
            }

            val status = connection.responseCode
            val rawStream = if (status in 200..299) connection.inputStream else connection.errorStream
            val stream = if (connection.contentEncoding.equals("gzip", ignoreCase = true) && rawStream != null) {
                GZIPInputStream(rawStream)
            } else {
                rawStream
            }
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status !in 200..299) {
                val payload = runCatching { json.parseToJsonElement(responseText).jsonObject }.getOrNull()
                throw BackendApiException(
                    statusCode = status,
                    code = payload?.string("code"),
                    message = payload?.string("message") ?: "HTTP $status",
                )
            }
            if (responseText.isBlank()) JsonObject(emptyMap()) else json.parseToJsonElement(responseText)
        } finally {
            connection.disconnect()
        }
    }

    suspend fun upload(url: String, contentType: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "PUT"
            connection.connectTimeout = CONNECT_TIMEOUT_MILLIS
            connection.readTimeout = READ_TIMEOUT_MILLIS
            connection.doOutput = true
            connection.setFixedLengthStreamingMode(bytes.size)
            connection.setRequestProperty("Content-Type", contentType)
            connection.outputStream.use { it.write(bytes) }
            val status = connection.responseCode
            if (status !in 200..299) {
                throw BackendApiException(status, null, "Avatar upload failed: HTTP $status")
            }
        } finally {
            connection.disconnect()
        }
    }

    fun endpoint(path: String): String = "$normalizedBaseUrl/${path.trimStart('/')}"

    private companion object {
        const val CONNECT_TIMEOUT_MILLIS = 15_000
        const val READ_TIMEOUT_MILLIS = 45_000
    }
}

internal fun JsonObject.string(key: String): String? = this[key]
    ?.jsonPrimitive
    ?.content
    ?.takeIf(String::isNotBlank)
