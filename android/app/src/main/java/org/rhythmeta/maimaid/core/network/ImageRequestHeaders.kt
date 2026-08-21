package org.rhythmeta.maimaid.core.network

import android.graphics.ImageDecoder
import android.os.Build

object ImageRequestHeaders {
    val ACCEPT: String by lazy {
        acceptHeader(
            supportsAvif = supportsMimeType("image/avif"),
            supportsWebp = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || supportsMimeType("image/webp"),
        )
    }

    internal fun acceptHeader(
        supportsAvif: Boolean,
        supportsWebp: Boolean,
    ): String = buildList {
        if (supportsAvif) add("image/avif")
        if (supportsWebp) add("image/webp")
        add("image/png")
        add("image/jpeg")
    }.joinToString(",")

    private fun supportsMimeType(mimeType: String): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && ImageDecoder.isMimeTypeSupported(mimeType)
}
