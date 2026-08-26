package org.rhythmeta.maimaid.core.ml

import kotlinx.serialization.Serializable

@Serializable
data class ModelManifestEntry(
    val filename: String,
    val sha256: String,
    val size: Long,
) {
    fun validated(): ModelManifestEntry {
        require(
            filename.isNotBlank() &&
                filename != "." &&
                filename != ".." &&
                !filename.contains('/') &&
                !filename.contains('\\') &&
                filename == java.io.File(filename).name
        ) {
            "Invalid model filename: $filename"
        }
        require(Sha256Pattern.matches(sha256)) { "Invalid SHA-256 for $filename" }
        require(size > 0L) { "Invalid model size for $filename" }
        return copy(sha256 = sha256.lowercase())
    }

    companion object {
        val Sha256Pattern = Regex("^[a-fA-F0-9]{64}$")
    }
}
