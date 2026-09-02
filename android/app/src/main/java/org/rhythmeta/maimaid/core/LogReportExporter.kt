package org.rhythmeta.maimaid.core

import android.content.Context
import android.os.Build
import android.os.Process
import androidx.core.content.FileProvider
import java.io.File
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.rhythmeta.maimaid.BuildConfig

internal object LogReportExporter {
    private const val LOG_LINE_LIMIT = 4_000
    private val fileNameFormatter = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")
        .withZone(ZoneId.systemDefault())

    suspend fun export(context: Context, crashLogStore: CrashLogStore): android.net.Uri = withContext(Dispatchers.IO) {
        val timestamp = fileNameFormatter.format(Instant.now())
        val file = File(context.cacheDir, "maimaid-logs-$timestamp.txt")
        val report = buildString {
            appendLine("maimaid diagnostic log")
            appendLine("Generated: ${Instant.now()}")
            appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("ABI: ${Build.SUPPORTED_ABIS.joinToString()}")
            appendLine("Process: ${Process.myPid()}")
            appendLine()
            appendLine("== previous crash ==")
            appendLine(crashLogStore.read() ?: "No previous crash report")
            appendLine()
            appendLine("== app logcat ==")
            appendLine(readRecentLogcat())
        }
        file.writeText(redact(report))
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    private fun readRecentLogcat(): String = runCatching {
        val process = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime",
            "-t", LOG_LINE_LIMIT.toString(),
        ).redirectErrorStream(true).start()
        process.inputStream.bufferedReader().use { it.readText() }.also { process.waitFor() }
    }.getOrElse { error ->
        "Unable to read logcat: ${error::class.simpleName}: ${error.message}"
    }

    private fun redact(value: String): String = value.lineSequence().joinToString("\n") { line ->
        line.replace(
            Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+|access[_-]?token\\s*[:=]\\s*|refresh[_-]?token\\s*[:=]\\s*|password\\s*[:=]\\s*)\\S+"),
            "$1[REDACTED]",
        )
    }
}
