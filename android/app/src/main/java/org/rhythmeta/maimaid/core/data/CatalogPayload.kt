package org.rhythmeta.maimaid.core.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CatalogPayload(
    val songs: List<Song> = emptyList(),
    val categories: List<Category> = emptyList(),
    val versions: List<Version> = emptyList(),
) {
    @Serializable
    data class Song(
        val songId: String,
        val category: String? = null,
        val title: String? = null,
        val artist: String? = null,
        val bpm: Double? = null,
        val imageName: String? = null,
        val version: String? = null,
        val releaseDate: String? = null,
        val isNew: Boolean? = null,
        val isLocked: Boolean? = null,
        val comment: String? = null,
        val sheets: List<Sheet> = emptyList(),
    )

    @Serializable
    data class Sheet(
        val type: String,
        val difficulty: String,
        val version: String? = null,
        val level: String,
        val levelValue: Double? = null,
        val internalLevel: String? = null,
        val internalLevelValue: Double? = null,
        val noteDesigner: String? = null,
        val noteCounts: NoteCounts? = null,
        val regions: Map<String, Boolean>? = null,
        val regionOverrides: Map<String, SheetOverride>? = null,
    )

    @Serializable
    data class SheetOverride(
        val version: String? = null,
        val level: String? = null,
        val levelValue: Double? = null,
        val internalLevel: String? = null,
        val internalLevelValue: Double? = null,
    )

    @Serializable
    data class NoteCounts(
        val tap: Int? = null,
        val hold: Int? = null,
        val slide: Int? = null,
        val touch: Int? = null,
        @SerialName("break") val breakCount: Int? = null,
        val total: Int? = null,
    )

    @Serializable
    data class Category(
        val category: String,
    )

    @Serializable
    data class Version(
        val version: String,
        val abbr: String,
        val releaseDate: String? = null,
    )
}
