package net.krtl.maimaid.core.data.repository

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import net.krtl.maimaid.core.data.remote.BackendErrorDto
import net.krtl.maimaid.core.data.remote.BackendHttpClient
import net.krtl.maimaid.core.data.remote.CommunityAliasApprovedSyncRowDto
import net.krtl.maimaid.core.data.remote.CommunityAliasDailyCountResponseDto
import net.krtl.maimaid.core.data.remote.CommunityAliasExistingCandidateDto
import net.krtl.maimaid.core.data.remote.CommunityAliasMyCandidateDto
import net.krtl.maimaid.core.data.remote.CommunityAliasRowsResponseDto
import net.krtl.maimaid.core.data.remote.CommunityAliasSubmitCandidateDto
import net.krtl.maimaid.core.data.remote.CommunityAliasSubmitRequestDto
import net.krtl.maimaid.core.data.remote.CommunityAliasSubmitResponseDto
import net.krtl.maimaid.core.data.remote.CommunityAliasVoteRequestDto
import net.krtl.maimaid.core.data.remote.CommunityAliasVoteResultDto
import net.krtl.maimaid.core.data.remote.CommunityAliasVotingBoardItemDto
import net.krtl.maimaid.core.data.session.BackendSessionManager
import net.krtl.maimaid.core.domain.DomainError
import net.krtl.maimaid.core.domain.Result
import net.krtl.maimaid.core.domain.repository.CommunityAliasRepository
import net.krtl.maimaid.data.local.dao.MaimaiDao
import net.krtl.maimaid.data.local.entity.CommunityAliasCacheEntity
import net.krtl.maimaid.domain.model.CommunityAliasApprovedAlias
import net.krtl.maimaid.domain.model.CommunityAliasDuplicateReason
import net.krtl.maimaid.domain.model.CommunityAliasExistingCandidate
import net.krtl.maimaid.domain.model.CommunityAliasMyCandidate
import net.krtl.maimaid.domain.model.CommunityAliasSubmitCandidate
import net.krtl.maimaid.domain.model.CommunityAliasSubmitResponse
import net.krtl.maimaid.domain.model.CommunityAliasSubmitStatus
import net.krtl.maimaid.domain.model.CommunityAliasVoteResult
import net.krtl.maimaid.domain.model.CommunityAliasVotingBoardItem
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId

class CommunityAliasRepositoryImpl(
    context: Context,
    private val httpClient: BackendHttpClient,
    private val sessionManager: BackendSessionManager,
    private val dao: MaimaiDao,
    private val json: Json
) : CommunityAliasRepository {
    private val syncStore = CommunityAliasSyncStore(context.applicationContext)

    override fun observeApprovedAliases(songIdentifier: String): Flow<List<CommunityAliasApprovedAlias>> {
        return dao.observeCommunityAliasCache(songIdentifier).map { list ->
            list.map { it.asDomain() }
        }
    }

    override suspend fun syncApprovedAliasesIfNeeded(minimumIntervalMinutes: Long): Result<Int, DomainError> {
        val now = System.currentTimeMillis()
        val lastPollAt = syncStore.getLastPollAt()
        if (lastPollAt != null && now - lastPollAt < minimumIntervalMinutes.coerceAtLeast(0) * 60_000L) {
            return Result.Ok(0)
        }
        syncStore.setLastPollAt(now)
        return syncApprovedAliasesIntoSongs(force = false)
    }

    override suspend fun syncApprovedAliasesIntoSongs(force: Boolean): Result<Int, DomainError> {
        if (!sessionManager.isConfigured()) {
            return Result.Ok(0)
        }

        val now = System.currentTimeMillis()
        val rawSinceMillis = if (force) null else syncStore.getLastApprovedSyncAt()
        val sinceMillis = rawSinceMillis?.takeUnless { it > now + 5 * 60_000L }
        if (rawSinceMillis != null && sinceMillis == null) {
            syncStore.clearLastApprovedSyncAt()
        }

        val path = buildApprovedSyncPath(sinceMillis)
        val result = runCatching {
            httpClient.execute(path = path, method = "GET")
        }.getOrElse {
            return Result.Err(DomainError.Network(it.message ?: "Network failure"))
        }
        if (result.statusCode !in 200..299) {
            return Result.Err(parseDomainError(result.statusCode, result.body))
        }

        val payload = runCatching {
            httpClient.decodeBody<CommunityAliasRowsResponseDto<CommunityAliasApprovedSyncRowDto>>(result.body)
        }.getOrElse {
            return Result.Err(DomainError.Unknown("Invalid approved alias payload"))
        }

        val rows = payload.rows.map { it.asDomain() }
        if (force) {
            reconcileForceSync(rows)
        }
        if (rows.isEmpty()) {
            return Result.Ok(0)
        }

        dao.upsertCommunityAliasCaches(rows.map { it.asEntity() })
        mergeAliasesIntoSongs(rows)

        val maxUpdatedAt = rows.maxOfOrNull { it.updatedAt }
        if (maxUpdatedAt != null) {
            syncStore.setLastApprovedSyncAt(minOf(maxUpdatedAt, now))
        }
        return Result.Ok(rows.size)
    }

    override suspend fun submitAlias(songIdentifier: String, aliasText: String): CommunityAliasSubmitResponse {
        if (!sessionManager.isConfigured()) {
            return CommunityAliasSubmitResponse(
                status = CommunityAliasSubmitStatus.ERROR,
                message = "Backend is not configured."
            )
        }
        if (!sessionManager.isAuthenticated()) {
            return CommunityAliasSubmitResponse(
                status = CommunityAliasSubmitStatus.UNAUTHENTICATED,
                message = "Authentication required."
            )
        }

        val localDate = LocalDate.now(ZoneId.systemDefault()).toString()
        val payload = CommunityAliasSubmitRequestDto(
            songIdentifier = songIdentifier,
            aliasText = aliasText,
            deviceLocalDate = localDate,
            tzOffsetMinutes = OffsetDateTime.now().offset.totalSeconds / 60
        )
        val response = withAuthorizedCall { token ->
            httpClient.execute(
                path = "v1/community/candidates",
                method = "POST",
                bodyJson = httpClient.encodeBody(payload),
                bearerToken = token
            )
        }
        return when (response) {
            is Result.Err -> response.error.toSubmitResponse()
            is Result.Ok -> {
                if (response.value.statusCode !in 200..299) {
                    parseDomainError(response.value.statusCode, response.value.body).toSubmitResponse()
                } else {
                    runCatching {
                        httpClient.decodeBody<CommunityAliasSubmitResponseDto>(response.value.body).asDomain()
                    }.getOrElse {
                        CommunityAliasSubmitResponse(
                            status = CommunityAliasSubmitStatus.ERROR,
                            message = "Invalid alias submission payload"
                        )
                    }
                }
            }
        }
    }

    override suspend fun fetchVotingBoard(limit: Int, offset: Int): Result<List<CommunityAliasVotingBoardItem>, DomainError> {
        if (!sessionManager.isConfigured()) {
            return Result.Err(DomainError.Validation("Backend is not configured"))
        }
        val safeLimit = limit.coerceIn(1, 200)
        val safeOffset = offset.coerceAtLeast(0)
        val token = sessionManager.accessTokenForRequest()
        val result = runCatching {
            httpClient.execute(
                path = "v1/community/candidates:votingBoard?limit=$safeLimit&offset=$safeOffset",
                method = "GET",
                bearerToken = token
            )
        }.getOrElse {
            return Result.Err(DomainError.Network(it.message ?: "Network failure"))
        }
        if (result.statusCode !in 200..299) {
            return Result.Err(parseDomainError(result.statusCode, result.body))
        }
        val payload = runCatching {
            httpClient.decodeBody<CommunityAliasRowsResponseDto<CommunityAliasVotingBoardItemDto>>(result.body)
        }.getOrElse {
            return Result.Err(DomainError.Unknown("Invalid voting board payload"))
        }
        return Result.Ok(payload.rows.map { it.asDomain() })
    }

    override suspend fun fetchMySongCandidates(
        songIdentifier: String,
        limit: Int
    ): Result<List<CommunityAliasMyCandidate>, DomainError> {
        val safeLimit = limit.coerceIn(1, 200)
        val response = withAuthorizedCall { token ->
            httpClient.execute(
                path = "v1/community/candidates:my?songIdentifier=${Uri.encode(songIdentifier)}&limit=$safeLimit",
                method = "GET",
                bearerToken = token
            )
        }
        return when (response) {
            is Result.Err -> Result.Err(response.error)
            is Result.Ok -> {
                if (response.value.statusCode !in 200..299) {
                    Result.Err(parseDomainError(response.value.statusCode, response.value.body))
                } else {
                    val payload = runCatching {
                        httpClient.decodeBody<CommunityAliasRowsResponseDto<CommunityAliasMyCandidateDto>>(response.value.body)
                    }.getOrElse {
                        return Result.Err(DomainError.Unknown("Invalid candidate payload"))
                    }
                    Result.Ok(payload.rows.map { it.asDomain() })
                }
            }
        }
    }

    override suspend fun fetchMyDailySubmissionCount(): Result<Int, DomainError> {
        val localDate = LocalDate.now(ZoneId.systemDefault()).toString()
        val response = withAuthorizedCall { token ->
            httpClient.execute(
                path = "v1/community/candidates:dailyCount?localDate=$localDate",
                method = "GET",
                bearerToken = token
            )
        }
        return when (response) {
            is Result.Err -> Result.Err(response.error)
            is Result.Ok -> {
                if (response.value.statusCode !in 200..299) {
                    Result.Err(parseDomainError(response.value.statusCode, response.value.body))
                } else {
                    val payload = runCatching {
                        httpClient.decodeBody<CommunityAliasDailyCountResponseDto>(response.value.body)
                    }.getOrElse {
                        return Result.Err(DomainError.Unknown("Invalid daily count payload"))
                    }
                    Result.Ok(payload.count.coerceAtLeast(0))
                }
            }
        }
    }

    override suspend fun vote(candidateId: String, support: Boolean): Result<CommunityAliasVoteResult, DomainError> {
        val payload = CommunityAliasVoteRequestDto(vote = if (support) 1 else -1)
        val response = withAuthorizedCall { token ->
            httpClient.execute(
                path = "v1/community/candidates/${Uri.encode(candidateId.lowercase())}:vote",
                method = "POST",
                bodyJson = httpClient.encodeBody(payload),
                bearerToken = token
            )
        }
        return when (response) {
            is Result.Err -> Result.Err(response.error)
            is Result.Ok -> {
                if (response.value.statusCode !in 200..299) {
                    Result.Err(parseDomainError(response.value.statusCode, response.value.body))
                } else {
                    val result = runCatching {
                        httpClient.decodeBody<CommunityAliasVoteResultDto>(response.value.body)
                    }.getOrElse {
                        return Result.Err(DomainError.Unknown("Invalid vote payload"))
                    }
                    Result.Ok(result.asDomain())
                }
            }
        }
    }

    private suspend fun reconcileForceSync(rows: List<CommunityAliasApprovedAlias>) {
        val remoteIds = rows.map { it.candidateId }.toSet()
        val localIds = dao.getCommunityAliasCacheRows().map { it.candidateId }
        if (remoteIds.isEmpty()) {
            if (localIds.isNotEmpty()) {
                dao.clearCommunityAliasCaches()
            }
            return
        }
        val staleIds = localIds.filterNot(remoteIds::contains)
        if (staleIds.isNotEmpty()) {
            dao.deleteCommunityAliasCachesByIds(staleIds)
        }
    }

    private suspend fun mergeAliasesIntoSongs(rows: List<CommunityAliasApprovedAlias>) {
        val grouped = rows.groupBy { it.songIdentifier }
        val songs = dao.getSongsByIdentifiers(grouped.keys.toList())
        songs.forEach { song ->
            val remoteAliases = grouped[song.songIdentifier].orEmpty().map { it.aliasText }
            if (remoteAliases.isEmpty()) {
                return@forEach
            }
            val mergedAliases = mergeAliases(song.aliases, remoteAliases)
            if (mergedAliases != song.aliases) {
                dao.updateSongAliases(song.songIdentifier, mergedAliases)
            }
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
        if (!refreshed) {
            return Result.Err(DomainError.Unauthorized())
        }
        val retryToken = sessionManager.accessTokenForRequest()
            ?: return Result.Err(DomainError.Unauthorized())
        val second = runCatching { block(retryToken) }
            .getOrElse { return Result.Err(DomainError.Network(it.message ?: "Network failure")) }
        return Result.Ok(second)
    }

    private fun buildApprovedSyncPath(sinceMillis: Long?): String {
        return if (sinceMillis != null) {
            val encoded = Uri.encode(Instant.ofEpochMilli(sinceMillis).toString())
            "v1/community/aliases:sync?since=$encoded&limit=1000"
        } else {
            "v1/community/aliases:sync?limit=1000"
        }
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
}

private fun mergeAliases(base: List<String>, additions: List<String>): List<String> {
    val seen = LinkedHashSet<String>()
    val merged = mutableListOf<String>()
    (base + additions).forEach { alias ->
        val normalized = alias.trim()
        if (normalized.isEmpty()) {
            return@forEach
        }
        val key = normalized.lowercase()
        if (seen.add(key)) {
            merged += normalized
        }
    }
    return merged
}

private fun CommunityAliasCacheEntity.asDomain(): CommunityAliasApprovedAlias = CommunityAliasApprovedAlias(
    candidateId = candidateId,
    songIdentifier = songIdentifier,
    aliasText = aliasText,
    updatedAt = updatedAt,
    approvedAt = approvedAt
)

private fun CommunityAliasApprovedAlias.asEntity(): CommunityAliasCacheEntity = CommunityAliasCacheEntity(
    candidateId = candidateId,
    songIdentifier = songIdentifier,
    aliasText = aliasText,
    updatedAt = updatedAt,
    approvedAt = approvedAt
)

private fun CommunityAliasSubmitResponseDto.asDomain(): CommunityAliasSubmitResponse = CommunityAliasSubmitResponse(
    status = when (status) {
        "created" -> CommunityAliasSubmitStatus.CREATED
        "rejected_duplicate" -> CommunityAliasSubmitStatus.REJECTED_DUPLICATE
        "quota_exceeded" -> CommunityAliasSubmitStatus.QUOTA_EXCEEDED
        "unauthenticated" -> CommunityAliasSubmitStatus.UNAUTHENTICATED
        "invalid_request" -> CommunityAliasSubmitStatus.INVALID_REQUEST
        else -> CommunityAliasSubmitStatus.ERROR
    },
    message = message,
    duplicateReason = when (duplicateReason) {
        "lxns_existing" -> CommunityAliasDuplicateReason.LXNS_EXISTING
        "community_existing" -> CommunityAliasDuplicateReason.COMMUNITY_EXISTING
        "admin_rejected_locked" -> CommunityAliasDuplicateReason.ADMIN_REJECTED_LOCKED
        else -> null
    },
    candidate = candidate?.asDomain(),
    existingCandidates = existingCandidates.map { it.asDomain() },
    similarAliases = similarAliases,
    quotaRemaining = quotaRemaining
)

private fun CommunityAliasSubmitCandidateDto.asDomain(): CommunityAliasSubmitCandidate = CommunityAliasSubmitCandidate(
    id = id,
    songIdentifier = songIdentifier,
    aliasText = aliasText,
    status = status,
    createdAt = createdAt.toEpochMillis()
)

private fun CommunityAliasExistingCandidateDto.asDomain(): CommunityAliasExistingCandidate = CommunityAliasExistingCandidate(
    candidateId = candidateId,
    aliasText = aliasText,
    status = status,
    similarity = similarity,
    bucket = bucket,
    supportCount = supportCount,
    opposeCount = opposeCount
)

private fun CommunityAliasVotingBoardItemDto.asDomain(): CommunityAliasVotingBoardItem = CommunityAliasVotingBoardItem(
    candidateId = candidateId,
    songIdentifier = songIdentifier,
    aliasText = aliasText,
    submitterId = submitterId,
    voteOpenAt = voteOpenAt?.toEpochMillis(),
    voteCloseAt = voteCloseAt?.toEpochMillis(),
    supportCount = supportCount,
    opposeCount = opposeCount,
    myVote = myVote,
    createdAt = createdAt.toEpochMillis()
)

private fun CommunityAliasMyCandidateDto.asDomain(): CommunityAliasMyCandidate = CommunityAliasMyCandidate(
    candidateId = candidateId,
    songIdentifier = songIdentifier,
    aliasText = aliasText,
    status = status,
    voteOpenAt = voteOpenAt?.toEpochMillis(),
    voteCloseAt = voteCloseAt?.toEpochMillis(),
    supportCount = supportCount,
    opposeCount = opposeCount,
    createdAt = createdAt.toEpochMillis(),
    updatedAt = updatedAt.toEpochMillis()
)

private fun CommunityAliasVoteResultDto.asDomain(): CommunityAliasVoteResult = CommunityAliasVoteResult(
    candidateId = candidateId,
    supportCount = supportCount,
    opposeCount = opposeCount,
    myVote = myVote
)

private fun CommunityAliasApprovedSyncRowDto.asDomain(): CommunityAliasApprovedAlias = CommunityAliasApprovedAlias(
    candidateId = candidateId,
    songIdentifier = songIdentifier,
    aliasText = aliasText,
    updatedAt = updatedAt.toEpochMillis(),
    approvedAt = approvedAt?.toEpochMillis()
)

private fun DomainError.toSubmitResponse(): CommunityAliasSubmitResponse = when (this) {
    is DomainError.Unauthorized -> CommunityAliasSubmitResponse(
        status = CommunityAliasSubmitStatus.UNAUTHENTICATED,
        message = message
    )
    is DomainError.Validation -> CommunityAliasSubmitResponse(
        status = CommunityAliasSubmitStatus.INVALID_REQUEST,
        message = message
    )
    else -> CommunityAliasSubmitResponse(
        status = CommunityAliasSubmitStatus.ERROR,
        message = when (this) {
            is DomainError.Network -> message
            is DomainError.Conflict -> message
            is DomainError.Server -> message
            is DomainError.Unknown -> message
        }
    )
}

private fun String.toEpochMillis(): Long = Instant.parse(this).toEpochMilli()
