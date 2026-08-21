package org.rhythmeta.maimaid.core

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter
import java.time.Instant
import org.rhythmeta.maimaid.BuildConfig

internal class CrashLogStore(context: Context) {
    private val file = context.applicationContext.filesDir.resolve("last-crash.txt")

    fun install() {
        val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val stackTrace = StringWriter().also { writer ->
                    throwable.printStackTrace(PrintWriter(writer))
                }.toString()
                file.writeText(
                    buildString {
                        appendLine("maimaid crash report")
                        appendLine("Time: ${Instant.now()}")
                        appendLine("App version: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
                        appendLine("Thread: ${thread.name}")
                        appendLine()
                        append(stackTrace)
                    }.redactSecrets(),
                )
            }
            previousHandler?.uncaughtException(thread, throwable)
        }
    }

    fun read(): String? = file.takeIf { it.isFile }?.runCatching { readText() }?.getOrNull()

    private fun String.redactSecrets(): String = lineSequence().joinToString("\n") { line ->
        line.replace(
            Regex("(?i)(authorization\\s*[:=]\\s*bearer\\s+|access[_-]?token\\s*[:=]\\s*|refresh[_-]?token\\s*[:=]\\s*|password\\s*[:=]\\s*)\\S+"),
            "$1[REDACTED]",
        )
    }
}
