package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sheets",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["songIdentifier"],
            childColumns = ["songIdentifier"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("songIdentifier")],
)
data class SheetEntity(
    @PrimaryKey val sheetKey: String,
    val songIdentifier: String,
    val type: String,
    val difficulty: String,
    val version: String?,
    val level: String,
    val levelValue: Double?,
    val internalLevel: String?,
    val internalLevelValue: Double?,
    val noteDesigner: String?,
    val tap: Int?,
    val hold: Int?,
    val slide: Int?,
    val touch: Int?,
    val breakCount: Int?,
    val total: Int?,
    val regionJp: Boolean,
    val regionIntl: Boolean,
    val regionUsa: Boolean,
    val regionCn: Boolean,
    val providerSongId: Int = 0,
    val isRemoved: Boolean = false,
    val intlVersion: String? = null,
    val intlLevel: String? = null,
    val intlLevelValue: Double? = null,
    val intlInternalLevel: String? = null,
    val intlInternalLevelValue: Double? = null,
    val cnVersion: String? = null,
    val cnLevel: String? = null,
    val cnLevelValue: Double? = null,
    val cnInternalLevel: String? = null,
    val cnInternalLevelValue: Double? = null,
    val multiverInternalLevelValue: Map<String, Double>? = null,
)
