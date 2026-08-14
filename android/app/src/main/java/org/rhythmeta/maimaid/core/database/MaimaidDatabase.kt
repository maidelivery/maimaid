package org.rhythmeta.maimaid.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        PresetAvatarEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class MaimaidDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun presetAvatarDao(): PresetAvatarDao
    abstract fun profileDao(): ProfileDao
    abstract fun scoreDao(): ScoreDao

    companion object {
        val Migration1To2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `preset_avatars` (" +
                        "`id` INTEGER NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`genre` TEXT NOT NULL, " +
                        "PRIMARY KEY(`id`))",
                )
            }
        }
    }
}
