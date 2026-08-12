package org.rhythmeta.maimaid.core.data

import androidx.room.withTransaction
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import org.rhythmeta.maimaid.core.database.MaimaidDatabase
import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity
import org.rhythmeta.maimaid.core.network.BackendApiClient

enum class BackendAccountResolution {
    Merge,
    KeepLocal,
    UseCloud,
}

data class BackendAccountConflict(
    val ownerUserId: String,
    val currentUserId: String,
)

class BackendProfileConflictException(val profileIds: List<String>) : Exception("Cloud profile is newer.")

class BackendSyncCoordinator(
    private val database: MaimaidDatabase,
    private val profileRepository: ProfileRepository,
    private val sessionManager: BackendSessionManager,
    private val apiClient: BackendApiClient,
    private val syncStateStore: BackendSyncStateStore,
    private val profileAvatarStore: ProfileAvatarStore,
    private val json: Json,
) {
    private val syncMutex = Mutex()

    suspend fun accountConflict(currentUserId: String): BackendAccountConflict? {
        val state = syncStateStore.load()
        val owner = state.ownerUserId
        if (owner == null) {
            if (hasLocalData()) syncStateStore.update { it.copy(ownerUserId = currentUserId) }
            return null
        }
        if (owner == currentUserId || !hasLocalData()) return null
        return BackendAccountConflict(ownerUserId = owner, currentUserId = currentUserId)
    }

    suspend fun resolveAccountConflict(resolution: BackendAccountResolution) = syncMutex.withLock {
        val user = sessionManager.state.value.user ?: error("Authentication required.")
        when (resolution) {
            BackendAccountResolution.Merge -> {
                val remoteSnapshot = fetchFullSnapshot()
                remapLocalProfiles()
                applySnapshot(
                    snapshot = remoteSnapshot.snapshot,
                    replace = false,
                    preserveLocalProfileIds = emptySet(),
                )
                syncStateStore.update { current ->
                    current.copy(
                        lastRevision = remoteSnapshot.latestRevision,
                        remoteUpdatedAtByProfile = remoteSnapshot.snapshot.profiles
                            .mapNotNull { profile -> profile.updatedAt?.let { profile.id to it } }
                            .toMap(),
                    )
                }
                pushAll(forceProfiles = true, overwriteProfileMetadata = true)
                pullAndApply(sinceRevision = "0", replace = false)
            }
            BackendAccountResolution.KeepLocal -> {
                remapLocalProfiles()
                clearRemoteProfiles()
                pushAll(forceProfiles = true)
                pullAndApply(sinceRevision = "0", replace = false)
            }
            BackendAccountResolution.UseCloud -> {
                val remoteSnapshot = fetchFullSnapshot()
                clearLocalUserData(createDefault = false)
                applySnapshot(remoteSnapshot.snapshot, replace = true, preserveLocalProfileIds = emptySet())
                syncStateStore.update { current ->
                    current.copy(
                        lastRevision = remoteSnapshot.latestRevision,
                        remoteUpdatedAtByProfile = remoteSnapshot.snapshot.profiles
                            .mapNotNull { profile -> profile.updatedAt?.let { profile.id to it } }
                            .toMap(),
                    )
                }
            }
        }
        profileRepository.ensureDefaultProfile()
        recordSyncedState(user.id)
    }

    suspend fun backup() = syncMutex.withLock {
        val user = sessionManager.state.value.user ?: error("Authentication required.")
        val state = syncStateStore.load()
        state.ownerUserId?.takeIf { it != user.id && hasLocalData() }?.let {
            error("Account data conflict requires resolution.")
        }
        pullAndApply(sinceRevision = "0", replace = false)
        pushAll(forceProfiles = false)
        pullAndApply(sinceRevision = syncStateStore.load().lastRevision, replace = false)
        recordSyncedState(user.id)
    }

    suspend fun restore() = syncMutex.withLock {
        val user = sessionManager.state.value.user ?: error("Authentication required.")
        val state = syncStateStore.load()
        state.ownerUserId?.takeIf { it != user.id && hasLocalData() }?.let {
            error("Account data conflict requires resolution.")
        }
        if (state.lastRevision == "0") {
            val response = fetchFullSnapshot()
            applySnapshot(response.snapshot, replace = false, preserveLocalProfileIds = emptySet())
            syncStateStore.update { current ->
                current.copy(
                    lastRevision = response.latestRevision,
                    remoteUpdatedAtByProfile = response.snapshot.profiles
                        .mapNotNull { profile -> profile.updatedAt?.let { profile.id to it } }
                        .toMap(),
                )
            }
        } else {
            pullAndApply(sinceRevision = "0", replace = false)
        }
        recordSyncedState(user.id)
    }

    suspend fun resolveProfileConflict(resolution: BackendAccountResolution) = syncMutex.withLock {
        val user = sessionManager.state.value.user ?: error("Authentication required.")
        when (resolution) {
            BackendAccountResolution.KeepLocal -> {
                pushAll(forceProfiles = true, overwriteProfileMetadata = true)
                pullAndApply(sinceRevision = syncStateStore.load().lastRevision, replace = false)
            }
            BackendAccountResolution.Merge -> {
                val profiles = database.profileDao().profiles()
                syncStateStore.update { current ->
                    current.copy(
                        syncedFingerprintByProfile = current.syncedFingerprintByProfile + profiles.associate { profile ->
                            profile.id to BackendSyncStateStore.profileFingerprint(profile)
                        },
                    )
                }
                pullAndApply(sinceRevision = "0", replace = false)
                pushAll(forceProfiles = false)
            }
            BackendAccountResolution.UseCloud -> {
                val profiles = database.profileDao().profiles()
                syncStateStore.update { current ->
                    current.copy(
                        syncedFingerprintByProfile = current.syncedFingerprintByProfile + profiles.associate { profile ->
                            profile.id to BackendSyncStateStore.profileFingerprint(profile)
                        },
                    )
                }
                pullAndApply(sinceRevision = "0", replace = false)
            }
        }
        recordSyncedState(user.id)
    }

    suspend fun clearLocalUserData() {
        clearLocalUserData(createDefault = true)
    }

    private suspend fun clearLocalUserData(createDefault: Boolean) {
        val paths = database.profileDao().profiles().mapNotNull(UserProfileEntity::avatarPath)
        database.withTransaction { database.profileDao().deleteAll() }
        paths.forEach(profileAvatarStore::deleteStored)
        syncStateStore.clearSessionState(keepOwner = false)
        if (createDefault) profileRepository.ensureDefaultProfile()
    }

    suspend fun onLogout(clearLocalData: Boolean) {
        if (clearLocalData) clearLocalUserData() else syncStateStore.clearSessionState(keepOwner = true)
    }

    private suspend fun pullAndApply(sinceRevision: String, replace: Boolean) {
        val response = if (sinceRevision == "0") {
            fetchFullSnapshot()
        } else {
            val responseElement = sessionManager.authorizedRequest(
                path = "v1/sync:pull?sinceRevision=$sinceRevision&includeSnapshot=true&limit=500",
            )
            json.decodeFromJsonElement(BackendSyncPullResponse.serializer(), responseElement)
        }
        val syncState = syncStateStore.load()
        val localProfiles = database.profileDao().profiles().associateBy(UserProfileEntity::id)
        val profileConflicts = mutableSetOf<String>()
        val preserveLocalProfileIds = response.snapshot.profiles.mapNotNullTo(mutableSetOf()) { remote ->
            val local = localProfiles[remote.id] ?: return@mapNotNullTo null
            val previousFingerprint = syncState.syncedFingerprintByProfile[remote.id]
                ?: return@mapNotNullTo remote.id
            val localChanged = BackendSyncStateStore.profileFingerprint(local) != previousFingerprint
            val remoteChanged = remote.updatedAt != syncState.remoteUpdatedAtByProfile[remote.id]
            if (localChanged && remoteChanged) {
                profileConflicts += remote.id
                return@mapNotNullTo remote.id
            }
            remote.id.takeIf { localChanged }
        }
        if (profileConflicts.isNotEmpty()) throw BackendProfileConflictException(profileConflicts.toList())
        applySnapshot(response.snapshot, replace, preserveLocalProfileIds)
        val deletedProfileIds = response.events
            .filter { it.entityType == "profile" && it.op == "delete" }
            .mapTo(mutableSetOf()) { it.profileId ?: it.entityId }
        if (deletedProfileIds.isNotEmpty()) {
            database.withTransaction {
                val profileDao = database.profileDao()
                for (profile in profileDao.profiles()) {
                    if (profile.id in deletedProfileIds) profileDao.delete(profile)
                }
            }
        }
        syncStateStore.update { current ->
            current.copy(
                lastRevision = response.latestRevision,
                remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + response.snapshot.profiles
                    .mapNotNull { profile -> profile.updatedAt?.let { profile.id to it } },
            )
        }
    }

    private suspend fun fetchFullSnapshot(): BackendSyncPullResponse {
        val responseElement = sessionManager.authorizedRequest(
            path = "v1/sync:pull?sinceRevision=0&includeSnapshot=true&limit=500",
        )
        val pullResponse = json.decodeFromJsonElement(BackendSyncPullResponse.serializer(), responseElement)
        val profilesElement = sessionManager.authorizedRequest("v1/profiles")
        val profiles = json.decodeFromJsonElement(ProfilesResponse.serializer(), profilesElement).profiles
        val scores = mutableListOf<BackendRemoteScore>()
        val records = mutableListOf<BackendRemotePlayRecord>()
        profiles.forEach { profile ->
            val scoreElement = sessionManager.authorizedRequest("v1/scores?profileId=${profile.id}")
            scores += json.decodeFromJsonElement(ScoresResponse.serializer(), scoreElement).scores
            val recordElement = sessionManager.authorizedRequest("v1/play-records?profileId=${profile.id}&limit=5000")
            records += json.decodeFromJsonElement(RecordsResponse.serializer(), recordElement).records
        }
        return pullResponse.copy(snapshot = BackendSyncSnapshot(profiles, scores, records))
    }

    private suspend fun applySnapshot(
        snapshot: BackendSyncSnapshot,
        replace: Boolean,
        preserveLocalProfileIds: Set<String>,
    ) {
        val oldAvatarPaths = mutableListOf<String>()
        database.withTransaction {
            val profileDao = database.profileDao()
            val scoreDao = database.scoreDao()
            val localProfiles = profileDao.profiles().associateBy(UserProfileEntity::id)
            if (replace) profileDao.deleteAll()

            snapshot.profiles.forEach { remote ->
                val existing = if (replace) null else localProfiles[remote.id]
                if (remote.id in preserveLocalProfileIds) return@forEach
                if (remote.avatarUrl != existing?.avatarUrl) existing?.avatarPath?.let(oldAvatarPaths::add)
                profileDao.upsert(remote.toEntity(existing))
            }

            val allProfiles = profileDao.profiles()
            val activeProfileId = snapshot.profiles.firstOrNull(BackendRemoteProfile::isActive)?.id
                ?: allProfiles.firstOrNull(UserProfileEntity::isActive)?.id
                ?: allProfiles.firstOrNull()?.id
            allProfiles.forEach { profile ->
                val shouldBeActive = profile.id == activeProfileId
                if (profile.isActive != shouldBeActive) profileDao.upsert(profile.copy(isActive = shouldBeActive))
            }

            val validProfileIds = (profileDao.profiles().map(UserProfileEntity::id)).toSet()
            val sheets = database.catalogDao().sheets()
            val sheetMap = buildSheetMap(sheets)
            if (replace) {
                validProfileIds.forEach { profileId ->
                    scoreDao.deleteScores(profileId)
                    scoreDao.deletePlayRecords(profileId)
                }
            }

            snapshot.scores.forEach { remote ->
                val sheetKey = remote.sheet?.let { resolveSheetKey(it, sheetMap) } ?: return@forEach
                if (remote.profileId !in validProfileIds) return@forEach
                val incoming = ScoreEntity(
                    profileId = remote.profileId,
                    sheetKey = sheetKey,
                    achievement = remote.achievements,
                    rank = remote.rank,
                    dxScore = remote.dxScore,
                    fc = ScoreRules.canonicalFc(remote.fc),
                    fs = ScoreRules.canonicalFs(remote.fs),
                    achievedAt = parseTime(remote.achievedAt),
                )
                val merged = if (replace) incoming else mergeScores(scoreDao.score(remote.profileId, sheetKey), incoming)
                scoreDao.upsertScore(merged)
            }

            snapshot.records.forEach { remote ->
                val sheetKey = remote.sheet?.let { resolveSheetKey(it, sheetMap) } ?: return@forEach
                if (remote.profileId !in validProfileIds) return@forEach
                val playedAt = parseTime(remote.playTime)
                val incoming = PlayRecordEntity(
                    id = stableRecordId(remote.profileId, sheetKey, remote, playedAt),
                    profileId = remote.profileId,
                    sheetKey = sheetKey,
                    achievement = remote.achievements,
                    rank = remote.rank,
                    dxScore = remote.dxScore,
                    fc = ScoreRules.canonicalFc(remote.fc),
                    fs = ScoreRules.canonicalFs(remote.fs),
                    playedAt = playedAt,
                )
                val duplicate = scoreDao.playRecords(remote.profileId, sheetKey).any { existing ->
                    recordFingerprint(existing) == recordFingerprint(incoming)
                }
                if (!duplicate) scoreDao.upsertPlayRecord(incoming)
            }
        }
        oldAvatarPaths.forEach(profileAvatarStore::deleteStored)
    }

    private suspend fun pushAll(
        forceProfiles: Boolean,
        overwriteProfileMetadata: Boolean = false,
    ) {
        val profiles = database.profileDao().profiles()
        if (profiles.isEmpty()) return
        val state = syncStateStore.load()
        val pendingMutation = state.pendingMutation ?: createPendingMutation(
            profiles = profiles,
            state = state,
            forceProfiles = forceProfiles,
            overwriteProfileMetadata = overwriteProfileMetadata,
        ).also { mutation ->
            syncStateStore.update { it.copy(pendingMutation = mutation) }
        }
        val payload = pendingMutation.payload
        val responseElement = sessionManager.authorizedRequest(
            path = "v1/sync:push",
            method = "POST",
            body = json.encodeToJsonElement(BackendSyncPushPayload.serializer(), payload),
        )
        val response = json.decodeFromJsonElement(BackendSyncPushResponse.serializer(), responseElement)
        val profileConflicts = response.conflicts.filter { it.reason == "server_newer" }.map { it.profileId }
        if (profileConflicts.isNotEmpty()) {
            syncStateStore.update { current ->
                current.copy(
                    pendingMutation = null,
                    remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + response.conflicts
                        .mapNotNull { conflict -> conflict.serverProfile?.updatedAt?.let { conflict.profileId to it } } +
                        response.profileVersions,
                )
            }
            throw BackendProfileConflictException(profileConflicts)
        }
        syncStateStore.update { current ->
                current.copy(
                    pendingMutation = null,
                    syncedFingerprintByProfile = current.syncedFingerprintByProfile +
                        pendingMutation.profileFingerprintById,
                    remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + response.profileVersions +
                        response.conflicts.mapNotNull { conflict ->
                            conflict.serverProfile?.updatedAt?.let { conflict.profileId to it }
                        },
                )
        }
        val remoteProfiles = fetchRemoteProfiles()
        syncStateStore.update { current ->
            current.copy(
                remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + remoteProfiles
                    .mapNotNull { profile -> profile.updatedAt?.let { profile.id to it } },
            )
        }
        if (uploadLocalAvatars()) pushAll(forceProfiles = false)
    }

    private suspend fun createPendingMutation(
        profiles: List<UserProfileEntity>,
        state: BackendSyncPersistentState,
        forceProfiles: Boolean,
        overwriteProfileMetadata: Boolean,
    ): BackendPendingSyncMutation {
        val sheets = database.catalogDao().sheets().associateBy(SheetEntity::sheetKey)
        val profileFingerprints = mutableMapOf<String, String>()
        val profileUpserts = profiles.mapNotNull profileMap@{ profile ->
            val fingerprint = BackendSyncStateStore.profileFingerprint(profile)
            val lastFingerprint = state.syncedFingerprintByProfile[profile.id]
            val localChanged = fingerprint != lastFingerprint
            if (
                !forceProfiles &&
                !localChanged &&
                state.remoteUpdatedAtByProfile[profile.id] != null
            ) return@profileMap null
            profileFingerprints[profile.id] = fingerprint
            profile.toUpsert(
                clientUpdatedAt = if (overwriteProfileMetadata) {
                    null
                } else {
                    state.remoteUpdatedAtByProfile[profile.id]
                },
            )
        }
        val payload = BackendSyncPushPayload(
            idempotencyKey = UUID.randomUUID().toString(),
            forceProfileOverwrite = overwriteProfileMetadata,
            profileUpserts = profileUpserts,
            scoreUpserts = profiles.mapNotNull scoreSetMap@{ profile ->
                database.scoreDao().scores(profile.id).mapNotNull scoreMap@{ score ->
                    val sheet = sheets[score.sheetKey] ?: return@scoreMap null
                    score.toEntry(sheet)
                }.takeIf(List<BackendScoreEntry>::isNotEmpty)?.let { BackendScoreSet(profile.id, it) }
            },
            playRecordUpserts = profiles.mapNotNull recordSetMap@{ profile ->
                database.scoreDao().playRecords(profile.id).mapNotNull recordMap@{ record ->
                    val sheet = sheets[record.sheetKey] ?: return@recordMap null
                    record.toEntry(sheet)
                }.takeIf(List<BackendPlayRecordEntry>::isNotEmpty)?.let { BackendRecordSet(profile.id, it) }
            },
        )
        return BackendPendingSyncMutation(payload, profileFingerprints)
    }

    private suspend fun uploadLocalAvatars(): Boolean {
        val profileDao = database.profileDao()
        var uploadedAny = false
        profileDao.profiles().forEach { profile ->
            val file = profile.avatarPath?.let(::File)?.takeIf(File::isFile) ?: return@forEach
            if (profile.avatarUrl != null) return@forEach
            val contentType = if (file.extension.equals("png", true)) "image/png" else "image/jpeg"
            val expectedVersion = syncStateStore.load().remoteUpdatedAtByProfile[profile.id]
            val response = sessionManager.authorizedRequest(
                path = "v1/profiles/${profile.id}/avatar:createUploadUrl",
                method = "POST",
                body = json.encodeToJsonElement(
                    AvatarUploadRequest.serializer(),
                    AvatarUploadRequest(contentType, expectedVersion),
                ),
            )
            val upload = json.decodeFromJsonElement(AvatarUploadResponse.serializer(), response)
            syncStateStore.update { current ->
                current.copy(
                    remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + (profile.id to upload.updatedAt),
                )
            }
            apiClient.upload(upload.uploadUrl, contentType, withContext(Dispatchers.IO) { file.readBytes() })
            val avatarUrl = apiClient.endpoint("v1/profiles/${profile.id}/avatar") +
                "?v=" + java.net.URLEncoder.encode(upload.updatedAt, Charsets.UTF_8.name())
            val updated = profile.copy(avatarUrl = avatarUrl)
            profileDao.upsert(updated)
            uploadedAny = true
        }
        return uploadedAny
    }

    private suspend fun clearRemoteProfiles() {
        val profiles = fetchRemoteProfiles()
        profiles.filter(BackendRemoteProfile::isActive).forEach { profile ->
            sessionManager.authorizedRequest(
                path = "v1/profiles/${profile.id}",
                method = "PATCH",
                body = kotlinx.serialization.json.buildJsonObject { put("isActive", false) },
            )
        }
        profiles.forEach { profile ->
            sessionManager.authorizedRequest(path = "v1/profiles/${profile.id}", method = "DELETE")
        }
    }

    private suspend fun fetchRemoteProfiles(): List<BackendRemoteProfile> {
        val response = sessionManager.authorizedRequest("v1/profiles")
        return json.decodeFromJsonElement(ProfilesResponse.serializer(), response).profiles
    }

    private suspend fun remapLocalProfiles() {
        val oldProfiles = database.profileDao().profiles()
        if (oldProfiles.isEmpty()) return
        val avatarMoves = mutableListOf<Pair<String, String>>()
        database.withTransaction {
            val profileDao = database.profileDao()
            val scoreDao = database.scoreDao()
            oldProfiles.forEach { old ->
                val newId = UUID.randomUUID().toString()
                profileDao.upsert(old.copy(id = newId, avatarUrl = null))
                old.avatarPath?.let { avatarMoves += newId to it }
                scoreDao.scores(old.id).forEach { scoreDao.upsertScore(it.copy(profileId = newId)) }
                scoreDao.playRecords(old.id).forEach { record ->
                    scoreDao.upsertPlayRecord(record.copy(id = UUID.randomUUID().toString(), profileId = newId))
                }
                profileDao.delete(old)
            }
        }
        avatarMoves.forEach { (profileId, oldPath) ->
            val file = File(oldPath)
            if (file.isFile) {
                val newPath = profileAvatarStore.saveRemote(
                    withContext(Dispatchers.IO) { file.readBytes() },
                    profileId,
                )
                database.profileDao().profiles().firstOrNull { it.id == profileId }?.let { profile ->
                    database.profileDao().upsert(profile.copy(avatarPath = newPath))
                }
                profileAvatarStore.deleteStored(oldPath)
            }
        }
        syncStateStore.update { it.copy(lastRevision = "0", remoteUpdatedAtByProfile = emptyMap(), syncedFingerprintByProfile = emptyMap()) }
    }

    private suspend fun recordSyncedState(userId: String) {
        val profiles = database.profileDao().profiles()
        syncStateStore.update { current ->
            current.copy(
                ownerUserId = userId,
                syncedFingerprintByProfile = profiles.associate { profile ->
                    profile.id to BackendSyncStateStore.profileFingerprint(profile)
                },
            )
        }
    }

    private suspend fun hasLocalData(): Boolean = database.profileDao().profiles().isNotEmpty()

    private fun BackendRemoteProfile.toEntity(existing: UserProfileEntity?): UserProfileEntity = UserProfileEntity(
        id = id,
        name = name,
        server = server,
        avatarPath = existing?.avatarPath?.takeIf { avatarUrl == existing.avatarUrl },
        avatarUrl = avatarUrl,
        isActive = isActive,
        createdAt = parseTime(createdAt),
        dfUsername = dfUsername,
        playerRating = playerRating,
        plate = plate,
        lastImportDateDf = lastImportDateDf?.let(::parseTime),
        lastImportDateLxns = lastImportDateLxns?.let(::parseTime),
        b35Count = b35Count,
        b15Count = b15Count,
        b35RecLimit = b35RecLimit,
        b15RecLimit = b15RecLimit,
    )

    private fun UserProfileEntity.toUpsert(clientUpdatedAt: String?) = BackendProfileUpsert(
        profileId = id,
        name = name.ifBlank { "Player" },
        server = server,
        isActive = isActive,
        playerRating = playerRating,
        plate = plate,
        avatarUrl = avatarUrl,
        dfUsername = dfUsername,
        b35Count = b35Count,
        b15Count = b15Count,
        b35RecLimit = b35RecLimit,
        b15RecLimit = b15RecLimit,
        createdAt = formatTime(createdAt),
        clientUpdatedAt = clientUpdatedAt,
    )

    private fun ScoreEntity.toEntry(sheet: SheetEntity) = BackendScoreEntry(
        songIdentifier = sheet.songIdentifier,
        songId = sheet.providerSongId.takeIf { it > 0 },
        type = canonicalType(sheet.type),
        difficulty = canonicalDifficulty(sheet.difficulty),
        achievements = achievement,
        rank = rank,
        dxScore = dxScore,
        fc = fc,
        fs = fs,
        achievedAt = formatTime(achievedAt),
    )

    private fun PlayRecordEntity.toEntry(sheet: SheetEntity) = BackendPlayRecordEntry(
        songIdentifier = sheet.songIdentifier,
        songId = sheet.providerSongId.takeIf { it > 0 },
        type = canonicalType(sheet.type),
        difficulty = canonicalDifficulty(sheet.difficulty),
        achievements = achievement,
        rank = rank,
        dxScore = dxScore,
        fc = fc,
        fs = fs,
        playTime = formatTime(playedAt),
    )

    private fun mergeScores(existing: ScoreEntity?, incoming: ScoreEntity): ScoreEntity {
        if (existing == null) return incoming
        return ScoreRules.mergeScore(
            profileId = incoming.profileId,
            sheetKey = incoming.sheetKey,
            existing = existing,
            input = ScoreInput(incoming.achievement, incoming.dxScore, incoming.fc, incoming.fs),
            now = incoming.achievedAt,
        )
    }

    private fun buildSheetMap(sheets: List<SheetEntity>): Map<String, String> = buildMap {
        sheets.forEach { sheet ->
            val type = canonicalType(sheet.type)
            val difficulty = canonicalDifficulty(sheet.difficulty)
            listOf(sheet.songIdentifier, sheet.providerSongId.takeIf { it > 0 }?.toString()).filterNotNull().forEach { id ->
                put("${id.lowercase()}|$type|$difficulty", sheet.sheetKey)
            }
        }
    }

    private fun resolveSheetKey(remote: BackendRemoteSheet, sheetMap: Map<String, String>): String? {
        val ids = listOf(remote.songIdentifier, remote.songId.takeIf { it > 0 }?.toString()).filterNotNull()
        val type = canonicalType(remote.chartType)
        val difficulty = canonicalDifficulty(remote.difficulty)
        return ids.firstNotNullOfOrNull { id -> sheetMap["${id.lowercase()}|$type|$difficulty"] }
    }

    private fun stableRecordId(profileId: String, sheetKey: String, remote: BackendRemotePlayRecord, playedAt: Long): String =
        UUID.nameUUIDFromBytes(
            listOf(profileId, sheetKey, playedAt, remote.achievements, remote.dxScore, remote.fc, remote.fs)
                .joinToString("|")
                .toByteArray(Charsets.UTF_8),
        ).toString()

    private fun recordFingerprint(record: PlayRecordEntity): String = listOf(
        record.profileId,
        record.sheetKey,
        record.playedAt,
        "%.4f".format(java.util.Locale.ROOT, record.achievement),
        record.dxScore,
        ScoreRules.canonicalFc(record.fc),
        ScoreRules.canonicalFs(record.fs),
    ).joinToString("|")

    private fun canonicalType(value: String): String = when (value.trim().lowercase()) {
        "standard", "std", "sd" -> "std"
        else -> value.trim().lowercase()
    }

    private fun canonicalDifficulty(value: String): String = value.trim().lowercase()
        .replace(" ", "")
        .replace("_", "")
        .replace(":", "")

    private fun parseTime(value: String): Long = runCatching { Instant.parse(value).toEpochMilli() }.getOrDefault(0L)
    private fun formatTime(value: Long): String = Instant.ofEpochMilli(value).toString()

    @Serializable
    private data class AvatarUploadRequest(val contentType: String, val clientUpdatedAt: String?)

    @Serializable
    private data class AvatarUploadResponse(val key: String, val uploadUrl: String, val updatedAt: String)

    @Serializable
    private data class ProfilesResponse(val profiles: List<BackendRemoteProfile>)

    @Serializable
    private data class ScoresResponse(val scores: List<BackendRemoteScore>)

    @Serializable
    private data class RecordsResponse(val records: List<BackendRemotePlayRecord>)
}
