package org.rhythmeta.maimaid.ui.common

import android.content.Context
import android.content.Intent
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.net.toUri

fun Context.openInAppBrowser(url: String): Boolean {
    val uri = url.toUri()
    if (uri.scheme !in SupportedWebSchemes) return false

    return runCatching {
        CustomTabsIntent.Builder()
            .setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            .setShowTitle(true)
            .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
            .build()
            .launchUrl(this, uri)
    }.isSuccess
}

fun Context.openExternalApp(url: String, packageNames: List<String>): Boolean {
    val uri = url.toUri()
    return packageNames.any { packageName ->
        runCatching {
            startActivity(
                Intent(Intent.ACTION_VIEW, uri)
                    .setPackage(packageName),
            )
        }.isSuccess
    }
}

private val SupportedWebSchemes = setOf("http", "https")
