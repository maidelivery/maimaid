package org.rhythmeta.maimaid.core.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_profiles",
    indices = [Index("isActive")],
)
data class UserProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val server: String,
    val avatarPath: String? = null,
    val avatarUrl: String? = null,
    val isActive: Boolean,
    val createdAt: Long,
    val dfUsername: String = "",
    val playerRating: Int = 0,
    val plate: String? = null,
    val lastImportDateDf: Long? = null,
    val lastImportDateLxns: Long? = null,
    val b35Count: Int = 35,
    val b15Count: Int = 15,
    val b35RecLimit: Int = 10,
    val b15RecLimit: Int = 10,
)
