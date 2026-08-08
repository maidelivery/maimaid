package net.krtl.maimaid.data.local.relation

import androidx.room.Embedded
import androidx.room.Relation
import net.krtl.maimaid.data.local.entity.SheetEntity
import net.krtl.maimaid.data.local.entity.SongEntity

data class SongWithSheets(
    @Embedded val song: SongEntity,
    @Relation(
        parentColumn = "songIdentifier",
        entityColumn = "songIdentifier"
    )
    val sheets: List<SheetEntity>
)
