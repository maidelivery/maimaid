package org.rhythmeta.maimaid.core.data

import androidx.room.withTransaction
import java.io.File
import java.time.Instant
import java.util.UUID
import kotlin.math.roundToLong
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

private const val SyncBatchSize = 250

enum class BackendAccountResolution {
    Merge,
    KeepLocal,
    UseCloud,
}

enum class ImportSyncResolution {
    MergeBest,
    KeepLocal,
    UseImport,
}

data class ImportSyncConflictPreview(
    val profileId: String,
    val baseRevision: String,
    val latestRevision: String,
    val localOnlyCount: Int,
    val differentCount: Int,
    val snapshot: BackendSyncSnapshot,
) {
    val conflictCount: Int get() = localOnlyCount + differentCount
}

data class BackendAccountConflict(
    val ownerUserId: String,
    val currentUserId: String,
)

class BackendProfileConflictException(val profileIds: List<String>) : Exception("Cloud profile is newer.")

data class BackendCloudRestorePreview(
    val localOnlyProfiles: List<UserProfileEntity>,
)

internal fun localProfilesAbsentFromCloud(
    localProfiles: List<UserProfileEntity>,
    cloudProfileIds: Set<String>,
): List<UserProfileEntity> = localProfiles.filter { it.id !in cloudProfileIds }

internal fun legacyDivingFishPlayRecords(
    records: List<PlayRecordEntity>,
    minimumBatchSize: Int = 100,
): List<PlayRecordEntity> {
    val legacyTimestamps = records.groupingBy(PlayRecordEntity::playedAt)
        .eachCount()
        .filterValues { it >= minimumBatchSize }
        .keys
    return records.filter { it.playedAt in legacyTimestamps }
}

class BackendSyncCoordinator(
    private val database: MaimaidDatabase,
    private val profileRepository: ProfileRepository,
    private val sessionManager: BackendSessionManager,
    private val apiClient: BackendApiClient,
    private val syncStateStore: BackendSyncStateStore,
    private val profileAvatarStore: ProfileAvatarStore,
    private val profileCredentialStore: ProfileCredentialStore,
    private val json: Json,
) {
    private val syncMutex = Mutex()

    suspend fun ensureProfileExists(profile: UserProfileEntity) = syncMutex.withLock {
        sessionManager.state.value.user ?: error("Authentication required.")
        sessionManager.authorizedRequest(
            path = "v1/profiles/${profile.id}",
            method = "PUT",
            body = json.encodeToJsonElement(
                BackendProfileUpsert.serializer(),
                profile.toUpsert(clientUpdatedAt = null),
            ),
        )
    }

    /** Mirrors iOS's score-save push without rewriting profile metadata. */
    suspend fun pushScoreUpdate(sheetKey: String, score: ScoreEntity) = syncMutex.withLock {
        if (!sessionManager.state.value.isAuthenticated) return@withLock
        val sheet = database.catalogDao().sheet(sheetKey) ?: return@withLock
        val scoreEntry = score.toEntry(sheet)
        val payload = BackendSyncPushPayload(
            idempotencyKey = UUID.randomUUID().toString(),
            profileUpserts = emptyList(),
            scoreUpserts = listOf(BackendScoreSet(score.profileId, listOf(scoreEntry))),
            playRecordUpserts = listOf(
                BackendRecordSet(
                    profileId = score.profileId,
                    records = listOf(
                        BackendPlayRecordEntry(
                            songIdentifier = scoreEntry.songIdentifier,
                            songId = scoreEntry.songId,
                            type = scoreEntry.type,
                            difficulty = scoreEntry.difficulty,
                            achievements = scoreEntry.achievements,
                            rank = scoreEntry.rank,
                            dxScore = scoreEntry.dxScore,
                            fc = scoreEntry.fc,
                            fs = scoreEntry.fs,
                            playTime = formatTime(System.currentTimeMillis()),
                        ),
                    ),
                ),
            ),
        )
        val responseElement = sessionManager.authorizedRequest(
            path = "v1/sync:push",
            method = "POST",
            body = json.encodeToJsonElement(BackendSyncPushPayload.serializer(), payload),
        )
        val response = json.decodeFromJsonElement(BackendSyncPushResponse.serializer(), responseElement)
        syncStateStore.update { current -> current.copy(lastRevision = response.latestRevision) }
    }

    suspend fun previewImportConflicts(profileId: String): ImportSyncConflictPreview = syncMutex.withLock {
        sessionManager.state.value.user ?: error("Authentication required.")
        val state = syncStateStore.load()
        val responseElement = sessionManager.authorizedRequest(
            path = "v1/sync:pull?sinceRevision=${state.lastRevision}&includeSnapshot=true&limit=500&profileId=$profileId",
        )
        val response = json.decodeFromJsonElement(BackendSyncPullResponse.serializer(), responseElement)
        val snapshot = response.snapshot.filtered(profileId)
        val sheetMap = buildSheetMap(database.catalogDao().sheets())
        val remoteBySheet = snapshot.scores.mapNotNull { remote ->
            val sheetKey = remote.sheet?.let { resolveSheetKey(it, sheetMap) } ?: return@mapNotNull null
            sheetKey to remote
        }.toMap()
        if (snapshot.scores.isNotEmpty() && remoteBySheet.isEmpty()) {
            error("Imported scores could not be mapped to the local catalog.")
        }
        val localBySheet = database.scoreDao().scores(profileId).associateBy(ScoreEntity::sheetKey)
        var localOnlyCount = 0
        var differentCount = 0
        (localBySheet.keys + remoteBySheet.keys).forEach { sheetKey ->
            val local = localBySheet[sheetKey]
            val remote = remoteBySheet[sheetKey]
            when {
                local != null && remote == null -> localOnlyCount += 1
                local != null && remote != null && !sameScoreValue(local, remote) -> differentCount += 1
            }
        }
        ImportSyncConflictPreview(
            profileId = profileId,
            baseRevision = state.lastRevision,
            latestRevision = response.latestRevision,
            localOnlyCount = localOnlyCount,
            differentCount = differentCount,
            snapshot = snapshot,
        )
    }

    suspend fun applyImportConflictResolution(
        resolution: ImportSyncResolution,
        preview: ImportSyncConflictPreview,
    ) = syncMutex.withLock {
        val user = sessionManager.state.value.user ?: error("Authentication required.")
        check(syncStateStore.load().lastRevision == preview.baseRevision) {
            "Cloud data changed. Check import conflicts again."
        }
        applyImportSnapshot(preview.snapshot, preview.profileId, resolution)
        val appliedProfile = database.profileDao().profiles().firstOrNull { it.id == preview.profileId }
        syncStateStore.update { current ->
            current.copy(
                lastRevision = preview.latestRevision,
                pendingMutation = null,
                remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + preview.snapshot.profiles
                    .mapNotNull { profile -> profile.updatedAt?.let { profile.id to it } },
                syncedFingerprintByProfile = if (appliedProfile == null) {
                    current.syncedFingerprintByProfile
                } else {
                    current.syncedFingerprintByProfile + (
                        appliedProfile.id to BackendSyncStateStore.profileFingerprint(appliedProfile)
                    )
                },
            )
        }
        if (resolution != ImportSyncResolution.UseImport) pushImportedProfileData(preview.profileId)
        recordSyncedState(user.id)
    }

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
        val localProfiles = database.profileDao().profiles()
        if (localProfiles.isEmpty()) return@withLock
        removeRemoteProfilesAbsentLocally(localProfiles.mapTo(mutableSetOf()) { it.id })
        syncStateStore.update { current -> current.copy(pendingMutation = null) }
        pushAll(forceProfiles = true, overwriteProfileMetadata = true)
        recordSyncedState(user.id)
    }

    suspend fun previewRestore() = syncMutex.withLock {
        sessionManager.state.value.user ?: error("Authentication required.")
        val cloudProfileIds = fetchRemoteProfiles().mapTo(mutableSetOf(), BackendRemoteProfile::id)
        BackendCloudRestorePreview(
            localOnlyProfiles = localProfilesAbsentFromCloud(
                localProfiles = database.profileDao().profiles(),
                cloudProfileIds = cloudProfileIds,
            ),
        )
    }

    suspend fun restore(removeLocalProfilesAbsentFromCloud: Boolean = false) = syncMutex.withLock {
        val user = sessionManager.state.value.user ?: error("Authentication required.")
        val state = syncStateStore.load()
        state.ownerUserId?.takeIf { it != user.id && hasLocalData() }?.let {
            error("Account data conflict requires resolution.")
        }
        if (state.lastRevision == "0") {
            val response = fetchFullSnapshot()
            applySnapshot(
                snapshot = response.snapshot,
                replace = false,
                preserveLocalProfileIds = emptySet(),
                removeLocalProfilesAbsentFromCloud = removeLocalProfilesAbsentFromCloud,
            )
            syncStateStore.update { current ->
                current.copy(
                    lastRevision = response.latestRevision,
                    remoteUpdatedAtByProfile = response.snapshot.profiles
                        .mapNotNull { profile -> profile.updatedAt?.let { profile.id to it } }
                        .toMap(),
                )
            }
        } else {
            pullAndApply(
                sinceRevision = "0",
                replace = false,
                removeLocalProfilesAbsentFromCloud = removeLocalProfilesAbsentFromCloud,
            )
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
        val profiles = database.profileDao().profiles()
        database.withTransaction { database.profileDao().deleteAll() }
        profiles.mapNotNull(UserProfileEntity::avatarPath).forEach(profileAvatarStore::deleteStored)
        profiles.forEach { profileCredentialStore.delete(it.id) }
        syncStateStore.clearSessionState(keepOwner = false)
        if (createDefault) profileRepository.ensureDefaultProfile()
    }

    suspend fun onLogout(clearLocalData: Boolean) {
        if (clearLocalData) clearLocalUserData() else syncStateStore.clearSessionState(keepOwner = true)
    }

    private suspend fun pullAndApply(
        sinceRevision: String,
        replace: Boolean,
        removeLocalProfilesAbsentFromCloud: Boolean = false,
    ) {
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
        applySnapshot(
            snapshot = response.snapshot,
            replace = replace,
            preserveLocalProfileIds = preserveLocalProfileIds,
            removeLocalProfilesAbsentFromCloud = removeLocalProfilesAbsentFromCloud,
        )
        if (removeLocalProfilesAbsentFromCloud) {
            val deletedProfileIds = response.events
                .filter { it.entityType == "profile" && it.op == "delete" }
                .mapTo(mutableSetOf()) { it.profileId ?: it.entityId }
            val deletedProfiles = database.profileDao().profiles()
                .filter { it.id in deletedProfileIds }
            if (deletedProfiles.isNotEmpty()) {
                database.withTransaction {
                    val profileDao = database.profileDao()
                    for (profile in deletedProfiles) {
                        profileDao.delete(profile)
                    }
                }
                deletedProfiles.mapNotNull(UserProfileEntity::avatarPath).forEach(profileAvatarStore::deleteStored)
                deletedProfiles.forEach { profileCredentialStore.delete(it.id) }
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
        removeLocalProfilesAbsentFromCloud: Boolean = false,
    ) {
        val oldAvatarPaths = mutableListOf<String>()
        val removedProfiles = mutableListOf<UserProfileEntity>()
        database.withTransaction {
            val profileDao = database.profileDao()
            val scoreDao = database.scoreDao()
            val localProfiles = profileDao.profiles().associateBy(UserProfileEntity::id)
            if (replace) profileDao.deleteAll()
            if (removeLocalProfilesAbsentFromCloud) {
                val cloudProfileIds = snapshot.profiles.mapTo(mutableSetOf(), BackendRemoteProfile::id)
                val localOnlyProfiles = localProfilesAbsentFromCloud(
                    localProfiles = localProfiles.values.toList(),
                    cloudProfileIds = cloudProfileIds,
                )
                localOnlyProfiles.forEach { profile ->
                    profileDao.delete(profile)
                    removedProfiles += profile
                }
            }

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

            val existingRecordFingerprintsByProfile = mutableMapOf<String, MutableSet<String>>()
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
                val existingFingerprints = existingRecordFingerprintsByProfile.getOrPut(remote.profileId) {
                    scoreDao.playRecords(remote.profileId).mapTo(mutableSetOf(), ::recordFingerprint)
                }
                val duplicate = !existingFingerprints.add(recordFingerprint(incoming))
                if (!duplicate) scoreDao.upsertPlayRecord(incoming)
            }
        }
        (oldAvatarPaths + removedProfiles.mapNotNull(UserProfileEntity::avatarPath))
            .distinct()
            .forEach(profileAvatarStore::deleteStored)
        removedProfiles.forEach { profileCredentialStore.delete(it.id) }
    }

    private suspend fun applyImportSnapshot(
        snapshot: BackendSyncSnapshot,
        profileId: String,
        resolution: ImportSyncResolution,
    ) {
        val oldAvatarPaths = mutableListOf<String>()
        database.withTransaction {
            val profileDao = database.profileDao()
            val scoreDao = database.scoreDao()
            snapshot.profiles.firstOrNull { it.id == profileId }?.let { remote ->
                val existing = profileDao.profiles().firstOrNull { it.id == profileId }
                if (remote.avatarUrl != existing?.avatarUrl) existing?.avatarPath?.let(oldAvatarPaths::add)
                profileDao.upsert(remote.toEntity(existing))
                if (remote.isActive) {
                    profileDao.profiles().forEach { profile ->
                        if (profile.isActive != (profile.id == profileId)) {
                            profileDao.upsert(profile.copy(isActive = profile.id == profileId))
                        }
                    }
                }
            }

            if (profileDao.profiles().none { it.id == profileId }) return@withTransaction
            val sheetMap = buildSheetMap(database.catalogDao().sheets())
            val localScores = scoreDao.scores(profileId).associateBy(ScoreEntity::sheetKey)
            val remoteScores = snapshot.scores.mapNotNull { remote ->
                val sheetKey = remote.sheet?.let { resolveSheetKey(it, sheetMap) } ?: return@mapNotNull null
                sheetKey to remote.toScoreEntity(sheetKey)
            }.toMap()
            if (snapshot.scores.isNotEmpty() && remoteScores.isEmpty()) {
                error("Imported scores could not be mapped to the local catalog.")
            }
            val resolvedScores = when (resolution) {
                ImportSyncResolution.UseImport -> remoteScores
                ImportSyncResolution.KeepLocal -> remoteScores + localScores
                ImportSyncResolution.MergeBest -> buildMap {
                    putAll(localScores)
                    remoteScores.forEach { (sheetKey, remote) ->
                        val local = get(sheetKey)
                        if (local == null || isScoreBetter(remote, local)) put(sheetKey, remote)
                    }
                }
            }
            scoreDao.deleteScores(profileId)
            resolvedScores.values.forEach { scoreDao.upsertScore(it) }

            val localRecords = scoreDao.playRecords(profileId).associateBy(::recordFingerprint)
            val remoteRecords = snapshot.records.mapNotNull { remote ->
                val sheetKey = remote.sheet?.let { resolveSheetKey(it, sheetMap) } ?: return@mapNotNull null
                val playedAt = parseTime(remote.playTime)
                val record = remote.toPlayRecordEntity(sheetKey, playedAt)
                recordFingerprint(record) to record
            }.toMap()
            if (snapshot.records.isNotEmpty() && remoteRecords.isEmpty()) {
                error("Imported play records could not be mapped to the local catalog.")
            }
            val resolvedRecords = when (resolution) {
                ImportSyncResolution.UseImport -> remoteRecords
                ImportSyncResolution.KeepLocal,
                ImportSyncResolution.MergeBest,
                -> localRecords + remoteRecords
            }
            scoreDao.deletePlayRecords(profileId)
            resolvedRecords.values.forEach { scoreDao.upsertPlayRecord(it) }
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
        val sheets = database.catalogDao().sheets().associateBy(SheetEntity::sheetKey)
        syncStateStore.update { it.copy(pendingMutation = null) }

        for (profile in profiles) {
            val localRecords = database.scoreDao().playRecords(profile.id)
            val legacyRecords = legacyDivingFishPlayRecords(localRecords)
            val legacyIds = legacyRecords.mapTo(mutableSetOf(), PlayRecordEntity::id)
            if (legacyRecords.isNotEmpty()) database.scoreDao().deletePlayRecords(legacyRecords)
            val records = localRecords.filterNot { it.id in legacyIds }
            val scores = database.scoreDao().scores(profile.id)
            val profileFingerprint = BackendSyncStateStore.profileFingerprint(profile)
            val profileChanged = profileFingerprint != state.syncedFingerprintByProfile[profile.id]
            val profileUpsert = if (
                forceProfiles || profileChanged || state.remoteUpdatedAtByProfile[profile.id] == null
            ) {
                listOf(
                    profile.toUpsert(
                        clientUpdatedAt = if (overwriteProfileMetadata) null else state.remoteUpdatedAtByProfile[profile.id],
                    ),
                )
            } else {
                emptyList()
            }
            val batchCount = maxOf(
                1,
                (scores.size + SyncBatchSize - 1) / SyncBatchSize,
                (records.size + SyncBatchSize - 1) / SyncBatchSize,
            )

            repeat(batchCount) { batchIndex ->
                val batchStart = batchIndex * SyncBatchSize
                val scoreEntries = if (batchStart < scores.size) {
                    scores.subList(batchStart, minOf(batchStart + SyncBatchSize, scores.size))
                        .mapNotNull { score -> sheets[score.sheetKey]?.let { score.toEntry(it) } }
                } else {
                    emptyList()
                }
                val recordEntries = if (batchStart < records.size) {
                    records.subList(batchStart, minOf(batchStart + SyncBatchSize, records.size))
                        .mapNotNull { record -> sheets[record.sheetKey]?.let { record.toEntry(it) } }
                } else {
                    emptyList()
                }
                val payload = BackendSyncPushPayload(
                    idempotencyKey = UUID.randomUUID().toString(),
                    forceProfileOverwrite = overwriteProfileMetadata,
                    profileUpserts = profileUpsert.takeIf { batchIndex == 0 }.orEmpty(),
                    scoreUpserts = scoreEntries.takeIf(List<BackendScoreEntry>::isNotEmpty)
                        ?.let { listOf(BackendScoreSet(profile.id, it)) }.orEmpty(),
                    playRecordUpserts = recordEntries.takeIf(List<BackendPlayRecordEntry>::isNotEmpty)
                        ?.let { listOf(BackendRecordSet(profile.id, it)) }.orEmpty(),
                )
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
                            remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + response.profileVersions,
                        )
                    }
                    throw BackendProfileConflictException(profileConflicts)
                }
                syncStateStore.update { current ->
                    current.copy(
                        pendingMutation = null,
                        lastRevision = response.latestRevision,
                        remoteUpdatedAtByProfile = current.remoteUpdatedAtByProfile + response.profileVersions,
                    )
                }
            }
            syncStateStore.update { current ->
                current.copy(syncedFingerprintByProfile = current.syncedFingerprintByProfile +
                    (profile.id to profileFingerprint))
            }
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

    private suspend fun pushImportedProfileData(profileId: String) {
        val sheets = database.catalogDao().sheets().associateBy(SheetEntity::sheetKey)
        val scores = database.scoreDao().scores(profileId).mapNotNull { score ->
            sheets[score.sheetKey]?.let { sheet -> score.toEntry(sheet) }
        }
        val records = database.scoreDao().playRecords(profileId).mapNotNull { record ->
            sheets[record.sheetKey]?.let { sheet -> record.toEntry(sheet) }
        }
        if (scores.isEmpty() && records.isEmpty()) return

        val payload = BackendSyncPushPayload(
            idempotencyKey = UUID.randomUUID().toString(),
            profileUpserts = emptyList(),
            scoreUpserts = scores.takeIf(List<BackendScoreEntry>::isNotEmpty)
                ?.let { listOf(BackendScoreSet(profileId, it)) }
                .orEmpty(),
            playRecordUpserts = records.takeIf(List<BackendPlayRecordEntry>::isNotEmpty)
                ?.let { listOf(BackendRecordSet(profileId, it)) }
                .orEmpty(),
        )
        val responseElement = sessionManager.authorizedRequest(
            path = "v1/sync:push",
            method = "POST",
            body = json.encodeToJsonElement(BackendSyncPushPayload.serializer(), payload),
        )
        val response = json.decodeFromJsonElement(BackendSyncPushResponse.serializer(), responseElement)
        syncStateStore.update { current ->
            current.copy(
                lastRevision = response.latestRevision,
                pendingMutation = null,
            )
        }
    }

    private suspend fun uploadLocalAvatars(): Boolean {
        val profileDao = database.profileDao()
        var uploadedAny = false
        profileDao.profiles().forEach { profile ->
            val file = profile.avatarPath?.let(::File)?.takeIf(File::isFile) ?: return@forEach
            if (PresetAvatarUrl.isPreset(profile.avatarUrl)) return@forEach
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

    private suspend fun removeRemoteProfilesAbsentLocally(localProfileIds: Set<String>) {
        val profilesToDelete = fetchRemoteProfiles().filter { it.id !in localProfileIds }
        profilesToDelete.filter(BackendRemoteProfile::isActive).forEach { profile ->
            sessionManager.authorizedRequest(
                path = "v1/profiles/${profile.id}",
                method = "PATCH",
                body = kotlinx.serialization.json.buildJsonObject { put("isActive", false) },
            )
        }
        profilesToDelete.forEach { profile ->
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

    private fun BackendSyncSnapshot.filtered(profileId: String) = BackendSyncSnapshot(
        profiles = profiles.filter { it.id == profileId },
        scores = scores.filter { it.profileId == profileId },
        records = records.filter { it.profileId == profileId },
    )

    private fun BackendRemoteScore.toScoreEntity(sheetKey: String) = ScoreEntity(
        profileId = profileId,
        sheetKey = sheetKey,
        achievement = achievements,
        rank = rank,
        dxScore = dxScore,
        fc = ScoreRules.canonicalFc(fc),
        fs = ScoreRules.canonicalFs(fs),
        achievedAt = parseTime(achievedAt),
    )

    private fun BackendRemotePlayRecord.toPlayRecordEntity(sheetKey: String, playedAt: Long) = PlayRecordEntity(
        id = stableRecordId(profileId, sheetKey, this, playedAt),
        profileId = profileId,
        sheetKey = sheetKey,
        achievement = achievements,
        rank = rank,
        dxScore = dxScore,
        fc = ScoreRules.canonicalFc(fc),
        fs = ScoreRules.canonicalFs(fs),
        playedAt = playedAt,
    )

    private fun sameScoreValue(local: ScoreEntity, remote: BackendRemoteScore): Boolean =
        (local.achievement * 10_000).roundToLong() == (remote.achievements * 10_000).roundToLong() &&
            local.rank.trim().lowercase() == remote.rank.trim().lowercase() &&
            local.dxScore == remote.dxScore &&
            ScoreRules.canonicalFc(local.fc) == ScoreRules.canonicalFc(remote.fc) &&
            ScoreRules.canonicalFs(local.fs) == ScoreRules.canonicalFs(remote.fs)

    private fun isScoreBetter(candidate: ScoreEntity, current: ScoreEntity): Boolean = when {
        candidate.achievement != current.achievement -> candidate.achievement > current.achievement
        candidate.achievedAt != current.achievedAt -> candidate.achievedAt > current.achievedAt
        candidate.dxScore != current.dxScore -> candidate.dxScore > current.dxScore
        else -> scoreProgressOrder(candidate) > scoreProgressOrder(current)
    }

    private fun scoreProgressOrder(score: ScoreEntity): Int = when (ScoreRules.canonicalFc(score.fc)) {
        "app" -> 400
        "ap" -> 300
        "fcp" -> 200
        "fc" -> 100
        else -> 0
    } + when (ScoreRules.canonicalFs(score.fs)) {
        "fsdp" -> 50
        "fsd" -> 40
        "fsp" -> 30
        "fs" -> 20
        "sync" -> 10
        else -> 0
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
