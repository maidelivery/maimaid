package net.krtl.maimaid.util

import net.krtl.maimaid.data.local.entity.MaimaiIconEntity
import net.krtl.maimaid.data.local.entity.PlayRecordEntity
import net.krtl.maimaid.data.local.entity.ScoreEntity
import net.krtl.maimaid.data.local.entity.SheetEntity
import net.krtl.maimaid.data.local.entity.SyncConfigEntity
import net.krtl.maimaid.data.local.entity.UserProfileEntity
import net.krtl.maimaid.data.local.relation.SongWithSheets
import net.krtl.maimaid.domain.model.GameServer
import net.krtl.maimaid.domain.model.MaimaiIcon
import net.krtl.maimaid.domain.model.PlayRecord
import net.krtl.maimaid.domain.model.Score
import net.krtl.maimaid.domain.model.Sheet
import net.krtl.maimaid.domain.model.Song
import net.krtl.maimaid.domain.model.SyncConfig
import net.krtl.maimaid.domain.model.ThemeMode
import net.krtl.maimaid.domain.model.UserProfile

fun SongWithSheets.asDomain(): Song = Song(
    songIdentifier = song.songIdentifier,
    category = song.category,
    title = song.title,
    artist = song.artist,
    imageName = song.imageName,
    version = song.version,
    releaseDate = song.releaseDate,
    sortOrder = song.sortOrder,
    bpm = song.bpm,
    isNew = song.isNew,
    isLocked = song.isLocked,
    comment = song.comment,
    searchKeywords = song.searchKeywords,
    aliases = song.aliases,
    songId = song.songId,
    isFavorite = song.isFavorite,
    sheets = sheets.sortedWith(compareBy({ it.type }, { difficultyOrder(it.difficulty) })).map { it.asDomain() }
)

fun SheetEntity.asDomain(): Sheet = Sheet(
    sheetId = sheetId,
    songIdentifier = songIdentifier,
    type = type,
    difficulty = difficulty,
    level = level,
    levelValue = levelValue,
    internalLevel = internalLevel,
    internalLevelValue = internalLevelValue,
    noteDesigner = noteDesigner,
    tap = tap,
    hold = hold,
    slide = slide,
    touch = touch,
    breakCount = breakCount,
    total = total,
    regionJp = regionJp,
    regionIntl = regionIntl,
    regionUsa = regionUsa,
    regionCn = regionCn,
    songId = songId
)

fun ScoreEntity.asDomain(): Score = Score(
    scoreKey = scoreKey,
    sheetId = sheetId,
    userProfileId = userProfileId,
    rate = rate,
    rank = rank,
    achievementDate = achievementDate,
    dxScore = dxScore,
    fc = fc,
    fs = fs
)

fun PlayRecordEntity.asDomain(): PlayRecord = PlayRecord(
    id = id,
    sheetId = sheetId,
    userProfileId = userProfileId,
    rate = rate,
    rank = rank,
    playDate = playDate,
    dxScore = dxScore,
    fc = fc,
    fs = fs
)

fun UserProfileEntity.asDomain(): UserProfile = UserProfile(
    id = id,
    name = name,
    server = GameServer.fromValue(server),
    avatarUrl = avatarUrl,
    isActive = isActive,
    createdAt = createdAt,
    playerRating = playerRating,
    plate = plate,
    b35Count = b35Count,
    b15Count = b15Count,
    b35RecLimit = b35RecLimit,
    b15RecLimit = b15RecLimit
)

fun SyncConfigEntity.asDomain(): SyncConfig = SyncConfig(
    id = id,
    isAutoUploadEnabled = isAutoUploadEnabled,
    backgroundSyncInterval = backgroundSyncInterval,
    cloudBackupInterval = cloudBackupInterval,
    themeMode = ThemeMode.fromRawValue(themeRawValue),
    lastStaticDataUpdateDate = lastStaticDataUpdateDate,
    lastCloudBackupDate = lastCloudBackupDate,
    lastSyncRevision = lastSyncRevision,
    localDataOwnerUserId = localDataOwnerUserId,
    pendingResolutionForUserId = pendingResolutionForUserId,
    pendingResolutionDetectedAt = pendingResolutionDetectedAt
)

fun MaimaiIconEntity.asDomain(): MaimaiIcon = MaimaiIcon(
    id = id,
    name = name,
    descriptionText = descriptionText,
    genre = genre
)

fun UserProfile.asEntity(): UserProfileEntity = UserProfileEntity(
    id = id,
    name = name,
    server = server.value,
    avatarUrl = avatarUrl,
    isActive = isActive,
    createdAt = createdAt,
    playerRating = playerRating,
    plate = plate,
    b35Count = b35Count,
    b15Count = b15Count,
    b35RecLimit = b35RecLimit,
    b15RecLimit = b15RecLimit
)

fun SyncConfig.asEntity(): SyncConfigEntity = SyncConfigEntity(
    id = id,
    isAutoUploadEnabled = isAutoUploadEnabled,
    backgroundSyncInterval = backgroundSyncInterval,
    cloudBackupInterval = cloudBackupInterval,
    themeRawValue = themeMode.rawValue,
    lastStaticDataUpdateDate = lastStaticDataUpdateDate,
    lastCloudBackupDate = lastCloudBackupDate,
    lastSyncRevision = lastSyncRevision,
    localDataOwnerUserId = localDataOwnerUserId,
    pendingResolutionForUserId = pendingResolutionForUserId,
    pendingResolutionDetectedAt = pendingResolutionDetectedAt
)

fun difficultyOrder(difficulty: String): Int = when (difficulty.lowercase()) {
    "basic" -> 0
    "advanced" -> 1
    "expert" -> 2
    "master" -> 3
    "remaster" -> 4
    else -> 5
}
