package org.rhythmeta.maimaid.core.data

import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.rhythmeta.maimaid.core.database.MaimaidDatabase
import org.rhythmeta.maimaid.core.database.ScoreEntity
import org.rhythmeta.maimaid.core.database.SheetEntity
import org.rhythmeta.maimaid.core.database.UserProfileEntity

class ScoreSyncService(
    private val database: MaimaidDatabase,
    private val preferences: AppPreferencesRepository,
    private val credentials: ProfileCredentialStore,
    private val backendSyncCoordinator: BackendSyncCoordinator,
    private val backendImportService: BackendImportService,
    private val json: Json,
) {
    suspend fun syncAfterScoreSave(sheetKey: String, score: ScoreEntity) = coroutineScope {
        launch {
            runCatching { backendSyncCoordinator.pushScoreUpdate(sheetKey, score) }
        }
        launch {
            runCatching { syncThirdParties(sheetKey, score) }
        }
    }

    private suspend fun syncThirdParties(sheetKey: String, score: ScoreEntity) {
        if (!preferences.thirdPartyScoreSyncEnabled.first()) return
        val sheet = database.catalogDao().sheet(sheetKey) ?: return
        val profile = database.profileDao().profiles().firstOrNull { it.id == score.profileId } ?: return
        if (!ThirdPartyScoreSyncPolicy.isEligible(profile, score, sheet)) return
        val title = database.catalogDao().song(sheet.songIdentifier)?.title.orEmpty()
        val profileCredentials = credentials.credentials(profile.id)
        coroutineScope {
            launch {
                runCatching {
                    uploadToDivingFish(
                        title = title,
                        sheet = sheet,
                        score = score,
                        profile = profile,
                        importToken = profileCredentials.divingFishToken,
                    )
                }
            }
            launch {
                runCatching {
                    uploadToLxns(
                        title = title,
                        sheet = sheet,
                        score = score,
                        profile = profile,
                        refreshToken = profileCredentials.lxnsToken,
                    )
                }
            }
        }
    }

    private suspend fun uploadToDivingFish(
        title: String,
        sheet: SheetEntity,
        score: ScoreEntity,
        profile: UserProfileEntity,
        importToken: String,
    ) {
        val token = importToken.trim()
        if (profile.dfUsername.isBlank() || token.isEmpty() || sheet.isUtage()) return
        postJson(
            url = DivingFishScoreUploadUrl,
            headers = mapOf("Import-Token" to token),
            body = json.encodeToString(
                listOf(
                    DivingFishScoreUploadRecord(
                    title = title,
                    levelIndex = ThirdPartyScoreSyncPolicy.difficultyIndex(sheet.difficulty),
                    achievements = score.achievement,
                    type = if (sheet.isDx()) "DX" else "SD",
                    dxScore = score.dxScore,
                    fc = score.fc,
                    fs = score.fs,
                    ),
                ),
            ),
        )
    }

    private suspend fun uploadToLxns(
        title: String,
        sheet: SheetEntity,
        score: ScoreEntity,
        profile: UserProfileEntity,
        refreshToken: String,
    ) {
        if (refreshToken.isBlank() || sheet.providerSongId <= 0) return
        val tokenPair = backendImportService.refreshLxnsToken(refreshToken)
        credentials.save(
            profile.id,
            credentials.credentials(profile.id).copy(lxnsToken = tokenPair.refreshToken),
        )
        postJson(
            url = LxnsScoreUploadUrl,
            headers = mapOf("Authorization" to "Bearer ${tokenPair.accessToken}"),
            body = json.encodeToString(
                LxnsScoreUploadRequest(
                    scores = listOf(
                        LxnsScoreUploadRecord(
                            id = ThirdPartyScoreSyncPolicy.lxnsSongId(sheet.providerSongId, sheet.type),
                            songName = title,
                            levelIndex = ThirdPartyScoreSyncPolicy.difficultyIndex(sheet.difficulty),
                            type = sheet.lxnsType(),
                            achievements = score.achievement,
                            dxScore = score.dxScore,
                            fc = score.fc,
                            fs = score.fs,
                        ),
                    ),
                ),
            ),
        )
    }

    private suspend fun postJson(url: String, headers: Map<String, String>, body: String) {
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = NetworkTimeoutMillis
                connection.readTimeout = NetworkTimeoutMillis
                connection.doOutput = true
                connection.setRequestProperty("Accept", "application/json")
                connection.setRequestProperty("Content-Type", "application/json")
                headers.forEach(connection::setRequestProperty)
                connection.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(body) }
                val status = connection.responseCode
                connection.errorStream?.close()
                connection.inputStream?.close()
                check(status in 200..299) { "Score sync failed: HTTP $status" }
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun SheetEntity.isDx(): Boolean = type.trim().equals("dx", ignoreCase = true)

    private fun SheetEntity.isUtage(): Boolean = type.trim().equals("utage", ignoreCase = true)

    private fun SheetEntity.lxnsType(): String = when {
        isDx() -> "dx"
        isUtage() -> "utage"
        else -> "standard"
    }

    private companion object {
        const val DivingFishScoreUploadUrl =
            "https://www.diving-fish.com/api/maimaidxprober/player/update_records"
        const val LxnsScoreUploadUrl = "https://maimai.lxns.net/api/v0/user/maimai/player/scores"
        const val NetworkTimeoutMillis = 30_000
    }
}

internal object ThirdPartyScoreSyncPolicy {
    fun isEligible(profile: UserProfileEntity, score: ScoreEntity, sheet: SheetEntity): Boolean =
        profile.isActive &&
            profile.server.equals("cn", ignoreCase = true) &&
            profile.id == score.profileId &&
            sheet.regionCn

    fun difficultyIndex(difficulty: String): Int = when (difficulty.trim().lowercase()) {
        "basic" -> 0
        "advanced" -> 1
        "expert" -> 2
        "master" -> 3
        "remaster" -> 4
        else -> 3
    }

    fun lxnsSongId(providerSongId: Int, chartType: String): Int = when {
        chartType.trim().equals("dx", ignoreCase = true) && providerSongId in 10_000..<100_000 ->
            providerSongId % 10_000
        else -> providerSongId
    }
}

@Serializable
private data class DivingFishScoreUploadRecord(
    val title: String,
    @SerialName("level_index") val levelIndex: Int,
    val achievements: Double,
    val type: String,
    @SerialName("dxScore") val dxScore: Int,
    val fc: String? = null,
    val fs: String? = null,
)

@Serializable
private data class LxnsScoreUploadRecord(
    val id: Int,
    @SerialName("song_name") val songName: String,
    @SerialName("level_index") val levelIndex: Int,
    val type: String,
    val achievements: Double,
    @SerialName("dx_score") val dxScore: Int,
    val fc: String? = null,
    val fs: String? = null,
)

@Serializable
private data class LxnsScoreUploadRequest(
    val scores: List<LxnsScoreUploadRecord>,
)
