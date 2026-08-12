package org.rhythmeta.maimaid.core.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProfileAvatarStore(context: Context) {
    private val contentResolver = context.applicationContext.contentResolver
    private val avatarDirectory = File(context.applicationContext.filesDir, "profile-avatars").apply {
        mkdirs()
    }
    private val stagingDirectory = File(context.applicationContext.cacheDir, "profile-avatar-staging").apply {
        mkdirs()
    }

    suspend fun stage(uri: Uri): String? = withContext(Dispatchers.IO) {
        val target = File(stagingDirectory, "${UUID.randomUUID()}.image")
        runCatching {
            contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: error("Unable to open avatar")
            target.takeIf { it.length() > 0L }?.absolutePath
        }.getOrElse {
            target.delete()
            null
        }
    }

    suspend fun commit(stagedPath: String, profileId: String): String? = withContext(Dispatchers.IO) {
        val staged = File(stagedPath)
        if (!staged.isFile || staged.parentFile != stagingDirectory) return@withContext null
        val target = File(avatarDirectory, "$profileId.image")
        runCatching {
            staged.inputStream().use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
            staged.delete()
            target.takeIf { it.length() > 0L }?.absolutePath
        }.getOrNull()
    }

    fun discard(stagedPath: String?) {
        stagedPath?.let(::File)?.takeIf { it.parentFile == stagingDirectory }?.delete()
    }

    fun deleteStored(path: String?) {
        path?.let(::File)?.takeIf { it.parentFile == avatarDirectory }?.delete()
    }
}
