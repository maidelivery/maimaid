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
        add(appending(LegacyCoverBaseUrl, imageName))
    }.distinct()

    fun presetAvatarCandidates(
        id: Int,
        value: StaticAssetConfiguration? = configuration,
    ): List<String> = buildList {
        val name = "$id.png"
        value?.presetAvatarBaseUrl?.let { add(appending(it, name)) }
        value?.presetAvatarFallbackBaseUrl?.let { add(appending(it, name)) }
        add(appending(LegacyPresetAvatarBaseUrl, name))
    }.distinct()

    fun presetAvatarId(url: String?): Int? {
        val value = url ?: return null
        val uri = runCatching { URI(value) }.getOrNull() ?: return null
        if (uri.scheme != "https") return null
        val path = uri.path.orEmpty()
        val isKnownSource =
            (uri.host == "assets2.lxns.net" && path.startsWith("/maimai/icon/")) ||
                path.contains("/static-assets/lxns-icons/")
        if (!isKnownSource) return null
        return path.substringAfterLast('/').removeSuffix(".png").toIntOrNull()
    }

    private fun appending(baseUrl: String, name: String): String {
        val encodedName = URLEncoder.encode(name, Charsets.UTF_8).replace("+", "%20")
        return "${baseUrl.trimEnd('/')}/$encodedName"
    }

    private const val LegacyCoverBaseUrl = "https://dp4p6x0xfi5o9.cloudfront.net/maimai/img/cover/"
    private const val LegacyPresetAvatarBaseUrl = "https://assets2.lxns.net/maimai/icon/"
}
