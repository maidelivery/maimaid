package org.rhythmeta.maimaid.core.data

import android.net.Uri
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.rhythmeta.maimaid.core.network.BackendApiException

@Serializable
data class BackendImportRunResponse(
    val importRunId: String,
    val fetchedCount: Int,
    val upsertedCount: Int,
    val skippedCount: Int,
    val latestRevision: String? = null,
)

data class LxnsTokenPair(
    val accessToken: String,
    val refreshToken: String,
)

class LxnsTokenExpiredException : Exception("LXNS authorization expired.")

class BackendImportService(
    private val sessionManager: BackendSessionManager,
    private val json: Json,
) {
    suspend fun importDivingFish(
        profileId: String,
        username: String?,
        qq: String?,
        importToken: String?,
    ): BackendImportRunResponse {
        val payload = buildJsonObject {
            put("profileId", profileId)
            username?.trim()?.takeIf(String::isNotEmpty)?.let { put("username", it) }
            qq?.trim()?.takeIf(String::isNotEmpty)?.let { put("qq", it) }
            importToken?.trim()?.takeIf(String::isNotEmpty)?.let { put("importToken", it) }
        }
        val response = sessionManager.authorizedRequest(
            path = "v1/imports:importDf",
            method = "POST",
            body = payload,
        )
        return json.decodeFromJsonElement(BackendImportRunResponse.serializer(), response)
    }

    suspend fun importLxns(profileId: String, accessToken: String): BackendImportRunResponse {
        val response = sessionManager.authorizedRequest(
            path = "v1/imports:importLxns",
            method = "POST",
            body = buildJsonObject {
                put("profileId", profileId)
                put("accessToken", accessToken)
            },
        )
        return json.decodeFromJsonElement(BackendImportRunResponse.serializer(), response)
    }

    fun createLxnsAuthorization(): LxnsAuthorization {
        val verifierBytes = ByteArray(64).also(SecureRandom()::nextBytes)
        val verifier = Base64.encodeToString(
            verifierBytes,
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        val url = Uri.parse(LxnsAuthorizeUrl).buildUpon()
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("client_id", LxnsClientId)
            .appendQueryParameter("redirect_uri", LxnsRedirectUri)
            .appendQueryParameter("scope", LxnsScope.replace('+', ' '))
            .appendQueryParameter("code_challenge", challenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("state", UUID.randomUUID().toString())
            .build()
            .toString()
        return LxnsAuthorization(url = url, codeVerifier = verifier)
    }

    suspend fun exchangeLxnsCode(code: String, codeVerifier: String): LxnsTokenPair {
        val response = sessionManager.authorizedRequest(
            path = "v1/imports:exchangeLxnsToken",
            method = "POST",
            body = buildJsonObject {
                put("code", code.trim())
                put("codeVerifier", codeVerifier)
            },
        )
        val token = json.decodeFromJsonElement(BackendLxnsToken.serializer(), response)
        return LxnsTokenPair(token.accessToken, token.refreshToken)
    }

    suspend fun refreshLxnsToken(refreshToken: String): LxnsTokenPair = withContext(Dispatchers.IO) {
        val body = formBody(
            "grant_type" to "refresh_token",
            "client_id" to LxnsClientId,
            "refresh_token" to refreshToken.trim(),
        )
        val connection = URL(LxnsTokenUrl).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = "POST"
            connection.connectTimeout = NetworkTimeoutMillis
            connection.readTimeout = NetworkTimeoutMillis
            connection.doOutput = true
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            if (status == 400) throw LxnsTokenExpiredException()
            if (status !in 200..299) {
                throw BackendApiException(status, "lxns_token_refresh_failed", "LXNS HTTP $status")
            }
            val response = json.decodeFromString(LxnsTokenEnvelope.serializer(), responseText)
            val token = response.data
                ?: throw BackendApiException(status, "lxns_token_refresh_failed", response.message ?: "LXNS token missing.")
            LxnsTokenPair(token.accessToken, token.refreshToken)
        } finally {
            connection.disconnect()
        }
    }

    private fun formBody(vararg values: Pair<String, String>): String = values.joinToString("&") { (key, value) ->
        "${encode(key)}=${encode(value)}"
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())

    data class LxnsAuthorization(val url: String, val codeVerifier: String)

    @Serializable
    private data class BackendLxnsToken(
        val accessToken: String,
        val refreshToken: String,
    )

    @Serializable
    private data class LxnsTokenEnvelope(
        val success: Boolean? = null,
        val data: LxnsTokenData? = null,
        val message: String? = null,
    )

    @Serializable
    private data class LxnsTokenData(
        @kotlinx.serialization.SerialName("access_token") val accessToken: String,
        @kotlinx.serialization.SerialName("refresh_token") val refreshToken: String,
    )

    private companion object {
        const val LxnsClientId = "cfb7ef40-bc0f-4e3a-8258-9e5f52cd7338"
        const val LxnsRedirectUri = "urn:ietf:wg:oauth:2.0:oob"
        const val LxnsScope = "read_user_profile+read_player+write_player+read_user_token"
        const val LxnsAuthorizeUrl = "https://maimai.lxns.net/oauth/authorize"
        const val LxnsTokenUrl = "https://maimai.lxns.net/api/v0/oauth/token"
        const val NetworkTimeoutMillis = 30_000
    }
}
