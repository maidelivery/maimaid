package org.rhythmeta.maimaid.core.data

import java.net.URI
import java.net.URLEncoder

object StaticAssetUrls {
    @Volatile
    private var configuration: StaticAssetConfiguration? = null

    fun configure(value: StaticAssetConfiguration?) {
        configuration = value
    }

    fun coverUrl(imageName: String): String = coverCandidates(imageName).first()

    fun presetAvatarUrl(id: Int): String = presetAvatarCandidates(id).first()

    fun coverCandidates(
        imageName: String,
        value: StaticAssetConfiguration? = configuration,
    ): List<String> = buildList {
        value?.coverBaseUrl?.let { add(appending(it, imageName)) }
        value?.coverFallbackBaseUrl?.let { add(appending(it, imageName)) }
        add(appending(LEGACY_COVER_BASE_URL, imageName))
    }.distinct()

    fun presetAvatarCandidates(
        id: Int,
        value: StaticAssetConfiguration? = configuration,
    ): List<String> = buildList {
        val name = "$id.png"
        value?.presetAvatarBaseUrl?.let { add(appending(it, name)) }
        value?.presetAvatarFallbackBaseUrl?.let { add(appending(it, name)) }
        add(appending(LEGACY_PRESET_AVATAR_BASE_URL, name))
    }.distinct()

    fun presetAvatarId(url: String?): Int? {
        val value = url ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val path = uri.path.orEmpty()
        val isKnownSource =
            (uri.host == "assets2.lxns.net" && path.startsWith("/maimai/icon/")) ||
                path.contains("/lxns-icons/")
        if (!isKnownSource) return null
        return path.substringAfterLast('/').removeSuffix(".png").toIntOrNull()
    }

    private fun appending(baseUrl: String, name: String): String {
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8.name()).replace("+", "%20")
        return "${normalizeImageTransformation(baseUrl).trimEnd('/')}/$encodedName"
    }

    internal fun normalizeImageTransformation(value: String): String = value
        .replace("/cdn-cgi/image/format=avif/", "/cdn-cgi/image/format=png/")
        .replace("/cdn-cgi/image/f=avif/", "/cdn-cgi/image/format=png/")
        .replace("/cdn-cgi/image/format=auto/", "/cdn-cgi/image/format=png/")
        .replace("/cdn-cgi/image/f=auto/", "/cdn-cgi/image/format=png/")
        .replace("/cdn-cgi/image/format=webp/", "/cdn-cgi/image/format=png/")
        .replace("/cdn-cgi/image/f=webp/", "/cdn-cgi/image/format=png/")

    private const val LEGACY_COVER_BASE_URL = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/"
    private const val LEGACY_PRESET_AVATAR_BASE_URL = "https://assets2.lxns.net/maimai/icon/"
}
