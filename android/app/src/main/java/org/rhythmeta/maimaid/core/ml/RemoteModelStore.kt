package org.rhythmeta.maimaid.core.ml

import android.content.Context
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

sealed interface ModelAvailability {
    data class Ready(val offline: Boolean = false) : ModelAvailability
    data class DownloadRequired(val totalBytes: Long) : ModelAvailability
    data class UpdateAvailable(val totalBytes: Long) : ModelAvailability
    data class Failed(val message: String) : ModelAvailability
}

data class ModelDownloadProgress(
    val downloadedBytes: Long,
    val totalBytes: Long,
) {
    val fraction: Float
        get() = if (totalBytes > 0L) {
            (downloadedBytes.toFloat() / totalBytes).coerceIn(0f, 1f)
        } else {
            0f
        }
}

internal interface ModelAssetTransport {
    suspend fun fetchManifest(): List<ModelManifestEntry>

    suspend fun download(
        entry: ModelManifestEntry,
        destination: File,
        onBytes: (Long) -> Unit,
    )
}

class RemoteModelStore internal constructor(
    private val modelDirectory: File,
    private val transport: ModelAssetTransport,
    private val json: Json,
) {
    constructor(context: Context, baseUrl: String, json: Json) : this(
        modelDirectory = File(context.applicationContext.filesDir, ModelDirectoryName),
        transport = HttpModelAssetTransport(baseUrl, json),
        json = json,
    )

    private val operationMutex = Mutex()
    private val activeManifestFile = File(modelDirectory, ActiveManifestFilename)
    private var pendingManifest: List<ModelManifestEntry>? = null

    init {
        modelDirectory.mkdirs()
    }

    suspend fun hasUsableCachedModels(): Boolean = withContext(Dispatchers.IO) {
        loadActiveManifest()?.let(::isComplete) == true
    }

    suspend fun invalidateActiveModels() = withContext(Dispatchers.IO) { operationMutex.withLock {
        loadActiveManifest()?.forEach { entry -> fileFor(entry).delete() }
        activeManifestFile.delete()
        pendingManifest = null
    } }

    suspend fun inspect(): ModelAvailability = withContext(Dispatchers.IO) { operationMutex.withLock {
        val active = loadActiveManifest()
        val activeReady = active?.let(::isComplete) == true
        try {
            val remote = requiredEntries(transport.fetchManifest())
            migrateLegacyFiles(remote)
            if (isComplete(remote)) {
                if (active != remote) writeActiveManifest(remote)
                pendingManifest = null
                return@withLock ModelAvailability.Ready()
            }

            val missingBytes = missingBytes(remote)
            pendingManifest = remote
            if (activeReady) {
                ModelAvailability.UpdateAvailable(missingBytes)
            } else {
                ModelAvailability.DownloadRequired(missingBytes)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            pendingManifest = null
            if (activeReady) {
                ModelAvailability.Ready(offline = true)
            } else {
                ModelAvailability.Failed(error.message ?: error::class.java.simpleName)
            }
        }
    } }

    suspend fun downloadPending(
        onProgress: (ModelDownloadProgress) -> Unit,
    ) = withContext(Dispatchers.IO) { operationMutex.withLock {
        val manifest = pendingManifest ?: requiredEntries(transport.fetchManifest())
        val totalBytes = missingBytes(manifest)
        var downloadedBytes = 0L
        onProgress(ModelDownloadProgress(downloadedBytes, totalBytes))

        try {
            for (entry in manifest) {
                currentCoroutineContext().ensureActive()
                if (isValid(fileFor(entry), entry)) continue

                val destination = fileFor(entry)
                destination.parentFile?.mkdirs()
                val temporary = File(destination.parentFile, "${entry.filename}.download")
                temporary.delete()
                transport.download(entry, temporary) { byteCount ->
                    downloadedBytes += byteCount.coerceAtLeast(0L)
                    onProgress(ModelDownloadProgress(downloadedBytes, totalBytes))
                }
                check(isValid(temporary, entry)) { "Model verification failed: ${entry.filename}" }
                moveAtomically(temporary, destination)
            }
            check(isComplete(manifest)) { "Downloaded model set is incomplete" }
            writeActiveManifest(manifest)
            pendingManifest = null
            onProgress(ModelDownloadProgress(totalBytes, totalBytes))
        } catch (error: CancellationException) {
            cleanupTemporaryFiles(manifest)
            throw error
        } catch (error: Exception) {
            cleanupTemporaryFiles(manifest)
            throw error
        }
    } }

    suspend fun file(asset: ModelAsset): File = withContext(Dispatchers.IO) {
        val active = loadActiveManifest() ?: error("Vision models have not been downloaded")
        val entry = active.firstOrNull { it.filename == asset.filename }
            ?: error("Model is missing from the active manifest: ${asset.filename}")
        val file = fileFor(entry)
        check(isValid(file, entry)) { "Cached model verification failed: ${entry.filename}" }
        file
    }

    private fun requiredEntries(entries: List<ModelManifestEntry>): List<ModelManifestEntry> {
        val validated = entries.map(ModelManifestEntry::validated).associateBy(ModelManifestEntry::filename)
        return ModelAsset.entries.map { asset ->
            validated[asset.filename] ?: error("Manifest is missing ${asset.filename}")
        }
    }

    private fun loadActiveManifest(): List<ModelManifestEntry>? = runCatching {
        requiredEntries(json.decodeFromString(activeManifestFile.readText()))
    }.getOrNull()

    private fun writeActiveManifest(entries: List<ModelManifestEntry>) {
        modelDirectory.mkdirs()
        val temporary = File(modelDirectory, "$ActiveManifestFilename.download")
        temporary.writeText(json.encodeToString(entries))
        moveAtomically(temporary, activeManifestFile)
    }

    private fun isComplete(entries: List<ModelManifestEntry>): Boolean =
        entries.all { entry -> isValid(fileFor(entry), entry) }

    private fun missingBytes(entries: List<ModelManifestEntry>): Long = entries.sumOf { entry ->
        if (isValid(fileFor(entry), entry)) 0L else entry.size
    }

    private fun fileFor(entry: ModelManifestEntry): File =
        File(File(modelDirectory, entry.sha256), entry.filename)

    private fun migrateLegacyFiles(entries: List<ModelManifestEntry>) {
        for (entry in entries) {
            val asset = ModelAsset.entries.first { it.filename == entry.filename }
            val legacyName = asset.legacyFilename ?: continue
            val legacy = File(modelDirectory, legacyName)
            val destination = fileFor(entry)
            if (isValid(destination, entry) || !isValid(legacy, entry)) continue
            destination.parentFile?.mkdirs()
            val temporary = File(destination.parentFile, "${entry.filename}.download")
            legacy.copyTo(temporary, overwrite = true)
            moveAtomically(temporary, destination)
        }
    }

    private fun cleanupTemporaryFiles(entries: List<ModelManifestEntry>) {
        entries.forEach { entry ->
            File(fileFor(entry).parentFile, "${entry.filename}.download").delete()
        }
    }

    private fun isValid(file: File, entry: ModelManifestEntry): Boolean =
        file.isFile && file.length() == entry.size && sha256(file) == entry.sha256

    private fun moveAtomically(source: File, destination: File) {
        destination.parentFile?.mkdirs()
        try {
            Files.move(
                source.toPath(),
                destination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING,
            )
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }

    private companion object {
        const val ModelDirectoryName = "onnx-models"
        const val ActiveManifestFilename = "active-manifest.json"

        fun sha256(file: File): String {
            val digest = MessageDigest.getInstance("SHA-256")
            file.inputStream().use { input ->
                val buffer = ByteArray(64 * 1_024)
                while (true) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}

private class HttpModelAssetTransport(
    private val baseUrl: String,
    private val json: Json,
) : ModelAssetTransport {
    override suspend fun fetchManifest(): List<ModelManifestEntry> = withContext(Dispatchers.IO) {
        val connection = openConnection("manifest.json", "application/json")
        try {
            check(connection.responseCode in 200..299) { "Model manifest HTTP ${connection.responseCode}" }
            connection.inputStream.bufferedReader().use { reader ->
                json.decodeFromString<List<ModelManifestEntry>>(reader.readText())
            }
        } finally {
            connection.disconnect()
        }
    }

    override suspend fun download(
        entry: ModelManifestEntry,
        destination: File,
        onBytes: (Long) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val connection = openConnection(entry.filename, "application/octet-stream, application/json")
        try {
            check(connection.responseCode in 200..299) { "Model HTTP ${connection.responseCode}: ${entry.filename}" }
            destination.parentFile?.mkdirs()
            connection.inputStream.use { input ->
                destination.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1_024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        onBytes(count.toLong())
                    }
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(filename: String, accept: String): HttpURLConnection =
        (URL("${baseUrl.trimEnd('/')}/$filename").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 120_000
            setRequestProperty("Accept", accept)
            setRequestProperty("User-Agent", "maimaid-android")
            setRequestProperty("X-Maimaid-Client", "android")
        }
}
