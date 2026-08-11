package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "scores",
    primaryKeys = ["profileId", "sheetKey"],
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SheetEntity::class,
            parentColumns = ["sheetKey"],
            childColumns = ["sheetKey"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("profileId"), Index("sheetKey")],
)
data class ScoreEntity(
    val profileId: String,
    val sheetKey: String,
    val achievement: Double,
    val rank: String,
    val dxScore: Int,
    val fc: String?,
    val fs: String?,
    val achievedAt: Long,
)
