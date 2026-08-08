package net.krtl.maimaid.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import net.krtl.maimaid.data.local.converter.RoomConverters
import net.krtl.maimaid.data.local.dao.MaimaiDao
import net.krtl.maimaid.data.local.entity.CommunityAliasCacheEntity
import net.krtl.maimaid.data.local.entity.MaimaiIconEntity
import net.krtl.maimaid.data.local.entity.PlayRecordEntity
import net.krtl.maimaid.data.local.entity.ScoreEntity
import net.krtl.maimaid.data.local.entity.SheetEntity
import net.krtl.maimaid.data.local.entity.SongEntity
import net.krtl.maimaid.data.local.entity.SyncConfigEntity
import net.krtl.maimaid.data.local.entity.UserProfileEntity

@Database(
    entities = [
        SongEntity::class,
        SheetEntity::class,
        ScoreEntity::class,
        PlayRecordEntity::class,
        UserProfileEntity::class,
        SyncConfigEntity::class,
        MaimaiIconEntity::class,
        CommunityAliasCacheEntity::class
    ],
    version = 5,
    exportSchema = true
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun maimaiDao(): MaimaiDao
}
