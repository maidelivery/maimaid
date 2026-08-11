package org.rhythmeta.maimaid.ui.song

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal data class SharedJacket(
    val uri: Uri,
    val mimeType: String,
)

internal fun jacketDisplayName(title: String, source: File): String {
    val baseName = title
        .trim()
        .replace(InvalidFileNameCharacters, "_")
        .trim('.', ' ')
        .take(80)
        .ifEmpty { "maimaid-cover" }
    return "$baseName.${jacketExtension(source)}"
}

internal suspend fun cacheJacketBitmap(
    context: Context,
    bitmap: Bitmap,
    imageName: String,
): File? = withContext(Dispatchers.IO) {
    runCatching {
        val directory = File(context.cacheDir, "jacket_actions").apply { mkdirs() }
        val baseName = File(imageName).nameWithoutExtension.ifBlank { "cover" }
        File(directory, "${baseName.take(80)}.png").also { destination ->
            destination.outputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
            }
        }
    }.getOrNull()
}

internal suspend fun prepareSharedJacket(
    context: Context,
    source: File,
    title: String,
): SharedJacket? = withContext(Dispatchers.IO) {
    runCatching {
        val directory = File(context.cacheDir, "shared_jackets").apply { mkdirs() }
        val destination = File(directory, jacketDisplayName(title, source))
        source.copyTo(destination, overwrite = true)
        SharedJacket(
            uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                destination,
            ),
            mimeType = jacketMimeType(destination),
        )
    }.getOrNull()
}

internal suspend fun saveJacketToDownloads(
    context: Context,
    source: File,
    title: String,
): Boolean = withContext(Dispatchers.IO) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return@withContext false
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, jacketDisplayName(title, source))
        put(MediaStore.MediaColumns.MIME_TYPE, jacketMimeType(source))
        put(MediaStore.MediaColumns.RELATIVE_PATH, "${Environment.DIRECTORY_DOWNLOADS}/maimaid")
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
        ?: return@withContext false
    runCatching {
        resolver.openOutputStream(uri)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open download destination")
        resolver.update(
            uri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )
    }.fold(
        onSuccess = { true },
        onFailure = {
            resolver.delete(uri, null, null)
            false
        },
    )
}

internal suspend fun saveJacketToUri(
    context: Context,
    source: File,
    destination: Uri,
): Boolean = withContext(Dispatchers.IO) {
    runCatching {
        context.contentResolver.openOutputStream(destination)?.use { output ->
            source.inputStream().use { input -> input.copyTo(output) }
        } ?: error("Unable to open document destination")
    }.isSuccess
}

private fun jacketExtension(file: File): String = file.extension
    .lowercase()
    .takeIf { it in SupportedJacketExtensions }
    ?: "jpg"

private fun jacketMimeType(file: File): String = MimeTypeMap.getSingleton()
    .getMimeTypeFromExtension(jacketExtension(file))
    ?: "image/jpeg"

private val InvalidFileNameCharacters = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
private val SupportedJacketExtensions = setOf("jpg", "jpeg", "png", "webp")
