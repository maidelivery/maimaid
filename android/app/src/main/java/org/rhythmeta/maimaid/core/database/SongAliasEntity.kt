package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "song_aliases",
    primaryKeys = ["songIdentifier", "alias"],
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["songIdentifier"],
            childColumns = ["songIdentifier"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("songIdentifier"), Index("alias")],
)
data class SongAliasEntity(
    val songIdentifier: String,
    val alias: String,
)
