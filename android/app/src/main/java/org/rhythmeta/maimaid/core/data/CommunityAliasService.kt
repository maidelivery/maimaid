package org.rhythmeta.maimaid.core.data

import java.net.URLEncoder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.rhythmeta.maimaid.core.network.BackendApiException
import org.rhythmeta.maimaid.core.network.BackendApiClient
import org.rhythmeta.maimaid.core.database.CatalogDao
import org.rhythmeta.maimaid.core.database.SongAliasEntity

class CommunityAliasService(
    private val apiClient: BackendApiClient,
    private val sessionManager: BackendSessionManager,
    private val catalogDao: CatalogDao,
    private val json: Json,
) {
    private val approvedSyncMutex = Mutex()
    private var lastApprovedSyncAt: Instant? = null
    private val mutableApprovedAliases = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    private val mutableMyAliases = MutableStateFlow<List<SongAliasEntity>>(emptyList())
    private var myAliasesOwnerId: String? = null
    val approvedAliases = mutableApprovedAliases.asStateFlow()
    val searchableAliases: Flow<List<SongAliasEntity>> = combine(
        catalogDao.observeAliases(),
        mutableMyAliases,
        ::mergeAliases,
    )
    val isConfigured: Boolean get() = sessionManager.isConfigured
    val isAuthenticated: Boolean get() = sessionManager.state.value.isAuthenticated

    suspend fun fetchVotingBoard(limit: Int = 150): List<CommunityAliasVotingBoardItem> {
        if (!isConfigured || !isAuthenticated) return emptyList()
        val payload = sessionManager.authorizedRequest(
            "v1/community/candidates:votingBoard?limit=${limit.coerceIn(1, 200)}&offset=0",
        )
        return json.decodeFromJsonElement(
            CommunityAliasRowsResponse.serializer(CommunityAliasVotingBoardItem.serializer()),
            payload,
        ).rows
    }

    suspend fun fetchMySongCandidates(
        songIdentifier: String,
        limit: Int = 30,
    ): List<CommunityAliasMyCandidate> {
        val rows = fetchMyCandidates(songIdentifier, limit)
            .filter { it.songIdentifier == songIdentifier }
        updateMyAliasesForSong(songIdentifier, rows)
        return rows
    }

    suspend fun syncMyAliases() {
        val ownerId = sessionManager.state.value.user?.id
        if (!isConfigured || ownerId == null) {
            clearMyAliases()
            return
        }
        val rows = fetchMyCandidates(songIdentifier = null, limit = 200)
        if (sessionManager.state.value.user?.id != ownerId) return
        myAliasesOwnerId = ownerId
        mutableMyAliases.value = rows.toSearchAliases()
    }

    fun rememberSubmittedAlias(songIdentifier: String, aliasText: String) {
        val ownerId = sessionManager.state.value.user?.id ?: return
        val newAlias = SongAliasEntity(songIdentifier, aliasText.trim())
        if (newAlias.alias.isEmpty()) return
        val current = if (myAliasesOwnerId == ownerId) mutableMyAliases.value else emptyList()
        myAliasesOwnerId = ownerId
        mutableMyAliases.value = mergeAliases(current, listOf(newAlias))
    }

    fun clearMyAliases() {
        myAliasesOwnerId = null
        mutableMyAliases.value = emptyList()
    }

    suspend fun fetchDailySubmissionCount(date: LocalDate = LocalDate.now()): Int {
        if (!isConfigured || !isAuthenticated) return 0
        val payload = sessionManager.authorizedRequest(
            "v1/community/candidates:dailyCount?localDate=$date",
        )
        return json.decodeFromJsonElement(CommunityAliasDailyCountResponse.serializer(), payload)
            .count
            .coerceIn(0, CommunityAliasDailyQuota)
    }

    suspend fun submitAlias(
        songIdentifier: String,
        aliasText: String,
    ): CommunityAliasSubmitResponse {
        if (!isConfigured) {
            return CommunityAliasSubmitResponse(
                status = CommunityAliasSubmitStatus.Error,
                message = "Cloud service is not configured.",
            )
        }
        if (!isAuthenticated) {
            return CommunityAliasSubmitResponse(status = CommunityAliasSubmitStatus.Unauthenticated)
        }
        val trimmedAlias = aliasText.trim()
        if (trimmedAlias.isEmpty()) {
            return CommunityAliasSubmitResponse(status = CommunityAliasSubmitStatus.InvalidRequest)
        }
        val offsetMinutes = ZoneId.systemDefault().rules
            .getOffset(Instant.now())
            .totalSeconds / 60
        return try {
            val payload = sessionManager.authorizedRequest(
                path = "v1/community/candidates",
                method = "POST",
                body = buildJsonObject {
                    put("songIdentifier", songIdentifier)
                    put("aliasText", trimmedAlias)
                    put("deviceLocalDate", LocalDate.now().toString())
                    put("tzOffsetMinutes", offsetMinutes)
                },
            )
            json.decodeFromJsonElement(CommunityAliasSubmitResponse.serializer(), payload)
        } catch (error: BackendApiException) {
            CommunityAliasSubmitResponse(
                status = when (error.statusCode) {
                    400 -> CommunityAliasSubmitStatus.InvalidRequest
                    401 -> CommunityAliasSubmitStatus.Unauthenticated
                    else -> CommunityAliasSubmitStatus.Error
                },
                message = error.message,
            )
        } catch (error: Exception) {
            CommunityAliasSubmitResponse(
                status = CommunityAliasSubmitStatus.Error,
                message = error.message.orEmpty(),
            )
        }
    }

    suspend fun vote(candidateId: String, support: Boolean): CommunityAliasVoteResult {
        val encodedId = URLEncoder.encode(candidateId, Charsets.UTF_8.name())
        val payload = sessionManager.authorizedRequest(
            path = "v1/community/candidates/$encodedId:vote",
            method = "POST",
            body = buildJsonObject { put("vote", if (support) 1 else -1) },
        )
        return json.decodeFromJsonElement(CommunityAliasVoteResult.serializer(), payload)
    }

    private suspend fun fetchMyCandidates(
        songIdentifier: String?,
        limit: Int,
    ): List<CommunityAliasMyCandidate> {
        if (!isConfigured || !isAuthenticated) return emptyList()
        val songQuery = songIdentifier?.let {
            "&songIdentifier=${URLEncoder.encode(it, Charsets.UTF_8.name())}"
        }.orEmpty()
        val payload = sessionManager.authorizedRequest(
            "v1/community/candidates:my?limit=${limit.coerceIn(1, 200)}$songQuery",
        )
        return json.decodeFromJsonElement(
            CommunityAliasRowsResponse.serializer(CommunityAliasMyCandidate.serializer()),
            payload,
        ).rows
    }

    private fun updateMyAliasesForSong(
        songIdentifier: String,
        candidates: List<CommunityAliasMyCandidate>,
    ) {
        val ownerId = sessionManager.state.value.user?.id ?: return
        val current = if (myAliasesOwnerId == ownerId) mutableMyAliases.value else emptyList()
        myAliasesOwnerId = ownerId
        mutableMyAliases.value = mergeAliases(
            current.filterNot { it.songIdentifier == songIdentifier },
            candidates.toSearchAliases(),
        )
    }

    suspend fun syncApprovedAliasesIfNeeded() = approvedSyncMutex.withLock {
        val now = Instant.now()
        val previousSync = lastApprovedSyncAt
        if (previousSync != null && now.isBefore(previousSync.plusSeconds(ApprovedSyncIntervalSeconds))) {
            return@withLock
        }
        val rows = runCatching {
            val payload = apiClient.request("v1/community/aliases:sync?limit=2000")
            json.decodeFromJsonElement(
                CommunityAliasRowsResponse.serializer(CommunityAliasApprovedSyncRow.serializer()),
                payload,
            ).rows
        }.getOrElse { return@withLock }
        mutableApprovedAliases.value = rows
            .groupBy(CommunityAliasApprovedSyncRow::songIdentifier)
            .mapValues { (_, values) ->
                values
                    .map(CommunityAliasApprovedSyncRow::aliasText)
                    .distinctBy(String::lowercase)
            }
        if (rows.isNotEmpty()) {
            val knownSongIdentifiers = catalogDao.songIdentifiers().toSet()
            catalogDao.upsertAliases(
                rows
                    .filter { row -> row.songIdentifier in knownSongIdentifiers }
                    .map { row -> SongAliasEntity(row.songIdentifier, row.aliasText) },
            )
        }
        lastApprovedSyncAt = now
    }

    private companion object {
        const val ApprovedSyncIntervalSeconds = 10L * 60L

        fun List<CommunityAliasMyCandidate>.toSearchAliases(): List<SongAliasEntity> =
            asSequence()
                .filter { it.status in SearchableCandidateStatuses }
                .map { SongAliasEntity(it.songIdentifier, it.aliasText.trim()) }
                .filter { it.alias.isNotEmpty() }
                .toList()

        fun mergeAliases(
            publicAliases: List<SongAliasEntity>,
            myAliases: List<SongAliasEntity>,
        ): List<SongAliasEntity> = (publicAliases + myAliases).distinctBy {
            it.songIdentifier to it.alias.trim().lowercase()
        }

        val SearchableCandidateStatuses = setOf("pool_private", "voting", "approved")
    }
}
