package net.krtl.maimaid.core.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class BackendHttpClient(
    private val okHttpClient: OkHttpClient,
    @PublishedApi internal val json: Json
) {
    companion object {
        private val jsonMediaType = "application/json".toMediaType()
    }

    data class HttpResult(
        val statusCode: Int,
        val body: String
    )

    suspend fun execute(
        path: String,
        method: String = "GET",
        bodyJson: String? = null,
        bearerToken: String? = null
    ): HttpResult = withContext(Dispatchers.IO) {
        val url = BackendConfig.endpoint(path) ?: error("Backend is not configured.")
        val request = Request.Builder()
            .url(url)
            .header("X-Maimaid-Client", "app")
            .apply {
                if (!bearerToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $bearerToken")
                }
            }
            .method(
                method,
                when {
                    method == "GET" || method == "DELETE" -> null
                    bodyJson != null -> bodyJson.toRequestBody(jsonMediaType)
                    else -> "{}".toRequestBody(jsonMediaType)
                }
            )
            .build()

        okHttpClient.newCall(request).execute().use { response ->
            HttpResult(
                statusCode = response.code,
                body = response.body.string()
            )
        }
    }

    inline fun <reified T> encodeBody(value: T): String = json.encodeToString(value)
    inline fun <reified T> decodeBody(value: String): T = json.decodeFromString(value)
}
