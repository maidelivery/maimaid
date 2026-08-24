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
        SongCollectionEntity::class,
        SongCollectionItemEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
@TypeConverters(DatabaseConverters::class)
abstract class MaimaidDatabase : RoomDatabase() {
    abstract fun catalogDao(): CatalogDao
    abstract fun presetAvatarDao(): PresetAvatarDao
    abstract fun profileDao(): ProfileDao
    abstract fun scoreDao(): ScoreDao
    abstract fun songCollectionDao(): SongCollectionDao

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

        val Migration4To5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_play_records_profileId_sheetKey` " +
                        "ON `play_records` (`profileId`, `sheetKey`)",
                )
            }
        }

        val Migration5To6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `song_collections` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `sortIndex` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `song_collection_items` (`id` TEXT NOT NULL, `collectionId` TEXT NOT NULL, `songId` TEXT NOT NULL, `chartType` TEXT NOT NULL, `difficulty` TEXT NOT NULL, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `deletedAt` INTEGER, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_collections_sortIndex` ON `song_collections` (`sortIndex`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_collections_updatedAt` ON `song_collections` (`updatedAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_song_collection_items_collectionId_songId_chartType_difficulty` ON `song_collection_items` (`collectionId`, `songId`, `chartType`, `difficulty`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_collection_items_collectionId_position` ON `song_collection_items` (`collectionId`, `position`)")
            }
        }

        val Migration6To7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `song_collections` ADD COLUMN `clientUpdatedAt` INTEGER")
                db.execSQL("ALTER TABLE `song_collection_items` ADD COLUMN `clientUpdatedAt` INTEGER")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_collections_clientUpdatedAt` ON `song_collections` (`clientUpdatedAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_collection_items_clientUpdatedAt` ON `song_collection_items` (`clientUpdatedAt`)")
            }
        }
    }
}
