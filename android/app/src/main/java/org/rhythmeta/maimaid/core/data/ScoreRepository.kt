package org.rhythmeta.maimaid.core.data

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import org.rhythmeta.maimaid.core.database.MaimaidDatabase
import org.rhythmeta.maimaid.core.database.PlayRecordEntity
import org.rhythmeta.maimaid.core.database.ScoreEntity
import java.util.UUID

data class SongScoreData(
    val profileId: String? = null,
    val scores: List<ScoreEntity> = emptyList(),
    val playRecords: List<PlayRecordEntity> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class ScoreRepository(
    private val database: MaimaidDatabase,
    private val profileRepository: ProfileRepository,
    private val clock: () -> Long = System::currentTimeMillis,
    private val idFactory: () -> String = { UUID.randomUUID().toString() },
) {
    private val catalogDao = database.catalogDao()
    private val scoreDao = database.scoreDao()

    fun observeActiveScores(): Flow<List<ScoreEntity>> =
        profileRepository.activeProfile.flatMapLatest { profile ->
            if (profile == null) flowOf(emptyList()) else scoreDao.observeScores(profile.id)
        }

    fun observeSongScoreData(songIdentifier: String): Flow<SongScoreData> =
        profileRepository.activeProfile.flatMapLatest { profile ->
            if (profile == null) {
                flowOf(SongScoreData())
            } else {
                flow {
                    repairMissingPlayRecords(profile.id, songIdentifier)
                    emitAll(
                        combine(
                            scoreDao.observeScoresForSong(profile.id, songIdentifier),
                            scoreDao.observePlayRecordsForSong(profile.id, songIdentifier),
                        ) { scores, records ->
                            SongScoreData(
                                profileId = profile.id,
                                scores = scores,
                                playRecords = records,
                            )
                        },
                    )
                }
            }
        }

    suspend fun saveScore(
        sheetKey: String,
        input: ScoreInput,
        maxDxScoreOverride: Int? = null,
    ): ScoreEntity = database.withTransaction {
        val profile = profileRepository.ensureActiveProfile()
        val sheet = requireNotNull(catalogDao.sheet(sheetKey)) { "Unknown sheet: $sheetKey" }
        val maxDxScore = ScoreRules.effectiveMaxDxScore(sheet.total, maxDxScoreOverride)
        val validationError = ScoreRules.validate(input, maxDxScore)
        require(validationError == null) { "Invalid score: $validationError" }

        val now = clock()
        val normalizedInput = input.copy(
            fc = ScoreRules.canonicalFc(input.fc),
            fs = ScoreRules.canonicalFs(input.fs),
        )
        val record = PlayRecordEntity(
            id = idFactory(),
            profileId = profile.id,
            sheetKey = sheetKey,
            achievement = normalizedInput.achievement,
            rank = ScoreRules.calculateRank(normalizedInput.achievement),
            dxScore = normalizedInput.dxScore,
            fc = normalizedInput.fc,
            fs = normalizedInput.fs,
            playedAt = now,
        )
        scoreDao.upsertPlayRecord(record)

        val merged = ScoreRules.mergeScore(
            profileId = profile.id,
            sheetKey = sheetKey,
            existing = scoreDao.score(profile.id, sheetKey),
            input = normalizedInput,
            now = now,
        )
        scoreDao.upsertScore(merged)
        merged
    }

    suspend fun deletePlayRecord(recordId: String) = database.withTransaction {
        val profile = profileRepository.ensureActiveProfile()
        val record = scoreDao.playRecord(recordId) ?: return@withTransaction
        if (record.profileId != profile.id) return@withTransaction

        scoreDao.deletePlayRecord(record)
        val currentScore = scoreDao.score(profile.id, record.sheetKey) ?: return@withTransaction
        if (!ScoreRules.deletedRecordWasBest(currentScore, record)) return@withTransaction

        val fallback = ScoreRules.bestHistoryRecord(scoreDao.playRecords(profile.id, record.sheetKey))
        if (fallback == null) {
            scoreDao.deleteScore(profile.id, record.sheetKey)
        } else {
            scoreDao.upsertScore(
                currentScore.copy(
                    achievement = fallback.achievement,
                    rank = fallback.rank,
                    dxScore = fallback.dxScore,
                    fc = fallback.fc,
                    fs = fallback.fs,
                    achievedAt = fallback.playedAt,
                ),
            )
        }
    }

    suspend fun deleteScore(sheetKey: String) = database.withTransaction {
        val profile = profileRepository.ensureActiveProfile()
        scoreDao.deleteScore(profile.id, sheetKey)
    }

    private suspend fun repairMissingPlayRecords(profileId: String, songIdentifier: String) {
        database.withTransaction {
            val scores = scoreDao.scoresForSong(profileId, songIdentifier)
            scores.forEach { score ->
                val records = scoreDao.playRecords(profileId, score.sheetKey)
                val hasMatchingRecord = records.any {
                    kotlin.math.abs(it.achievement - score.achievement) < 0.0001
                }
                if (!hasMatchingRecord && score.achievement > 0) {
                    scoreDao.upsertPlayRecord(
                        PlayRecordEntity(
                            id = idFactory(),
                            profileId = score.profileId,
                            sheetKey = score.sheetKey,
                            achievement = score.achievement,
                            rank = score.rank,
                            dxScore = score.dxScore,
                            fc = score.fc,
                            fs = score.fs,
                            playedAt = score.achievedAt,
                        ),
                    )
                }
            }
        }
    }
}
