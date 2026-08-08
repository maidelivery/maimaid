package net.krtl.maimaid.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val songIdentifier: String,
    val category: String,
    val title: String,
    val artist: String,
    val imageName: String,
    val version: String?,
    val releaseDate: String?,
    val sortOrder: Int,
    val bpm: Double?,
    val isNew: Boolean,
    val isLocked: Boolean,
    val comment: String?,
    val searchKeywords: String?,
    val aliases: List<String>,
    val songId: Int,
    val isFavorite: Boolean
)

@Entity(
    tableName = "sheets",
    foreignKeys = [
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["songIdentifier"],
            childColumns = ["songIdentifier"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("songIdentifier")]
)
data class SheetEntity(
    @PrimaryKey val sheetId: String,
    val songIdentifier: String,
    val type: String,
    val difficulty: String,
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
    val songId: Int
)

@Entity(
    tableName = "scores",
    foreignKeys = [
        ForeignKey(
            entity = SheetEntity::class,
            parentColumns = ["sheetId"],
            childColumns = ["sheetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userProfileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sheetId", "userProfileId"]),
        Index("userProfileId")
    ]
)
data class ScoreEntity(
    @PrimaryKey val scoreKey: String,
    val sheetId: String,
    val userProfileId: String,
    val rate: Double,
    val rank: String,
    val achievementDate: Long,
    val dxScore: Int,
    val fc: String?,
    val fs: String?
)

@Entity(
    tableName = "play_records",
    foreignKeys = [
        ForeignKey(
            entity = SheetEntity::class,
            parentColumns = ["sheetId"],
            childColumns = ["sheetId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["userProfileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("sheetId"),
        Index(value = ["userProfileId", "playDate"])
    ]
)
data class PlayRecordEntity(
    @PrimaryKey val id: String,
    val sheetId: String,
    val userProfileId: String,
    val rate: Double,
    val rank: String,
    val playDate: Long,
    val dxScore: Int,
    val fc: String?,
    val fs: String?
)

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val server: String,
    val avatarUrl: String?,
    val isActive: Boolean,
    val createdAt: Long,
    val playerRating: Int,
    val plate: String?,
    val b35Count: Int,
    val b15Count: Int,
    val b35RecLimit: Int,
    val b15RecLimit: Int
)

@Entity(tableName = "sync_config")
data class SyncConfigEntity(
    @PrimaryKey val id: Int = 1,
    val isAutoUploadEnabled: Boolean,
    val backgroundSyncInterval: Int,
    val cloudBackupInterval: Int = 0,
    val themeRawValue: Int,
    val lastStaticDataUpdateDate: Long?,
    val lastCloudBackupDate: Long? = null,
    val lastSyncRevision: String = "0",
    val localDataOwnerUserId: String? = null,
    val pendingResolutionForUserId: String? = null,
    val pendingResolutionDetectedAt: Long? = null
)

@Entity(tableName = "maimai_icons")
data class MaimaiIconEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val descriptionText: String,
    val genre: String
)

@Entity(
    tableName = "community_alias_cache",
    indices = [
        Index("songIdentifier"),
        Index("updatedAt")
    ]
)
data class CommunityAliasCacheEntity(
    @PrimaryKey val candidateId: String,
    val songIdentifier: String,
    val aliasText: String,
    val updatedAt: Long,
    val approvedAt: Long?
)
