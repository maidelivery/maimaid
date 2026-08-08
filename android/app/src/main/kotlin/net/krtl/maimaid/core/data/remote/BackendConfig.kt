package net.krtl.maimaid.core.data.remote

import net.krtl.maimaid.BuildConfig
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

object BackendConfig {
    val baseUrl: HttpUrl?
        get() = BuildConfig.BACKEND_URL.trim().toHttpUrlOrNull()

    val webAuthBaseUrl: HttpUrl?
        get() {
            val configured = BuildConfig.BACKEND_AUTH_URL.trim()
            if (configured.isNotEmpty()) {
                return configured.toHttpUrlOrNull()
            }
            val base = baseUrl ?: return null
            if (base.port == 8787 && (base.host == "localhost" || base.host == "127.0.0.1")) {
                return base.newBuilder()
                    .port(3000)
                    .encodedPath("/")
                    .query(null)
                    .fragment(null)
                    .build()
            }
            return base
        }

    fun endpoint(path: String): HttpUrl? {
        val base = baseUrl ?: return null
        val trimmed = path.trim()
        if (trimmed.isEmpty()) return base
        return base.newBuilder().apply {
            val split = trimmed.split("?", limit = 2)
            val relativePath = split.first().trim('/')
            if (relativePath.isNotEmpty()) {
                addPathSegments(relativePath)
            }
            if (split.size == 2) {
                encodedQuery(split[1])
            }
        }.build()
    }
}

