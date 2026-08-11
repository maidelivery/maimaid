package org.rhythmeta.maimaid.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SongEntity::class,
        SheetEntity::class,
        UserProfileEntity::class,
        ScoreEntity::class,
        PlayRecordEntity::class,
        SongCategoryEntity::class,
        GameVersionEntity::class,
        SongAliasEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class MaimaidDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun profileDao(): ProfileDao
    abstract fun scoreDao(): ScoreDao
}
