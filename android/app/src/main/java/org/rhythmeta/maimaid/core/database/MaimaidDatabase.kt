package org.rhythmeta.maimaid.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
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
    version = 4,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
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

        val Migration2To3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `intlVersion` TEXT")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `intlLevel` TEXT")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `intlLevelValue` REAL")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `intlInternalLevel` TEXT")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `intlInternalLevelValue` REAL")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `cnVersion` TEXT")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `cnLevel` TEXT")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `cnLevelValue` REAL")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `cnInternalLevel` TEXT")
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `cnInternalLevelValue` REAL")
            }
        }

        val Migration3To4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `sheets` ADD COLUMN `multiverInternalLevelValue` TEXT")
            }
        }
    }
}
