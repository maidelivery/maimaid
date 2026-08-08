package net.krtl.maimaid.core.data.repository

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import net.krtl.maimaid.core.data.remote.BackendErrorDto
import net.krtl.maimaid.core.data.remote.BackendHttpClient
import net.krtl.maimaid.core.data.remote.BackendSyncPullResponseDto
import net.krtl.maimaid.core.data.remote.BackendSyncPushRequestDto
import net.krtl.maimaid.core.data.remote.BackendSyncPushResponseDto
import net.krtl.maimaid.core.data.session.BackendSessionManager
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.SessionState
import net.krtl.maimaid.core.domain.SyncConflictPolicy
import net.krtl.maimaid.core.domain.SyncPullResult
import net.krtl.maimaid.core.domain.SyncPushResult
import net.krtl.maimaid.core.domain.repository.SyncPushPayload
import net.krtl.maimaid.core.domain.repository.SyncRepository
import net.krtl.maimaid.data.local.dao.MaimaiDao
import net.krtl.maimaid.data.local.entity.PlayRecordEntity
import net.krtl.maimaid.data.local.entity.ScoreEntity
import net.krtl.maimaid.data.local.entity.SheetEntity
import net.krtl.maimaid.data.local.entity.SyncConfigEntity
import net.krtl.maimaid.data.local.entity.UserProfileEntity
import java.time.Instant
import java.util.UUID

class SyncRepositoryImpl(
    private val httpClient: BackendHttpClient,
    private val sessionManager: BackendSessionManager,
    private val dao: MaimaiDao,
    private val json: Json
) : SyncRepository {
    override suspend fun push(payload: SyncPushPayload): Result<SyncPushResult, DomainError> {
        val request = BackendSyncPushRequestDto(
            idempotencyKey = payload.idempotencyKey,
            profileUpserts = payload.profileUpserts,
            scoreUpserts = payload.scoreUpserts,
            playRecordUpserts = payload.playRecordUpserts
        )
        val result = withAuthorizedCall {
            httpClient.execute(
                path = "v1/sync:push",
                method = "POST",
                bodyJson = httpClient.encodeBody(request),
                bearerToken = it
            )
        }
        return when (result) {
            is Result.Err -> result
            is Result.Ok -> {
                if (result.value.statusCode !in 200..299) {
                    Result.Err(parseDomainError(result.value.statusCode, result.value.body))
                } else {
                    val payloadResponse = runCatching {
                        httpClient.decodeBody<BackendSyncPushResponseDto>(result.value.body)
                    }.getOrElse {
                        return Result.Err(DomainError.Unknown("Invalid sync push payload"))
                    }
                    persistPushState(payloadResponse.latestRevision)
                    Result.Ok(SyncPushResult(latestRevision = payloadResponse.latestRevision))
                }
            }
        }
    }

    override suspend fun pushLocalSnapshot(profileId: String?): Result<SyncPushResult, DomainError> {
        val payload = runCatching { buildLocalSnapshotPayload(profileId) }
            .getOrElse { return Result.Err(DomainError.Unknown(it.message ?: "Failed to prepare local sync payload")) }
        return push(payload)
    }

    override suspend fun pull(
        sinceRevision: String,
        profileId: String?,
        force: Boolean
    ): Result<SyncPullResult, DomainError> {
        val revision = if (force) "0" else sinceRevision
        val query = buildString {
            append("v1/sync:pull?sinceRevision=")
            append(revision)
            append("&includeSnapshot=true")
            if (!profileId.isNullOrBlank()) {
                append("&profileId=")
                append(profileId)
            }
        }
        val result = withAuthorizedCall {
            httpClient.execute(
                path = query,
                method = "GET",
                bearerToken = it
            )
        }
        return when (result) {
            is Result.Err -> result
            is Result.Ok -> {
                if (result.value.statusCode !in 200..299) {
                    Result.Err(parseDomainError(result.value.statusCode, result.value.body))
                } else {
                    val payload = runCatching {
                        httpClient.decodeBody<BackendSyncPullResponseDto>(result.value.body)
                    }.getOrElse {
                        return Result.Err(DomainError.Unknown("Invalid sync pull payload"))
                    }
                    val applyResult = runCatching {
                        applySnapshot(
                            snapshot = payload.snapshot,
                            latestRevision = payload.latestRevision
                        )
                    }.getOrElse {
                        return Result.Err(DomainError.Unknown(it.message ?: "Failed to apply sync snapshot"))
                    }
                    Result.Ok(
                        SyncPullResult(
                            latestRevision = payload.latestRevision,
                            profileCount = applyResult.profileCount,
                            scoreCount = applyResult.appliedScoreCount,
                            recordCount = applyResult.appliedRecordCount
                        )
                    )
                }
            }
        }
    }

    override suspend fun resolveConflict(policy: SyncConflictPolicy): Result<Unit, DomainError> {
        return when (policy) {
            SyncConflictPolicy.MERGE_LOCAL_AND_CLOUD,
            SyncConflictPolicy.OVERWRITE_LOCAL_WITH_CLOUD,
            SyncConflictPolicy.OVERWRITE_CLOUD_WITH_LOCAL -> Result.Ok(Unit)
        }
    }

    private suspend fun withAuthorizedCall(
        block: suspend (token: String) -> BackendHttpClient.HttpResult
    ): Result<BackendHttpClient.HttpResult, DomainError> {
        val token = sessionManager.accessTokenForRequest()
            ?: return Result.Err(DomainError.Unauthorized())
        val first = runCatching { block(token) }
            .getOrElse { return Result.Err(DomainError.Network(it.message ?: "Network failure")) }
        if (first.statusCode != 401) {
            return Result.Ok(first)
        }
        val refreshed = sessionManager.refreshSessionSilently()
        if (!refreshed) return Result.Err(DomainError.Unauthorized())
        val retryToken = sessionManager.accessTokenForRequest()
            ?: return Result.Err(DomainError.Unauthorized())
        val second = runCatching { block(retryToken) }
            .getOrElse { return Result.Err(DomainError.Network(it.message ?: "Network failure")) }
        return Result.Ok(second)
    }

    private fun parseDomainError(statusCode: Int, body: String): DomainError {
        val payload = runCatching {
            json.decodeFromString(BackendErrorDto.serializer(), body)
        }.getOrNull()
        val message = payload?.message?.takeIf { it.isNotBlank() } ?: "HTTP $statusCode"
        return when (statusCode) {
            400 -> DomainError.Validation(message)
            401 -> DomainError.Unauthorized(message)
            409 -> DomainError.Conflict(message)
            in 500..599 -> DomainError.Server(statusCode, message)
            else -> DomainError.Unknown(message)
        }
    }

    private suspend fun buildLocalSnapshotPayload(profileId: String?): SyncPushPayload {
        val profiles = dao.getProfiles()
            .filter { profileId.isNullOrBlank() || it.id == profileId }
        if (profiles.isEmpty()) {
            return SyncPushPayload(idempotencyKey = UUID.randomUUID().toString())
        }

        val songsByIdentifier = dao.getSongsWithSheets().associateBy { it.song.songIdentifier }
        val sheetById = songsByIdentifier.values
            .flatMap { it.sheets }
            .associateBy { it.sheetId }
        val scoreGroups = profiles.mapNotNull { profile ->
            val scores = dao.getScores(profile.id)
            if (scores.isEmpty()) {
                null
            } else {
                buildJsonObject {
                    put("profileId", profile.id.lowercase())
                    put(
                        "scores",
                        JsonArray(
                            scores.map { score ->
                                scoreJson(
                                    score = score,
                                    songsByIdentifier = songsByIdentifier,
                                    sheetById = sheetById
                                )
                            }
                        )
                    )
                }
            }
        }
        val playRecordGroups = profiles.mapNotNull { profile ->
            val records = dao.getPlayRecords(profile.id)
            if (records.isEmpty()) {
                null
            } else {
                buildJsonObject {
                    put("profileId", profile.id.lowercase())
                    put(
                        "records",
                        JsonArray(
                            records.map { record ->
                                playRecordJson(
                                    record = record,
                                    songsByIdentifier = songsByIdentifier,
                                    sheetById = sheetById
                                )
                            }
                        )
                    )
                }
            }
        }
        return SyncPushPayload(
            idempotencyKey = UUID.randomUUID().toString(),
            profileUpserts = profiles.map(::profileJson),
            scoreUpserts = scoreGroups,
            playRecordUpserts = playRecordGroups
        )
    }

    private fun profileJson(profile: UserProfileEntity): JsonObject = buildJsonObject {
        put("profileId", profile.id.lowercase())
        put("name", profile.name)
        put("server", profile.server.lowercase())
        put("isActive", profile.isActive)
        put("playerRating", profile.playerRating)
        putNullableString("plate", profile.plate)
        putNullableString("avatarUrl", profile.avatarUrl)
        put("b35Count", profile.b35Count)
        put("b15Count", profile.b15Count)
        put("b35RecLimit", profile.b35RecLimit)
        put("b15RecLimit", profile.b15RecLimit)
        put("createdAt", Instant.ofEpochMilli(profile.createdAt).toString())
        put("clientUpdatedAt", Instant.now().toString())
    }

    private fun scoreJson(
        score: ScoreEntity,
        songsByIdentifier: Map<String, net.krtl.maimaid.data.local.relation.SongWithSheets>,
        sheetById: Map<String, SheetEntity>
    ): JsonObject {
        val sheet = sheetById[score.sheetId]
        val song = sheet?.songIdentifier?.let(songsByIdentifier::get)?.song
        return buildJsonObject {
            putNullableString("songIdentifier", sheet?.songIdentifier)
            sheet?.songId?.takeIf { it > 0 }?.let { put("songId", it) }
            putNullableString("title", song?.title)
            putNullableString("type", sheet?.type?.lowercase())
            putNullableString("difficulty", sheet?.difficulty?.lowercase())
            chartLevelIndex(sheet?.difficulty)?.let { put("levelIndex", it) }
            put("achievements", score.rate)
            putNullableString("rank", score.rank)
            put("dxScore", score.dxScore)
            putNullableString("fc", score.fc)
            putNullableString("fs", score.fs)
            put("achievedAt", Instant.ofEpochMilli(score.achievementDate).toString())
        }
    }

    private fun playRecordJson(
        record: PlayRecordEntity,
        songsByIdentifier: Map<String, net.krtl.maimaid.data.local.relation.SongWithSheets>,
        sheetById: Map<String, SheetEntity>
    ): JsonObject {
        val sheet = sheetById[record.sheetId]
        val song = sheet?.songIdentifier?.let(songsByIdentifier::get)?.song
        return buildJsonObject {
            putNullableString("songIdentifier", sheet?.songIdentifier)
            sheet?.songId?.takeIf { it > 0 }?.let { put("songId", it) }
            putNullableString("title", song?.title)
            putNullableString("type", sheet?.type?.lowercase())
            putNullableString("difficulty", sheet?.difficulty?.lowercase())
            chartLevelIndex(sheet?.difficulty)?.let { put("levelIndex", it) }
            put("achievements", record.rate)
            putNullableString("rank", record.rank)
            put("dxScore", record.dxScore)
            putNullableString("fc", record.fc)
            putNullableString("fs", record.fs)
            put("playTime", Instant.ofEpochMilli(record.playDate).toString())
        }
    }

    private fun chartLevelIndex(difficulty: String?): Int? = when (canonicalDifficulty(difficulty)) {
        "basic" -> 0
        "advanced" -> 1
        "expert" -> 2
        "master" -> 3
        "remaster" -> 4
        else -> null
    }

    private suspend fun persistPushState(latestRevision: String) {
        val currentConfig = dao.getSyncConfig() ?: SyncConfigEntity(
            isAutoUploadEnabled = false,
            backgroundSyncInterval = 0,
            themeRawValue = 0,
            lastStaticDataUpdateDate = null
        )
        val ownerUserId = when (val session = sessionManager.sessionState.value) {
            is SessionState.LoggedIn -> session.user.id
            else -> currentConfig.localDataOwnerUserId
        }
        dao.upsertSyncConfig(
            currentConfig.copy(
                lastSyncRevision = latestRevision,
                lastCloudBackupDate = System.currentTimeMillis(),
                localDataOwnerUserId = ownerUserId
            )
        )
    }

    private suspend fun applySnapshot(
        snapshot: net.krtl.maimaid.core.data.remote.BackendSyncSnapshotDto,
        latestRevision: String
    ): ApplySnapshotResult {
        val parsedProfiles = snapshot.profiles.mapNotNull(::parseProfile)
        if (parsedProfiles.isNotEmpty()) {
            dao.upsertProfiles(parsedProfiles.map(::toProfileEntity))
            parsedProfiles.firstOrNull { it.isActive }?.let { active ->
                dao.clearActiveProfiles()
                dao.activateProfile(active.id)
            }
        }

        val touchedProfileIds = parsedProfiles.mapTo(mutableSetOf()) { it.id }
        val sheets = dao.getSheets()
        val sheetLookup = buildSheetLookup(sheets)

        val scoreEntities = mutableListOf<ScoreEntity>()
        var unmappedScores = 0
        snapshot.scores.forEach { raw ->
            val profileId = raw.string("profileId")?.lowercase().orEmpty()
            if (profileId.isBlank()) {
                return@forEach
            }
            touchedProfileIds += profileId

            val remoteSheet = raw.obj("sheet")
            val localSheetId = resolveLocalSheetId(remoteSheet, sheetLookup)
            if (localSheetId == null) {
                unmappedScores += 1
                return@forEach
            }
            val achievements = raw.double("achievements") ?: return@forEach
            val rank = raw.string("rank") ?: "D"
            val dxScore = raw.int("dxScore") ?: 0
            val achievedAtMillis = parseIsoInstantMillis(raw.string("achievedAt"))
            scoreEntities += ScoreEntity(
                scoreKey = "$profileId::$localSheetId",
                sheetId = localSheetId,
                userProfileId = profileId,
                rate = achievements,
                rank = rank,
                achievementDate = achievedAtMillis,
                dxScore = dxScore,
                fc = raw.string("fc"),
                fs = raw.string("fs")
            )
        }

        val recordEntities = mutableListOf<PlayRecordEntity>()
        var unmappedRecords = 0
        snapshot.records.forEach { raw ->
            val profileId = raw.string("profileId")?.lowercase().orEmpty()
            if (profileId.isBlank()) {
                return@forEach
            }
            touchedProfileIds += profileId

            val remoteSheet = raw.obj("sheet")
            val localSheetId = resolveLocalSheetId(remoteSheet, sheetLookup)
            if (localSheetId == null) {
                unmappedRecords += 1
                return@forEach
            }
            val achievements = raw.double("achievements") ?: return@forEach
            val rank = raw.string("rank") ?: "D"
            val dxScore = raw.int("dxScore") ?: 0
            val playTimeMillis = parseIsoInstantMillis(raw.string("playTime"))
            recordEntities += PlayRecordEntity(
                id = raw.string("id").takeUnless { it.isNullOrBlank() } ?: UUID.randomUUID().toString(),
                sheetId = localSheetId,
                userProfileId = profileId,
                rate = achievements,
                rank = rank,
                playDate = playTimeMillis,
                dxScore = dxScore,
                fc = raw.string("fc"),
                fs = raw.string("fs")
            )
        }

        val scoresByProfile = scoreEntities.groupBy { it.userProfileId }
        val recordsByProfile = recordEntities.groupBy { it.userProfileId }
        touchedProfileIds.forEach { profileId ->
            dao.replaceProfileScoresAndRecords(
                profileId = profileId,
                scores = scoresByProfile[profileId].orEmpty(),
                records = recordsByProfile[profileId].orEmpty()
            )
        }

        val currentConfig = dao.getSyncConfig() ?: SyncConfigEntity(
            isAutoUploadEnabled = false,
            backgroundSyncInterval = 0,
            themeRawValue = 0,
            lastStaticDataUpdateDate = null
        )
        dao.upsertSyncConfig(currentConfig.copy(lastSyncRevision = latestRevision))

        return ApplySnapshotResult(
            profileCount = parsedProfiles.size,
            appliedScoreCount = scoreEntities.size,
            appliedRecordCount = recordEntities.size,
            unmappedScoreCount = unmappedScores,
            unmappedRecordCount = unmappedRecords
        )
    }

    private fun parseProfile(raw: JsonObject): ParsedProfile? {
        val id = raw.string("id")?.lowercase().orEmpty()
        if (id.isBlank()) {
            return null
        }
        val name = raw.string("name") ?: "Player"
        return ParsedProfile(
            id = id,
            name = name,
            server = raw.string("server") ?: "jp",
            avatarUrl = raw.string("avatarUrl"),
            isActive = raw.boolean("isActive") ?: false,
            createdAt = parseIsoInstantMillis(raw.string("createdAt")),
            playerRating = raw.int("playerRating") ?: 0,
            plate = raw.string("plate"),
            b35Count = raw.int("b35Count") ?: 35,
            b15Count = raw.int("b15Count") ?: 15,
            b35RecLimit = raw.int("b35RecLimit") ?: 10,
            b15RecLimit = raw.int("b15RecLimit") ?: 10
        )
    }

    private fun toProfileEntity(profile: ParsedProfile): UserProfileEntity {
        return UserProfileEntity(
            id = profile.id,
            name = profile.name,
            server = profile.server,
            avatarUrl = profile.avatarUrl,
            isActive = profile.isActive,
            createdAt = profile.createdAt,
            playerRating = profile.playerRating,
            plate = profile.plate,
            b35Count = profile.b35Count,
            b15Count = profile.b15Count,
            b35RecLimit = profile.b35RecLimit,
            b15RecLimit = profile.b15RecLimit
        )
    }

    private fun buildSheetLookup(sheets: List<SheetEntity>): Map<String, String> {
        val map = mutableMapOf<String, String>()
        sheets.forEach { sheet ->
            val chartType = canonicalChartType(sheet.type)
            val difficulty = canonicalDifficulty(sheet.difficulty)
            if (chartType.isEmpty() || difficulty.isEmpty()) {
                return@forEach
            }
            val identifiers = buildSet {
                add(sheet.songIdentifier.trim().lowercase())
                if (sheet.songId > 0) {
                    add(sheet.songId.toString())
                }
            }.filter { it.isNotBlank() && it != "0" }

            identifiers.forEach { identifier ->
                map[sheetKey(identifier, "_", chartType, difficulty)] = sheet.sheetId
                map[sheetKey(identifier, "-", chartType, difficulty)] = sheet.sheetId
            }
        }
        return map
    }

    private fun resolveLocalSheetId(
        remoteSheet: JsonObject?,
        sheetLookup: Map<String, String>
    ): String? {
        remoteSheet ?: return null
        val songIdentifier = remoteSheet.string("songIdentifier")
        val songId = remoteSheet.int("songId")
        val chartType = canonicalChartType(remoteSheet.string("chartType") ?: remoteSheet.string("type"))
        val difficulty = canonicalDifficulty(remoteSheet.string("difficulty"))
        if (chartType.isEmpty() || difficulty.isEmpty()) {
            return null
        }
        val identifiers = buildList {
            if (!songIdentifier.isNullOrBlank()) {
                add(songIdentifier.trim().lowercase())
            }
            if (songId != null && songId > 0) {
                add(songId.toString())
            }
        }.distinct()

        identifiers.forEach { identifier ->
            sheetLookup[sheetKey(identifier, "_", chartType, difficulty)]?.let { return it }
            sheetLookup[sheetKey(identifier, "-", chartType, difficulty)]?.let { return it }
        }
        return null
    }

    private fun parseIsoInstantMillis(value: String?): Long {
        val raw = value?.trim().orEmpty()
        if (raw.isEmpty()) {
            return System.currentTimeMillis()
        }
        return runCatching { Instant.parse(raw).toEpochMilli() }
            .getOrElse { System.currentTimeMillis() }
    }

    private fun canonicalChartType(value: String?): String {
        return when (val normalized = value?.trim()?.lowercase().orEmpty()) {
            "standard", "std", "sd" -> "std"
            "dx" -> "dx"
            "utage" -> "utage"
            else -> normalized
        }
    }

    private fun canonicalDifficulty(value: String?): String {
        return value?.trim()?.lowercase()
            ?.replace(" ", "")
            ?.replace("_", "")
            ?.replace(":", "")
            .orEmpty()
    }

    private fun sheetKey(
        identifier: String,
        separator: String,
        chartType: String,
        difficulty: String
    ): String = "${identifier.lowercase()}$separator$chartType$separator$difficulty"

    private fun JsonObject.obj(name: String): JsonObject? =
        this[name]?.let { element ->
            runCatching { element.jsonObject }.getOrNull()
        }

    private fun JsonObject.string(name: String): String? =
        primitiveContentOrNull(name)?.takeIf { it.isNotBlank() }

    private fun JsonObject.int(name: String): Int? =
        primitiveContentOrNull(name)?.toIntOrNull()

    private fun JsonObject.double(name: String): Double? =
        primitiveContentOrNull(name)?.toDoubleOrNull()

    private fun JsonObject.boolean(name: String): Boolean? =
        primitiveContentOrNull(name)?.toBooleanStrictOrNull()

    private fun JsonObject.primitiveContentOrNull(name: String): String? {
        val element = this[name] ?: return null
        if (element.toString() == "null") {
            return null
        }
        return runCatching { element.jsonPrimitive.content }.getOrNull()
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putNullableString(
        key: String,
        value: String?
    ) {
        if (value == null) {
            put(key, JsonPrimitive(null as String?))
        } else {
            put(key, value)
        }
    }

    private data class ParsedProfile(
        val id: String,
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

    private data class ApplySnapshotResult(
        val profileCount: Int,
        val appliedScoreCount: Int,
        val appliedRecordCount: Int,
        val unmappedScoreCount: Int,
        val unmappedRecordCount: Int
    )
}
