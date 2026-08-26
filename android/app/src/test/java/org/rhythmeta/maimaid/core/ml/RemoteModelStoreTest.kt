package org.rhythmeta.maimaid.core.ml

import java.nio.file.Files
import java.nio.file.Path
import java.util.Comparator
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RemoteModelStoreTest {
    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun downloadsAndActivatesAllRequiredModelsWhileIgnoringUnknownEntries() = runBlocking {
        withTemporaryDirectory("maimaid-models-test") { directory ->
            val transport = FakeTransport().apply {
                manifest += ModelManifestEntry("future-model.bin", "f".repeat(64), 12L)
            }
            val store = RemoteModelStore(directory.toFile(), transport, json)

            assertTrue(store.inspect() is ModelAvailability.DownloadRequired)
            store.downloadPending { }

            assertTrue(store.inspect() is ModelAvailability.Ready)
            assertEquals("score-v1", store.file(ModelAsset.ScoreReader).readText())
            assertEquals("characters-v1", store.file(ModelAsset.TextCharacters).readText())
        }
    }

    @Test
    fun validCacheWorksWhenManifestRequestFails() = runBlocking {
        withTemporaryDirectory("maimaid-models-offline-test") { directory ->
            val store = RemoteModelStore(directory.toFile(), FakeTransport(), json)
            store.inspect()
            store.downloadPending { }

            val offlineStore = RemoteModelStore(directory.toFile(), FailingTransport(), json)

            assertEquals(ModelAvailability.Ready(offline = true), offlineStore.inspect())
        }
    }

    @Test
    fun corruptedCachedModelIsDownloadedAgain() = runBlocking {
        withTemporaryDirectory("maimaid-models-corrupt-test") { directory ->
            val transport = FakeTransport()
            val store = RemoteModelStore(directory.toFile(), transport, json)
            store.inspect()
            store.downloadPending { }
            store.file(ModelAsset.ScoreReader).writeText("corrupt")

            assertFalse(store.hasUsableCachedModels())
            assertTrue(store.inspect() is ModelAvailability.DownloadRequired)
            store.downloadPending { }

            assertEquals(2, transport.downloadCounts.getValue(ModelAsset.ScoreReader.filename))
            assertEquals("score-v1", store.file(ModelAsset.ScoreReader).readText())
        }
    }

    @Test
    fun incompleteTemporaryFileIsNeverReturnedToSessionFactory() = runBlocking {
        withTemporaryDirectory("maimaid-models-temporary-test") { directory ->
            val store = RemoteModelStore(directory.toFile(), FakeTransport(), json)
            store.inspect()
            store.downloadPending { }
            val finalFile = store.file(ModelAsset.ScoreReader)
            val temporary = finalFile.resolveSibling("${finalFile.name}.download")
            val validBytes = finalFile.readBytes()
            finalFile.delete()
            temporary.writeBytes(validBytes)

            val result = runCatching { store.file(ModelAsset.ScoreReader) }

            assertTrue(result.isFailure)
            assertTrue(temporary.isFile)
        }
    }

    @Test
    fun checksumFailureRemovesTemporaryDownload() = runBlocking {
        withTemporaryDirectory("maimaid-models-checksum-test") { directory ->
            val transport = FakeTransport().apply {
                corruptDownloadFilename = ModelAsset.ScoreReader.filename
            }
            val store = RemoteModelStore(directory.toFile(), transport, json)
            store.inspect()

            val result = runCatching { store.downloadPending { } }

            assertTrue(result.isFailure)
            assertEquals(
                0L,
                Files.walk(directory).use { paths ->
                    paths.filter { it.fileName.toString().endsWith(".download") }.count()
                },
            )
        }
    }

    @Test
    fun failedUpdatePreservesPreviousActiveManifest() = runBlocking {
        withTemporaryDirectory("maimaid-models-atomic-test") { directory ->
            val transport = FakeTransport()
            val store = RemoteModelStore(directory.toFile(), transport, json)
            store.inspect()
            store.downloadPending { }
            val activeManifest = directory.resolve("active-manifest.json").toFile().readText()

            transport.setVersion("v2")
            transport.failDownloadFilename = ModelAsset.RegionDetector.filename
            assertTrue(store.inspect() is ModelAvailability.UpdateAvailable)
            assertTrue(runCatching { store.downloadPending { } }.isFailure)

            assertEquals(activeManifest, directory.resolve("active-manifest.json").toFile().readText())
            assertEquals("score-v1", store.file(ModelAsset.ScoreReader).readText())
            assertTrue(store.hasUsableCachedModels())
        }
    }

    @Test
    fun manifestDecoderAllowsUnknownFields() {
        val entry = json.decodeFromString<ModelManifestEntry>(
            """{"filename":"model.onnx","sha256":"${"a".repeat(64)}","size":1,"future":true}""",
        )

        assertEquals("model.onnx", entry.filename)
        assertEquals(1L, entry.size)
    }

    @Test
    fun rejectsUnsafeManifestEntry() {
        listOf("../model.onnx", "folder/model.onnx", "folder\\model.onnx").forEach { filename ->
            val entry = ModelManifestEntry(filename, "a".repeat(64), 1L)
            assertTrue(runCatching { entry.validated() }.isFailure)
        }
    }

    @Test
    fun ocrCharactersLoadFromDownloadedFile() {
        val file = Files.createTempFile("maimaid-ocr-characters", ".json").toFile()
        try {
            file.writeText("""["日","本","A"]""")

            assertEquals(listOf("日", "本", "A", " "), loadPaddleCharacters(file))
        } finally {
            file.delete()
        }
    }

    private suspend fun withTemporaryDirectory(
        prefix: String,
        operation: suspend (Path) -> Unit,
    ) {
        val directory = Files.createTempDirectory(prefix)
        try {
            operation(directory)
        } finally {
            directory.deleteRecursively()
        }
    }

    private class FakeTransport : ModelAssetTransport {
        private var contentByFilename = contentForVersion("v1")
        var manifest = manifestFor(contentByFilename)
        var failDownloadFilename: String? = null
        var corruptDownloadFilename: String? = null
        val downloadCounts = ModelAsset.entries.associate { it.filename to 0 }.toMutableMap()

        fun setVersion(version: String) {
            contentByFilename = contentForVersion(version)
            manifest = manifestFor(contentByFilename)
        }

        override suspend fun fetchManifest(): List<ModelManifestEntry> = manifest

        override suspend fun download(entry: ModelManifestEntry, destination: java.io.File, onBytes: (Long) -> Unit) {
            if (entry.filename == failDownloadFilename) error("download failed")
            val expected = contentByFilename.getValue(entry.filename)
            val content = if (entry.filename == corruptDownloadFilename) "$expected-corrupt" else expected
            val bytes = content.toByteArray()
            destination.writeBytes(bytes)
            downloadCounts[entry.filename] = downloadCounts.getValue(entry.filename) + 1
            onBytes(bytes.size.toLong())
        }
    }

    private class FailingTransport : ModelAssetTransport {
        override suspend fun fetchManifest(): List<ModelManifestEntry> = error("offline")

        override suspend fun download(entry: ModelManifestEntry, destination: java.io.File, onBytes: (Long) -> Unit) =
            error("offline")
    }

    private fun Path.deleteRecursively() {
        if (!Files.exists(this)) return
        Files.walk(this).use { stream ->
            stream.sorted(Comparator.reverseOrder()).forEach { Files.deleteIfExists(it) }
        }
    }

    private companion object {
        fun contentForVersion(version: String): Map<String, String> = mapOf(
            ModelAsset.ScoreReader.filename to "score-$version",
            ModelAsset.RegionDetector.filename to "region-$version",
            ModelAsset.ScreenClassifier.filename to "classifier-$version",
            ModelAsset.TextRecognizer.filename to "text-$version",
            ModelAsset.TextCharacters.filename to "characters-$version",
        )

        fun manifestFor(contentByFilename: Map<String, String>): List<ModelManifestEntry> =
            contentByFilename.map { (filename, content) ->
                ModelManifestEntry(
                    filename = filename,
                    sha256 = sha256(content),
                    size = content.toByteArray().size.toLong(),
                )
            }

        fun sha256(value: String): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            return digest.digest(value.toByteArray()).joinToString("") { byte -> "%02x".format(byte) }
        }
    }
}
