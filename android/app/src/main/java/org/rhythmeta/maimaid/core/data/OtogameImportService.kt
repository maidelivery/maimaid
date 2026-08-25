package org.rhythmeta.maimaid.core.data

import androidx.room.withTransaction
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.rhythmeta.maimaid.core.database.MaimaidDatabase
import org.rhythmeta.maimaid.core.database.PlayRecordEntity

data class OtogameImportResult(
    val fetchedCount: Int,
    val importedCount: Int,
    val duplicateCount: Int,
    val unmatchedCount: Int,
)

class OtogameImportService(
    private val database: MaimaidDatabase,
    json: Json,
) {
    private val apiClient = OtogameApiClient(json)

    suspend fun importRecent(
        authorizationHeader: String,
        onPageProgress: (currentPage: Int, totalPages: Int) -> Unit = { _, _ -> },
    ): OtogameImportResult {
        val profile = database.profileDao().activeProfile() ?: throw OtogameProfileUnavailableException()
        if (!OtogameImportPolicy.isEligibleServer(profile.server)) throw OtogameProfileUnavailableException()

        val existingRecordIds = database.scoreDao().playRecords(profile.id)
            .mapTo(mutableSetOf(), PlayRecordEntity::id)
        val fetched = fetchNewPlaylogs(
            profileId = profile.id,
            authorizationHeader = authorizationHeader,
            existingRecordIds = existingRecordIds,
            onPageProgress = onPageProgress,
        )
        val ratingEntries = try {
            apiClient.fetchRating(authorizationHeader).data.let { data ->
                data.ratingList + data.newRatingList
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            emptyList()
        }
        val matcher = OtogameSheetMatcher(
            songs = database.catalogDao().songs(),
            sheets = database.catalogDao().sheets(),
        )
        val mapped = fetched.playlogs.mapNotNull { pending ->
            val sheet = matcher.match(pending.playlog) ?: return@mapNotNull null
            val achievement = OtogameImportPolicy.achievement(pending.playlog.achievement)
            val input = ScoreInput(
                achievement = achievement,
                dxScore = pending.playlog.deluxeScore,
                fc = OtogameImportPolicy.fullCombo(pending.playlog.comboStatus),
                fs = OtogameImportPolicy.fullSync(pending.playlog.syncStatus),
            )
            val maxDxScore = ScoreRules.effectiveMaxDxScore(sheet.total)
            val playedAt = pending.playlog.playDate.takeIf { it > 0 }
                ?.coerceAtMost(Long.MAX_VALUE / 1_000)
                ?.times(1_000)
                ?: return@mapNotNull null
            if (ScoreRules.validate(input, maxDxScore) != null) return@mapNotNull null
            MappedPlaylog(pending.id, sheet.sheetKey, input, playedAt, pending.playlog.scoreRank)
        }
        val mappedRatingScores = ratingEntries.mapNotNull { entry ->
            val sheet = matcher.match(entry) ?: return@mapNotNull null
            val input = ScoreInput(
                achievement = OtogameImportPolicy.achievement(entry.achievement),
                fc = OtogameImportPolicy.fullCombo(entry.comboStatus),
            )
            if (ScoreRules.validate(input, ScoreRules.effectiveMaxDxScore(sheet.total)) != null) {
                return@mapNotNull null
            }
            MappedRatingScore(sheet.sheetKey, input)
        }

        var importedCount = 0
        var concurrentDuplicateCount = 0
        database.withTransaction {
            val currentProfile = database.profileDao().activeProfile()
                ?: throw OtogameProfileUnavailableException()
            if (currentProfile.id != profile.id || !OtogameImportPolicy.isEligibleServer(currentProfile.server)) {
                throw OtogameProfileUnavailableException()
            }
            val scoreDao = database.scoreDao()
            mapped.forEach { item ->
                if (scoreDao.playRecord(item.id) != null) {
                    concurrentDuplicateCount += 1
                    return@forEach
                }
                val rank = OtogameImportPolicy.rank(item.scoreRank)
                    ?: ScoreRules.calculateRank(item.input.achievement)
                scoreDao.upsertPlayRecord(
                    PlayRecordEntity(
                        id = item.id,
                        profileId = profile.id,
                        sheetKey = item.sheetKey,
                        achievement = item.input.achievement,
                        rank = rank,
                        dxScore = item.input.dxScore,
                        fc = ScoreRules.canonicalFc(item.input.fc),
                        fs = ScoreRules.canonicalFs(item.input.fs),
                        playedAt = item.playedAt,
                    ),
                )
                scoreDao.upsertScore(
                    ScoreRules.mergeScore(
                        profileId = profile.id,
                        sheetKey = item.sheetKey,
                        existing = scoreDao.score(profile.id, item.sheetKey),
                        input = item.input,
                        now = item.playedAt,
                    ),
                )
                importedCount += 1
            }
            mappedRatingScores.forEach { item ->
                val existing = scoreDao.score(profile.id, item.sheetKey)
                val merged = ScoreRules.mergeScore(
                    profileId = profile.id,
                    sheetKey = item.sheetKey,
                    existing = existing,
                    input = item.input,
                    now = System.currentTimeMillis(),
                )
                if (merged != existing) {
                    scoreDao.upsertScore(merged)
                    importedCount += 1
                }
            }
        }

        return OtogameImportResult(
            fetchedCount = fetched.fetchedCount,
            importedCount = importedCount,
            duplicateCount = fetched.duplicateCount + concurrentDuplicateCount,
            unmatchedCount = fetched.playlogs.size - mapped.size,
        )
    }

    private suspend fun fetchNewPlaylogs(
        profileId: String,
        authorizationHeader: String,
        existingRecordIds: Set<String>,
        onPageProgress: (currentPage: Int, totalPages: Int) -> Unit,
    ): FetchedPlaylogs {
        val playlogs = mutableListOf<PendingPlaylog>()
        val seenRecordIds = mutableSetOf<String>()
        var fetchedCount = 0
        var duplicateCount = 0
        var page = 1

        while (true) {
            val response = apiClient.fetchPlaylogs(authorizationHeader, page)
            val totalPages = response.data.pagination.totalPage
                .coerceIn(1, OtogameImportPolicy.PlaylogPageLimit)
            onPageProgress(page, totalPages)
            if (response.data.data.isEmpty()) break

            for (playlog in response.data.data) {
                fetchedCount += 1
                val id = OtogameImportPolicy.stableRecordId(profileId, playlog)
                if (id in existingRecordIds) {
                    duplicateCount += 1
                    continue
                }
                if (!seenRecordIds.add(id)) {
                    duplicateCount += 1
                    continue
                }
                playlogs += PendingPlaylog(id, playlog)
            }
            if (page >= totalPages) break
            page += 1
        }
        return FetchedPlaylogs(playlogs, fetchedCount, duplicateCount)
    }

    private data class PendingPlaylog(val id: String, val playlog: OtogamePlaylog)

    private data class FetchedPlaylogs(
        val playlogs: List<PendingPlaylog>,
        val fetchedCount: Int,
        val duplicateCount: Int,
    )

    private data class MappedPlaylog(
        val id: String,
        val sheetKey: String,
        val input: ScoreInput,
        val playedAt: Long,
        val scoreRank: Int,
    )

    private data class MappedRatingScore(
        val sheetKey: String,
        val input: ScoreInput,
    )
}

class OtogameUnauthorizedException : Exception("Otogame session expired.")

class OtogameProfileUnavailableException : Exception("A Japanese server profile is required.")

private class OtogameApiClient(
    private val json: Json,
) {
    suspend fun fetchPlaylogs(authorizationHeader: String, page: Int): OtogamePlaylogResponse =
        withContext(Dispatchers.IO) {
            require(authorizationHeader.startsWith("Bearer ", ignoreCase = true))
            val connection = URL("$PlaylogEndpoint?page=$page").openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TimeoutMillis
                connection.readTimeout = TimeoutMillis
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", authorizationHeader)
                connection.setRequestProperty("Referer", "https://u.otogame.net/maimai/music")
                when (val status = connection.responseCode) {
                    HttpURLConnection.HTTP_OK -> connection.inputStream.bufferedReader().use { reader ->
                        try {
                            json.decodeFromString<OtogamePlaylogResponse>(reader.readText())
                        } catch (error: SerializationException) {
                            throw OtogameResponseException(error)
                        }
                    }
                    HttpURLConnection.HTTP_UNAUTHORIZED, HttpURLConnection.HTTP_FORBIDDEN ->
                        throw OtogameUnauthorizedException()
                    else -> throw IllegalStateException("Otogame request failed with HTTP $status.")
                }
            } finally {
                connection.disconnect()
            }
        }

    suspend fun fetchRating(authorizationHeader: String): OtogameRatingResponse =
        withContext(Dispatchers.IO) {
            require(authorizationHeader.startsWith("Bearer ", ignoreCase = true))
            val connection = URL(RatingEndpoint).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "GET"
                connection.connectTimeout = TimeoutMillis
                connection.readTimeout = TimeoutMillis
                connection.useCaches = false
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Authorization", authorizationHeader)
                connection.setRequestProperty("Referer", "https://u.otogame.net/maimai/music")
                if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                    throw IllegalStateException("Otogame rating request failed.")
                }
                connection.inputStream.bufferedReader().use { reader ->
                    try {
                        json.decodeFromString<OtogameRatingResponse>(reader.readText())
                    } catch (error: SerializationException) {
                        throw OtogameResponseException(error)
                    }
                }
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val PlaylogEndpoint = "https://u.otogame.net/api/game/maimai/playlog"
        const val RatingEndpoint = "https://u.otogame.net/api/game/maimai/rating"
        const val TimeoutMillis = 15_000
    }
}

private class OtogameResponseException(cause: Throwable) :
    Exception("Otogame returned an unsupported response.", cause)
